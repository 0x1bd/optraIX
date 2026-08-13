package org.kvxd.optraix.redstone.mutation

import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.TickPriority
import org.kvxd.optraix.world.World

class WorldMutationContext internal constructor(
    internal val backingWorld: World,
    val options: WorldMutationOptions,
) : World by backingWorld, AutoCloseable {

    var blockWrites: Int = 0
        private set

    var blockEntityWrites: Int = 0
        private set

    var scheduledTickWrites: Int = 0
        private set

    private var closed = false

    override fun setBlock(pos: BlockPos, state: Int): Boolean {
        checkOpen()
        val changed = backingWorld.setBlock(pos, state)
        if (changed) blockWrites++
        return changed
    }

    fun setBlockSilent(pos: BlockPos, state: Int): Boolean {
        checkOpen()
        val world = backingWorld as? GameWorld ?: error("silent block writes require a GameWorld")
        val changed = world.setBlockSilent(pos, state)
        if (changed) blockWrites++
        return changed
    }

    override fun setBlockEntity(pos: BlockPos, entity: BlockEntity) {
        checkOpen()
        backingWorld.setBlockEntity(pos, entity)
        blockEntityWrites++
    }

    override fun deleteBlockEntity(pos: BlockPos) {
        checkOpen()
        backingWorld.deleteBlockEntity(pos)
        blockEntityWrites++
    }

    override fun scheduleTick(pos: BlockPos, delay: Int, priority: TickPriority) {
        checkOpen()
        backingWorld.scheduleTick(pos, delay, priority)
        scheduledTickWrites++
    }

    override fun close() {
        closed = true
    }

    private fun checkOpen() {
        check(!closed) { "world mutation transaction is closed" }
    }
}
