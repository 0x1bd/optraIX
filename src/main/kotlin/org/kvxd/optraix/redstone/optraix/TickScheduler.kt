package org.kvxd.optraix.redstone.optraix

class TickScheduler(nodeCount: Int) {

    private val queueData = Array(WheelSize * Priorities) { IntArray(16) }
    private val queueSize = IntArray(WheelSize * Priorities)
    private val pending = ShortArray(nodeCount)
    private var now = 0

    var queued: Int = 0
        private set

    fun isPending(node: Int): Boolean = pending[node] > 0

    fun schedule(node: Int, delay: Int, priority: Int) {
        scheduleEntry(node, delay, priority)
        pending[node]++
    }

    fun scheduleEntry(entry: Int, delay: Int, priority: Int) {
        val slot = ((now + delay) and WheelMask) * Priorities + priority
        var data = queueData[slot]
        val size = queueSize[slot]
        if (size == data.size) {
            data = data.copyOf(size shl 1)
            queueData[slot] = data
        }
        data[size] = entry
        queueSize[slot] = size + 1
        queued++
    }

    fun releaseEntry() {
        queued--
    }

    fun nextBucket(): Int {
        now = (now + 1) and WheelMask
        return now
    }

    fun itemsAt(bucket: Int, priority: Int): IntArray = queueData[bucket * Priorities + priority]

    fun sizeAt(bucket: Int, priority: Int): Int = queueSize[bucket * Priorities + priority]

    fun clearAt(bucket: Int, priority: Int) {
        queueSize[bucket * Priorities + priority] = 0
    }

    internal fun peekNext(priority: Int): IntArray {
        val slot = ((now + 1) and WheelMask) * Priorities + priority
        return queueData[slot].copyOf(queueSize[slot])
    }

    fun forEachPending(action: (Int, Int, Int) -> Unit) {
        for (offset in 1..WheelSize) {
            val bucket = (now + offset) and WheelMask
            for (priority in 0 until Priorities) {
                val slot = bucket * Priorities + priority
                for (index in 0 until queueSize[slot]) action(queueData[slot][index], offset, priority)
            }
        }
    }

    fun release(node: Int) {
        pending[node]--
        queued--
    }

    fun clear() {
        queueSize.fill(0)
        pending.fill(0)
        queued = 0
        now = 0
    }

    companion object {
        const val WheelSize = 32
        const val WheelMask = WheelSize - 1
        const val Priorities = 4
    }
}
