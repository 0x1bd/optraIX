package org.kvxd.gogolmc.bench

import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.block.property.BlockDirection
import org.kvxd.gogolmc.block.property.LeverFace
import org.kvxd.gogolmc.redstone.mchprs.Wire
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.GameWorld
import org.kvxd.gogolmc.world.WorldGenerator

class BenchCircuit(
    val world: GameWorld,
    val levers: List<BlockPos>,
    val components: Int,
    val dust: Int,
    val repeaters: Int,
    val torches: Int,
    val comparators: Int,
) {

    companion object {

        private val stone = Blocks.require("minecraft:stone").defaultStateId

        fun busses(lanes: Int, segments: Int, seed: Long = 7L): BenchCircuit {
            val world = GameWorld(WorldGenerator(Blocks.airState, 0))
            val levers = ArrayList<BlockPos>(lanes)
            val dustPositions = ArrayList<BlockPos>(lanes * segments * 3)
            var repeaters = 0
            var torches = 0
            var comparators = 0
            val random = java.util.Random(seed)

            for (lane in 0 until lanes) {
                val z = lane * 3
                val length = segments * 4
                for (x in 0..length) world.setBlockSilent(BlockPos(x, 0, z), stone)

                val leverPos = BlockPos(0, 1, z)
                world.setBlockSilent(
                    leverPos,
                    BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false),
                )
                levers.add(leverPos)

                var x = 1
                while (x + 3 <= length) {
                    for (offset in 0 until 3) {
                        val pos = BlockPos(x + offset, 1, z)
                        world.setBlockSilent(pos, Wire.make(sideNone(), sideNone(), sideNone(), sideNone(), 0))
                        dustPositions.add(pos)
                    }
                    val gate = BlockPos(x + 3, 1, z)
                    when (random.nextInt(10)) {
                        0, 1 -> {
                            world.setBlockSilent(
                                gate,
                                BlockStates.comparatorState(
                                    BlockDirection.West,
                                    org.kvxd.gogolmc.block.property.ComparatorMode.Compare,
                                    false,
                                ),
                            )
                            comparators++
                        }
                        else -> {
                            world.setBlockSilent(
                                gate,
                                BlockStates.repeaterState(
                                    1 + random.nextInt(2),
                                    BlockDirection.West,
                                    locked = false,
                                    powered = false,
                                ),
                            )
                            repeaters++
                        }
                    }
                    x += 4
                }
            }

            repeat(2) {
                for (pos in dustPositions) {
                    world.setBlockSilent(pos, Wire.getRegulatedSides(world.getBlock(pos), world, pos))
                }
            }
            world.changedBlocks.clear()

            return BenchCircuit(
                world = world,
                levers = levers,
                components = dustPositions.size + repeaters + comparators + torches + levers.size,
                dust = dustPositions.size,
                repeaters = repeaters,
                torches = torches,
                comparators = comparators,
            )
        }

        private fun sideNone() = org.kvxd.gogolmc.block.property.WireSide.None
    }
}
