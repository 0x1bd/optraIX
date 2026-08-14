package org.kvxd.optraix.redstone.optraix.graph

import java.util.AbstractList
import org.kvxd.optraix.redstone.optraix.NodeType
import org.kvxd.optraix.redstone.optraix.collection.LongIntMap
import org.kvxd.optraix.world.BlockPos

class OptraIxGraph(expectedNodes: Int = 0) {
    private var capacity = expectedNodes.coerceAtLeast(16)
    private var size = 0
    private var positions = LongArray(capacity)
    private var types = ByteArray(capacity)
    private var states = IntArray(capacity)
    private var metadata = IntArray(capacity)
    private var firstInputs = LongArray(capacity)
    private var firstOutputs = LongArray(capacity)
    private var inputSizes = ShortArray(capacity)
    private var outputSizes = ShortArray(capacity)
    private var extraInputs = arrayOfNulls<LongArray>(capacity)
    private var extraOutputs = arrayOfNulls<LongArray>(capacity)
    private var chains = arrayOfNulls<List<GraphNode>>(capacity)
    private var byPos: LongIntMap? = LongIntMap(maxOf(expectedNodes, 8))

    val nodes: List<GraphNode> = NodeList()

    fun add(pos: BlockPos, type: Int, state: Int): GraphNode {
        ensureCapacity()
        val id = size++
        positions[id] = pos.asLong()
        types[id] = type.toByte()
        states[id] = state
        byPos?.put(positions[id], id) ?: error("graph position index was released")
        return GraphNode(this, id)
    }

    fun idAt(pos: BlockPos): Int = byPos?.get(pos.asLong()) ?: -1

    fun nodeAt(pos: BlockPos): GraphNode? = idAt(pos).takeIf { it >= 0 }?.let { GraphNode(this, it) }

    fun releasePositionIndex() {
        byPos = null
    }

    fun link(source: Int, target: Int, weight: Int, side: Boolean) {
        if (source < 0 || target < 0) return
        for (index in 0 until inputSize(target)) {
            if (inputNode(target, index) != source || inputSide(target, index) != side) continue
            if (weight < inputWeight(target, index)) {
                setInput(target, index, packEdge(source, weight, side))
                for (output in 0 until outputSize(source)) {
                    if (outputNode(source, output) == target && outputSide(source, output) == side) {
                        setOutput(source, output, packEdge(target, weight, side))
                        break
                    }
                }
            }
            return
        }
        addInput(target, packEdge(source, weight, side))
        addOutput(source, packEdge(target, weight, side))
    }

    val edgeCount: Int
        get() {
            var count = 0
            for (id in 0 until size) count += outputSize(id)
            return count
        }

    fun histogram(): IntArray {
        val counts = IntArray(NodeType.Count)
        for (id in 0 until size) counts[type(id)]++
        return counts
    }

    internal fun posKey(id: Int): Long = positions[id]

    internal fun type(id: Int): Int = types[id].toInt()

    internal fun state(id: Int): Int = states[id]

    internal fun bits(id: Int, shift: Int, mask: Int): Int = (metadata[id] ushr shift) and mask

    internal fun setBits(id: Int, shift: Int, mask: Int, value: Int) {
        metadata[id] = (metadata[id] and (mask shl shift).inv()) or ((value and mask) shl shift)
    }

    internal fun flag(id: Int, bit: Int): Boolean = metadata[id] and bit != 0

    internal fun setFlag(id: Int, bit: Int, value: Boolean) {
        metadata[id] = if (value) metadata[id] or bit else metadata[id] and bit.inv()
    }

    internal fun chainLinks(id: Int): List<GraphNode>? = chains[id]

    internal fun setChainLinks(id: Int, value: List<GraphNode>?) {
        chains[id] = value
    }

    internal fun inputSize(id: Int): Int = inputSizes[id].toInt() and 0xFFFF

    internal fun outputSize(id: Int): Int = outputSizes[id].toInt() and 0xFFFF

    internal fun inputNode(id: Int, index: Int): Int = inputAt(id, index).toInt()

    internal fun inputWeight(id: Int, index: Int): Int = weight(inputAt(id, index))

    internal fun inputSide(id: Int, index: Int): Boolean = side(inputAt(id, index))

    internal fun outputNode(id: Int, index: Int): Int = outputAt(id, index).toInt()

    internal fun outputWeight(id: Int, index: Int): Int = weight(outputAt(id, index))

    internal fun outputSide(id: Int, index: Int): Boolean = side(outputAt(id, index))

    private fun inputAt(id: Int, index: Int): Long = if (index == 0) firstInputs[id] else extraInputs[id]!![index - 1]

    private fun outputAt(id: Int, index: Int): Long = if (index == 0) firstOutputs[id] else extraOutputs[id]!![index - 1]

    private fun setInput(id: Int, index: Int, value: Long) {
        if (index == 0) firstInputs[id] = value else extraInputs[id]!![index - 1] = value
    }

    private fun setOutput(id: Int, index: Int, value: Long) {
        if (index == 0) firstOutputs[id] = value else extraOutputs[id]!![index - 1] = value
    }

    private fun addInput(id: Int, value: Long) {
        val count = inputSize(id)
        if (count == 0) firstInputs[id] = value else {
            val index = count - 1
            var data = extraInputs[id]
            if (data == null || index == data.size) {
                data = data?.copyOf(nextCapacity(index)) ?: LongArray(1)
                extraInputs[id] = data
            }
            data[index] = value
        }
        inputSizes[id] = (count + 1).toShort()
    }

    private fun addOutput(id: Int, value: Long) {
        val count = outputSize(id)
        if (count == 0) firstOutputs[id] = value else {
            val index = count - 1
            var data = extraOutputs[id]
            if (data == null || index == data.size) {
                data = data?.copyOf(nextCapacity(index)) ?: LongArray(1)
                extraOutputs[id] = data
            }
            data[index] = value
        }
        outputSizes[id] = (count + 1).toShort()
    }

    private fun ensureCapacity() {
        if (size < capacity) return
        capacity += capacity shr 1
        positions = positions.copyOf(capacity)
        types = types.copyOf(capacity)
        states = states.copyOf(capacity)
        metadata = metadata.copyOf(capacity)
        firstInputs = firstInputs.copyOf(capacity)
        firstOutputs = firstOutputs.copyOf(capacity)
        inputSizes = inputSizes.copyOf(capacity)
        outputSizes = outputSizes.copyOf(capacity)
        extraInputs = extraInputs.copyOf(capacity)
        extraOutputs = extraOutputs.copyOf(capacity)
        chains = chains.copyOf(capacity)
    }

    private fun nextCapacity(size: Int): Int = if (size == 0) 1 else size * 2

    private fun packEdge(node: Int, weight: Int, side: Boolean): Long =
        (node.toLong() and 0xFFFFFFFFL) or
            ((weight.toLong() and WeightMask) shl WeightShift) or
            (if (side) SideBit else 0L)

    private fun weight(edge: Long): Int = ((edge ushr WeightShift) and WeightMask).toInt()

    private fun side(edge: Long): Boolean = edge and SideBit != 0L

    private inner class NodeList : AbstractList<GraphNode>() {
        override val size: Int
            get() = this@OptraIxGraph.size

        override fun get(index: Int): GraphNode = GraphNode(this@OptraIxGraph, index)
    }

    private companion object {
        const val WeightShift = 32
        const val WeightMask = 15L
        const val SideBit = 1L shl 36
    }
}
