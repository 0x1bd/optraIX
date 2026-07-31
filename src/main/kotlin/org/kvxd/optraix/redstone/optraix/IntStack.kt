package org.kvxd.optraix.redstone.optraix

class IntStack(initial: Int = 16) {

    var items = IntArray(if (initial < 4) 4 else initial)
        private set

    var size = 0
        private set

    fun push(value: Int) {
        if (size == items.size) items = items.copyOf(items.size shl 1)
        items[size++] = value
    }

    fun pop(): Int = items[--size]

    fun clear() {
        size = 0
    }

    val isEmpty: Boolean get() = size == 0
}
