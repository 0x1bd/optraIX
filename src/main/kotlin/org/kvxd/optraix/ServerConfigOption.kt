package org.kvxd.optraix

internal class ServerConfigOption<T>(
    val key: String,
    val default: T,
    private val parse: (String) -> T?,
    private val expected: String,
    private val validator: (T) -> String?,
    private val get: (ServerConfig) -> T,
    private val set: (ServerConfig, T) -> ServerConfig,
    val booleanFlag: Boolean = false,
) {

    fun encoded(config: ServerConfig): String = get(config).let(::encode)

    fun apply(config: ServerConfig, raw: String, source: String): ServerConfig {
        val value = parse(raw)
            ?: throw IllegalArgumentException("$source: '$key' must be $expected, got '$raw'")
        validator(value)?.let { problem ->
            throw IllegalArgumentException("$source: '$key' $problem")
        }
        return set(config, value)
    }

    fun validate(config: ServerConfig, source: String) {
        validator(get(config))?.let { problem ->
            throw IllegalArgumentException("$source: '$key' $problem")
        }
    }

    private fun encode(value: T): String = when (value) {
        is java.io.File -> value.path
        else -> value.toString()
    }
}
