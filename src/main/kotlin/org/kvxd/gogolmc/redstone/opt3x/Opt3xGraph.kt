package org.kvxd.gogolmc.redstone.opt3x

import org.kvxd.gogolmc.world.BlockPos

class GraphNode(
    val id: Int,
    val pos: BlockPos,
    val type: Int,
    val state: Int,
) {
    var output: Int = 0
    var on: Boolean = false
    var locked: Boolean = false
    var delay: Int = 0
    var mode: Int = 0
    var facing: Int = -1
    var frontDiode: Boolean = false
    var adjacentOverride: Int = -1
    var farOverride: Int = -1
    var note: Int = 0
    var instrument: Int = 0
    var onStrength: Int = 0
    var chainLinks: List<GraphNode>? = null

    val inputs = ArrayList<GraphEdge>()
    val outputs = ArrayList<GraphEdge>()
}

class GraphEdge(
    val source: Int,
    val target: Int,
    var weight: Int,
    val side: Boolean,
)

class Opt3xGraph {

    val nodes = ArrayList<GraphNode>()
    private val byPos = HashMap<Long, Int>()

    fun add(pos: BlockPos, type: Int, state: Int): GraphNode {
        val node = GraphNode(nodes.size, pos, type, state)
        nodes += node
        byPos[pos.asLong()] = node.id
        return node
    }

    fun idAt(pos: BlockPos): Int = byPos[pos.asLong()] ?: -1

    fun nodeAt(pos: BlockPos): GraphNode? = byPos[pos.asLong()]?.let { nodes[it] }

    fun link(source: Int, target: Int, weight: Int, side: Boolean) {
        if (source < 0 || target < 0 || source == target) return
        val existing = nodes[target].inputs.firstOrNull { it.source == source && it.side == side }
        if (existing != null) {
            if (weight < existing.weight) existing.weight = weight
            return
        }
        val edge = GraphEdge(source, target, weight, side)
        nodes[target].inputs += edge
        nodes[source].outputs += edge
    }

    val edgeCount: Int get() = nodes.sumOf { it.outputs.size }

    fun histogram(): IntArray {
        val counts = IntArray(NodeType.Count)
        for (node in nodes) counts[node.type]++
        return counts
    }
}
