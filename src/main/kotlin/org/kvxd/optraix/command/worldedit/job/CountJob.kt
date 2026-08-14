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

internal class CountJob(
    worldEdit: WorldEdit,
    id: Long,
    player: Player,
    private val region: Region,
    private val predicate: (Int) -> Boolean,
    progress: (String) -> Unit,
    completion: (EditOutcome) -> Unit,
) : EditJob(worldEdit, id, player, "count", progress, completion) {
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

