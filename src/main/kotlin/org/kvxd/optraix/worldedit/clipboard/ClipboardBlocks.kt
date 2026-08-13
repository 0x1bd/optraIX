package org.kvxd.optraix.worldedit.clipboard

internal interface ClipboardBlocks {
    val dense: IntArray?
    val storedBlockCount: Int
    operator fun get(index: Int): Int
    operator fun set(index: Int, state: Int)
    fun positionAt(entry: Int): Int
    fun stateAt(entry: Int): Int
    fun forEachNonAir(action: (Int, Int) -> Unit)
}
