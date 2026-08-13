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
            val source = context.source
            val submission = worldEdit.submitHistory(source.player, true, source::reply) { outcome ->
                when (outcome) {
                    is EditOutcome.Completed -> source.success("redid ${outcome.changed} blocks")
                    is EditOutcome.Cancelled -> source.reply("redo cancelled")
                    is EditOutcome.Failed -> source.error("redo failed: ${outcome.message}")
                }
            }
            if (submission == null) source.reply("nothing to redo")
            else if (!submission.completed) source.reply("redo #${submission.jobId} started")
        })
    }
}
