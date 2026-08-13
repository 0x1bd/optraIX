package org.kvxd.optraix.worldedit.history

internal class UndoAccumulator(initialCapacity: Int) {
    private var positions = LongArray(initialCapacity.coerceAtLeast(1))
    private var states = IntArray(initialCapacity.coerceAtLeast(1))
    var size: Int = 0
        private set

    fun add(position: Long, state: Int) {
        if (size == positions.size) {
            val capacity = if (size < 1 shl 20) size * 2 else size + (size shr 1)
            positions = positions.copyOf(capacity)
            states = states.copyOf(capacity)
        }
        positions[size] = position
        states[size] = state
        size++
    }

    fun build(): UndoEntry = UndoEntry(positions, states, size)
}
