package org.kvxd.optraix

import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.block.property.ComparatorMode
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WorldStorage
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PersistenceTest {

    private fun tempFile(): File {
        val directory = Files.createTempDirectory("optraix").toFile()
        directory.deleteOnExit()
        return File(directory, "world.dat")
    }

    @Test
    fun worldSurvivesSaveAndLoad() {
        val file = tempFile()
        val original = GameWorld()

        val placed = HashMap<BlockPos, Int>()
        var seed = 987654321
        for (n in 0 until 500) {
            seed = seed * 1103515245 + 12345
            val pos = BlockPos(
                ((seed ushr 8) % 200) - 100,
                1 + ((seed ushr 20) and 63),
                ((seed ushr 14) % 200) - 100,
            )
            val state = 1 + (((seed ushr 4) and 0x3FF) % 900)
            original.setBlock(pos, state)
            placed[pos] = state
        }

        val comparatorPos = BlockPos(4, 1, 4)
        original.setBlock(
            comparatorPos,
            BlockStates.comparatorState(BlockDirection.West, ComparatorMode.Subtract, true),
        )
        original.setBlockEntity(comparatorPos, BlockEntity.Comparator(11))

        val signPos = BlockPos(6, 1, 6)
        original.setBlockEntity(
            signPos,
            BlockEntity.Sign(listOf("a", "b", "c", "d"), listOf("e", "f", "g", "h")),
        )

        val saved = WorldStorage.save(original, file)
        assertTrue(saved > 0)
        assertTrue(file.length() > 0)

        val restored = GameWorld()
        val count = WorldStorage.load(restored, file)
        assertEquals(saved, count)

        for ((pos, state) in placed) {
            assertEquals(state, restored.getBlock(pos), "block lost at $pos")
        }

        val sandstone = Blocks.require("minecraft:sandstone").defaultStateId
        assertEquals(sandstone, restored.getBlock(BlockPos(0, 0, 0)), "floor lost")

        val comparator = restored.getBlockEntity(comparatorPos)
        assertTrue(comparator is BlockEntity.Comparator)
        assertEquals(11, comparator.outputStrength)

        val sign = restored.getBlockEntity(signPos)
        assertTrue(sign is BlockEntity.Sign)
        assertEquals(listOf("a", "b", "c", "d"), sign.frontRows)
        assertEquals("h", sign.backRows[3])
    }

    @Test
    fun savedChunkKeepsDirectPalette() {
        val file = tempFile()
        val original = GameWorld()
        for (index in 0 until 600) {
            original.setBlock(BlockPos(index and 15, 20 + (index shr 8), (index shr 4) and 15), index + 1)
        }
        WorldStorage.save(original, file)

        val restored = GameWorld()
        WorldStorage.load(restored, file)
        for (index in 0 until 600) {
            val pos = BlockPos(index and 15, 20 + (index shr 8), (index shr 4) and 15)
            assertEquals(index + 1, restored.getBlock(pos))
        }
    }

    private fun emptyWorld() = GameWorld(org.kvxd.optraix.world.WorldGenerator(Blocks.airState, 0))

    private fun tick(world: GameWorld, times: Int) {
        repeat(times) {
            world.tickScheduled { pos -> org.kvxd.optraix.redstone.mchprs.MchprsRedstone.tick(world, pos) }
        }
    }

    @Test
    fun pendingTorchTickSurvivesReload() {
        val file = tempFile()
        val stone = Blocks.require("minecraft:stone").defaultStateId
        val interaction = org.kvxd.optraix.interaction.Interaction(
            org.kvxd.optraix.redstone.mchprs.MchprsRedstone
        )

        val world = emptyWorld()
        world.setBlock(BlockPos(1, 0, 0), stone)
        val torchPos = BlockPos(1, 1, 0)
        interaction.placeInWorld(BlockStates.torchState(true), world, torchPos, null)

        interaction.placeInWorld(
            BlockStates.leverState(
                org.kvxd.optraix.block.property.LeverFace.Wall, BlockDirection.East, false
            ),
            world, BlockPos(2, 0, 0), null,
        )
        org.kvxd.optraix.redstone.mchprs.MchprsRedstone.onUse(world, BlockPos(2, 0, 0))

        assertTrue(world.scheduledTicks > 0, "powering the base must schedule the torch to go out")
        assertTrue(BlockStates.lit[world.getBlock(torchPos)], "torch is still lit before the tick fires")

        WorldStorage.save(world, file)

        val reloaded = emptyWorld()
        WorldStorage.load(reloaded, file)
        assertEquals(world.scheduledTicks, reloaded.scheduledTicks, "pending ticks must survive the reload")
        assertTrue(BlockStates.lit[reloaded.getBlock(torchPos)])

        tick(reloaded, 1)
        assertTrue(
            !BlockStates.lit[reloaded.getBlock(torchPos)],
            "the restored tick must fire with no player input",
        )
    }

    @Test
    fun pendingRepeaterDelaySurvivesReload() {
        val file = tempFile()
        val stone = Blocks.require("minecraft:stone").defaultStateId
        val interaction = org.kvxd.optraix.interaction.Interaction(
            org.kvxd.optraix.redstone.mchprs.MchprsRedstone
        )

        val world = emptyWorld()
        for (x in 0..2) world.setBlock(BlockPos(x, 0, 0), stone)

        val repeaterPos = BlockPos(1, 1, 0)
        interaction.placeInWorld(
            BlockStates.repeaterState(4, BlockDirection.West, locked = false, powered = false),
            world, repeaterPos, null,
        )
        interaction.placeInWorld(
            BlockStates.leverState(
                org.kvxd.optraix.block.property.LeverFace.Floor, BlockDirection.North, false
            ),
            world, BlockPos(0, 1, 0), null,
        )
        org.kvxd.optraix.redstone.mchprs.MchprsRedstone.onUse(world, BlockPos(0, 1, 0))

        tick(world, 2)
        assertTrue(!BlockStates.powered[world.getBlock(repeaterPos)], "still inside the 4 tick delay")
        assertTrue(world.scheduledTicks > 0)

        WorldStorage.save(world, file)
        val reloaded = emptyWorld()
        WorldStorage.load(reloaded, file)

        tick(reloaded, 2)
        assertTrue(
            BlockStates.powered[reloaded.getBlock(repeaterPos)],
            "the repeater must finish its delay after the reload",
        )
    }

    @Test
    fun loadingMissingFileIsNoop() {
        val world = GameWorld()
        assertEquals(0, WorldStorage.load(world, File("/nonexistent/optraix.world")))
    }

    @Test
    fun editsAfterLoadStillWork() {
        val file = tempFile()
        val original = GameWorld()
        original.setBlock(BlockPos(1, 5, 1), Blocks.require("minecraft:stone").defaultStateId)
        WorldStorage.save(original, file)

        val restored = GameWorld()
        WorldStorage.load(restored, file)

        val glass = Blocks.require("minecraft:glass").defaultStateId
        assertTrue(restored.setBlock(BlockPos(1, 5, 1), glass))
        assertEquals(glass, restored.getBlock(BlockPos(1, 5, 1)))

        val wire = BlockStates.wireState(
            org.kvxd.optraix.block.property.WireSide.Side,
            org.kvxd.optraix.block.property.WireSide.Side,
            org.kvxd.optraix.block.property.WireSide.Side,
            org.kvxd.optraix.block.property.WireSide.Side,
            9,
        )
        assertTrue(restored.setBlock(BlockPos(2, 5, 1), wire))
        assertEquals(wire, restored.getBlock(BlockPos(2, 5, 1)))
    }
}
