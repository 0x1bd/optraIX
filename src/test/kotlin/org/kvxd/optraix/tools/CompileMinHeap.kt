package org.kvxd.optraix.tools

import org.kvxd.optraix.redstone.optraix.OptraIxCompiler
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WorldStorage
import java.io.File

object CompileMinHeap {

    @JvmStatic
    fun main(args: Array<String>) {
        val path = args.getOrElse(0) { "run/world_1/optraix.world" }
        val runtime = Runtime.getRuntime()
        val world = GameWorld()
        WorldStorage.load(world, File(path))
        val regionChunks = args.getOrNull(1)?.toIntOrNull() ?: OptraIxCompiler.DefaultRegionChunks
        val started = System.nanoTime()
        val circuit = OptraIxCompiler.compile(world, regionChunks = regionChunks)
        println(
            "compiled ${circuit.count} nodes, regionChunks=$regionChunks, " +
                "heap ${runtime.maxMemory() / 1048576} MiB, ${(System.nanoTime() - started) / 1_000_000}ms"
        )
    }
}
