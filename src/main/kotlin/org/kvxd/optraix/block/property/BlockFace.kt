package org.kvxd.optraix.block.property

enum class BlockFace {
    Bottom,
    Top,
    North,
    South,
    West,
    East;

    val isHorizontal: Boolean
        get() = this == North || this == South || this == West || this == East

    fun unwrapDirection(): BlockDirection = when (this) {
        North -> BlockDirection.North
        South -> BlockDirection.South
        East -> BlockDirection.East
        West -> BlockDirection.West
        else -> throw IllegalStateException("no direction for $this")
    }

    fun opposite(): BlockFace = when (this) {
        Bottom -> Top
        Top -> Bottom
        North -> South
        South -> North
        West -> East
        East -> West
    }

    companion object {
        val All: Array<BlockFace> = arrayOf(Top, Bottom, North, South, East, West)

        fun fromId(id: Int): BlockFace = when (id) {
            0 -> Bottom
            1 -> Top
            2 -> North
            3 -> South
            4 -> West
            5 -> East
            else -> throw IllegalArgumentException("invalid block face id $id")
        }
    }
}
