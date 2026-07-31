package org.kvxd.gogolmc.redstone.opt3x

import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.block.property.BlockDirection
import org.kvxd.gogolmc.block.property.ComparatorMode
import org.kvxd.gogolmc.block.property.Instrument
import org.kvxd.gogolmc.redstone.mchprs.NoteBlock
import org.kvxd.gogolmc.world.BlockEntity
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.GameWorld
import org.kvxd.gogolmc.world.TickPriority
import org.kvxd.gogolmc.world.World

class Opt3xCircuit internal constructor(
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
) {

    private val scheduler = TickScheduler(count)
    private val consumerQueue = IntQueue(1024)
    private val queuedFlag = BooleanArray(count)
    private val dirty = BooleanArray(count)
    private val dirtyList = IntStack(1024)
    private val soundEvents = IntStack(16)

    var nodeUpdates: Long = 0
        private set

    var nodeTicks: Long = 0
        private set

    val edgeCount: Int get() = edges.size

    val histogramBytes: Int get() = counts.size

    val pendingTicks: Int get() = scheduler.queued

    init {
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
                val node = items[cursor++]
                scheduler.release(node)
                tickNode(node)
                drainUpdates()
            }
            scheduler.clearAt(bucket, priority)
        }
        drainUpdates()
    }

    fun settle() {
        for (node in 0 until count) enqueue(node)
        drainUpdates()
    }

    fun exportPendingTicks(world: GameWorld) {
        scheduler.forEachPending { node, delay, priority ->
            world.scheduleTick(BlockPos.unpack(posKey[node]), delay, TickPriority.entries[priority])
        }
    }

    internal fun importPendingTick(node: Int, delay: Int, priority: Int) {
        scheduler.schedule(node, delay.coerceIn(1, TickScheduler.WheelSize - 1), priority)
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

    fun flush(world: World) {
        while (!dirtyList.isEmpty) {
            val node = dirtyList.pop()
            dirty[node] = false
            val pos = BlockPos.unpack(posKey[node])
            world.setBlock(pos, stateOf(node))
            if (typeOf(node) == NodeType.Comparator) {
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
        for (node in 0 until count) {
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
