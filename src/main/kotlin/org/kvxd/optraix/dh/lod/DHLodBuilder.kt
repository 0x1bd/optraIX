package org.kvxd.optraix.dh.lod

import org.kvxd.optraix.block.mcData
import org.kvxd.optraix.dh.io.DHByteWriter
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.world.ChunkSection
import org.kvxd.optraix.world.Chunk
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.SECTION_COUNT
import org.kvxd.optraix.world.WORLD_HEIGHT
import org.tukaani.xz.LZMA2Options
import org.tukaani.xz.XZ
import org.tukaani.xz.XZOutputStream
import java.io.ByteArrayOutputStream

internal class DHLodBuilder {
    fun build(world: GameWorld, position: DHSectionPos): DHLod {
        require(position.detailLevel == DHSectionPos.SupportedDetailLevel)
        val mappings = ArrayList<String>()
        val mappingByState = HashMap<Int, Int>()
        val columns = ArrayList<List<Run>>(DHSectionPos.Width * DHSectionPos.Width)
        val firstBlockX = position.x shl position.detailLevel
        val firstBlockZ = position.z shl position.detailLevel
        val sections = snapshotSections(world, firstBlockX shr 4, firstBlockZ shr 4)

        for (relativeX in 0 until DHSectionPos.Width) {
            for (relativeZ in 0 until DHSectionPos.Width) {
                columns += column(sections, relativeX, relativeZ, mappingByState, mappings)
            }
        }

        val now = System.currentTimeMillis()
        return DHLod(encode(position, columns, mappings, now), now)
    }

    private fun column(
        chunks: Array<ChunkSnapshot?>,
        relativeX: Int,
        relativeZ: Int,
        mappingByState: MutableMap<Int, Int>,
        mappings: MutableList<String>,
    ): List<Run> {
        val chunk = chunks[(relativeX shr 4) * 4 + (relativeZ shr 4)]
        if (chunk == null) {
            return listOf(Run(mapping(Blocks.Air.defaultState, mappingByState, mappings), 0, WORLD_HEIGHT))
        }
        val runs = ArrayList<Run>(16)
        var state = Int.MIN_VALUE
        var start = 0
        var height = 0

        fun append(nextState: Int, count: Int) {
            if (nextState == state) {
                height += count
                return
            }
            if (height != 0) runs += Run(mapping(state, mappingByState, mappings), start, height)
            state = nextState
            start += height
            height = count
        }

        var nextSection = 0
        for (entryIndex in chunk.indices.indices) {
            val sectionIndex = chunk.indices[entryIndex]
            if (sectionIndex > nextSection) append(Blocks.Air.defaultState, (sectionIndex - nextSection) * 16)
            val section = chunk.sections[entryIndex]
            for (localY in 0 until 16) {
                append(section.get(Chunk.index(relativeX and 15, localY, relativeZ and 15)), 1)
            }
            nextSection = sectionIndex + 1
        }
        if (nextSection < SECTION_COUNT) append(Blocks.Air.defaultState, (SECTION_COUNT - nextSection) * 16)
        if (height != 0) runs += Run(mapping(state, mappingByState, mappings), start, height)
        runs.reverse()
        return runs
    }

    private fun snapshotSections(
        world: GameWorld,
        firstChunkX: Int,
        firstChunkZ: Int,
    ): Array<ChunkSnapshot?> = arrayOfNulls<ChunkSnapshot>(16).also { chunks ->
        for (dx in 0 until 4) {
            for (dz in 0 until 4) {
                val chunk = world.chunkIfLoaded(firstChunkX + dx, firstChunkZ + dz) ?: continue
                val indices = IntArray(chunk.sections.count { it != null && it.blockCount != 0 })
                if (indices.isEmpty()) continue
                val sections = arrayOfNulls<ChunkSection>(indices.size)
                var target = 0
                for (index in chunk.sections.indices) {
                    val section = chunk.sections[index] ?: continue
                    if (section.blockCount == 0) continue
                    indices[target] = index
                    sections[target] = section.snapshotCopy()
                    target++
                }
                @Suppress("UNCHECKED_CAST")
                chunks[dx * 4 + dz] = ChunkSnapshot(indices, sections as Array<ChunkSection>)
            }
        }
    }

    private fun mapping(state: Int, indexes: MutableMap<Int, Int>, mappings: MutableList<String>): Int =
        indexes.getOrPut(state) {
            val block = mcData.blockByStateId(state) ?: Blocks.Air
            val properties = block.propertiesOf(state)
            val propertyText = if (properties.isEmpty()) "" else properties.entries.joinToString(
                separator = "",
                prefix = "_STATE_",
            ) { (key, value) -> "{$key:$value}" }
            mappings += "minecraft:plains_DH-BSW_minecraft:${block.name}$propertyText"
            mappings.lastIndex
        }

    private fun encode(
        position: DHSectionPos,
        columns: List<List<Run>>,
        mappings: List<String>,
        now: Long,
    ): ByteArray = DHByteWriter(8192).apply {
        long(position.packed)
        int(0)

        compressed {
            for (column in columns) {
                short(column.size)
                for (run in column) long(run.packed)
            }
        }
        repeat(4) { int(0) }
        sizedBytes(CompressedGenerationSteps)
        sizedBytes(CompressedWorldCompressionTypes)
        compressed {
            int(mappings.size)
            for (mapping in mappings) shortString(mapping)
        }
        byte(DataFormatVersion)
        byte(Lzma2Compression)
        boolean(true)
        boolean(false)
        long(now)
        long(now)
    }.toByteArray()

    private fun DHByteWriter.compressed(body: DHByteWriter.() -> Unit) {
        val raw = DHByteWriter(8192).apply(body).toByteArray()
        sizedCompressed(raw)
    }

    private fun DHByteWriter.sizedCompressed(raw: ByteArray) {
        sizedBytes(compress(raw))
    }

    private fun DHByteWriter.sizedBytes(compressed: ByteArray) {
        int(compressed.size)
        bytes(compressed)
    }

    private fun compress(raw: ByteArray): ByteArray {
        val bytes = ByteArrayOutputStream()
        val options = LZMA2Options(1).apply {
            dictSize = raw.size.coerceIn(LZMA2Options.DICT_SIZE_MIN, 1 shl 20)
        }
        XZOutputStream(bytes, options, XZ.CHECK_CRC64).use { it.write(raw) }
        return bytes.toByteArray()
    }

    private data class Run(val mapping: Int, val startY: Int, val height: Int) {
        val packed: Long
            get() =
                (mapping.toLong() and 0xFFFFFFFFL) or
                    ((height.toLong() and 0xFFFL) shl 32) or
                    ((startY.toLong() and 0xFFFL) shl 44) or
                    (15L shl 56)
    }

    private data class ChunkSnapshot(val indices: IntArray, val sections: Array<ChunkSection>)

    private companion object {
        const val DataFormatVersion = 1
        const val Lzma2Compression = 3
        val GenerationSteps = ByteArray(DHSectionPos.Width * DHSectionPos.Width) { 9 }
        val WorldCompressionTypes = ByteArray(DHSectionPos.Width * DHSectionPos.Width)
        val CompressedGenerationSteps by lazy { DHLodBuilder().compress(GenerationSteps) }
        val CompressedWorldCompressionTypes by lazy { DHLodBuilder().compress(WorldCompressionTypes) }

        init {
            require(WORLD_HEIGHT <= 0xFFF)
        }
    }
}
