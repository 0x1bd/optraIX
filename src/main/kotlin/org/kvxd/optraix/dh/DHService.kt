package org.kvxd.optraix.dh

import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundCustomPayloadPacket
import org.kvxd.optraix.Log
import org.kvxd.optraix.dh.lod.DHLodCache
import org.kvxd.optraix.dh.lod.DHSectionPos
import org.kvxd.optraix.dh.net.DHClientState
import org.kvxd.optraix.dh.net.DHMessage
import org.kvxd.optraix.dh.net.DHProtocol
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.world.GameWorld
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class DHService : AutoCloseable {
    private val cache = DHLodCache()
    private val clients = ConcurrentHashMap<UUID, DHClientState>()
    private val bufferIds = AtomicInteger()
    private val workers = Executors.newFixedThreadPool(WorkerCount) { task ->
        Thread(task, "optraix-dh").apply { isDaemon = true }
    }

    fun handle(player: Player, world: GameWorld, payload: ByteArray): Boolean {
        val message = try {
            DHProtocol.decode(payload)
        } catch (cause: Throwable) {
            Log.warn("dh", "rejected payload from ${player.name}: ${cause.message}")
            if (cause.message?.startsWith("unsupported DH protocol") == true) {
                send(player, DHProtocol.closeReason(cause.message ?: "incompatible DH protocol"))
            }
            return true
        }

        val state = clients.computeIfAbsent(player.uuid) { DHClientState() }
        when (message) {
            is DHMessage.LevelInitRequest -> {
                send(
                    player,
                    DHProtocol.levelInit(
                        worldKey = message.worldKey,
                        serverKey = "",
                        levelKey = levelKey(player),
                        time = System.currentTimeMillis(),
                    ),
                )
            }

            is DHMessage.RemoteConfig -> {
                state.enabled = message.distantGeneration
                state.concurrencyLimit = message.requestConcurrency.coerceIn(1, DefaultRequestConcurrency)
                send(
                    player,
                    DHProtocol.remoteConfig(
                        renderDistance = message.renderDistance.coerceIn(1, MaxRenderDistanceChunks),
                        concurrency = state.concurrencyLimit,
                    ),
                )
                Log.info(
                    "dh",
                    "${player.name} ${if (state.enabled) "enabled" else "disabled"} DH " +
                        "(distance ${message.renderDistance}, ${state.concurrencyLimit} requests)",
                )
            }

            is DHMessage.Cancel -> if (message.tracker in state.activeTrackers) {
                state.cancelledTrackers += message.tracker
            }
            is DHMessage.FullDataRequest -> request(player, world, state, message)
        }
        return true
    }

    fun disconnect(player: Player) {
        clients.remove(player.uuid)
    }

    fun worldChanged(player: Player) {
        val state = clients[player.uuid] ?: return
        state.generation.incrementAndGet()
        state.cancelledTrackers.clear()
        send(
            player,
            DHProtocol.levelInit(
                worldKey = "minecraft:overworld",
                serverKey = "",
                levelKey = levelKey(player),
                time = System.currentTimeMillis(),
            ),
        )
    }

    private fun request(
        player: Player,
        world: GameWorld,
        state: DHClientState,
        request: DHMessage.FullDataRequest,
    ) {
        if (!state.enabled) {
            exception(player, request.tracker, RequestRejected, "Distant generation is disabled")
            return
        }
        if (request.worldName != levelKey(player)) {
            exception(player, request.tracker, RequestRejected, "Unknown level ${request.worldName}")
            return
        }
        if (request.position.detailLevel != DHSectionPos.SupportedDetailLevel) {
            exception(player, request.tracker, SectionRequiresSplitting, "Only detail level 6 is supported")
            return
        }
        if (state.activeRequests.incrementAndGet() > state.concurrencyLimit) {
            state.activeRequests.decrementAndGet()
            exception(player, request.tracker, RateLimited, "Too many concurrent LOD requests")
            return
        }
        state.activeTrackers += request.tracker
        val generation = state.generation.get()

        workers.execute {
            try {
                if (generation != state.generation.get()) return@execute
                if (state.cancelledTrackers.remove(request.tracker)) return@execute
                val lod = cache.get(world, request.position)
                if (generation != state.generation.get()) return@execute
                if (state.cancelledTrackers.remove(request.tracker)) return@execute
                val sendData = request.timestamp == null || request.timestamp < lod.timestamp
                if (!sendData) {
                    send(player, DHProtocol.fullDataResponse(request.tracker, null, EmptyBeacons))
                    return@execute
                }

                val bufferId = bufferIds.incrementAndGet()
                var offset = 0
                var first = true
                while (offset < lod.data.size) {
                    if (state.cancelledTrackers.remove(request.tracker)) return@execute
                    val length = minOf(DHProtocol.ChunkPayloadSize, lod.data.size - offset)
                    send(player, DHProtocol.fullDataChunk(bufferId, lod.data, offset, length, first))
                    first = false
                    offset += length
                }
                send(player, DHProtocol.fullDataResponse(request.tracker, bufferId, EmptyBeacons))
            } catch (cause: Throwable) {
                Log.error("dh", "could not build LOD ${request.position.x},${request.position.z}", cause)
                exception(player, request.tracker, RequestRejected, "Could not generate LOD")
            } finally {
                state.activeTrackers.remove(request.tracker)
                state.cancelledTrackers.remove(request.tracker)
                state.activeRequests.decrementAndGet()
            }
        }
    }

    private fun exception(player: Player, tracker: Int, type: Int, reason: String) {
        send(player, DHProtocol.exception(tracker, type, reason))
    }

    private fun send(player: Player, data: ByteArray) {
        player.connection.send(ClientboundCustomPayloadPacket(DHProtocol.Channel, data))
    }

    private fun levelKey(player: Player): String = "optraix:${player.worldName}"

    override fun close() {
        clients.clear()
        workers.shutdownNow()
        workers.awaitTermination(5, TimeUnit.SECONDS)
    }

    companion object {
        const val DefaultRequestConcurrency = 20
        const val MaxRenderDistanceChunks = 4096
        private val WorkerCount = (Runtime.getRuntime().availableProcessors() / 2).coerceIn(4, 16)
        private const val RateLimited = 0
        private const val RequestRejected = 2
        private const val SectionRequiresSplitting = 3
        private val EmptyBeacons = byteArrayOf(0, 0, 0, 0)
    }
}
