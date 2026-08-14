package org.kvxd.optraix.collection

class ConcurrentLongChangeSet : AbstractMutableSet<Long>() {
    private val values = HashSet<Long>()

    override val size: Int
        get() = synchronized(values) { values.size }

    override fun add(element: Long): Boolean = synchronized(values) { values.add(element) }

    override fun contains(element: Long): Boolean = synchronized(values) { values.contains(element) }

    override fun remove(element: Long): Boolean = synchronized(values) { values.remove(element) }

    override fun clear() = synchronized(values) { values.clear() }

    override fun iterator(): MutableIterator<Long> {
        val snapshot = synchronized(values) { values.toLongArray() }
        var index = 0
        var last = -1
        return object : MutableIterator<Long> {
            override fun hasNext(): Boolean = index < snapshot.size

            override fun next(): Long {
                if (!hasNext()) throw NoSuchElementException()
                last = index
                return snapshot[index++]
            }

            override fun remove() {
                if (last < 0) throw IllegalStateException()
                this@ConcurrentLongChangeSet.remove(snapshot[last])
                last = -1
            }
        }
    }

    fun drain(): LongArray = synchronized(values) {
        val snapshot = values.toLongArray()
        values.clear()
        snapshot
    }
}
