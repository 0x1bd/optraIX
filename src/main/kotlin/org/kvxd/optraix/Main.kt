package org.kvxd.optraix

import kotlinx.coroutines.runBlocking
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.net.OptraIxServer
import java.util.concurrent.atomic.AtomicBoolean
import org.kvxd.optraix.block.mcData

fun main(args: Array<String>): Unit = runBlocking {
    val config = ServerConfigLoader.load(args)
    config.prepareDirectories()

    val started = System.currentTimeMillis()
    val states = mcData.blockStateCount
    BlockStates.airState
    println("loaded $states block states in ${System.currentTimeMillis() - started}ms")

    val server = OptraIxServer(config)
    val shutdownReported = AtomicBoolean(false)

    fun shutdownAndReport() {
        val report = shutdownReported.compareAndSet(false, true)
        if (report) println("shutting down, saving world")
        val saved = server.shutdown()
        if (report) println("saved $saved chunks across ${server.worlds.all().size} world(s) in ${config.worldDirectory.path}")
    }

    val shutdownHook = Thread(::shutdownAndReport, "optraix-shutdown")
    Runtime.getRuntime().addShutdownHook(shutdownHook)

    try {
        server.start(this)
        server.awaitStop()
    } finally {
        shutdownAndReport()
        runCatching { Runtime.getRuntime().removeShutdownHook(shutdownHook) }
    }
}
