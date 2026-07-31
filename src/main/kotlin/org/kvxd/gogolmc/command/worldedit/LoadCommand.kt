package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.argument
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class LoadCommand(private val worldEdit: WorldEdit) : GogolCommand {

    override val name = "/load"

    override val description = "load a .schem into the clipboard"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("/load").then(
                argument("name", StringArgumentType.greedyString()).runs { context ->
                    Schematics.load(context.source, StringArgumentType.getString(context, "name"))
                }
            )
        )
    }
}
