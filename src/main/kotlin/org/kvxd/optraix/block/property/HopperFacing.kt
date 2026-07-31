package org.kvxd.optraix.block.property

enum class HopperFacing {
    Down,
    North,
    South,
    West,
    East;

    fun rotate(): HopperFacing = when (this) {
        North -> East
        East -> South
        South -> West
        West -> North
        Down -> Down
    }

    fun flip(axisX: Boolean): HopperFacing = when {
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
}
