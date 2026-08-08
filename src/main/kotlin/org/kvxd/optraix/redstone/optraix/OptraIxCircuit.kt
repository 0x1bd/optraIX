package org.kvxd.optraix.redstone.optraix

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.ComparatorMode
import org.kvxd.optraix.block.property.Instrument
import org.kvxd.optraix.redstone.mchprs.NoteBlock
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.TickPriority
import org.kvxd.optraix.world.World

class OptraIxCircuit internal constructor(
    val count: Int,
    internal val posKey: LongArray,
    internal val baseState: IntArray,
    internal val delayData: ByteArray,
    internal val modeData: ByteArray,
    internal val facingData: ByteArray,
    internal val adjacentOverride: ByteArray,
    internal val farOverride: ByteArray,
    internal val edgeStart: IntArray,
    internal val edges: IntArray,
    internal val index: HashMap<Long, Int>,
    private val state: LongArray,
    private val histBase: IntArray,
    private val counts: ByteArray,
    private val chainIndexOf: IntArray,
    private val chainNodeOf: IntArray,
    private val chainOffset: IntArray,
    private val chainLength: IntArray,
    private val chainPowered: LongArray,
    private val linkChainOf: IntArray,
    private val linkKind: ByteArray,
    private val linkPos: LongArray,
    private val linkFacing: ByteArray,
    private val linkOn: ByteArray,
    internal val linkIndex: HashMap<Long, Int>,
) {

    private val scheduler = TickScheduler(count)
    private val consumerQueue = IntQueue(1024)
    private val queuedFlag = BooleanArray(count)
    private val chainPendingMask = LongArray(chainOffset.size)
    private val chainShadow = LongArray(chainOffset.size)
    private val dirty = BooleanArray(count)
    private val dirtyList = IntStack(1024)
    private val soundEvents = IntStack(16)

    var nodeUpdates: Long = 0
        private set

    var nodeTicks: Long = 0
        private set

    var linkTicks: Long = 0
        private set

    val edgeCount: Int get() = edges.size

    val histogramBytes: Int get() = counts.size

    val chainCount: Int get() = chainOffset.size

    val fusedLinks: Int get() = linkKind.size

    val pendingTicks: Int get() = scheduler.queued

    init {
        for (chain in chainShadow.indices) chainShadow[chain] = chainPowered[chain]
        for (node in 0 until count) {
            val value = (state[node] ushr OutputShift).toInt() and 0xF
            var edge = edgeStart[node]
            val end = edgeStart[node + 1]
            while (edge < end) {
                val packed = edges[edge]
                val weight = (packed ushr WeightShift) and 0xF
                val contribution = if (value > weight) value - weight else 0
                val target = packed and TargetMask
                if (packed and SoloBit != 0) {
                    state[target] = state[target] or
                        (1L shl ((if (packed and SideBit != 0) SideMaskShift else DefMaskShift) + contribution))
                } else if (packed and SideBit != 0) {
                    addSide(target, contribution)
                } else {
                    addDefault(target, contribution)
                }
                edge++
            }
        }
    }

    private fun addDefault(node: Int, value: Int) {
        val slot = histBase[node] + value
        if (counts[slot].toInt() == 0) state[node] = state[node] or (1L shl (DefMaskShift + value))
        counts[slot]++
    }

    private fun addSide(node: Int, value: Int) {
        val slot = histBase[node] + SideHistogram + value
        if (counts[slot].toInt() == 0) state[node] = state[node] or (1L shl (SideMaskShift + value))
        counts[slot]++
    }

    private fun moveDefault(node: Int, from: Int, to: Int) {
        val base = histBase[node]
        var word = state[node]
        if ((--counts[base + from]).toInt() == 0) word = word and (1L shl (DefMaskShift + from)).inv()
        if (counts[base + to].toInt() == 0) word = word or (1L shl (DefMaskShift + to))
        counts[base + to]++
        state[node] = word
    }

    private fun moveSide(node: Int, from: Int, to: Int) {
        val base = histBase[node] + SideHistogram
        var word = state[node]
        if ((--counts[base + from]).toInt() == 0) word = word and (1L shl (SideMaskShift + from)).inv()
        if (counts[base + to].toInt() == 0) word = word or (1L shl (SideMaskShift + to))
        counts[base + to]++
        state[node] = word
    }

    private fun defPowered(word: Long): Boolean = ((word ushr (DefMaskShift + 1)) and 0x7FFFL) != 0L

    private fun sidePowered(word: Long): Boolean = ((word ushr (SideMaskShift + 1)) and 0x7FFFL) != 0L

    private fun maxOfMask(word: Long, shift: Int): Int {
        val mask = ((word ushr shift) and 0xFFFFL).toInt()
        return if (mask == 0) 0 else 31 - Integer.numberOfLeadingZeros(mask)
    }

    private fun markDirty(node: Int) {
        if (dirty[node]) return
        dirty[node] = true
        dirtyList.push(node)
    }

    private fun enqueue(node: Int) {
        if (queuedFlag[node]) return
        queuedFlag[node] = true
        consumerQueue.add(node)
    }

    private fun emit(node: Int, value: Int) {
        val word = state[node]
        val previous = (word ushr OutputShift).toInt() and 0xF
        if (previous == value) return
        state[node] = (word and OutputClear) or (value.toLong() shl OutputShift)
        markDirty(node)
        val start = edgeStart[node]
        val end = edgeStart[node + 1]
        val single = end - start == 1
        var edge = start
        while (edge < end) {
            val packed = edges[edge]
            val weight = (packed ushr WeightShift) and 0xF
            val before = if (previous > weight) previous - weight else 0
            val after = if (value > weight) value - weight else 0
            if (before != after) {
                val target = packed and TargetMask
                if (packed and SoloBit != 0) {
                    val shift = if (packed and SideBit != 0) SideMaskShift else DefMaskShift
                    state[target] = (state[target] and (1L shl (shift + before)).inv()) or (1L shl (shift + after))
                } else if (packed and SideBit != 0) {
                    moveSide(target, before, after)
                } else {
                    moveDefault(target, before, after)
                }
                if (single && !queuedFlag[target]) updateNode(target) else enqueue(target)
            }
            edge++
        }
    }

    private fun drainUpdates() {
        while (!consumerQueue.isEmpty) {
            val node = consumerQueue.poll()
            queuedFlag[node] = false
            updateNode(node)
        }
    }

    private fun updateNode(node: Int) {
        if (Stats) nodeUpdates++
        val word = state[node]
        when ((word and 0xFL).toInt()) {
            NodeType.Wire -> emit(node, maxOfMask(word, DefMaskShift))

            NodeType.Repeater -> {
                val shouldBeLocked = sidePowered(word)
                if (((word and LockedBit) != 0L) != shouldBeLocked) {
                    state[node] = word xor LockedBit
                    markDirty(node)
                }
                if (!shouldBeLocked && !scheduler.isPending(node)) {
                    val shouldBePowered = defPowered(word)
                    if (shouldBePowered != ((word and OnBit) != 0L)) {
                        val priority = when {
                            (word and FrontDiodeBit) != 0L -> 0
                            !shouldBePowered -> 1
                            else -> 2
                        }
                        scheduler.schedule(node, ((word ushr DelayShift) and 0xFL).toInt(), priority)
                    }
                }
            }

            NodeType.Comparator -> {
                if (scheduler.isPending(node)) return
                val input = comparatorInput(node, word)
                val sides = maxOfMask(word, SideMaskShift)
                val compare = (word and CompareBit) != 0L
                val strength = comparatorStrength(input, sides, compare)
                val current = (word ushr OutputShift).toInt() and 0xF
                val powered = comparatorPowered(input, sides, compare)
                if (strength != current || ((word and OnBit) != 0L) != powered) {
                    scheduler.schedule(node, 1, if ((word and FrontDiodeBit) != 0L) 2 else 3)
                }
            }

            NodeType.Torch, NodeType.WallTorch -> {
                val shouldBeOff = defPowered(word)
                if (((word and OnBit) != 0L) == shouldBeOff && !scheduler.isPending(node)) {
                    scheduler.schedule(node, 1, 3)
                }
            }

            NodeType.Lamp -> {
                val shouldBeLit = defPowered(word)
                if ((word and OnBit) != 0L && !shouldBeLit) {
                    scheduler.schedule(node, 2, 3)
                } else if ((word and OnBit) == 0L && shouldBeLit) {
                    state[node] = word or OnBit
                    markDirty(node)
                }
            }

            NodeType.Trapdoor -> {
                val shouldBePowered = defPowered(word)
                if (((word and OnBit) != 0L) != shouldBePowered) {
                    state[node] = word xor OnBit
                    markDirty(node)
                }
            }

            NodeType.NoteBlock -> {
                val shouldBePowered = defPowered(word)
                if (((word and OnBit) != 0L) != shouldBePowered) {
                    state[node] = word xor OnBit
                    markDirty(node)
                    if (shouldBePowered) soundEvents.push(node)
                }
            }

            NodeType.Chain -> updateLink(chainIndexOf[node], 0, if (defPowered(word)) 1L else 0L)
        }
    }

    private fun updateLink(chain: Int, link: Int, input: Long) {
        if (Stats) nodeUpdates++
        val bit = 1L shl link
        if ((chainPendingMask[chain] and bit) != 0L) return
        val p = (chainPowered[chain] ushr link) and 1L
        val slot = chainOffset[chain] + link
        val kind = linkKind[slot].toInt()
        when ((kind ushr LinkTypeShift) and 3) {
            LinkRepeater -> {
                if (p == input) return
                val priority = when {
                    (kind and FrontDiodeLink) != 0 -> 0
                    input == 0L -> 1
                    else -> 2
                }
                scheduleLink(chain, bit, slot, kind and 7, priority)
            }
            LinkComparator -> {
                if (p == input) return
                scheduleLink(chain, bit, slot, 1, if ((kind and FrontDiodeLink) != 0) 2 else 3)
            }
            else -> {
                if (p != input) return
                scheduleLink(chain, bit, slot, 1, 3)
            }
        }
    }

    private fun scheduleLink(chain: Int, bit: Long, slot: Int, delay: Int, priority: Int) {
        chainPendingMask[chain] = chainPendingMask[chain] or bit
        scheduler.scheduleEntry(slot or LinkTag, delay, priority)
    }

    private fun tickLink(slot: Int) {
        if (Stats) {
            nodeTicks++
            linkTicks++
        }
        val chain = linkChainOf[slot]
        val link = slot - chainOffset[chain]
        val bit = 1L shl link
        chainPendingMask[chain] = chainPendingMask[chain] and bit.inv()
        val powered = chainPowered[chain]
        val p = (powered ushr link) and 1L
        val input = if (link == 0) {
            if (defPowered(state[chainNodeOf[chain]])) 1L else 0L
        } else {
            (powered ushr (link - 1)) and 1L
        }
        val kind = linkKind[slot].toInt()
        when ((kind ushr LinkTypeShift) and 3) {
            LinkRepeater -> if (p == 1L) {
                if (input == 0L) toggleLink(chain, link, slot, 0L)
            } else {
                if (input == 0L) scheduleLink(chain, bit, slot, kind and 7, 1)
                toggleLink(chain, link, slot, 1L)
            }
            LinkComparator -> {
                if (p != input) toggleLink(chain, link, slot, input)
            }
            else -> if (p == 1L) {
                if (input == 1L) toggleLink(chain, link, slot, 0L)
            } else {
                if (input == 0L) toggleLink(chain, link, slot, 1L)
            }
        }
    }

    private fun toggleLink(chain: Int, link: Int, slot: Int, value: Long) {
        chainPowered[chain] = chainPowered[chain] xor (1L shl link)
        markDirty(chainNodeOf[chain])
        if (link == chainLength[chain] - 1) {
            emit(chainNodeOf[chain], if (value != 0L) linkOn[slot].toInt() else 0)
        } else {
            updateLink(chain, link + 1, value)
        }
    }

    private fun comparatorInput(node: Int, word: Long): Int {
        val adjacent = adjacentOverride[node].toInt()
        if (adjacent >= 0) return adjacent
        val base = maxOfMask(word, DefMaskShift)
        val far = farOverride[node].toInt()
        return if (base < 15 && far >= 0) far else base
    }

    private fun comparatorStrength(input: Int, sides: Int, compare: Boolean): Int =
        if (!compare) {
            if (input > sides) input - sides else 0
        } else if (input >= sides) {
            input
        } else {
            0
        }

    private fun comparatorPowered(input: Int, sides: Int, compare: Boolean): Boolean {
        if (input == 0) return false
        if (input > sides) return true
        return sides == input && compare
    }

    private fun comparatorOutput(node: Int, word: Long): Int {
        val input = comparatorInput(node, word)
        val sides = maxOfMask(word, SideMaskShift)
        return if ((word and CompareBit) == 0L) {
            if (input > sides) input - sides else 0
        } else if (input >= sides) {
            input
        } else {
            0
        }
    }

    private fun comparatorShouldBePowered(node: Int, word: Long): Boolean {
        val input = comparatorInput(node, word)
        if (input == 0) return false
        val sides = maxOfMask(word, SideMaskShift)
        if (input > sides) return true
        return sides == input && (word and CompareBit) != 0L
    }

    private fun tickNode(node: Int) {
        if (Stats) nodeTicks++
        val word = state[node]
        when ((word and 0xFL).toInt()) {
            NodeType.Repeater -> {
                if ((word and LockedBit) != 0L) return
                val shouldBePowered = defPowered(word)
                val on = (word and OnBit) != 0L
                if (on && !shouldBePowered) {
                    state[node] = word and OnBit.inv()
                    markDirty(node)
                    emit(node, 0)
                } else if (!on) {
                    if (!shouldBePowered) {
                        scheduler.schedule(node, ((word ushr DelayShift) and 0xFL).toInt(), 1)
                    }
                    state[node] = word or OnBit
                    markDirty(node)
                    emit(node, 15)
                }
            }

            NodeType.Comparator -> {
                val input = comparatorInput(node, word)
                val sides = maxOfMask(word, SideMaskShift)
                val compare = (word and CompareBit) != 0L
                val strength = comparatorStrength(input, sides, compare)
                val previous = (word ushr OutputShift).toInt() and 0xF
                if (strength != previous || compare) {
                    val shouldBePowered = comparatorPowered(input, sides, compare)
                    if (((word and OnBit) != 0L) != shouldBePowered) {
                        state[node] = state[node] xor OnBit
                        markDirty(node)
                    }
                    if (strength != previous) {
                        markDirty(node)
                        emit(node, strength)
                    }
                }
            }

            NodeType.Torch, NodeType.WallTorch -> {
                val shouldBeOff = defPowered(word)
                val on = (word and OnBit) != 0L
                if (on && shouldBeOff) {
                    state[node] = word and OnBit.inv()
                    markDirty(node)
                    emit(node, 0)
                } else if (!on && !shouldBeOff) {
                    state[node] = word or OnBit
                    markDirty(node)
                    emit(node, 15)
                }
            }

            NodeType.Lamp -> {
                if ((word and OnBit) != 0L && !defPowered(word)) {
                    state[node] = word and OnBit.inv()
                    markDirty(node)
                }
            }

            NodeType.Button -> {
                if ((word and OnBit) != 0L) {
                    state[node] = word and OnBit.inv()
                    markDirty(node)
                    emit(node, 0)
                }
            }
        }
    }

    fun tick() {
        val bucket = scheduler.nextBucket()
        if (scheduler.queued == 0) return
        for (priority in 0 until TickScheduler.Priorities) {
            val items = scheduler.itemsAt(bucket, priority)
            val size = scheduler.sizeAt(bucket, priority)
            var cursor = 0
            while (cursor < size) {
                val entry = items[cursor++]
                if (entry and LinkTag != 0) {
                    scheduler.releaseEntry()
                    tickLink(entry and LinkSlotMask)
                } else {
                    scheduler.release(entry)
                    tickNode(entry)
                }
                drainUpdates()
            }
            scheduler.clearAt(bucket, priority)
        }
        drainUpdates()
    }

    fun settle() {
        for (node in 0 until count) enqueue(node)
        drainUpdates()
        for (chain in chainOffset.indices) {
            val powered = chainPowered[chain]
            for (link in 1 until chainLength[chain]) {
                updateLink(chain, link, (powered ushr (link - 1)) and 1L)
            }
        }
    }

    fun exportPendingTicks(world: GameWorld) {
        scheduler.forEachPending { entry, delay, priority ->
            val pos = if (entry and LinkTag != 0) {
                linkPos[entry and LinkSlotMask]
            } else {
                posKey[entry]
            }
            world.scheduleTick(BlockPos.unpack(pos), delay, TickPriority.entries[priority])
        }
    }

    internal fun importPendingTick(node: Int, delay: Int, priority: Int) {
        scheduler.schedule(node, delay.coerceIn(1, TickScheduler.WheelSize - 1), priority)
    }

    internal fun importPendingLinkTick(slot: Int, delay: Int, priority: Int) {
        val chain = linkChainOf[slot]
        val bit = 1L shl (slot - chainOffset[chain])
        if ((chainPendingMask[chain] and bit) != 0L) return
        chainPendingMask[chain] = chainPendingMask[chain] or bit
        scheduler.scheduleEntry(slot or LinkTag, delay.coerceIn(1, TickScheduler.WheelSize - 1), priority)
    }

    fun setSource(node: Int, powered: Boolean) {
        val word = state[node]
        if (((word and OnBit) != 0L) == powered) return
        state[node] = word xor OnBit
        markDirty(node)
        emit(node, if (powered) 15 else 0)
        drainUpdates()
    }

    fun pressButton(node: Int) {
        if ((state[node] and OnBit) != 0L) return
        setSource(node, true)
        scheduler.schedule(node, delayData[node].toInt(), 3)
    }

    fun nodeAt(pos: BlockPos): Int = index[pos.asLong()] ?: -1

    fun typeOf(node: Int): Int = (state[node] and 0xFL).toInt()

    fun isOn(node: Int): Boolean = (state[node] and OnBit) != 0L

    fun outputOf(node: Int): Int = ((state[node] ushr OutputShift).toInt() and 0xF)

    fun stateOf(node: Int): Int {
        val base = baseState[node]
        val word = state[node]
        val on = (word and OnBit) != 0L
        return when ((word and 0xFL).toInt()) {
            NodeType.Wire -> BlockStates.wireWithPower(base, outputOf(node))
            NodeType.Repeater -> BlockStates.repeaterState(
                delayData[node].toInt(),
                BlockDirection.Values[facingData[node].toInt()],
                (word and LockedBit) != 0L,
                on,
            )
            NodeType.Comparator -> BlockStates.comparatorState(
                BlockDirection.Values[facingData[node].toInt()],
                ComparatorMode.entries[modeData[node].toInt()],
                on,
            )
            NodeType.Torch -> BlockStates.torchState(on)
            NodeType.WallTorch -> BlockStates.wallTorchState(on, BlockDirection.Values[facingData[node].toInt()])
            NodeType.Lamp -> BlockStates.lampState(on)
            NodeType.Lever -> BlockStates.leverState(
                BlockStates.leverFaceOf(base),
                BlockDirection.Values[facingData[node].toInt()],
                on,
            )
            NodeType.Button -> BlockStates.withPowered(base, on)
            NodeType.PressurePlate -> BlockStates.withPowered(base, on)
            NodeType.NoteBlock -> BlockStates.noteBlockState(
                Instrument.Values[modeData[node].toInt()],
                delayData[node].toInt(),
                on,
            )
            NodeType.Trapdoor -> trapdoorState(base, on)
            else -> base
        }
    }

    private fun trapdoorState(base: Int, powered: Boolean): Int {
        val blockType = Blocks.typeOf(base)
        val value = if (powered) "true" else "false"
        var result = blockType.withValue(base, blockType.requireProperty("powered"), value)
        result = blockType.withValue(result, blockType.requireProperty("open"), value)
        return result
    }

    private fun writeChain(world: World, node: Int) {
        val chain = chainIndexOf[node]
        val offset = chainOffset[chain]
        val powered = chainPowered[chain]

        var changed = powered xor chainShadow[chain]
        chainShadow[chain] = powered

        while (changed != 0L) {
            val link = java.lang.Long.numberOfTrailingZeros(changed)
            changed = changed and (changed - 1)

            writeLink(
                world,
                offset + link,
                ((powered ushr link) and 1L) != 0L,
            )
        }
    }

    private fun writeLink(world: World, slot: Int, on: Boolean) {
        val pos = BlockPos.unpack(linkPos[slot])
        val kind = linkKind[slot].toInt()

        when ((kind ushr LinkTypeShift) and 3) {
            LinkRepeater -> world.setBlock(
                pos,
                BlockStates.repeaterState(
                    kind and 7,
                    BlockDirection.Values[linkFacing[slot].toInt()],
                    false,
                    on,
                ),
            )

            LinkComparator -> {
                world.setBlock(
                    pos,
                    BlockStates.comparatorState(
                        BlockDirection.Values[linkFacing[slot].toInt()],
                        if ((kind and SubtractLink) != 0) {
                            ComparatorMode.Subtract
                        } else {
                            ComparatorMode.Compare
                        },
                        on,
                    ),
                )

                world.setBlockEntity(
                    pos,
                    BlockEntity.Comparator(
                        if (on) linkOn[slot].toInt() else 0
                    ),
                )
            }

            LinkTorch ->
                world.setBlock(pos, BlockStates.torchState(on))

            else ->
                world.setBlock(
                    pos,
                    BlockStates.wallTorchState(
                        on,
                        BlockDirection.Values[linkFacing[slot].toInt()],
                    ),
                )
        }
    }

    private fun writeLinkSnapshot(
        world: GameWorld,
        slot: Int,
        on: Boolean,
    ) {
        val pos = BlockPos.unpack(linkPos[slot])
        val kind = linkKind[slot].toInt()

        when ((kind ushr LinkTypeShift) and 3) {
            LinkRepeater -> world.setBlockSilent(
                pos,
                BlockStates.repeaterState(
                    kind and 7,
                    BlockDirection.Values[linkFacing[slot].toInt()],
                    false,
                    on,
                ),
            )

            LinkComparator -> {
                world.setBlockSilent(
                    pos,
                    BlockStates.comparatorState(
                        BlockDirection.Values[linkFacing[slot].toInt()],
                        if ((kind and SubtractLink) != 0) {
                            ComparatorMode.Subtract
                        } else {
                            ComparatorMode.Compare
                        },
                        on,
                    ),
                )

                world.setBlockEntitySilent(
                    pos,
                    BlockEntity.Comparator(
                        if (on) linkOn[slot].toInt() else 0
                    ),
                )
            }

            LinkTorch ->
                world.setBlockSilent(pos, BlockStates.torchState(on))

            else ->
                world.setBlockSilent(
                    pos,
                    BlockStates.wallTorchState(
                        on,
                        BlockDirection.Values[linkFacing[slot].toInt()],
                    ),
                )
        }
    }

    fun writeSnapshot(world: GameWorld) {
        for (node in 0 until count) {
            if (typeOf(node) == NodeType.Chain) {
                val chain = chainIndexOf[node]
                val offset = chainOffset[chain]
                val powered = chainPowered[chain]

                for (link in 0 until chainLength[chain]) {
                    writeLinkSnapshot(
                        world,
                        offset + link,
                        ((powered ushr link) and 1L) != 0L,
                    )
                }

                continue
            }

            val pos = BlockPos.unpack(posKey[node])

            world.setBlockSilent(pos, stateOf(node))

            if (typeOf(node) == NodeType.Comparator) {
                world.setBlockEntitySilent(
                    pos,
                    BlockEntity.Comparator(outputOf(node)),
                )
            }
        }
    }

    fun flush(world: World, ioOnly: Boolean = false) {
        while (!dirtyList.isEmpty) {
            val node = dirtyList.pop()
            dirty[node] = false
            val type = typeOf(node)
            if (ioOnly && !NodeType.isIo(type)) continue
            if (type == NodeType.Chain) {
                writeChain(world, node)
                continue
            }
            val pos = BlockPos.unpack(posKey[node])
            world.setBlock(pos, stateOf(node))
            if (type == NodeType.Comparator) {
                world.setBlockEntity(pos, BlockEntity.Comparator(outputOf(node)))
            }
        }
        while (!soundEvents.isEmpty) {
            val node = soundEvents.pop()
            val pos = BlockPos.unpack(posKey[node])
            if (NoteBlock.isUnblocked(world, pos)) {
                NoteBlock.playNote(
                    world,
                    pos,
                    Instrument.Values[modeData[node].toInt()],
                    delayData[node].toInt(),
                )
            }
        }
    }

    fun writeAll(world: World) {
        for (chain in chainShadow.indices) {
            chainShadow[chain] = chainPowered[chain] xor ((1L shl chainLength[chain]) - 1L)
        }
        for (node in 0 until count) {
            if (typeOf(node) == NodeType.Chain) {
                writeChain(world, node)
                continue
            }
            val pos = BlockPos.unpack(posKey[node])
            world.setBlock(pos, stateOf(node))
            if (typeOf(node) == NodeType.Comparator) {
                world.setBlockEntity(pos, BlockEntity.Comparator(outputOf(node)))
            }
        }
        dirtyList.clear()
        dirty.fill(false)
        soundEvents.clear()
    }

    fun resetStats() {
        nodeUpdates = 0
        nodeTicks = 0
        linkTicks = 0
    }

    internal fun peekNext(priority: Int): IntArray = scheduler.peekNext(priority)

    internal fun defaultInputOf(node: Int): Int = maxOfMask(state[node], DefMaskShift)

    internal fun sideInputOf(node: Int): Int = maxOfMask(state[node], SideMaskShift)

    internal fun comparatorInputOf(node: Int): Int = comparatorInput(node, state[node])

    companion object {
        const val Stats = true

        const val HistogramStride = 32
        const val SideHistogram = 16
        const val NoHistogram = -HistogramStride

        const val OutputShift = 4
        const val DelayShift = 11
        const val DefMaskShift = 16
        const val SideMaskShift = 32

        const val OnBit = 1L shl 8
        const val LockedBit = 1L shl 9
        const val FrontDiodeBit = 1L shl 10
        const val CompareBit = 1L shl 15

        const val OutputClear = (0xFL shl OutputShift).inv()

        const val TargetMask = 0x03FFFFFF
        const val WeightShift = 26
        const val SideBit = 1 shl 30
        const val SoloBit = 1 shl 31

        const val LinkTag = 1 shl 26
        const val LinkSlotMask = LinkTag - 1
        const val LinkTypeShift = 3
        const val LinkRepeater = 0
        const val LinkComparator = 1
        const val LinkTorch = 2
        const val LinkWallTorch = 3
        const val FrontDiodeLink = 0x20
        const val SubtractLink = 0x40

        fun pack(
            type: Int,
            output: Int,
            on: Boolean,
            locked: Boolean,
            frontDiode: Boolean,
            delay: Int,
            compare: Boolean,
        ): Long {
            var word = type.toLong() or (output.toLong() shl OutputShift)
            if (on) word = word or OnBit
            if (locked) word = word or LockedBit
            if (frontDiode) word = word or FrontDiodeBit
            word = word or ((delay.toLong() and 0xFL) shl DelayShift)
            if (compare) word = word or CompareBit
            return word
        }

        fun packEdge(target: Int, weight: Int, side: Boolean, solo: Boolean): Int =
            (target and TargetMask) or (weight shl WeightShift) or
                (if (side) SideBit else 0) or (if (solo) SoloBit else 0)
    }
}
