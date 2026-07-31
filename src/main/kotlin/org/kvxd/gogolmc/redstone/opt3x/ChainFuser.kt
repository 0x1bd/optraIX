package org.kvxd.gogolmc.redstone.opt3x

import org.kvxd.gogolmc.block.property.ComparatorMode

object ChainFuser {

    const val MinLength = 3
    const val MaxLength = 63

    fun fuse(graph: Opt3xGraph, minLength: Int = MinLength): Opt3xGraph {
        val count = graph.nodes.size
        val isLink = BooleanArray(count) { id ->
            val node = graph.nodes[id]
            val shape = node.outputs.size == 1 && node.inputs.size == 1 && !node.inputs[0].side
            when {
                !shape -> false
                node.type == NodeType.Repeater -> !node.locked && node.delay in 1..7
                node.type == NodeType.Comparator ->
                    node.mode == ComparatorMode.Compare.ordinal &&
                        node.adjacentOverride < 0 &&
                        node.farOverride < 0
                else -> false
            }
        }

        val chainOf = IntArray(count) { -1 }
        val chains = ArrayList<List<GraphNode>>()

        var progress = true
        while (progress) {
            progress = false
            for (node in graph.nodes) {
            if (!isLink[node.id] || chainOf[node.id] >= 0) continue
            val previous = node.inputs[0].source
            if (isLink[previous] && chainOf[previous] < 0) continue

            val links = ArrayList<GraphNode>()
            var current: GraphNode? = node
            while (current != null && isLink[current.id] && chainOf[current.id] < 0 && links.size < MaxLength) {
                links += current
                val next = graph.nodes[current.outputs[0].target]
                current = if (isLink[next.id] && next.inputs[0].source == current.id) next else null
            }
            while (links.isNotEmpty() && links.last().type != NodeType.Repeater) links.removeAt(links.size - 1)
            while (links.isNotEmpty() && links[0].type != NodeType.Repeater) links.removeAt(0)
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
                    if (link.type == NodeType.Repeater) 15 else incoming
                }
                link.onStrength = strength
            }
            while (links.size > cut) links.removeAt(links.size - 1)
            while (links.isNotEmpty() && links.last().type != NodeType.Repeater) links.removeAt(links.size - 1)
            if (links.size < minLength) continue
            val index = chains.size
            for (link in links) chainOf[link.id] = index
            chains += links
            progress = true
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
            copy.output = if (chain >= 0) chains[chain].last().output else node.output
            copy.on = if (chain >= 0) chains[chain].last().on else node.on
            copy.locked = node.locked
            copy.delay = node.delay
            copy.mode = node.mode
            copy.facing = node.facing
            copy.frontDiode = if (chain >= 0) chains[chain].last().frontDiode else node.frontDiode
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
}
