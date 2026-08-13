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
            val source = context.source
            val submission = worldEdit.submitHistory(source.player, false, source::reply) { outcome ->
                when (outcome) {
                    is EditOutcome.Completed -> source.success("undid ${outcome.changed} blocks")
                    is EditOutcome.Cancelled -> source.reply("undo cancelled")
                    is EditOutcome.Failed -> source.error("undo failed: ${outcome.message}")
                }
            }
            if (submission == null) source.reply("nothing to undo")
            else if (!submission.completed) source.reply("undo #${submission.jobId} started")
        })
    }
}
