package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import org.kvxd.optraix.block.property.BlockFacing
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.argument.DirectionArgumentType
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import org.kvxd.optraix.worldedit.Directions

class StackCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/stack"

    override val description = "repeat the selection along a direction"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("/stack").then(
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
        val facing = resolve(source, direction) ?: return
        source.success("stacked ${worldEdit.stack(source.player, region, count, facing)} blocks")
    }

    private fun resolve(source: CommandSource, direction: String?): BlockFacing? {
        val player = source.player
        if (direction == null) return Directions.facing(player.yaw, player.pitch)
        val facing = Directions.parse(direction, player.yaw, player.pitch)
        if (facing == null) source.error("unknown direction: $direction")
        return facing
    }
}
