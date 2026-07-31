package org.kvxd.gogolmc

import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.Blocks
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

class Opt3xPlateTest {

    private val stone = Blocks.require("minecraft:stone").defaultStateId

    private fun world(): Pair<GameWorld, BlockPos> {
        val world = GameWorld(WorldGenerator(Blocks.airState, 0))
        for (x in 0..6) world.setBlockSilent(BlockPos(x, 0, 0), stone)
        val plate = BlockPos(0, 1, 0)
        world.setBlockSilent(plate, Blocks.require("minecraft:stone_pressure_plate").defaultStateId)
        val dusts = (1..3).map { BlockPos(it, 1, 0) }
        for (pos in dusts) {
            world.setBlockSilent(pos, Wire.make(WireSide.None, WireSide.None, WireSide.None, WireSide.None, 0))
        }
        world.setBlockSilent(BlockPos(4, 1, 0), BlockStates.lampState(false))
        repeat(2) {
            for (pos in dusts) world.setBlockSilent(pos, Wire.getRegulatedSides(world.getBlock(pos), world, pos))
        }
        world.changedBlocks.clear()
        return world to plate
    }

    @Test
    fun compiledPlateDrivesTheCircuit() {
        val (world, plate) = world()
        val engine = Opt3xEngine()
        assertTrue(engine.compile(world), "compile should succeed: ${engine.lastError}")

        val lamp = BlockPos(4, 1, 0)
        assertFalse(BlockStates.lit[world.getBlock(lamp)])

        engine.setPressurePlate(world, plate, true)
        repeat(4) { engine.tickWorld(world) }
        assertTrue(BlockStates.pressurePlatePowered(world.getBlock(plate))!!, "plate should be powered")
        assertTrue(BlockStates.lit[world.getBlock(lamp)], "lamp should light from the plate")
        assertTrue(engine.compiled, "stepping on a plate must not decompile the circuit")

        engine.setPressurePlate(world, plate, false)
        repeat(4) { engine.tickWorld(world) }
        assertFalse(BlockStates.pressurePlatePowered(world.getBlock(plate))!!)
        assertFalse(BlockStates.lit[world.getBlock(lamp)], "lamp should go dark when the plate releases")
    }

    @Test
    fun plateMatchesInterpreter() {
        val (reference, plate) = world()
        val (candidate, _) = world()
        val engine = Opt3xEngine()
        assertTrue(engine.compile(candidate))

        val watched = listOf(plate, BlockPos(4, 1, 0))
        for (round in 0 until 3) {
            MchprsRedstone.setPressurePlate(reference, plate, true)
            engine.setPressurePlate(candidate, plate, true)
            repeat(6) {
                reference.tickScheduled { pos -> MchprsRedstone.tick(reference, pos) }
                engine.tickWorld(candidate)
            }
            for (pos in watched) {
                assertEquals(reference.getBlock(pos), candidate.getBlock(pos), "round $round pressed at $pos")
            }

            MchprsRedstone.setPressurePlate(reference, plate, false)
            engine.setPressurePlate(candidate, plate, false)
            repeat(6) {
                reference.tickScheduled { pos -> MchprsRedstone.tick(reference, pos) }
                engine.tickWorld(candidate)
            }
            for (pos in watched) {
                assertEquals(reference.getBlock(pos), candidate.getBlock(pos), "round $round released at $pos")
            }
        }
    }

    @Test
    fun harmlessRightClicksDoNotDecompile() {
        val (world, _) = world()
        val engine = Opt3xEngine()
        assertTrue(engine.compile(world))

        engine.onUse(world, BlockPos(0, 0, 0))
        assertTrue(engine.compiled, "clicking stone must not decompile")

        engine.onUse(world, BlockPos(4, 1, 0))
        assertTrue(engine.compiled, "clicking a lamp must not decompile")

        engine.onUse(world, BlockPos(1, 1, 0))
        assertFalse(engine.compiled, "clicking dust changes redstone and must decompile")
    }
}
