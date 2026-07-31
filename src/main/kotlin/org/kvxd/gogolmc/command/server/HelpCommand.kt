package org.kvxd.gogolmc.command.server

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.CommandUsage
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs
import org.kvxd.gogolmc.net.ChatFont
import org.kvxd.gogolmc.net.Text

class HelpCommand(private val catalogue: () -> List<GogolCommand>) : GogolCommand {

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
        source.heading("gogolmc commands")
        for ((usage, text) in rows) source.reply(Text.columns(usage, text, target))
        source.reply("wooden axe: left click sets pos1, right click sets pos2")
    }

    private companion object {
        const val Gutter = 12
    }
}
