package org.kvxd.optraix.worldedit

import java.io.DataInputStream
import java.io.File
import net.lenni0451.mcstructs.nbt.tags.ByteArrayTag
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import net.lenni0451.mcstructs.nbt.tags.IntArrayTag
import net.lenni0451.mcstructs.nbt.tags.ListTag
import org.kvxd.optraix.nbt.NbtIo
import org.kvxd.optraix.nbt.asIntOrNull
import org.kvxd.optraix.nbt.asStringOrNull
import org.kvxd.optraix.nbt.compound
import org.kvxd.optraix.nbt.int
import org.kvxd.optraix.nbt.list
import org.kvxd.optraix.nbt.tag
import org.kvxd.optraix.world.BlockEntityNbt
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.block.mcData

object Schematic {

    fun load(file: File): Clipboard {
        if (!file.isFile) throw SchematicException("no such schematic: ${file.name}")
        val root = file.inputStream().use { input ->
            NbtIo.readCompressedOrPlain(input) { path, size, data ->
                if (!isBlockData(path)) return@readCompressedOrPlain null
                data.skipNBytes(size.toLong())
                ByteArrayTag(byteArrayOf())
            }
        }
        val schematic = root.compound("Schematic") ?: root
        val v3 = schematic.compound("Blocks") != null
        if (!v3 && schematic.tag("Palette") == null) {
            throw SchematicException("unrecognised schematic format in ${file.name}")
        }
        val (width, height, length) = dimensions(schematic)
        val volume = checkedVolume(width, height, length)
        val blocks = if (v3) schematic.compound("Blocks")!! else schematic
        val palette = blocks.compound("Palette")
            ?: throw SchematicException(if (v3) "schematic has no Blocks.Palette" else "schematic has no Palette")
        val lookup = paletteLookup(palette)
        val builder = SparseClipboardBuilder(minOf(volume, INITIAL_CAPACITY))
        var decoded = false
        file.inputStream().use { input ->
            NbtIo.readCompressedOrPlain(input) { path, size, data ->
                if (!isBlockData(path)) return@readCompressedOrPlain null
                if (decoded) throw SchematicException("schematic contains more than one block data array")
                decode(data, size, volume, lookup, builder)
                decoded = true
                ByteArrayTag(byteArrayOf())
            }
        }
        if (!decoded) {
            throw SchematicException(if (v3) "schematic has no Blocks.Data" else "schematic has no BlockData")
        }
        val clipboard = Clipboard.sparse(
            width,
            height,
            length,
            offsetOf(schematic),
            builder.build(sorted = true),
        )
        val blockEntities = blocks.list("BlockEntities")
        loadBlockEntities(clipboard, blockEntities)
        return clipboard
    }

    private fun dimensions(schematic: CompoundTag): Triple<Int, Int, Int> {
        val width = schematic.int("Width") ?: throw SchematicException("schematic has no Width")
        val height = schematic.int("Height") ?: throw SchematicException("schematic has no Height")
        val length = schematic.int("Length") ?: throw SchematicException("schematic has no Length")
        if (width <= 0 || height <= 0 || length <= 0) throw SchematicException("schematic dimensions must be positive")
        return Triple(width, height, length)
    }

    private fun checkedVolume(width: Int, height: Int, length: Int): Int = try {
        Math.multiplyExact(Math.multiplyExact(width, height), length)
    } catch (_: ArithmeticException) {
        throw SchematicException("schematic volume exceeds ${Int.MAX_VALUE} blocks")
    }

    private fun offsetOf(schematic: CompoundTag): BlockPos {
        val metadata = schematic.compound("Metadata")
        if (metadata != null) {
            val x = metadata.int("WEOffsetX")
            val y = metadata.int("WEOffsetY")
            val z = metadata.int("WEOffsetZ")
            if (x != null && y != null && z != null) return BlockPos(x, y, z)
        }
        return BlockPos(0, 0, 0)
    }

    private fun paletteLookup(palette: CompoundTag): IntArray {
        val maxIndex = palette.value.values.mapNotNull { it.asIntOrNull() }.maxOrNull() ?: 0
        val lookup = IntArray(maxIndex + 1) { Blocks.Air.defaultState }
        for ((name, value) in palette) {
            val index = value.asIntOrNull() ?: continue
            if (index >= 0) lookup[index] = mcData.blockState(name) ?: Blocks.Air.defaultState
        }
        return lookup
    }

    private fun decode(
        input: DataInputStream,
        byteCount: Int,
        volume: Int,
        lookup: IntArray,
        builder: SparseClipboardBuilder,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var remaining = byteCount
        var target = 0
        var value = 0
        var shift = 0
        while (remaining > 0) {
            val count = minOf(remaining, buffer.size)
            input.readFully(buffer, 0, count)
            remaining -= count
            for (index in 0 until count) {
                val byte = buffer[index].toInt() and 0xFF
                value = value or ((byte and 0x7F) shl shift)
                if (byte and 0x80 != 0) {
                    shift += 7
                    if (shift > 35) throw SchematicException("malformed varint in block data")
                    continue
                }
                if (target >= volume) throw SchematicException("block data contains more entries than the schematic volume")
                builder.add(target, lookup.getOrElse(value) { Blocks.Air.defaultState })
                target++
                value = 0
                shift = 0
            }
        }
        if (shift != 0) throw SchematicException("truncated varint in block data")
        if (target != volume) throw SchematicException("block data contains $target entries for a volume of $volume")
    }

    private fun loadBlockEntities(clipboard: Clipboard, blockEntities: ListTag<*>?) {
        blockEntities?.forEach { element ->
            val compound = element as? CompoundTag ?: return@forEach
            val pos = compound.tag("Pos")
            val coords = when (pos) {
                is IntArrayTag -> pos.value
                is ListTag<*> -> pos.mapNotNull { it.asIntOrNull() }.toIntArray()
                else -> return@forEach
            }
            if (coords.size < 3) return@forEach
            val id = (compound.tag("Id") ?: compound.tag("id"))?.asStringOrNull() ?: return@forEach
            val payload = compound.compound("Data") ?: compound
            BlockEntityNbt.fromNbt(id, payload)?.let {
                clipboard.blockEntities[clipboard.index(coords[0], coords[1], coords[2])] = it
            }
        }
    }

    private fun isBlockData(path: String): Boolean =
        path == "BlockData" || path.endsWith("/BlockData") || path == "Blocks/Data" || path.endsWith("/Blocks/Data")

    private const val BUFFER_SIZE = 1 shl 20
    private const val INITIAL_CAPACITY = 1 shl 20
}
