package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class UndoCommand(private val worldEdit: WorldEdit) : ServerCommand {

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
