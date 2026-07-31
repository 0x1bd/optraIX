package org.kvxd.optraix.block.property

enum class BlockFacing {
    North,
    East,
    South,
    West,
    Up,
    Down;

    fun rotate(): BlockFacing = when (this) {
        North -> East
        East -> South
        South -> West
        West -> North
        else -> this
    }

    fun rotateCcw(): BlockFacing = when (this) {
        North -> West
        West -> South
        South -> East
        East -> North
        else -> this
    }

    fun flip(axisX: Boolean): BlockFacing = when {
        axisX -> when (this) {
            East -> West
            West -> East
            else -> this
        }
        else -> when (this) {
            North -> South
            South -> North
            else -> this
        }
    }

    companion object {
        val Values: Array<BlockFacing> = values()
    }
}
