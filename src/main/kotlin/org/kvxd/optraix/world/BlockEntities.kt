package org.kvxd.optraix.world

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.mcdata.v1_20_4.Blocks

object BlockEntities {

    fun defaultFor(state: Int): BlockEntity? {
        if (BlockStates.isSign(state) || BlockStates.isWallSign(state)) return BlockEntity.Sign(EmptyRows, EmptyRows)
        return when (BlockStates.typeOf(state)) {
            Blocks.Comparator -> BlockEntity.Comparator(0)
            Blocks.Barrel -> BlockEntity.Container(ContainerKind.Barrel, 0, emptyList())
            Blocks.Chest -> BlockEntity.Container(ContainerKind.Chest, 0, emptyList())
            Blocks.Furnace -> BlockEntity.Container(ContainerKind.Furnace, 0, emptyList())
            Blocks.Hopper -> BlockEntity.Container(ContainerKind.Hopper, 0, emptyList())
            else -> null
        }
    }

    fun ensure(world: World, pos: BlockPos): BlockEntity? {
        world.getBlockEntity(pos)?.let { return it }
        val created = defaultFor(world.getBlock(pos)) ?: return null
        world.setBlockEntity(pos, created)
        return created
    }

    private val EmptyRows = listOf("", "", "", "")
}
