package org.kvxd.gogolmc.bench

import org.kvxd.gogolmc.redstone.opt3x.Opt3xCircuit
import org.kvxd.gogolmc.redstone.opt3x.Opt3xCompiler

object Opt3xAbBench {

    private const val TogglePeriod = 8
    private const val WarmupRuns = 6
    private const val MeasuredRuns = 9
    private const val RunMillis = 400L

    private class Config(
        val label: String,
        val circuit: Opt3xCircuit,
        val levers: IntArray,
        val world: org.kvxd.gogolmc.world.GameWorld? = null,
        val ioOnly: Boolean = false,
    )

    private fun timedRun(config: Config): Double {
        val circuit = config.circuit
        val levers = config.levers
        val world = config.world
        val budget = RunMillis * 1_000_000
        val started = System.nanoTime()
        var ticks = 0
        var tick = 0
        while (true) {
            if (tick % TogglePeriod == 0) {
                for (lever in levers) circuit.setSource(lever, !circuit.isOn(lever))
            }
            circuit.tick()
            if (world != null) {
                circuit.flush(world, config.ioOnly)
                world.changedBlocks.clear()
                world.changedBlockEntities.clear()
            }
            tick++
            ticks++
            if (ticks and 0x3FFF == 0 && System.nanoTime() - started > budget) break
        }
        val elapsed = System.nanoTime() - started
        return ticks * 1_000_000_000.0 / elapsed
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val lanes = (args.getOrNull(0) ?: "60").toInt()
        val segments = (args.getOrNull(1) ?: "40").toInt()
        val mode = args.getOrNull(2) ?: "ab"

        val bench = BenchCircuit.busses(lanes, segments)
        println("components=${bench.components} dust=${bench.dust} repeaters=${bench.repeaters} comparators=${bench.comparators} torches=${bench.torches}")

        fun build(fuse: Boolean, label: String): Config {
            val circuit = Opt3xCompiler.compile(bench.world, fuseChains = fuse)
            circuit.settle()
            println("$label: nodes=${circuit.count} edges=${circuit.edgeCount} chains=${circuit.chainCount} links=${circuit.fusedLinks}")
            return Config(label, circuit, bench.levers.map { circuit.nodeAt(it) }.toIntArray())
        }

        val configs = when (mode) {
            "fused" -> listOf(build(true, "fused"))
            "plain" -> listOf(build(false, "plain"))
            "flush" -> {
                val io = build(true, "flush-io")
                val full = build(true, "flush-full")
                listOf(
                    Config("flush-io", io.circuit, io.levers, bench.world, ioOnly = true),
                    Config("flush-full", full.circuit, full.levers, bench.world, ioOnly = false),
                )
            }
            else -> listOf(build(true, "fused"), build(false, "plain"))
        }

        for (config in configs) repeat(WarmupRuns) { timedRun(config) }

        val results = configs.associate { it.label to ArrayList<Double>() }
        repeat(MeasuredRuns) {
            for (config in configs) results.getValue(config.label).add(timedRun(config))
        }

        for (config in configs) {
            val sorted = results.getValue(config.label).sorted()
            val median = sorted[sorted.size / 2]
            println(
                "${config.label}: median=${"%.0f".format(median)} tps " +
                    "spread=${"%.2f".format(sorted.last() / sorted.first())}x " +
                    "runs=" + sorted.joinToString(" ") { "%.0f".format(it) }
            )
        }
        if (configs.size == 2) {
            val first = results.getValue(configs[0].label).sorted()[MeasuredRuns / 2]
            val second = results.getValue(configs[1].label).sorted()[MeasuredRuns / 2]
            println("speedup ${configs[0].label}/${configs[1].label} = ${"%.2f".format(first / second)}x")
        }
    }
}
