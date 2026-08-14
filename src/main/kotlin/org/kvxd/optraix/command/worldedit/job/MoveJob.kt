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

internal class MoveJob(
    worldEdit: WorldEdit,
    id: Long,
    player: Player,
    private val region: Region,
    count: Int,
    facing: BlockFacing,
    progress: (String) -> Unit,
    completion: (EditOutcome) -> Unit,
) : EditJob(worldEdit, id, player, "move", progress, completion) {
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

