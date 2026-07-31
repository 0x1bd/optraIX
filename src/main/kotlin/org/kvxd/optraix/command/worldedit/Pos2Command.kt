package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class Pos2Command(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/pos2"

    override val aliases = listOf("/2", "/hpos2")

    override val description = "set the second selection corner to where you stand"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        for (alias in listOf(name) + aliases) {
            dispatcher.register(literal(alias).runs { set(it.source) })
        }
    }

    private fun set(source: CommandSource) =
        worldEdit.setPositionTwo(source.player, source.player.blockPos)
}
