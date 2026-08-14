package org.kvxd.optraix.command.worldedit

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.property.BlockFacing
import org.kvxd.optraix.command.worldedit.job.ApplyJob
import org.kvxd.optraix.command.worldedit.job.CopyJob
import org.kvxd.optraix.command.worldedit.job.CountJob
import org.kvxd.optraix.command.worldedit.job.EditJob
import org.kvxd.optraix.command.worldedit.job.HistoryJob
import org.kvxd.optraix.command.worldedit.job.MoveJob
import org.kvxd.optraix.command.worldedit.job.PasteJob
import org.kvxd.optraix.command.worldedit.job.StackJob
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.net.ChunkPackets
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.redstone.mutation.RecompilePolicy
import org.kvxd.optraix.redstone.mutation.WorldMutationContext
import org.kvxd.optraix.redstone.mutation.WorldMutationOptions
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.worldedit.clipboard.Clipboard
import org.kvxd.optraix.worldedit.clipboard.SparseClipboardBuilder
import org.kvxd.optraix.worldedit.Region
import org.kvxd.optraix.worldedit.history.UndoAccumulator
import org.kvxd.optraix.worldedit.history.UndoEntry
import org.kvxd.optraix.worldedit.history.ChangeJournal
import org.kvxd.optraix.worldedit.history.positionArray
import org.kvxd.optraix.world.management.ManagedWorld
import java.io.File
import java.util.ArrayDeque
import kotlin.math.min

class WorldEdit(internal val server: OptraIxServer) {

    private val activeJobs = LinkedHashMap<ManagedWorld, EditJob>()
    private val waitingJobs = HashMap<ManagedWorld, ArrayDeque<EditJob>>()
    private var nextJobId = 1L

    init {
        File(server.config.runDirectory, "tmp/worldedit").listFiles()
            ?.filter { it.name.startsWith("worldedit-") && it.extension == "journal" }
            ?.forEach(File::delete)
    }

    fun tickJobs() {
        if (activeJobs.isEmpty()) return
        val tickNanos = if (server.targetTps > 0) 1_000_000_000L / server.targetTps else MaxEditNanos
        val deadline = System.nanoTime() + min(MaxEditNanos, tickNanos / 4)
        for (job in activeJobs.values.toList()) {
            if (System.nanoTime() >= deadline) break
            advance(job, deadline)
        }
    }

    fun cancel(player: Player): Boolean {
        val runtime = server.runtimeFor(player)
        val active = activeJobs[runtime]
        if (active != null && active.player === player) {
            active.cancelled = true
            return true
        }
        val waiting = waitingJobs[runtime] ?: return false
        val iterator = waiting.iterator()
        while (iterator.hasNext()) {
            val job = iterator.next()
            if (job.player !== player) continue
            iterator.remove()
            job.discard()
            job.complete(EditOutcome.Cancelled(0))
            return true
        }
        return false
    }

    fun shutdownJobs() {
        for (job in activeJobs.values) job.cancelled = true
        for (queue in waitingJobs.values) {
            while (queue.isNotEmpty()) {
                val job = queue.removeFirst()
                job.discard()
                job.complete(EditOutcome.Cancelled(0))
            }
        }
        waitingJobs.clear()
        while (activeJobs.isNotEmpty()) {
            for (job in activeJobs.values.toList()) advance(job, Long.MAX_VALUE)
        }
    }

    fun submitPaste(
        player: Player,
        clipboard: Clipboard,
        includeAir: Boolean,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ): EditSubmission {
        val job = PasteJob(this, nextJobId++, player, clipboard, includeAir, progress, completion)
        val runtime = server.runtimeFor(player)
        if (activeJobs.containsKey(runtime)) {
            waitingJobs.getOrPut(runtime, ::ArrayDeque).addLast(job)
            progress("paste #${job.id} queued")
            return EditSubmission(job.id, completed = false)
        }
        start(runtime, job)
        advance(job, System.nanoTime() + InitialEditNanos)
        return EditSubmission(job.id, completed = !activeJobs.containsValue(job))
    }

    private fun start(runtime: ManagedWorld, job: EditJob) {
        check(server.beginWorldEdit(job.player, job.id))
        activeJobs[runtime] = job
        runCatching { job.start() }.onFailure(job::fail)
    }

    private fun advance(job: EditJob, deadline: Long) {
        val outcome = runCatching { job.advance(deadline) }.getOrElse {
            job.fail(it)
            runCatching { job.advance(deadline) }
                .getOrElse { rollbackFailure ->
                    EditOutcome.Failed(rollbackFailure.message ?: "edit and rollback failed")
                }
        }
        if (outcome == null) return
        finish(job, job.finalOutcome(outcome))
    }

    private fun finish(job: EditJob, outcome: EditOutcome) {
        val runtime = server.runtimeFor(job.player)
        runCatching { job.complete(outcome) }
        val next = waitingJobs[runtime]?.pollFirst()
        if (next != null) {
            runtime.editJobId = next.id
            runtime.editOwner = next.player.uuid
            runtime.redstoneProgress = "starting ${next.name}"
            activeJobs[runtime] = next
            runCatching { next.start() }.onFailure(next::fail)
            if (waitingJobs[runtime]?.isEmpty() == true) waitingJobs.remove(runtime)
            return
        }
        waitingJobs.remove(runtime)
        activeJobs.remove(runtime)
        server.completeWorldEdit(job.player)
    }

    fun submitApply(
        player: Player,
        region: Region,
        name: String,
        mutator: (BlockPos) -> Int?,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ): EditSubmission = submit(ApplyJob(this, nextJobId++, player, region, name, mutator, progress, completion))

    fun submitCopy(
        player: Player,
        region: Region,
        cut: Boolean,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ): EditSubmission = submit(CopyJob(this, nextJobId++, player, region, cut, progress, completion))

    fun submitCount(
        player: Player,
        region: Region,
        predicate: (Int) -> Boolean,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ): EditSubmission = submit(CountJob(this, nextJobId++, player, region, predicate, progress, completion))

    fun submitStack(
        player: Player,
        region: Region,
        count: Int,
        facing: BlockFacing,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ): EditSubmission = submit(StackJob(this, nextJobId++, player, region, count, facing, progress, completion))

    fun submitMove(
        player: Player,
        region: Region,
        count: Int,
        facing: BlockFacing,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ): EditSubmission = submit(MoveJob(this, nextJobId++, player, region, count, facing, progress, completion))

    fun submitHistory(
        player: Player,
        redo: Boolean,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ): EditSubmission? {
        val source = if (redo) player.redoStack else player.undoStack
        if (source.isEmpty()) return null
        return submit(HistoryJob(this, nextJobId++, player, redo, progress, completion))
    }

    private fun submit(job: EditJob): EditSubmission {
        val runtime = server.runtimeFor(job.player)
        if (activeJobs.containsKey(runtime)) {
            waitingJobs.getOrPut(runtime, ::ArrayDeque).addLast(job)
            job.queued()
            return EditSubmission(job.id, completed = false)
        }
        start(runtime, job)
        advance(job, System.nanoTime() + InitialEditNanos)
        return EditSubmission(job.id, completed = !activeJobs.containsValue(job))
    }

    fun regionOf(player: Player): Region? {
        val one = player.selectionOne ?: return null
        val two = player.selectionTwo ?: return null
        return Region(one, two)
    }

    fun setPositionOne(player: Player, pos: BlockPos) {
        player.selectionOne = pos
        player.connection.sendMessage("first position set to $pos${sizeSuffix(player)}")
    }

    fun setPositionTwo(player: Player, pos: BlockPos) {
        player.selectionTwo = pos
        player.connection.sendMessage("second position set to $pos${sizeSuffix(player)}")
    }

    private fun sizeSuffix(player: Player): String {
        val region = regionOf(player) ?: return ""
        return " (${region.volume} blocks)"
    }

    fun apply(player: Player, region: Region, mutator: (BlockPos) -> Int?): Int {
        val world = server.worldFor(player)
        val positions = ArrayList<Long>()
        val previous = ArrayList<Int>()
        var changed = 0
        server.engineFor(player).mutate(world, mutationOptions(region.volume)) {
            region.forEach { pos ->
                val next = mutator(pos) ?: return@forEach
                val current = getBlock(pos)
                if (current == next) return@forEach
                positions.add(pos.asLong())
                previous.add(current)
                setBlockSilent(pos, next)
                changed++
            }
            if (changed > 0) refresh(player, this, positions)
        }
        if (changed > 0) {
            player.pushUndo(UndoEntry(positions.toLongArray(), previous.toIntArray()))
            resend(player, region.min, region.max)
        }
        return changed
    }

    private fun refresh(player: Player, mutation: WorldMutationContext, positions: Collection<Long>) {
        if (positions.isEmpty()) return
        if (positions.size > MaxRefreshedBlocks) return

        val interaction = server.interactionFor(player)
        val engine = server.engineFor(player)

        for (packed in positions) {
            interaction.changeSurroundingBlocks(mutation, BlockPos.unpack(packed))
        }
        for (packed in positions) {
            val pos = BlockPos.unpack(packed)
            val state = mutation.getBlock(pos)
            if (BlockStates.isType(state, Blocks.RedstoneWire)) engine.updateWireNeighbors(mutation, pos)
            else engine.updateSurroundingBlocks(mutation, pos)
        }
    }

    internal fun refresh(player: Player, mutation: WorldMutationContext, positions: LongArray, size: Int) {
        if (size == 0) return
        if (size > MaxRefreshedBlocks) return

        val interaction = server.interactionFor(player)
        val engine = server.engineFor(player)

        for (index in 0 until size) {
            interaction.changeSurroundingBlocks(mutation, BlockPos.unpack(positions[index]))
        }
        for (index in 0 until size) {
            val pos = BlockPos.unpack(positions[index])
            val state = mutation.getBlock(pos)
            if (BlockStates.isType(state, Blocks.RedstoneWire)) engine.updateWireNeighbors(mutation, pos)
            else engine.updateSurroundingBlocks(mutation, pos)
        }
    }

    private fun resend(player: Player, min: BlockPos, max: BlockPos) {
        val runtime = server.runtimeFor(player)
        val world = runtime.world
        for (chunkX in (min.x shr 4)..(max.x shr 4)) {
            for (chunkZ in (min.z shr 4)..(max.z shr 4)) {
                val key = (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)
                val packet = ChunkPackets.encode(
                    world.chunkAt(chunkX, chunkZ),
                    includeSkyLight = server.config.viaversion,
                )
                for (target in server.players) {
                    if (server.runtimeFor(target) === runtime && key in target.loadedChunks) {
                        target.connection.send(packet)
                    }
                }
            }
        }
    }

    fun copy(player: Player, region: Region): Clipboard {
        val world = server.worldFor(player)
        val origin = player.blockPos
        val clipboard = Clipboard(
            sizeX = region.sizeX,
            sizeY = region.sizeY,
            sizeZ = region.sizeZ,
            offset = BlockPos(region.min.x - origin.x, region.min.y - origin.y, region.min.z - origin.z),
            blocks = IntArray(region.volume.toInt()),
        )
        for (y in 0 until region.sizeY) {
            for (z in 0 until region.sizeZ) {
                for (x in 0 until region.sizeX) {
                    val pos = BlockPos(region.min.x + x, region.min.y + y, region.min.z + z)
                    clipboard[x, y, z] = world.getBlock(pos)
                    world.getBlockEntity(pos)?.let {
                        clipboard.blockEntities[clipboard.index(x, y, z)] = it
                    }
                }
            }
        }
        player.clipboard = clipboard
        return clipboard
    }

    fun paste(player: Player, clipboard: Clipboard, includeAir: Boolean): Int {
        val world = server.worldFor(player)
        val origin = player.blockPos
        val base = BlockPos(
            origin.x + clipboard.offset.x,
            origin.y + clipboard.offset.y,
            origin.z + clipboard.offset.z,
        )
        val maximumChanges = if (!includeAir) clipboard.storedBlockCount else clipboard.volume
        val journal = ChangeJournal(
            File(server.config.runDirectory, "tmp/worldedit"),
            (maximumChanges.toLong() + clipboard.blockEntities.size)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt(),
        )
        val changedChunks = HashSet<Long>()
        var undoEntry: UndoEntry? = null
        var changed = 0
        if (maximumChanges > 0) {
            server.engineFor(player).mutate(world, mutationOptions(maximumChanges.toLong())) {
                fun paste(index: Int, state: Int) {
                    val x = index % clipboard.sizeX
                    val z = (index / clipboard.sizeX) % clipboard.sizeZ
                    val y = index / (clipboard.sizeX * clipboard.sizeZ)
                    val pos = BlockPos(base.x + x, base.y + y, base.z + z)
                    val current = getBlock(pos)
                    if (current == state) return
                    journal.add(pos.asLong(), current, getBlockEntity(pos))
                    if (!setBlockSilent(pos, state)) return
                    changedChunks.add(chunkKey(pos.x shr 4, pos.z shr 4))
                    changed++
                }

                if (includeAir) {
                    for (y in 0 until clipboard.sizeY) {
                        for (z in 0 until clipboard.sizeZ) {
                            for (x in 0 until clipboard.sizeX) {
                                paste(clipboard.index(x, y, z), clipboard[x, y, z])
                            }
                        }
                    }
                } else {
                    clipboard.forEachNonAir(::paste)
                }

                for ((index, entity) in clipboard.blockEntities) {
                    val x = index % clipboard.sizeX
                    val z = (index / clipboard.sizeX) % clipboard.sizeZ
                    val y = index / (clipboard.sizeX * clipboard.sizeZ)
                    val pos = BlockPos(base.x + x, base.y + y, base.z + z)
                    journal.add(pos.asLong(), getBlock(pos), getBlockEntity(pos))
                    setBlockEntity(pos, entity)
                }

                if (journal.size > 0) {
                    val entry = journal.finish()
                    undoEntry = entry
                    if (entry.size <= MaxRefreshedBlocks) {
                        val positions = entry.positionArray()
                        refresh(player, this, positions, positions.size)
                    }
                }
            }
        }

        if (undoEntry != null) {
            player.pushUndo(undoEntry)
            resendChunks(player, changedChunks)
        } else {
            journal.close()
        }
        return changed
    }

    fun stack(player: Player, region: Region, count: Int, facing: BlockFacing): Int {
        val world = server.worldFor(player)
        val step = regionOffset(facing, region)
        val positions = ArrayList<Long>()
        val previous = ArrayList<Int>()
        var changed = 0
        server.engineFor(player).mutate(world, mutationOptions(region.volume * count)) {
            for (iteration in 1..count) {
                val shift = BlockPos(step.x * iteration, step.y * iteration, step.z * iteration)
                region.forEach { pos ->
                    val state = getBlock(pos)
                    val target = BlockPos(pos.x + shift.x, pos.y + shift.y, pos.z + shift.z)
                    val current = getBlock(target)
                    if (current != state) {
                        positions.add(target.asLong())
                        previous.add(current)
                        setBlockSilent(target, state)
                        changed++
                    }
                    getBlockEntity(pos)?.let { setBlockEntity(target, it) }
                }
            }
            if (changed > 0) refresh(player, this, positions)
        }
        if (changed > 0) {
            player.pushUndo(UndoEntry(positions.toLongArray(), previous.toIntArray()))
            resendSpan(player, region, BlockPos(step.x * count, step.y * count, step.z * count))
        }
        return changed
    }

    fun move(player: Player, region: Region, count: Int, facing: BlockFacing): Int {
        val world = server.worldFor(player)
        val step = unitOffset(facing)
        val shift = BlockPos(step.x * count, step.y * count, step.z * count)
        val positions = ArrayList<Long>()
        val previous = ArrayList<Int>()
        val air = Blocks.Air.defaultState

        server.engineFor(player).mutate(world, mutationOptions(region.volume)) {
            val snapshot = HashMap<Long, Int>()
            region.forEach { pos -> snapshot[pos.asLong()] = getBlock(pos) }

            region.forEach { pos ->
                val current = getBlock(pos)
                if (current != air) {
                    positions.add(pos.asLong())
                    previous.add(current)
                    setBlockSilent(pos, air)
                }
            }
            for ((packed, state) in snapshot) {
                val pos = BlockPos.unpack(packed)
                val target = BlockPos(pos.x + shift.x, pos.y + shift.y, pos.z + shift.z)
                val current = getBlock(target)
                if (current != state) {
                    positions.add(target.asLong())
                    previous.add(current)
                    setBlockSilent(target, state)
                }
            }
            if (positions.isNotEmpty()) refresh(player, this, positions)
        }

        player.pushUndo(UndoEntry(positions.toLongArray(), previous.toIntArray()))
        refreshSelectionAfterMove(player, region, shift)
        resendSpan(player, region, shift)
        return region.volume.toInt()
    }

    internal fun refreshSelectionAfterMove(player: Player, region: Region, shift: BlockPos) {
        player.selectionOne = BlockPos(region.min.x + shift.x, region.min.y + shift.y, region.min.z + shift.z)
        player.selectionTwo = BlockPos(region.max.x + shift.x, region.max.y + shift.y, region.max.z + shift.z)
    }

    internal fun regionPosition(region: Region, cursor: Long): BlockPos {
        val x = (cursor % region.sizeX).toInt()
        val z = ((cursor / region.sizeX) % region.sizeZ).toInt()
        val y = (cursor / (region.sizeX.toLong() * region.sizeZ)).toInt()
        return BlockPos(region.min.x + x, region.min.y + y, region.min.z + z)
    }

    internal fun restore(mutation: WorldMutationContext, record: org.kvxd.optraix.worldedit.history.UndoRecord) {
        val pos = BlockPos.unpack(record.position)
        mutation.setBlockSilent(pos, record.state)
        if (record.entity == null) mutation.deleteBlockEntity(pos) else mutation.setBlockEntity(pos, record.entity)
    }

    fun undo(player: Player): Int? {
        val entry = player.undoStack.poll() ?: return null
        player.pushRedo(swap(player, entry))
        return entry.size
    }

    fun redo(player: Player): Int? {
        val entry = player.redoStack.poll() ?: return null
        player.pushUndoFromRedo(swap(player, entry))
        return entry.size
    }

    private fun swap(player: Player, entry: UndoEntry): UndoEntry {
        val world = server.worldFor(player)
        if (entry.size == 0) return UndoEntry(LongArray(0), IntArray(0))
        val journal = ChangeJournal(File(server.config.runDirectory, "tmp/worldedit"), entry.size)
        val changedChunks = HashSet<Long>()
        server.engineFor(player).mutate(world, mutationOptions(entry.size.toLong())) {
            for (index in entry.size - 1 downTo 0) {
                val record = entry.entryAt(index)
                val pos = BlockPos.unpack(record.position)
                journal.add(record.position, getBlock(pos), getBlockEntity(pos))
                setBlockSilent(pos, record.state)
                if (record.entity == null) deleteBlockEntity(pos) else setBlockEntity(pos, record.entity)
                changedChunks.add(chunkKey(pos.x shr 4, pos.z shr 4))
            }
            if (entry.size <= MaxRefreshedBlocks) {
                val positions = entry.positionArray()
                refresh(player, this, positions, positions.size)
            }
        }
        resendChunks(player, changedChunks)
        entry.close()
        return journal.finish()
    }

    private fun resendSpan(player: Player, region: Region, shift: BlockPos) {
        resend(
            player,
            BlockPos(
                minOf(region.min.x, region.min.x + shift.x),
                minOf(region.min.y, region.min.y + shift.y),
                minOf(region.min.z, region.min.z + shift.z),
            ),
            BlockPos(
                maxOf(region.max.x, region.max.x + shift.x),
                maxOf(region.max.y, region.max.y + shift.y),
                maxOf(region.max.z, region.max.z + shift.z),
            ),
        )
    }

    fun unitOffset(facing: BlockFacing): BlockPos = when (facing) {
        BlockFacing.North -> BlockPos(0, 0, -1)
        BlockFacing.South -> BlockPos(0, 0, 1)
        BlockFacing.East -> BlockPos(1, 0, 0)
        BlockFacing.West -> BlockPos(-1, 0, 0)
        BlockFacing.Up -> BlockPos(0, 1, 0)
        BlockFacing.Down -> BlockPos(0, -1, 0)
    }

    internal fun mutationOptions(changeCount: Long): WorldMutationOptions = WorldMutationOptions(
        recompilePolicy = RecompilePolicy.Automatic,
    )

    private companion object {
        const val MaxRefreshedBlocks = 250_000
        const val InitialEditNanos = 2_000_000L
        const val MaxEditNanos = 8_000_000L

        fun chunkKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }

    internal fun resendChunks(player: Player, chunks: Collection<Long>) {
        val runtime = server.runtimeFor(player)
        val world = runtime.world
        for (key in chunks) {
            val targets = server.players.filter {
                server.runtimeFor(it) === runtime && key in it.loadedChunks
            }
            if (targets.isEmpty()) continue
            val packet = ChunkPackets.encode(
                world.chunkAt((key shr 32).toInt(), key.toInt()),
                includeSkyLight = server.config.viaversion,
            )
            for (target in targets) target.connection.send(packet)
        }
    }

    fun regionOffset(facing: BlockFacing, region: Region): BlockPos = when (facing) {
        BlockFacing.North -> BlockPos(0, 0, -region.sizeZ)
        BlockFacing.South -> BlockPos(0, 0, region.sizeZ)
        BlockFacing.East -> BlockPos(region.sizeX, 0, 0)
        BlockFacing.West -> BlockPos(-region.sizeX, 0, 0)
        BlockFacing.Up -> BlockPos(0, region.sizeY, 0)
        BlockFacing.Down -> BlockPos(0, -region.sizeY, 0)
    }
}
