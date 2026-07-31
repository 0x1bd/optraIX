package org.kvxd.optraix.command.argument

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.concurrent.CompletableFuture

class DirectionArgumentType private constructor() : ArgumentType<String> {

    override fun parse(reader: StringReader): String {
        val start = reader.cursor
        while (reader.canRead() && reader.peek() != ' ') reader.skip()
        val text = reader.string.substring(start, reader.cursor).lowercase()
        if (text !in Names) throw UnknownDirection.createWithContext(reader)
        return text
    }

    override fun <S> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val remaining = builder.remaining.lowercase()
        for (name in Names) if (name.startsWith(remaining)) builder.suggest(name)
        return builder.buildFuture()
    }

    override fun getExamples(): Collection<String> = listOf("north", "up", "me")

    companion object {

        private val Names = listOf(
            "north", "south", "east", "west", "up", "down",
            "n", "s", "e", "w", "u", "d", "me", "forward", "back",
        )

        private val UnknownDirection = SimpleCommandExceptionType { "unknown direction" }

        fun direction(): DirectionArgumentType = DirectionArgumentType()

        fun direction(context: CommandContext<*>, name: String): String =
            context.getArgument(name, String::class.java)
    }
}
