package org.kvxd.optraix.command.argument

import com.mojang.brigadier.StringReader
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import java.util.concurrent.CompletableFuture
import org.kvxd.optraix.block.mcData

class BlockStateArgumentType private constructor() : ArgumentType<Int> {

    override fun parse(reader: StringReader): Int {
        val start = reader.cursor
        while (reader.canRead() && reader.peek() != ' ') reader.skip()
        val text = reader.string.substring(start, reader.cursor)
        return mcData.blockState(text) ?: throw UnknownBlock.createWithContext(reader)
    }

    override fun <S> listSuggestions(
        context: CommandContext<S>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val remaining = builder.remaining.lowercase()
        for (type in mcData.blocks) {
            if (type.name.startsWith(remaining)) builder.suggest(type.name)
            else if ("minecraft:${type.name}".startsWith(remaining)) builder.suggest("minecraft:${type.name}")
        }
        return builder.buildFuture()
    }

    override fun getExamples(): Collection<String> =
        listOf("stone", "sandstone", "redstone_wire", "repeater[delay=2,facing=east]")

    companion object {

        private val UnknownBlock = SimpleCommandExceptionType { "unknown block" }

        fun blockState(): BlockStateArgumentType = BlockStateArgumentType()

        fun blockState(context: CommandContext<*>, name: String): Int =
            context.getArgument(name, Int::class.javaObjectType)
    }
}
