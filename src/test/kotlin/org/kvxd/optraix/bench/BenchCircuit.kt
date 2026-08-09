package org.kvxd.optraix.bench

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.redstone.mchprs.Wire
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WorldGenerator
import org.kvxd.optraix.mcdata.v1_20_4.Blocks

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

        private val stone = Blocks.Stone.defaultState

        fun busses(lanes: Int, segments: Int, seed: Long = 7L): BenchCircuit {
            val world = GameWorld(WorldGenerator(Blocks.Air.defaultState, 0))
            val levers = ArrayList<BlockPos>(lanes)
            val dustPositions = ArrayList<BlockPos>(lanes * segments * 4)
            var repeaters = 0
            var torches = 0
            var comparators = 0
            val random = java.util.Random(seed)
            val length = segments * 4

            fun dust(pos: BlockPos) {
                world.setBlockSilent(pos, Wire.make(sideNone(), sideNone(), sideNone(), sideNone(), 0))
                dustPositions.add(pos)
            }

            for (lane in 0 until lanes) {
                val z = lane * 3
                for (x in 0..length) {
                    world.setBlockSilent(BlockPos(x, 0, z), stone)
                    world.setBlockSilent(BlockPos(x, 0, z + 1), stone)
                    world.setBlockSilent(BlockPos(x, 0, z + 2), stone)
                }

                val leverPos = BlockPos(0, 1, z)
                world.setBlockSilent(
                    leverPos,
                    BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false),
                )
                levers.add(leverPos)

                var x = 1
                while (x + 3 <= length) {
                    val roll = random.nextInt(12)
                    val dustRun = if (roll in 4..6) 2 else 3
                    for (offset in 0 until dustRun) dust(BlockPos(x + offset, 1, z))
                    val gate = BlockPos(x + 3, 1, z)
                    when (roll) {
                        0, 1, 2, 3 -> {
                            world.setBlockSilent(
                                gate,
                                BlockStates.comparatorState(
                                    BlockDirection.West,
                                    if (random.nextBoolean()) ComparatorMode.Compare else ComparatorMode.Subtract,
                                    false,
                                ),
                            )
                            comparators++
                        }
                        4, 5, 6 -> {
                            world.setBlockSilent(BlockPos(x + 2, 1, z), stone)
                            world.setBlockSilent(gate, BlockStates.wallTorchState(true, BlockDirection.East))
                            torches++
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

            var mergeColumn = 3
            while (mergeColumn + 3 <= length) {
                var first = 0
                while (first + 1 < lanes) {
                    val span = 4 + random.nextInt(4)
                    if (random.nextInt(100) < 16) {
                        for (k in 0 until minOf(span, lanes - 1 - first)) {
                            val z = (first + k) * 3
                            dust(BlockPos(mergeColumn, 1, z + 1))
                            dust(BlockPos(mergeColumn, 1, z + 2))
                        }
                    }
                    first += span
                }
                mergeColumn += 4
            }

            repeat(3) {
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

        private fun sideNone() = org.kvxd.optraix.block.property.WireSide.None
    }
}
