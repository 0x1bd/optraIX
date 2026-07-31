package org.kvxd.optraix.world

import org.kvxd.optraix.block.Blocks

interface World {

    fun getBlock(pos: BlockPos): Int

    fun setBlock(pos: BlockPos, state: Int): Boolean

    fun getBlockEntity(pos: BlockPos): BlockEntity?

    fun setBlockEntity(pos: BlockPos, entity: BlockEntity)

    fun deleteBlockEntity(pos: BlockPos)

    fun scheduleTick(pos: BlockPos, delay: Int, priority: TickPriority)

    fun pendingTickAt(pos: BlockPos): Boolean

    fun playSound(pos: BlockPos, soundId: Int, category: Int, volume: Float, pitch: Float) {}

    fun isAir(pos: BlockPos): Boolean = getBlock(pos) == Blocks.airState
}
