package org.kvxd.optraix

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.redstone.mchprs.Wire
import org.kvxd.optraix.redstone.optraix.OptraIxCompiler
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WORLD_MIN_Y
import org.kvxd.optraix.world.WorldGenerator
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kvxd.optraix.mcdata.v1_20_4.Blocks

class OptraIxChainDiffTest {

    private val stone = Blocks.Stone.defaultState

    private class Built(val world: GameWorld, val levers: List<BlockPos>)

    private fun build(seed: Long, lanes: Int, segments: Int): Built {
        val world = GameWorld(WorldGenerator(Blocks.Air.defaultState, 0))
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
        val positions = ArrayList<BlockPos>()
        for (chunk in world.snapshotChunks()) {
            for (sectionIndex in chunk.sections.indices) {
                val section = chunk.sections[sectionIndex] ?: continue
                for (slot in 0 until 4096) {
                    val state = section.get(slot)
                    if (state == Blocks.Air.defaultState || state == stone) continue
                    positions.add(
                        BlockPos(
                            chunk.x * 16 + (slot and 15),
                            WORLD_MIN_Y + (sectionIndex shl 4) + (slot shr 8),
                            chunk.z * 16 + ((slot shr 4) and 15),
                        )
                    )
                }
            }
        }
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
                    if (state == Blocks.Air.defaultState || state == stone) continue
                    if (BlockStates.isType(state, Blocks.RedstoneWire)) continue
                    positions.add(
                        BlockPos(
                            chunk.x * 16 + (slot and 15),
                            WORLD_MIN_Y + (sectionIndex shl 4) + (slot shr 8),
                            chunk.z * 16 + ((slot shr 4) and 15),
                        )
                    )
                }
            }
        }
        return positions
    }

    private fun run(seed: Long, lanes: Int, segments: Int, ticks: Int, togglePeriod: Int): Int {
        val reference = build(seed, lanes, segments)
        settle(reference.world)
        val candidate = build(seed, lanes, segments)
        settle(candidate.world)

        val circuit = OptraIxCompiler.compile(candidate.world, eliminateWire = true)
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
    fun diodeRunsMatchInterpreter() {
        var links = 0
        for (seed in 1L..45L) links += run(seed, lanes = 4, segments = 9, ticks = 90, togglePeriod = 8)
        assertTrue(links > 100, "chain fusion produced only $links links across the sweep")
    }

    @Test
    fun longChainsMatchInterpreter() {
        var links = 0
        for (seed in 40L..70L) links += run(seed, lanes = 3, segments = 20, ticks = 120, togglePeriod = 11)
        assertTrue(links > 100, "chain fusion produced only $links links across the sweep")
    }

    @Test
    fun rapidTogglesMatchInterpreter() {
        var links = 0
        for (seed in 70L..120L) links += run(seed, lanes = 4, segments = 12, ticks = 140, togglePeriod = 2)
        assertTrue(links > 100, "chain fusion produced only $links links across the sweep")
    }


    @Test
    fun variedTogglePeriodsMatchInterpreter() {
        var links = 0
        for (seed in 200L..260L) {
            val period = 1 + (seed % 7).toInt()
            links += run(seed, lanes = 3, segments = 11, ticks = 110, togglePeriod = period)
        }
        assertTrue(links > 100, "chain fusion produced only $links links across the sweep")
    }

    @Test
    fun singleTickPulsesMatchInterpreter() {
        var links = 0
        for (seed in 300L..360L) links += run(seed, lanes = 3, segments = 14, ticks = 130, togglePeriod = 1)
        assertTrue(links > 100, "chain fusion produced only $links links across the sweep")
    }

    @Test
    fun torchChainsMatchInterpreter() {
        var links = 0
        for (seed in 500L..560L) {
            links += runTorchy(seed, ticks = 130, togglePeriod = 1 + (seed % 9).toInt())
        }
        assertTrue(links > 100, "chain fusion produced only $links links across the sweep")
    }

    private fun runTorchy(seed: Long, ticks: Int, togglePeriod: Int): Int {
        val reference = buildTorchy(seed)
        settle(reference.world)
        val candidate = buildTorchy(seed)
        settle(candidate.world)

        val circuit = OptraIxCompiler.compile(candidate.world, eliminateWire = true)
        circuit.settle()
        circuit.writeAll(candidate.world)

        val watched = redstonePositions(reference.world)
        for (pos in watched) {
            assertEquals(
                reference.world.getBlock(pos),
                candidate.world.getBlock(pos),
                "torchy seed $seed: initial state differs at $pos",
            )
        }
        val leverNodes = reference.levers.map { circuit.nodeAt(it) }
        val random = Random(seed * 131 + 5)

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
                    "torchy seed $seed tick $tick: block mismatch at $pos",
                )
            }
        }
        return circuit.fusedLinks
    }

    private fun buildTorchy(seed: Long): Built {
        val world = GameWorld(WorldGenerator(Blocks.Air.defaultState, 0))
        val random = Random(seed)
        val levers = ArrayList<BlockPos>()
        val dusts = ArrayList<BlockPos>()

        fun dust(pos: BlockPos) {
            world.setBlockSilent(pos, Wire.make(WireSide.None, WireSide.None, WireSide.None, WireSide.None, 0))
            dusts.add(pos)
        }

        for (lane in 0 until 3) {
            val z = lane * 3
            val segments = 8 + random.nextInt(6)
            val length = segments * 4 + 2
            for (x in 0..length) world.setBlockSilent(BlockPos(x, 0, z), stone)
            val lever = BlockPos(0, 1, z)
            world.setBlockSilent(lever, BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false))
            levers.add(lever)

            var x = 1
            while (x + 3 <= length) {
                val roll = random.nextInt(12)
                val dustRun = if (roll in 4..6) 2 else 3
                for (offset in 0 until dustRun) dust(BlockPos(x + offset, 1, z))
                val gate = BlockPos(x + 3, 1, z)
                when (roll) {
                    0, 1 -> world.setBlockSilent(
                        gate,
                        BlockStates.comparatorState(BlockDirection.West, ComparatorMode.Compare, false),
                    )
                    2, 3 -> world.setBlockSilent(
                        gate,
                        BlockStates.comparatorState(BlockDirection.West, ComparatorMode.Subtract, false),
                    )
                    4, 5, 6 -> {
                        world.setBlockSilent(BlockPos(x + 2, 1, z), stone)
                        world.setBlockSilent(gate, BlockStates.wallTorchState(true, BlockDirection.East))
                    }
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
        }

        repeat(3) {
            for (pos in dusts) world.setBlockSilent(pos, Wire.getRegulatedSides(world.getBlock(pos), world, pos))
        }
        world.changedBlocks.clear()
        return Built(world, levers)
    }

    @Test
    fun adjacentDiodesMatchInterpreter() {
        for (seed in 400L..430L) runAdjacent(seed, ticks = 160, togglePeriod = 1 + (seed % 9).toInt())
    }

    private fun runAdjacent(seed: Long, ticks: Int, togglePeriod: Int) {
        val reference = buildAdjacent(seed)
        settle(reference.world)
        val candidate = buildAdjacent(seed)
        settle(candidate.world)

        val circuit = OptraIxCompiler.compile(candidate.world, eliminateWire = true)
        circuit.settle()
        circuit.writeAll(candidate.world)

        val watched = redstonePositions(reference.world)
        val leverNodes = reference.levers.map { circuit.nodeAt(it) }
        val random = Random(seed * 31 + 7)

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
                    "adjacent seed $seed tick $tick: block mismatch at $pos",
                )
            }
        }
    }

    private fun buildAdjacent(seed: Long): Built {
        val world = GameWorld(WorldGenerator(Blocks.Air.defaultState, 0))
        val random = Random(seed)
        val levers = ArrayList<BlockPos>()
        for (lane in 0 until 3) {
            val z = lane * 3
            val length = 14 + random.nextInt(10)
            for (x in 0..length) world.setBlockSilent(BlockPos(x, 0, z), stone)
            val lever = BlockPos(0, 1, z)
            world.setBlockSilent(lever, BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false))
            levers.add(lever)
            for (x in 1..length) {
                if (random.nextInt(8) == 0) {
                    world.setBlockSilent(
                        BlockPos(x, 1, z),
                        BlockStates.comparatorState(BlockDirection.West, ComparatorMode.Compare, false),
                    )
                } else {
                    world.setBlockSilent(
                        BlockPos(x, 1, z),
                        BlockStates.repeaterState(
                            1 + random.nextInt(4),
                            BlockDirection.West,
                            locked = false,
                            powered = false,
                        ),
                    )
                }
            }
        }
        world.changedBlocks.clear()
        return Built(world, levers)
    }
}
