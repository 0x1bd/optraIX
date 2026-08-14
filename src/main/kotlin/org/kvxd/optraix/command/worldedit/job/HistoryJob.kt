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

internal class HistoryJob(
    worldEdit: WorldEdit,
    id: Long,
    player: Player,
    private val redo: Boolean,
    progress: (String) -> Unit,
    completion: (EditOutcome) -> Unit,
) : EditJob(worldEdit, id, player, if (redo) "redo" else "undo", progress, completion) {
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

