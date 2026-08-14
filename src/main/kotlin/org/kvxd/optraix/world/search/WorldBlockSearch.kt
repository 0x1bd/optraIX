package org.kvxd.optraix.world.search

import org.kvxd.optraix.block.mcData
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.Chunk
import org.kvxd.optraix.world.ChunkSection
import org.kvxd.optraix.world.SECTION_COUNT
import org.kvxd.optraix.world.WORLD_MIN_Y

object WorldBlockSearch {

    data class Match(val position: BlockPos, val state: Int)

    data class Page(val matches: List<Match>, val hasNext: Boolean)

    fun matchingStates(query: String): BooleanArray {
        val terms = query.trim().lowercase().split(Whitespace).filter(String::isNotEmpty)
        if (terms.isEmpty()) return BooleanArray(StateDescriptions.size)
        return BooleanArray(StateDescriptions.size) { state ->
            state != Blocks.Air.defaultState && terms.all(StateDescriptions[state]::contains)
        }
    }

    fun page(
        chunks: List<Chunk>,
        matchingStates: BooleanArray,
        page: Int,
        pageSize: Int,
    ): Page {
        val skip = (page.toLong() - 1L) * pageSize
        val results = ArrayList<Match>(pageSize + 1)
        var matched = 0L

        chunkLoop@ for (chunk in chunks) {
            for (sectionIndex in 0 until SECTION_COUNT) {
                val section = chunk.sections[sectionIndex] ?: continue
                if (section.blockCount == 0 || !paletteCanMatch(section, matchingStates)) continue
                section.forEachState { slot, state ->
                    if (state !in matchingStates.indices || !matchingStates[state]) return@forEachState
                    if (matched++ < skip || results.size > pageSize) return@forEachState
                    results += Match(
                        BlockPos(
                            (chunk.x shl 4) + (slot and 15),
                            WORLD_MIN_Y + (sectionIndex shl 4) + (slot shr 8),
                            (chunk.z shl 4) + ((slot shr 4) and 15),
                        ),
                        state,
                    )
                }
                if (results.size > pageSize) break@chunkLoop
            }
        }

        val hasNext = results.size > pageSize
        if (hasNext) results.removeLast()
        return Page(results, hasNext)
    }

    fun describe(state: Int): String = StateDescriptions[state]

    private fun paletteCanMatch(section: ChunkSection, matchingStates: BooleanArray): Boolean {
        if (section.isDirect) return true
        for (index in 0 until section.paletteSize) {
            val state = section.palette[index]
            if (state in matchingStates.indices && matchingStates[state]) return true
        }
        return false
    }

    private val StateDescriptions: Array<String> by lazy {
        Array(mcData.blockStateCount) { state ->
            mcData.blockByStateId(state)?.let { "minecraft:${it.describe(state)}".lowercase() }.orEmpty()
        }
    }

    private val Whitespace = Regex("\\s+")
}
