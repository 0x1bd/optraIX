package org.kvxd.optraix.redstone.optraix.collection

internal class LongIntLookup private constructor(
    private val keys: LongArray,
    private val values: IntArray,
) {
    operator fun get(key: Long): Int {
        var low = 0
        var high = keys.size - 1
        while (low <= high) {
            val middle = (low + high) ushr 1
            when {
                keys[middle] < key -> low = middle + 1
                keys[middle] > key -> high = middle - 1
                else -> return values[middle]
            }
        }
        return -1
    }

    companion object {
        fun from(keys: LongArray): LongIntLookup {
            val sortedKeys = keys.copyOf()
            val sortedValues = IntArray(keys.size) { it }
            if (sortedKeys.size > 1) sort(sortedKeys, sortedValues, 0, sortedKeys.lastIndex)
            return LongIntLookup(sortedKeys, sortedValues)
        }

        private fun sort(keys: LongArray, values: IntArray, first: Int, last: Int) {
            var low = first
            var high = last
            val pivot = keys[(first + last) ushr 1]
            while (low <= high) {
                while (keys[low] < pivot) low++
                while (keys[high] > pivot) high--
                if (low <= high) {
                    val key = keys[low]
                    keys[low] = keys[high]
                    keys[high] = key
                    val value = values[low]
                    values[low] = values[high]
                    values[high] = value
                    low++
                    high--
                }
            }
            if (first < high) sort(keys, values, first, high)
            if (low < last) sort(keys, values, low, last)
        }
    }
}
