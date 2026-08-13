package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class CancelCommand(private val worldEdit: WorldEdit) : ServerCommand {
    override val name = "/cancel"

    override val description = "cancel the active or queued WorldEdit job"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(literal(name).runs { context ->
            if (worldEdit.cancel(context.source.player)) context.source.reply("cancelling WorldEdit job")
            else context.source.reply("no WorldEdit job to cancel")
        })
    }
}
