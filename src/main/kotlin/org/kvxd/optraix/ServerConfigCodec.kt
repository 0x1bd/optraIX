package org.kvxd.optraix

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Properties

object ServerConfigCodec {

    fun read(file: File): ServerConfig {
        val properties = Properties()
        file.reader(StandardCharsets.UTF_8).use(properties::load)
        var config = ServerConfig()
        for (option in ServerConfigSchema.options) {
            val raw = properties.getProperty(option.key) ?: continue
            config = option.apply(config, raw, file.path)
        }
        return ServerConfigSchema.validate(config, file.path)
    }

    fun write(config: ServerConfig, file: File) {
        ServerConfigSchema.validate(config, file.path)
        file.parentFile?.mkdirs()
        file.writeText(
            buildString {
                for (option in ServerConfigSchema.options) {
                    append(option.key)
                    append('=')
                    append(escapeProperty(option.encoded(config)))
                    append('\n')
                }
            },
            StandardCharsets.UTF_8,
        )
    }

    fun applyArguments(config: ServerConfig, args: Array<String>): ServerConfig {
        var result = config
        var index = 0
        while (index < args.size) {
            val argument = args[index]
            require(argument.startsWith("--")) { "Unknown argument '$argument'" }
            val token = argument.removePrefix("--")
            val separator = token.indexOf('=')
            var key = if (separator < 0) token else token.substring(0, separator)
            val inlineValue = if (separator < 0) null else token.substring(separator + 1)
            val negated = key.startsWith("no-")
            if (negated) key = key.removePrefix("no-")
            val option = ServerConfigSchema.byKey[key]
                ?: throw IllegalArgumentException("Unknown argument '$argument'")
            val nextArgument = args.getOrNull(index + 1)

            val raw = when {
                negated -> {
                    require(option.booleanFlag && inlineValue == null) { "Unknown argument '$argument'" }
                    "false"
                }
                inlineValue != null -> inlineValue
                option.booleanFlag && (nextArgument == null || nextArgument.startsWith("--")) -> "true"
                else -> args.getOrNull(++index)
                    ?: throw IllegalArgumentException("Missing value for --$key")
            }
            result = option.apply(result, raw, "command line")
            index++
        }
        return ServerConfigSchema.validate(result, "command line")
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
