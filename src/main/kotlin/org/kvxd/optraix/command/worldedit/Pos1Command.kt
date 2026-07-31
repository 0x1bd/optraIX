package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class Pos1Command(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/pos1"

    override val aliases = listOf("/1", "/hpos1")

    override val description = "set the first selection corner to where you stand"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        for (alias in listOf(name) + aliases) {
            dispatcher.register(literal(alias).runs { set(it.source) })
        }
    }

    private fun set(source: CommandSource) =
        worldEdit.setPositionOne(source.player, source.player.blockPos)
}
