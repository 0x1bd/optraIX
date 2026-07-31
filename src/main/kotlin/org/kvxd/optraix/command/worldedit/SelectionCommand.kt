package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class SelectionCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/sel"

    override val aliases = listOf("/desel", "/deselect")

    override val description = "clear the current selection"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        for (alias in listOf(name) + aliases) {
            dispatcher.register(literal(alias).runs { clear(it.source) })
        }
    }

    private fun clear(source: CommandSource) {
        source.player.selectionOne = null
        source.player.selectionTwo = null
        source.reply("selection cleared")
    }
}
