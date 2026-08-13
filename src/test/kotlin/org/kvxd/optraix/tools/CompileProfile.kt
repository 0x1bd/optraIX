package org.kvxd.optraix.tools

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.redstone.optraix.compiler.OptraIxCompiler
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.SECTION_COUNT
import org.kvxd.optraix.world.WorldStorage
import java.io.File

object CompileProfile {

    private val runtime: Runtime = Runtime.getRuntime()

    private fun used(): Long {
        System.gc()
        Thread.sleep(120)
        System.gc()
        Thread.sleep(120)
        return runtime.totalMemory() - runtime.freeMemory()
    }

    private fun mib(bytes: Long): String = "%.1f MiB".format(bytes / 1048576.0)

    private class Sampler : Thread() {
        @Volatile var peak: Long = 0
        @Volatile var running = true
        override fun run() {
            while (running) {
                val current = runtime.totalMemory() - runtime.freeMemory()
                if (current > peak) peak = current
                try {
                    sleep(5)
                } catch (_: InterruptedException) {
                    return
                }
            }
        }
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val path = args.getOrElse(0) { "run/world_1/optraix.world" }
        println("max heap: ${mib(runtime.maxMemory())}")

        val world = GameWorld()
        val loadStart = System.nanoTime()
        val chunks = WorldStorage.load(world, File(path))
        val loadMillis = (System.nanoTime() - loadStart) / 1_000_000
        val afterLoad = used()
        println("loaded $chunks chunks in ${loadMillis}ms, heap after load ${mib(afterLoad)}")

        var blocks = 0L
        var sections = 0L
        val kinds = HashMap<String, Int>()
        for (chunk in world.snapshotChunks()) {
            for (index in 0 until SECTION_COUNT) {
                val section = chunk.sections[index] ?: continue
                if (section.blockCount == 0) continue
                sections++
                blocks += section.blockCount
                for (slot in 0 until 4096) {
                    val state = section.get(slot)
                    val type = BlockStates.typeOf(state)
                    if (type == Blocks.Air) continue
                    val name = when {
                        BlockStates.pressurePlatePowered(state) != null -> "PressurePlate"
                        BlockStates.isButton(state) -> "Button"
                        else -> type.displayName.replace(" ", "")
                    }
                    kinds[name] = (kinds[name] ?: 0) + 1
                }
            }
        }
        println("sections=$sections nonAirBlocks=$blocks")
        val interesting = setOf(
            "RedstoneWire", "Repeater", "Comparator", "RedstoneTorch", "RedstoneWallTorch",
            "RedstoneLamp", "Lever", "Button", "RedstoneBlock", "IronTrapdoor", "NoteBlock",
            "PressurePlate", "Observer", "TripwireHook",
        )
        var components = 0
        for ((name, count) in kinds.entries.sortedByDescending { it.value }) {
            if (name in interesting) {
                components += count
                println("  component $name = $count")
            }
        }
        println("total component candidates = $components")

        val preflightStart = System.nanoTime()
        val plan = org.kvxd.optraix.redstone.optraix.compiler.CompileMemoryPreflight.evaluate(world)
        println(
            "preflight: needs ${mib(plan.requiredBytes)}, heap free ${mib(plan.heapAvailableBytes)}, " +
                "system free ${mib(plan.systemAvailableBytes)}, verdict ${plan.failure ?: "ok"} " +
                "(${(System.nanoTime() - preflightStart) / 1_000_000}ms)"
        )

        OptraIxCompiler.stageListener = { stage, graph ->
            val live = runtime.totalMemory() - runtime.freeMemory()
            val shape = if (graph == null) "" else " nodes=${graph.nodes.size} edges=${graph.edgeCount}"
            println("  stage $stage: live heap ${mib(live)}$shape")
        }

        val sampler = Sampler()
        sampler.start()
        val compileStart = System.nanoTime()
        val regionChunks = args.getOrNull(1)?.toIntOrNull() ?: OptraIxCompiler.DefaultRegionChunks
        println("region size: $regionChunks chunks")
        val result = runCatching { OptraIxCompiler.compile(world, regionChunks = regionChunks) }
        val compileMillis = (System.nanoTime() - compileStart) / 1_000_000
        sampler.running = false
        sampler.join()

        result.onSuccess { circuit ->
            println("compiled ${circuit.count} nodes in ${compileMillis}ms")
        }.onFailure { cause ->
            println("compile failed after ${compileMillis}ms: ${cause::class.simpleName}: ${cause.message}")
        }
        println("peak heap during compile: ${mib(sampler.peak)}")
        println("heap delta over load: ${mib(sampler.peak - afterLoad)}")
    }
}
