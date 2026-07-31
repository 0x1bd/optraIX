package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class RedoCommand(private val worldEdit: WorldEdit) : GogolCommand {

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
