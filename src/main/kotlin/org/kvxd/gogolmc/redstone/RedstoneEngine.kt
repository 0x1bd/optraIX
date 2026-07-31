package org.kvxd.gogolmc.redstone

import org.kvxd.gogolmc.block.property.BlockDirection
import org.kvxd.gogolmc.block.property.BlockFace
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.GameWorld
import org.kvxd.gogolmc.world.World

interface RedstoneEngine {

    val name: String

    val stats: RedstoneStats

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
