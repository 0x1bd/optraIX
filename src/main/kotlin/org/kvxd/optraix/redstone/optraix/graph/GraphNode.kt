package org.kvxd.optraix.redstone.optraix.graph

import org.kvxd.optraix.world.BlockPos

class GraphNode internal constructor(
    private val graph: OptraIxGraph,
    val id: Int,
) {
    val posKey: Long
        get() = graph.posKey(id)

    val pos: BlockPos
        get() = BlockPos.unpack(posKey)

    val type: Int
        get() = graph.type(id)

    val state: Int
        get() = graph.state(id)

    var output: Int
        get() = graph.bits(id, OutputShift, 15)
        set(value) = graph.setBits(id, OutputShift, 15, value)

    var on: Boolean
        get() = graph.flag(id, OnBit)
        set(value) = graph.setFlag(id, OnBit, value)

    var locked: Boolean
        get() = graph.flag(id, LockedBit)
        set(value) = graph.setFlag(id, LockedBit, value)

    var delay: Int
        get() = graph.bits(id, DelayShift, 31)
        set(value) = graph.setBits(id, DelayShift, 31, value)

    var mode: Int
        get() = graph.bits(id, ModeShift, 3)
        set(value) = graph.setBits(id, ModeShift, 3, value)

    var facing: Int
        get() = graph.bits(id, FacingShift, 7) - 1
        set(value) = graph.setBits(id, FacingShift, 7, value + 1)

    var frontDiode: Boolean
        get() = graph.flag(id, FrontDiodeBit)
        set(value) = graph.setFlag(id, FrontDiodeBit, value)

    var adjacentOverride: Int
        get() = graph.bits(id, AdjacentShift, 31) - 1
        set(value) = graph.setBits(id, AdjacentShift, 31, value + 1)

    var farOverride: Int
        get() = graph.bits(id, FarShift, 31) - 1
        set(value) = graph.setBits(id, FarShift, 31, value + 1)

    var onStrength: Int
        get() = graph.bits(id, OnStrengthShift, 15)
        set(value) = graph.setBits(id, OnStrengthShift, 15, value)

    var chainLinks: List<GraphNode>?
        get() = graph.chainLinks(id)
        set(value) = graph.setChainLinks(id, value)

    val inputSize: Int
        get() = graph.inputSize(id)

    val outputSize: Int
        get() = graph.outputSize(id)

    val inputs: List<GraphEdge>
        get() = List(inputSize) { index ->
            GraphEdge(inputNode(index), id, inputWeight(index), inputSide(index))
        }

    val outputs: List<GraphEdge>
        get() = List(outputSize) { index ->
            GraphEdge(id, outputNode(index), outputWeight(index), outputSide(index))
        }

    fun inputNode(index: Int): Int = graph.inputNode(id, index)

    fun inputWeight(index: Int): Int = graph.inputWeight(id, index)

    fun inputSide(index: Int): Boolean = graph.inputSide(id, index)

    fun outputNode(index: Int): Int = graph.outputNode(id, index)

    fun outputWeight(index: Int): Int = graph.outputWeight(id, index)

    fun outputSide(index: Int): Boolean = graph.outputSide(id, index)

    internal companion object {
        const val OutputShift = 0
        const val OnBit = 1 shl 4
        const val LockedBit = 1 shl 5
        const val DelayShift = 6
        const val ModeShift = 11
        const val FacingShift = 13
        const val FrontDiodeBit = 1 shl 16
        const val OnStrengthShift = 17
        const val AdjacentShift = 21
        const val FarShift = 26
    }
}
