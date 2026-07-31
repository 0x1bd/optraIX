package org.kvxd.optraix.world


class WorldGenerator(val floorState: Int, val floorY: Int) {

    fun generate(chunk: Chunk) {
        val section = chunk.sectionFor(floorY, true) ?: return
        val y = floorY and 15
        for (z in 0 until 16) {
            for (x in 0 until 16) {
                section.set(Chunk.index(x, y, z), floorState)
            }
        }
    }
}
