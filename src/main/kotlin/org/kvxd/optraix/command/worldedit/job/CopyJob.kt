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

internal class CopyJob(
    worldEdit: WorldEdit,
    id: Long,
    player: Player,
    private val region: Region,
    private val cut: Boolean,
    progress: (String) -> Unit,
    completion: (EditOutcome) -> Unit,
) : EditJob(worldEdit, id, player, if (cut) "cut" else "copy", progress, completion) {
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

