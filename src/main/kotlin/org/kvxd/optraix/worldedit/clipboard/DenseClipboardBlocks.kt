package org.kvxd.optraix.worldedit.clipboard

import org.kvxd.optraix.mcdata.v1_20_4.Blocks

internal class DenseClipboardBlocks(override val dense: IntArray) : ClipboardBlocks {
    override val storedBlockCount: Int
        get() = dense.count { it != Blocks.Air.defaultState }

    override fun get(index: Int): Int = dense[index]

    override fun set(index: Int, state: Int) {
        dense[index] = state
    }

    override fun positionAt(entry: Int): Int = entry

    override fun stateAt(entry: Int): Int = dense[entry]

    override fun forEachNonAir(action: (Int, Int) -> Unit) {
        dense.forEachIndexed { index, state ->
            if (state != Blocks.Air.defaultState) action(index, state)
        }
    }
}
