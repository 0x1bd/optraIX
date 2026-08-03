package org.kvxd.optraix.world

import org.kvxd.optraix.block.Blocks

class ChunkSection {

    var bitsPerEntry: Int = 0
        internal set

    var palette: IntArray = intArrayOf(Blocks.airState)
        internal set

    var paletteSize: Int = 1
        internal set

    var data: LongArray = LongArray(0)
        internal set

    var blockCount: Int = 0
        internal set

    internal var paletteIndex: HashMap<Int, Int>? = null

    val isDirect: Boolean
        get() = bitsPerEntry >= DirectBits

    fun get(index: Int): Int {
        if (bitsPerEntry == 0) return palette[0]
        val valuesPerLong = 64 / bitsPerEntry
        val cell = index / valuesPerLong
        val bit = (index - cell * valuesPerLong) * bitsPerEntry
        val mask = (1L shl bitsPerEntry) - 1L
        val raw = ((data[cell] ushr bit) and mask).toInt()
        return if (isDirect) raw else palette[raw]
    }

    fun set(index: Int, state: Int): Boolean {
        val previous = get(index)
        if (previous == state) return false
        if (previous == Blocks.airState) blockCount++
        if (state == Blocks.airState) blockCount--

        val raw = if (isDirect) state else indexFor(state)
        writeRaw(index, raw)
        return true
    }

    private fun indexFor(state: Int): Int {
        paletteIndex?.get(state)?.let { return it }
        for (index in 0 until paletteSize) {
            if (palette[index] == state) return index
        }
        if (bitsPerEntry == 0) {
            grow(4)
            return indexFor(state)
        }
        if (paletteSize >= (1 shl bitsPerEntry)) {
            if (bitsPerEntry >= 8) {
                grow(DirectBits)
                return state
            }
            grow(bitsPerEntry + 1)
            return indexFor(state)
        }
        if (paletteSize >= palette.size) palette = palette.copyOf(maxOf(4, palette.size * 2))
        palette[paletteSize] = state
        val index = paletteSize++
        if (paletteSize >= IndexedPaletteSize) {
            val lookup = paletteIndex ?: HashMap<Int, Int>(paletteSize * 2).also { paletteIndex = it }
            for (entry in 0 until paletteSize) lookup[palette[entry]] = entry
        }
        return index
    }

    private fun writeRaw(index: Int, raw: Int) {
        val valuesPerLong = 64 / bitsPerEntry
        val cell = index / valuesPerLong
        val bit = (index - cell * valuesPerLong) * bitsPerEntry
        val mask = (1L shl bitsPerEntry) - 1L
        data[cell] = (data[cell] and (mask shl bit).inv()) or ((raw.toLong() and mask) shl bit)
    }

    private fun grow(newBits: Int) {
        if (bitsPerEntry == 0) {
            bitsPerEntry = newBits
            data = LongArray(longArraySize(newBits))
            palette = palette.copyOf(maxOf(paletteSize, 1 shl newBits))
            return
        }
        val old = IntArray(4096) { get(it) }
        bitsPerEntry = newBits
        data = LongArray(longArraySize(newBits))
        if (newBits >= DirectBits) {
            palette = intArrayOf()
            paletteSize = 0
            paletteIndex = null
            for (i in 0 until 4096) writeRaw(i, old[i])
        } else {
            val previousPalette = palette
            val previousSize = paletteSize
            palette = previousPalette.copyOf(maxOf(previousSize, 1 shl newBits))
            paletteSize = previousSize
            for (i in 0 until 4096) writeRaw(i, indexFor(old[i]))
        }
    }

    fun fill(state: Int) {
        bitsPerEntry = 0
        palette = intArrayOf(state)
        paletteSize = 1
        paletteIndex = null
        data = LongArray(0)
        blockCount = if (state == Blocks.airState) 0 else 4096
    }

    companion object {
        const val DirectBits = 15
        private const val IndexedPaletteSize = 32

        fun restore(
            bitsPerEntry: Int,
            palette: IntArray,
            paletteSize: Int,
            data: LongArray,
            blockCount: Int,
        ): ChunkSection = ChunkSection().also { section ->
            section.bitsPerEntry = bitsPerEntry
            section.palette = if (palette.isEmpty()) IntArray(0) else palette
            section.paletteSize = paletteSize
            section.data = data
            section.blockCount = blockCount
            if (paletteSize >= IndexedPaletteSize) {
                val index = HashMap<Int, Int>(paletteSize * 2)
                for (entry in 0 until paletteSize) index[palette[entry]] = entry
                section.paletteIndex = index
            }
        }

        fun longArraySize(bits: Int): Int {
            if (bits == 0) return 0
            val valuesPerLong = 64 / bits
            return (4096 + valuesPerLong - 1) / valuesPerLong
        }
    }
}
