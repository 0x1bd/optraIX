package org.kvxd.optraix

import org.kvxd.optraix.bench.BenchCircuit
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.LeverFace
import org.kvxd.optraix.block.property.WireSide
import org.kvxd.optraix.redstone.mchprs.Wire
import org.kvxd.optraix.redstone.optraix.OptraIxCircuit
import org.kvxd.optraix.redstone.optraix.OptraIxCompiler
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WorldGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptraIxRegionSplitTest {

    private fun describe(circuit: OptraIxCircuit): List<String> {
        val rows = ArrayList<String>(circuit.count)
        for (node in 0 until circuit.count) {
            val pos = BlockPos.unpack(circuit.posKey[node])
            val outgoing = ArrayList<String>()
            for (slot in circuit.edgeStart[node] until circuit.edgeStart[node + 1]) {
                val packed = circuit.edges[slot]
                val target = packed and OptraIxCircuit.TargetMask
                val weight = (packed ushr OptraIxCircuit.WeightShift) and 0xF
                val side = (packed and OptraIxCircuit.SideBit) != 0
                outgoing += "${BlockPos.unpack(circuit.posKey[target])}:$weight:$side"
            }
            outgoing.sort()
            rows += "$pos type=${circuit.typeOf(node)} on=${circuit.isOn(node)} -> ${outgoing.joinToString(",")}"
        }
        rows.sort()
        return rows
    }

    @Test
    fun regionSizeDoesNotChangeTheCompiledCircuit() {
        val circuit = BenchCircuit.busses(8, 10)
        val reference = describe(OptraIxCompiler.compile(circuit.world, regionChunks = 1024))
        assertTrue(reference.isNotEmpty())

        for (regionChunks in intArrayOf(1, 2, 4, 8, 32)) {
            val candidate = describe(OptraIxCompiler.compile(circuit.world, regionChunks = regionChunks))
            assertEquals(
                reference,
                candidate,
                "compiling with regionChunks=$regionChunks changed the circuit",
            )
        }
    }

    @Test
    fun longDustRunAcrossARegionBorderKeepsItsSignalLoss() {
        for (start in 4..20) {
            val world = GameWorld(WorldGenerator(Blocks.airState, 0))
            val stone = Blocks.require("minecraft:stone").defaultStateId
            val lamp = BlockPos(start + 15, 1, 0)
            for (x in start..(start + 15)) world.setBlockSilent(BlockPos(x, 0, 0), stone)
            world.setBlockSilent(
                BlockPos(start, 1, 0),
                BlockStates.leverState(LeverFace.Floor, BlockDirection.North, true),
            )
            val dusts = ((start + 1)..(start + 14)).map { BlockPos(it, 1, 0) }
            for (pos in dusts) {
                world.setBlockSilent(pos, Wire.make(WireSide.None, WireSide.None, WireSide.None, WireSide.None, 0))
            }
            world.setBlockSilent(lamp, BlockStates.lampState(false))
            repeat(2) {
                for (pos in dusts) world.setBlockSilent(pos, Wire.getRegulatedSides(world.getBlock(pos), world, pos))
            }

            val reference = describe(OptraIxCompiler.compile(world, regionChunks = 1024))
            assertEquals(2, reference.size, "only the lever and the lamp should survive at start=$start")
            assertTrue(
                reference.any { it.contains("-> $lamp:13:false") },
                "the 14 dust run should collapse to one weight-13 edge at start=$start, got $reference",
            )
            for (regionChunks in intArrayOf(1, 2)) {
                assertEquals(
                    reference,
                    describe(OptraIxCompiler.compile(world, regionChunks = regionChunks)),
                    "dust run starting at x=$start diverged with regionChunks=$regionChunks",
                )
            }
        }
    }

    @Test
    fun wireRunsCrossingRegionBordersKeepTheirWeights() {
        val circuit = BenchCircuit.busses(4, 24)
        val reference = describe(OptraIxCompiler.compile(circuit.world, regionChunks = 1024, fuseChains = false))

        for (regionChunks in intArrayOf(1, 2, 4)) {
            val candidate = describe(
                OptraIxCompiler.compile(circuit.world, regionChunks = regionChunks, fuseChains = false)
            )
            assertEquals(
                reference,
                candidate,
                "unfused compile with regionChunks=$regionChunks changed the circuit",
            )
        }
    }
}
