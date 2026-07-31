package org.kvxd.optraix.nbt

import net.benwoodworth.knbt.NbtByte
import net.benwoodworth.knbt.NbtByteArray
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtDouble
import net.benwoodworth.knbt.NbtFloat
import net.benwoodworth.knbt.NbtInt
import net.benwoodworth.knbt.NbtIntArray
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtLong
import net.benwoodworth.knbt.NbtLongArray
import net.benwoodworth.knbt.NbtShort
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream

object NbtIo {

    fun readCompressedOrPlain(input: InputStream): NbtCompound {
        val pushback = PushbackInputStream(input, 2)
        val first = pushback.read()
        if (first < 0) throw EOFException("empty nbt stream")
        val second = pushback.read()
        if (second >= 0) pushback.unread(second)
        pushback.unread(first)
        val stream = if (first == 0x1f && second == 0x8b) GZIPInputStream(pushback) else pushback
        return readNamed(DataInputStream(stream.buffered()))
    }

    fun readNamed(input: DataInputStream): NbtCompound {
        val type = input.readUnsignedByte()
        if (type == 0) return NbtCompound(emptyMap())
        input.readUTF()
        val tag = readPayload(input, type)
        return tag as? NbtCompound ?: NbtCompound(emptyMap())
    }

    private fun readPayload(input: DataInputStream, type: Int): NbtTag = when (type) {
        1 -> NbtByte(input.readByte())
        2 -> NbtShort(input.readShort())
        3 -> NbtInt(input.readInt())
        4 -> NbtLong(input.readLong())
        5 -> NbtFloat(input.readFloat())
        6 -> NbtDouble(input.readDouble())
        7 -> {
            val size = input.readInt()
            val bytes = ByteArray(size)
            input.readFully(bytes)
            NbtByteArray(bytes)
        }
        8 -> NbtString(input.readUTF())
        9 -> {
            val elementType = input.readUnsignedByte()
            val size = input.readInt()
            if (elementType == 0 || size <= 0) {
                NbtList(emptyList<NbtByte>())
            } else {
                toList(List(size) { readPayload(input, elementType) }, elementType)
            }
        }
        10 -> {
            val values = LinkedHashMap<String, NbtTag>()
            while (true) {
                val entryType = input.readUnsignedByte()
                if (entryType == 0) break
                val name = input.readUTF()
                values[name] = readPayload(input, entryType)
            }
            NbtCompound(values)
        }
        11 -> {
            val size = input.readInt()
            NbtIntArray(IntArray(size) { input.readInt() })
        }
        12 -> {
            val size = input.readInt()
            NbtLongArray(LongArray(size) { input.readLong() })
        }
        else -> throw IllegalArgumentException("unsupported nbt tag type $type")
    }

    @Suppress("UNCHECKED_CAST")
    private fun toList(values: List<NbtTag>, elementType: Int): NbtList<*> = when (elementType) {
        1 -> NbtList(values as List<NbtByte>)
        2 -> NbtList(values as List<NbtShort>)
        3 -> NbtList(values as List<NbtInt>)
        4 -> NbtList(values as List<NbtLong>)
        5 -> NbtList(values as List<NbtFloat>)
        6 -> NbtList(values as List<NbtDouble>)
        7 -> NbtList(values as List<NbtByteArray>)
        8 -> NbtList(values as List<NbtString>)
        9 -> NbtList(values as List<NbtList<*>>)
        10 -> NbtList(values as List<NbtCompound>)
        11 -> NbtList(values as List<NbtIntArray>)
        12 -> NbtList(values as List<NbtLongArray>)
        else -> throw IllegalArgumentException("unsupported nbt list element type $elementType")
    }
}
