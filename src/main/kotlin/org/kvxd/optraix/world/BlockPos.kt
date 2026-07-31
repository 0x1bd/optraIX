package org.kvxd.optraix.world

import org.kvxd.optraix.block.property.BlockFace
import org.kvxd.optraix.block.property.BlockFacing

data class BlockPos(val x: Int, val y: Int, val z: Int) {

    fun offset(face: BlockFace): BlockPos = when (face) {
        BlockFace.Bottom -> BlockPos(x, y - 1, z)
        BlockFace.Top -> BlockPos(x, y + 1, z)
        BlockFace.North -> BlockPos(x, y, z - 1)
        BlockFace.South -> BlockPos(x, y, z + 1)
        BlockFace.West -> BlockPos(x - 1, y, z)
        BlockFace.East -> BlockPos(x + 1, y, z)
    }

    fun offset(facing: BlockFacing, n: Int = 1): BlockPos = when (facing) {
        BlockFacing.North -> BlockPos(x, y, z - n)
        BlockFacing.South -> BlockPos(x, y, z + n)
        BlockFacing.East -> BlockPos(x + n, y, z)
        BlockFacing.West -> BlockPos(x - n, y, z)
        BlockFacing.Up -> BlockPos(x, y + n, z)
        BlockFacing.Down -> BlockPos(x, y - n, z)
    }

    operator fun plus(other: BlockPos) = BlockPos(x + other.x, y + other.y, z + other.z)

    operator fun minus(other: BlockPos) = BlockPos(x - other.x, y - other.y, z - other.z)

    operator fun times(factor: Int) = BlockPos(x * factor, y * factor, z * factor)

    fun min(other: BlockPos) = BlockPos(minOf(x, other.x), minOf(y, other.y), minOf(z, other.z))

    fun max(other: BlockPos) = BlockPos(maxOf(x, other.x), maxOf(y, other.y), maxOf(z, other.z))

    fun asLong(): Long = pack(x, y, z)

    override fun toString(): String = "($x, $y, $z)"

    companion object {
        val Zero = BlockPos(0, 0, 0)

        fun pack(x: Int, y: Int, z: Int): Long =
            ((x.toLong() and 0x3FFFFFF) shl 38) or ((z.toLong() and 0x3FFFFFF) shl 12) or (y.toLong() and 0xFFF)

        fun unpack(packed: Long): BlockPos {
            val x = (packed shr 38).toInt()
            val z = ((packed shl 26) shr 38).toInt()
            val y = ((packed shl 52) shr 52).toInt()
            return BlockPos(x, y, z)
        }
    }
}
