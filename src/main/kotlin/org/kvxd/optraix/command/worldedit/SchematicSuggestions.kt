package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.suggestMatching
import java.io.File
import java.util.concurrent.CompletableFuture

object SchematicSuggestions {

    fun suggest(
        context: CommandContext<CommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val directory = context.source.server.config.schematicsDirectory
        if (!directory.isDirectory) return builder.buildFuture()

        val names = directory.listFiles()
            ?.asSequence()
            ?.filter(File::isFile)
            ?.map(File::getName)
            ?.filter(::isSchematicName)
            ?.sortedWith(String.CASE_INSENSITIVE_ORDER)
            ?.toList()
            .orEmpty()

        return builder.suggestMatching(names)
    }

    fun isSchematicName(name: String): Boolean =
        name.endsWith(".schem", ignoreCase = true) ||
                name.endsWith(".schematic", ignoreCase = true)
}