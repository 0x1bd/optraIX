package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class RedoCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/redo"

    override val description = "reapply the last undone edit"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(literal("/redo").runs { context ->
            val count = worldEdit.redo(context.source.player)
            if (count == null) context.source.reply("nothing to redo")
            else context.source.success("redid $count blocks")
        })
    }
}
