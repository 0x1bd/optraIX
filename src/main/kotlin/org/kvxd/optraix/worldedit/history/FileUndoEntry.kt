package org.kvxd.optraix.worldedit.history

import java.io.BufferedInputStream
import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile
import org.kvxd.optraix.world.BlockEntity

class FileUndoEntry(
    val file: File,
    override val size: Int,
    private val entitiesByEntry: Map<Int, BlockEntity>,
) : UndoEntry(LongArray(0), IntArray(0), 0) {
    private var reader: RandomAccessFile? = null

    override val positions: LongArray
        get() = error("file-backed undo has no position array")

    override val states: IntArray
        get() = error("file-backed undo has no state array")

    override fun forEach(action: (Long, Int, BlockEntity?) -> Unit) {
        DataInputStream(BufferedInputStream(file.inputStream())).use { input ->
            repeat(size) { index ->
                val position = input.readLong()
                val state = input.readInt()
                action(position, state, entitiesByEntry[index])
            }
        }
    }

    override fun entryAt(index: Int): UndoRecord {
        val journal = reader ?: RandomAccessFile(file, "r").also { reader = it }
        journal.seek(index * RecordBytes)
        val position = journal.readLong()
        return UndoRecord(position, journal.readInt(), entitiesByEntry[index])
    }

    override fun close() {
        reader?.close()
        reader = null
        file.delete()
    }

    private companion object {
        const val RecordBytes = 12L
    }
}
