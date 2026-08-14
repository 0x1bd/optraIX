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
import org.kvxd.optraix.block.ItemStack
import org.kvxd.optraix.block.itemByProtocolId
import org.kvxd.optraix.block.mcData
import org.kvxd.optraix.mcdata.v1_20_4.Items as GeneratedItems
import org.kvxd.optraix.net.viaversion.ViaVersionRuntime
import org.kvxd.optraix.command.CommandRegistry
import org.kvxd.optraix.interaction.Interaction
import org.kvxd.optraix.interaction.UseOnBlockContext
import org.kvxd.optraix.nbt.compoundOf
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.player.PlayerProfileStore
import org.kvxd.optraix.redstone.RedstoneEngine
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockEntityNbt
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.management.DefaultWorldName
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.management.ManagedWorld
import org.kvxd.optraix.world.management.WorldManager
import org.kvxd.optraix.world.WorldStorage
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.generated.Protocols
import org.kvxd.kmcprotocol.network.server.Server
import org.kvxd.kmcprotocol.network.server.ServerSession
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.configuration.clientbound.ClientboundFeatureFlagsPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.configuration.clientbound.ClientboundFinishConfigurationPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.configuration.clientbound.ClientboundRegistryDataPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.configuration.clientbound.ClientboundTagsPacket
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
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundServerDataPacket
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
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundPickItemPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundSetCreativeSlotPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundUpdateSignPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.GameProfile
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.ItemSoundHolder
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.Position
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.Slot
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.SoundSource
import io.ktor.network.sockets.InetSocketAddress
import java.io.File
import org.kvxd.optraix.redstone.optraix.OptraIxEngine
import org.kvxd.optraix.redstone.optraix.OptraIxBuild
import org.kvxd.optraix.redstone.optraix.compiler.CompileWorldSnapshot
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.locks.LockSupport
import kotlin.math.floor
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.status.clientbound.ClientboundServerInfoPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.status.serverbound.ServerboundPingPacket as StatusPingPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.status.clientbound.ClientboundPingPacket as StatusPongPacket
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.world.management.RedstoneMode
import org.kvxd.optraix.world.management.RedstoneStage

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
        File(System.getProperty("java.io.tmpdir"), "optraix-compile").listFiles()
            ?.filter { it.extension == "buffer" }
            ?.forEach(File::delete)
    }

    private val entityIds = AtomicInteger(1)
    private val tasks = ConcurrentLinkedQueue<Runnable>()
    private val compileExecutor = Executors.newSingleThreadExecutor { task ->
        Thread(task, "optraix-compile").apply { isDaemon = true }
    }
    private val protocol = Protocols.requireMinecraftVersion("1.20.4")

    fun submit(task: Runnable) {
        tasks.add(task)
    }

    internal fun runSubmittedTasks() {
        while (true) {
            val task = tasks.poll() ?: break
            runCatching { task.run() }
        }
    }

    fun runtimeFor(player: Player): ManagedWorld = worlds.find(player.worldName) ?: worlds.default

    fun worldFor(player: Player): GameWorld = runtimeFor(player).world

    fun engineFor(player: Player): RedstoneEngine = runtimeFor(player).engine

    fun interactionFor(player: Player): Interaction = runtimeFor(player).interaction

    fun beginWorldEdit(player: Player, jobId: Long): Boolean {
        val runtime = runtimeFor(player)
        if (runtime.editJobId != null || runtime.redstoneFrozen) return false
        runtime.editJobId = jobId
        runtime.editOwner = player.uuid
        runtime.compileTicket++
        runtime.compiling = false
        runtime.redstoneFrozen = true
        runtime.redstoneStage = RedstoneStage.Editing
        runtime.redstoneProgress = "starting"
        (runtime.engine as? OptraIxEngine)?.suspendForTransition(runtime.world)
        return true
    }

    fun completeWorldEdit(player: Player) {
        val runtime = runtimeFor(player)
        runtime.editJobId = null
        runtime.editOwner = null
        runtime.redstoneStage = RedstoneStage.Interpreted
        runtime.redstoneProgress = "edit complete"
        val target = runtime.engine as? OptraIxEngine
        if (running && runtime.desiredMode == RedstoneMode.Compiled && target != null) {
            requestCompile(runtime, target) {}
        } else {
            target?.reconcile(runtime.world)
            runtime.redstoneFrozen = false
        }
        refreshSidebar(force = true)
    }

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

    private var viaVersionRuntime: ViaVersionRuntime? = null

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

        val viaVersion = if (config.viaversion) ViaVersionRuntime.start(this) else null
        viaVersionRuntime = viaVersion
        val middlewares = viaVersion?.let { listOf(it.middleware) }.orEmpty()
        val server = Server.bind(
            address = InetSocketAddress(config.host, config.port),
            middlewares = middlewares,
        ) { protocol.protocolData() }
        socket = server
        boundPort = (server.localAddress as? InetSocketAddress)?.port ?: config.port
        println("optraix listening on ${config.host}:$boundPort (1.20.4, protocol 765)")
        if (viaVersion != null) println("ViaVersion ${ViaVersionRuntime.Version}: enabled")
        println("redstone engine: ${engine.name}, target tps: ${tpsLabel()}")

        for (runtime in worlds.all()) {
            (runtime.engine as? OptraIxEngine)?.let { requestCompile(runtime, it) {} }
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

            commands.shutdownWorldEdit()
            for (runtime in worlds.all()) {
                runtime.compileTicket++
                if (runtime.redstoneFrozen) {
                    (runtime.engine as? OptraIxEngine)?.reconcile(runtime.world)
                    runtime.redstoneFrozen = false
                }
            }
            disconnectPlayers()
            runCatching { socket?.close() }
            networkJob?.cancel()
            runCatching { viaVersionRuntime?.close() }
            viaVersionRuntime = null
            compileExecutor.shutdownNow()
            compileExecutor.awaitTermination(ShutdownJoinMillis, TimeUnit.MILLISECONDS)

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
            if (runtime.editJobId != null || runtime.redstoneFrozen) continue
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

    fun requestCompile(
        player: Player,
        target: OptraIxEngine,
        completion: (Boolean) -> Unit = {},
    ) = requestCompile(runtimeFor(player), target, completion)

    fun requestCompile(
        target: OptraIxEngine,
        completion: (Boolean) -> Unit = {},
    ) = requestCompile(worlds.default, target, completion)

    private fun requestCompile(
        runtime: ManagedWorld,
        target: OptraIxEngine,
        completion: (Boolean) -> Unit,
    ) {
        if (runtime.desiredMode != RedstoneMode.Compiled) {
            completion(false)
            return
        }
        val superseding = runtime.redstoneFrozen
        val ticket = ++runtime.compileTicket
        runtime.compiling = true
        runtime.redstoneFrozen = true
        runtime.redstoneStage =
            if (superseding) RedstoneStage.Queued else RedstoneStage.Reconciling
        runtime.redstoneProgress =
            if (superseding) "cancelling previous transition" else "leaving compiled mode"
        refreshSidebar(force = true)

        val generation = target.mutationCounter
        compileExecutor.execute {
            val result = runCatching {
                if (runtime.compileTicket != ticket) throw InterruptedException("compile cancelled")
                submit {
                    if (runtime.compileTicket == ticket) {
                        runtime.redstoneStage = RedstoneStage.Reconciling
                        runtime.redstoneProgress = "leaving compiled mode"
                    }
                }
                target.resume()
                target.suspendForTransition(runtime.world)
                if (runtime.compileTicket != ticket) throw InterruptedException("compile cancelled")
                submit {
                    if (runtime.compileTicket == ticket) {
                        runtime.redstoneStage = RedstoneStage.Snapshotting
                        runtime.redstoneProgress = "snapshotting"
                    }
                }
                val snapshot = CompileWorldSnapshot.create(runtime.world)
                submit {
                    if (runtime.compileTicket == ticket) {
                        runtime.redstoneFrozen = false
                        runtime.redstoneStage = RedstoneStage.Compiling
                        runtime.redstoneProgress = "compiling; interpreted redstone active"
                        refreshSidebar(force = true)
                    }
                }
                target.build(
                    snapshot,
                    enforceMemoryBudget = true,
                    stageListener = { stage ->
                        submit {
                            if (runtime.compileTicket == ticket) runtime.redstoneProgress = stage
                        }
                    },
                    cancelled = {
                        runtime.compileTicket != ticket ||
                            runtime.desiredMode != RedstoneMode.Compiled ||
                            !running
                    },
                )
            }
            submit {
                finishCompile(runtime, target, ticket, generation, result, completion)
            }
        }
    }

    fun requestPause(
        player: Player,
        target: OptraIxEngine,
        completion: (Boolean) -> Unit = {},
    ) {
        val runtime = runtimeFor(player)
        runtime.desiredMode = RedstoneMode.Interpreted
        val superseding = runtime.redstoneFrozen
        val ticket = ++runtime.compileTicket
        runtime.compiling = false
        runtime.redstoneFrozen = true
        runtime.redstoneStage =
            if (superseding) RedstoneStage.Queued else RedstoneStage.Reconciling
        runtime.redstoneProgress =
            if (superseding) "cancelling previous transition" else "materializing interpreted redstone"
        refreshSidebar(force = true)
        compileExecutor.execute {
            val result = runCatching {
                if (runtime.compileTicket != ticket) throw InterruptedException("pause cancelled")
                submit {
                    if (runtime.compileTicket == ticket) {
                        runtime.redstoneStage = RedstoneStage.Reconciling
                        runtime.redstoneProgress = "materializing interpreted redstone"
                    }
                }
                val completed = target.pauseForTransition(runtime.world) {
                    runtime.compileTicket != ticket ||
                        runtime.desiredMode != RedstoneMode.Interpreted ||
                        !running
                }
                if (!completed) throw InterruptedException("pause cancelled")
            }
            submit {
                if (runtime.compileTicket != ticket) return@submit
                runtime.redstoneFrozen = false
                if (result.isSuccess) {
                    runtime.redstoneStage = RedstoneStage.Interpreted
                    runtime.redstoneProgress = "interpreted"
                    completion(true)
                } else {
                    target.failCompile(result.exceptionOrNull()?.message)
                    runtime.redstoneStage = RedstoneStage.Failed
                    runtime.redstoneProgress = target.lastError ?: "pause failed"
                    completion(false)
                }
                refreshSidebar(force = true)
            }
        }
    }

    private fun finishCompile(
        runtime: ManagedWorld,
        target: OptraIxEngine,
        ticket: Long,
        generation: Long,
        result: Result<OptraIxBuild>,
        completion: (Boolean) -> Unit,
    ) {
        if (runtime.compileTicket != ticket) return
        if (target.mutationCounter != generation) {
            runtime.compiling = false
            runtime.redstoneFrozen = false
            runtime.redstoneStage = RedstoneStage.Interpreted
            runtime.redstoneProgress = "stale; waiting to rebuild"
            runtime.lastMutationCounter = target.mutationCounter
            runtime.lastMutationAt = System.currentTimeMillis()
            completion(false)
            refreshSidebar(force = true)
            return
        }

        val build = result.getOrNull()
        if (build == null) {
            target.failCompile(result.exceptionOrNull()?.message)
            runtime.compiling = false
            runtime.redstoneFrozen = false
            runtime.redstoneStage = RedstoneStage.Failed
            runtime.redstoneProgress = target.lastError ?: "compile failed"
            completion(false)
            refreshSidebar(force = true)
            return
        }

        runtime.redstoneStage = RedstoneStage.Activating
        runtime.redstoneProgress = "activating"
        target.activate(runtime.world, build)
        runtime.compiling = false
        runtime.redstoneFrozen = false
        runtime.redstoneStage = RedstoneStage.Compiled
        runtime.redstoneProgress = "compiled"
        runtime.lastMutationCounter = target.mutationCounter
        runtime.lastMutationAt = 0L
        completion(true)
        println("[optraix:${runtime.name}] compiled ${build.circuit.count} nodes, ${build.circuit.edgeCount} edges in ${build.millis}ms")
        refreshSidebar(force = true)
    }

    private var lastSidebar = 0L

    private fun maintainPressurePlates() {
        val now = System.currentTimeMillis()
        for (player in players) {
            val runtime = runtimeFor(player)
            if (runtime.redstoneFrozen) continue
            val world = runtime.world
            val pos = BlockPos(floor(player.x).toInt(), floor(player.y).toInt(), floor(player.z).toInt())
            if (BlockStates.pressurePlatePowered(world.getBlock(pos)) == null) continue
            if (runtime.plateHeldUntil.put(pos.asLong(), now + PlateReleaseMillis) == null) {
                runtime.engine.setPressurePlate(world, pos, true)
            }
        }
        for (runtime in worlds.all()) {
            if (runtime.redstoneFrozen) continue
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
            if (runtime.desiredMode != RedstoneMode.Compiled || runtime.compiling) continue
            val counter = optraix.mutationCounter
            if (counter != runtime.lastMutationCounter) {
                runtime.lastMutationCounter = counter
                runtime.lastMutationAt = now
                continue
            }
            if (runtime.lastMutationAt == 0L || optraix.compiled) continue
            if (now - runtime.lastMutationAt < RecompileDelayMillis) continue
            runtime.lastMutationAt = 0L
            requestCompile(runtime, optraix) {}
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
                runtime.redstoneStage == RedstoneStage.Failed -> "failed" to Text.Red
                runtime.redstoneFrozen -> runtime.redstoneStage.name.lowercase() to Text.Yellow
                runtime.desiredMode == RedstoneMode.Interpreted -> "interpreted" to Text.Gray
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
        for (runtime in worlds.all()) {
            if (!runtime.redstoneFrozen) runtime.engine.tickWorld(runtime.world)
        }
    }

    private fun nextBatchSize(current: Int, batchNanos: Long): Int {
        if (batchNanos > BatchTargetNanos && current > 1) return maxOf(1, current shr 1)
        if (batchNanos < BatchTargetNanos / 2 && current < MaxBatch) return current shl 1
        return current
    }

    private fun runHousekeeping() {
        runSubmittedTasks()
        commands.tickWorldEdit()
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
            !it.redstoneFrozen &&
                (it.world.changedBlocks.isNotEmpty() || it.world.changedBlockEntities.isNotEmpty())
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

            val blocks = world.changedBlocks.drain()
            val states = IntArray(blocks.size)
            for (index in blocks.indices) {
                val packed = blocks[index]
                states[index] = world.getBlock(BlockPos.unpack(packed))
            }

            val entityKeys = world.changedBlockEntities.drain()
            val entities = arrayOfNulls<BlockEntity>(entityKeys.size)
            for (index in entityKeys.indices) {
                val packed = entityKeys[index]
                entities[index] = world.getBlockEntity(BlockPos.unpack(packed))
            }
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
                        session.send(ClientboundFeatureFlagsPacket(listOf("minecraft:vanilla")))
                        session.send(ClientboundRegistryDataPacket(Registries.codec))
                        session.send(ClientboundTagsPacket(emptyList()))
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

    fun resetWorld(name: String): ManagedWorld? {
        val previous = worlds.find(name) ?: return null
        val occupants = players.filter { runtimeFor(it) === previous }
        val replacement = worlds.reset(previous.name) ?: return null
        configureWorld(replacement)

        for (player in occupants) {
            ContainerScreens.close(player)
            unloadAllChunks(player)
            if (player.pendingBlockAck >= 0) {
                player.pendingBlockAck = -1
                pendingAcks = maxOf(0, pendingAcks - 1)
            }
            player.selectionOne = null
            player.selectionTwo = null
            player.clearHistory()
            player.lastChunkX = Int.MIN_VALUE
            player.lastChunkZ = Int.MIN_VALUE
            player.moved = false
            player.connection.send(ClientboundGameStateChangePacket(WaitForChunksReason, 0.0f))
            updateChunks(player, force = true)
        }

        refreshSidebar(force = true)
        return replacement
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
        player.clearHistory()
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
        connection.send(ClientboundServerDataPacket(Text.of(config.motd), null, false))
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
            else Slot(true, stack.item.id, stack.count.toByte(), stack.nbt)
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
        player.clearHistory()
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
            player.connection.send(
                ChunkPackets.encode(worldFor(player).chunkAt(cx, cz), includeSkyLight = config.viaversion)
            )
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
            is ServerboundPickItemPacket -> pickInventorySlot(player, packet.slot)
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
                    if (runtimeFor(player).redstoneFrozen) return
                    val containerPos = player.openContainer
                    if (containerPos != null && packet.changedSlots.isNotEmpty()) {
                        val engine = engineFor(player)
                        engine.mutate(world) {
                            var changedContainer = false
                            for (changed in packet.changedSlots) {
                                changedContainer = ContainerScreens.applyClick(
                                    this,
                                    player,
                                    changed.location.toInt(),
                                    changed.item,
                                ) || changedContainer
                            }
                            if (changedContainer) engine.updateSurroundingBlocks(this, containerPos)
                        }
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
        if (runtimeFor(player).redstoneFrozen) return
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
        return ItemStack(itemByProtocolId(itemId), slot.itemCount?.toInt() ?: 1, slot.nbtData)
    }

    fun pickItemFromBlock(playerUuid: UUID, pos: BlockPos, includeData: Boolean) {
        val player = players.firstOrNull { it.uuid == playerUuid } ?: return
        val dx = pos.x + 0.5 - player.x
        val dy = pos.y + 0.5 - player.y
        val dz = pos.z + 0.5 - player.z
        if (dx * dx + dy * dy + dz * dz > PickBlockRangeSquared) return

        val world = worldFor(player)
        val blockName = mcData.requireBlockByStateId(world.getBlock(pos)).name
        val itemName = when {
            blockName == "redstone_wire" -> "minecraft:redstone"
            blockName == "redstone_wall_torch" -> "minecraft:redstone_torch"
            blockName.endsWith("_wall_sign") -> blockName.removeSuffix("_wall_sign") + "_sign"
            blockName == "water_cauldron" -> "minecraft:cauldron"
            blockName == "lava_cauldron" -> "minecraft:cauldron"
            blockName == "powder_snow_cauldron" -> "minecraft:cauldron"
            else -> blockName
        }
        val item = mcData.item(itemName) ?: return
        val nbt = if (includeData) {
            world.getBlockEntity(pos)?.let { compoundOf("BlockEntityTag" to BlockEntityNbt.toNbt(it)) }
        } else {
            null
        }
        pickItem(player, ItemStack(item, 1, nbt))
    }

    private fun pickInventorySlot(player: Player, sourceInventorySlot: Int) {
        val sourceSlot = when (sourceInventorySlot) {
            in 0..8 -> 36 + sourceInventorySlot
            in 9..35 -> sourceInventorySlot
            else -> return
        }
        if (sourceSlot in 36..44) {
            player.selectedSlot = sourceSlot - 36
            player.connection.send(ClientboundHeldItemSlotPacket(player.selectedSlot.toByte()))
            return
        }

        val targetSlot = (36..44).firstOrNull { player.inventory[it] == null } ?: (36 + player.selectedSlot)
        val displaced = player.inventory[targetSlot]
        player.inventory[targetSlot] = player.inventory[sourceSlot]
        player.inventory[sourceSlot] = displaced
        player.selectedSlot = targetSlot - 36
        sendInventory(player)
        player.connection.send(ClientboundHeldItemSlotPacket(player.selectedSlot.toByte()))
    }

    private fun pickItem(player: Player, wanted: ItemStack) {
        val sourceSlot = findMatchingStorageSlot(player, wanted)
        if (sourceSlot in 36..44) {
            player.selectedSlot = sourceSlot - 36
            player.connection.send(ClientboundHeldItemSlotPacket(player.selectedSlot.toByte()))
            return
        }

        val targetSlot = (36..44).firstOrNull { player.inventory[it] == null } ?: (36 + player.selectedSlot)
        if (sourceSlot >= 0) {
            val displaced = player.inventory[targetSlot]
            player.inventory[targetSlot] = player.inventory[sourceSlot]
            player.inventory[sourceSlot] = displaced
        } else {
            val displaced = player.inventory[targetSlot]
            if (displaced != null) {
                val emptySlot = (9..35).firstOrNull { player.inventory[it] == null }
                if (emptySlot != null) player.inventory[emptySlot] = displaced
            }
            player.inventory[targetSlot] = wanted
        }

        player.selectedSlot = targetSlot - 36
        sendInventory(player)
        player.connection.send(ClientboundHeldItemSlotPacket(player.selectedSlot.toByte()))
    }

    private fun findMatchingStorageSlot(player: Player, wanted: ItemStack): Int {
        for (slot in 36..44) {
            val existing = player.inventory[slot] ?: continue
            if (samePickedItem(existing, wanted)) return slot
        }
        for (slot in 9..35) {
            val existing = player.inventory[slot] ?: continue
            if (samePickedItem(existing, wanted)) return slot
        }
        return -1
    }

    private fun samePickedItem(first: ItemStack, second: ItemStack): Boolean =
        first.item.id == second.item.id && first.nbt == second.nbt

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
        if (runtimeFor(player).redstoneFrozen) {
            player.connection.send(
                ClientboundBlockChangePacket(Position(pos.x, pos.z, pos.y), world.getBlock(pos))
            )
            return
        }
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
                if (state != Blocks.Air.defaultState) interaction.destroy(state, world, pos)
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
        if (runtimeFor(player).redstoneFrozen) {
            player.connection.send(
                ClientboundBlockChangePacket(Position(pos.x, pos.z, pos.y), world.getBlock(pos))
            )
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

        val stack = held ?: ItemStack(GeneratedItems.Air, 0, null)
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
        const val PickBlockRangeSquared = 30.25
        const val BatchTargetNanos = 500_000L
        const val MaxBatch = 65_536
        const val ShutdownJoinMillis = 5_000L
        const val DisconnectTimeoutMillis = 1_000L
        const val ShutdownReason = "Server closed"
    }
}
