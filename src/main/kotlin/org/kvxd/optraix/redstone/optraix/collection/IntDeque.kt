package org.kvxd.optraix.redstone.optraix.collection

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
