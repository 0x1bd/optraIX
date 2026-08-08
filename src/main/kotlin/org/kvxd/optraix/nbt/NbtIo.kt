package org.kvxd.optraix.nbt

import net.lenni0451.mcstructs.nbt.NbtTag
import net.lenni0451.mcstructs.nbt.NbtType
import net.lenni0451.mcstructs.nbt.tags.ByteArrayTag
import net.lenni0451.mcstructs.nbt.tags.ByteTag
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import net.lenni0451.mcstructs.nbt.tags.DoubleTag
import net.lenni0451.mcstructs.nbt.tags.FloatTag
import net.lenni0451.mcstructs.nbt.tags.IntArrayTag
import net.lenni0451.mcstructs.nbt.tags.IntTag
import net.lenni0451.mcstructs.nbt.tags.ListTag
import net.lenni0451.mcstructs.nbt.tags.LongArrayTag
import net.lenni0451.mcstructs.nbt.tags.LongTag
import net.lenni0451.mcstructs.nbt.tags.ShortTag
import net.lenni0451.mcstructs.nbt.tags.StringTag
import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.io.PushbackInputStream
import java.util.zip.GZIPInputStream

object NbtIo {

    fun readCompressedOrPlain(
        input: InputStream,
        byteArrayReader: ((String, Int, DataInputStream) -> ByteArrayTag?)? = null,
    ): CompoundTag {
        val pushback = PushbackInputStream(input, 2)
        val first = pushback.read()
        if (first < 0) throw EOFException("empty nbt stream")
        val second = pushback.read()
        if (second >= 0) pushback.unread(second)
        pushback.unread(first)
        val stream = if (first == 0x1f && second == 0x8b) GZIPInputStream(pushback) else pushback
        return readNamed(DataInputStream(stream.buffered()), byteArrayReader)
    }

    fun readNamed(
        input: DataInputStream,
        byteArrayReader: ((String, Int, DataInputStream) -> ByteArrayTag?)? = null,
    ): CompoundTag {
        val type = input.readUnsignedByte()
        if (type == 0) return CompoundTag()
        input.readUTF()
        val tag = readPayload(input, type, "", byteArrayReader)
        return tag as? CompoundTag ?: CompoundTag()
    }

    private fun readPayload(
        input: DataInputStream,
        type: Int,
        path: String,
        byteArrayReader: ((String, Int, DataInputStream) -> ByteArrayTag?)?,
    ): NbtTag = when (type) {
        1 -> ByteTag(input.readByte())
        2 -> ShortTag(input.readShort())
        3 -> IntTag(input.readInt())
        4 -> LongTag(input.readLong())
        5 -> FloatTag(input.readFloat())
        6 -> DoubleTag(input.readDouble())
        7 -> {
            val size = input.readInt()
            if (size < 0) throw IllegalArgumentException("negative nbt byte array size $size")
            byteArrayReader?.invoke(path, size, input) ?: run {
                val bytes = ByteArray(size)
                input.readFully(bytes)
                ByteArrayTag(bytes)
            }
        }
        8 -> StringTag(input.readUTF())
        9 -> {
            val elementType = input.readUnsignedByte()
            val size = input.readInt()
            if (elementType == 0 || size <= 0) {
                ListTag<NbtTag>()
            } else {
                val values = MutableList(size) { readPayload(input, elementType, path, byteArrayReader) }
                ListTag(nbtType(elementType), values)
            }
        }
        10 -> {
            val values = LinkedHashMap<String, NbtTag>()
            while (true) {
                val entryType = input.readUnsignedByte()
                if (entryType == 0) break
                val name = input.readUTF()
                val childPath = if (path.isEmpty()) name else "$path/$name"
                values[name] = readPayload(input, entryType, childPath, byteArrayReader)
            }
            CompoundTag(values)
        }
        11 -> {
            val size = input.readInt()
            IntArrayTag(IntArray(size) { input.readInt() })
        }
        12 -> {
            val size = input.readInt()
            LongArrayTag(LongArray(size) { input.readLong() })
        }
        else -> throw IllegalArgumentException("unsupported nbt tag type $type")
    }

    private fun nbtType(elementType: Int): NbtType =
        NbtType.byId(elementType)
            ?: throw IllegalArgumentException("unsupported nbt list element type $elementType")
}
