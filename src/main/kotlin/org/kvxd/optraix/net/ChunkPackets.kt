package org.kvxd.optraix.net

import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import org.kvxd.kmcprotocol.core.encoding.PacketWriter
import org.kvxd.kmcprotocol.extensions.chunk.ChunkFormat
import org.kvxd.kmcprotocol.extensions.chunk.Palette
import org.kvxd.kmcprotocol.extensions.chunk.PaletteKind
import org.kvxd.kmcprotocol.extensions.chunk.PalettedContainer
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundMapChunkPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.ChunkBlockEntity
import org.kvxd.optraix.world.BlockEntityNbt
import org.kvxd.optraix.world.Chunk
import org.kvxd.optraix.world.ChunkSection
import org.kvxd.optraix.world.SECTION_COUNT
import org.kvxd.optraix.world.WORLD_MIN_Y
import org.kvxd.kmcprotocol.extensions.chunk.ChunkSection as WireChunkSection
import org.kvxd.optraix.mcdata.v1_20_4.Blocks

object ChunkPackets {

    private val format = ChunkFormat.v1_18(SECTION_COUNT)

    private const val LightBytesPerSection = 2048

    private val fullLightMask: List<Long> = buildLightMask()

    private val fullSkyLightSection: List<Short> = List(LightBytesPerSection) { 0xFF.toShort() }

    private val fullSkyLight: List<List<Short>> = List(SECTION_COUNT + 2) { fullSkyLightSection }

    private val airBiomes = PalettedContainer.ofSingleValue(PaletteKind.Biomes, 0)

    private val airSectionBytes: ByteArray = PacketWriter(16).let { writer ->
        WireChunkSection(
            blockCount = 0,
            fluidCount = 0,
            blockStates = PalettedContainer.ofSingleValue(PaletteKind.BlockStates, Blocks.Air.defaultState),
            biomes = airBiomes,
        ).write(writer, format)
        writer.toByteArray()
    }

    private val airRun: ByteArray = ByteArray(airSectionBytes.size * SECTION_COUNT).also { run ->
        for (index in 0 until SECTION_COUNT) airSectionBytes.copyInto(run, index * airSectionBytes.size)
    }

    private fun buildLightMask(): List<Long> {
        val bits = SECTION_COUNT + 2
        val longs = LongArray((bits + 63) / 64)
        for (i in 0 until bits) longs[i / 64] = longs[i / 64] or (1L shl (i % 64))
        return longs.toList()
    }

    private fun isAir(section: ChunkSection?): Boolean =
        section == null ||
                (section.bitsPerEntry == 0 && section.blockCount == 0 && section.palette[0] == Blocks.Air.defaultState)

    private fun wireSection(section: ChunkSection): WireChunkSection {
        val blockStates = when {
            section.bitsPerEntry == 0 ->
                PalettedContainer.ofSingleValue(PaletteKind.BlockStates, section.palette[0])

            section.isDirect -> PalettedContainer(
                PaletteKind.BlockStates, section.bitsPerEntry, Palette.Direct, section.data.copyOf()
            )

            else -> PalettedContainer(
                PaletteKind.BlockStates,
                section.bitsPerEntry,
                Palette.Indirect(section.palette.copyOf(section.paletteSize)),
                section.data.copyOf(),
            )
        }
        return WireChunkSection(
            blockCount = section.blockCount,
            fluidCount = 0,
            blockStates = blockStates,
            biomes = airBiomes,
        )
    }

    fun sectionData(chunk: Chunk): ByteArray =
        chunk.wireData ?: encodeSections(chunk).also { chunk.wireData = it }

    private fun encodeSections(chunk: Chunk): ByteArray {
        var capacity = 0
        for (index in 0 until SECTION_COUNT) {
            val section = chunk.sections[index]
            capacity += if (isAir(section)) {
                airSectionBytes.size
            } else {
                16 + section!!.paletteSize * 5 + section.data.size * 8
            }
        }

        val writer = PacketWriter(capacity)
        var run = 0
        for (index in 0 until SECTION_COUNT) {
            val section = chunk.sections[index]
            if (isAir(section)) {
                run++
                continue
            }
            if (run > 0) {
                writer.writeBytes(airRun, 0, run * airSectionBytes.size)
                run = 0
            }
            wireSection(section!!).write(writer, format)
        }
        if (run > 0) writer.writeBytes(airRun, 0, run * airSectionBytes.size)
        return writer.toByteArray()
    }

    fun encode(chunk: Chunk): ClientboundMapChunkPacket {
        val blockEntities = chunk.blockEntities.entries.map { (key, entity) ->
            ChunkBlockEntity(
                packed = ChunkBlockEntity.XZ(key and 15, (key shr 4) and 15),
                y = ((key shr 8) + WORLD_MIN_Y).toShort(),
                type = BlockEntityNbt.typeId(entity),
                nbtData = BlockEntityNbt.toNbt(entity),
            )
        }

        return ClientboundMapChunkPacket(
            x = chunk.x,
            z = chunk.z,
            heightmaps = CompoundTag(),
            chunkData = sectionData(chunk),
            blockEntities = blockEntities,
            skyLightMask = fullLightMask,
            blockLightMask = emptyList(),
            emptySkyLightMask = emptyList(),
            emptyBlockLightMask = fullLightMask,
            skyLight = fullSkyLight,
            blockLight = emptyList(),
        )
    }
}
