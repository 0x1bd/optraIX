package org.kvxd.optraix.dh.lod

import org.kvxd.optraix.world.GameWorld

internal class DHLodCache(private val maxBytes: Long = 256L * 1024 * 1024) {
    private val builder = DHLodBuilder()
    private val entries = LinkedHashMap<Key, Entry>(256, 0.75f, true)
    private var usedBytes = 0L

    fun get(world: GameWorld, position: DHSectionPos): DHLod {
        var signature = signature(world, position)
        synchronized(entries) {
            entries[Key(world, position.packed)]?.takeIf { it.signature == signature }?.let { return it.lod }
        }

        while (true) {
            val lod = builder.build(world, position)
            val after = signature(world, position)
            if (signature == after) {
                put(Key(world, position.packed), Entry(signature, lod))
                return lod
            }
            signature = after
        }
    }

    fun clear(world: GameWorld) {
        synchronized(entries) {
            val iterator = entries.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.key.world === world) {
                    usedBytes -= entry.value.size
                    iterator.remove()
                }
            }
        }
    }

    private fun put(key: Key, entry: Entry) {
        if (entry.size > maxBytes) return
        synchronized(entries) {
            entries.put(key, entry)?.let { usedBytes -= it.size }
            usedBytes += entry.size
            val iterator = entries.entries.iterator()
            while (usedBytes > maxBytes && iterator.hasNext()) {
                val eldest = iterator.next()
                usedBytes -= eldest.value.size
                iterator.remove()
            }
        }
    }

    private fun signature(world: GameWorld, position: DHSectionPos): Long {
        val firstChunkX = position.x shl (position.detailLevel - 4)
        val firstChunkZ = position.z shl (position.detailLevel - 4)
        var result = -3750763034362895579L
        for (dx in 0 until 4) {
            for (dz in 0 until 4) {
                val chunk = world.chunkIfLoaded(firstChunkX + dx, firstChunkZ + dz)
                val value = if (chunk == null) 0L else {
                    (System.identityHashCode(chunk).toLong() shl 32) xor chunk.revision
                }
                result = (result xor value) * 1099511628211L
            }
        }
        return result
    }

    private class Key(val world: GameWorld, val position: Long) {
        override fun equals(other: Any?): Boolean =
            other is Key && world === other.world && position == other.position

        override fun hashCode(): Int = 31 * System.identityHashCode(world) + position.hashCode()
    }

    private data class Entry(val signature: Long, val lod: DHLod) {
        val size: Int get() = lod.data.size
    }
}
