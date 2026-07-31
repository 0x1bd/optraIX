package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class SelectionCommand(private val worldEdit: WorldEdit) : GogolCommand {

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
