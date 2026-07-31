package org.kvxd.optraix.command.server

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.CommandUsage
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import org.kvxd.optraix.net.ChatFont
import org.kvxd.optraix.net.Text

class HelpCommand(private val catalogue: () -> List<ServerCommand>) : ServerCommand {

    override val name = "help"

    override val description = "list every command"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(literal(name).runs { context -> print(context.source, dispatcher) })
    }

    private fun print(source: CommandSource, dispatcher: CommandDispatcher<CommandSource>) {
        val rows = ArrayList<Pair<String, String>>()

        for (command in catalogue().sortedBy { command -> command.name.removePrefix("/") }) {
            val node = dispatcher.root.getChild(command.name) ?: continue
            val usage = "/" + CommandUsage.render(node)
            val aliases = command.aliases.joinToString(" ") { "/$it" }
            rows += usage to
                if (aliases.isEmpty()) command.description else "${command.description}  ($aliases)"
        }

        val target = (rows.maxOfOrNull { ChatFont.width(it.first) } ?: 0) + Gutter
        source.heading("optraix commands")
        for ((usage, text) in rows) source.reply(Text.columns(usage, text, target))
        source.reply("wooden axe: left click sets pos1, right click sets pos2")
    }

    private companion object {
        const val Gutter = 12
    }
}
