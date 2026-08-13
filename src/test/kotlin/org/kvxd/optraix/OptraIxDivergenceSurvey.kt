package org.kvxd.optraix

import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.redstone.optraix.compiler.OptraIxCompiler
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import java.util.Random
import kotlin.test.Test

class OptraIxDivergenceSurvey {

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

    private fun firstDivergence(seed: Long, rows: Int, length: Int, ticks: Int): Int {
        val reference = buildMethod.invoke(fuzz, seed, rows, length)
        val referenceWorld = worldOf(reference)
        val levers = leversOf(reference)
        settleMethod.invoke(fuzz, referenceWorld)

        val candidate = buildMethod.invoke(fuzz, seed, rows, length)
        val candidateWorld = worldOf(candidate)
        settleMethod.invoke(fuzz, candidateWorld)

        val circuit = OptraIxCompiler.compile(candidateWorld, eliminateWire = false)
        circuit.settle()
        circuit.flush(candidateWorld)

        val watched = circuit.posKey.map { BlockPos.unpack(it) }
        for (pos in watched) {
            if (referenceWorld.getBlock(pos) != candidateWorld.getBlock(pos)) return -1
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
                if (referenceWorld.getBlock(pos) != candidateWorld.getBlock(pos)) return tick
            }
        }
        return Int.MAX_VALUE
    }

    @Test
    fun survey() {
        var compileMismatch = 0
        var diverged = 0
        var clean = 0
        val ticks = 150
        val seeds = 1L..60L
        val divergenceTicks = ArrayList<Int>()

        for (seed in seeds) {
            when (val tick = firstDivergence(seed, rows = 8, length = 32, ticks = ticks)) {
                -1 -> compileMismatch++
                Int.MAX_VALUE -> clean++
                else -> {
                    diverged++
                    divergenceTicks.add(tick)
                    println("  seed $seed diverged at tick $tick")
                }
            }
        }

        val total = seeds.count()
        println("optraix divergence survey over $total random circuits, $ticks ticks each")
        println("  compile-time mismatches : $compileMismatch")
        println("  clean for all $ticks ticks : $clean")
        println("  diverged                : $diverged")
        if (divergenceTicks.isNotEmpty()) {
            println("  earliest divergence tick: ${divergenceTicks.min()}")
            println("  median divergence tick  : ${divergenceTicks.sorted()[divergenceTicks.size / 2]}")
        }
    }
}
