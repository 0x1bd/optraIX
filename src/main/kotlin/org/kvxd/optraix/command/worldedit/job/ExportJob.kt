package org.kvxd.optraix.command.worldedit.job

import kotlin.math.min
import org.kvxd.optraix.command.worldedit.EditOutcome
import org.kvxd.optraix.command.worldedit.WorldEdit
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.worldedit.Region
import org.kvxd.optraix.worldedit.clipboard.Clipboard
import org.kvxd.optraix.worldedit.clipboard.SparseClipboardBuilder

internal class ExportJob(
    worldEdit: WorldEdit,
    id: Long,
    player: Player,
    private val region: Region,
    private val snapshot: (Clipboard) -> Unit,
    progress: (String) -> Unit,
    completion: (EditOutcome) -> Unit,
) : EditJob(worldEdit, id, player, "schematic export", progress, completion) {
    private val world = server.worldFor(player)
    private val origin = player.blockPos
    private val builder = SparseClipboardBuilder(min(region.volume, InitialClipboardCapacity.toLong()).toInt())
    private val blockEntities = HashMap<Int, BlockEntity>()
    private var cursor = 0L

    override fun start() = Unit

    override fun advance(deadline: Long): EditOutcome? {
        if (cancelled) return EditOutcome.Cancelled(0)
        var sliceEntries = 0
        while (cursor < region.volume && sliceEntries < MaxEditEntriesPerSlice && System.nanoTime() < deadline) {
            val position = regionPosition(region, cursor)
            val index = cursor.toInt()
            builder.add(index, world.getBlock(position))
            world.getBlockEntity(position)?.let { blockEntities[index] = it }
            cursor++
            sliceEntries++
        }
        report(cursor, region.volume, cursor.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
        if (cursor < region.volume) return null

        val clipboard = Clipboard.sparse(
            region.sizeX,
            region.sizeY,
            region.sizeZ,
            BlockPos(
                region.min.x - origin.x,
                region.min.y - origin.y,
                region.min.z - origin.z,
            ),
            builder.build(sorted = true),
        )
        clipboard.blockEntities.putAll(blockEntities)
        snapshot(clipboard)
        return EditOutcome.Completed(region.volume.coerceAtMost(Int.MAX_VALUE.toLong()).toInt())
    }
}
