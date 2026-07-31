package org.kvxd.optraix.bench

import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.redstone.optraix.OptraIxCircuit
import org.kvxd.optraix.redstone.optraix.OptraIxCompiler
import org.kvxd.optraix.world.GameWorld

object RedstoneBench {

    const val TogglePeriod = 8

    class Result(
        val label: String,
        val ticks: Int,
        val nanos: Long,
        val blockUpdates: Long,
        val wireUpdates: Long,
        val scheduled: Long,
    ) {
        val tps: Double get() = ticks * 1_000_000_000.0 / nanos
        val mspt: Double get() = nanos / 1_000_000.0 / ticks
        val updates: Long get() = blockUpdates + wireUpdates
        val updatesPerSecond: Double get() = updates * 1_000_000_000.0 / nanos
    }

    fun runMchprs(circuit: BenchCircuit, ticks: Int, togglePeriod: Int): Result {
        val world = circuit.world
        MchprsRedstone.stats.reset()
        settle(world)

        val started = System.nanoTime()
        for (tick in 0 until ticks) {
            if (tick % togglePeriod == 0) {
                for (lever in circuit.levers) MchprsRedstone.onUse(world, lever)
            }
            world.tickScheduled { pos -> MchprsRedstone.tick(world, pos) }
            world.changedBlocks.clear()
            world.changedBlockEntities.clear()
        }
        val elapsed = System.nanoTime() - started

        val stats = MchprsRedstone.stats
        return Result("mchprs", ticks, elapsed, stats.blockUpdates, stats.wireUpdates, stats.scheduledTicks)
    }

    fun runOptraIx(circuit: OptraIxCircuit, levers: IntArray, ticks: Int, togglePeriod: Int): Result {
        circuit.resetStats()

        val started = System.nanoTime()
        for (tick in 0 until ticks) {
            if (tick % togglePeriod == 0) {
                for (lever in levers) circuit.setSource(lever, !circuit.isOn(lever))
            }
            circuit.tick()
        }
        val elapsed = System.nanoTime() - started

        println(
            "  per tick: updates=${circuit.nodeUpdates / ticks} ticks=${circuit.nodeTicks / ticks}"
        )
        return Result("optraix", ticks, elapsed, circuit.nodeUpdates, 0, circuit.nodeTicks)
    }

    private fun settle(world: GameWorld) {
        repeat(40) {
            world.tickScheduled { pos -> MchprsRedstone.tick(world, pos) }
            world.changedBlocks.clear()
            world.changedBlockEntities.clear()
        }
    }

    private fun report(result: Result, components: Int) {
        println()
        println("engine            ${result.label}")
        println("ticks             ${result.ticks}")
        println("elapsed           ${result.nanos / 1_000_000}ms")
        println("tps               ${"%.1f".format(result.tps)}")
        println("mspt              ${"%.4f".format(result.mspt)}")
        println("node updates      ${result.blockUpdates}")
        println("wire updates      ${result.wireUpdates}")
        println("node ticks        ${result.scheduled}")
        println("updates/sec       ${"%.2f M".format(result.updatesPerSecond / 1_000_000.0)}")
        println("updates/tick      ${result.updates / result.ticks}")
        println("updates/component ${"%.2f".format(result.updates.toDouble() / result.ticks / components)}")
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val lanes = (args.getOrNull(0) ?: "60").toInt()
        val segments = (args.getOrNull(1) ?: "40").toInt()
        val ticks = (args.getOrNull(2) ?: "400").toInt()

        println("building circuit: lanes=$lanes segments=$segments")
        val built = System.nanoTime()
        val circuit = BenchCircuit.busses(lanes, segments)
        println("built in ${(System.nanoTime() - built) / 1_000_000}ms")
        println(
            "components=${circuit.components} dust=${circuit.dust} repeaters=${circuit.repeaters} " +
                "comparators=${circuit.comparators} torches=${circuit.torches}"
        )

        if (System.getProperty("optraixOnly") != null) {
            val built2 = OptraIxCompiler.compile(circuit.world)
            built2.settle()
            val levers2 = circuit.levers.map { built2.nodeAt(it) }.toIntArray()
            println("nodes=${built2.count} edges=${built2.edgeCount} chains=${built2.chainCount} links=${built2.fusedLinks}")
            repeat(4) { runOptraIx(built2, levers2, ticks * 20, TogglePeriod) }
            val started = System.nanoTime()
            var loops = 0
            while (System.nanoTime() - started < 20_000_000_000L) {
                runOptraIx(built2, levers2, ticks * 20, TogglePeriod)
                loops++
            }
            println("profiled $loops loops")
            return
        }

        println("warmup mchprs")
        runMchprs(circuit, 100, TogglePeriod)

        println("measuring mchprs for $ticks ticks")
        val mchprs = runMchprs(circuit, ticks, TogglePeriod)
        report(mchprs, circuit.components)

        val compileStarted = System.nanoTime()
        val compiled = OptraIxCompiler.compile(circuit.world)
        compiled.settle()
        val compileMillis = (System.nanoTime() - compileStarted) / 1_000_000
        val leverNodes = circuit.levers.map { compiled.nodeAt(it) }.toIntArray()

        println()
        println("compiled in       ${compileMillis}ms")
        println("nodes             ${compiled.count}")
        println("edges             ${compiled.edgeCount}")
        println("chains            ${compiled.chainCount}")
        println("fused links       ${compiled.fusedLinks}")
        println("histogram bytes   ${compiled.histogramBytes} (dense would be ${compiled.count * 32})")

        val optraixTicks = maxOf(ticks * 20, 12_000_000 / maxOf(1, circuit.components / 100))

        println()
        println("warmup optraix ($optraixTicks ticks per run)")
        repeat(6) { runOptraIx(compiled, leverNodes, optraixTicks, TogglePeriod) }

        val runs = ArrayList<Result>()
        repeat(9) { runs += runOptraIx(compiled, leverNodes, optraixTicks, TogglePeriod) }
        println("optraix order       " + runs.joinToString(" ") { "%.0f".format(it.tps) })
        val sorted = runs.map { it.tps }.sorted()
        val median = sorted[sorted.size / 2]
        val best = sorted.last()
        val worst = sorted.first()

        report(runs.last(), circuit.components)
        println()
        println("optraix runs        " + sorted.joinToString(" ") { "%.0f".format(it) })
        println("optraix median tps  ${"%.1f".format(median)}")
        println("optraix best tps    ${"%.1f".format(best)}")
        println("optraix spread      ${"%.2f".format(best / worst)}x")
        println()
        println("speedup vs mchprs ${"%.1f".format(median / mchprs.tps)}x tps (median)")
    }
}
