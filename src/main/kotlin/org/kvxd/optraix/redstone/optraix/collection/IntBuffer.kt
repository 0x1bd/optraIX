package org.kvxd.optraix.redstone.optraix.collection

import java.io.Closeable
import java.io.RandomAccessFile

internal class IntBuffer(capacity: Int = 16, private val spillEntries: Int = DefaultSpillEntries) : Closeable {
    private var data = IntArray(capacity)
    private var spill: RandomAccessFile? = null
    private var spillFile: java.io.File? = null

    var size = 0
        private set

    fun add(value: Int) {
        val file = spill
        if (file != null) {
            file.seek(size.toLong() * Int.SIZE_BYTES)
            file.writeInt(value)
            size++
            return
        }
        if (size == spillEntries) spill()
        if (spill != null) {
            add(value)
            return
        }
        if (size == data.size) data = data.copyOf(size * 2)
        data[size++] = value
    }

    fun clear() {
        size = 0
        spill?.setLength(0)
    }

    operator fun get(index: Int): Int {
        val file = spill ?: return data[index]
        file.seek(index.toLong() * Int.SIZE_BYTES)
        return file.readInt()
    }

    private fun spill() {
        val path = SpillStorage.create("int-")
        val file = RandomAccessFile(path, "rw")
        for (index in 0 until size) file.writeInt(data[index])
        data = IntArray(0)
        spillFile = path
        spill = file
    }

    override fun close() {
        spill?.close()
        spill = null
        spillFile?.delete()
        spillFile = null
    }

    private companion object {
        const val DefaultSpillEntries = 1_048_576
    }
}
