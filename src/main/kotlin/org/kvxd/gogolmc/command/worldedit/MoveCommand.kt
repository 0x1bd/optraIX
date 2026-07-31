package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import org.kvxd.gogolmc.block.property.BlockFacing
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.argument
import org.kvxd.gogolmc.command.argument.DirectionArgumentType
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs
import org.kvxd.gogolmc.worldedit.Directions

class MoveCommand(private val worldEdit: WorldEdit) : GogolCommand {

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
        source.success("moved ${worldEdit.move(player, region, count, facing)} blocks")
    }
}
