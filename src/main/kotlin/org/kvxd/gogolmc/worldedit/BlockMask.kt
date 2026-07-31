package org.kvxd.gogolmc.worldedit

import org.kvxd.gogolmc.block.Blocks

class BlockMask(private val stateId: Int, private val exact: Boolean) {

    fun matches(state: Int): Boolean =
        if (exact) state == stateId else Blocks.typeOf(state) === Blocks.typeOf(stateId)

    companion object {

        fun exact(stateId: Int): BlockMask = BlockMask(stateId, true)

        fun ofType(stateId: Int): BlockMask = BlockMask(stateId, false)

        fun parse(text: String): BlockMask? {
            val state = Blocks.parse(text) ?: return null
            return BlockMask(state, text.contains('['))
        }
    }
}
