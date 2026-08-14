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

internal class ApplyJob(
    worldEdit: WorldEdit,
    id: Long,
    player: Player,
    private val region: Region,
    name: String,
    private val mutator: (BlockPos) -> Int?,
    progress: (String) -> Unit,
    completion: (EditOutcome) -> Unit,
) : EditJob(worldEdit, id, player, name, progress, completion) {
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

