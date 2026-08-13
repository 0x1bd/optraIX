package org.kvxd.optraix.worldedit.clipboard

import org.kvxd.optraix.mcdata.v1_20_4.Blocks

internal class SparseClipboardBlocks(
    private val positions: IntArray,
    private val states: IntArray,
    override val storedBlockCount: Int,
    private val sorted: Boolean,
) : ClipboardBlocks {
    override val dense: IntArray? = null

    override fun get(index: Int): Int {
        if (sorted) {
            var low = 0
            var high = storedBlockCount - 1
            while (low <= high) {
                val middle = (low + high) ushr 1
                val position = positions[middle]
                when {
                    position < index -> low = middle + 1
                    position > index -> high = middle - 1
                    else -> return states[middle]
                }
            }
            return Blocks.Air.defaultState
        }
        for (entry in 0 until storedBlockCount) {
            if (positions[entry] == index) return states[entry]
        }
        return Blocks.Air.defaultState
    }

    override fun set(index: Int, state: Int) {
        error("sparse clipboard blocks are immutable")
    }

    override fun forEachNonAir(action: (Int, Int) -> Unit) {
        for (entry in 0 until storedBlockCount) action(positions[entry], states[entry])
    }
}
