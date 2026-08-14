package org.kvxd.optraix

import java.io.File

internal object ServerConfigSchema {

    private val declared = ArrayList<ServerConfigOption<*>>()

    val host = string("host", "0.0.0.0", ServerConfig::host) { config, value ->
        config.copy(host = value)
    }
    val port = integer(
        "port",
        25565,
        ServerConfig::port,
        validation = { if (it in 0..65535) null else "must be between 0 and 65535" },
    ) { config, value -> config.copy(port = value) }
    val motd = string("motd", "optraix redstone server", ServerConfig::motd) { config, value ->
        config.copy(motd = value)
    }
    val maxPlayers = nonNegativeInteger("max-players", 100, ServerConfig::maxPlayers) { config, value ->
        config.copy(maxPlayers = value)
    }
    val viewDistance = nonNegativeInteger("view-distance", 10, ServerConfig::viewDistance) { config, value ->
        config.copy(viewDistance = value)
    }
    val tps = integer(
        "tps",
        20,
        ServerConfig::tps,
        validation = { if (it > 0) null else "must be greater than 0" },
    ) { config, value -> config.copy(tps = value) }
    val clientUpdateRate = nonNegativeInteger(
        "client-update-rate",
        500,
        ServerConfig::clientUpdateRate,
    ) { config, value -> config.copy(clientUpdateRate = value) }
    val compressionThreshold = integer(
        "compression-threshold",
        256,
        ServerConfig::compressionThreshold,
        validation = { if (it >= -1) null else "must be at least -1" },
    ) { config, value -> config.copy(compressionThreshold = value) }
    val runDirectory = file("run-dir", File("run"), ServerConfig::runDirectory) { config, value ->
        config.copy(runDirectory = value)
    }
    val autosaveSeconds = nonNegativeInteger(
        "autosave-seconds",
        300,
        ServerConfig::autosaveSeconds,
    ) { config, value -> config.copy(autosaveSeconds = value) }
    val viaversion = boolean("viaversion", false, ServerConfig::viaversion) { config, value ->
        config.copy(viaversion = value)
    }

    val options: List<ServerConfigOption<*>> = declared.toList()

    val byKey: Map<String, ServerConfigOption<*>> = options.associateBy { it.key }

    fun validate(config: ServerConfig, source: String): ServerConfig {
        for (option in options) option.validate(config, source)
        return config
    }

    private fun string(
        key: String,
        default: String,
        get: (ServerConfig) -> String,
        set: (ServerConfig, String) -> ServerConfig,
    ) = register(ServerConfigOption(key, default, { it }, "a string", { null }, get, set))

    private fun integer(
        key: String,
        default: Int,
        get: (ServerConfig) -> Int,
        validation: (Int) -> String? = { null },
        set: (ServerConfig, Int) -> ServerConfig,
    ) = register(ServerConfigOption(key, default, String::toIntOrNull, "an integer", validation, get, set))

    private fun nonNegativeInteger(
        key: String,
        default: Int,
        get: (ServerConfig) -> Int,
        set: (ServerConfig, Int) -> ServerConfig,
    ) = integer(
        key,
        default,
        get,
        validation = { if (it >= 0) null else "must be at least 0" },
        set = set,
    )

    private fun file(
        key: String,
        default: File,
        get: (ServerConfig) -> File,
        set: (ServerConfig, File) -> ServerConfig,
    ) = register(ServerConfigOption(key, default, ::File, "a path", { null }, get, set))

    private fun boolean(
        key: String,
        default: Boolean,
        get: (ServerConfig) -> Boolean,
        set: (ServerConfig, Boolean) -> ServerConfig,
    ) = register(
        ServerConfigOption(
            key,
            default,
            parse = { raw ->
                when (raw.lowercase()) {
                    "true", "yes", "on", "1" -> true
                    "false", "no", "off", "0" -> false
                    else -> null
                }
            },
            expected = "true or false",
            validator = { null },
            get = get,
            set = set,
            booleanFlag = true,
        )
    )

    private fun <T> register(option: ServerConfigOption<T>): ServerConfigOption<T> {
        declared += option
        return option
    }
}
