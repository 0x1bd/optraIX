package org.kvxd.gogolmc.interaction

import org.kvxd.gogolmc.block.property.BlockFace
import org.kvxd.gogolmc.world.BlockPos

class UseOnBlockContext(
    val blockPos: BlockPos,
    val blockFace: BlockFace,
    val cursorY: Float,
    val yaw: Float,
    val pitch: Float,
    val crouching: Boolean,
    val playerPos: BlockPos,
)
