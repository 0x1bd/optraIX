package org.kvxd.optraix.worldedit.schematic

import java.io.BufferedOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPOutputStream
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import net.lenni0451.mcstructs.nbt.tags.IntArrayTag
import net.lenni0451.mcstructs.nbt.tags.IntTag
import net.lenni0451.mcstructs.nbt.tags.ShortTag
import net.lenni0451.mcstructs.nbt.tags.StringTag
import org.kvxd.optraix.block.mcData
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.nbt.NbtIo
import org.kvxd.optraix.nbt.compoundOf
import org.kvxd.optraix.nbt.string
import org.kvxd.optraix.world.BlockEntityNbt
import org.kvxd.optraix.worldedit.clipboard.Clipboard

internal object SchematicWriter {

    fun write(file: File, clipboard: Clipboard) {
        validateDimensions(clipboard)
        val palette = paletteOf(clipboard)
        val blockDataSize = blockDataSize(clipboard, palette)

        DataOutputStream(
            GZIPOutputStream(BufferedOutputStream(file.outputStream(), BufferSize), BufferSize)
        ).use { output ->
            output.writeByte(Compound)
            output.writeUTF("")
            output.writeByte(Compound)
            output.writeUTF("Schematic")
            NbtIo.writeNamed(output, "Version", IntTag(Schematic.EXPORT_FORMAT_VERSION))
            NbtIo.writeNamed(output, "DataVersion", IntTag(MinecraftDataVersion))
            NbtIo.writeNamed(output, "Width", ShortTag(clipboard.sizeX.toShort()))
            NbtIo.writeNamed(output, "Height", ShortTag(clipboard.sizeY.toShort()))
            NbtIo.writeNamed(output, "Length", ShortTag(clipboard.sizeZ.toShort()))
            NbtIo.writeNamed(
                output,
                "Offset",
                IntArrayTag(intArrayOf(clipboard.offset.x, clipboard.offset.y, clipboard.offset.z)),
            )
            writeMetadata(output, clipboard)
            writeBlocks(output, clipboard, palette, blockDataSize)
            output.writeByte(End)
            output.writeByte(End)
        }
    }

    private fun validateDimensions(clipboard: Clipboard) {
        val dimensions = listOf(clipboard.sizeX, clipboard.sizeY, clipboard.sizeZ)
        if (dimensions.any { it <= 0 || it > UnsignedShortMax }) {
            throw SchematicException("Sponge schematics support dimensions from 1 to $UnsignedShortMax")
        }
    }

    private fun paletteOf(clipboard: Clipboard): LinkedHashMap<Int, Int> {
        val palette = LinkedHashMap<Int, Int>()
        palette[Blocks.Air.defaultState] = 0
        clipboard.forEachNonAir { _, state -> palette.getOrPut(state) { palette.size } }
        return palette
    }

    private fun blockDataSize(clipboard: Clipboard, palette: Map<Int, Int>): Int {
        var size = clipboard.volume.toLong()
        clipboard.forEachNonAir { _, state ->
            size += varIntSize(palette.getValue(state)) - 1
        }
        if (size > Int.MAX_VALUE) {
            throw SchematicException("encoded schematic block data exceeds ${Int.MAX_VALUE} bytes")
        }
        return size.toInt()
    }

    private fun writeMetadata(output: DataOutputStream, clipboard: Clipboard) {
        NbtIo.writeNamed(
            output,
            "Metadata",
            compoundOf(
                "WEOffsetX" to IntTag(clipboard.offset.x),
                "WEOffsetY" to IntTag(clipboard.offset.y),
                "WEOffsetZ" to IntTag(clipboard.offset.z),
            ),
        )
    }

    private fun writeBlocks(
        output: DataOutputStream,
        clipboard: Clipboard,
        palette: LinkedHashMap<Int, Int>,
        blockDataSize: Int,
    ) {
        output.writeByte(Compound)
        output.writeUTF("Blocks")

        val paletteTag = CompoundTag()
        for ((state, index) in palette) {
            paletteTag.add("minecraft:${mcData.describeState(state)}", IntTag(index))
        }
        NbtIo.writeNamed(output, "Palette", paletteTag)

        output.writeByte(ByteArray)
        output.writeUTF("Data")
        output.writeInt(blockDataSize)
        writeBlockData(output, clipboard, palette)
        writeBlockEntities(output, clipboard)
        output.writeByte(End)
    }

    private fun writeBlockData(
        output: DataOutputStream,
        clipboard: Clipboard,
        palette: Map<Int, Int>,
    ) {
        if (!clipboard.isSparse) {
            for (position in 0 until clipboard.volume) {
                val x = position % clipboard.sizeX
                val z = (position / clipboard.sizeX) % clipboard.sizeZ
                val y = position / (clipboard.sizeX * clipboard.sizeZ)
                writeVarInt(output, palette.getValue(clipboard[x, y, z]))
            }
            return
        }
        var storedEntry = 0
        var storedPosition = if (clipboard.storedBlockCount == 0) -1 else clipboard.pastePosition(0, false)
        for (position in 0 until clipboard.volume) {
            val state = if (position == storedPosition) {
                val storedState = clipboard.pasteState(storedEntry, false)
                storedEntry++
                storedPosition = if (storedEntry < clipboard.storedBlockCount) {
                    clipboard.pastePosition(storedEntry, false)
                } else {
                    -1
                }
                storedState
            } else {
                Blocks.Air.defaultState
            }
            writeVarInt(output, palette.getValue(state))
        }
    }

    private fun writeBlockEntities(output: DataOutputStream, clipboard: Clipboard) {
        output.writeByte(List)
        output.writeUTF("BlockEntities")
        output.writeByte(Compound)
        output.writeInt(clipboard.blockEntities.size)
        for ((index, entity) in clipboard.blockEntities.toSortedMap()) {
            val x = index % clipboard.sizeX
            val z = (index / clipboard.sizeX) % clipboard.sizeZ
            val y = index / (clipboard.sizeX * clipboard.sizeZ)
            val data = BlockEntityNbt.toNbt(entity)
            val id = data.string("id")
                ?: throw SchematicException("block entity at $x,$y,$z has no id")
            NbtIo.writePayload(
                output,
                compoundOf(
                    "Id" to StringTag(id),
                    "Pos" to IntArrayTag(intArrayOf(x, y, z)),
                    "Data" to data,
                ),
            )
        }
    }

    private fun writeVarInt(output: DataOutputStream, input: Int) {
        var value = input
        while (value and -128 != 0) {
            output.writeByte(value and 127 or 128)
            value = value ushr 7
        }
        output.writeByte(value)
    }

    private fun varIntSize(input: Int): Int {
        var value = input
        var size = 1
        while (value and -128 != 0) {
            size++
            value = value ushr 7
        }
        return size
    }

    private const val End = 0
    private const val ByteArray = 7
    private const val List = 9
    private const val Compound = 10
    private const val MinecraftDataVersion = 3700
    private const val UnsignedShortMax = 65_535
    private const val BufferSize = 1 shl 20
}
