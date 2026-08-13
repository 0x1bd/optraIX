package org.kvxd.optraix.redstone.optraix.collection

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
