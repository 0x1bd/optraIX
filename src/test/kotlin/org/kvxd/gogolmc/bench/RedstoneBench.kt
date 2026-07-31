package org.kvxd.gogolmc.bench

import org.kvxd.gogolmc.redstone.mchprs.MchprsRedstone
import org.kvxd.gogolmc.redstone.opt3x.Opt3xCircuit
import org.kvxd.gogolmc.redstone.opt3x.Opt3xCompiler
import org.kvxd.gogolmc.world.GameWorld

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

    fun runOpt3x(circuit: Opt3xCircuit, levers: IntArray, ticks: Int, togglePeriod: Int): Result {
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
            "  per tick: updates=${circuit.nodeUpdates / ticks} ticks=${circuit.nodeTicks / ticks} " +
                "chainLinkVisits=${circuit.linkVisits / ticks}"
        )
        return Result("opt3x", ticks, elapsed, circuit.nodeUpdates, 0, circuit.nodeTicks)
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
            "components=${circuit.components} dust=${circuit.dust} " +
                "repeaters=${circuit.repeaters} comparators=${circuit.comparators}"
        )

        if (System.getProperty("opt3xOnly") != null) {
            val built2 = Opt3xCompiler.compile(circuit.world)
            built2.settle()
            val levers2 = circuit.levers.map { built2.nodeAt(it) }.toIntArray()
            println("nodes=${built2.count} edges=${built2.edgeCount}")
            repeat(4) { runOpt3x(built2, levers2, ticks * 20, TogglePeriod) }
            val started = System.nanoTime()
            var loops = 0
            while (System.nanoTime() - started < 20_000_000_000L) {
                runOpt3x(built2, levers2, ticks * 20, TogglePeriod)
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
        val compiled = Opt3xCompiler.compile(circuit.world)
        compiled.settle()
        val compileMillis = (System.nanoTime() - compileStarted) / 1_000_000
        val leverNodes = circuit.levers.map { compiled.nodeAt(it) }.toIntArray()

        println()
        println("compiled in       ${compileMillis}ms")
        println("nodes             ${compiled.count}")
        println("edges             ${compiled.edgeCount}")

        println()
        println("warmup opt3x")
        repeat(8) { runOpt3x(compiled, leverNodes, ticks * 20, TogglePeriod) }

        val runs = ArrayList<Result>()
        repeat(9) { runs += runOpt3x(compiled, leverNodes, ticks * 20, TogglePeriod) }
        val sorted = runs.map { it.tps }.sorted()
        val median = sorted[sorted.size / 2]
        val best = sorted.last()
        val worst = sorted.first()

        report(runs.last(), circuit.components)
        println()
        println("opt3x runs        " + sorted.joinToString(" ") { "%.0f".format(it) })
        println("opt3x median tps  ${"%.1f".format(median)}")
        println("opt3x best tps    ${"%.1f".format(best)}")
        println("opt3x spread      ${"%.2f".format(best / worst)}x")
        println()
        println("speedup vs mchprs ${"%.1f".format(median / mchprs.tps)}x tps (median)")
    }
}
