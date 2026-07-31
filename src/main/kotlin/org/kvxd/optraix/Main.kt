package org.kvxd.optraix

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.net.OptraIxServer

fun main(args: Array<String>): Unit = runBlocking {
    val config = ServerConfig.fromArgs(args)
    config.prepareDirectories()

    val started = System.currentTimeMillis()
    val states = Blocks.stateCount
    BlockStates.airState
    println("loaded $states block states in ${System.currentTimeMillis() - started}ms")

    val server = OptraIxServer(config)

    Runtime.getRuntime().addShutdownHook(
        Thread {
            println("shutting down, saving world")
            val saved = server.shutdown()
            println("saved $saved chunks to ${config.worldFile.path}")
        }
    )

    coroutineScope {
        server.start(this)
        awaitCancellation()
    }
}
