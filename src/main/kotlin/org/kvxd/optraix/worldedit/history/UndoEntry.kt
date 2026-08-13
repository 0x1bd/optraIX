package org.kvxd.optraix.worldedit.history

class UndoEntry(
    val positions: LongArray,
    val states: IntArray,
    val size: Int = positions.size,
) {
    init {
        require(size in 0..positions.size)
        require(size <= states.size)
    }
}
