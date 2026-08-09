package org.kvxd.optraix.world

import org.kvxd.optraix.mcdata.v1_20_4.Blocks


class ChunkSection {

    var bitsPerEntry: Int = 0
        internal set

    var palette: IntArray = intArrayOf(Blocks.Air.defaultState)
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

    inline fun forEachState(action: (slot: Int, state: Int) -> Unit) {
        if (bitsPerEntry == 0) {
            val state = palette[0]
            for (slot in 0 until 4096) action(slot, state)
            return
        }
        val bits = bitsPerEntry
        val valuesPerLong = 64 / bits
        val mask = (1L shl bits) - 1L
        val direct = isDirect
        val words = data
        val entries = palette
        var slot = 0
        var cell = 0
        while (slot < 4096) {
            var word = words[cell]
            var offset = 0
            while (offset < valuesPerLong && slot < 4096) {
                val raw = (word and mask).toInt()
                action(slot, if (direct) raw else entries[raw])
                word = word ushr bits
                offset++
                slot++
            }
            cell++
        }
    }

    fun set(index: Int, state: Int): Boolean {
        val previous = get(index)
        if (previous == state) return false
        if (previous == Blocks.Air.defaultState) blockCount++
        if (state == Blocks.Air.defaultState) blockCount--

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

    fun fillLayer(y: Int, state: Int) {
        if (state == Blocks.Air.defaultState) return
        if (bitsPerEntry != 0 || palette[0] != Blocks.Air.defaultState) {
            val base = y shl 8
            for (index in base until base + 256) set(index, state)
            return
        }
        bitsPerEntry = 4
        palette = IntArray(1 shl 4).also {
            it[0] = Blocks.Air.defaultState
            it[1] = state
        }
        paletteSize = 2
        paletteIndex = null
        data = LongArray(longArraySize(4))
        val base = y * 16
        for (cell in base until base + 16) data[cell] = LayerWord
        blockCount = 256
    }

    fun fill(state: Int) {
        bitsPerEntry = 0
        palette = intArrayOf(state)
        paletteSize = 1
        paletteIndex = null
        data = LongArray(0)
        blockCount = if (state == Blocks.Air.defaultState) 0 else 4096
    }

    companion object {
        const val DirectBits = 15
        private const val IndexedPaletteSize = 32

        private val LayerWord: Long = run {
            var word = 0L
            for (slot in 0 until 16) word = word or (1L shl (slot * 4))
            word
        }

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
