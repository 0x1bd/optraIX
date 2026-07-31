package org.kvxd.optraix.net

import net.benwoodworth.knbt.NbtCompound
import org.kvxd.kmcprotocol.extensions.chunk.ChunkFormat
import org.kvxd.kmcprotocol.extensions.chunk.ChunkSections
import org.kvxd.kmcprotocol.extensions.chunk.Palette
import org.kvxd.kmcprotocol.extensions.chunk.PaletteKind
import org.kvxd.kmcprotocol.extensions.chunk.PalettedContainer
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundMapChunkPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.ChunkBlockEntity
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.world.BlockEntityNbt
import org.kvxd.optraix.world.Chunk
import org.kvxd.optraix.world.SECTION_COUNT
import org.kvxd.optraix.world.WORLD_MIN_Y
import org.kvxd.kmcprotocol.extensions.chunk.ChunkSection as WireChunkSection

object ChunkPackets {

    private val format = ChunkFormat.v1_18(SECTION_COUNT)

    private val emptyLightMask: List<Long> = buildLightMask()

    private fun buildLightMask(): List<Long> {
        val bits = SECTION_COUNT + 2
        val longs = LongArray((bits + 63) / 64)
        for (i in 0 until bits) longs[i / 64] = longs[i / 64] or (1L shl (i % 64))
        return longs.toList()
    }

    fun encode(chunk: Chunk): ClientboundMapChunkPacket {
        val sections = ArrayList<WireChunkSection>(SECTION_COUNT)
        for (index in 0 until SECTION_COUNT) {
            val section = chunk.sections[index]
            val blockStates = if (section == null) {
                PalettedContainer.ofSingleValue(PaletteKind.BlockStates, Blocks.airState)
            } else {
                when {
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
            }
            sections += WireChunkSection(
                blockCount = section?.blockCount ?: 0,
                fluidCount = 0,
                blockStates = blockStates,
                biomes = PalettedContainer.ofSingleValue(PaletteKind.Biomes, 0),
            )
        }

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
            heightmaps = NbtCompound(emptyMap()),
            chunkData = ChunkSections.encode(sections, format),
            blockEntities = blockEntities,
            skyLightMask = emptyList(),
            blockLightMask = emptyList(),
            emptySkyLightMask = emptyLightMask,
            emptyBlockLightMask = emptyLightMask,
            skyLight = emptyList(),
            blockLight = emptyList(),
        )
    }
}
