package org.kvxd.gogolmc.redstone

import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.property.BlockDirection
import org.kvxd.gogolmc.block.property.BlockFace
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.GameWorld
import org.kvxd.gogolmc.world.World

interface RedstoneEngine {

    val name: String

    val stats: RedstoneStats

    fun setPressurePlate(world: GameWorld, pos: BlockPos, powered: Boolean) {
        val state = world.getBlock(pos)
        if ((BlockStates.pressurePlatePowered(state) ?: return) == powered) return
        world.setBlock(pos, BlockStates.withPowered(state, powered))
        updateSurroundingBlocks(world, pos)
        updateSurroundingBlocks(world, pos.offset(BlockFace.Bottom))
    }

    fun tickWorld(world: GameWorld) {
        world.tickScheduled { pos -> tick(world, pos) }
    }

    fun onUse(world: World, pos: BlockPos): Boolean

    fun update(world: World, pos: BlockPos)

    fun tick(world: World, pos: BlockPos)

    fun updateSurroundingBlocks(world: World, pos: BlockPos)

    fun updateWireNeighbors(world: World, pos: BlockPos)

    fun wireStateOnNeighborChanged(world: World, pos: BlockPos, state: Int, side: BlockFace): Int

    fun wireStateForPlacement(world: World, pos: BlockPos): Int

    fun repeaterStateForPlacement(world: World, pos: BlockPos, facing: BlockDirection): Int

    fun redstoneLampShouldBeLit(world: World, pos: BlockPos): Boolean

    fun getRedstonePower(world: World, pos: BlockPos, facing: BlockFace): Int

    fun isDiode(state: Int): Boolean
}
