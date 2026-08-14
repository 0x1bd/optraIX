package org.kvxd.optraix.command.worldedit.job

import org.kvxd.optraix.command.worldedit.EditOutcome
import org.kvxd.optraix.command.worldedit.WorldEdit
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.redstone.mutation.WorldMutationContext
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.worldedit.Region
import org.kvxd.optraix.worldedit.clipboard.Clipboard
import org.kvxd.optraix.worldedit.history.UndoRecord

internal abstract class EditJob(
    protected val worldEdit: WorldEdit,
    val id: Long,
    val player: Player,
    val name: String,
    private val progress: (String) -> Unit,
    private val completion: (EditOutcome) -> Unit,
) {
    protected val server get() = worldEdit.server
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

    protected fun regionPosition(region: Region, cursor: Long): BlockPos =
        worldEdit.regionPosition(region, cursor)

    protected fun restore(mutation: WorldMutationContext, record: UndoRecord) =
        worldEdit.restore(mutation, record)

    protected fun mutationOptions(changeCount: Long) = worldEdit.mutationOptions(changeCount)

    protected fun resendChunks(player: Player, chunks: Collection<Long>) =
        worldEdit.resendChunks(player, chunks)

    protected fun refreshSelectionAfterMove(player: Player, region: Region, shift: BlockPos) =
        worldEdit.refreshSelectionAfterMove(player, region, shift)

    protected fun refresh(player: Player, mutation: WorldMutationContext, positions: LongArray, size: Int) =
        worldEdit.refresh(player, mutation, positions, size)

    protected fun unitOffset(facing: org.kvxd.optraix.block.property.BlockFacing) = worldEdit.unitOffset(facing)

    protected fun regionOffset(facing: org.kvxd.optraix.block.property.BlockFacing, region: Region) =
        worldEdit.regionOffset(facing, region)

    protected fun chunkKey(x: Int, z: Int): Long =
        (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    protected fun entitiesEstimate(clipboard: Clipboard): Int = clipboard.blockEntities.size

    protected companion object {
        const val MaxRefreshedBlocks = 250_000
        const val MaxEditEntriesPerSlice = 16_384
        const val InitialClipboardCapacity = 1_048_576
        const val ProgressIntervalMillis = 1_000L
    }
}
