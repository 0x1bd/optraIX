package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import java.util.Locale
import java.util.concurrent.CompletableFuture

class LoadCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/load"

    override val description = "load a .schem into the clipboard"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name).then(
                argument("name", StringArgumentType.greedyString())
                    .suggests { context, builder -> suggestSchematics(context, builder) }
                    .runs { context ->
                        Schematics.load(
                            context.source,
                            StringArgumentType.getString(context, "name"),
                        )
                    }
            )
        )
    }

    private fun suggestSchematics(
        context: CommandContext<CommandSource>,
        builder: SuggestionsBuilder,
    ): CompletableFuture<Suggestions> {
        val remaining = builder.remaining.lowercase(Locale.ROOT)
        val directory = context.source.server.config.schematicsDirectory

        directory.listFiles { file ->
            file.isFile && (
                    file.extension.equals("schem", ignoreCase = true) ||
                            file.extension.equals("schematic", ignoreCase = true)
                    )
        }
            ?.asSequence()
            ?.map { it.name }
            ?.filter { it.lowercase(Locale.ROOT).startsWith(remaining) }
            ?.sortedWith(String.CASE_INSENSITIVE_ORDER)
            ?.forEach(builder::suggest)

        return builder.buildFuture()
    }
}
