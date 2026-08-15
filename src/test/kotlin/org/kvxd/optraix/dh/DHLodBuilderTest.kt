package org.kvxd.optraix.dh

import org.kvxd.optraix.dh.lod.DHLodBuilder
import org.kvxd.optraix.dh.lod.DHLodCache
import org.kvxd.optraix.dh.lod.DHSectionPos
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WORLD_HEIGHT
import org.tukaani.xz.XZInputStream
import java.io.DataInputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class DHLodBuilderTest {
    @Test
    fun absentRegionProducesValidCompactAirLod() {
        val position = DHSectionPos(6, 2, -3)
        val lod = DHLodBuilder().build(GameWorld(), position)
        assertTrue(lod.data.size < 2048, "empty LOD was ${lod.data.size} bytes")

        val input = DataInputStream(lod.data.inputStream())
        assertEquals(position.packed, input.readLong())
        assertEquals(0, input.readInt())
        val columns = decompressed(input)
        repeat(64 * 64) {
            assertEquals(1, columns.readUnsignedShort())
            val point = columns.readLong()
            assertEquals(0, (point and 0xFFFFFFFFL).toInt())
            assertEquals(WORLD_HEIGHT, ((point ushr 32) and 0xFFF).toInt())
            assertEquals(0, ((point ushr 44) and 0xFFF).toInt())
            assertEquals(15, ((point ushr 56) and 0xF).toInt())
        }
    }

    @Test
    fun cacheReusesAndInvalidatesLods() {
        val world = GameWorld()
        val position = DHSectionPos(6, 0, 0)
        val cache = DHLodCache(4L * 1024 * 1024)
        val first = cache.get(world, position)
        assertSame(first, cache.get(world, position))

        world.setBlockSilent(BlockPos(1, 10, 1), Blocks.RedstoneBlock.defaultState)
        val changed = cache.get(world, position)
        assertNotSame(first, changed)
        assertTrue(!first.data.contentEquals(changed.data))
    }

    private fun decompressed(input: DataInputStream): DataInputStream {
        val compressed = ByteArray(input.readInt())
        input.readFully(compressed)
        return DataInputStream(XZInputStream(compressed.inputStream()))
    }
}
