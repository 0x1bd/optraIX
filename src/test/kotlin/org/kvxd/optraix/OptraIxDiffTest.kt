package org.kvxd.optraix

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.redstone.mchprs.Wire
import org.kvxd.optraix.redstone.optraix.NodeType
import org.kvxd.optraix.redstone.optraix.OptraIxCompiler
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WORLD_MIN_Y
import org.kvxd.optraix.world.WorldGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.block.mcData

class OptraIxDiffTest {

    private val stone = Blocks.Stone.defaultState
    private val redstoneBlock = Blocks.RedstoneBlock.defaultState

    private fun emptyWorld(): GameWorld = GameWorld(WorldGenerator(Blocks.Air.defaultState, 0))

    private fun dust(world: GameWorld, pos: BlockPos) {
        world.setBlockSilent(
            pos,
            Wire.make(WireSide.None, WireSide.None, WireSide.None, WireSide.None, 0),
        )
    }

    private fun regulate(world: GameWorld, positions: List<BlockPos>) {
        repeat(2) {
            for (pos in positions) {
                world.setBlockSilent(pos, Wire.getRegulatedSides(world.getBlock(pos), world, pos))
            }
        }
        world.changedBlocks.clear()
    }

    private fun settle(world: GameWorld) {
        val positions = ArrayList<BlockPos>()
        for (chunk in world.snapshotChunks()) {
            for (sectionIndex in chunk.sections.indices) {
                val section = chunk.sections[sectionIndex] ?: continue
                for (slot in 0 until 4096) {
                    if (section.get(slot) == Blocks.Air.defaultState) continue
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
        repeat(30) { world.tickScheduled { pos -> MchprsRedstone.tick(world, pos) } }
        world.changedBlocks.clear()
        world.changedBlockEntities.clear()
    }

    private fun compare(label: String, build: (GameWorld) -> List<BlockPos>, ticks: Int, toggleEvery: Int) {
        val reference = emptyWorld()
        val levers = build(reference)
        settle(reference)

        val candidate = emptyWorld()
        build(candidate)
        settle(candidate)

        val circuit = OptraIxCompiler.compile(candidate, eliminateWire = false)
        val leverNodes = levers.map { pos ->
            val node = circuit.nodeAt(pos)
            assertTrue(node >= 0, "$label: lever at $pos was not compiled")
            node
        }

        val watched = circuit.posKey.map { BlockPos.unpack(it) }

        circuit.settle()
        circuit.flush(candidate)
        for (pos in watched) {
            assertEquals(
                reference.getBlock(pos),
                candidate.getBlock(pos),
                "$label: compiled graph disagrees with the settled world at $pos",
            )
        }

        for (tick in 0 until ticks) {
            if (toggleEvery > 0 && tick % toggleEvery == 0) {
                for (pos in levers) MchprsRedstone.onUse(reference, pos)
                for (node in leverNodes) circuit.setSource(node, !circuit.isOn(node))
            }
            reference.tickScheduled { pos -> MchprsRedstone.tick(reference, pos) }
            circuit.tick()
            circuit.flush(candidate)

            for (pos in watched) {
                val expected = reference.getBlock(pos)
                val actual = candidate.getBlock(pos)
                assertEquals(
                    expected,
                    actual,
                    "$label: tick $tick at $pos expected ${mcData.requireBlockByStateId(expected).name}/" +
                        "$expected but was ${mcData.requireBlockByStateId(actual).name}/$actual",
                )
                val expectedEntity = reference.getBlockEntity(pos) as? BlockEntity.Comparator
                if (expectedEntity != null) {
                    val actualEntity = candidate.getBlockEntity(pos) as? BlockEntity.Comparator
                    assertEquals(
                        expectedEntity.outputStrength,
                        actualEntity?.outputStrength,
                        "$label: tick $tick comparator strength at $pos",
                    )
                }
            }
        }
    }

    @Test
    fun wireLineMatches() {
        compare("wire line", { world ->
            val dusts = ArrayList<BlockPos>()
            for (x in 0..15) world.setBlockSilent(BlockPos(x, 0, 0), stone)
            world.setBlockSilent(
                BlockPos(0, 1, 0),
                BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false),
            )
            for (x in 1..15) {
                val pos = BlockPos(x, 1, 0)
                dust(world, pos)
                dusts.add(pos)
            }
            regulate(world, dusts)
            listOf(BlockPos(0, 1, 0))
        }, ticks = 40, toggleEvery = 5)
    }

    @Test
    fun repeaterChainMatches() {
        compare("repeater chain", { world ->
            val dusts = ArrayList<BlockPos>()
            for (x in 0..40) world.setBlockSilent(BlockPos(x, 0, 0), stone)
            world.setBlockSilent(
                BlockPos(0, 1, 0),
                BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false),
            )
            var x = 1
            var delay = 1
            while (x + 3 <= 40) {
                for (offset in 0 until 3) {
                    val pos = BlockPos(x + offset, 1, 0)
                    dust(world, pos)
                    dusts.add(pos)
                }
                world.setBlockSilent(
                    BlockPos(x + 3, 1, 0),
                    BlockStates.repeaterState(delay, BlockDirection.West, locked = false, powered = false),
                )
                delay = if (delay == 4) 1 else delay + 1
                x += 4
            }
            regulate(world, dusts)
            listOf(BlockPos(0, 1, 0))
        }, ticks = 60, toggleEvery = 3)
    }

    @Test
    fun torchInverterChainMatches() {
        compare("torch chain", { world ->
            val dusts = ArrayList<BlockPos>()
            world.setBlockSilent(
                BlockPos(0, 1, -1),
                BlockStates.leverState(LeverFace.Wall, BlockDirection.North, false),
            )
            for (stage in 0 until 6) {
                val x = stage * 4
                val y = 1 + stage
                world.setBlockSilent(BlockPos(x, y, 0), stone)
                world.setBlockSilent(BlockPos(x, y + 1, 0), BlockStates.torchState(true))
                for (offset in 1..2) {
                    world.setBlockSilent(BlockPos(x + offset, y, 0), stone)
                    val pos = BlockPos(x + offset, y + 1, 0)
                    dust(world, pos)
                    dusts.add(pos)
                }
                world.setBlockSilent(
                    BlockPos(x + 3, y + 1, 0),
                    BlockStates.repeaterState(1, BlockDirection.West, locked = false, powered = false),
                )
            }
            regulate(world, dusts)
            listOf(BlockPos(0, 1, -1))
        }, ticks = 60, toggleEvery = 4)
    }

    @Test
    fun comparatorSubtractMatches() {
        compare("comparator subtract", { world ->
            val dusts = ArrayList<BlockPos>()
            for (x in -1..2) for (z in -6..2) world.setBlockSilent(BlockPos(x, 0, z), stone)
            world.setBlockSilent(
                BlockPos(0, 1, 0),
                BlockStates.comparatorState(BlockDirection.East, ComparatorMode.Subtract, false),
            )
            world.setBlockSilent(BlockPos(1, 1, 0), redstoneBlock)
            for (z in -3..-1) {
                val pos = BlockPos(0, 1, z)
                dust(world, pos)
                dusts.add(pos)
            }
            world.setBlockSilent(
                BlockPos(0, 1, -4),
                BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false),
            )
            val out = BlockPos(-1, 1, 0)
            dust(world, out)
            dusts.add(out)
            regulate(world, dusts)
            listOf(BlockPos(0, 1, -4))
        }, ticks = 40, toggleEvery = 5)
    }

    @Test
    fun comparatorCompareMatches() {
        compare("comparator compare", { world ->
            val dusts = ArrayList<BlockPos>()
            for (x in -1..2) for (z in -6..2) world.setBlockSilent(BlockPos(x, 0, z), stone)
            world.setBlockSilent(
                BlockPos(0, 1, 0),
                BlockStates.comparatorState(BlockDirection.East, ComparatorMode.Compare, false),
            )
            world.setBlockSilent(BlockPos(1, 1, 0), redstoneBlock)
            for (z in -3..-1) {
                val pos = BlockPos(0, 1, z)
                dust(world, pos)
                dusts.add(pos)
            }
            world.setBlockSilent(
                BlockPos(0, 1, -4),
                BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false),
            )
            val out = BlockPos(-1, 1, 0)
            dust(world, out)
            dusts.add(out)
            regulate(world, dusts)
            listOf(BlockPos(0, 1, -4))
        }, ticks = 40, toggleEvery = 5)
    }

    @Test
    fun repeaterLockingMatches() {
        compare("repeater lock", { world ->
            for (x in -1..1) for (z in -3..1) world.setBlockSilent(BlockPos(x, 0, z), stone)
            world.setBlockSilent(
                BlockPos(0, 1, 0),
                BlockStates.repeaterState(1, BlockDirection.West, locked = false, powered = false),
            )
            world.setBlockSilent(
                BlockPos(0, 1, -1),
                BlockStates.repeaterState(1, BlockDirection.North, locked = false, powered = false),
            )
            world.setBlockSilent(
                BlockPos(0, 1, -2),
                BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false),
            )
            listOf(BlockPos(0, 1, -2))
        }, ticks = 40, toggleEvery = 6)
    }

    @Test
    fun lampMatches() {
        compare("lamp", { world ->
            val dusts = ArrayList<BlockPos>()
            for (x in 0..6) world.setBlockSilent(BlockPos(x, 0, 0), stone)
            world.setBlockSilent(
                BlockPos(0, 1, 0),
                BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false),
            )
            for (x in 1..5) {
                val pos = BlockPos(x, 1, 0)
                dust(world, pos)
                dusts.add(pos)
            }
            world.setBlockSilent(BlockPos(6, 1, 0), BlockStates.lampState(false))
            regulate(world, dusts)
            listOf(BlockPos(0, 1, 0))
        }, ticks = 40, toggleEvery = 5)
    }

    @Test
    fun compiledGraphCoversExpectedNodes() {
        val world = emptyWorld()
        for (x in 0..5) world.setBlockSilent(BlockPos(x, 0, 0), stone)
        world.setBlockSilent(
            BlockPos(0, 1, 0),
            BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false),
        )
        val dusts = (1..4).map { BlockPos(it, 1, 0) }
        for (pos in dusts) dust(world, pos)
        world.setBlockSilent(BlockPos(5, 1, 0), BlockStates.lampState(false))
        regulate(world, dusts)

        val circuit = OptraIxCompiler.compile(world, eliminateWire = false)
        assertEquals(6, circuit.count)
        assertEquals(NodeType.Lever, circuit.typeOf(circuit.nodeAt(BlockPos(0, 1, 0))))
        assertEquals(NodeType.Wire, circuit.typeOf(circuit.nodeAt(BlockPos(2, 1, 0))))
        assertEquals(NodeType.Lamp, circuit.typeOf(circuit.nodeAt(BlockPos(5, 1, 0))))
        assertTrue(circuit.edgeCount > 0)
    }
}
