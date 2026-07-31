package org.kvxd.optraix

import org.kvxd.optraix.block.BlockKind
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.LeverFace
import org.kvxd.optraix.block.property.WireSide
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.redstone.mchprs.Wire
import org.kvxd.optraix.redstone.optraix.OptraIxEngine
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WorldGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OptraIxButtonTest {

    private val stone = Blocks.require("minecraft:stone").defaultStateId

    private fun world(buttonName: String): Pair<GameWorld, BlockPos> {
        val world = GameWorld(WorldGenerator(Blocks.airState, 0))
        for (x in 0..6) world.setBlockSilent(BlockPos(x, 0, 0), stone)
        val button = BlockPos(0, 1, 0)
        world.setBlockSilent(
            button,
            BlockStates.buttonStateFor(
                Blocks.require(buttonName), LeverFace.Floor, BlockDirection.North, false,
            ),
        )
        val dusts = (1..3).map { BlockPos(it, 1, 0) }
        for (pos in dusts) {
            world.setBlockSilent(pos, Wire.make(WireSide.None, WireSide.None, WireSide.None, WireSide.None, 0))
        }
        world.setBlockSilent(BlockPos(4, 1, 0), BlockStates.lampState(false))
        repeat(2) {
            for (pos in dusts) world.setBlockSilent(pos, Wire.getRegulatedSides(world.getBlock(pos), world, pos))
        }
        world.changedBlocks.clear()
        return world to button
    }

    @Test
    fun woodenButtonsAreRedstoneComponents() {
        for (name in listOf("minecraft:oak_button", "minecraft:warped_button", "minecraft:polished_blackstone_button")) {
            val state = Blocks.require(name).defaultStateId
            assertEquals(BlockKind.Button, BlockStates.kindOf(state), "$name should be a button")
        }
        assertEquals(10, BlockStates.buttonDuration(Blocks.require("minecraft:stone_button").defaultStateId))
        assertEquals(10, BlockStates.buttonDuration(Blocks.require("minecraft:polished_blackstone_button").defaultStateId))
        assertEquals(15, BlockStates.buttonDuration(Blocks.require("minecraft:oak_button").defaultStateId))
    }

    @Test
    fun woodenButtonDrivesTheCircuit() {
        val (world, button) = world("minecraft:oak_button")
        val engine = OptraIxEngine()
        assertTrue(engine.compile(world), "compile should succeed: ${engine.lastError}")

        val lamp = BlockPos(4, 1, 0)
        assertFalse(BlockStates.lit[world.getBlock(lamp)])

        assertTrue(engine.onUse(world, button), "button press should be handled")
        engine.tickWorld(world)
        assertTrue(BlockStates.powered[world.getBlock(button)], "button should be pressed")
        assertEquals("minecraft:oak_button", Blocks.nameOf(world.getBlock(button)), "block type must be preserved")
        assertTrue(BlockStates.lit[world.getBlock(lamp)], "lamp should light from a wooden button")

        repeat(13) { engine.tickWorld(world) }
        assertTrue(BlockStates.powered[world.getBlock(button)], "wooden button holds through tick 14")
        engine.tickWorld(world)
        assertFalse(BlockStates.powered[world.getBlock(button)], "wooden button releases on tick 15")
    }

    @Test
    fun buttonsMatchInterpreter() {
        for (name in listOf("minecraft:oak_button", "minecraft:stone_button")) {
            val (reference, button) = world(name)
            val (candidate, _) = world(name)
            val engine = OptraIxEngine()
            assertTrue(engine.compile(candidate))

            MchprsRedstone.onUse(reference, button)
            engine.onUse(candidate, button)

            val watched = listOf(button, BlockPos(4, 1, 0))
            for (tick in 0 until 40) {
                reference.tickScheduled { pos -> MchprsRedstone.tick(reference, pos) }
                engine.tickWorld(candidate)
                for (pos in watched) {
                    assertEquals(
                        reference.getBlock(pos),
                        candidate.getBlock(pos),
                        "$name tick $tick mismatch at $pos",
                    )
                }
            }
        }
    }
}
