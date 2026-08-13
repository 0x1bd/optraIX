package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.argument.BlockStateArgumentType
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import org.kvxd.optraix.worldedit.BlockMask

class CountCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/count"

    override val description = "count matching blocks in the selection"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("/count").then(
                argument("block", BlockStateArgumentType.blockState()).runs { context ->
                    count(context.source, BlockStateArgumentType.blockState(context, "block"))
                }
            )
        )
    }

    private fun count(source: CommandSource, state: Int) {
        val region = worldEdit.regionOf(source.player)
        if (region == null) {
            source.error("make a selection first")
            return
        }
        val mask = BlockMask.exact(state)
        val submission = worldEdit.submitCount(source.player, region, mask::matches, source::reply) { outcome ->
            when (outcome) {
                is EditOutcome.Completed -> source.reply("counted ${outcome.changed} blocks")
                is EditOutcome.Cancelled -> source.reply("count cancelled")
                is EditOutcome.Failed -> source.error("count failed: ${outcome.message}")
            }
        }
        if (!submission.completed) source.reply("count #${submission.jobId} started; use //cancel to stop it")
    }
}
