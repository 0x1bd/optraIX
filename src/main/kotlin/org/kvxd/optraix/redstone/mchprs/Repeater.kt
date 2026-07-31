package org.kvxd.optraix.redstone.mchprs

import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.BlockFace
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.TickPriority
import org.kvxd.optraix.world.World

object Repeater {

    fun getStateForPlacement(world: World, pos: BlockPos, facing: BlockDirection): Int =
        BlockStates.repeaterState(1, facing, shouldBeLocked(facing, world, pos), false)

    private fun shouldBeLocked(facing: BlockDirection, world: World, pos: BlockPos): Boolean {
        val rightSide = getPowerOnSide(world, pos, facing.rotate())
        val leftSide = getPowerOnSide(world, pos, facing.rotateCcw())
        return maxOf(rightSide, leftSide) > 0
    }

    private fun getPowerOnSide(world: World, pos: BlockPos, side: BlockDirection): Int {
        val sidePos = pos.offset(side.blockFace())
        val sideState = world.getBlock(sidePos)
        return if (MchprsRedstone.isDiode(sideState)) {
            MchprsRedstone.getWeakPower(sideState, world, sidePos, side.blockFace(), false)
        } else {
            0
        }
    }

    private fun onStateChange(facing: BlockDirection, world: World, pos: BlockPos) {
        val frontPos = pos.offset(facing.opposite().blockFace())
        MchprsRedstone.update(world.getBlock(frontPos), world, frontPos)
        for (direction in BlockFace.All) {
            val neighborPos = frontPos.offset(direction)
            MchprsRedstone.update(world.getBlock(neighborPos), world, neighborPos)
        }
    }

    private fun scheduleTick(
        state: Int,
        facing: BlockDirection,
        world: World,
        pos: BlockPos,
        shouldBePowered: Boolean,
    ) {
        val frontState = world.getBlock(pos.offset(facing.opposite().blockFace()))
        val priority = when {
            MchprsRedstone.isDiode(frontState) -> TickPriority.Highest
            !shouldBePowered -> TickPriority.Higher
            else -> TickPriority.High
        }
        world.scheduleTick(pos, BlockStates.delay[state].toInt(), priority)
        MchprsRedstone.stats.scheduledTicks++
    }

    private fun shouldBePowered(facing: BlockDirection, world: World, pos: BlockPos): Boolean =
        MchprsRedstone.diodeGetInputStrength(world, pos, facing) > 0

    fun onNeighborUpdated(state: Int, world: World, pos: BlockPos) {
        val facing = BlockStates.directionOf(state) ?: return
        var current = state
        var locked = BlockStates.locked[current]
        val shouldBeLocked = shouldBeLocked(facing, world, pos)
        if (!locked && shouldBeLocked) {
            locked = true
            current = BlockStates.repeaterState(
                BlockStates.delay[current].toInt(), facing, true, BlockStates.powered[current]
            )
            world.setBlock(pos, current)
        } else if (locked && !shouldBeLocked) {
            locked = false
            current = BlockStates.repeaterState(
                BlockStates.delay[current].toInt(), facing, false, BlockStates.powered[current]
            )
            world.setBlock(pos, current)
        }

        if (!locked && !world.pendingTickAt(pos)) {
            val shouldBePowered = shouldBePowered(facing, world, pos)
            if (shouldBePowered != BlockStates.powered[current]) {
                scheduleTick(current, facing, world, pos, shouldBePowered)
            }
        }
    }

    fun tick(state: Int, world: World, pos: BlockPos) {
        if (BlockStates.locked[state]) return
        val facing = BlockStates.directionOf(state) ?: return
        val delay = BlockStates.delay[state].toInt()
        val powered = BlockStates.powered[state]

        val shouldBePowered = shouldBePowered(facing, world, pos)
        if (powered && !shouldBePowered) {
            world.setBlock(pos, BlockStates.repeaterState(delay, facing, false, false))
            onStateChange(facing, world, pos)
        } else if (!powered) {
            if (!shouldBePowered) {
                world.scheduleTick(pos, delay, TickPriority.Higher)
                MchprsRedstone.stats.scheduledTicks++
            }
            world.setBlock(pos, BlockStates.repeaterState(delay, facing, false, true))
            onStateChange(facing, world, pos)
        }
    }
}
