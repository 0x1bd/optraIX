package org.kvxd.gogolmc

import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.block.property.BlockDirection
import org.kvxd.gogolmc.block.property.LeverFace
import org.kvxd.gogolmc.block.property.WireSide
import org.kvxd.gogolmc.redstone.mchprs.MchprsRedstone
import org.kvxd.gogolmc.redstone.mchprs.Wire
import org.kvxd.gogolmc.redstone.opt3x.Opt3xEngine
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.GameWorld
import org.kvxd.gogolmc.world.WorldGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class Opt3xEngineTest {

    private val stone = Blocks.require("minecraft:stone").defaultStateId

    private fun lampWorld(): Pair<GameWorld, BlockPos> {
        val world = GameWorld(WorldGenerator(Blocks.airState, 0))
        for (x in 0..6) world.setBlockSilent(BlockPos(x, 0, 0), stone)
        val lever = BlockPos(0, 1, 0)
        world.setBlockSilent(lever, BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false))
        val dusts = (1..4).map { BlockPos(it, 1, 0) }
        for (pos in dusts) {
            world.setBlockSilent(pos, Wire.make(WireSide.None, WireSide.None, WireSide.None, WireSide.None, 0))
        }
        world.setBlockSilent(BlockPos(5, 1, 0), BlockStates.repeaterState(1, BlockDirection.West, false, false))
        world.setBlockSilent(BlockPos(6, 1, 0), BlockStates.lampState(false))
        repeat(2) {
            for (pos in dusts) world.setBlockSilent(pos, Wire.getRegulatedSides(world.getBlock(pos), world, pos))
        }
        world.changedBlocks.clear()
        return world to lever
    }

    @Test
    fun compiledEngineDrivesTheCircuit() {
        val (world, lever) = lampWorld()
        val engine = Opt3xEngine()

        assertTrue(engine.compile(world), "compile should succeed: ${engine.lastError}")
        assertTrue(engine.compiled)
        assertEquals(3, engine.circuit?.count, "dust collapses into weighted edges")

        val lamp = BlockPos(6, 1, 0)
        assertFalse(BlockStates.lit[world.getBlock(lamp)])

        assertTrue(engine.onUse(world, lever))
        repeat(6) { engine.tickWorld(world) }

        assertTrue(BlockStates.lit[world.getBlock(lamp)], "lamp should light through the compiled circuit")

        assertTrue(engine.onUse(world, lever))
        repeat(6) { engine.tickWorld(world) }
        assertFalse(BlockStates.lit[world.getBlock(lamp)], "lamp should go dark again")
    }

    @Test
    fun decompileHandsBackToTheInterpreter() {
        val (world, lever) = lampWorld()
        val engine = Opt3xEngine()
        assertTrue(engine.compile(world))
        assertTrue(engine.onUse(world, lever))
        repeat(2) { engine.tickWorld(world) }

        engine.decompile(world)
        assertFalse(engine.compiled)

        repeat(6) { world.tickScheduled { pos -> MchprsRedstone.tick(world, pos) } }
        assertTrue(
            BlockStates.lit[world.getBlock(BlockPos(6, 1, 0))],
            "the interpreter should finish what the compiled circuit started",
        )
    }

    @Test
    fun structuralChangeDecompilesAutomatically() {
        val (world, _) = lampWorld()
        val engine = Opt3xEngine()
        assertTrue(engine.compile(world))
        assertTrue(engine.compiled)

        engine.updateSurroundingBlocks(world, BlockPos(3, 1, 0))
        assertFalse(engine.compiled, "a structural update must fall back to the interpreter")
    }

    @Test
    fun compiledEngineMatchesInterpreterOverTime() {
        val (reference, referenceLever) = lampWorld()
        val (candidate, candidateLever) = lampWorld()
        val engine = Opt3xEngine()
        assertTrue(engine.compile(candidate))

        val watched = listOf(BlockPos(0, 1, 0), BlockPos(5, 1, 0), BlockPos(6, 1, 0))
        for (tick in 0 until 40) {
            if (tick % 7 == 0) {
                MchprsRedstone.onUse(reference, referenceLever)
                engine.onUse(candidate, candidateLever)
            }
            reference.tickScheduled { pos -> MchprsRedstone.tick(reference, pos) }
            engine.tickWorld(candidate)
            for (pos in watched) {
                assertEquals(
                    reference.getBlock(pos),
                    candidate.getBlock(pos),
                    "tick $tick at $pos",
                )
            }
        }
    }
}
