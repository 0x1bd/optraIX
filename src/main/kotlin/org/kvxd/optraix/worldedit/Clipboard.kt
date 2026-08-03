package org.kvxd.optraix.worldedit

import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.block.property.FlipDirection
import org.kvxd.optraix.block.property.RotateAmount
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos

class Clipboard private constructor(
    val sizeX: Int,
    val sizeY: Int,
    val sizeZ: Int,
    val offset: BlockPos,
    private val storage: ClipboardBlocks,
    val blockEntities: MutableMap<Int, BlockEntity> = HashMap(),
) {

    constructor(
        sizeX: Int,
        sizeY: Int,
        sizeZ: Int,
        offset: BlockPos,
        blocks: IntArray,
        blockEntities: MutableMap<Int, BlockEntity> = HashMap(),
    ) : this(sizeX, sizeY, sizeZ, offset, DenseClipboardBlocks(blocks), blockEntities)

    val volume: Int
        get() = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ)

    val blocks: IntArray
        get() = storage.dense ?: error("sparse clipboard has no dense block array")

    val isSparse: Boolean
        get() = storage.dense == null

    val storedBlockCount: Int
        get() = storage.storedBlockCount

    fun index(x: Int, y: Int, z: Int): Int = (y * sizeZ + z) * sizeX + x

    operator fun get(x: Int, y: Int, z: Int): Int = storage[index(x, y, z)]

    operator fun set(x: Int, y: Int, z: Int, state: Int) {
        storage[index(x, y, z)] = state
    }

    internal fun forEachNonAir(action: (Int, Int) -> Unit) {
        storage.forEachNonAir(action)
    }

    fun rotate(amount: RotateAmount): Clipboard {
        var current = this
        val turns = when (amount) {
            RotateAmount.Rotate90 -> 1
            RotateAmount.Rotate180 -> 2
            RotateAmount.Rotate270 -> 3
        }
        repeat(turns) { current = current.rotate90() }
        return current
    }

    private fun rotate90(): Clipboard {
        val result = transformed(
            newSizeX = sizeZ,
            newSizeZ = sizeX,
            newOffset = BlockPos(-offset.z - sizeZ + 1, offset.y, offset.x),
            position = { x, y, z -> Triple(sizeZ - 1 - z, y, x) },
            state = BlockTransform::rotate90,
        )
        for ((key, entity) in blockEntities) {
            val x = key % sizeX
            val z = (key / sizeX) % sizeZ
            val y = key / (sizeX * sizeZ)
            result.blockEntities[result.index(sizeZ - 1 - z, y, x)] = entity
        }
        return result
    }

    fun flip(direction: FlipDirection): Clipboard {
        val newOffset = when (direction) {
            FlipDirection.FlipX -> BlockPos(-offset.x - sizeX + 1, offset.y, offset.z)
            FlipDirection.FlipZ -> BlockPos(offset.x, offset.y, -offset.z - sizeZ + 1)
        }
        val result = transformed(
            newSizeX = sizeX,
            newSizeZ = sizeZ,
            newOffset = newOffset,
            position = { x, y, z ->
                Triple(
                    if (direction == FlipDirection.FlipX) sizeX - 1 - x else x,
                    y,
                    if (direction == FlipDirection.FlipZ) sizeZ - 1 - z else z,
                )
            },
            state = { BlockTransform.flip(it, direction) },
        )
        for ((key, entity) in blockEntities) {
            val x = key % sizeX
            val z = (key / sizeX) % sizeZ
            val y = key / (sizeX * sizeZ)
            val targetX = if (direction == FlipDirection.FlipX) sizeX - 1 - x else x
            val targetZ = if (direction == FlipDirection.FlipZ) sizeZ - 1 - z else z
            result.blockEntities[result.index(targetX, y, targetZ)] = entity
        }
        return result
    }

    private fun transformed(
        newSizeX: Int,
        newSizeZ: Int,
        newOffset: BlockPos,
        position: (Int, Int, Int) -> Triple<Int, Int, Int>,
        state: (Int) -> Int,
    ): Clipboard {
        val targetVolume = Math.multiplyExact(Math.multiplyExact(newSizeX, sizeY), newSizeZ)
        if (!isSparse) {
            val target = IntArray(targetVolume) { Blocks.airState }
            storage.forEachNonAir { sourceIndex, sourceState ->
                val x = sourceIndex % sizeX
                val z = (sourceIndex / sizeX) % sizeZ
                val y = sourceIndex / (sizeX * sizeZ)
                val (targetX, targetY, targetZ) = position(x, y, z)
                target[(targetY * newSizeZ + targetZ) * newSizeX + targetX] = state(sourceState)
            }
            return Clipboard(newSizeX, sizeY, newSizeZ, newOffset, target)
        }
        val target = SparseClipboardBuilder(storedBlockCount)
        storage.forEachNonAir { sourceIndex, sourceState ->
            val x = sourceIndex % sizeX
            val z = (sourceIndex / sizeX) % sizeZ
            val y = sourceIndex / (sizeX * sizeZ)
            val (targetX, targetY, targetZ) = position(x, y, z)
            target.add((targetY * newSizeZ + targetZ) * newSizeX + targetX, state(sourceState))
        }
        return sparse(newSizeX, sizeY, newSizeZ, newOffset, target.build(sorted = false))
    }

    companion object {
        internal fun sparse(
            sizeX: Int,
            sizeY: Int,
            sizeZ: Int,
            offset: BlockPos,
            blocks: SparseClipboardBlocks,
        ): Clipboard = Clipboard(sizeX, sizeY, sizeZ, offset, blocks)
    }
}

private interface ClipboardBlocks {
    val dense: IntArray?
    val storedBlockCount: Int
    operator fun get(index: Int): Int
    operator fun set(index: Int, state: Int)
    fun forEachNonAir(action: (Int, Int) -> Unit)
}

private class DenseClipboardBlocks(override val dense: IntArray) : ClipboardBlocks {
    override val storedBlockCount: Int
        get() = dense.count { it != Blocks.airState }

    override fun get(index: Int): Int = dense[index]

    override fun set(index: Int, state: Int) {
        dense[index] = state
    }

    override fun forEachNonAir(action: (Int, Int) -> Unit) {
        dense.forEachIndexed { index, state ->
            if (state != Blocks.airState) action(index, state)
        }
    }
}

internal class SparseClipboardBlocks(
    private val positions: IntArray,
    private val states: IntArray,
    override val storedBlockCount: Int,
    private val sorted: Boolean,
) : ClipboardBlocks {
    override val dense: IntArray? = null

    override fun get(index: Int): Int {
        if (sorted) {
            var low = 0
            var high = storedBlockCount - 1
            while (low <= high) {
                val middle = (low + high) ushr 1
                val position = positions[middle]
                when {
                    position < index -> low = middle + 1
                    position > index -> high = middle - 1
                    else -> return states[middle]
                }
            }
            return Blocks.airState
        }
        for (entry in 0 until storedBlockCount) {
            if (positions[entry] == index) return states[entry]
        }
        return Blocks.airState
    }

    override fun set(index: Int, state: Int) {
        error("sparse clipboard blocks are immutable")
    }

    override fun forEachNonAir(action: (Int, Int) -> Unit) {
        for (entry in 0 until storedBlockCount) action(positions[entry], states[entry])
    }
}

internal class SparseClipboardBuilder(initialCapacity: Int = 1024) {
    private var positions = IntArray(initialCapacity.coerceAtLeast(1))
    private var states = IntArray(initialCapacity.coerceAtLeast(1))
    private var size = 0

    fun add(position: Int, state: Int) {
        if (state == Blocks.airState) return
        if (size == positions.size) {
            val capacity = if (size < 1 shl 20) size * 2 else size + (size shr 1)
            positions = positions.copyOf(capacity)
            states = states.copyOf(capacity)
        }
        positions[size] = position
        states[size] = state
        size++
    }

    fun build(sorted: Boolean): SparseClipboardBlocks = SparseClipboardBlocks(positions, states, size, sorted)
}
