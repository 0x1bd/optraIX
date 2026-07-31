package org.kvxd.optraix.world


data class ChunkPos(val x: Int, val z: Int) {
    fun asLong(): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    companion object {
        fun key(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }
}
