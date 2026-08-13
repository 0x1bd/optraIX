package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class CutCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/cut"

    override val description = "copy the selection then clear it"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(literal("/cut").runs { context -> cut(context.source) })
    }

    private fun cut(source: CommandSource) {
        val region = worldEdit.regionOf(source.player)
        if (region == null) {
            source.error("make a selection first")
            return
        }
        val submission = worldEdit.submitCopy(source.player, region, true, source::reply) { outcome ->
            when (outcome) {
                is EditOutcome.Completed -> source.success("cut ${outcome.changed} blocks")
                is EditOutcome.Cancelled -> source.reply("cut cancelled; restored ${outcome.restored} blocks")
                is EditOutcome.Failed -> source.error("cut failed: ${outcome.message}")
            }
        }
        if (!submission.completed) source.reply("cut #${submission.jobId} started; use //cancel to roll it back")
    }
}
