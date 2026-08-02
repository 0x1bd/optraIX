package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class LoadCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/load"

    override val description = "load a .schem into the clipboard"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name).then(
                argument("name", StringArgumentType.greedyString())
                    .suggests(SchematicSuggestions::suggest)
                    .runs { context ->
                        Schematics.load(
                            context.source,
                            StringArgumentType.getString(context, "name"),
                        )
                    }
            )
        )
    }
}