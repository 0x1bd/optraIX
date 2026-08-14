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

internal class PasteJob(
    worldEdit: WorldEdit,
    id: Long,
    player: Player,
    private val clipboard: Clipboard,
    private val includeAir: Boolean,
    progress: (String) -> Unit,
    completion: (EditOutcome) -> Unit,
) : EditJob(worldEdit, id, player, "paste", progress, completion) {
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

