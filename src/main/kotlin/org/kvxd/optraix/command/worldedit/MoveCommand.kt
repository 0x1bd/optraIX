package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.argument.DirectionArgumentType
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import org.kvxd.optraix.worldedit.Directions

class MoveCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/move"

    override val description = "shift the selection contents"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("/move").then(
                argument("count", IntegerArgumentType.integer(1))
                    .runs { run(it.source, IntegerArgumentType.getInteger(it, "count"), null) }
                    .then(
                        argument("direction", DirectionArgumentType.direction()).runs {
                            run(
                                it.source,
                                IntegerArgumentType.getInteger(it, "count"),
                                DirectionArgumentType.direction(it, "direction"),
                            )
                        }
                    )
            )
        )
    }

    private fun run(source: CommandSource, count: Int, direction: String?) {
        val region = worldEdit.regionOf(source.player)
        if (region == null) {
            source.error("make a selection first")
            return
        }
        val player = source.player
        val facing = if (direction == null) Directions.facing(player.yaw, player.pitch)
        else Directions.parse(direction, player.yaw, player.pitch)
        if (facing == null) {
            source.error("unknown direction: $direction")
            return
        }
        val submission = worldEdit.submitMove(player, region, count, facing, source::reply) { outcome ->
            when (outcome) {
                is EditOutcome.Completed -> source.success("moved ${outcome.changed} blocks")
                is EditOutcome.Cancelled -> source.reply("move cancelled; restored ${outcome.restored} blocks")
                is EditOutcome.Failed -> source.error("move failed: ${outcome.message}")
            }
        }
        if (!submission.completed) source.reply("move #${submission.jobId} started; use //cancel to roll it back")
    }
}
