package org.kvxd.optraix.redstone.optraix

import org.kvxd.optraix.block.BlockKind
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.BlockFace
import org.kvxd.optraix.block.property.ComparatorMode
import org.kvxd.optraix.block.property.LeverFace
import org.kvxd.optraix.redstone.mchprs.Comparator
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.redstone.mchprs.Wire
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.SECTION_COUNT
import org.kvxd.optraix.world.WORLD_MIN_Y
import org.kvxd.optraix.world.World

class OptraIxCompileException(message: String) : RuntimeException(message)

object OptraIxCompiler {

    fun compile(
        world: GameWorld,
        eliminateWire: Boolean = true,
        fuseChains: Boolean = true,
    ): OptraIxCircuit {
        val graph = OptraIxGraph()
        scan(world, graph)
        for (node in graph.nodes) buildEdges(world, graph, node)
        var resolved = if (eliminateWire) compact(eliminateWires(graph)) else graph
        if (fuseChains) resolved = ChainFuser.fuse(resolved)
        val circuit = flatten(resolved)
        for (entry in world.snapshotTicks()) {
            val slot = circuit.linkIndex[entry.pos.asLong()]
            if (slot != null) {
                circuit.importPendingLinkTick(slot, entry.ticksLeft, entry.priority.ordinal)
                continue
            }
            val node = circuit.nodeAt(entry.pos)
            if (node < 0) continue
            circuit.importPendingTick(node, entry.ticksLeft, entry.priority.ordinal)
        }
        return circuit
    }

    private fun scan(world: GameWorld, graph: OptraIxGraph) {
        for (chunk in world.snapshotChunks()) {
            for (sectionIndex in 0 until SECTION_COUNT) {
                val section = chunk.sections[sectionIndex] ?: continue
                if (section.blockCount == 0) continue
                for (slot in 0 until 4096) {
                    val state = section.get(slot)
                    val type = typeOf(state)
                    if (type < 0) continue
                    val pos = BlockPos(
                        chunk.x * 16 + (slot and 15),
                        WORLD_MIN_Y + (sectionIndex shl 4) + (slot shr 8),
                        chunk.z * 16 + ((slot shr 4) and 15),
                    )
                    initialise(world, graph.add(pos, type, state))
                }
            }
        }
    }

    private fun typeOf(state: Int): Int {
        if (BlockStates.pressurePlatePowered(state) != null) return NodeType.PressurePlate
        return when (BlockStates.kindOf(state)) {
            BlockKind.RedstoneWire -> NodeType.Wire
            BlockKind.Repeater -> NodeType.Repeater
            BlockKind.Comparator -> NodeType.Comparator
            BlockKind.RedstoneTorch -> NodeType.Torch
            BlockKind.RedstoneWallTorch -> NodeType.WallTorch
            BlockKind.RedstoneLamp -> NodeType.Lamp
            BlockKind.Lever -> NodeType.Lever
            BlockKind.Button -> NodeType.Button
            BlockKind.RedstoneBlock -> NodeType.Constant
            BlockKind.IronTrapdoor -> NodeType.Trapdoor
            BlockKind.NoteBlock -> NodeType.NoteBlock
            BlockKind.Observer, BlockKind.TripwireHook ->
                throw OptraIxCompileException("${Blocks.nameOf(state)} is not supported by optraix")
            else -> -1
        }
    }

    private fun initialise(world: World, node: GraphNode) {
        val state = node.state
        when (node.type) {
            NodeType.Wire -> node.output = BlockStates.wirePower[state].toInt()

            NodeType.Repeater -> {
                val facing = BlockStates.directionOf(state)
                    ?: throw OptraIxCompileException("repeater without facing at ${node.pos}")
                node.facing = facing.ordinal
                node.delay = BlockStates.delay[state].toInt()
                node.locked = BlockStates.locked[state]
                node.on = BlockStates.powered[state]
                node.output = if (node.on) 15 else 0
                node.frontDiode = MchprsRedstone.isDiode(
                    world.getBlock(node.pos.offset(facing.opposite().blockFace()))
                )
            }

            NodeType.Comparator -> {
                val facing = BlockStates.directionOf(state)
                    ?: throw OptraIxCompileException("comparator without facing at ${node.pos}")
                node.facing = facing.ordinal
                node.mode = BlockStates.comparatorModeOf(state).ordinal
                node.on = BlockStates.powered[state]
                node.output = (world.getBlockEntity(node.pos) as? BlockEntity.Comparator)?.outputStrength ?: 0
                node.frontDiode = MchprsRedstone.isDiode(
                    world.getBlock(node.pos.offset(facing.opposite().blockFace()))
                )
            }

            NodeType.Torch -> {
                node.on = BlockStates.lit[state]
                node.output = if (node.on) 15 else 0
            }

            NodeType.WallTorch -> {
                val facing = BlockStates.directionOf(state)
                    ?: throw OptraIxCompileException("wall torch without facing at ${node.pos}")
                node.facing = facing.ordinal
                node.on = BlockStates.lit[state]
                node.output = if (node.on) 15 else 0
            }

            NodeType.Lamp -> node.on = BlockStates.lit[state]

            NodeType.Lever -> {
                node.facing = (BlockStates.directionOf(state) ?: BlockDirection.North).ordinal
                node.on = BlockStates.powered[state]
                node.output = if (node.on) 15 else 0
            }

            NodeType.Button -> {
                node.facing = (BlockStates.directionOf(state) ?: BlockDirection.North).ordinal
                node.on = BlockStates.powered[state]
                node.output = if (node.on) 15 else 0
                node.delay = BlockStates.buttonDuration(state)
            }

            NodeType.PressurePlate -> {
                node.on = BlockStates.powered[state]
                node.output = if (node.on) 15 else 0
            }

            NodeType.Constant -> {
                node.on = true
                node.output = 15
            }

            NodeType.Trapdoor -> node.on = BlockStates.powered[state]

            NodeType.NoteBlock -> {
                node.on = BlockStates.powered[state]
                node.delay = BlockStates.note[state].toInt()
                node.mode = BlockStates.instrumentOf(state).ordinal
            }
        }
    }

    private fun emitsWeak(world: World, state: Int, pos: BlockPos, side: BlockFace, dustPower: Boolean): Boolean {
        if (BlockStates.pressurePlatePowered(state) != null) return true
        return when (BlockStates.kindOf(state)) {
            BlockKind.RedstoneTorch -> side != BlockFace.Top
            BlockKind.RedstoneWallTorch -> {
                val facing = BlockStates.directionOf(state)
                facing != null && facing.blockFace() != side
            }
            BlockKind.RedstoneBlock, BlockKind.Lever, BlockKind.Button -> true
            BlockKind.Repeater, BlockKind.Comparator ->
                BlockStates.directionOf(state)?.blockFace() == side
            BlockKind.RedstoneWire -> when {
                !dustPower -> false
                side == BlockFace.Top -> true
                side == BlockFace.Bottom -> false
                else -> {
                    val direction = side.unwrapDirection()
                    val regulated = Wire.getRegulatedSides(state, world, pos)
                    !Wire.getCurrentSide(regulated, direction.opposite()).isNone
                }
            }
            else -> false
        }
    }

    private fun emitsStrong(world: World, state: Int, pos: BlockPos, side: BlockFace, dustPower: Boolean): Boolean {
        if (BlockStates.pressurePlatePowered(state) != null) return side == BlockFace.Top
        return when (BlockStates.kindOf(state)) {
            BlockKind.RedstoneTorch, BlockKind.RedstoneWallTorch -> side == BlockFace.Bottom
            BlockKind.Lever, BlockKind.Button -> {
                val face = BlockStates.leverFaceOf(state)
                val facing = BlockStates.directionOf(state)
                when (side) {
                    BlockFace.Top -> face == LeverFace.Floor
                    BlockFace.Bottom -> face == LeverFace.Ceiling
                    else -> face == LeverFace.Wall && facing == side.unwrapDirection()
                }
            }
            BlockKind.RedstoneWire, BlockKind.Repeater, BlockKind.Comparator ->
                emitsWeak(world, state, pos, side, dustPower)
            else -> false
        }
    }

    private fun addPowerSources(
        world: World,
        graph: OptraIxGraph,
        target: Int,
        pos: BlockPos,
        face: BlockFace,
        dustPower: Boolean,
        side: Boolean,
        weight: Int,
    ) {
        val state = world.getBlock(pos)
        if (BlockStates.isSolid(state)) {
            for (probe in BlockFace.All) {
                val offset = pos.offset(probe)
                val neighbor = world.getBlock(offset)
                if (!emitsStrong(world, neighbor, offset, probe, dustPower)) continue
                val source = graph.idAt(offset)
                if (source < 0) throw OptraIxCompileException("unmapped power source at $offset")
                graph.link(source, target, weight, side)
            }
        } else if (emitsWeak(world, state, pos, face, dustPower)) {
            val source = graph.idAt(pos)
            if (source < 0) throw OptraIxCompileException("unmapped power source at $pos")
            graph.link(source, target, weight, side)
        }
    }

    private fun buildEdges(world: World, graph: OptraIxGraph, node: GraphNode) {
        when (node.type) {
            NodeType.Wire -> buildWireEdges(world, graph, node)
            NodeType.Repeater -> buildRepeaterEdges(world, graph, node)
            NodeType.Comparator -> buildComparatorEdges(world, graph, node)
            NodeType.Torch -> addPowerSources(
                world, graph, node.id, node.pos.offset(BlockFace.Bottom),
                BlockFace.Top, dustPower = true, side = false, weight = 0,
            )
            NodeType.WallTorch -> {
                val facing = BlockDirection.Values[node.facing]
                val wall = facing.opposite().blockFace()
                addPowerSources(
                    world, graph, node.id, node.pos.offset(wall),
                    wall, dustPower = true, side = false, weight = 0,
                )
            }
            NodeType.Lamp, NodeType.Trapdoor, NodeType.NoteBlock -> {
                for (face in BlockFace.All) {
                    addPowerSources(
                        world, graph, node.id, node.pos.offset(face),
                        face, dustPower = true, side = false, weight = 0,
                    )
                }
            }
        }
    }

    private fun buildWireEdges(world: World, graph: OptraIxGraph, node: GraphNode) {
        val pos = node.pos
        val above = world.getBlock(pos.offset(BlockFace.Top))
        for (face in BlockFace.All) {
            val neighborPos = pos.offset(face)
            val neighbor = world.getBlock(neighborPos)
            if (BlockStates.kindOf(neighbor) == BlockKind.RedstoneWire) {
                graph.link(graph.idAt(neighborPos), node.id, 1, false)
            }
            addPowerSources(
                world, graph, node.id, neighborPos, face,
                dustPower = false, side = false, weight = 0,
            )
            if (!face.isHorizontal) continue
            if (!BlockStates.isSolid(above) && !BlockStates.isTransparent(neighbor)) {
                val upPos = neighborPos.offset(BlockFace.Top)
                if (BlockStates.kindOf(world.getBlock(upPos)) == BlockKind.RedstoneWire) {
                    graph.link(graph.idAt(upPos), node.id, 1, false)
                }
            }
            if (!BlockStates.isSolid(neighbor)) {
                val downPos = neighborPos.offset(BlockFace.Bottom)
                if (BlockStates.kindOf(world.getBlock(downPos)) == BlockKind.RedstoneWire) {
                    graph.link(graph.idAt(downPos), node.id, 1, false)
                }
            }
        }
    }

    private fun buildRepeaterEdges(world: World, graph: OptraIxGraph, node: GraphNode) {
        val facing = BlockDirection.Values[node.facing]
        val inputPos = node.pos.offset(facing.blockFace())
        addPowerSources(
            world, graph, node.id, inputPos, facing.blockFace(),
            dustPower = true, side = false, weight = 0,
        )
        if (BlockStates.kindOf(world.getBlock(inputPos)) == BlockKind.RedstoneWire) {
            graph.link(graph.idAt(inputPos), node.id, 0, false)
        }
        for (side in arrayOf(facing.rotate(), facing.rotateCcw())) {
            val sidePos = node.pos.offset(side.blockFace())
            val sideState = world.getBlock(sidePos)
            if (!MchprsRedstone.isDiode(sideState)) continue
            if (!emitsWeak(world, sideState, sidePos, side.blockFace(), false)) continue
            graph.link(graph.idAt(sidePos), node.id, 0, true)
        }
    }

    private fun buildComparatorEdges(world: World, graph: OptraIxGraph, node: GraphNode) {
        val facing = BlockDirection.Values[node.facing]
        val face = facing.blockFace()
        val inputPos = node.pos.offset(face)
        val inputState = world.getBlock(inputPos)

        if (Comparator.hasOverride(inputState)) {
            node.adjacentOverride = Comparator.getOverride(inputState, world, inputPos)
        } else {
            addPowerSources(
                world, graph, node.id, inputPos, face,
                dustPower = true, side = false, weight = 0,
            )
            if (BlockStates.kindOf(inputState) == BlockKind.RedstoneWire) {
                graph.link(graph.idAt(inputPos), node.id, 0, false)
            }
            if (BlockStates.isSolid(inputState)) {
                val farPos = inputPos.offset(face)
                val farState = world.getBlock(farPos)
                if (Comparator.hasOverride(farState)) {
                    node.farOverride = Comparator.getOverride(farState, world, farPos)
                }
            }
        }

        for (side in arrayOf(facing.rotate(), facing.rotateCcw())) {
            val sidePos = node.pos.offset(side.blockFace())
            val sideState = world.getBlock(sidePos)
            when {
                MchprsRedstone.isDiode(sideState) -> {
                    if (emitsWeak(world, sideState, sidePos, side.blockFace(), false)) {
                        graph.link(graph.idAt(sidePos), node.id, 0, true)
                    }
                }
                BlockStates.kindOf(sideState) == BlockKind.RedstoneWire ->
                    graph.link(graph.idAt(sidePos), node.id, 0, true)
                BlockStates.kindOf(sideState) == BlockKind.RedstoneBlock ->
                    graph.link(graph.idAt(sidePos), node.id, 0, true)
            }
        }
    }

    private fun eliminateWires(graph: OptraIxGraph): OptraIxGraph {
        val count = graph.nodes.size
        val isWire = BooleanArray(count) { graph.nodes[it].type == NodeType.Wire }
        if (!isWire.any { it }) return graph

        val collected = ArrayList<GraphEdge>()
        val dist = IntArray(count)
        val distStamp = IntArray(count) { -1 }
        val bestDefault = IntArray(count)
        val bestDefaultStamp = IntArray(count) { -1 }
        val bestSide = IntArray(count)
        val bestSideStamp = IntArray(count) { -1 }
        val deque = java.util.ArrayDeque<Int>()
        var stamp = 0

        for (source in graph.nodes) {
            if (isWire[source.id]) continue
            val version = stamp++
            deque.clear()

            fun reach(target: Int, weight: Int, side: Boolean) {
                if (weight >= MaxSignal) return
                if (side) {
                    if (bestSideStamp[target] == version && bestSide[target] <= weight) return
                    bestSideStamp[target] = version
                    bestSide[target] = weight
                } else {
                    if (bestDefaultStamp[target] == version && bestDefault[target] <= weight) return
                    bestDefaultStamp[target] = version
                    bestDefault[target] = weight
                }
                collected += GraphEdge(source.id, target, weight, side)
            }

            for (edge in source.outputs) {
                val target = edge.target
                if (!isWire[target]) {
                    reach(target, edge.weight, edge.side)
                    continue
                }
                if (edge.weight >= MaxSignal) continue
                if (distStamp[target] == version && dist[target] <= edge.weight) continue
                distStamp[target] = version
                dist[target] = edge.weight
                if (edge.weight == 0) deque.addFirst(target) else deque.addLast(target)
            }

            while (deque.isNotEmpty()) {
                val wire = deque.pollFirst()
                val current = dist[wire]
                for (edge in graph.nodes[wire].outputs) {
                    val target = edge.target
                    val next = current + edge.weight
                    if (next >= MaxSignal) continue
                    if (!isWire[target]) {
                        reach(target, next, edge.side)
                        continue
                    }
                    if (distStamp[target] == version && dist[target] <= next) continue
                    distStamp[target] = version
                    dist[target] = next
                    if (edge.weight == 0) deque.addFirst(target) else deque.addLast(target)
                }
            }
        }

        for (node in graph.nodes) {
            node.inputs.clear()
            node.outputs.clear()
        }
        for (edge in collected) graph.link(edge.source, edge.target, edge.weight, edge.side)
        return graph
    }

    private fun compact(graph: OptraIxGraph): OptraIxGraph {
        val compacted = OptraIxGraph()
        val remap = IntArray(graph.nodes.size) { -1 }

        for (node in graph.nodes) {
            if (node.type == NodeType.Wire) continue
            val copy = compacted.add(node.pos, node.type, node.state)
            remap[node.id] = copy.id
            copy.output = node.output
            copy.on = node.on
            copy.locked = node.locked
            copy.delay = node.delay
            copy.mode = node.mode
            copy.facing = node.facing
            copy.frontDiode = node.frontDiode
            copy.adjacentOverride = node.adjacentOverride
            copy.farOverride = node.farOverride
        }

        for (node in graph.nodes) {
            val source = remap[node.id]
            if (source < 0) continue
            for (edge in node.outputs) {
                val target = remap[edge.target]
                if (target < 0) continue
                compacted.link(source, target, edge.weight, edge.side)
            }
        }
        return compacted
    }

    private const val MaxSignal = 15

    private fun fanOutOrder(graph: OptraIxGraph, source: GraphNode): java.util.Comparator<GraphEdge> =
        compareBy<GraphEdge> { faceRank(source.pos, graph.nodes[it.target].pos) }
            .thenBy { graph.nodes[it.target].pos.asLong() }

    private fun localityOrder(graph: OptraIxGraph): IntArray {
        val count = graph.nodes.size
        val order = IntArray(count)
        val rank = IntArray(count) { -1 }
        val queue = IntArray(count)
        var next = 0

        for (seed in 0 until count) {
            if (rank[seed] >= 0) continue
            var head = 0
            var tail = 0
            rank[seed] = next
            order[next++] = seed
            queue[tail++] = seed
            while (head < tail) {
                val node = graph.nodes[queue[head++]]
                for (edge in node.outputs) {
                    if (rank[edge.target] >= 0) continue
                    rank[edge.target] = next
                    order[next++] = edge.target
                    queue[tail++] = edge.target
                }
                for (edge in node.inputs) {
                    if (rank[edge.source] >= 0) continue
                    rank[edge.source] = next
                    order[next++] = edge.source
                    queue[tail++] = edge.source
                }
            }
        }
        return rank
    }

    private fun faceRank(source: BlockPos, target: BlockPos): Int {
        val dx = target.x - source.x
        val dy = target.y - source.y
        val dz = target.z - source.z
        return when {
            dx == 0 && dy == 1 && dz == 0 -> 0
            dx == 0 && dy == -1 && dz == 0 -> 1
            dx == 0 && dy == 0 && dz == -1 -> 2
            dx == 0 && dy == 0 && dz == 1 -> 3
            dx == -1 && dy == 0 && dz == 0 -> 4
            dx == 1 && dy == 0 && dz == 0 -> 5
            else -> 6
        }
    }

    private fun flatten(graph: OptraIxGraph): OptraIxCircuit {
        val count = graph.nodes.size
        val posKey = LongArray(count)
        val baseState = IntArray(count)
        val delayData = ByteArray(count)
        val modeData = ByteArray(count)
        val facingData = ByteArray(count)
        val adjacentOverride = ByteArray(count)
        val farOverride = ByteArray(count)
        val state = LongArray(count)
        val index = HashMap<Long, Int>(count * 2)

        val rank = localityOrder(graph)

        val edgeStart = IntArray(count + 1)
        for (node in graph.nodes) edgeStart[rank[node.id]] = node.outputs.size
        var total = 0
        for (slot in 0 until count) {
            val size = edgeStart[slot]
            edgeStart[slot] = total
            total += size
        }
        edgeStart[count] = total

        val edges = IntArray(total)
        val defaultInputs = IntArray(count)
        val sideInputs = IntArray(count)
        for (node in graph.nodes) {
            for (edge in node.inputs) {
                if (edge.side) sideInputs[rank[node.id]]++ else defaultInputs[rank[node.id]]++
            }
        }

        val histBase = IntArray(count) { OptraIxCircuit.NoHistogram }
        var histogramNodes = 0
        for (id in 0 until count) {
            if (defaultInputs[id] <= 1 && sideInputs[id] <= 1) continue
            histBase[id] = histogramNodes * OptraIxCircuit.HistogramStride
            histogramNodes++
        }
        val counts = ByteArray(histogramNodes * OptraIxCircuit.HistogramStride)

        var chainTotal = 0
        var linkTotal = 0
        for (node in graph.nodes) {
            val links = node.chainLinks ?: continue
            chainTotal++
            linkTotal += links.size
        }
        val chainIndexOf = IntArray(count) { -1 }
        val chainNodeOf = IntArray(chainTotal)
        val chainOffset = IntArray(chainTotal)
        val chainLength = IntArray(chainTotal)
        val chainPowered = LongArray(chainTotal)
        val linkChainOf = IntArray(linkTotal)
        val linkKind = ByteArray(linkTotal)
        val linkPos = LongArray(linkTotal)
        val linkFacing = ByteArray(linkTotal)
        val linkOn = ByteArray(linkTotal)
        val linkIndex = HashMap<Long, Int>(linkTotal * 2)
        var chainCursor = 0
        var linkCursor = 0
        for (node in graph.nodes) {
            val links = node.chainLinks ?: continue
            val id = rank[node.id]
            val chain = chainCursor++
            chainIndexOf[id] = chain
            chainNodeOf[chain] = id
            chainOffset[chain] = linkCursor
            chainLength[chain] = links.size
            var powered = 0L
            for ((offset, link) in links.withIndex()) {
                val slot = linkCursor + offset
                linkChainOf[slot] = chain
                var kind = when (link.type) {
                    NodeType.Repeater -> link.delay or
                        (OptraIxCircuit.LinkRepeater shl OptraIxCircuit.LinkTypeShift)
                    NodeType.Comparator -> (OptraIxCircuit.LinkComparator shl OptraIxCircuit.LinkTypeShift) or
                        (if (link.mode == ComparatorMode.Subtract.ordinal) OptraIxCircuit.SubtractLink else 0)
                    NodeType.Torch -> OptraIxCircuit.LinkTorch shl OptraIxCircuit.LinkTypeShift
                    else -> OptraIxCircuit.LinkWallTorch shl OptraIxCircuit.LinkTypeShift
                }
                if (link.frontDiode) kind = kind or OptraIxCircuit.FrontDiodeLink
                linkKind[slot] = kind.toByte()
                linkPos[slot] = link.pos.asLong()
                linkFacing[slot] = link.facing.toByte()
                linkOn[slot] = link.onStrength.toByte()
                linkIndex[link.pos.asLong()] = slot
                if (link.on) powered = powered or (1L shl offset)
            }
            chainPowered[chain] = powered
            linkCursor += links.size
        }

        for (node in graph.nodes) {
            val id = rank[node.id]
            posKey[id] = node.pos.asLong()
            baseState[id] = node.state
            delayData[id] = node.delay.toByte()
            modeData[id] = node.mode.toByte()
            facingData[id] = node.facing.toByte()
            adjacentOverride[id] = node.adjacentOverride.toByte()
            farOverride[id] = node.farOverride.toByte()
            index[node.pos.asLong()] = id
            state[id] = OptraIxCircuit.pack(
                type = node.type,
                output = node.output,
                on = node.on,
                locked = node.locked,
                frontDiode = node.frontDiode,
                delay = node.delay,
                compare = node.type == NodeType.Comparator &&
                    node.mode == ComparatorMode.Compare.ordinal,
            )

            var cursor = edgeStart[id]
            for (edge in node.outputs.sortedWith(fanOutOrder(graph, node))) {
                val target = rank[edge.target]
                val solo = if (edge.side) sideInputs[target] == 1 else defaultInputs[target] == 1
                edges[cursor] = OptraIxCircuit.packEdge(target, edge.weight, edge.side, solo)
                cursor++
            }
        }

        return OptraIxCircuit(
            count = count,
            posKey = posKey,
            baseState = baseState,
            delayData = delayData,
            modeData = modeData,
            facingData = facingData,
            adjacentOverride = adjacentOverride,
            farOverride = farOverride,
            edgeStart = edgeStart,
            edges = edges,
            index = index,
            state = state,
            histBase = histBase,
            counts = counts,
            chainIndexOf = chainIndexOf,
            chainNodeOf = chainNodeOf,
            chainOffset = chainOffset,
            chainLength = chainLength,
            chainPowered = chainPowered,
            linkChainOf = linkChainOf,
            linkKind = linkKind,
            linkPos = linkPos,
            linkFacing = linkFacing,
            linkOn = linkOn,
            linkIndex = linkIndex,
        )
    }
}
