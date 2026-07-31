package org.kvxd.gogolmc

import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.block.property.BlockDirection
import org.kvxd.gogolmc.block.property.ComparatorMode
import org.kvxd.gogolmc.block.property.LeverFace
import org.kvxd.gogolmc.block.property.WireSide
import org.kvxd.gogolmc.redstone.mchprs.MchprsRedstone
import org.kvxd.gogolmc.redstone.mchprs.Wire
import org.kvxd.gogolmc.redstone.opt3x.Opt3xCircuit
import org.kvxd.gogolmc.redstone.opt3x.Opt3xCompiler
import org.kvxd.gogolmc.world.BlockEntity
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.GameWorld
import org.kvxd.gogolmc.world.WorldGenerator
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class Opt3xFuzzTest {

    private val stone = Blocks.require("minecraft:stone").defaultStateId
    private val redstoneBlock = Blocks.require("minecraft:redstone_block").defaultStateId

    private class Built(val world: GameWorld, val levers: List<BlockPos>)

    private fun build(seed: Long, rows: Int, length: Int): Built {
        val world = GameWorld(WorldGenerator(Blocks.airState, 0))
        val random = Random(seed)
        val levers = ArrayList<BlockPos>()
        val dusts = ArrayList<BlockPos>()

        for (x in 0..length + 1) {
            for (z in 0..rows * 2) world.setBlockSilent(BlockPos(x, 0, z), stone)
        }

        fun placeDust(pos: BlockPos) {
            world.setBlockSilent(
                pos,
                Wire.make(WireSide.None, WireSide.None, WireSide.None, WireSide.None, 0),
            )
            dusts.add(pos)
        }

        for (row in 0 until rows) {
            val z = row * 2
            when {
                random.nextInt(100) < 60 -> {
                    val pos = BlockPos(0, 1, z)
                    world.setBlockSilent(
                        pos,
                        BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false),
                    )
                    levers.add(pos)
                }
                random.nextInt(100) < 50 -> world.setBlockSilent(BlockPos(0, 1, z), redstoneBlock)
            }

            var x = 1
            while (x <= length) {
                when (random.nextInt(100)) {
                    in 0 until 50 -> {
                        placeDust(BlockPos(x, 1, z))
                        x += 1
                    }
                    in 50 until 68 -> {
                        world.setBlockSilent(
                            BlockPos(x, 1, z),
                            BlockStates.repeaterState(
                                1 + random.nextInt(4), BlockDirection.West, locked = false, powered = false,
                            ),
                        )
                        x += 1
                    }
                    in 68 until 80 -> {
                        world.setBlockSilent(
                            BlockPos(x, 1, z),
                            BlockStates.comparatorState(
                                BlockDirection.West,
                                if (random.nextBoolean()) ComparatorMode.Compare else ComparatorMode.Subtract,
                                false,
                            ),
                        )
                        x += 1
                    }
                    else -> {
                        if (x + 1 <= length) {
                            world.setBlockSilent(BlockPos(x, 1, z), stone)
                            world.setBlockSilent(
                                BlockPos(x + 1, 1, z),
                                BlockStates.wallTorchState(true, BlockDirection.East),
                            )
                            x += 2
                        } else {
                            placeDust(BlockPos(x, 1, z))
                            x += 1
                        }
                    }
                }
            }

            if (random.nextBoolean()) {
                world.setBlockSilent(BlockPos(length + 1, 1, z), BlockStates.lampState(false))
            }
        }

        for (row in 0 until rows - 1) {
            val z = row * 2 + 1
            for (x in 1..length) {
                if (random.nextInt(100) < 18) placeDust(BlockPos(x, 1, z))
            }
        }

        repeat(3) {
            for (pos in dusts) {
                world.setBlockSilent(pos, Wire.getRegulatedSides(world.getBlock(pos), world, pos))
            }
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
                    if (section.get(slot) == Blocks.airState) continue
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
        for (pos in positions) MchprsRedstone.update(world, pos)
        repeat(60) { world.tickScheduled { pos -> MchprsRedstone.tick(world, pos) } }
        world.changedBlocks.clear()
        world.changedBlockEntities.clear()
    }

    private fun fuzz(seed: Long, rows: Int, length: Int, ticks: Int): Opt3xCircuit {
        val reference = build(seed, rows, length)
        settle(reference.world)

        val candidate = build(seed, rows, length)
        settle(candidate.world)

        val circuit = Opt3xCompiler.compile(candidate.world, eliminateWire = false)
        circuit.settle()
        circuit.flush(candidate.world)

        val watched = circuit.posKey.map { BlockPos.unpack(it) }
        for (pos in watched) {
            assertEquals(
                reference.world.getBlock(pos),
                candidate.world.getBlock(pos),
                "seed $seed: compiled graph disagrees with the settled world at $pos",
            )
        }

        val leverNodes = reference.levers.map { circuit.nodeAt(it) }
        val random = Random(seed * 31 + 5)
        var referenceChanges = 0L

        for (tick in 0 until ticks) {
            if (leverNodes.isNotEmpty() && random.nextInt(3) == 0) {
                val pick = random.nextInt(leverNodes.size)
                MchprsRedstone.onUse(reference.world, reference.levers[pick])
                circuit.setSource(leverNodes[pick], !circuit.isOn(leverNodes[pick]))
            }
            reference.world.tickScheduled { pos -> MchprsRedstone.tick(reference.world, pos) }
            circuit.tick()
            circuit.flush(candidate.world)

            referenceChanges += reference.world.changedBlocks.size
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

        assertTrue(
            referenceChanges > ticks.toLong(),
            "seed $seed: circuit was inert ($referenceChanges block changes over $ticks ticks)",
        )
        assertTrue(
            circuit.nodeTicks > 0,
            "seed $seed: no scheduled node ever fired, diode timing was never exercised",
        )
        return circuit
    }

    @Test
    fun randomCircuitsMatch() {
        var nodes = 0
        var edges = 0
        for (seed in 1L..10L) {
            val circuit = fuzz(seed, rows = 6, length = 24, ticks = 80)
            nodes += circuit.count
            edges += circuit.edgeCount
        }
        assertTrue(nodes > 1000, "generated circuits were too small: $nodes nodes")
        assertTrue(edges > nodes, "generated circuits were poorly connected: $edges edges / $nodes nodes")
    }

    @Test
    fun largerRandomCircuitsMatch() {
        for (seed in 100L..104L) fuzz(seed, rows = 10, length = 40, ticks = 120)
    }
}
