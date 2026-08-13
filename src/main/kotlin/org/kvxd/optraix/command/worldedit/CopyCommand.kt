package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class CopyCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/copy"

    override val description = "copy the selection to the clipboard"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(literal("/copy").runs { context -> copy(context.source) })
    }

    private fun copy(source: CommandSource) {
        val region = worldEdit.regionOf(source.player)
        if (region == null) {
            source.error("make a selection first")
            return
        }
        val submission = worldEdit.submitCopy(source.player, region, false, source::reply) { outcome ->
            when (outcome) {
                is EditOutcome.Completed -> source.success("copied ${outcome.changed} blocks")
                is EditOutcome.Cancelled -> source.reply("copy cancelled")
                is EditOutcome.Failed -> source.error("copy failed: ${outcome.message}")
            }
        }
        if (!submission.completed) source.reply("copy #${submission.jobId} started; use //cancel to stop it")
    }
}
