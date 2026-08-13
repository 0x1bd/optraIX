package org.kvxd.optraix.redstone.optraix.collection

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
