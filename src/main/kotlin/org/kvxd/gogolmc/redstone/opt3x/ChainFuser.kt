package org.kvxd.gogolmc.redstone.opt3x

object ChainFuser {

    const val MinRun = 3
    const val MaxRun = 63

    fun fuse(graph: Opt3xGraph, minRun: Int = MinRun): Opt3xGraph {
        val count = graph.nodes.size
        val linkType = BooleanArray(count) { id ->
            val node = graph.nodes[id]
            val shape = node.inputs.size == 1 && !node.inputs[0].side
            when {
                !shape -> false
                node.type == NodeType.Repeater -> !node.locked && node.delay in 1..7
                node.type == NodeType.Comparator ->
                    node.adjacentOverride < 0 && node.farOverride < 0
                node.type == NodeType.Torch || node.type == NodeType.WallTorch -> true
                else -> false
            }
        }
        val strictLink = BooleanArray(count) { id ->
            linkType[id] && graph.nodes[id].outputs.size == 1
        }
        val headType = BooleanArray(count) { id ->
            val type = graph.nodes[id].type
            type == NodeType.Repeater || type == NodeType.Torch || type == NodeType.WallTorch
        }

        val claimed = BooleanArray(count)
        val chainOf = IntArray(count) { -1 }
        val chains = ArrayList<List<GraphNode>>()

        fun tryStart(node: GraphNode): Boolean {
            val links = ArrayList<GraphNode>()
            var current = node
            while (true) {
                links += current
                if (links.size >= MaxRun) break
                val edge = current.outputs.firstOrNull() ?: break
                val next = graph.nodes[edge.target]
                if (!linkType[next.id] || claimed[next.id] || next.id == node.id ||
                    next.inputs[0].source != current.id
                ) break
                if (next.outputs.size != 1) {
                    links += next
                    break
                }
                current = next
            }
            while (links.isNotEmpty() && !headType[links[0].id]) links.removeAt(0)
            var strength = 0
            var cut = links.size
            for ((index, link) in links.withIndex()) {
                strength = if (index == 0) {
                    15
                } else {
                    val incoming = strength - link.inputs[0].weight
                    if (incoming <= 0) {
                        cut = index
                        break
                    }
                    if (link.type == NodeType.Comparator) incoming else 15
                }
                link.onStrength = strength
            }
            while (links.size > cut) links.removeAt(links.size - 1)
            if (links.size < minRun) return false
            val index = chains.size
            for (link in links) {
                claimed[link.id] = true
                chainOf[link.id] = index
            }
            chains += links
            return true
        }

        var progress = true
        while (progress) {
            progress = false
            for (node in graph.nodes) {
                if (!strictLink[node.id] || claimed[node.id] || !headType[node.id]) continue
                val previous = node.inputs[0].source
                if (strictLink[previous] && !claimed[previous] && headUpstream(graph, strictLink, headType, claimed, node.id)) continue
                if (tryStart(node)) progress = true
            }
        }

        if (chains.isEmpty()) return graph

        val fused = Opt3xGraph()
        val remap = IntArray(count) { -1 }

        for (node in graph.nodes) {
            val chain = chainOf[node.id]
            if (chain >= 0 && chains[chain][0].id != node.id) continue
            val type = if (chain >= 0) NodeType.Chain else node.type
            val copy = fused.add(node.pos, type, node.state)
            remap[node.id] = copy.id
            val last = if (chain >= 0) chains[chain].last() else node
            copy.output = last.output
            copy.on = last.on
            copy.locked = node.locked
            copy.delay = node.delay
            copy.mode = node.mode
            copy.facing = node.facing
            copy.frontDiode = node.frontDiode
            copy.adjacentOverride = node.adjacentOverride
            copy.farOverride = node.farOverride
            if (chain >= 0) copy.chainLinks = chains[chain]
        }

        for (link in chains.flatten()) {
            if (remap[link.id] < 0) remap[link.id] = remap[chains[chainOf[link.id]][0].id]
        }

        for (node in graph.nodes) {
            val chain = chainOf[node.id]
            val source = remap[node.id]
            if (source < 0) continue
            if (chain >= 0 && chains[chain].last().id != node.id) continue
            val outputs = if (chain >= 0) chains[chain].last().outputs else node.outputs
            for (edge in outputs) {
                val target = remap[edge.target]
                if (target < 0 || target == source) continue
                fused.link(source, target, edge.weight, edge.side)
            }
        }

        return fused
    }

    private fun headUpstream(
        graph: Opt3xGraph,
        strictLink: BooleanArray,
        headType: BooleanArray,
        claimed: BooleanArray,
        start: Int,
    ): Boolean {
        var current = start
        var steps = 0
        while (steps++ < MaxRun) {
            val previous = graph.nodes[current].inputs[0].source
            if (!strictLink[previous] || claimed[previous]) return false
            if (headType[previous]) return true
            current = previous
        }
        return false
    }
}
