package org.kvxd.optraix.redstone.optraix

class IntQueue(initial: Int = 16) {

    private var items = IntArray(if (initial < 4) 4 else initial)
    private var head = 0
    private var tail = 0

    val isEmpty: Boolean get() = head == tail

    fun add(value: Int) {
        if (tail == items.size) grow()
        items[tail++] = value
    }

    fun poll(): Int {
        val value = items[head++]
        if (head == tail) {
            head = 0
            tail = 0
        }
        return value
    }

    fun clear() {
        head = 0
        tail = 0
    }

    private fun grow() {
        if (head > 0) {
            System.arraycopy(items, head, items, 0, tail - head)
            tail -= head
            head = 0
        } else {
            items = items.copyOf(items.size shl 1)
        }
    }
}
