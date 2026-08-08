package org.kvxd.optraix.world


class WorldGenerator(val floorState: Int, val floorY: Int) {

    fun generate(chunk: Chunk) {
        val section = chunk.sectionFor(floorY, true) ?: return
        section.fillLayer(floorY and 15, floorState)
    }
}
