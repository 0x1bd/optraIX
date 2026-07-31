package org.kvxd.optraix.redstone.mchprs

import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.BlockFace
import org.kvxd.optraix.block.BlockKind
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.property.ComparatorMode
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.TickPriority
import org.kvxd.optraix.world.World

object Comparator {

    private fun getPowerOnSide(world: World, pos: BlockPos, side: BlockDirection): Int {
        val sidePos = pos.offset(side.blockFace())
        val sideState = world.getBlock(sidePos)
        return when {
            MchprsRedstone.isDiode(sideState) ->
                MchprsRedstone.getWeakPower(sideState, world, sidePos, side.blockFace(), false)
            BlockStates.kindOf(sideState) == BlockKind.RedstoneWire ->
                BlockStates.wirePower[sideState].toInt()
            BlockStates.kindOf(sideState) == BlockKind.RedstoneBlock -> 15
            else -> 0
        }
    }

    private fun getPowerOnSides(facing: BlockDirection, world: World, pos: BlockPos): Int = maxOf(
        getPowerOnSide(world, pos, facing.rotate()),
        getPowerOnSide(world, pos, facing.rotateCcw()),
    )

    fun hasOverride(state: Int): Boolean = when (BlockStates.kindOf(state)) {
        BlockKind.Barrel, BlockKind.Furnace, BlockKind.Hopper,
        BlockKind.Cauldron, BlockKind.Composter, BlockKind.Cake -> true
        BlockKind.EndPortalFrame -> BlockStates.eye[state]
        else -> false
    }

    fun getOverride(state: Int, world: World, pos: BlockPos): Int = when (BlockStates.kindOf(state)) {
        BlockKind.Barrel, BlockKind.Furnace, BlockKind.Hopper ->
            (world.getBlockEntity(pos) as? BlockEntity.Container)?.comparatorOverride ?: 0
        BlockKind.Cauldron -> 0
        BlockKind.WaterCauldron -> BlockStates.level[state].toInt()
        BlockKind.Composter -> BlockStates.level[state].toInt()
        BlockKind.Cake -> 14 - 2 * BlockStates.level[state].toInt()
        BlockKind.EndPortalFrame -> 15
        else -> 0
    }

    fun getFarInput(world: World, pos: BlockPos, facing: BlockDirection): Int? {
        val face = facing.blockFace()
        val inputPos = pos.offset(face)
        val inputState = world.getBlock(inputPos)
        if (!BlockStates.isSolid(inputState) || hasOverride(inputState)) return null

        val farInputPos = inputPos.offset(face)
        val farInputState = world.getBlock(farInputPos)
        return if (hasOverride(farInputState)) getOverride(farInputState, world, farInputPos) else null
    }

    private fun calculateInputStrength(facing: BlockDirection, world: World, pos: BlockPos): Int {
        val baseInputStrength = MchprsRedstone.diodeGetInputStrength(world, pos, facing)
        val inputPos = pos.offset(facing.blockFace())
        val inputState = world.getBlock(inputPos)
        return when {
            hasOverride(inputState) -> getOverride(inputState, world, inputPos)
            baseInputStrength < 15 && BlockStates.isSolid(inputState) -> {
                val farInputPos = inputPos.offset(facing.blockFace())
                val farInputState = world.getBlock(farInputPos)
                if (hasOverride(farInputState)) getOverride(farInputState, world, farInputPos)
                else baseInputStrength
            }
            else -> baseInputStrength
        }
    }

    fun shouldBePowered(facing: BlockDirection, mode: ComparatorMode, world: World, pos: BlockPos): Boolean {
        val inputStrength = calculateInputStrength(facing, world, pos)
        if (inputStrength == 0) return false
        val powerOnSides = getPowerOnSides(facing, world, pos)
        return if (inputStrength > powerOnSides) true
        else powerOnSides == inputStrength && mode == ComparatorMode.Compare
    }

    private fun calculateOutputStrength(
        facing: BlockDirection,
        mode: ComparatorMode,
        world: World,
        pos: BlockPos,
    ): Int {
        val inputStrength = calculateInputStrength(facing, world, pos)
        return if (mode == ComparatorMode.Subtract) {
            maxOf(0, inputStrength - getPowerOnSides(facing, world, pos))
        } else if (inputStrength >= getPowerOnSides(facing, world, pos)) {
            inputStrength
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

    fun update(state: Int, world: World, pos: BlockPos) {
        if (world.pendingTickAt(pos)) return
        val facing = BlockStates.directionOf(state) ?: return
        val mode = BlockStates.comparatorModeOf(state)
        val outputStrength = calculateOutputStrength(facing, mode, world, pos)
        val oldStrength = (world.getBlockEntity(pos) as? BlockEntity.Comparator)?.outputStrength ?: 0
        if (outputStrength != oldStrength ||
            BlockStates.powered[state] != shouldBePowered(facing, mode, world, pos)
        ) {
            val frontState = world.getBlock(pos.offset(facing.opposite().blockFace()))
            val priority = if (MchprsRedstone.isDiode(frontState)) TickPriority.High else TickPriority.Normal
            world.scheduleTick(pos, 1, priority)
            MchprsRedstone.stats.scheduledTicks++
        }
    }

    fun tick(state: Int, world: World, pos: BlockPos) {
        val facing = BlockStates.directionOf(state) ?: return
        val mode = BlockStates.comparatorModeOf(state)
        val newStrength = calculateOutputStrength(facing, mode, world, pos)
        val oldStrength = (world.getBlockEntity(pos) as? BlockEntity.Comparator)?.outputStrength ?: 0
        if (newStrength != oldStrength || mode == ComparatorMode.Compare) {
            world.setBlockEntity(pos, BlockEntity.Comparator(newStrength))
            val shouldBePowered = shouldBePowered(facing, mode, world, pos)
            val powered = BlockStates.powered[state]
            if (powered && !shouldBePowered) {
                world.setBlock(pos, BlockStates.comparatorState(facing, mode, false))
            } else if (!powered && shouldBePowered) {
                world.setBlock(pos, BlockStates.comparatorState(facing, mode, true))
            }
            onStateChange(facing, world, pos)
        }
    }
}
