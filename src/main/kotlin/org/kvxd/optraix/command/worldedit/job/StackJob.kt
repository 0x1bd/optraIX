package org.kvxd.optraix.command.worldedit.job

import java.io.File
import kotlin.math.min
import org.kvxd.optraix.block.property.BlockFacing
import org.kvxd.optraix.command.worldedit.EditOutcome
import org.kvxd.optraix.command.worldedit.WorldEdit
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.redstone.mutation.WorldMutationContext
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.worldedit.Region
import org.kvxd.optraix.worldedit.clipboard.Clipboard
import org.kvxd.optraix.worldedit.clipboard.SparseClipboardBuilder
import org.kvxd.optraix.worldedit.history.ChangeJournal
import org.kvxd.optraix.worldedit.history.UndoEntry
import org.kvxd.optraix.worldedit.history.positionArray

internal class StackJob(
    worldEdit: WorldEdit,
    id: Long,
    player: Player,
    private val region: Region,
    private val count: Int,
    facing: BlockFacing,
    progress: (String) -> Unit,
    completion: (EditOutcome) -> Unit,
) : EditJob(worldEdit, id, player, "stack", progress, completion) {
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

