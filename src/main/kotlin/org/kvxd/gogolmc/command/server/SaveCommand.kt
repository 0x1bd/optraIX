package org.kvxd.gogolmc.command.server

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class SaveCommand : GogolCommand {

    override val name = "save"

    override val description = "write the world and profiles to disk"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("save").runs { context ->
                val saved = context.source.server.saveWorld()
                context.source.success("saved $saved chunks")
            }
        )
    }
}
