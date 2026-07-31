package org.kvxd.optraix.world

import org.kvxd.optraix.block.BlockKind
import org.kvxd.optraix.block.BlockStates

object BlockEntities {

    fun defaultFor(state: Int): BlockEntity? = when (BlockStates.kindOf(state)) {
        BlockKind.Comparator -> BlockEntity.Comparator(0)
        BlockKind.Barrel -> BlockEntity.Container(ContainerKind.Barrel, 0, emptyList())
        BlockKind.Chest -> BlockEntity.Container(ContainerKind.Chest, 0, emptyList())
        BlockKind.Furnace -> BlockEntity.Container(ContainerKind.Furnace, 0, emptyList())
        BlockKind.Hopper -> BlockEntity.Container(ContainerKind.Hopper, 0, emptyList())
        BlockKind.Sign, BlockKind.WallSign -> BlockEntity.Sign(EmptyRows, EmptyRows)
        else -> null
    }

    fun ensure(world: World, pos: BlockPos): BlockEntity? {
        world.getBlockEntity(pos)?.let { return it }
        val created = defaultFor(world.getBlock(pos)) ?: return null
        world.setBlockEntity(pos, created)
        return created
    }

    private val EmptyRows = listOf("", "", "", "")
}
