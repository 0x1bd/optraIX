package org.kvxd.optraix

import org.kvxd.optraix.block.BlockKind
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.redstone.optraix.NodeType
import org.kvxd.optraix.redstone.optraix.OptraIxCompiler
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import java.util.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class OptraIxWireElimTest {

    private val fuzz = OptraIxFuzzTest()

    private val buildMethod = OptraIxFuzzTest::class.java
        .getDeclaredMethod("build", Long::class.java, Int::class.java, Int::class.java)
        .apply { isAccessible = true }

    private val settleMethod = OptraIxFuzzTest::class.java
        .getDeclaredMethod("settle", GameWorld::class.java)
        .apply { isAccessible = true }

    private fun worldOf(built: Any) = built.javaClass.getDeclaredField("world")
        .apply { isAccessible = true }.get(built) as GameWorld

    @Suppress("UNCHECKED_CAST")
    private fun leversOf(built: Any) = built.javaClass.getDeclaredField("levers")
        .apply { isAccessible = true }.get(built) as List<BlockPos>

    private fun check(seed: Long, rows: Int, length: Int, ticks: Int): Pair<Int, Int> {
        val reference = buildMethod.invoke(fuzz, seed, rows, length)
        val referenceWorld = worldOf(reference)
        val levers = leversOf(reference)
        settleMethod.invoke(fuzz, referenceWorld)

        val candidate = buildMethod.invoke(fuzz, seed, rows, length)
        val candidateWorld = worldOf(candidate)
        settleMethod.invoke(fuzz, candidateWorld)

        val exact = OptraIxCompiler.compile(candidateWorld, eliminateWire = false)
        val circuit = OptraIxCompiler.compile(candidateWorld, eliminateWire = true)
        circuit.settle()
        circuit.flush(candidateWorld)

        val watched = circuit.posKey.map { BlockPos.unpack(it) }
        assertTrue(
            watched.none { BlockStates.kindOf(referenceWorld.getBlock(it)) == BlockKind.RedstoneWire },
            "seed $seed: wire elimination left dust nodes in the graph",
        )

        for (pos in watched) {
            assertEquals(
                referenceWorld.getBlock(pos),
                candidateWorld.getBlock(pos),
                "seed $seed: eliminated graph disagrees with the settled world at $pos",
            )
        }

        val leverNodes = levers.map { circuit.nodeAt(it) }
        val random = Random(seed * 31 + 5)

        for (tick in 0 until ticks) {
            if (leverNodes.isNotEmpty() && random.nextInt(3) == 0) {
                val pick = random.nextInt(leverNodes.size)
                MchprsRedstone.onUse(referenceWorld, levers[pick])
                circuit.setSource(leverNodes[pick], !circuit.isOn(leverNodes[pick]))
            }
            referenceWorld.tickScheduled { pos -> MchprsRedstone.tick(referenceWorld, pos) }
            circuit.tick()
            circuit.flush(candidateWorld)

            for (pos in watched) {
                assertEquals(
                    referenceWorld.getBlock(pos),
                    candidateWorld.getBlock(pos),
                    "seed $seed tick $tick: non-dust mismatch at $pos",
                )
                val expected = referenceWorld.getBlockEntity(pos) as? BlockEntity.Comparator ?: continue
                val actual = candidateWorld.getBlockEntity(pos) as? BlockEntity.Comparator
                assertEquals(
                    expected.outputStrength,
                    actual?.outputStrength,
                    "seed $seed tick $tick: comparator strength at $pos",
                )
            }
        }
        return exact.count to circuit.count
    }

    @Test
    fun eliminatedGraphMatchesInterpreterOnComponents() {
        var exactNodes = 0
        var elimNodes = 0
        var clean = 0
        var diverged = 0
        for (seed in 1L..20L) {
            val result = runCatching { check(seed, rows = 6, length = 24, ticks = 60) }
            if (result.isSuccess) {
                clean++
                exactNodes += result.getOrThrow().first
                elimNodes += result.getOrThrow().second
            } else {
                diverged++
            }
        }
        println("wire elimination: $clean/${clean + diverged} circuits match on all component blocks")
        println("  nodes with dust: $exactNodes   without dust: $elimNodes")
        assertTrue(exactNodes > 0)
        assertTrue(
            elimNodes * 2 < exactNodes,
            "elimination should remove a large majority of nodes: $exactNodes -> $elimNodes",
        )
        assertTrue(clean >= 18, "only $clean/20 circuits matched under wire elimination")
    }

    @Test
    fun busCircuitCollapsesToGates() {
        val circuit = org.kvxd.optraix.bench.BenchCircuit.busses(6, 8)
        val compiled = OptraIxCompiler.compile(circuit.world, eliminateWire = true)
        assertTrue(
            compiled.count < circuit.components / 3,
            "expected heavy node reduction, got ${compiled.count} from ${circuit.components}",
        )
        for (node in 0 until compiled.count) {
            assertTrue(compiled.typeOf(node) != NodeType.Wire, "dust survived elimination")
        }
    }
}
