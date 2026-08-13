package org.kvxd.optraix.bench

import org.kvxd.optraix.world.BlockPos
import java.util.concurrent.ConcurrentLinkedQueue

object TickLoopBench {

    private const val Iterations = 20_000_000

    private inline fun measure(label: String, body: (Int) -> Unit) {
        repeat(2_000_000) { body(it) }
        val started = System.nanoTime()
        for (i in 0 until Iterations) body(i)
        val elapsed = System.nanoTime() - started
        val perOp = elapsed.toDouble() / Iterations
        println("%-34s %7.2f ns/tick  %12.0f tps".format(label, perOp, 1_000_000_000.0 / perOp))
    }

    @JvmStatic
    fun main(args: Array<String>) {
        var sink = 0L
        val tasks = ConcurrentLinkedQueue<Runnable>()
        val changedBlocks = HashSet<Long>()
        val changedBlockEntities = HashSet<Long>()
        val players = ArrayList<String>()

        measure("empty loop body") { sink += it }
        measure("System.nanoTime() x1") { sink += System.nanoTime() }
        measure("System.nanoTime() x2") { sink += System.nanoTime() + System.nanoTime() }
        measure("System.currentTimeMillis() x1") { sink += System.currentTimeMillis() }
        measure("System.currentTimeMillis() x3") {
            sink += System.currentTimeMillis() + System.currentTimeMillis() + System.currentTimeMillis()
        }
        measure("ConcurrentLinkedQueue.poll") { sink += if (tasks.poll() == null) 0 else 1 }
        measure("2x HashSet.isEmpty + players") {
            if (changedBlocks.isNotEmpty()) sink++
            if (changedBlockEntities.isNotEmpty()) sink++
            for (player in players) sink++
        }
        measure("current server housekeeping") {
            while (true) {
                tasks.poll() ?: break
            }
            if (changedBlocks.isNotEmpty()) sink++
            if (changedBlockEntities.isNotEmpty()) sink++
            for (player in players) sink++
            sink += System.currentTimeMillis()
            sink += System.currentTimeMillis()
            sink += System.currentTimeMillis()
            sink += System.nanoTime()
            sink += System.nanoTime()
        }
        measure("amortised housekeeping (1/256)") {
            if (it and 255 == 0) {
                while (true) {
                    tasks.poll() ?: break
                }
                if (changedBlocks.isNotEmpty()) sink++
                if (changedBlockEntities.isNotEmpty()) sink++
                for (player in players) sink++
                sink += System.currentTimeMillis()
                sink += System.nanoTime()
            }
        }

        println("sink=$sink (ignore)")
        println()

        val circuit = BenchCircuit.busses(20, 20)
        val compiled = org.kvxd.optraix.redstone.optraix.compiler.OptraIxCompiler.compile(circuit.world)
        compiled.settle()
        repeat(200) { compiled.tick() }
        println("idle circuit: ${compiled.count} nodes, ${compiled.pendingTicks} pending")
        measure("optraix idle tick") { compiled.tick() }
        measure("optraix idle tick + housekeeping") {
            compiled.tick()
            if (it and 255 == 0) {
                while (true) {
                    tasks.poll() ?: break
                }
                if (changedBlocks.isNotEmpty()) sink++
                for (player in players) sink++
                sink += System.nanoTime()
            }
        }
        println("sink=$sink (ignore)")
    }
}
