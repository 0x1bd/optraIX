package org.kvxd.optraix.command.server

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class StopCommand : ServerCommand {

    override val name = "stop"

    override val description = "save and stop the server"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name).runs { context ->
                val source = context.source
                source.success("stopping server...")
                source.server.requestStop()
            }
        )
    }
}
