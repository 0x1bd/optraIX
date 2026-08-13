package org.kvxd.optraix.redstone.optraix.compiler

import org.kvxd.optraix.block.property.blockFace
import org.kvxd.optraix.block.property.opposite
import org.kvxd.optraix.block.property.rotate
import org.kvxd.optraix.block.property.rotateCcw
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.property.BlockFace
import org.kvxd.optraix.redstone.mchprs.Comparator
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.redstone.mchprs.Wire
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.ChunkPos
import org.kvxd.optraix.world.ChunkSection
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.SECTION_COUNT
import org.kvxd.optraix.world.WORLD_MIN_Y
import org.kvxd.optraix.world.World
import org.kvxd.optraix.block.mcData
import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.ComparatorMode
import org.kvxd.optraix.block.property.LeverFace
import org.kvxd.optraix.block.property.isNone
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.redstone.optraix.NodeType
import org.kvxd.optraix.redstone.optraix.OptraIxCircuit
import org.kvxd.optraix.redstone.optraix.collection.IntBuffer
import org.kvxd.optraix.redstone.optraix.collection.IntDeque
import org.kvxd.optraix.redstone.optraix.collection.LongBuffer
import org.kvxd.optraix.redstone.optraix.collection.LongIntMap
import org.kvxd.optraix.redstone.optraix.graph.ChainFuser
import org.kvxd.optraix.redstone.optraix.graph.GraphEdge
import org.kvxd.optraix.redstone.optraix.graph.GraphNode
import org.kvxd.optraix.redstone.optraix.graph.OptraIxGraph

internal fun isComponentCandidate(state: Int): Boolean {
    if (BlockStates.pressurePlatePowered(state) != null || BlockStates.isButton(state)) return true
    return when (BlockStates.typeOf(state)) {
        Blocks.RedstoneWire, Blocks.Repeater, Blocks.Comparator,
        Blocks.RedstoneTorch, Blocks.RedstoneWallTorch, Blocks.RedstoneLamp,
        Blocks.Lever, Blocks.RedstoneBlock, Blocks.IronTrapdoor, Blocks.NoteBlock,
        Blocks.Observer, Blocks.TripwireHook -> true
        else -> false
    }
}

internal fun sectionHasCandidates(section: ChunkSection): Boolean {
    if (section.isDirect) return true
    for (index in 0 until section.paletteSize) {
        if (isComponentCandidate(section.palette[index])) return true
    }
    return false
}

object OptraIxCompiler {

    const val DefaultRegionChunks = 32

    fun compile(
        world: GameWorld,
        eliminateWire: Boolean = true,
        fuseChains: Boolean = true,
        regionChunks: Int = DefaultRegionChunks,
        stageListener: ((String, OptraIxGraph?) -> Unit)? = null,
        cancelled: () -> Boolean = { false },
    ): OptraIxCircuit {
        checkCancelled(cancelled)
        val graph = OptraIxGraph()
        val wires = if (eliminateWire) WireIndex() else null
        scan(world, graph, wires, cancelled)
        checkCancelled(cancelled)
        stageListener?.invoke("scan", graph)
        if (wires == null) {
            val sink = GraphSink(graph)
            for (node in graph.nodes) buildEdges(world, sink, node, node.id)
        } else {
            resolveThroughWires(world, graph, wires, regionChunks, cancelled)
        }
        checkCancelled(cancelled)
        stageListener?.invoke("buildEdges", graph)
        var resolved = graph
        if (fuseChains) resolved = ChainFuser.fuse(resolved)
        checkCancelled(cancelled)
        stageListener?.invoke("fuse", resolved)
        val circuit = flatten(resolved)
        checkCancelled(cancelled)
        stageListener?.invoke("flatten", null)
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

    private fun checkCancelled(cancelled: () -> Boolean) {
        if (cancelled()) throw OptraIxCompileException("compile cancelled")
    }

    private fun scan(
        world: GameWorld,
        graph: OptraIxGraph,
        wires: WireIndex?,
        cancelled: () -> Boolean,
    ) {
        val wireSlots = ShortArray(4096)
        for (chunk in world.snapshotChunks()) {
            checkCancelled(cancelled)
            for (sectionIndex in 0 until SECTION_COUNT) {
                val section = chunk.sections[sectionIndex] ?: continue
                if (section.blockCount == 0) continue
                if (!sectionHasCandidates(section)) continue
                var wireCount = 0
                section.forEachState { slot, state ->
                    val type = if (isComponentCandidate(state)) typeOf(state) else -1
                    if (type == NodeType.Wire && wires != null) {
                        wireSlots[wireCount++] = slot.toShort()
                    } else if (type >= 0) {
                        val pos = BlockPos(
                            chunk.x * 16 + (slot and 15),
                            WORLD_MIN_Y + (sectionIndex shl 4) + (slot shr 8),
                            chunk.z * 16 + ((slot shr 4) and 15),
                        )
                        initialise(world, graph.add(pos, type, state))
                    }
                }
                if (wireCount > 0) wires?.add(chunk.x, chunk.z, sectionIndex, wireSlots.copyOf(wireCount))
            }
        }
    }

    private interface EdgeSink {
        fun resolve(pos: BlockPos): Int
        fun edge(source: Int, target: Int, weight: Int, side: Boolean)
        fun unmapped(pos: BlockPos)
    }

    private class GraphSink(private val graph: OptraIxGraph) : EdgeSink {
        override fun resolve(pos: BlockPos): Int = graph.idAt(pos)

        override fun edge(source: Int, target: Int, weight: Int, side: Boolean) =
            graph.link(source, target, weight, side)

        override fun unmapped(pos: BlockPos) =
            throw OptraIxCompileException("unmapped power source at $pos")
    }

    private fun typeOf(state: Int): Int {
        if (BlockStates.pressurePlatePowered(state) != null) return NodeType.PressurePlate
        if (BlockStates.isButton(state)) return NodeType.Button
        return when (BlockStates.typeOf(state)) {
            Blocks.RedstoneWire -> NodeType.Wire
            Blocks.Repeater -> NodeType.Repeater
            Blocks.Comparator -> NodeType.Comparator
            Blocks.RedstoneTorch -> NodeType.Torch
            Blocks.RedstoneWallTorch -> NodeType.WallTorch
            Blocks.RedstoneLamp -> NodeType.Lamp
            Blocks.Lever -> NodeType.Lever
            Blocks.RedstoneBlock -> NodeType.Constant
            Blocks.IronTrapdoor -> NodeType.Trapdoor
            Blocks.NoteBlock -> NodeType.NoteBlock
            Blocks.Observer, Blocks.TripwireHook ->
                throw OptraIxCompileException("${mcData.requireBlockByStateId(state).name} is not supported by optraix")
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
        if (BlockStates.pressurePlatePowered(state) != null || BlockStates.isButton(state)) return true
        return when (BlockStates.typeOf(state)) {
            Blocks.RedstoneTorch -> side != BlockFace.Top
            Blocks.RedstoneWallTorch -> {
                val facing = BlockStates.directionOf(state)
                facing != null && facing.blockFace() != side
            }
            Blocks.RedstoneBlock, Blocks.Lever -> true
            Blocks.Repeater, Blocks.Comparator ->
                BlockStates.directionOf(state)?.blockFace() == side
            Blocks.RedstoneWire -> when {
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
        if (BlockStates.isButton(state)) {
            val face = BlockStates.leverFaceOf(state)
            val facing = BlockStates.directionOf(state)
            return when (side) {
                BlockFace.Top -> face == LeverFace.Floor
                BlockFace.Bottom -> face == LeverFace.Ceiling
                else -> face == LeverFace.Wall && facing == side.unwrapDirection()
            }
        }
        return when (BlockStates.typeOf(state)) {
            Blocks.RedstoneTorch, Blocks.RedstoneWallTorch -> side == BlockFace.Bottom
            Blocks.Lever -> {
                val face = BlockStates.leverFaceOf(state)
                val facing = BlockStates.directionOf(state)
                when (side) {
                    BlockFace.Top -> face == LeverFace.Floor
                    BlockFace.Bottom -> face == LeverFace.Ceiling
                    else -> face == LeverFace.Wall && facing == side.unwrapDirection()
                }
            }
            Blocks.RedstoneWire, Blocks.Repeater, Blocks.Comparator ->
                emitsWeak(world, state, pos, side, dustPower)
            else -> false
        }
    }

    private fun addPowerSources(
        world: World,
        sink: EdgeSink,
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
                val source = sink.resolve(offset)
                if (source < 0) {
                    sink.unmapped(offset)
                    continue
                }
                sink.edge(source, target, weight, side)
            }
        } else if (emitsWeak(world, state, pos, face, dustPower)) {
            val source = sink.resolve(pos)
            if (source < 0) {
                sink.unmapped(pos)
                return
            }
            sink.edge(source, target, weight, side)
        }
    }

    private fun buildEdges(world: World, sink: EdgeSink, node: GraphNode, target: Int) {
        when (node.type) {
            NodeType.Wire -> buildWireEdges(world, sink, node.pos, target)
            NodeType.Repeater -> buildRepeaterEdges(world, sink, node, target)
            NodeType.Comparator -> buildComparatorEdges(world, sink, node, target)
            NodeType.Torch -> addPowerSources(
                world, sink, target, node.pos.offset(BlockFace.Bottom),
                BlockFace.Top, dustPower = true, side = false, weight = 0,
            )
            NodeType.WallTorch -> {
                val facing = BlockDirection.entries[node.facing]
                val wall = facing.opposite().blockFace()
                addPowerSources(
                    world, sink, target, node.pos.offset(wall),
                    wall, dustPower = true, side = false, weight = 0,
                )
            }
            NodeType.Lamp, NodeType.Trapdoor, NodeType.NoteBlock -> {
                for (face in BlockFace.All) {
                    addPowerSources(
                        world, sink, target, node.pos.offset(face),
                        face, dustPower = true, side = false, weight = 0,
                    )
                }
            }
        }
    }

    private fun buildWireEdges(world: World, sink: EdgeSink, pos: BlockPos, target: Int) {
        val above = world.getBlock(pos.offset(BlockFace.Top))
        for (face in BlockFace.All) {
            val neighborPos = pos.offset(face)
            val neighbor = world.getBlock(neighborPos)
            if (BlockStates.isType(neighbor, Blocks.RedstoneWire)) {
                sink.edge(sink.resolve(neighborPos), target, 1, false)
            }
            addPowerSources(
                world, sink, target, neighborPos, face,
                dustPower = false, side = false, weight = 0,
            )
            if (!face.isHorizontal) continue
            if (!BlockStates.isSolid(above) && !BlockStates.isTransparent(neighbor)) {
                val upPos = neighborPos.offset(BlockFace.Top)
                if (BlockStates.isType(world.getBlock(upPos), Blocks.RedstoneWire)) {
                    sink.edge(sink.resolve(upPos), target, 1, false)
                }
            }
            if (!BlockStates.isSolid(neighbor)) {
                val downPos = neighborPos.offset(BlockFace.Bottom)
                if (BlockStates.isType(world.getBlock(downPos), Blocks.RedstoneWire)) {
                    sink.edge(sink.resolve(downPos), target, 1, false)
                }
            }
        }
    }

    private fun buildRepeaterEdges(world: World, sink: EdgeSink, node: GraphNode, target: Int) {
        val facing = BlockDirection.entries[node.facing]
        val inputPos = node.pos.offset(facing.blockFace())
        addPowerSources(
            world, sink, target, inputPos, facing.blockFace(),
            dustPower = true, side = false, weight = 0,
        )
        if (BlockStates.isType(world.getBlock(inputPos), Blocks.RedstoneWire)) {
            sink.edge(sink.resolve(inputPos), target, 0, false)
        }
        for (side in arrayOf(facing.rotate(), facing.rotateCcw())) {
            val sidePos = node.pos.offset(side.blockFace())
            val sideState = world.getBlock(sidePos)
            if (!MchprsRedstone.isDiode(sideState)) continue
            if (!emitsWeak(world, sideState, sidePos, side.blockFace(), false)) continue
            sink.edge(sink.resolve(sidePos), target, 0, true)
        }
    }

    private fun buildComparatorEdges(world: World, sink: EdgeSink, node: GraphNode, target: Int) {
        val facing = BlockDirection.entries[node.facing]
        val face = facing.blockFace()
        val inputPos = node.pos.offset(face)
        val inputState = world.getBlock(inputPos)

        if (Comparator.hasOverride(inputState)) {
            node.adjacentOverride = Comparator.getOverride(inputState, world, inputPos)
        } else {
            addPowerSources(
                world, sink, target, inputPos, face,
                dustPower = true, side = false, weight = 0,
            )
            if (BlockStates.isType(inputState, Blocks.RedstoneWire)) {
                sink.edge(sink.resolve(inputPos), target, 0, false)
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
                        sink.edge(sink.resolve(sidePos), target, 0, true)
                    }
                }
                BlockStates.isType(sideState, Blocks.RedstoneWire) ->
                    sink.edge(sink.resolve(sidePos), target, 0, true)
                BlockStates.isType(sideState, Blocks.RedstoneBlock) ->
                    sink.edge(sink.resolve(sidePos), target, 0, true)
            }
        }
    }

    private class RegionSink(
        private val world: World,
        private val graph: OptraIxGraph,
        private val wireOf: LongIntMap,
        private val localOf: IntArray,
        private val wireCount: Int,
        private val core: BooleanArray,
        private val componentGlobal: IntBuffer,
        private val pairSource: IntBuffer,
        private val pairTarget: IntBuffer,
    ) : EdgeSink {

        override fun resolve(pos: BlockPos): Int {
            val wire = wireOf[pos.asLong()]
            if (wire >= 0) return wire
            val global = graph.idAt(pos)
            if (global < 0) return -1
            val local = localOf[global]
            return if (local < 0) -1 else wireCount + local
        }

        override fun edge(source: Int, target: Int, weight: Int, side: Boolean) {
            if (source < 0 || (source == target && source < wireCount)) return
            if (source < wireCount) {
                record(source, target, weight, side)
                return
            }
            val component = source - wireCount
            if (target >= wireCount) {
                if (!core[target - wireCount]) return
                graph.link(componentGlobal[component], componentGlobal[target - wireCount], weight, side)
                return
            }
            if (!core[component]) return
            record(source, target, weight, side)
        }

        override fun unmapped(pos: BlockPos) {
            if (graph.idAt(pos) >= 0) return
            if (BlockStates.isType(world.getBlock(pos), Blocks.RedstoneWire)) return
            throw OptraIxCompileException("unmapped power source at $pos")
        }

        private fun record(source: Int, target: Int, weight: Int, side: Boolean) {
            pairSource.add(source)
            pairTarget.add((target shl 5) or (weight shl 1) or (if (side) 1 else 0))
        }
    }

    private fun resolveThroughWires(
        world: World,
        graph: OptraIxGraph,
        wires: WireIndex,
        regionChunks: Int,
        cancelled: () -> Boolean,
    ) {
        if (regionChunks < 1 || Integer.bitCount(regionChunks) != 1) {
            throw OptraIxCompileException("region size must be a power of two, got $regionChunks")
        }
        val shift = 4 + Integer.numberOfTrailingZeros(regionChunks)
        val span = 1 shl shift
        val reach = (Halo + span - 1) / span

        val buckets = HashMap<Long, IntBuffer>()
        for (node in graph.nodes) {
            buckets.getOrPut(ChunkPos.key(node.pos.x shr shift, node.pos.z shr shift)) { IntBuffer() }
                .add(node.id)
        }

        val localOf = IntArray(graph.nodes.size) { -1 }
        val wirePos = LongBuffer()
        val componentGlobal = IntBuffer()
        val pairSource = IntBuffer()
        val pairTarget = IntBuffer()
        val deque = IntDeque()

        try {
            for ((key, core) in buckets) {
            checkCancelled(cancelled)
            val regionX = (key shr 32).toInt()
            val regionZ = key.toInt()
            val minX = (regionX shl shift) - Halo
            val maxX = (regionX shl shift) + span - 1 + Halo
            val minZ = (regionZ shl shift) - Halo
            val maxZ = (regionZ shl shift) + span - 1 + Halo

            wirePos.clear()
            wires.collect(minX, maxX, minZ, maxZ, wirePos)
            val wireCount = wirePos.size
            val wireOf = LongIntMap(maxOf(wireCount, 1))
            for (index in 0 until wireCount) wireOf.put(wirePos[index], index)

            componentGlobal.clear()
            for (offsetX in -reach..reach) {
                for (offsetZ in -reach..reach) {
                    val neighbours = buckets[ChunkPos.key(regionX + offsetX, regionZ + offsetZ)] ?: continue
                    for (index in 0 until neighbours.size) {
                        val id = neighbours[index]
                        val pos = graph.nodes[id].pos
                        if (pos.x < minX || pos.x > maxX || pos.z < minZ || pos.z > maxZ) continue
                        localOf[id] = componentGlobal.size
                        componentGlobal.add(id)
                    }
                }
            }
            val componentCount = componentGlobal.size
            val coreFlags = BooleanArray(componentCount)
            for (index in 0 until core.size) coreFlags[localOf[core[index]]] = true

            pairSource.clear()
            pairTarget.clear()
            val sink = RegionSink(
                world, graph, wireOf, localOf, wireCount, coreFlags,
                componentGlobal, pairSource, pairTarget,
            )
            for (index in 0 until wireCount) {
                buildWireEdges(world, sink, BlockPos.unpack(wirePos[index]), index)
            }
            for (index in 0 until componentCount) {
                buildEdges(world, sink, graph.nodes[componentGlobal[index]], wireCount + index)
            }

            traceRegion(
                graph, deque, wireCount, componentCount, coreFlags,
                componentGlobal, pairSource, pairTarget,
            )

                for (index in 0 until componentCount) localOf[componentGlobal[index]] = -1
            }
        } finally {
            for (bucket in buckets.values) bucket.close()
            wirePos.close()
            componentGlobal.close()
            pairSource.close()
            pairTarget.close()
        }
    }

    private fun traceRegion(
        graph: OptraIxGraph,
        deque: IntDeque,
        wireCount: Int,
        componentCount: Int,
        core: BooleanArray,
        componentGlobal: IntBuffer,
        pairSource: IntBuffer,
        pairTarget: IntBuffer,
    ) {
        val pairs = pairSource.size
        val outStart = IntArray(wireCount + 1)
        val seedStart = IntArray(componentCount + 1)
        for (index in 0 until pairs) {
            val source = pairSource[index]
            if (source < wireCount) outStart[source]++ else seedStart[source - wireCount]++
        }
        var running = 0
        for (slot in 0 until wireCount) {
            val size = outStart[slot]
            outStart[slot] = running
            running += size
        }
        outStart[wireCount] = running
        val outTarget = IntArray(running)
        running = 0
        for (slot in 0 until componentCount) {
            val size = seedStart[slot]
            seedStart[slot] = running
            running += size
        }
        seedStart[componentCount] = running
        val seedTarget = IntArray(running)

        val outCursor = outStart.copyOf(wireCount)
        val seedCursor = seedStart.copyOf(componentCount)
        for (index in 0 until pairs) {
            val source = pairSource[index]
            if (source < wireCount) {
                outTarget[outCursor[source]++] = pairTarget[index]
            } else {
                seedTarget[seedCursor[source - wireCount]++] = pairTarget[index]
            }
        }

        val dist = IntArray(wireCount)
        val distStamp = IntArray(wireCount) { -1 }
        val bestDefault = IntArray(componentCount)
        val bestDefaultStamp = IntArray(componentCount) { -1 }
        val bestSide = IntArray(componentCount)
        val bestSideStamp = IntArray(componentCount) { -1 }

        for (origin in 0 until componentCount) {
            if (!core[origin]) continue
            val sourceGlobal = componentGlobal[origin]
            deque.clear()

            for (slot in seedStart[origin] until seedStart[origin + 1]) {
                val packed = seedTarget[slot]
                val wire = packed ushr 5
                val weight = (packed ushr 1) and 15
                if (weight >= MaxSignal) continue
                if (distStamp[wire] == origin && dist[wire] <= weight) continue
                distStamp[wire] = origin
                dist[wire] = weight
                if (weight == 0) deque.addFirst(wire) else deque.addLast(wire)
            }

            while (!deque.isEmpty) {
                val wire = deque.pollFirst()
                val current = dist[wire]
                for (slot in outStart[wire] until outStart[wire + 1]) {
                    val packed = outTarget[slot]
                    val target = packed ushr 5
                    val next = current + ((packed ushr 1) and 15)
                    if (next >= MaxSignal) continue
                    if (target >= wireCount) {
                        val component = target - wireCount
                        if ((packed and 1) != 0) {
                            if (bestSideStamp[component] == origin && bestSide[component] <= next) continue
                            bestSideStamp[component] = origin
                            bestSide[component] = next
                            graph.link(sourceGlobal, componentGlobal[component], next, true)
                        } else {
                            if (bestDefaultStamp[component] == origin && bestDefault[component] <= next) continue
                            bestDefaultStamp[component] = origin
                            bestDefault[component] = next
                            graph.link(sourceGlobal, componentGlobal[component], next, false)
                        }
                        continue
                    }
                    if (distStamp[target] == origin && dist[target] <= next) continue
                    distStamp[target] = origin
                    dist[target] = next
                    if (((packed ushr 1) and 15) == 0) deque.addFirst(target) else deque.addLast(target)
                }
            }
        }
    }

    private const val MaxSignal = 15

    private const val Halo = 20

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
