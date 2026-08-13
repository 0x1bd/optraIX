package org.kvxd.optraix.redstone.optraix.graph

import org.kvxd.optraix.redstone.optraix.NodeType
import org.kvxd.optraix.redstone.optraix.collection.LongIntMap
import org.kvxd.optraix.world.BlockPos

class OptraIxGraph {

    val nodes = ArrayList<GraphNode>()
    private val byPos = LongIntMap()

    fun add(pos: BlockPos, type: Int, state: Int): GraphNode {
        val node = GraphNode(nodes.size, pos, type, state)
        nodes += node
        byPos.put(pos.asLong(), node.id)
        return node
    }

    fun idAt(pos: BlockPos): Int = byPos[pos.asLong()]

    fun nodeAt(pos: BlockPos): GraphNode? = byPos[pos.asLong()].takeIf { it >= 0 }?.let { nodes[it] }

    fun link(source: Int, target: Int, weight: Int, side: Boolean) {
        if (source < 0 || target < 0) return
        val inputs = nodes[target].inputs
        for (index in inputs.indices) {
            val existing = inputs[index]
            if (existing.source != source || existing.side != side) continue
            if (weight < existing.weight) existing.weight = weight
            return
        }
        val edge = GraphEdge(source, target, weight, side)
        inputs += edge
        nodes[source].outputs += edge
    }

    val edgeCount: Int get() = nodes.sumOf { it.outputs.size }

    fun histogram(): IntArray {
        val counts = IntArray(NodeType.Count)
        for (node in nodes) counts[node.type]++
        return counts
    }
}
