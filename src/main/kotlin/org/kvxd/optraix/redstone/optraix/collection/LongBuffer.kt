package org.kvxd.optraix.redstone.optraix.collection

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
