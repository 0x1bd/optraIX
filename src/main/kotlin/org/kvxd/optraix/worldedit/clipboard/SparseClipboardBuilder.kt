package org.kvxd.optraix.worldedit.clipboard

import org.kvxd.optraix.mcdata.v1_20_4.Blocks

internal class SparseClipboardBuilder(initialCapacity: Int = 1024) {
    private var positions = IntArray(initialCapacity.coerceAtLeast(1))
    private var states = IntArray(initialCapacity.coerceAtLeast(1))
    private var size = 0

    fun add(position: Int, state: Int) {
        if (state == Blocks.Air.defaultState) return
        if (size == positions.size) {
            val capacity = if (size < 1 shl 20) size * 2 else size + (size shr 1)
            positions = positions.copyOf(capacity)
            states = states.copyOf(capacity)
        }
        positions[size] = position
        states[size] = state
        size++
    }

    fun build(sorted: Boolean): SparseClipboardBlocks = SparseClipboardBlocks(positions, states, size, sorted)
}
