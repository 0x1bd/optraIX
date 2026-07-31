package org.kvxd.gogolmc.world


class TickEntry(
    var ticksLeft: Int,
    val priority: TickPriority,
    val pos: BlockPos,
)
