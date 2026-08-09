package org.kvxd.optraix.bench

import org.kvxd.optraix.block.BlockKind
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.redstone.optraix.NodeType
import org.kvxd.optraix.redstone.optraix.OptraIxCircuit
import org.kvxd.optraix.redstone.optraix.OptraIxCompiler
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WORLD_MIN_Y
import org.kvxd.optraix.world.WorldGenerator
import org.kvxd.optraix.world.WorldStorage
import java.io.File
import org.kvxd.optraix.mcdata.v1_20_4.Blocks

object WorldAbBench {

    private const val TogglePeriod = 8
    private const val WarmupRuns = 6
    private const val MeasuredRuns = 9
    private const val RunMillis = 400L

    private class Config(val label: String, val circuit: OptraIxCircuit, val levers: IntArray)

    private fun timedRun(config: Config): Double {
        val circuit = config.circuit
        val levers = config.levers
        val budget = RunMillis * 1_000_000
        val started = System.nanoTime()
        var ticks = 0
        var tick = 0
        while (true) {
            if (tick % TogglePeriod == 0) {
                for (lever in levers) circuit.setSource(lever, !circuit.isOn(lever))
            }
            circuit.tick()
            tick++
            ticks++
            if (ticks and 0x3FFF == 0 && System.nanoTime() - started > budget) break
        }
        val elapsed = System.nanoTime() - started
        return ticks * 1_000_000_000.0 / elapsed
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val path = args.getOrNull(0) ?: "worlds/cpu-22-08-2025.world"
        val leverCount = (args.getOrNull(1) ?: "8").toInt()

        val world = GameWorld(WorldGenerator(Blocks.Air.defaultState, 0))
        val chunks = WorldStorage.load(world, File(path))
        println("loaded $path: $chunks chunks")

        val levers = ArrayList<BlockPos>()
        for (chunk in world.snapshotChunks()) {
            for (sectionIndex in chunk.sections.indices) {
                val section = chunk.sections[sectionIndex] ?: continue
                if (section.blockCount == 0) continue
                for (slot in 0 until 4096) {
                    if (BlockStates.kindOf(section.get(slot)) != BlockKind.Lever) continue
                    levers.add(
                        BlockPos(
                            chunk.x * 16 + (slot and 15),
                            WORLD_MIN_Y + (sectionIndex shl 4) + (slot shr 8),
                            chunk.z * 16 + ((slot shr 4) and 15),
                        )
                    )
                }
            }
        }
        levers.sortBy { it.asLong() }
        println("levers in world: ${levers.size}")
        val stride = maxOf(1, levers.size / leverCount)
        val chosen = levers.filterIndexed { index, _ -> index % stride == 0 }.take(leverCount)

        fun build(fuse: Boolean, label: String): Config {
            val circuit = OptraIxCompiler.compile(world, fuseChains = fuse)
            circuit.settle()
            val nodes = IntArray(NodeType.Count)
            for (node in 0 until circuit.count) nodes[circuit.typeOf(node)]++
            println(
                "$label: nodes=${circuit.count} edges=${circuit.edgeCount} " +
                    "chains=${circuit.chainCount} links=${circuit.fusedLinks} " +
                    "types=" + NodeType.names.withIndex()
                        .filter { nodes[it.index] > 0 }
                        .joinToString(",") { "${it.value}:${nodes[it.index]}" }
            )
            return Config(label, circuit, chosen.map { circuit.nodeAt(it) }.filter { it >= 0 }.toIntArray())
        }

        val configs = listOf(build(true, "fused"), build(false, "plain"))
        for (config in configs) {
            println("${config.label}: driving ${config.levers.size} levers")
            repeat(WarmupRuns) { timedRun(config) }
        }

        val results = configs.associate { it.label to ArrayList<Double>() }
        repeat(MeasuredRuns) {
            for (config in configs) results.getValue(config.label).add(timedRun(config))
        }

        for (config in configs) {
            val sorted = results.getValue(config.label).sorted()
            println(
                "${config.label}: median=${"%.0f".format(sorted[sorted.size / 2])} tps " +
                    "spread=${"%.2f".format(sorted.last() / sorted.first())}x " +
                    "runs=" + sorted.joinToString(" ") { "%.0f".format(it) }
            )
        }
        val fused = results.getValue("fused").sorted()[MeasuredRuns / 2]
        val plain = results.getValue("plain").sorted()[MeasuredRuns / 2]
        println("speedup fused/plain = ${"%.2f".format(fused / plain)}x")
    }
}
