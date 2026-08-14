package org.kvxd.optraix

import java.io.File

data class ServerConfig(
    val host: String = ServerConfigSchema.host.default,
    val port: Int = ServerConfigSchema.port.default,
    val motd: String = ServerConfigSchema.motd.default,
    val maxPlayers: Int = ServerConfigSchema.maxPlayers.default,
    val viewDistance: Int = ServerConfigSchema.viewDistance.default,
    val tps: Int = ServerConfigSchema.tps.default,
    val clientUpdateRate: Int = ServerConfigSchema.clientUpdateRate.default,
    val compressionThreshold: Int = ServerConfigSchema.compressionThreshold.default,
    val runDirectory: File = ServerConfigSchema.runDirectory.default,
    val autosaveSeconds: Int = ServerConfigSchema.autosaveSeconds.default,
    val viaversion: Boolean = ServerConfigSchema.viaversion.default,
) {

    val worldDirectory: File = File(runDirectory, "world")

    val worldFile: File = File(worldDirectory, "optraix.world")

    val playerFile: File = File(runDirectory, "players.dat")

    val schematicsDirectory: File = File(runDirectory, "schematics")

    fun prepareDirectories() {
        runDirectory.mkdirs()
        schematicsDirectory.mkdirs()
        worldDirectory.mkdirs()
    }
}
