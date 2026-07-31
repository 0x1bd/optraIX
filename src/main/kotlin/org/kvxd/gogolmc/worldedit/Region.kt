package org.kvxd.gogolmc.worldedit

import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.WORLD_HEIGHT
import org.kvxd.gogolmc.world.WORLD_MIN_Y

class Region(first: BlockPos, second: BlockPos) {

    val min = BlockPos(
        minOf(first.x, second.x),
        maxOf(minOf(first.y, second.y), WORLD_MIN_Y),
        minOf(first.z, second.z),
    )

    val max = BlockPos(
        maxOf(first.x, second.x),
        minOf(maxOf(first.y, second.y), WORLD_MIN_Y + WORLD_HEIGHT - 1),
        maxOf(first.z, second.z),
    )

    val sizeX: Int get() = max.x - min.x + 1
    val sizeY: Int get() = max.y - min.y + 1
    val sizeZ: Int get() = max.z - min.z + 1

    val volume: Long
        get() = sizeX.toLong() * sizeY.toLong() * sizeZ.toLong()

    inline fun forEach(action: (BlockPos) -> Unit) {
        for (y in min.y..max.y) {
            for (z in min.z..max.z) {
                for (x in min.x..max.x) action(BlockPos(x, y, z))
            }
        }
    }
}
