package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class UndoCommand(private val worldEdit: WorldEdit) : GogolCommand {

    override val name = "/undo"

    override val description = "revert the last edit"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(literal("/undo").runs { context ->
            val count = worldEdit.undo(context.source.player)
            if (count == null) context.source.reply("nothing to undo")
            else context.source.success("undid $count blocks")
        })
    }
}
