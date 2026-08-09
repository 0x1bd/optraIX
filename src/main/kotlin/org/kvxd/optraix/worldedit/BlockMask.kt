package org.kvxd.optraix.worldedit

import org.kvxd.optraix.block.mcData


class BlockMask(private val stateId: Int, private val exact: Boolean) {

    fun matches(state: Int): Boolean =
        if (exact) state == stateId else mcData.requireBlockByStateId(state) === mcData.requireBlockByStateId(stateId)

    companion object {

        fun exact(stateId: Int): BlockMask = BlockMask(stateId, true)

        fun ofType(stateId: Int): BlockMask = BlockMask(stateId, false)

        fun parse(text: String): BlockMask? {
            val state = mcData.blockState(text) ?: return null
            return BlockMask(state, text.contains('['))
        }
    }
}
