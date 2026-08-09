package org.kvxd.optraix

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.redstone.mchprs.Wire
import org.kvxd.optraix.redstone.optraix.ChainFuser
import org.kvxd.optraix.redstone.optraix.NodeType
import org.kvxd.optraix.redstone.optraix.OptraIxCompiler
import org.kvxd.optraix.redstone.optraix.OptraIxGraph
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WorldGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kvxd.optraix.mcdata.v1_20_4.Blocks

class OptraIxFeedbackTest {

    @Test
    fun graphPreservesSelfFeedbackEdges() {
        val graph = OptraIxGraph()
        val node = graph.add(BlockPos(0, 1, 0), NodeType.Torch, BlockStates.torchState(true))

        graph.link(node.id, node.id, 0, side = false)

        assertEquals(1, graph.edgeCount)
        assertEquals(1, node.inputs.size)
        assertEquals(1, node.outputs.size)

        val fused = ChainFuser.fuse(graph)
        assertEquals(1, fused.edgeCount)
        assertEquals(0, fused.nodes.single().outputs.single().target)
    }

    @Test
    fun wireEliminatedSelfFeedbackKeepsOscillating() {
        val world = GameWorld(WorldGenerator(Blocks.Air.defaultState, -64))
        val stone = Blocks.Stone.defaultState
        val torch = BlockPos(1, 1, 0)
        val dusts = listOf(
            BlockPos(1, 1, 1), BlockPos(1, 1, 2), BlockPos(0, 1, 2), BlockPos(-1, 1, 2),
            BlockPos(-2, 1, 2), BlockPos(-2, 1, 1), BlockPos(-2, 1, 0), BlockPos(-1, 1, 0),
        )

        for (pos in dusts) world.setBlockSilent(BlockPos(pos.x, 0, pos.z), stone)
        world.setBlockSilent(BlockPos(0, 1, 0), stone)
        world.setBlockSilent(torch, BlockStates.wallTorchState(true, BlockDirection.East))
        for (pos in dusts) world.setBlockSilent(pos, Wire.makeCross(0))
        repeat(3) {
            for (pos in dusts) {
                world.setBlockSilent(pos, Wire.getRegulatedSides(world.getBlock(pos), world, pos))
            }
            for (pos in dusts) MchprsRedstone.update(world, pos)
        }
        MchprsRedstone.update(world, torch)

        assertTrue(MchprsRedstone.wallTorchShouldBeOff(world, torch, BlockDirection.East))
        val circuit = OptraIxCompiler.compile(world)
        val node = circuit.nodeAt(torch)
        assertEquals(1, circuit.edgeCount)
        assertEquals(1, circuit.pendingTicks)

        circuit.settle()
        repeat(8) { tick ->
            circuit.tick()
            if (tick % 2 == 0) assertFalse(circuit.isOn(node)) else assertTrue(circuit.isOn(node))
            assertEquals(1, circuit.pendingTicks)
        }
    }
}
