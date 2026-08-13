package org.kvxd.optraix.world

import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import java.util.concurrent.ConcurrentHashMap


class Chunk(val x: Int, val z: Int) {

    val sections = arrayOfNulls<ChunkSection>(SECTION_COUNT)
    val blockEntities = ConcurrentHashMap<Int, BlockEntity>()

    var wireData: ByteArray? = null

    fun invalidateWire() {
        wireData = null
    }

    fun sectionFor(y: Int, create: Boolean): ChunkSection? {
        val index = (y - WORLD_MIN_Y) shr 4
        if (index < 0 || index >= SECTION_COUNT) return null
        var section = sections[index]
        if (section == null && create) {
            section = ChunkSection()
            sections[index] = section
            wireData = null
        }
        return section
    }

    fun getBlock(localX: Int, y: Int, localZ: Int): Int {
        val section = sectionFor(y, false) ?: return Blocks.Air.defaultState
        return section.get(index(localX, y and 15, localZ))
    }

    fun setBlock(localX: Int, y: Int, localZ: Int, state: Int): Boolean {
        if (y < WORLD_MIN_Y || y >= WORLD_MIN_Y + WORLD_HEIGHT) return false
        if (state == Blocks.Air.defaultState && sectionFor(y, false) == null) return false
        val section = sectionFor(y, true) ?: return false
        val changed = section.set(index(localX, y and 15, localZ), state)
        if (changed) wireData = null
        return changed
    }

    fun blockEntityKey(localX: Int, y: Int, localZ: Int): Int =
        ((y - WORLD_MIN_Y) shl 8) or (localZ shl 4) or localX

    companion object {
        fun index(x: Int, y: Int, z: Int): Int = (y shl 8) or (z shl 4) or x
    }
}
