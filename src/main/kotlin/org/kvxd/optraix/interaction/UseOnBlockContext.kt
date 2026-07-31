package org.kvxd.optraix.interaction

import org.kvxd.optraix.block.property.BlockFace
import org.kvxd.optraix.world.BlockPos

class UseOnBlockContext(
    val blockPos: BlockPos,
    val blockFace: BlockFace,
    val cursorY: Float,
    val yaw: Float,
    val pitch: Float,
    val crouching: Boolean,
    val playerPos: BlockPos,
)
