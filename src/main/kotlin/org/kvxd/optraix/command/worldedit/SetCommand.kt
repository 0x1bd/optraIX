package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.argument.BlockStateArgumentType
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class SetCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/set"

    override val description = "fill the selection"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("/set").then(
                argument("block", BlockStateArgumentType.blockState()).runs { context ->
                    fill(context.source, BlockStateArgumentType.blockState(context, "block"))
                }
            )
        )
    }

    private fun fill(source: CommandSource, state: Int) {
        val region = worldEdit.regionOf(source.player)
        if (region == null) {
            source.error("make a selection first")
            return
        }
        val submission = worldEdit.submitApply(source.player, region, "set", { state }, source::reply) { outcome ->
            when (outcome) {
                is EditOutcome.Completed -> source.success("${outcome.changed} blocks changed")
                is EditOutcome.Cancelled -> source.reply("set cancelled; restored ${outcome.restored} blocks")
                is EditOutcome.Failed -> source.error("set failed: ${outcome.message}")
            }
        }
        if (!submission.completed) source.reply("set #${submission.jobId} started; use //cancel to roll it back")
    }
}
