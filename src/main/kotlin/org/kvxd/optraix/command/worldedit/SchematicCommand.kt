package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class SchematicCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/schem"

    override val aliases = listOf("/schematic")

    override val description = "list the schematics folder"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        for (alias in listOf(name) + aliases) {
            dispatcher.register(
                literal(alias)
                    .then(literal("list").runs { list(it.source) })
                    .then(
                        literal("load").then(
                            argument("name", StringArgumentType.greedyString()).runs { context ->
                                Schematics.load(
                                    context.source,
                                    StringArgumentType.getString(context, "name"),
                                )
                            }
                        )
                    )
            )
        }
    }

    private fun list(source: CommandSource) {
        val directory = source.server.config.schematicsDirectory
        val files = directory.listFiles { file ->
            file.extension == "schem" || file.extension == "schematic"
        }
        if (files == null || files.isEmpty()) source.reply("no schematics in ${directory.path}")
        else source.reply("schematics: " + files.joinToString { it.name })
    }
}
