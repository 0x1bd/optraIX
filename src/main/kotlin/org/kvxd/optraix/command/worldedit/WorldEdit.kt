package org.kvxd.optraix.command.worldedit

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.property.BlockFacing
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

class WorldEdit(private val server: OptraIxServer) {

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
        val job = PasteJob(nextJobId++, player, clipboard, includeAir, progress, completion)
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

    private abstract inner class EditJob(
        val id: Long,
        val player: Player,
        val name: String,
        private val progress: (String) -> Unit,
        private val completion: (EditOutcome) -> Unit,
    ) {
        var cancelled = false
        private var lastProgressAt = 0L
        private var failure: String? = null

        abstract fun start()
        abstract fun advance(deadline: Long): EditOutcome?
        open fun discard() = Unit

        fun report(processed: Long, total: Long, changed: Int) {
            val now = System.currentTimeMillis()
            if (now - lastProgressAt < ProgressIntervalMillis) return
            lastProgressAt = now
            val percent = if (total == 0L) 100 else (processed * 100 / total).toInt()
            val message = "$name #$id: $percent% ($changed changed)"
            server.runtimeFor(player).redstoneProgress = message
            progress(message)
        }

        fun complete(outcome: EditOutcome) {
            completion(outcome)
        }

        fun queued() {
            progress("$name #$id queued")
        }

        fun fail(cause: Throwable) {
            failure = cause.message ?: cause::class.simpleName ?: "edit failed"
            cancelled = true
        }

        fun finalOutcome(outcome: EditOutcome): EditOutcome {
            val message = failure ?: return outcome
            return if (outcome is EditOutcome.Cancelled) EditOutcome.Failed(message) else outcome
        }
    }

    fun submitApply(
        player: Player,
        region: Region,
        name: String,
        mutator: (BlockPos) -> Int?,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ): EditSubmission {
        val job = ApplyJob(nextJobId++, player, region, name, mutator, progress, completion)
        val runtime = server.runtimeFor(player)
        if (activeJobs.containsKey(runtime)) {
            waitingJobs.getOrPut(runtime, ::ArrayDeque).addLast(job)
            progress("$name #${job.id} queued")
            return EditSubmission(job.id, completed = false)
        }
        start(runtime, job)
        advance(job, System.nanoTime() + InitialEditNanos)
        return EditSubmission(job.id, completed = !activeJobs.containsValue(job))
    }

    fun submitCopy(
        player: Player,
        region: Region,
        cut: Boolean,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ): EditSubmission = submit(CopyJob(nextJobId++, player, region, cut, progress, completion))

    fun submitCount(
        player: Player,
        region: Region,
        predicate: (Int) -> Boolean,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ): EditSubmission = submit(CountJob(nextJobId++, player, region, predicate, progress, completion))

    fun submitStack(
        player: Player,
        region: Region,
        count: Int,
        facing: BlockFacing,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ): EditSubmission = submit(StackJob(nextJobId++, player, region, count, facing, progress, completion))

    fun submitMove(
        player: Player,
        region: Region,
        count: Int,
        facing: BlockFacing,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ): EditSubmission = submit(MoveJob(nextJobId++, player, region, count, facing, progress, completion))

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

    fun submitHistory(
        player: Player,
        redo: Boolean,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ): EditSubmission? {
        val source = if (redo) player.redoStack else player.undoStack
        if (source.isEmpty()) return null
        val job = HistoryJob(nextJobId++, player, redo, progress, completion)
        val runtime = server.runtimeFor(player)
        if (activeJobs.containsKey(runtime)) {
            waitingJobs.getOrPut(runtime, ::ArrayDeque).addLast(job)
            progress("${job.name} #${job.id} queued")
            return EditSubmission(job.id, completed = false)
        }
        start(runtime, job)
        advance(job, System.nanoTime() + InitialEditNanos)
        return EditSubmission(job.id, completed = !activeJobs.containsValue(job))
    }

    private inner class CountJob(
        id: Long,
        player: Player,
        private val region: Region,
        private val predicate: (Int) -> Boolean,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ) : EditJob(id, player, "count", progress, completion) {
        private val world = server.worldFor(player)
        private var cursor = 0L
        private var matches = 0

        override fun start() = Unit

        override fun advance(deadline: Long): EditOutcome? {
            if (cancelled) return EditOutcome.Cancelled(0)
            var sliceEntries = 0
            while (cursor < region.volume && sliceEntries < MaxEditEntriesPerSlice && System.nanoTime() < deadline) {
                if (predicate(world.getBlock(regionPosition(region, cursor)))) matches++
                cursor++
                sliceEntries++
            }
            report(cursor, region.volume, matches)
            return if (cursor == region.volume) EditOutcome.Completed(matches) else null
        }
    }

    private inner class CopyJob(
        id: Long,
        player: Player,
        private val region: Region,
        private val cut: Boolean,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ) : EditJob(id, player, if (cut) "cut" else "copy", progress, completion) {
        private val world = server.worldFor(player)
        private val builder = SparseClipboardBuilder(min(region.volume, InitialClipboardCapacity.toLong()).toInt())
        private val blockEntities = HashMap<Int, org.kvxd.optraix.world.BlockEntity>()
        private val changedChunks = HashSet<Long>()
        private val journal = ChangeJournal(
            File(server.config.runDirectory, "tmp/worldedit"),
            if (cut) region.volume.coerceAtMost(Int.MAX_VALUE.toLong()).toInt() else 0,
        )
        private lateinit var mutation: WorldMutationContext
        private var cursor = 0L
        private var changed = 0
        private var history: UndoEntry? = null
        private var rollbackIndex = -1

        override fun start() {
            mutation = server.engineFor(player).beginMutation(world, mutationOptions(region.volume))
        }

        override fun discard() = journal.close()

        override fun advance(deadline: Long): EditOutcome? {
            if (cancelled) return rollback(deadline)
            var sliceEntries = 0
            while (cursor < region.volume && sliceEntries < MaxEditEntriesPerSlice && System.nanoTime() < deadline) {
                val position = regionPosition(region, cursor)
                val index = cursor.toInt()
                val state = mutation.getBlock(position)
                val entity = mutation.getBlockEntity(position)
                builder.add(index, state)
                if (entity != null) blockEntities[index] = entity
                if (cut && (state != Blocks.Air.defaultState || entity != null)) {
                    journal.add(position.asLong(), state, entity)
                    mutation.setBlockSilent(position, Blocks.Air.defaultState)
                    if (entity != null) mutation.deleteBlockEntity(position)
                    changedChunks.add(chunkKey(position.x shr 4, position.z shr 4))
                    changed++
                }
                cursor++
                sliceEntries++
            }
            report(cursor, region.volume, if (cut) changed else cursor.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
            if (cursor < region.volume) return null
            val clipboard = Clipboard.sparse(
                region.sizeX,
                region.sizeY,
                region.sizeZ,
                BlockPos(
                    region.min.x - player.blockPos.x,
                    region.min.y - player.blockPos.y,
                    region.min.z - player.blockPos.z,
                ),
                builder.build(sorted = true),
            )
            clipboard.blockEntities.putAll(blockEntities)
            player.clipboard = clipboard
            if (cut) {
                history = journal.finish()
                history?.let(player::pushUndo)
                resendChunks(player, changedChunks)
            } else {
                journal.close()
            }
            mutation.close()
            return EditOutcome.Completed(if (cut) changed else region.volume.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        }

        private fun rollback(deadline: Long): EditOutcome? {
            if (!cut) {
                mutation.close()
                journal.close()
                return EditOutcome.Cancelled(0)
            }
            if (history == null) {
                history = journal.finish()
                rollbackIndex = history!!.size - 1
            }
            val undo = history ?: return EditOutcome.Cancelled(0)
            while (rollbackIndex >= 0 && System.nanoTime() < deadline) {
                restore(mutation, undo.entryAt(rollbackIndex--))
            }
            if (rollbackIndex >= 0) return null
            mutation.close()
            resendChunks(player, changedChunks)
            val restored = undo.size
            undo.close()
            return EditOutcome.Cancelled(restored)
        }
    }

    private inner class StackJob(
        id: Long,
        player: Player,
        private val region: Region,
        private val count: Int,
        facing: BlockFacing,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ) : EditJob(id, player, "stack", progress, completion) {
        private val world = server.worldFor(player)
        private val step = regionOffset(facing, region)
        private val total = Math.multiplyExact(region.volume, count.toLong())
        private val journal = ChangeJournal(
            File(server.config.runDirectory, "tmp/worldedit"),
            total.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
        private val changedChunks = HashSet<Long>()
        private lateinit var mutation: WorldMutationContext
        private var cursor = 0L
        private var changed = 0
        private var history: UndoEntry? = null
        private var rollbackIndex = -1

        override fun start() {
            mutation = server.engineFor(player).beginMutation(world, mutationOptions(total))
        }

        override fun discard() = journal.close()

        override fun advance(deadline: Long): EditOutcome? {
            if (cancelled) return rollback(deadline)
            var sliceEntries = 0
            while (cursor < total && sliceEntries < MaxEditEntriesPerSlice && System.nanoTime() < deadline) {
                val iteration = (cursor / region.volume + 1).toInt()
                val source = regionPosition(region, cursor % region.volume)
                val target = BlockPos(
                    source.x + step.x * iteration,
                    source.y + step.y * iteration,
                    source.z + step.z * iteration,
                )
                val state = mutation.getBlock(source)
                val entity = mutation.getBlockEntity(source)
                val current = mutation.getBlock(target)
                val currentEntity = mutation.getBlockEntity(target)
                if (current != state || entity != null || currentEntity != null) {
                    journal.add(target.asLong(), current, currentEntity)
                    mutation.setBlockSilent(target, state)
                    if (entity == null) mutation.deleteBlockEntity(target) else mutation.setBlockEntity(target, entity)
                    changedChunks.add(chunkKey(target.x shr 4, target.z shr 4))
                    changed++
                }
                cursor++
                sliceEntries++
            }
            report(cursor, total, changed)
            if (cursor < total) return null
            history = journal.finish()
            mutation.close()
            history?.let(player::pushUndo)
            resendChunks(player, changedChunks)
            return EditOutcome.Completed(changed)
        }

        private fun rollback(deadline: Long): EditOutcome? {
            if (history == null) {
                history = journal.finish()
                rollbackIndex = history!!.size - 1
            }
            val undo = history ?: return EditOutcome.Cancelled(0)
            while (rollbackIndex >= 0 && System.nanoTime() < deadline) restore(mutation, undo.entryAt(rollbackIndex--))
            if (rollbackIndex >= 0) return null
            mutation.close()
            resendChunks(player, changedChunks)
            val restored = undo.size
            undo.close()
            return EditOutcome.Cancelled(restored)
        }
    }

    private inner class MoveJob(
        id: Long,
        player: Player,
        private val region: Region,
        count: Int,
        facing: BlockFacing,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ) : EditJob(id, player, "move", progress, completion) {
        private val world = server.worldFor(player)
        private val unit = unitOffset(facing)
        private val shift = BlockPos(unit.x * count, unit.y * count, unit.z * count)
        private val sourceJournal = ChangeJournal(
            File(server.config.runDirectory, "tmp/worldedit"),
            region.volume.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
        private val undoJournal = ChangeJournal(
            File(server.config.runDirectory, "tmp/worldedit"),
            (region.volume * 2).coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
        private val changedChunks = HashSet<Long>()
        private lateinit var mutation: WorldMutationContext
        private var source: UndoEntry? = null
        private var phase = 0
        private var cursor = 0L
        private var changed = 0
        private var history: UndoEntry? = null
        private var rollbackIndex = -1

        override fun start() {
            mutation = server.engineFor(player).beginMutation(world, mutationOptions(region.volume * 2))
        }

        override fun discard() {
            sourceJournal.close()
            undoJournal.close()
        }

        override fun advance(deadline: Long): EditOutcome? {
            if (cancelled) return rollback(deadline)
            var sliceEntries = 0
            while (sliceEntries < MaxEditEntriesPerSlice && System.nanoTime() < deadline) {
                when (phase) {
                    0 -> {
                        if (cursor == region.volume) {
                            source = sourceJournal.finish()
                            cursor = 0L
                            phase = 1
                            continue
                        }
                        val pos = regionPosition(region, cursor++)
                        sourceJournal.add(pos.asLong(), mutation.getBlock(pos), mutation.getBlockEntity(pos))
                    }
                    1 -> {
                        if (cursor == region.volume) {
                            cursor = 0L
                            phase = 2
                            continue
                        }
                        val record = source!!.entryAt(cursor.toInt())
                        if (record.state != Blocks.Air.defaultState || record.entity != null) {
                            val pos = BlockPos.unpack(record.position)
                            undoJournal.add(record.position, mutation.getBlock(pos), mutation.getBlockEntity(pos))
                            mutation.setBlockSilent(pos, Blocks.Air.defaultState)
                            mutation.deleteBlockEntity(pos)
                            changedChunks.add(chunkKey(pos.x shr 4, pos.z shr 4))
                            changed++
                        }
                        cursor++
                    }
                    2 -> {
                        if (cursor == region.volume) {
                            source?.close()
                            source = null
                            history = undoJournal.finish()
                            mutation.close()
                            history?.let(player::pushUndo)
                            refreshSelectionAfterMove(player, region, shift)
                            resendChunks(player, changedChunks)
                            return EditOutcome.Completed(changed)
                        }
                        val record = source!!.entryAt(cursor.toInt())
                        val sourcePos = BlockPos.unpack(record.position)
                        val target = BlockPos(sourcePos.x + shift.x, sourcePos.y + shift.y, sourcePos.z + shift.z)
                        val current = mutation.getBlock(target)
                        val currentEntity = mutation.getBlockEntity(target)
                        if (current != record.state || record.entity != null || currentEntity != null) {
                            undoJournal.add(target.asLong(), current, currentEntity)
                            mutation.setBlockSilent(target, record.state)
                            if (record.entity == null) mutation.deleteBlockEntity(target)
                            else mutation.setBlockEntity(target, record.entity)
                            changedChunks.add(chunkKey(target.x shr 4, target.z shr 4))
                            changed++
                        }
                        cursor++
                    }
                }
                sliceEntries++
            }
            report(phase * region.volume + cursor, region.volume * 3, changed)
            return null
        }

        private fun rollback(deadline: Long): EditOutcome? {
            if (phase == 0) {
                mutation.close()
                sourceJournal.close()
                undoJournal.close()
                return EditOutcome.Cancelled(0)
            }
            if (history == null) {
                history = undoJournal.finish()
                rollbackIndex = history!!.size - 1
            }
            val undo = history ?: return EditOutcome.Cancelled(0)
            while (rollbackIndex >= 0 && System.nanoTime() < deadline) restore(mutation, undo.entryAt(rollbackIndex--))
            if (rollbackIndex >= 0) return null
            mutation.close()
            source?.close()
            resendChunks(player, changedChunks)
            val restored = undo.size
            undo.close()
            return EditOutcome.Cancelled(restored)
        }
    }

    private inner class HistoryJob(
        id: Long,
        player: Player,
        private val redo: Boolean,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ) : EditJob(id, player, if (redo) "redo" else "undo", progress, completion) {
        private val world = server.worldFor(player)
        private val changedChunks = HashSet<Long>()
        private lateinit var mutation: WorldMutationContext
        private lateinit var source: UndoEntry
        private lateinit var inverse: ChangeJournal
        private var index = -1
        private var restored = 0
        private var rollback: UndoEntry? = null
        private var rollbackIndex = -1

        override fun start() {
            source = if (redo) player.redoStack.poll() else player.undoStack.poll()
                ?: error("history became empty")
            inverse = ChangeJournal(File(server.config.runDirectory, "tmp/worldedit"), source.size)
            index = source.size - 1
            mutation = server.engineFor(player).beginMutation(world, mutationOptions(source.size.toLong()))
        }

        override fun advance(deadline: Long): EditOutcome? {
            if (cancelled) return rollback(deadline)
            var sliceEntries = 0
            while (index >= 0 && sliceEntries < MaxEditEntriesPerSlice && System.nanoTime() < deadline) {
                val record = source.entryAt(index--)
                val pos = BlockPos.unpack(record.position)
                inverse.add(record.position, mutation.getBlock(pos), mutation.getBlockEntity(pos))
                mutation.setBlockSilent(pos, record.state)
                if (record.entity == null) mutation.deleteBlockEntity(pos)
                else mutation.setBlockEntity(pos, record.entity)
                changedChunks.add(chunkKey(pos.x shr 4, pos.z shr 4))
                restored++
                sliceEntries++
            }
            report(restored.toLong(), source.size.toLong(), restored)
            if (index >= 0) return null
            mutation.close()
            val result = inverse.finish()
            source.close()
            if (redo) player.pushUndoFromRedo(result) else player.pushRedo(result)
            resendChunks(player, changedChunks)
            return EditOutcome.Completed(restored)
        }

        private fun rollback(deadline: Long): EditOutcome? {
            if (rollback == null) {
                rollback = inverse.finish()
                rollbackIndex = rollback!!.size - 1
            }
            val history = rollback ?: return EditOutcome.Cancelled(0)
            while (rollbackIndex >= 0 && System.nanoTime() < deadline) {
                val record = history.entryAt(rollbackIndex--)
                val pos = BlockPos.unpack(record.position)
                mutation.setBlockSilent(pos, record.state)
                if (record.entity == null) mutation.deleteBlockEntity(pos)
                else mutation.setBlockEntity(pos, record.entity)
            }
            if (rollbackIndex >= 0) return null
            mutation.close()
            history.close()
            if (redo) player.pushRedo(source) else player.pushUndoFromRedo(source)
            resendChunks(player, changedChunks)
            return EditOutcome.Cancelled(restored)
        }
    }

    private inner class ApplyJob(
        id: Long,
        player: Player,
        private val region: Region,
        name: String,
        private val mutator: (BlockPos) -> Int?,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ) : EditJob(id, player, name, progress, completion) {
        private val world = server.worldFor(player)
        private val journal = ChangeJournal(
            File(server.config.runDirectory, "tmp/worldedit"),
            region.volume.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
        )
        private val changedChunks = HashSet<Long>()
        private lateinit var mutation: WorldMutationContext
        private var cursor = 0L
        private var changed = 0
        private var history: UndoEntry? = null
        private var rollbackIndex = -1

        override fun start() {
            mutation = server.engineFor(player).beginMutation(world, mutationOptions(region.volume))
        }

        override fun discard() = journal.close()

        override fun advance(deadline: Long): EditOutcome? {
            if (cancelled) return rollback(deadline)
            var sliceEntries = 0
            while (
                cursor < region.volume &&
                sliceEntries < MaxEditEntriesPerSlice &&
                System.nanoTime() < deadline
            ) {
                val x = (cursor % region.sizeX).toInt()
                val z = ((cursor / region.sizeX) % region.sizeZ).toInt()
                val y = (cursor / (region.sizeX.toLong() * region.sizeZ)).toInt()
                val pos = BlockPos(region.min.x + x, region.min.y + y, region.min.z + z)
                val state = mutator(pos)
                if (state != null) {
                    val current = mutation.getBlock(pos)
                    if (current != state) {
                        journal.add(pos.asLong(), current, mutation.getBlockEntity(pos))
                        if (mutation.setBlockSilent(pos, state)) {
                            changedChunks.add(chunkKey(pos.x shr 4, pos.z shr 4))
                            changed++
                        }
                    }
                }
                cursor++
                sliceEntries++
            }
            report(cursor, region.volume, changed)
            if (cursor < region.volume) return null
            history = journal.finish()
            mutation.close()
            history?.let(player::pushUndo)
            resendChunks(player, changedChunks)
            return EditOutcome.Completed(changed)
        }

        private fun rollback(deadline: Long): EditOutcome? {
            if (history == null) {
                history = journal.finish()
                rollbackIndex = history!!.size - 1
            }
            val undo = history ?: return EditOutcome.Cancelled(0)
            while (rollbackIndex >= 0 && System.nanoTime() < deadline) {
                val record = undo.entryAt(rollbackIndex--)
                val pos = BlockPos.unpack(record.position)
                mutation.setBlockSilent(pos, record.state)
                if (record.entity == null) mutation.deleteBlockEntity(pos)
                else mutation.setBlockEntity(pos, record.entity)
            }
            if (rollbackIndex >= 0) return null
            mutation.close()
            resendChunks(player, changedChunks)
            undo.close()
            return EditOutcome.Cancelled(undo.size)
        }
    }

    private inner class PasteJob(
        id: Long,
        player: Player,
        private val clipboard: Clipboard,
        private val includeAir: Boolean,
        progress: (String) -> Unit,
        completion: (EditOutcome) -> Unit,
    ) : EditJob(id, player, "paste", progress, completion) {
        private val world = server.worldFor(player)
        private val origin = player.blockPos
        private val base = BlockPos(
            origin.x + clipboard.offset.x,
            origin.y + clipboard.offset.y,
            origin.z + clipboard.offset.z,
        )
        private val entries = clipboard.pasteEntryCount(includeAir)
        private val journal = ChangeJournal(
            File(server.config.runDirectory, "tmp/worldedit"),
            (entries.toLong() + entitiesEstimate(clipboard))
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt(),
        )
        private val changedChunks = HashSet<Long>()
        private val entities = clipboard.blockEntities.entries.toList()
        private lateinit var mutation: WorldMutationContext
        private var entry = 0
        private var entity = 0
        private var changed = 0
        private var mutationClosed = false
        private var resendIterator: Iterator<Long>? = null
        private var undoEntry: UndoEntry? = null
        private var rollbackIndex = -1

        override fun start() {
            mutation = server.engineFor(player).beginMutation(world, mutationOptions(entries.toLong()))
        }

        override fun discard() = journal.close()

        override fun advance(deadline: Long): EditOutcome? {
            if (cancelled) return rollback(deadline)
            var sliceEntries = 0
            while (
                entry < entries &&
                sliceEntries < MaxEditEntriesPerSlice &&
                System.nanoTime() < deadline
            ) {
                val index = clipboard.pastePosition(entry, includeAir)
                val state = clipboard.pasteState(entry, includeAir)
                val x = index % clipboard.sizeX
                val z = (index / clipboard.sizeX) % clipboard.sizeZ
                val y = index / (clipboard.sizeX * clipboard.sizeZ)
                val pos = BlockPos(base.x + x, base.y + y, base.z + z)
                val current = mutation.getBlock(pos)
                if (current != state) {
                    journal.add(pos.asLong(), current, mutation.getBlockEntity(pos))
                    if (mutation.setBlockSilent(pos, state)) {
                        changedChunks.add(chunkKey(pos.x shr 4, pos.z shr 4))
                        changed++
                    }
                }
                entry++
                sliceEntries++
            }
            report(entry.toLong(), entries.toLong(), changed)
            if (entry < entries) return null

            while (entity < entities.size && System.nanoTime() < deadline) {
                val (index, blockEntity) = entities[entity++]
                val x = index % clipboard.sizeX
                val z = (index / clipboard.sizeX) % clipboard.sizeZ
                val y = index / (clipboard.sizeX * clipboard.sizeZ)
                val pos = BlockPos(base.x + x, base.y + y, base.z + z)
                journal.add(pos.asLong(), mutation.getBlock(pos), mutation.getBlockEntity(pos))
                mutation.setBlockEntity(pos, blockEntity)
            }
            if (entity < entities.size) return null

            if (!mutationClosed) {
                mutationClosed = true
                undoEntry = journal.finish()
                val positions = if ((undoEntry?.size ?: 0) <= MaxRefreshedBlocks) {
                    undoEntry?.positionArray() ?: LongArray(0)
                } else {
                    LongArray(0)
                }
                refresh(player, mutation, positions, positions.size)
                mutation.close()
                undoEntry?.let(player::pushUndo)
                resendIterator = changedChunks.iterator()
            }

            val iterator = resendIterator
            if (iterator != null) {
                while (iterator.hasNext() && System.nanoTime() < deadline) {
                    resendChunks(player, listOf(iterator.next()))
                }
                if (iterator.hasNext()) return null
            }
            return EditOutcome.Completed(changed)
        }

        private fun rollback(deadline: Long): EditOutcome? {
            if (undoEntry == null) {
                undoEntry = journal.finish()
                rollbackIndex = undoEntry!!.size - 1
            }
            val history = undoEntry ?: return EditOutcome.Cancelled(0)
            while (rollbackIndex >= 0 && System.nanoTime() < deadline) {
                val record = history.entryAt(rollbackIndex--)
                val pos = BlockPos.unpack(record.position)
                mutation.setBlockSilent(pos, record.state)
                if (record.entity == null) mutation.deleteBlockEntity(pos)
                else mutation.setBlockEntity(pos, record.entity)
            }
            if (rollbackIndex >= 0) return null
            if (!mutationClosed) {
                mutationClosed = true
                mutation.close()
            }
            resendChunks(player, changedChunks)
            history.close()
            return EditOutcome.Cancelled(history.size)
        }
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

    private fun refresh(player: Player, mutation: WorldMutationContext, positions: LongArray, size: Int) {
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

    private fun refreshSelectionAfterMove(player: Player, region: Region, shift: BlockPos) {
        player.selectionOne = BlockPos(region.min.x + shift.x, region.min.y + shift.y, region.min.z + shift.z)
        player.selectionTwo = BlockPos(region.max.x + shift.x, region.max.y + shift.y, region.max.z + shift.z)
    }

    private fun regionPosition(region: Region, cursor: Long): BlockPos {
        val x = (cursor % region.sizeX).toInt()
        val z = ((cursor / region.sizeX) % region.sizeZ).toInt()
        val y = (cursor / (region.sizeX.toLong() * region.sizeZ)).toInt()
        return BlockPos(region.min.x + x, region.min.y + y, region.min.z + z)
    }

    private fun restore(mutation: WorldMutationContext, record: org.kvxd.optraix.worldedit.history.UndoRecord) {
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

    private fun mutationOptions(changeCount: Long): WorldMutationOptions = WorldMutationOptions(
        recompilePolicy = RecompilePolicy.Automatic,
    )

    private companion object {
        const val MaxRefreshedBlocks = 250_000
        const val InitialEditNanos = 2_000_000L
        const val MaxEditNanos = 8_000_000L
        const val ProgressIntervalMillis = 1_000L
        const val MaxEditEntriesPerSlice = 16_384
        const val InitialClipboardCapacity = 1_048_576

        fun chunkKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

        fun entitiesEstimate(clipboard: Clipboard): Int = clipboard.blockEntities.size
    }

    private fun resendChunks(player: Player, chunks: Collection<Long>) {
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
