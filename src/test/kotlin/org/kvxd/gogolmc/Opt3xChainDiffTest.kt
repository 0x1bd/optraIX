package org.kvxd.gogolmc

import org.kvxd.gogolmc.block.BlockKind
import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.block.property.BlockDirection
import org.kvxd.gogolmc.block.property.ComparatorMode
import org.kvxd.gogolmc.block.property.LeverFace
import org.kvxd.gogolmc.block.property.WireSide
import org.kvxd.gogolmc.redstone.mchprs.MchprsRedstone
import org.kvxd.gogolmc.redstone.mchprs.Wire
import org.kvxd.gogolmc.redstone.opt3x.Opt3xCompiler
import org.kvxd.gogolmc.world.BlockEntity
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.GameWorld
import org.kvxd.gogolmc.world.WorldGenerator
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

class Opt3xChainDiffTest {

    private val stone = Blocks.require("minecraft:stone").defaultStateId

    private class Built(val world: GameWorld, val levers: List<BlockPos>)

    private fun build(seed: Long, lanes: Int, segments: Int): Built {
        val world = GameWorld(WorldGenerator(Blocks.airState, 0))
        val random = Random(seed)
        val levers = ArrayList<BlockPos>()
        val dusts = ArrayList<BlockPos>()

        fun dust(pos: BlockPos) {
            world.setBlockSilent(pos, Wire.make(WireSide.None, WireSide.None, WireSide.None, WireSide.None, 0))
            dusts.add(pos)
        }

        val length = segments * 4 + 2
        for (lane in 0 until lanes) {
            val z = lane * 3
            for (x in 0..length) {
                world.setBlockSilent(BlockPos(x, 0, z), stone)
                world.setBlockSilent(BlockPos(x, 0, z + 1), stone)
            }

            val lever = BlockPos(0, 1, z)
            world.setBlockSilent(lever, BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false))
            levers.add(lever)

            var x = 1
            while (x + 3 <= length) {
                for (offset in 0 until 3) dust(BlockPos(x + offset, 1, z))
                val gate = BlockPos(x + 3, 1, z)
                when (random.nextInt(12)) {
                    0, 1 -> world.setBlockSilent(
                        gate,
                        BlockStates.comparatorState(BlockDirection.West, ComparatorMode.Compare, false),
                    )
                    2 -> world.setBlockSilent(
                        gate,
                        BlockStates.comparatorState(BlockDirection.West, ComparatorMode.Subtract, false),
                    )
                    else -> world.setBlockSilent(
                        gate,
                        BlockStates.repeaterState(
                            1 + random.nextInt(4),
                            BlockDirection.West,
                            locked = false,
                            powered = false,
                        ),
                    )
                }
                x += 4
            }

            if (lane > 0 && random.nextBoolean()) {
                val tap = 1 + random.nextInt(maxOf(1, segments - 1)) * 4
                dust(BlockPos(tap, 1, z - 1))
            }
        }

        repeat(3) {
            for (pos in dusts) world.setBlockSilent(pos, Wire.getRegulatedSides(world.getBlock(pos), world, pos))
        }
        world.changedBlocks.clear()
        return Built(world, levers)
    }

    private fun settle(world: GameWorld) {
        val positions = redstonePositions(world)
        for (pos in positions) MchprsRedstone.update(world, pos)
        repeat(80) { world.tickScheduled { pos -> MchprsRedstone.tick(world, pos) } }
        world.changedBlocks.clear()
        world.changedBlockEntities.clear()
    }

    private fun redstonePositions(world: GameWorld): List<BlockPos> {
        val positions = ArrayList<BlockPos>()
        for (chunk in world.snapshotChunks()) {
            for (sectionIndex in chunk.sections.indices) {
                val section = chunk.sections[sectionIndex] ?: continue
                for (slot in 0 until 4096) {
                    val state = section.get(slot)
                    if (state == Blocks.airState || state == stone) continue
                    if (BlockStates.kindOf(state) == BlockKind.RedstoneWire) continue
                    positions.add(
                        BlockPos(
                            chunk.x * 16 + (slot and 15),
                            (sectionIndex shl 4) + (slot shr 8),
                            chunk.z * 16 + ((slot shr 4) and 15),
                        )
                    )
                }
            }
        }
        return positions
    }

    private fun run(seed: Long, lanes: Int, segments: Int, ticks: Int, togglePeriod: Int, fuse: Boolean = false): Int {
        val reference = build(seed, lanes, segments)
        settle(reference.world)
        val candidate = build(seed, lanes, segments)
        settle(candidate.world)

        val circuit = Opt3xCompiler.compile(candidate.world, eliminateWire = true, fuseChains = fuse)
        circuit.settle()
        circuit.writeAll(candidate.world)

        val watched = redstonePositions(reference.world)

        for (pos in watched) {
            assertEquals(
                reference.world.getBlock(pos),
                candidate.world.getBlock(pos),
                "seed $seed: initial state differs at $pos",
            )
        }

        val leverNodes = reference.levers.map { circuit.nodeAt(it) }
        val random = Random(seed * 7919 + 13)

        for (tick in 0 until ticks) {
            if (tick % togglePeriod == 0) {
                val pick = random.nextInt(leverNodes.size)
                MchprsRedstone.onUse(reference.world, reference.levers[pick])
                circuit.setSource(leverNodes[pick], !circuit.isOn(leverNodes[pick]))
            }
            reference.world.tickScheduled { pos -> MchprsRedstone.tick(reference.world, pos) }
            circuit.tick()
            circuit.flush(candidate.world)
            reference.world.changedBlocks.clear()
            candidate.world.changedBlocks.clear()

            for (pos in watched) {
                assertEquals(
                    reference.world.getBlock(pos),
                    candidate.world.getBlock(pos),
                    "seed $seed tick $tick: block mismatch at $pos",
                )
                val expected = reference.world.getBlockEntity(pos) as? BlockEntity.Comparator ?: continue
                val actual = candidate.world.getBlockEntity(pos) as? BlockEntity.Comparator
                assertEquals(
                    expected.outputStrength,
                    actual?.outputStrength,
                    "seed $seed tick $tick: comparator strength at $pos",
                )
            }
        }
        return circuit.fusedLinks
    }

    @Test
    fun fusedChainsMatchInterpreter() {
        for (seed in 1L..45L) run(seed, lanes = 4, segments = 9, ticks = 90, togglePeriod = 8)
    }

    @Test
    fun longChainsMatchInterpreter() {
        for (seed in 40L..70L) run(seed, lanes = 3, segments = 20, ticks = 120, togglePeriod = 11)
    }

    @Test
    fun rapidTogglesMatchInterpreter() {
        for (seed in 70L..120L) run(seed, lanes = 4, segments = 12, ticks = 140, togglePeriod = 2)
    }

    @Test
    fun chainFusionStillDivergesFromInterpreter() {
        val failure = assertFails {
            run(83L, lanes = 4, segments = 12, ticks = 140, togglePeriod = 2, fuse = true)
        }
        assertTrue(
            failure.message.orEmpty().contains("mismatch"),
            "expected fusion to diverge, got: ${failure.message}",
        )
    }

    @Test
    fun variedTogglePeriodsMatchInterpreter() {
        for (seed in 200L..260L) {
            val period = 1 + (seed % 7).toInt()
            run(seed, lanes = 3, segments = 11, ticks = 110, togglePeriod = period)
        }
    }
}
