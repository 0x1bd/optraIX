package org.kvxd.gogolmc

import org.kvxd.gogolmc.block.property.BlockDirection
import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.block.property.FlipDirection
import org.kvxd.gogolmc.block.property.RotateAmount
import org.kvxd.gogolmc.block.property.WireSide
import org.kvxd.gogolmc.redstone.mchprs.Wire
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.Chunk
import org.kvxd.gogolmc.world.ChunkSection
import org.kvxd.gogolmc.world.GameWorld
import org.kvxd.gogolmc.world.SECTION_COUNT
import org.kvxd.gogolmc.net.ChunkPackets
import org.kvxd.gogolmc.worldedit.Schematic
import org.kvxd.kmcprotocol.extensions.chunk.ChunkFormat
import org.kvxd.kmcprotocol.extensions.chunk.ChunkSections
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldAndEditTest {

    @Test
    fun sectionStoresSingleValueWithoutData() {
        val section = ChunkSection()
        assertEquals(0, section.bitsPerEntry)
        assertEquals(Blocks.airState, section.get(0))
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
        val stone = Blocks.require("minecraft:stone").defaultStateId
        section.set(0, stone)
        section.set(1, stone)
        assertEquals(2, section.blockCount)
        section.set(1, Blocks.airState)
        assertEquals(1, section.blockCount)
    }

    @Test
    fun worldGeneratesFlatSandstoneFloor() {
        val world = GameWorld()
        val sandstone = Blocks.require("minecraft:sandstone").defaultStateId
        for (pos in listOf(BlockPos(0, 0, 0), BlockPos(-500, 0, 1200), BlockPos(9999, 0, -9999))) {
            assertEquals(sandstone, world.getBlock(pos), "floor missing at $pos")
            assertEquals(Blocks.airState, world.getBlock(BlockPos(pos.x, 1, pos.z)))
            assertEquals(Blocks.airState, world.getBlock(BlockPos(pos.x, 200, pos.z)))
        }
    }

    @Test
    fun worldIsInfiniteInBothDirections() {
        val world = GameWorld()
        val stone = Blocks.require("minecraft:stone").defaultStateId
        val far = BlockPos(-1_000_000, 64, 1_000_000)
        world.setBlock(far, stone)
        assertEquals(stone, world.getBlock(far))
    }

    @Test
    fun blockPosPacksAndUnpacksNegatives() {
        for (pos in listOf(
            BlockPos(0, 0, 0),
            BlockPos(-1, 255, -1),
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
        assertEquals(SECTION_COUNT, 16)
    }

    @Test
    fun clipboardRotationIsIdentityAfterFourTurns() {
        val world = GameWorld()
        val clipboard = org.kvxd.gogolmc.worldedit.Clipboard(
            3, 1, 2, BlockPos(0, 0, 0), IntArray(6) { Blocks.airState }
        )
        clipboard[0, 0, 0] = BlockStates.repeaterState(2, BlockDirection.North, false, false)
        clipboard[1, 0, 0] = Blocks.require("minecraft:stone").defaultStateId
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
        val clipboard = org.kvxd.gogolmc.worldedit.Clipboard(
            1, 1, 1, BlockPos(0, 0, 0), IntArray(1) { Blocks.airState }
        )
        clipboard[0, 0, 0] = BlockStates.repeaterState(2, BlockDirection.North, false, false)
        val rotated = clipboard.rotate(RotateAmount.Rotate90)
        assertEquals(BlockDirection.East, BlockStates.directionOf(rotated[0, 0, 0]))
        assertEquals(2, BlockStates.delay[rotated[0, 0, 0]].toInt(), "delay must survive rotation")
    }

    @Test
    fun clipboardRotationTurnsWireSides() {
        val clipboard = org.kvxd.gogolmc.worldedit.Clipboard(
            1, 1, 1, BlockPos(0, 0, 0), IntArray(1) { Blocks.airState }
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
        val clipboard = org.kvxd.gogolmc.worldedit.Clipboard(
            2, 1, 3, BlockPos(0, 0, 0), IntArray(6) { Blocks.airState }
        )
        clipboard[0, 0, 0] = BlockStates.repeaterState(1, BlockDirection.East, false, false)
        clipboard[1, 0, 2] = Blocks.require("minecraft:stone").defaultStateId

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

        assertEquals(Blocks.require("minecraft:sandstone").defaultStateId, clipboard[0, 0, 0])

        val wire = clipboard[1, 0, 0]
        assertEquals(7, Wire.power(wire))
        assertEquals(WireSide.Side, Wire.north(wire))
        assertEquals(WireSide.Side, Wire.east(wire))

        val repeater = clipboard[2, 0, 0]
        assertEquals(3, BlockStates.delay[repeater].toInt())
        assertEquals(BlockDirection.East, BlockStates.directionOf(repeater))

        assertEquals(Blocks.airState, clipboard[1, 1, 1])
        assertEquals(Blocks.require("minecraft:sandstone").defaultStateId, clipboard[0, 1, 1])
    }

    @Test
    fun chunkEncodesToWireFormatAndBack() {
        val world = GameWorld()
        val chunk = world.chunkAt(3, -7)
        val sandstone = Blocks.require("minecraft:sandstone").defaultStateId
        val expected = HashMap<Triple<Int, Int, Int>, Int>()

        var seed = 12345
        for (n in 0 until 400) {
            seed = seed * 1103515245 + 12345
            val x = ((seed ushr 16) and 15)
            val y = 1 + ((seed ushr 8) and 63)
            val z = (seed and 15)
            val state = 1 + (((seed ushr 20) and 0x3FF) % 900)
            chunk.setBlock(x, y, z, state)
            expected[Triple(x, y, z)] = state
        }

        val packet = ChunkPackets.encode(chunk)
        assertEquals(3, packet.x)
        assertEquals(-7, packet.z)

        val sections = ChunkSections.decode(packet.chunkData, ChunkFormat.v1_18(SECTION_COUNT))
        assertEquals(SECTION_COUNT, sections.size)

        for ((key, state) in expected) {
            val (x, y, z) = key
            val decoded = sections[y shr 4].blockStateAt(x, y and 15, z)
            assertEquals(state, decoded, "mismatch at $x,$y,$z")
        }

        for (x in 0 until 16) {
            for (z in 0 until 16) {
                if (expected.containsKey(Triple(x, 0, z))) continue
                assertEquals(sandstone, sections[0].blockStateAt(x, 0, z), "floor lost at $x,0,$z")
            }
        }
    }

    @Test
    fun chunkWithDirectPaletteSurvivesEncoding() {
        val world = GameWorld()
        val chunk = world.chunkAt(0, 0)
        for (index in 0 until 600) {
            val y = 16 + (index shr 8)
            chunk.setBlock(index and 15, y, (index shr 4) and 15, index + 1)
        }
        val sections = ChunkSections.decode(
            ChunkPackets.encode(chunk).chunkData, ChunkFormat.v1_18(SECTION_COUNT)
        )
        for (index in 0 until 600) {
            val y = 16 + (index shr 8)
            assertEquals(
                index + 1,
                sections[y shr 4].blockStateAt(index and 15, y and 15, (index shr 4) and 15),
            )
        }
    }

    @Test
    fun parsesBlockStatesFromText() {
        assertEquals(
            Blocks.require("minecraft:sandstone").defaultStateId,
            Blocks.parse("sandstone"),
        )
        val repeater = Blocks.parse("minecraft:repeater[delay=4,facing=west,powered=true]")!!
        assertEquals(4, BlockStates.delay[repeater].toInt())
        assertEquals(BlockDirection.West, BlockStates.directionOf(repeater))
        assertTrue(BlockStates.powered[repeater])
        assertEquals(null, Blocks.parse("minecraft:not_a_block"))
    }

    @Test
    fun describeRoundTripsThroughParse() {
        val original = BlockStates.comparatorState(
            BlockDirection.South, org.kvxd.gogolmc.block.property.ComparatorMode.Subtract, true
        )
        assertEquals(original, Blocks.parse(Blocks.describe(original)))
    }
}
