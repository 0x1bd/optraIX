package org.kvxd.optraix.worldedit.history

import java.io.Closeable
import org.kvxd.optraix.world.BlockEntity

open class UndoEntry(
    open val positions: LongArray,
    open val states: IntArray,
    open val size: Int = positions.size,
    val entities: Array<BlockEntity?> = arrayOfNulls(positions.size),
) : Closeable {
    open fun forEach(action: (Long, Int, BlockEntity?) -> Unit) {
        for (index in 0 until size) action(positions[index], states[index], entities[index])
    }

    open fun entryAt(index: Int): UndoRecord = UndoRecord(positions[index], states[index], entities[index])

    override fun close() = Unit
}

fun UndoEntry.positionArray(): LongArray = LongArray(size).also { result ->
    var index = 0
    forEach { position, _, _ -> result[index++] = position }
}
