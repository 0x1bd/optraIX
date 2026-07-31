package org.kvxd.gogolmc

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.net.GogolServer

fun main(args: Array<String>): Unit = runBlocking {
    val config = ServerConfig.fromArgs(args)
    config.prepareDirectories()

    val started = System.currentTimeMillis()
    val states = Blocks.stateCount
    BlockStates.airState
    println("loaded $states block states in ${System.currentTimeMillis() - started}ms")

    val server = GogolServer(config)

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
