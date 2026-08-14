package org.kvxd.optraix.redstone.optraix.collection

import java.io.Closeable
import java.io.RandomAccessFile

internal class LongBuffer(capacity: Int = 16, private val spillEntries: Int = DefaultSpillEntries) : Closeable {
    private var data = LongArray(capacity)
    private var spill: RandomAccessFile? = null
    private var spillFile: java.io.File? = null

    var size = 0
        private set

    fun add(value: Long) {
        val file = spill
        if (file != null) {
            file.seek(size.toLong() * Long.SIZE_BYTES)
            file.writeLong(value)
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

    operator fun get(index: Int): Long {
        val file = spill ?: return data[index]
        file.seek(index.toLong() * Long.SIZE_BYTES)
        return file.readLong()
    }

    private fun spill() {
        val path = SpillStorage.create("long-")
        val file = RandomAccessFile(path, "rw")
        for (index in 0 until size) file.writeLong(data[index])
        data = LongArray(0)
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
