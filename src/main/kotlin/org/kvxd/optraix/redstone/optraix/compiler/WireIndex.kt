package org.kvxd.optraix.redstone.optraix.compiler

import org.kvxd.optraix.redstone.optraix.collection.LongBuffer
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.ChunkPos
import org.kvxd.optraix.world.WORLD_MIN_Y

internal class WireIndex {

    private class ChunkWires {
        var sections = IntArray(4)
        var slots = arrayOfNulls<ShortArray>(4)
        var size = 0

        fun add(sectionIndex: Int, entries: ShortArray) {
            if (size == sections.size) {
                sections = sections.copyOf(size * 2)
                slots = slots.copyOf(size * 2)
            }
            sections[size] = sectionIndex
            slots[size] = entries
            size++
        }
    }

    private val chunks = HashMap<Long, ChunkWires>()

    var count = 0
        private set

    fun add(chunkX: Int, chunkZ: Int, sectionIndex: Int, entries: ShortArray) {
        if (entries.isEmpty()) return
        chunks.getOrPut(ChunkPos.key(chunkX, chunkZ)) { ChunkWires() }.add(sectionIndex, entries)
        count += entries.size
    }

    fun collect(minX: Int, maxX: Int, minZ: Int, maxZ: Int, out: LongBuffer) {
        for (chunkX in (minX shr 4)..(maxX shr 4)) {
            for (chunkZ in (minZ shr 4)..(maxZ shr 4)) {
                val entry = chunks[ChunkPos.key(chunkX, chunkZ)] ?: continue
                for (index in 0 until entry.size) {
                    val baseY = WORLD_MIN_Y + (entry.sections[index] shl 4)
                    val slots = entry.slots[index] ?: continue
                    for (packed in slots) {
                        val slot = packed.toInt() and 0xFFF
                        val x = (chunkX shl 4) or (slot and 15)
                        val z = (chunkZ shl 4) or ((slot shr 4) and 15)
                        if (x < minX || x > maxX || z < minZ || z > maxZ) continue
                        out.add(BlockPos.pack(x, baseY + (slot shr 8), z))
                    }
                }
            }
        }
    }
}
