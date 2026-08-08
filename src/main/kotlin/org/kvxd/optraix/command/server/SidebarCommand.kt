package org.kvxd.optraix.command.server

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class SidebarCommand : ServerCommand {

    override val name = "sidebar"

    override val aliases = listOf("sc", "board")

    override val description = "show or hide the sidebar"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        for (alias in listOf(name) + aliases) {
            dispatcher.register(
                literal(alias)
                    .runs { status(it.source) }
                    .then(literal("show").runs { setVisible(it.source, true) })
                    .then(literal("hide").runs { setVisible(it.source, false) })
            )
        }
    }

    private fun status(source: CommandSource) {
        source.reply("sidebar is ${if (source.player.showSidebar) "shown" else "hidden"}")
    }

    private fun setVisible(source: CommandSource, visible: Boolean) {
        source.server.setSidebarVisible(source.player, visible)
        source.success("sidebar ${if (visible) "shown" else "hidden"}")
    }
}
