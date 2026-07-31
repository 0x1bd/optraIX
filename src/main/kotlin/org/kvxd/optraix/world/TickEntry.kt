package org.kvxd.optraix.world


class TickEntry(
    var ticksLeft: Int,
    val priority: TickPriority,
    val pos: BlockPos,
)
