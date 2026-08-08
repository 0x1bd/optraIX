package org.kvxd.optraix.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import org.kvxd.optraix.Log
import org.kvxd.optraix.ServerConfig
import org.kvxd.optraix.block.property.BlockFace
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.block.ItemStack
import org.kvxd.optraix.block.Items
import org.kvxd.optraix.command.CommandRegistry
import org.kvxd.optraix.interaction.Interaction
import org.kvxd.optraix.interaction.UseOnBlockContext
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.player.PlayerProfileStore
import org.kvxd.optraix.redstone.RedstoneEngine
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockEntityNbt
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.DefaultWorldName
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.ManagedWorld
import org.kvxd.optraix.world.WorldManager
import org.kvxd.optraix.world.WorldStorage
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.generated.Protocols
import org.kvxd.kmcprotocol.network.server.Server
import org.kvxd.kmcprotocol.network.server.ServerSession
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.configuration.clientbound.ClientboundFinishConfigurationPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.configuration.clientbound.ClientboundRegistryDataPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.login.clientbound.ClientboundCompressPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.login.clientbound.ClientboundSuccessPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.login.serverbound.ServerboundLoginStartPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundAbilitiesPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundAcknowledgePlayerDiggingPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundBlockChangePacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundChunkBatchFinishedPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundChunkBatchStartPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundEntityDestroyPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundEntityHeadRotationPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundEntityTeleportPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundEntityUpdateAttributesPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundGameStateChangePacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundHeldItemSlotPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundKeepAlivePacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundLoginPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundMultiBlockChangePacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundOpenSignEntityPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundPlayerInfoPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundPlayerRemovePacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundPositionPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundSetSlotPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundSoundEffectPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundSpawnEntityPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundSpawnPositionPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundTileEntityDataPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundUnloadChunkPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundUpdateViewPositionPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundWindowItemsPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundBlockDigPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundBlockPlacePacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundChatCommandPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundChatMessagePacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundEntityActionPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundFlyingPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundHeldItemSlotPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundLookPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundPositionLookPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundPositionPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundSetCreativeSlotPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundUpdateSignPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.GameProfile
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.ItemSoundHolder
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.Position
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.Slot
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.SoundSource
import io.ktor.network.sockets.InetSocketAddress
import org.kvxd.optraix.redstone.optraix.OptraIxEngine
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlin.math.floor
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.status.clientbound.ClientboundServerInfoPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.status.serverbound.ServerboundPingPacket as StatusPingPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.status.clientbound.ClientboundPingPacket as StatusPongPacket

class OptraIxServer(val config: ServerConfig) {

    val worlds = WorldManager(config.worldDirectory)

    val world: GameWorld
        get() = worlds.default.world

    val engine: RedstoneEngine
        get() = worlds.default.engine

    val interaction: Interaction
        get() = worlds.default.interaction

    val players = ArrayList<Player>()

    val commands = CommandRegistry(this)

    val startedAt: Long = System.currentTimeMillis()

    @Volatile
    var targetTps: Int = config.tps

    @Volatile
    var measuredTps: Double = 0.0

    @Volatile
    var averageMspt: Double = 0.0

    @Volatile
    var running: Boolean = true

    @Volatile
    var onlineCount: Int = 0

    var currentTick: Long = 0
        private set

    init {
        configureWorld(worlds.default)
    }

    private val entityIds = AtomicInteger(1)
    private val tasks = ConcurrentLinkedQueue<Runnable>()
    private val protocol = Protocols.requireMinecraftVersion("1.20.4")

    fun submit(task: Runnable) {
        tasks.add(task)
    }

    fun runtimeFor(player: Player): ManagedWorld = worlds.find(player.worldName) ?: worlds.default

    fun worldFor(player: Player): GameWorld = runtimeFor(player).world

    fun engineFor(player: Player): RedstoneEngine = runtimeFor(player).engine

    fun interactionFor(player: Player): Interaction = runtimeFor(player).interaction

    fun useEngine(next: RedstoneEngine) {
        worlds.default.useEngine(next)
    }

    fun useEngine(player: Player, next: RedstoneEngine) {
        runtimeFor(player).useEngine(next)
    }

    private fun configureWorld(runtime: ManagedWorld) {
        runtime.world.soundListener = { pos, soundId, category, volume, pitch ->
            playSound(runtime, pos, soundId, category, volume, pitch)
        }
    }

    var boundPort: Int = 0
        private set

    private var socket: Server? = null

    private var tickThread: Thread? = null

    private var networkJob: Job? = null

    private val stopSignal = CompletableDeferred<Unit>()

    private val shutdownLock = Any()

    @Volatile
    private var shutdownResult: Int? = null

    suspend fun start(scope: CoroutineScope) {
        publishScope = scope

        runCatching {
            val restored = worlds.loadAll()
            for (runtime in worlds.all()) configureWorld(runtime)
            for ((name, chunks) in restored) {
                if (chunks > 0) println("restored $chunks chunks for world '$name'")
            }
        }.onFailure { Log.error("world", "could not load worlds from ${config.worldDirectory.path}", it) }

        runCatching {
            val restored = profiles.load()
            if (restored > 0) println("restored $restored player profiles")
        }.onFailure { Log.error("players", "could not load ${config.playerFile.path}", it) }

        val server = Server.bind(InetSocketAddress(config.host, config.port)) { protocol.protocolData() }
        socket = server
        boundPort = (server.localAddress as? InetSocketAddress)?.port ?: config.port
        println("optraix listening on ${config.host}:$boundPort (1.20.4, protocol 765)")
        println("redstone engine: ${engine.name}, target tps: ${tpsLabel()}")

        for (runtime in worlds.all()) {
            (runtime.engine as? OptraIxEngine)?.let { compileRedstone(runtime, it) }
        }

        startGameLoop()

        networkJob = scope.launch(Dispatchers.Default) {
            server.sessions.collect { session ->
                launch { runCatching { handleSession(session, this) } }
            }
        }
    }

    fun requestStop() {
        running = false
        tickThread?.interrupt()
        stopSignal.complete(Unit)
    }

    suspend fun awaitStop() {
        stopSignal.await()
    }

    fun shutdown(): Int {
        requestStop()
        synchronized(shutdownLock) {
            shutdownResult?.let { return it }

            val thread = tickThread
            if (thread != null && thread !== Thread.currentThread()) {
                runCatching { thread.join(ShutdownJoinMillis) }
                if (thread.isAlive) Log.warn("server", "tick thread did not stop within ${ShutdownJoinMillis}ms")
            }

            disconnectPlayers()
            runCatching { socket?.close() }
            networkJob?.cancel()

            val saved = saveWorld()
            shutdownResult = saved
            return saved
        }
    }

    private fun disconnectPlayers() {
        val connections = players.map { it.connection }.distinct()
        runBlocking {
            withTimeoutOrNull(DisconnectTimeoutMillis) {
                coroutineScope {
                    for (connection in connections) {
                        launch { runCatching { connection.disconnect(Text.of(ShutdownReason)) } }
                    }
                }
            }
        }
        for (connection in connections) connection.close()
    }

    fun saveWorld(): Int {
        for (player in players) profiles.put(player)
        runCatching { profiles.save() }
            .onFailure { Log.error("players", "save failed", it) }

        var total = 0
        for (runtime in worlds.all()) {
            val activeCircuit = (runtime.engine as? OptraIxEngine)?.circuit
            val worldToSave = if (activeCircuit == null) {
                runtime.world
            } else {
                runtime.world.copyForSave().also { snapshot ->
                    activeCircuit.writeSnapshot(snapshot)
                    activeCircuit.exportPendingTicks(snapshot)
                }
            }

            total += runCatching { WorldStorage.save(worldToSave, runtime.file) }
                .onFailure { Log.error("world", "save failed for '${runtime.name}'", it) }
                .getOrDefault(0)
        }
        return total
    }

    fun compileRedstone(target: OptraIxEngine) = compileRedstone(worlds.default, target)

    fun compileRedstone(player: Player, target: OptraIxEngine) = compileRedstone(runtimeFor(player), target)

    private fun compileRedstone(runtime: ManagedWorld, target: OptraIxEngine) {
        if (target.paused) return
        if (target.manualCompileRequired) return
        runtime.compiling = true
        refreshSidebar(force = true)
        val ok = target.compile(runtime.world)
        runtime.compiling = false
        if (ok) {
            val circuit = target.circuit
            println("[optraix:${runtime.name}] compiled ${circuit?.count} nodes, ${circuit?.edgeCount} edges in ${target.compileMillis}ms")
        } else {
            println("[optraix:${runtime.name}] compile failed: ${target.lastError} (running interpreted)")
        }
        refreshSidebar(force = true)
    }

    private var lastSidebar = 0L

    private fun maintainPressurePlates() {
        val now = System.currentTimeMillis()
        for (player in players) {
            val runtime = runtimeFor(player)
            val world = runtime.world
            val pos = BlockPos(floor(player.x).toInt(), floor(player.y).toInt(), floor(player.z).toInt())
            if (BlockStates.pressurePlatePowered(world.getBlock(pos)) == null) continue
            if (runtime.plateHeldUntil.put(pos.asLong(), now + PlateReleaseMillis) == null) {
                runtime.engine.setPressurePlate(world, pos, true)
            }
        }
        for (runtime in worlds.all()) {
            if (runtime.plateHeldUntil.isEmpty()) continue
            val iterator = runtime.plateHeldUntil.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value > now) continue
                iterator.remove()
                runtime.engine.setPressurePlate(runtime.world, BlockPos.unpack(entry.key), false)
            }
        }
    }

    private fun maintainRedstoneCompile() {
        val now = System.currentTimeMillis()
        for (runtime in worlds.all()) {
            val optraix = runtime.engine as? OptraIxEngine ?: continue
            if (optraix.paused || optraix.manualCompileRequired) continue
            val counter = optraix.changeCounter
            if (counter != runtime.lastEditCounter) {
                runtime.lastEditCounter = counter
                runtime.lastEditAt = now
                continue
            }
            if (runtime.lastEditAt == 0L || optraix.compiled) continue
            if (now - runtime.lastEditAt < RecompileDelayMillis) continue
            runtime.lastEditAt = 0L
            compileRedstone(runtime, optraix)
        }
    }

    private fun refreshSidebar(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastSidebar < SidebarIntervalMillis) return
        lastSidebar = now

        for (runtime in worlds.all()) {
            val targets = players.filter { runtimeFor(it) === runtime }
            if (targets.isEmpty()) continue
            val optraix = runtime.engine as? OptraIxEngine
            val circuit = optraix?.circuit
            val state = when {
                runtime.compiling -> "compiling" to Text.Yellow
                optraix?.paused == true -> "paused" to Text.Gray
                circuit != null -> "compiled" to Text.Green
                optraix?.lastError != null -> "failed" to Text.Red
                optraix != null -> "interpreted" to Text.Yellow
                else -> runtime.engine.name to Text.Gray
            }
            val lines = ArrayList<Sidebar.Line>(7)
            lines += Sidebar.Line("world", "world ", runtime.name, Text.Aqua)
            lines += Sidebar.Line("tps", "tps ", "%.1f".format(measuredTps), tpsColor())
            lines += Sidebar.Line("mspt", "mspt ", "%.2f".format(averageMspt), Text.White)
            lines += Sidebar.Line("redstone", "redstone ", state.first, state.second)
            if (circuit != null) {
                lines += Sidebar.Line("nodes", "nodes ", circuit.count.toString(), Text.Aqua)
                lines += Sidebar.Line("compile", "built ", "${optraix.compileMillis}ms", Text.Aqua)
            }
            sidebar.update(targets, lines)
        }
    }

    private fun tpsColor(): String {
        if (targetTps <= 0) return Text.Aqua
        return when {
            measuredTps >= targetTps * 0.95 -> Text.Green
            measuredTps >= targetTps * 0.7 -> Text.Yellow
            else -> Text.Red
        }
    }

    fun tpsLabel(): String = if (targetTps <= 0) "unlimited" else targetTps.toString()

    private fun startGameLoop() {
        val thread = Thread({
            var lastSample = System.nanoTime()
            var ticksSinceSample = 0L
            var nanosSinceSample = 0L
            var nextTick = System.nanoTime()
            var batch = 1

            while (running) {
                val target = targetTps
                val batchStart = System.nanoTime()

                if (target > 0) {
                    runHousekeeping()
                    if (!running) break
                    tickWorlds()
                    publishWorldChanges()
                    currentTick++
                    ticksSinceSample++
                } else {
                    var executed = 0
                    while (executed < batch && running) {
                        tickWorlds()
                        publishWorldChanges()
                        executed++
                    }
                    currentTick += executed
                    ticksSinceSample += executed
                    runHousekeeping()
                }

                val batchNanos = System.nanoTime() - batchStart
                nanosSinceSample += batchNanos

                if (target <= 0) {
                    batch = nextBatchSize(batch, batchNanos)
                }

                val sampleElapsed = System.nanoTime() - lastSample
                if (sampleElapsed >= 1_000_000_000L && ticksSinceSample > 0) {
                    measuredTps = ticksSinceSample * 1_000_000_000.0 / sampleElapsed
                    averageMspt = nanosSinceSample / 1_000_000.0 / ticksSinceSample
                    ticksSinceSample = 0
                    nanosSinceSample = 0
                    lastSample = System.nanoTime()
                }

                if (target <= 0) {
                    nextTick = System.nanoTime()
                    continue
                }

                val budget = 1_000_000_000L / target
                nextTick += budget
                var now = System.nanoTime()
                if (now - nextTick > budget * 20) nextTick = now
                while (running && now < nextTick) {
                    val remaining = nextTick - now
                    if (remaining > 500_000L) LockSupport.parkNanos(remaining - 300_000L)
                    else Thread.onSpinWait()
                    now = System.nanoTime()
                }
            }
        }, "optraix-tick")
        thread.isDaemon = true
        tickThread = thread
        thread.start()
    }

    private fun tickWorlds() {
        for (runtime in worlds.all()) runtime.engine.tickWorld(runtime.world)
    }

    private fun nextBatchSize(current: Int, batchNanos: Long): Int {
        if (batchNanos > BatchTargetNanos && current > 1) return maxOf(1, current shr 1)
        if (batchNanos < BatchTargetNanos / 2 && current < MaxBatch) return current shl 1
        return current
    }

    private fun runHousekeeping() {
        while (true) {
            val task = tasks.poll() ?: break
            runCatching { task.run() }
        }
        broadcastMovement()
        maintainConnections()
        maintainSelectionOutlines()
        maintainPressurePlates()
        maintainRedstoneCompile()
        refreshSidebar()
        maintainAutosave()
    }

    private var publishScope: CoroutineScope? = null
    private var lastPublish = 0L
    private val publishing = java.util.concurrent.atomic.AtomicBoolean(false)

    private var pendingAcks = 0

    private fun queueBlockAck(player: Player, sequence: Int) {
        if (player.pendingBlockAck < 0) pendingAcks++
        if (sequence > player.pendingBlockAck) player.pendingBlockAck = sequence
    }

    private fun takeBlockAcks(): List<Pair<Player, Int>> {
        pendingAcks = 0
        var acks: MutableList<Pair<Player, Int>>? = null
        for (index in players.indices) {
            val player = players[index]
            val sequence = player.pendingBlockAck
            if (sequence < 0) continue
            player.pendingBlockAck = -1
            val list = acks ?: ArrayList<Pair<Player, Int>>(2).also { acks = it }
            list += player to sequence
        }
        return acks ?: emptyList()
    }

    private fun sendBlockAcks(acks: List<Pair<Player, Int>>) {
        for ((player, sequence) in acks) {
            player.connection.send(ClientboundAcknowledgePlayerDiggingPacket(sequence))
        }
    }

    private data class WorldChangeBatch(
        val runtime: ManagedWorld,
        val blocks: LongArray,
        val states: IntArray,
        val entityKeys: LongArray,
        val entities: Array<BlockEntity?>,
    )

    internal fun publishWorldChanges() {
        val changed = worlds.all().filter {
            it.world.changedBlocks.isNotEmpty() || it.world.changedBlockEntities.isNotEmpty()
        }
        if (changed.isEmpty()) {
            if (pendingAcks > 0) sendBlockAcks(takeBlockAcks())
            return
        }
        if (players.isEmpty()) {
            pendingAcks = 0
            for (runtime in changed) {
                runtime.world.changedBlocks.clear()
                runtime.world.changedBlockEntities.clear()
            }
            return
        }
        val now = System.nanoTime()
        if (pendingAcks == 0 && now - lastPublish < PublishIntervalNanos) return
        if (!publishing.compareAndSet(false, true)) return
        lastPublish = now
        val acks = takeBlockAcks()
        val batches = ArrayList<WorldChangeBatch>(changed.size)

        for (runtime in changed) {
            val world = runtime.world
            if (players.none { runtimeFor(it) === runtime }) {
                world.changedBlocks.clear()
                world.changedBlockEntities.clear()
                continue
            }

            val blocks = LongArray(world.changedBlocks.size)
            val states = IntArray(blocks.size)
            var index = 0
            for (packed in world.changedBlocks) {
                blocks[index] = packed
                states[index] = world.getBlock(BlockPos.unpack(packed))
                index++
            }
            world.changedBlocks.clear()

            val entityKeys = LongArray(world.changedBlockEntities.size)
            val entities = arrayOfNulls<BlockEntity>(entityKeys.size)
            index = 0
            for (packed in world.changedBlockEntities) {
                entityKeys[index] = packed
                entities[index] = world.getBlockEntity(BlockPos.unpack(packed))
                index++
            }
            world.changedBlockEntities.clear()
            batches += WorldChangeBatch(runtime, blocks, states, entityKeys, entities)
        }

        if (batches.isEmpty()) {
            sendBlockAcks(acks)
            publishing.set(false)
            return
        }

        val scope = publishScope
        if (scope == null) {
            for (batch in batches) {
                dispatchPackets(
                    batch.runtime,
                    encodeWorldChanges(batch.blocks, batch.states, batch.entityKeys, batch.entities),
                )
            }
            sendBlockAcks(acks)
            publishing.set(false)
            return
        }
        scope.launch(Dispatchers.Default) {
            val encoded = batches.map { batch ->
                batch.runtime to encodeWorldChanges(batch.blocks, batch.states, batch.entityKeys, batch.entities)
            }
            submit {
                for ((runtime, packets) in encoded) dispatchPackets(runtime, packets)
                sendBlockAcks(acks)
                publishing.set(false)
            }
        }
    }

    private fun encodeWorldChanges(
        blocks: LongArray,
        states: IntArray,
        entityKeys: LongArray,
        entities: Array<BlockEntity?>,
    ): List<Pair<Long, org.kvxd.kmcprotocol.core.MinecraftPacket>> {
        val packets = ArrayList<Pair<Long, org.kvxd.kmcprotocol.core.MinecraftPacket>>()
        if (blocks.isNotEmpty()) {
            val bySection = HashMap<Long, MutableList<Int>>()
            for (slot in blocks.indices) {
                val pos = BlockPos.unpack(blocks[slot])
                val key = sectionKey(pos.x shr 4, pos.y shr 4, pos.z shr 4)
                bySection.getOrPut(key) { ArrayList() }.add(slot)
            }
            for ((key, slots) in bySection) {
                val chunkX = (key shr 40).toInt()
                val chunkZ = ((key shl 24) shr 40).toInt()
                val sectionY = ((key shl 48) shr 48).toInt()
                val chunkKey = chunkKey(chunkX, chunkZ)
                if (slots.size == 1) {
                    val pos = BlockPos.unpack(blocks[slots[0]])
                    packets += chunkKey to ClientboundBlockChangePacket(
                        Position(pos.x, pos.z, pos.y),
                        states[slots[0]],
                    )
                } else {
                    val records = slots.map { slot ->
                        val pos = BlockPos.unpack(blocks[slot])
                        (states[slot] shl 12) or ((pos.x and 15) shl 8) or ((pos.z and 15) shl 4) or (pos.y and 15)
                    }
                    packets += chunkKey to ClientboundMultiBlockChangePacket(
                        ClientboundMultiBlockChangePacket.ChunkCoordinates(chunkX, chunkZ, sectionY),
                        records,
                    )
                }
            }
        }
        for (slot in entityKeys.indices) {
            val pos = BlockPos.unpack(entityKeys[slot])
            val entity = entities[slot]
            packets += chunkKey(pos.x shr 4, pos.z shr 4) to ClientboundTileEntityDataPacket(
                Position(pos.x, pos.z, pos.y),
                entity?.let { BlockEntityNbt.typeId(it) } ?: 0,
                entity?.let { BlockEntityNbt.toNbt(it) },
            )
        }
        return packets
    }

    private fun sectionKey(chunkX: Int, sectionY: Int, chunkZ: Int): Long =
        (chunkX.toLong() shl 40) or ((chunkZ.toLong() and 0xFFFFFF) shl 16) or (sectionY.toLong() and 0xFFFF)

    private fun chunkKey(chunkX: Int, chunkZ: Int): Long =
        (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)

    private fun dispatchPackets(
        runtime: ManagedWorld,
        packets: List<Pair<Long, org.kvxd.kmcprotocol.core.MinecraftPacket>>,
    ) {
        for ((chunkKey, packet) in packets) sendToChunk(runtime, chunkKey, packet)
    }

    private fun sendToChunk(
        runtime: ManagedWorld,
        chunkKey: Long,
        packet: org.kvxd.kmcprotocol.core.MinecraftPacket,
    ) {
        for (player in players) {
            if (runtimeFor(player) === runtime && chunkKey in player.loadedChunks) player.connection.send(packet)
        }
    }

    fun broadcast(packet: org.kvxd.kmcprotocol.core.MinecraftPacket, except: Player? = null) {
        for (player in players) if (player !== except) player.connection.send(packet)
    }

    fun broadcastWorld(
        runtime: ManagedWorld,
        packet: org.kvxd.kmcprotocol.core.MinecraftPacket,
        except: Player? = null,
    ) {
        for (player in players) {
            if (player !== except && runtimeFor(player) === runtime) player.connection.send(packet)
        }
    }

    fun broadcastMessage(text: String) {
        for (player in players) player.connection.sendMessage(text)
    }

    private var lastOutline: Long = 0

    private fun maintainSelectionOutlines() {
        val now = System.currentTimeMillis()
        if (now - lastOutline < SelectionOutlineIntervalMillis) return
        lastOutline = now
        for (player in players) {
            if (player.showSelection) SelectionOutline.draw(player)
        }
    }

    private val sidebar = Sidebar()

    fun setSidebarVisible(player: Player, visible: Boolean) {
        player.showSidebar = visible
        sidebar.setVisible(player, visible)
    }

    private var lastKeepAlive: Long = 0
    private var lastAutosave: Long = System.currentTimeMillis()

    private fun maintainAutosave() {
        if (config.autosaveSeconds <= 0) return
        val now = System.currentTimeMillis()
        if (now - lastAutosave < config.autosaveSeconds * 1000L) return
        lastAutosave = now
        val saved = saveWorld()
        if (saved > 0) println("[world] autosaved $saved chunks")
    }

    private fun maintainConnections() {
        val now = System.currentTimeMillis()
        if (now - lastKeepAlive < KeepAliveIntervalMillis) return
        lastKeepAlive = now
        for (player in players) {
            player.lastKeepAlive = now
            player.connection.send(ClientboundKeepAlivePacket(now))
        }
    }

    private fun broadcastMovement() {
        for (player in players) {
            if (!player.moved) continue
            player.moved = false
            val yaw = angleToByte(player.yaw)
            val teleport = ClientboundEntityTeleportPacket(
                player.entityId, player.x, player.y, player.z, yaw, angleToByte(player.pitch), player.onGround
            )
            val head = ClientboundEntityHeadRotationPacket(player.entityId, yaw)
            val runtime = runtimeFor(player)
            broadcastWorld(runtime, teleport, player)
            broadcastWorld(runtime, head, player)
        }
    }

    private fun angleToByte(angle: Float): Byte =
        (Math.floorMod((angle * 256.0f / 360.0f).toInt(), 256)).toByte()

    private suspend fun handleSession(session: ServerSession, scope: CoroutineScope) {
        var username: String? = null
        var player: Player? = null
        try {
            while (true) {
                val packet = session.receiveOrNull() ?: break
                when (packet) {
                    is org.kvxd.kmcprotocol.packets.generated.v1_20_4.status.serverbound.ServerboundPingStartPacket,
                    is StatusPingPacket -> handleStatus(session, packet)

                    is ServerboundLoginStartPacket -> {
                        username = packet.username
                        if (config.compressionThreshold > 0) {
                            session.send(ClientboundCompressPacket(config.compressionThreshold))
                        }
                        session.send(ClientboundSuccessPacket(offlineUuid(packet.username), packet.username, emptyList()))
                    }

                    is org.kvxd.kmcprotocol.packets.generated.v1_20_4.login.serverbound.ServerboundLoginAcknowledgedPacket -> {
                        session.send(brandPacket())
                        session.send(ClientboundRegistryDataPacket(Registries.codec))
                        session.send(ClientboundFinishConfigurationPacket)
                    }

                    is org.kvxd.kmcprotocol.packets.generated.v1_20_4.configuration.serverbound.ServerboundFinishConfigurationPacket -> {
                        if (player == null) {
                            val created = createPlayer(session, scope, username ?: "player")
                            player = created
                            submit { addPlayer(created) }
                        }
                    }

                    else -> {
                        val target = player
                        if (target != null && session.data.state == ProtocolState.Play) {
                            submit { handlePlayPacket(target, packet) }
                        }
                    }
                }
            }
            if (running) Log.info("net", "${session.remoteAddress} closed the connection")
        } catch (cause: Throwable) {
            if (running) Log.error("net", "${session.remoteAddress} failed in ${session.data.state}", cause)
        } finally {
            val target = player
            if (target != null) submit { removePlayer(target) } else runCatching { session.close() }
        }
    }

    private fun brandPacket() =
        org.kvxd.kmcprotocol.packets.generated.v1_20_4.configuration.clientbound.ClientboundCustomPayloadPacket(
            "minecraft:brand",
            encodeBrand("optraix"),
        )

    private fun encodeBrand(brand: String): ByteArray {
        val bytes = brand.toByteArray(Charsets.UTF_8)
        val output = java.io.ByteArrayOutputStream()
        var value = bytes.size
        while (true) {
            if (value and 0x7F.inv() == 0) {
                output.write(value)
                break
            }
            output.write((value and 0x7F) or 0x80)
            value = value ushr 7
        }
        output.write(bytes)
        return output.toByteArray()
    }

    private suspend fun handleStatus(session: ServerSession, packet: org.kvxd.kmcprotocol.core.MinecraftPacket) {
        when (packet) {
            is org.kvxd.kmcprotocol.packets.generated.v1_20_4.status.serverbound.ServerboundPingStartPacket -> {
                val online = onlineCount
                val json = buildString {
                    append("{\"version\":{\"name\":\"1.20.4\",\"protocol\":765},")
                    append("\"players\":{\"max\":${config.maxPlayers},\"online\":$online,\"sample\":[]},")
                    append("\"description\":{\"text\":\"${config.motd.replace("\"", "\\\"")}\"},")
                    append("\"enforcesSecureChat\":false}")
                }
                session.send(ClientboundServerInfoPacket(json))
            }
            is StatusPingPacket -> session.send(StatusPongPacket(packet.time))
            else -> Unit
        }
    }

    private fun offlineUuid(name: String): UUID =
        UUID.nameUUIDFromBytes("OfflinePlayer:$name".toByteArray(Charsets.UTF_8))

    fun createPlayer(session: ServerSession, scope: CoroutineScope, name: String): Player {
        val connection = PlayerConnection(session, scope)
        return Player(entityIds.incrementAndGet(), offlineUuid(name), name, connection)
    }

    val profiles = PlayerProfileStore(config.playerFile)

    fun addPlayer(player: Player) {
        profiles[player.name]?.applyTo(player)
        if (worlds.find(player.worldName) == null) player.worldName = DefaultWorldName
        players.add(player)
        onlineCount = players.size
        sendJoinSequence(player)
        sidebar.install(player)
        refreshSidebar(force = true)
    }

    fun createWorld(name: String): ManagedWorld? = worlds.create(name)?.also(::configureWorld)

    fun deleteWorld(name: String): Boolean {
        val runtime = worlds.find(name) ?: return false
        if (players.any { runtimeFor(it) === runtime }) return false
        return worlds.delete(runtime.name)
    }

    fun joinWorld(player: Player, name: String): Boolean {
        val target = worlds.find(name) ?: return false
        val previous = runtimeFor(player)
        if (previous === target) return true

        val previousPeers = players.filter { it !== player && runtimeFor(it) === previous }
        val targetPeers = players.filter { it !== player && runtimeFor(it) === target }

        if (previousPeers.isNotEmpty()) {
            val removePlayer = ClientboundEntityDestroyPacket(listOf(player.entityId))
            for (peer in previousPeers) peer.connection.send(removePlayer)
            player.connection.send(ClientboundEntityDestroyPacket(previousPeers.map { it.entityId }))
        }

        ContainerScreens.close(player)
        unloadAllChunks(player)
        if (player.pendingBlockAck >= 0) {
            player.pendingBlockAck = -1
            pendingAcks = maxOf(0, pendingAcks - 1)
        }
        player.worldName = target.name
        player.selectionOne = null
        player.selectionTwo = null
        player.undoStack.clear()
        player.redoStack.clear()
        player.lastChunkX = Int.MIN_VALUE
        player.lastChunkZ = Int.MIN_VALUE
        player.moved = false

        sendPosition(player)
        player.connection.send(ClientboundGameStateChangePacket(WaitForChunksReason, 0.0f))
        updateChunks(player, force = true)

        for (peer in targetPeers) {
            player.connection.send(spawnPacket(peer))
            peer.connection.send(spawnPacket(player))
        }
        profiles.put(player)
        refreshSidebar(force = true)
        return true
    }

    private fun unloadAllChunks(player: Player) {
        for (key in player.loadedChunks) {
            player.connection.send(
                ClientboundUnloadChunkPacket((key and 0xFFFFFFFFL).toInt(), (key shr 32).toInt())
            )
        }
        player.loadedChunks.clear()
    }

    private fun sendJoinSequence(player: Player) {
        val connection = player.connection
        connection.send(
            ClientboundLoginPacket(
                entityId = player.entityId,
                isHardcore = false,
                worldNames = listOf("minecraft:overworld"),
                maxPlayers = config.maxPlayers,
                viewDistance = config.viewDistance,
                simulationDistance = config.viewDistance,
                reducedDebugInfo = false,
                enableRespawnScreen = false,
                doLimitedCrafting = false,
                worldType = "minecraft:overworld",
                worldName = "minecraft:overworld",
                hashedSeed = 0L,
                gameMode = 1,
                previousGameMode = -1,
                isDebug = false,
                isFlat = true,
                death = null,
                portalCooldown = 0,
            )
        )
        sendAbilities(player)
        connection.send(ClientboundHeldItemSlotPacket(player.selectedSlot.toByte()))
        connection.send(ClientboundSpawnPositionPacket(Position(0, 0, 1), 0.0f))
        connection.send(commands.declarePacket)
        sendInventory(player)

        sendPosition(player)
        connection.send(ClientboundGameStateChangePacket(WaitForChunksReason, 0.0f))
        updateChunks(player, force = true)

        val addSelf = playerInfoAdd(listOf(player))
        val runtime = runtimeFor(player)
        for (other in players) {
            other.connection.send(addSelf)
            if (other !== player) {
                player.connection.send(playerInfoAdd(listOf(other)))
                if (runtimeFor(other) === runtime) {
                    player.connection.send(spawnPacket(other))
                    other.connection.send(spawnPacket(player))
                }
            }
        }
        broadcastMessage("${player.name} joined")
    }

    private fun playerInfoAdd(targets: List<Player>): ClientboundPlayerInfoPacket =
        ClientboundPlayerInfoPacket(
            action = setOf(
                ClientboundPlayerInfoPacket.Action.AddPlayer,
                ClientboundPlayerInfoPacket.Action.UpdateListed,
                ClientboundPlayerInfoPacket.Action.UpdateGameMode,
            ),
            data = targets.map { target ->
                ClientboundPlayerInfoPacket.DataEntry(
                    uuid = target.uuid,
                    player = GameProfile(target.name, emptyList()),
                    chatSession = null,
                    gamemode = 1,
                    listed = 1,
                    latency = null,
                    displayName = null,
                )
            },
        )

    private fun spawnPacket(target: Player): ClientboundSpawnEntityPacket =
        ClientboundSpawnEntityPacket(
            entityId = target.entityId,
            objectUUID = target.uuid,
            type = PlayerEntityTypeId,
            x = target.x,
            y = target.y,
            z = target.z,
            pitch = angleToByte(target.pitch),
            yaw = angleToByte(target.yaw),
            headPitch = angleToByte(target.yaw),
            objectData = 0,
            velocity = org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.Vec3i16(0, 0, 0),
        )

    fun sendPosition(player: Player) {
        player.teleportId++
        player.connection.send(
            ClientboundPositionPacket(
                player.x, player.y, player.z, player.yaw, player.pitch, 0, player.teleportId
            )
        )
    }

    fun sendAbilities(player: Player) {
        var flags = 0x01 or 0x04 or 0x08
        if (player.flying) flags = flags or 0x02
        player.connection.send(
            ClientboundAbilitiesPacket(flags.toByte(), player.flyingSpeed, player.walkingSpeed)
        )
        player.connection.send(
            ClientboundEntityUpdateAttributesPacket(
                player.entityId,
                listOf(
                    ClientboundEntityUpdateAttributesPacket.Property(
                        name = "minecraft:generic.movement_speed",
                        value = player.walkingSpeed.toDouble(),
                        modifiers = emptyList(),
                    )
                ),
            )
        )
    }

    fun sendInventory(player: Player) {
        val slots = player.inventory.map { stack ->
            if (stack == null) Slot(false, null, null, null)
            else Slot(true, stack.item.protocolId, stack.count.toByte(), stack.nbt)
        }
        player.connection.send(
            ClientboundWindowItemsPacket(0, 0, slots, Slot(false, null, null, null))
        )
    }

    fun removePlayer(player: Player) {
        val runtime = runtimeFor(player)
        if (!players.remove(player)) return
        profiles.put(player)
        onlineCount = players.size
        player.connection.close()
        broadcastWorld(runtime, ClientboundEntityDestroyPacket(listOf(player.entityId)))
        broadcast(ClientboundPlayerRemovePacket(listOf(player.uuid)))
        broadcastMessage("${player.name} left")
    }

    fun updateChunks(player: Player, force: Boolean = false) {
        val chunkX = floor(player.x).toInt() shr 4
        val chunkZ = floor(player.z).toInt() shr 4
        if (!force && chunkX == player.lastChunkX && chunkZ == player.lastChunkZ) return
        player.lastChunkX = chunkX
        player.lastChunkZ = chunkZ

        player.connection.send(ClientboundUpdateViewPositionPacket(chunkX, chunkZ))

        val desired = HashSet<Long>()
        val distance = config.viewDistance
        for (dx in -distance..distance) {
            for (dz in -distance..distance) desired.add(chunkKey(chunkX + dx, chunkZ + dz))
        }

        val toUnload = player.loadedChunks.filter { it !in desired }
        for (key in toUnload) {
            player.loadedChunks.remove(key)
            player.connection.send(
                ClientboundUnloadChunkPacket((key and 0xFFFFFFFFL).toInt(), (key shr 32).toInt())
            )
        }

        val toLoad = desired.filter { it !in player.loadedChunks }.sortedBy { key ->
            val dx = (key shr 32).toInt() - chunkX
            val dz = (key and 0xFFFFFFFFL).toInt() - chunkZ
            dx * dx + dz * dz
        }
        if (toLoad.isEmpty()) return

        player.connection.send(ClientboundChunkBatchStartPacket)
        for (key in toLoad) {
            val cx = (key shr 32).toInt()
            val cz = (key and 0xFFFFFFFFL).toInt()
            player.connection.send(ChunkPackets.encode(worldFor(player).chunkAt(cx, cz)))
            player.loadedChunks.add(key)
        }
        player.connection.send(ClientboundChunkBatchFinishedPacket(toLoad.size))
    }

    fun handlePlayPacket(player: Player, packet: org.kvxd.kmcprotocol.core.MinecraftPacket) {
        val world = worldFor(player)
        when (packet) {
            is ServerboundPositionPacket -> {
                player.x = packet.x
                player.y = packet.y
                player.z = packet.z
                player.onGround = packet.onGround
                player.moved = true
                updateChunks(player)
            }
            is ServerboundPositionLookPacket -> {
                player.x = packet.x
                player.y = packet.y
                player.z = packet.z
                player.yaw = packet.yaw
                player.pitch = packet.pitch
                player.onGround = packet.onGround
                player.moved = true
                updateChunks(player)
            }
            is ServerboundLookPacket -> {
                player.yaw = packet.yaw
                player.pitch = packet.pitch
                player.onGround = packet.onGround
                player.moved = true
            }
            is ServerboundFlyingPacket -> player.onGround = packet.onGround
            is ServerboundHeldItemSlotPacket -> player.selectedSlot = packet.slotId.toInt().coerceIn(0, 8)
            is ServerboundSetCreativeSlotPacket -> {
                val slot = packet.slot.toInt()
                if (slot in player.inventory.indices) {
                    player.inventory[slot] = toStack(packet.item)
                    player.connection.send(
                        ClientboundSetSlotPacket(0, 0, slot.toShort(), packet.item)
                    )
                }
            }
            is org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundCloseWindowPacket ->
                ContainerScreens.close(player)
            is org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundWindowClickPacket -> {
                if (packet.windowId.toInt() == ContainerScreens.WindowId) {
                    for (changed in packet.changedSlots) {
                        ContainerScreens.applyClick(world, player, changed.location.toInt(), changed.item)
                    }
                } else if (packet.windowId.toInt() == 0) {
                    for (changed in packet.changedSlots) {
                        val slot = changed.location.toInt()
                        if (slot in player.inventory.indices) player.inventory[slot] = toStack(changed.item)
                    }
                    player.carriedItem = toStack(packet.cursorItem)
                }
            }
            is ServerboundEntityActionPacket -> {
                when (packet.actionId) {
                    0 -> player.crouching = true
                    1 -> player.crouching = false
                }
            }
            is org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundAbilitiesPacket -> {
                player.flying = (packet.flags.toInt() and 0x02) != 0
            }
            is org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundKeepAlivePacket -> {
                if (packet.keepAliveId == player.lastKeepAlive) {
                    player.latency = (System.currentTimeMillis() - packet.keepAliveId).toInt()
                }
            }
            is ServerboundBlockDigPacket -> handleDig(player, packet)
            is ServerboundBlockPlacePacket -> handlePlace(player, packet)
            is ServerboundChatMessagePacket -> broadcastMessage("<${player.name}> ${packet.message}")
            is ServerboundChatCommandPacket -> commands.execute(player, packet.command)
            is org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundTabCompletePacket ->
                commands.complete(player, packet.transactionId, packet.text)
            is ServerboundUpdateSignPacket -> handleSignUpdate(player, packet)
            else -> Unit
        }
    }

    private fun handleSignUpdate(player: Player, packet: ServerboundUpdateSignPacket) {
        val world = worldFor(player)
        val pos = BlockPos(packet.location.x, packet.location.y, packet.location.z)
        val lines = listOf(packet.text1, packet.text2, packet.text3, packet.text4)
        val existing = world.getBlockEntity(pos) as? BlockEntity.Sign
        val entity = if (packet.isFrontText) {
            BlockEntity.Sign(lines, existing?.backRows ?: listOf("", "", "", ""))
        } else {
            BlockEntity.Sign(existing?.frontRows ?: listOf("", "", "", ""), lines)
        }
        world.setBlockEntity(pos, entity)
    }

    private fun toStack(slot: Slot): ItemStack? {
        val itemId = slot.itemId
        if (!slot.present || itemId == null) return null
        return ItemStack(Items.byProtocolId(itemId), slot.itemCount?.toInt() ?: 1, slot.nbtData)
    }

    private fun dropHeld(player: Player, wholeStack: Boolean) {
        val index = 36 + player.selectedSlot
        val held = player.inventory[index] ?: return
        player.inventory[index] = when {
            wholeStack || held.count <= 1 -> null
            else -> ItemStack(held.item, held.count - 1, held.nbt)
        }
    }

    private fun handleDig(player: Player, packet: ServerboundBlockDigPacket) {
        val world = worldFor(player)
        val interaction = interactionFor(player)
        val pos = BlockPos(packet.location.x, packet.location.y, packet.location.z)
        if (packet.status == 3 || packet.status == 4) {
            dropHeld(player, wholeStack = packet.status == 4)
            return
        }
        queueBlockAck(player, packet.sequence)
        when (packet.status) {
            0 -> {
                val held = player.heldItem
                if (held != null && held.item.name == "minecraft:wooden_axe") {
                    commands.worldEdit.setPositionOne(player, pos)
                    player.connection.send(
                        ClientboundBlockChangePacket(Position(pos.x, pos.z, pos.y), world.getBlock(pos))
                    )
                    return
                }
                val state = world.getBlock(pos)
                if (state != Blocks.airState) interaction.destroy(state, world, pos)
            }
        }
    }

    private fun handlePlace(player: Player, packet: ServerboundBlockPlacePacket) {
        if (packet.hand != 0) return
        val world = worldFor(player)
        val interaction = interactionFor(player)
        val pos = BlockPos(packet.location.x, packet.location.y, packet.location.z)
        queueBlockAck(player, packet.sequence)
        val held = player.heldItem

        if (held != null && held.item.name == "minecraft:wooden_axe") {
            commands.worldEdit.setPositionTwo(player, pos)
            return
        }

        val context = UseOnBlockContext(
            blockPos = pos,
            blockFace = BlockFace.fromId(packet.direction),
            cursorY = packet.cursorY,
            yaw = player.yaw,
            pitch = player.pitch,
            crouching = player.crouching,
            playerPos = player.blockPos,
        )

        val stack = held ?: ItemStack(Items.unknown, 0, null)
        val result = interaction.useItemOnBlock(stack, world, context)
        result.openSignEditorAt?.let { signPos ->
            player.connection.send(
                ClientboundOpenSignEntityPacket(Position(signPos.x, signPos.z, signPos.y), true)
            )
        }
        result.openContainerAt?.let { containerPos ->
            val container = org.kvxd.optraix.world.BlockEntities.ensure(world, containerPos)
            if (container is BlockEntity.Container) {
                ContainerScreens.open(player, containerPos, container)
            }
        }
    }

    private fun playSound(
        runtime: ManagedWorld,
        pos: BlockPos,
        soundId: Int,
        category: Int,
        volume: Float,
        pitch: Float,
    ) {
        val packet = ClientboundSoundEffectPacket(
            sound = ItemSoundHolder(soundId + 1, null),
            soundCategory = SoundSource.entries.getOrElse(category) { SoundSource.Record },
            x = pos.x * 8,
            y = pos.y * 8,
            z = pos.z * 8,
            volume = volume,
            pitch = pitch,
            seed = 0L,
        )
        sendToChunk(runtime, chunkKey(pos.x shr 4, pos.z shr 4), packet)
    }

    companion object {
        const val PlayerEntityTypeId = 124
        const val KeepAliveIntervalMillis = 5_000L
        const val SelectionOutlineIntervalMillis = 1_000L
        const val RecompileDelayMillis = 3_000L
        const val PlateReleaseMillis = 1_000L
        const val PublishIntervalNanos = 50_000_000L
        const val SidebarIntervalMillis = 500L
        const val WaitForChunksReason: Short = 13
        const val BatchTargetNanos = 500_000L
        const val MaxBatch = 65_536
        const val ShutdownJoinMillis = 5_000L
        const val DisconnectTimeoutMillis = 1_000L
        const val ShutdownReason = "Server closed"
    }
}
