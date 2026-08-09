package org.kvxd.optraix

import org.kvxd.optraix.block.BlockKind
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.LeverFace
import org.kvxd.optraix.block.property.WireSide
import org.kvxd.optraix.interaction.Interaction
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
import org.kvxd.optraix.mcdata.v1_20_4.Blocks

class OptraIxEngineTest {

    private val stone = Blocks.Stone.defaultState

    private fun lampWorld(): Pair<GameWorld, BlockPos> {
        val world = GameWorld(WorldGenerator(Blocks.Air.defaultState, 0))
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
        val engine = OptraIxEngine()

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
        val engine = OptraIxEngine()
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
    fun replacingCompiledRepeaterWithDustDoesNotRestoreRepeater() {
        val (world, _) = lampWorld()
        val engine = OptraIxEngine()
        val interaction = Interaction(engine)
        val pos = BlockPos(5, 1, 0)

        assertTrue(engine.compile(world))
        assertEquals(BlockKind.Repeater, BlockStates.kindOf(world.getBlock(pos)))

        interaction.destroy(world.getBlock(pos), world, pos)
        assertEquals(Blocks.Air.defaultState, world.getBlock(pos))
        assertFalse(engine.compiled)

        interaction.placeInWorld(Wire.makeCross(0), world, pos, null)
        repeat(4) { engine.tickWorld(world) }

        assertEquals(BlockKind.RedstoneWire, BlockStates.kindOf(world.getBlock(pos)))
    }

    @Test
    fun propagationDoesNotImplicitlyInvalidateCompiledCircuit() {
        val (world, _) = lampWorld()
        val engine = OptraIxEngine()
        assertTrue(engine.compile(world))

        engine.updateSurroundingBlocks(world, BlockPos(3, 1, 0))

        assertTrue(engine.compiled)
    }

    @Test
    fun worldMutationDecompilesBeforeTheFirstEdit() {
        val (world, _) = lampWorld()
        val engine = OptraIxEngine()
        val pos = BlockPos(5, 1, 0)
        assertTrue(engine.compile(world))

        engine.mutate(world) {
            assertFalse(engine.compiled)
            setBlock(pos, Wire.makeCross(0))
        }

        assertEquals(BlockKind.RedstoneWire, BlockStates.kindOf(world.getBlock(pos)))
    }

    @Test
    fun worldMutationInvalidatesOnceForABatch() {
        val (world, _) = lampWorld()
        val engine = OptraIxEngine()
        assertTrue(engine.compile(world))
        val before = engine.mutationCounter

        engine.mutate(world) {
            setBlock(BlockPos(1, 1, 0), Blocks.Air.defaultState)
            setBlock(BlockPos(2, 1, 0), Blocks.Air.defaultState)
        }

        assertEquals(before + 1, engine.mutationCounter)
        assertFalse(engine.compiled)
    }

    @Test
    fun compiledEngineMatchesInterpreterOverTime() {
        val (reference, referenceLever) = lampWorld()
        val (candidate, candidateLever) = lampWorld()
        val engine = OptraIxEngine()
        assertTrue(engine.compile(candidate))

        val io = listOf(BlockPos(0, 1, 0), BlockPos(6, 1, 0))
        for (tick in 0 until 40) {
            if (tick % 7 == 0) {
                MchprsRedstone.onUse(reference, referenceLever)
                engine.onUse(candidate, candidateLever)
            }
            reference.tickScheduled { pos -> MchprsRedstone.tick(reference, pos) }
            engine.tickWorld(candidate)
            for (pos in io) {
                assertEquals(
                    reference.getBlock(pos),
                    candidate.getBlock(pos),
                    "tick $tick at $pos",
                )
            }
        }

        engine.decompile(candidate)
        for (pos in listOf(BlockPos(0, 1, 0), BlockPos(5, 1, 0), BlockPos(6, 1, 0))) {
            assertEquals(
                reference.getBlock(pos),
                candidate.getBlock(pos),
                "after decompile at $pos",
            )
        }
    }

    @Test
    fun savingWhileCompiledKeepsPendingTicks() {
        val (world, lever) = lampWorld()
        val engine = OptraIxEngine()
        assertTrue(engine.compile(world), "compile should succeed: ${engine.lastError}")

        assertTrue(engine.onUse(world, lever))
        assertTrue(engine.circuit!!.pendingTicks > 0, "toggling the lever should leave work in flight")
        assertEquals(0, world.snapshotTicks().size, "a compiled circuit holds its ticks internally")

        engine.decompile(world)
        assertTrue(world.snapshotTicks().isNotEmpty(), "decompile must hand pending ticks back to the world")

        val file = java.io.File.createTempFile("optraix-save", ".dat")
        file.deleteOnExit()
        org.kvxd.optraix.world.WorldStorage.save(world, file)

        val restored = GameWorld(WorldGenerator(Blocks.Air.defaultState, 0))
        org.kvxd.optraix.world.WorldStorage.load(restored, file)
        assertEquals(
            world.snapshotTicks().size,
            restored.snapshotTicks().size,
            "saved world must carry the in-flight ticks",
        )

        assertTrue(engine.compile(restored), "recompile after save should succeed")
        repeat(6) { engine.tickWorld(restored) }

        val lamp = BlockPos(6, 1, 0)
        assertTrue(BlockStates.lit[restored.getBlock(lamp)], "lamp should be lit after the round trip")
    }

    @Test
    fun pauseDecompilesAndBlocksUntilResume() {
        val (world, lever) = lampWorld()
        val engine = OptraIxEngine()

        assertTrue(engine.compile(world))
        assertTrue(engine.compiled)
        assertFalse(engine.paused)

        engine.pause(world)
        assertTrue(engine.paused)
        assertFalse(engine.compiled)

        MchprsRedstone.onUse(world, lever)
        repeat(6) { engine.tickWorld(world) }
        val lamp = BlockPos(6, 1, 0)
        assertTrue(BlockStates.lit[world.getBlock(lamp)], "paused engine must run interpreted")

        assertTrue(engine.compile(world), "explicit compile resumes")
        assertFalse(engine.paused)
        assertTrue(engine.compiled)
    }

    private fun chainWorld(): Triple<GameWorld, BlockPos, BlockPos> {
        val world = GameWorld(WorldGenerator(Blocks.Air.defaultState, 0))
        for (x in 0..6) world.setBlockSilent(BlockPos(x, 0, 0), stone)
        val lever = BlockPos(0, 1, 0)
        world.setBlockSilent(lever, BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false))
        for (x in 1..4) {
            world.setBlockSilent(BlockPos(x, 1, 0), BlockStates.repeaterState(1, BlockDirection.West, false, false))
        }
        val dust = BlockPos(5, 1, 0)
        world.setBlockSilent(dust, Wire.make(WireSide.None, WireSide.None, WireSide.None, WireSide.None, 0))
        world.setBlockSilent(BlockPos(6, 1, 0), BlockStates.lampState(false))
        world.setBlockSilent(dust, Wire.getRegulatedSides(world.getBlock(dust), world, dust))
        world.changedBlocks.clear()
        return Triple(world, lever, BlockPos(6, 1, 0))
    }

    @Test
    fun ioOnlyFlushFreezesFusedInteriorUntilDecompile() {
        val (world, lever, lamp) = chainWorld()
        val engine = OptraIxEngine()

        assertTrue(engine.compile(world), "compile should succeed: ${engine.lastError}")
        val circuit = engine.circuit!!
        assertTrue(circuit.fusedLinks >= 3, "repeater run should fuse, got ${circuit.fusedLinks}")

        assertTrue(engine.onUse(world, lever))
        repeat(10) { engine.tickWorld(world) }

        assertTrue(BlockStates.lit[world.getBlock(lamp)], "lamp is IO and must update")
        for (x in 1..4) {
            assertFalse(
                BlockStates.powered[world.getBlock(BlockPos(x, 1, 0))],
                "fused repeater at x=$x stays frozen while compiled",
            )
        }

        engine.decompile(world)
        for (x in 1..4) {
            assertTrue(
                BlockStates.powered[world.getBlock(BlockPos(x, 1, 0))],
                "decompile must materialise the true state at x=$x",
            )
        }
    }

    @Test
    fun decompileMaterialisesEliminatedWirePower() {
        val (world, lever) = lampWorld()
        val engine = OptraIxEngine()

        assertTrue(engine.compile(world), "compile should succeed: ${engine.lastError}")
        assertTrue(engine.onUse(world, lever))
        repeat(6) { engine.tickWorld(world) }

        assertEquals(
            0,
            BlockStates.wirePower[world.getBlock(BlockPos(1, 1, 0))].toInt(),
            "wire state stays intentionally frozen while compiled",
        )

        engine.decompile(world)

        assertEquals(15, BlockStates.wirePower[world.getBlock(BlockPos(1, 1, 0))].toInt())
        assertEquals(14, BlockStates.wirePower[world.getBlock(BlockPos(2, 1, 0))].toInt())
        assertEquals(13, BlockStates.wirePower[world.getBlock(BlockPos(3, 1, 0))].toInt())
        assertEquals(12, BlockStates.wirePower[world.getBlock(BlockPos(4, 1, 0))].toInt())
    }
}
