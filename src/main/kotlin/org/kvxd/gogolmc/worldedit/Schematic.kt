package org.kvxd.gogolmc.worldedit

import java.io.File
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.nbt.NbtIo
import org.kvxd.gogolmc.nbt.asIntOrNull
import org.kvxd.gogolmc.nbt.asStringOrNull
import org.kvxd.gogolmc.nbt.byteArray
import org.kvxd.gogolmc.nbt.compound
import org.kvxd.gogolmc.nbt.int
import org.kvxd.gogolmc.world.BlockEntityNbt
import org.kvxd.gogolmc.world.BlockPos

object Schematic {

    fun load(file: File): Clipboard {
        if (!file.isFile) throw SchematicException("no such schematic: ${file.name}")
        val root = file.inputStream().use { NbtIo.readCompressedOrPlain(it) }
        val schematic = root.compound("Schematic") ?: root
        return when {
            schematic.compound("Blocks") != null -> loadV3(schematic)
            schematic["Palette"] != null || schematic["BlockData"] != null -> loadV2(schematic)
            else -> throw SchematicException("unrecognised schematic format in ${file.name}")
        }
    }

    private fun dimensions(schematic: NbtCompound): Triple<Int, Int, Int> {
        val width = schematic.int("Width") ?: throw SchematicException("schematic has no Width")
        val height = schematic.int("Height") ?: throw SchematicException("schematic has no Height")
        val length = schematic.int("Length") ?: throw SchematicException("schematic has no Length")
        return Triple(width, height, length)
    }

    private fun offsetOf(schematic: NbtCompound, width: Int, height: Int, length: Int): BlockPos {
        val metadata = schematic.compound("Metadata")
        if (metadata != null) {
            val x = metadata.int("WEOffsetX")
            val y = metadata.int("WEOffsetY")
            val z = metadata.int("WEOffsetZ")
            if (x != null && y != null && z != null) return BlockPos(x, y, z)
        }
        return BlockPos(0, 0, 0)
    }

    private fun loadV2(schematic: NbtCompound): Clipboard {
        val (width, height, length) = dimensions(schematic)
        val palette = schematic.compound("Palette")
            ?: throw SchematicException("schematic has no Palette")
        val data = schematic.byteArray("BlockData")
            ?: throw SchematicException("schematic has no BlockData")
        return build(
            width, height, length,
            offsetOf(schematic, width, height, length),
            palette, data,
            schematic["BlockEntities"] as? NbtList<*>,
        )
    }

    private fun loadV3(schematic: NbtCompound): Clipboard {
        val (width, height, length) = dimensions(schematic)
        val blocks = schematic.compound("Blocks")
            ?: throw SchematicException("schematic has no Blocks container")
        val palette = blocks.compound("Palette")
            ?: throw SchematicException("schematic has no Blocks.Palette")
        val data = blocks.byteArray("Data")
            ?: throw SchematicException("schematic has no Blocks.Data")
        return build(
            width, height, length,
            offsetOf(schematic, width, height, length),
            palette, data,
            blocks["BlockEntities"] as? NbtList<*>,
        )
    }

    private fun build(
        width: Int,
        height: Int,
        length: Int,
        offset: BlockPos,
        palette: NbtCompound,
        data: ByteArray,
        blockEntities: NbtList<*>?,
    ): Clipboard {
        val maxIndex = palette.values.mapNotNull { it.asIntOrNull() }.maxOrNull() ?: 0
        val lookup = IntArray(maxIndex + 1) { Blocks.airState }
        for ((name, value) in palette) {
            val index = value.asIntOrNull() ?: continue
            lookup[index] = Blocks.parse(name) ?: Blocks.airState
        }

        val clipboard = Clipboard(width, height, length, offset, IntArray(width * height * length))
        var cursor = 0
        var target = 0
        while (cursor < data.size && target < clipboard.volume) {
            var value = 0
            var shift = 0
            while (true) {
                val byte = data[cursor++].toInt()
                value = value or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0) break
                shift += 7
                if (shift > 35) throw SchematicException("malformed varint in BlockData")
            }
            val y = target / (width * length)
            val z = (target % (width * length)) / width
            val x = (target % (width * length)) % width
            clipboard[x, y, z] = lookup.getOrElse(value) { Blocks.airState }
            target++
        }

        blockEntities?.forEach { element ->
            val compound = element as? NbtCompound ?: return@forEach
            val pos = compound["Pos"]
            val coords = when (pos) {
                is net.benwoodworth.knbt.NbtIntArray -> pos.toIntArray()
                is NbtList<*> -> pos.mapNotNull { it.asIntOrNull() }.toIntArray()
                else -> return@forEach
            }
            if (coords.size < 3) return@forEach
            val id = (compound["Id"] ?: compound["id"])?.asStringOrNull() ?: return@forEach
            val payload = compound.compound("Data") ?: compound
            BlockEntityNbt.fromNbt(id, payload)?.let {
                clipboard.blockEntities[clipboard.index(coords[0], coords[1], coords[2])] = it
            }
        }

        return clipboard
    }
}
