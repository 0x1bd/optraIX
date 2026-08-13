package org.kvxd.optraix.redstone.optraix.graph

import org.kvxd.optraix.world.BlockPos

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
    var onStrength: Int = 0
    var chainLinks: List<GraphNode>? = null

    val inputs = ArrayList<GraphEdge>(0)
    val outputs = ArrayList<GraphEdge>(0)
}
