package org.kvxd.optraix.redstone.optraix

import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.ChunkPos
import org.kvxd.optraix.world.WORLD_MIN_Y

internal class IntBuffer(capacity: Int = 16) {

    var data = IntArray(capacity)
        private set

    var size = 0
        private set

    fun add(value: Int) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun clear() {
        size = 0
    }

    operator fun get(index: Int): Int = data[index]
}

internal class LongBuffer(capacity: Int = 16) {

    private var data = LongArray(capacity)

    var size = 0
        private set

    fun add(value: Long) {
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun clear() {
        size = 0
    }

    operator fun get(index: Int): Long = data[index]
}

internal class IntDeque(capacity: Int = 16) {

    private var data = IntArray(Integer.highestOneBit(maxOf(4, capacity - 1)) * 2)
    private var mask = data.size - 1
    private var head = 0
    private var tail = 0

    val isEmpty: Boolean get() = head == tail

    fun clear() {
        head = 0
        tail = 0
    }

    fun addFirst(value: Int) {
        head = (head - 1) and mask
        data[head] = value
        if (head == tail) grow()
    }

    fun addLast(value: Int) {
        data[tail] = value
        tail = (tail + 1) and mask
        if (head == tail) grow()
    }

    fun pollFirst(): Int {
        val value = data[head]
        head = (head + 1) and mask
        return value
    }

    private fun grow() {
        val old = data.size
        val grown = IntArray(old * 2)
        val front = old - head
        System.arraycopy(data, head, grown, 0, front)
        System.arraycopy(data, 0, grown, front, head)
        data = grown
        mask = grown.size - 1
        head = 0
        tail = old
    }
}

internal class LongIntMap(expected: Int = 8) {

    private var keys: LongArray
    private var values: IntArray
    private var mask: Int
    private var limit: Int

    var size = 0
        private set

    init {
        val capacity = Integer.highestOneBit(maxOf(8, expected * 2 - 1)) * 2
        keys = LongArray(capacity)
        values = IntArray(capacity)
        java.util.Arrays.fill(values, -1)
        mask = capacity - 1
        limit = capacity - (capacity shr 2)
    }

    fun put(key: Long, value: Int) {
        var slot = slotOf(key, mask)
        while (values[slot] >= 0) {
            if (keys[slot] == key) {
                values[slot] = value
                return
            }
            slot = (slot + 1) and mask
        }
        keys[slot] = key
        values[slot] = value
        size++
        if (size >= limit) grow()
    }

    operator fun get(key: Long): Int {
        var slot = slotOf(key, mask)
        while (values[slot] >= 0) {
            if (keys[slot] == key) return values[slot]
            slot = (slot + 1) and mask
        }
        return -1
    }

    private fun grow() {
        val oldKeys = keys
        val oldValues = values
        val capacity = oldKeys.size * 2
        val grownKeys = LongArray(capacity)
        val grownValues = IntArray(capacity)
        java.util.Arrays.fill(grownValues, -1)
        val grownMask = capacity - 1
        for (index in oldValues.indices) {
            val value = oldValues[index]
            if (value < 0) continue
            val key = oldKeys[index]
            var slot = slotOf(key, grownMask)
            while (grownValues[slot] >= 0) slot = (slot + 1) and grownMask
            grownKeys[slot] = key
            grownValues[slot] = value
        }
        keys = grownKeys
        values = grownValues
        mask = grownMask
        limit = capacity - (capacity shr 2)
    }

    private fun slotOf(key: Long, bound: Int): Int {
        var hash = key * -7046029254386353131L
        hash = hash xor (hash ushr 32)
        return hash.toInt() and bound
    }
}

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
