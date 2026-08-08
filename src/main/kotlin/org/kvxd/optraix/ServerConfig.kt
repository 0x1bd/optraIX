package org.kvxd.optraix

import java.io.File
import java.net.URI
import java.nio.charset.StandardCharsets
import java.util.Properties

data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 25565,
    val motd: String = "optraix redstone server",
    val maxPlayers: Int = 100,
    val viewDistance: Int = 10,
    val tps: Int = 20,
    val compressionThreshold: Int = 256,
    val runDirectory: File = File("run"),
    val autosaveSeconds: Int = 300,
    val viaversion: Boolean = false,
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

    fun save(file: File) {
        file.parentFile?.mkdirs()
        file.writeText(
            buildString {
                appendProperty("host", host)
                appendProperty("port", port)
                appendProperty("motd", motd)
                appendProperty("max-players", maxPlayers)
                appendProperty("view-distance", viewDistance)
                appendProperty("tps", tps)
                appendProperty("compression-threshold", compressionThreshold)
                appendProperty("run-dir", runDirectory.path)
                appendProperty("autosave-seconds", autosaveSeconds)
                appendProperty("viaversion", viaversion)
            },
            StandardCharsets.UTF_8,
        )
    }

    fun withArgs(args: Array<String>): ServerConfig {
        var result = this
        var index = 0
        while (index < args.size) {
            val argument = args[index]
            when {
                argument == "--host" -> result = result.copy(host = requireValue(args, ++index, argument))
                argument == "--port" -> result = result.copy(port = requireInt(args, ++index, argument))
                argument == "--motd" -> result = result.copy(motd = requireValue(args, ++index, argument))
                argument == "--max-players" -> result = result.copy(maxPlayers = requireInt(args, ++index, argument))
                argument == "--view-distance" -> result =
                    result.copy(viewDistance = requireInt(args, ++index, argument))

                argument == "--tps" -> result = result.copy(tps = requireInt(args, ++index, argument))
                argument == "--compression-threshold" -> {
                    result = result.copy(compressionThreshold = requireInt(args, ++index, argument))
                }

                argument == "--run-dir" -> result =
                    result.copy(runDirectory = File(requireValue(args, ++index, argument)))

                argument == "--autosave-seconds" -> {
                    result = result.copy(autosaveSeconds = requireInt(args, ++index, argument))
                }

                argument == "--viaversion" -> {
                    val next = args.getOrNull(index + 1)
                    if (next != null && !next.startsWith("--")) {
                        result = result.copy(viaversion = parseBoolean(next, argument))
                        index++
                    } else {
                        result = result.copy(viaversion = true)
                    }
                }

                argument == "--no-viaversion" -> result = result.copy(viaversion = false)
                argument.startsWith("--viaversion=") -> {
                    result = result.copy(viaversion = parseBoolean(argument.substringAfter('='), "--viaversion"))
                }

                else -> throw IllegalArgumentException("Unknown argument '$argument'")
            }
            index++
        }
        return result.validated("command line")
    }

    private fun validated(source: String): ServerConfig {
        require(port in 0..65535) { "$source: port must be between 0 and 65535" }
        require(maxPlayers >= 0) { "$source: max-players must be at least 0" }
        require(viewDistance >= 0) { "$source: view-distance must be at least 0" }
        require(tps > 0) { "$source: tps must be greater than 0" }
        require(compressionThreshold >= -1) { "$source: compression-threshold must be at least -1" }
        require(autosaveSeconds >= 0) { "$source: autosave-seconds must be at least 0" }
        return this
    }

    companion object {

        fun load(args: Array<String>, file: File = defaultConfigFile()): ServerConfig {
            val persisted = if (file.exists()) fromProperties(file) else ServerConfig()
            if (!file.exists()) persisted.save(file)
            return persisted.withArgs(args)
        }

        fun fromArgs(args: Array<String>): ServerConfig = ServerConfig().withArgs(args)

        fun fromProperties(file: File): ServerConfig {
            val properties = Properties()
            file.reader(StandardCharsets.UTF_8).use(properties::load)
            return ServerConfig(
                host = properties.string("host", "0.0.0.0"),
                port = properties.int("port", 25565, file),
                motd = properties.string("motd", "optraix redstone server"),
                maxPlayers = properties.int("max-players", 100, file),
                viewDistance = properties.int("view-distance", 10, file),
                tps = properties.int("tps", 20, file),
                compressionThreshold = properties.int("compression-threshold", 256, file),
                runDirectory = File(properties.string("run-dir", "run")),
                autosaveSeconds = properties.int("autosave-seconds", 300, file),
                viaversion = properties.boolean("viaversion", false, file),
            ).validated(file.path)
        }

        fun defaultConfigFile(): File = File(executableDirectory(), "optraix.cfg")

        private fun executableDirectory(): File {
            System.getProperty("optraix.executable.dir")?.takeIf { it.isNotBlank() }?.let {
                return File(it).absoluteFile
            }

            if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
                ProcessHandle.current().info().command().orElse(null)?.let {
                    return File(it).absoluteFile.parentFile
                }
            }

            runCatching {
                val uri: URI = ServerConfig::class.java.protectionDomain.codeSource.location.toURI()
                val location = File(uri).absoluteFile
                if (location.isFile) {
                    val parent = location.parentFile
                    if (parent.name == "lib") {
                        val bin = File(parent.parentFile, "bin")
                        if (bin.isDirectory) return bin
                    }
                    return parent
                }
            }

            return File(System.getProperty("user.dir", ".")).absoluteFile
        }

        private fun requireValue(args: Array<String>, index: Int, option: String): String =
            args.getOrNull(index) ?: throw IllegalArgumentException("Missing value for $option")

        private fun requireInt(args: Array<String>, index: Int, option: String): Int {
            val value = requireValue(args, index, option)
            return value.toIntOrNull() ?: throw IllegalArgumentException("Invalid integer for $option: '$value'")
        }

        private fun parseBoolean(value: String, option: String): Boolean =
            when (value.lowercase()) {
                "true", "yes", "on", "1" -> true
                "false", "no", "off", "0" -> false
                else -> throw IllegalArgumentException("Invalid boolean for $option: '$value'")
            }

        private fun Properties.string(key: String, fallback: String): String = getProperty(key) ?: fallback

        private fun Properties.int(key: String, fallback: Int, file: File): Int {
            val value = getProperty(key) ?: return fallback
            return value.toIntOrNull()
                ?: throw IllegalArgumentException("${file.path}: '$key' must be an integer, got '$value'")
        }

        private fun Properties.boolean(key: String, fallback: Boolean, file: File): Boolean {
            val value = getProperty(key) ?: return fallback
            return runCatching { parseBoolean(value, key) }
                .getOrElse { throw IllegalArgumentException("${file.path}: '$key' must be true or false, got '$value'") }
        }

        private fun StringBuilder.appendProperty(key: String, value: Any) {
            append(key)
            append('=')
            append(escapeProperty(value.toString()))
            append('\n')
        }

        private fun escapeProperty(value: String): String = buildString(value.length) {
            value.forEachIndexed { index, character ->
                when (character) {
                    '\\' -> append("\\\\")
                    '\n' -> append("\\n")
                    '\r' -> append("\\r")
                    '\t' -> append("\\t")
                    '\u000c' -> append("\\f")
                    '=', ':' -> append('\\').append(character)
                    '#', '!' -> if (index == 0) append('\\').append(character) else append(character)
                    ' ' -> if (index == 0) append("\\ ") else append(character)
                    else -> append(character)
                }
            }
        }
    }
}