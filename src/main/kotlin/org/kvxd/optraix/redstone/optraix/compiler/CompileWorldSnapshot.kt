package org.kvxd.optraix.redstone.optraix.compiler

import org.kvxd.optraix.world.ChunkPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.SECTION_COUNT

internal object CompileWorldSnapshot {
    fun create(source: GameWorld): GameWorld {
        val chunks = source.snapshotChunks()
        val included = HashMap<Long, BooleanArray>()
        for (chunk in chunks) {
            for (sectionIndex in 0 until SECTION_COUNT) {
                val section = chunk.sections[sectionIndex] ?: continue
                val candidate = synchronized(section) {
                    section.blockCount > 0 && sectionHasCandidates(section)
                }
                if (!candidate) continue
                for (chunkX in chunk.x - 1..chunk.x + 1) {
                    for (chunkZ in chunk.z - 1..chunk.z + 1) {
                        val sections = included.getOrPut(ChunkPos.key(chunkX, chunkZ)) {
                            BooleanArray(SECTION_COUNT)
                        }
                        for (targetSection in sectionIndex - 1..sectionIndex + 1) {
                            if (targetSection in 0 until SECTION_COUNT) sections[targetSection] = true
                        }
                    }
                }
            }
        }

        val snapshot = GameWorld(source.generator)
        for (chunk in chunks) {
            val sections = included[ChunkPos.key(chunk.x, chunk.z)] ?: continue
            val target = snapshot.replaceChunk(chunk.x, chunk.z)
            for (sectionIndex in 0 until SECTION_COUNT) {
                if (!sections[sectionIndex]) continue
                target.sections[sectionIndex] = chunk.sections[sectionIndex]?.snapshotCopy()
            }
            for ((key, entity) in chunk.blockEntities) {
                val sectionIndex = (key ushr 12)
                if (sectionIndex in sections.indices && sections[sectionIndex]) target.blockEntities[key] = entity
            }
        }
        snapshot.restoreTicks(source.snapshotTicks())
        return snapshot
    }
}
