package org.kvxd.gogolmc

import java.io.File

class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 25565,
    val motd: String = "gogolmc redstone server",
    val maxPlayers: Int = 100,
    val viewDistance: Int = 10,
    val tps: Int = 20,
    val compressionThreshold: Int = 256,
    val runDirectory: File = File("run"),
    val autosaveSeconds: Int = 300,
) {

    val worldFile: File = File(runDirectory, "world/gogolmc.world")

    val playerFile: File = File(runDirectory, "players.dat")

    val schematicsDirectory: File = File(runDirectory, "schematics")

    fun prepareDirectories() {
        runDirectory.mkdirs()
        schematicsDirectory.mkdirs()
        worldFile.parentFile?.mkdirs()
    }

    companion object {

        fun fromArgs(args: Array<String>): ServerConfig {
            var host = "0.0.0.0"
            var port = 25565
            var viewDistance = 10
            var tps = 20
            var runDirectory = File("run")
            var index = 0
            while (index < args.size) {
                when (args[index]) {
                    "--host" -> host = args[++index]
                    "--port" -> port = args[++index].toInt()
                    "--view-distance" -> viewDistance = args[++index].toInt()
                    "--tps" -> tps = args[++index].toInt()
                    "--run-dir" -> runDirectory = File(args[++index])
                }
                index++
            }
            return ServerConfig(
                host = host,
                port = port,
                viewDistance = viewDistance,
                tps = tps,
                runDirectory = runDirectory,
            )
        }
    }
}
