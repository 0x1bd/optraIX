package org.kvxd.optraix.block.property

enum class BlockDirection {
    North,
    South,
    West,
    East;

    fun opposite(): BlockDirection = when (this) {
        North -> South
        South -> North
        East -> West
        West -> East
    }

    fun blockFace(): BlockFace = when (this) {
        North -> BlockFace.North
        South -> BlockFace.South
        East -> BlockFace.East
        West -> BlockFace.West
    }

    fun blockFacing(): BlockFacing = when (this) {
        North -> BlockFacing.North
        South -> BlockFacing.South
        East -> BlockFacing.East
        West -> BlockFacing.West
    }

    fun rotate(): BlockDirection = when (this) {
        North -> East
        East -> South
        South -> West
        West -> North
    }

    fun rotateCcw(): BlockDirection = when (this) {
        North -> West
        West -> South
        South -> East
        East -> North
    }

    fun flip(axisX: Boolean): BlockDirection = when {
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
        val Values: Array<BlockDirection> = values()

        fun fromRotation(rotation: Int): BlockDirection? = when (rotation) {
            0 -> South
            4 -> West
            8 -> North
            12 -> East
            else -> null
        }
    }
}
