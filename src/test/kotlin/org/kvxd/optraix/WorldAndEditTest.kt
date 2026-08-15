package org.kvxd.optraix

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.property.FlipDirection
import org.kvxd.optraix.block.property.RotateAmount
import org.kvxd.optraix.redstone.mchprs.Wire
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.Chunk
import org.kvxd.optraix.world.ChunkSection
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.SECTION_COUNT
import org.kvxd.optraix.world.WORLD_HEIGHT
import org.kvxd.optraix.world.WORLD_MIN_Y
import org.kvxd.optraix.net.ChunkPackets
import org.kvxd.optraix.worldedit.schematic.Schematic
import org.kvxd.kmcprotocol.extensions.chunk.ChunkFormat
import org.kvxd.kmcprotocol.extensions.chunk.ChunkSections
import java.io.File
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.block.mcData
import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.WireSide

class WorldAndEditTest {

    @Test
    fun sectionStoresSingleValueWithoutData() {
        val section = ChunkSection()
        assertEquals(0, section.bitsPerEntry)
        assertEquals(Blocks.Air.defaultState, section.get(0))
        assertEquals(0, section.data.size)
    }

    @Test
    fun sectionGrowsPaletteAndReadsBack() {
        val section = ChunkSection()
        val states = (1..40).map { it }
        for ((index, state) in states.withIndex()) section.set(index, state)
        for ((index, state) in states.withIndex()) assertEquals(state, section.get(index))
        assertTrue(section.bitsPerEntry in 6..8, "40 entries should fit an indirect palette")
        assertEquals(40, section.blockCount)
    }

    @Test
    fun sectionFallsBackToDirectPalette() {
        val section = ChunkSection()
        for (index in 0 until 600) section.set(index, index + 1)
        assertTrue(section.isDirect, "over 256 palette entries must switch to the global palette")
        for (index in 0 until 600) assertEquals(index + 1, section.get(index))
        assertEquals(ChunkSection.longArraySize(section.bitsPerEntry), section.data.size)
    }

    @Test
    fun sectionTracksBlockCount() {
        val section = ChunkSection()
        val stone = Blocks.Stone.defaultState
        section.set(0, stone)
        section.set(1, stone)
        assertEquals(2, section.blockCount)
        section.set(1, Blocks.Air.defaultState)
        assertEquals(1, section.blockCount)
    }

    @Test
    fun worldGeneratesFlatSandstoneFloor() {
        val world = GameWorld()
        val sandstone = Blocks.Sandstone.defaultState
        for (pos in listOf(BlockPos(0, 0, 0), BlockPos(-500, 0, 1200), BlockPos(9999, 0, -9999))) {
            assertEquals(sandstone, world.getBlock(pos), "floor missing at $pos")
            assertEquals(Blocks.Air.defaultState, world.getBlock(BlockPos(pos.x, 1, pos.z)))
            assertEquals(Blocks.Air.defaultState, world.getBlock(BlockPos(pos.x, 200, pos.z)))
        }
    }

    @Test
    fun worldIsInfiniteInBothDirections() {
        val world = GameWorld()
        val stone = Blocks.Stone.defaultState
        val far = BlockPos(-1_000_000, 64, 1_000_000)
        world.setBlock(far, stone)
        assertEquals(stone, world.getBlock(far))
    }

    @Test
    fun blockPosPacksAndUnpacksNegatives() {
        for (pos in listOf(
            BlockPos(0, 0, 0),
            BlockPos(-1, 255, -1),
            BlockPos(-1, WORLD_MIN_Y, -1),
            BlockPos(1, WORLD_MIN_Y + WORLD_HEIGHT - 1, -1),
            BlockPos(-33_554_432, 0, 33_554_431),
            BlockPos(1234, 200, -5678),
        )) {
            assertEquals(pos, BlockPos.unpack(pos.asLong()), "round trip failed for $pos")
        }
    }

    @Test
    fun chunkSectionIndexesMatchProtocolOrder() {
        assertEquals(0, Chunk.index(0, 0, 0))
        assertEquals(1, Chunk.index(1, 0, 0))
        assertEquals(16, Chunk.index(0, 0, 1))
        assertEquals(256, Chunk.index(0, 1, 0))
        assertEquals(SECTION_COUNT, 254)
    }

    @Test
    fun worldStoresBlocksAtBothEndsOfItsHeight() {
        val world = GameWorld()
        val stone = Blocks.Stone.defaultState
        val bottom = BlockPos(7, WORLD_MIN_Y, -4)
        val top = BlockPos(7, WORLD_MIN_Y + WORLD_HEIGHT - 1, -4)

        assertTrue(world.setBlock(bottom, stone))
        assertTrue(world.setBlock(top, stone))
        assertEquals(stone, world.getBlock(bottom))
        assertEquals(stone, world.getBlock(top))
        assertEquals(Blocks.Air.defaultState, world.getBlock(top.offset(org.kvxd.optraix.block.property.BlockFace.Top)))
    }

    @Test
    fun clipboardRotationIsIdentityAfterFourTurns() {
        val world = GameWorld()
        val clipboard = org.kvxd.optraix.worldedit.clipboard.Clipboard(
            3, 1, 2, BlockPos(0, 0, 0), IntArray(6) { Blocks.Air.defaultState }
        )
        clipboard[0, 0, 0] = BlockStates.repeaterState(2, BlockDirection.North, false, false)
        clipboard[1, 0, 0] = Blocks.Stone.defaultState
        clipboard[2, 0, 1] = Wire.make(WireSide.Side, WireSide.None, WireSide.Up, WireSide.None, 5)

        var rotated = clipboard
        repeat(4) { rotated = rotated.rotate(RotateAmount.Rotate90) }

        assertEquals(clipboard.sizeX, rotated.sizeX)
        assertEquals(clipboard.sizeZ, rotated.sizeZ)
        for (index in clipboard.blocks.indices) {
            assertEquals(clipboard.blocks[index], rotated.blocks[index], "block $index changed after 360 degrees")
        }
        assertEquals(world.loadedChunks, 0)
    }

    @Test
    fun clipboardRotationTurnsRepeaterFacing() {
        val clipboard = org.kvxd.optraix.worldedit.clipboard.Clipboard(
            1, 1, 1, BlockPos(0, 0, 0), IntArray(1) { Blocks.Air.defaultState }
        )
        clipboard[0, 0, 0] = BlockStates.repeaterState(2, BlockDirection.North, false, false)
        val rotated = clipboard.rotate(RotateAmount.Rotate90)
        assertEquals(BlockDirection.East, BlockStates.directionOf(rotated[0, 0, 0]))
        assertEquals(2, BlockStates.delay[rotated[0, 0, 0]].toInt(), "delay must survive rotation")
    }

    @Test
    fun clipboardRotationTurnsWireSides() {
        val clipboard = org.kvxd.optraix.worldedit.clipboard.Clipboard(
            1, 1, 1, BlockPos(0, 0, 0), IntArray(1) { Blocks.Air.defaultState }
        )
        clipboard[0, 0, 0] = Wire.make(WireSide.Side, WireSide.None, WireSide.Up, WireSide.None, 5)
        val rotated = clipboard.rotate(RotateAmount.Rotate90)[0, 0, 0]
        assertEquals(WireSide.None, Wire.north(rotated))
        assertEquals(WireSide.Side, Wire.east(rotated))
        assertEquals(WireSide.Up, Wire.south(rotated))
        assertEquals(5, Wire.power(rotated))
    }

    @Test
    fun clipboardFlipIsSelfInverse() {
        val clipboard = org.kvxd.optraix.worldedit.clipboard.Clipboard(
            2, 1, 3, BlockPos(0, 0, 0), IntArray(6) { Blocks.Air.defaultState }
        )
        clipboard[0, 0, 0] = BlockStates.repeaterState(1, BlockDirection.East, false, false)
        clipboard[1, 0, 2] = Blocks.Stone.defaultState

        val twice = clipboard.flip(FlipDirection.FlipX).flip(FlipDirection.FlipX)
        for (index in clipboard.blocks.indices) assertEquals(clipboard.blocks[index], twice.blocks[index])

        val once = clipboard.flip(FlipDirection.FlipX)
        assertEquals(BlockDirection.West, BlockStates.directionOf(once[1, 0, 0]))
    }

    @Test
    fun loadsSpongeV2Schematic() {
        val file = File(javaClass.getResource("/test_v2.schem")!!.toURI())
        val clipboard = Schematic.load(file)

        assertEquals(3, clipboard.sizeX)
        assertEquals(2, clipboard.sizeY)
        assertEquals(2, clipboard.sizeZ)
        assertEquals(BlockPos(-1, 0, -1), clipboard.offset)

        assertEquals(Blocks.Sandstone.defaultState, clipboard[0, 0, 0])

        val wire = clipboard[1, 0, 0]
        assertEquals(7, Wire.power(wire))
        assertEquals(WireSide.Side, Wire.north(wire))
        assertEquals(WireSide.Side, Wire.east(wire))

        val repeater = clipboard[2, 0, 0]
        assertEquals(3, BlockStates.delay[repeater].toInt())
        assertEquals(BlockDirection.East, BlockStates.directionOf(repeater))

        assertEquals(Blocks.Air.defaultState, clipboard[1, 1, 1])
        assertEquals(Blocks.Sandstone.defaultState, clipboard[0, 1, 1])
    }

    @Test
    fun chunkEncodesToWireFormatAndBack() {
        val world = GameWorld()
        val chunk = world.chunkAt(3, -7)
        val sandstone = Blocks.Sandstone.defaultState
        val expected = HashMap<Triple<Int, Int, Int>, Int>()

        var seed = 12345
        for (n in 0 until 400) {
            seed = seed * 1103515245 + 12345
            val x = ((seed ushr 16) and 15)
            val y = WORLD_MIN_Y + 1 + ((seed ushr 8) and 63)
            val z = (seed and 15)
            val state = 1 + (((seed ushr 20) and 0x3FF) % 900)
            chunk.setBlock(x, y, z, state)
            expected[Triple(x, y, z)] = state
        }

        val packet = ChunkPackets.encode(chunk)
        assertEquals(3, packet.x)
        assertEquals(-7, packet.z)
        assertTrue(packet.skyLight.isEmpty())

        val sections = ChunkSections.decode(packet.chunkData, ChunkFormat.v1_18(SECTION_COUNT))
        assertEquals(SECTION_COUNT, sections.size)

        for ((key, state) in expected) {
            val (x, y, z) = key
            val decoded = sections[(y - WORLD_MIN_Y) shr 4].blockStateAt(x, y and 15, z)
            assertEquals(state, decoded, "mismatch at $x,$y,$z")
        }

        for (x in 0 until 16) {
            for (z in 0 until 16) {
                if (expected.containsKey(Triple(x, 0, z))) continue
                assertEquals(
                    sandstone,
                    sections[-WORLD_MIN_Y shr 4].blockStateAt(x, 0, z),
                    "floor lost at $x,0,$z",
                )
            }
        }
    }

    @Test
    fun chunkOnlyIncludesSkyLightForSectionsWithBlocks() {
        val world = GameWorld()
        val chunk = world.chunkAt(0, 0)
        val clearedY = WORLD_MIN_Y + 16
        chunk.setBlock(0, clearedY, 0, Blocks.Stone.defaultState)
        chunk.setBlock(0, clearedY, 0, Blocks.Air.defaultState)

        val packet = ChunkPackets.encode(chunk, includeSkyLight = true)

        assertContentEquals(ChunkPackets.sectionData(GameWorld().chunkAt(0, 0)), packet.chunkData)
        assertEquals(1, packet.skyLight.size)
        assertEquals(2048, packet.skyLight.single().size)
        assertTrue(packet.skyLight.single().all { it == 0xFF.toShort() })
        assertEquals(1, packet.skyLightMask.sumOf { java.lang.Long.bitCount(it) })
        assertEquals(SECTION_COUNT + 1, packet.emptySkyLightMask.sumOf { java.lang.Long.bitCount(it) })
        assertTrue(packet.skyLightMask.zip(packet.emptySkyLightMask).all { (present, empty) -> present and empty == 0L })

        val floorLightSection = ((0 - WORLD_MIN_Y) shr 4) + 1
        assertTrue(packet.skyLightMask[floorLightSection / 64] and (1L shl (floorLightSection % 64)) != 0L)
    }

    @Test
    fun chunkWithDirectPaletteSurvivesEncoding() {
        val world = GameWorld()
        val chunk = world.chunkAt(0, 0)
        for (index in 0 until 600) {
            val y = WORLD_MIN_Y + 16 + (index shr 8)
            chunk.setBlock(index and 15, y, (index shr 4) and 15, index + 1)
        }
        val sections = ChunkSections.decode(
            ChunkPackets.encode(chunk).chunkData, ChunkFormat.v1_18(SECTION_COUNT)
        )
        for (index in 0 until 600) {
            val y = WORLD_MIN_Y + 16 + (index shr 8)
            assertEquals(
                index + 1,
                sections[(y - WORLD_MIN_Y) shr 4].blockStateAt(index and 15, y and 15, (index shr 4) and 15),
            )
        }
    }

    @Test
    fun parsesBlockStatesFromText() {
        assertEquals(
            Blocks.Sandstone.defaultState,
            mcData.blockState("sandstone"),
        )
        val repeater = mcData.blockState("minecraft:repeater[delay=4,facing=west,powered=true]")!!
        assertEquals(4, BlockStates.delay[repeater].toInt())
        assertEquals(BlockDirection.West, BlockStates.directionOf(repeater))
        assertTrue(BlockStates.powered[repeater])
        assertEquals(null, mcData.blockState("minecraft:not_a_block"))
    }

    @Test
    fun describeRoundTripsThroughParse() {
        val original = BlockStates.comparatorState(
            BlockDirection.South, org.kvxd.optraix.block.property.ComparatorMode.Subtract, true
        )
        assertEquals(original, mcData.blockState(mcData.describeState(original)))
    }
}
