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
                val source = context.source
                source.reply("saving...")
                source.server.submit {
                    val saved = source.server.saveWorld()
                    source.success("saved $saved chunks")
                }
            }
        )
    }
}
