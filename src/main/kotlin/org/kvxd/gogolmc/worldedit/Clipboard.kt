package org.kvxd.gogolmc.worldedit

import org.kvxd.gogolmc.block.property.FlipDirection
import org.kvxd.gogolmc.block.property.RotateAmount
import org.kvxd.gogolmc.world.BlockEntity
import org.kvxd.gogolmc.world.BlockPos

class Clipboard(
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val offset: BlockPos,
    val blocks: IntArray,
    val blockEntities: MutableMap<Int, BlockEntity> = HashMap(),
) {

    val volume: Int
        get() = sizeX * sizeY * sizeZ

    fun index(x: Int, y: Int, z: Int): Int = (y * sizeZ + z) * sizeX + x

    operator fun get(x: Int, y: Int, z: Int): Int = blocks[index(x, y, z)]

    operator fun set(x: Int, y: Int, z: Int, state: Int) {
        blocks[index(x, y, z)] = state
    }

    fun rotate(amount: RotateAmount): Clipboard {
        var current = this
        val turns = when (amount) {
            RotateAmount.Rotate90 -> 1
            RotateAmount.Rotate180 -> 2
            RotateAmount.Rotate270 -> 3
        }
        repeat(turns) { current = current.rotate90() }
        return current
    }

    private fun rotate90(): Clipboard {
        val result = Clipboard(
            sizeX = sizeZ,
            sizeY = sizeY,
            sizeZ = sizeX,
            offset = BlockPos(-offset.z - sizeZ + 1, offset.y, offset.x),
            blocks = IntArray(volume),
        )
        for (y in 0 until sizeY) {
            for (z in 0 until sizeZ) {
                for (x in 0 until sizeX) {
                    result[sizeZ - 1 - z, y, x] = BlockTransform.rotate90(this[x, y, z])
                }
            }
        }
        for ((key, entity) in blockEntities) {
            val x = key % sizeX
            val z = (key / sizeX) % sizeZ
            val y = key / (sizeX * sizeZ)
            result.blockEntities[result.index(sizeZ - 1 - z, y, x)] = entity
        }
        return result
    }

    fun flip(direction: FlipDirection): Clipboard {
        val result = Clipboard(
            sizeX = sizeX,
            sizeY = sizeY,
            sizeZ = sizeZ,
            offset = when (direction) {
                FlipDirection.FlipX -> BlockPos(-offset.x - sizeX + 1, offset.y, offset.z)
                FlipDirection.FlipZ -> BlockPos(offset.x, offset.y, -offset.z - sizeZ + 1)
            },
            blocks = IntArray(volume),
        )
        for (y in 0 until sizeY) {
            for (z in 0 until sizeZ) {
                for (x in 0 until sizeX) {
                    val targetX = if (direction == FlipDirection.FlipX) sizeX - 1 - x else x
                    val targetZ = if (direction == FlipDirection.FlipZ) sizeZ - 1 - z else z
                    result[targetX, y, targetZ] = BlockTransform.flip(this[x, y, z], direction)
                }
            }
        }
        for ((key, entity) in blockEntities) {
            val x = key % sizeX
            val z = (key / sizeX) % sizeZ
            val y = key / (sizeX * sizeZ)
            val targetX = if (direction == FlipDirection.FlipX) sizeX - 1 - x else x
            val targetZ = if (direction == FlipDirection.FlipZ) sizeZ - 1 - z else z
            result.blockEntities[result.index(targetX, y, targetZ)] = entity
        }
        return result
    }
}
