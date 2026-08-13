package org.kvxd.optraix.worldedit.history

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataOutputStream
import java.io.File
import org.kvxd.optraix.world.BlockEntity
import java.io.DataInputStream

internal class ChangeJournal(
    directory: File,
    expectedEntries: Int,
) : Closeable {

    private val file: File? = if (expectedEntries > SpillThreshold) {
        directory.mkdirs()
        val required = expectedEntries.toLong() * RecordBytes + MinimumFreeBytes
        check(directory.usableSpace >= required) {
            "WorldEdit undo needs ${required / Mib} MiB of temporary disk space in ${directory.absolutePath}"
        }
        File.createTempFile("worldedit-", ".journal", directory)
    } else {
        null
    }

    private var output: DataOutputStream? = file?.outputStream()?.let { DataOutputStream(BufferedOutputStream(it)) }
    private var positions = if (file == null) LongArray(expectedEntries.coerceAtLeast(1)) else LongArray(0)
    private var states = if (file == null) IntArray(expectedEntries.coerceAtLeast(1)) else IntArray(0)
    private var entities =
        if (file == null) arrayOfNulls<BlockEntity>(expectedEntries.coerceAtLeast(1)) else emptyArray()
    private val spilledEntities = if (file != null) HashMap<Int, BlockEntity>() else null

    var size = 0
        private set

    val spilled: Boolean
        get() = file != null

    fun add(position: Long, state: Int, entity: BlockEntity?) {
        val stream = output
        if (stream != null) {
            stream.writeLong(position)
            stream.writeInt(state)
            if (entity != null) spilledEntities?.put(size, entity)
        } else {
            ensureCapacity()
            positions[size] = position
            states[size] = state
            entities[size] = entity
        }
        size++
    }

    fun finish(): UndoEntry {
        output?.flush()
        output?.close()
        output = null
        return if (file == null) {
            UndoEntry(positions.copyOf(size), states.copyOf(size), size, entities.copyOf(size))
        } else {
            FileUndoEntry(file, size, spilledEntities.orEmpty())
        }
    }

    fun forEachForward(action: (Long, Int, BlockEntity?) -> Unit) {
        output?.flush()
        val journal = file
        if (journal == null) {
            for (index in 0 until size) action(positions[index], states[index], entities[index])
            return
        }
        DataInputStream(BufferedInputStream(journal.inputStream())).use { input ->
            repeat(size) { index ->
                val position = input.readLong()
                val state = input.readInt()
                action(position, state, spilledEntities?.get(index))
            }
        }
    }

    private fun ensureCapacity() {
        if (size < positions.size) return
        val capacity = if (size < 1 shl 20) size * 2 else size + (size shr 1)
        positions = positions.copyOf(capacity)
        states = states.copyOf(capacity)
        entities = entities.copyOf(capacity)
    }

    override fun close() {
        output?.close()
        output = null
        file?.delete()
    }

    companion object {
        const val SpillThreshold = 1_000_000
        private const val RecordBytes = 12L
        private const val MinimumFreeBytes = 64L * 1024L * 1024L
        private const val Mib = 1024L * 1024L
    }
}
