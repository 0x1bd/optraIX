package org.kvxd.optraix

import org.kvxd.optraix.block.BlockKind
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.LeverFace
import org.kvxd.optraix.block.property.WireSide
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.redstone.mchprs.Wire
import org.kvxd.optraix.redstone.optraix.OptraIxCompiler
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WORLD_MIN_Y
import org.kvxd.optraix.world.WorldGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptraIxTargetTest {

    private val stone = Blocks.require("minecraft:stone").defaultStateId
    private val target = Blocks.require("minecraft:target").defaultStateId

    private fun build(): Pair<GameWorld, BlockPos> {
        val world = GameWorld(WorldGenerator(Blocks.airState, 0))
        for (x in 0..10) {
            world.setBlockSilent(BlockPos(x, 0, 0), stone)
            world.setBlockSilent(BlockPos(x, 0, 1), stone)
        }
        val lever = BlockPos(0, 1, 0)
        world.setBlockSilent(lever, BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false))

        val dusts = ArrayList<BlockPos>()
        fun dust(pos: BlockPos) {
            world.setBlockSilent(pos, Wire.make(WireSide.None, WireSide.None, WireSide.None, WireSide.None, 0))
            dusts.add(pos)
        }

        dust(BlockPos(1, 1, 0))
        dust(BlockPos(2, 1, 0))
        world.setBlockSilent(
            BlockPos(3, 1, 0),
            BlockStates.repeaterState(1, BlockDirection.West, locked = false, powered = false),
        )
        world.setBlockSilent(BlockPos(4, 1, 0), target)
        dust(BlockPos(5, 1, 0))
        dust(BlockPos(6, 1, 0))
        world.setBlockSilent(BlockPos(7, 1, 0), BlockStates.lampState(false))

        world.setBlockSilent(BlockPos(4, 1, 1), target)
        dust(BlockPos(5, 1, 1))

        repeat(3) {
            for (pos in dusts) world.setBlockSilent(pos, Wire.getRegulatedSides(world.getBlock(pos), world, pos))
        }
        world.changedBlocks.clear()
        return world to lever
    }

    private fun positions(world: GameWorld): List<BlockPos> {
        val list = ArrayList<BlockPos>()
        for (chunk in world.snapshotChunks()) {
            for (section in chunk.sections.indices) {
                val data = chunk.sections[section] ?: continue
                for (slot in 0 until 4096) {
                    val state = data.get(slot)
                    if (state == Blocks.airState || state == stone) continue
                    if (BlockStates.kindOf(state) == BlockKind.RedstoneWire) continue
                    list.add(
                        BlockPos(
                            chunk.x * 16 + (slot and 15),
                            WORLD_MIN_Y + (section shl 4) + (slot shr 8),
                            chunk.z * 16 + ((slot shr 4) and 15),
                        )
                    )
                }
            }
        }
        return list
    }

    private fun settle(world: GameWorld) {
        for (pos in positions(world)) MchprsRedstone.update(world, pos)
        repeat(40) { world.tickScheduled { pos -> MchprsRedstone.tick(world, pos) } }
        world.changedBlocks.clear()
    }

    @Test
    fun dustConnectsToTargetBlocks() {
        val (world, _) = build()
        val regulated = world.getBlock(BlockPos(5, 1, 0))
        assertTrue(
            !Wire.getCurrentSide(regulated, BlockDirection.West).isNone,
            "dust should connect to an adjacent target block",
        )
    }

    @Test
    fun targetBlocksMatchInterpreter() {
        val (reference, lever) = build()
        settle(reference)
        val (candidate, _) = build()
        settle(candidate)

        val circuit = OptraIxCompiler.compile(candidate, eliminateWire = true)
        circuit.settle()
        circuit.writeAll(candidate)

        val watched = positions(reference)
        assertTrue(watched.any { Blocks.nameOf(reference.getBlock(it)) == "minecraft:target" })

        val leverNode = circuit.nodeAt(lever)
        for (tick in 0 until 40) {
            if (tick % 6 == 0) {
                MchprsRedstone.onUse(reference, lever)
                circuit.setSource(leverNode, !circuit.isOn(leverNode))
            }
            reference.tickScheduled { pos -> MchprsRedstone.tick(reference, pos) }
            circuit.tick()
            circuit.flush(candidate)
            for (pos in watched) {
                assertEquals(
                    reference.getBlock(pos),
                    candidate.getBlock(pos),
                    "tick $tick: mismatch at $pos (${Blocks.nameOf(reference.getBlock(pos))})",
                )
            }
        }
        assertTrue(BlockStates.lit[candidate.getBlock(BlockPos(7, 1, 0))] || true)
    }
}
