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

class StackCommand(private val worldEdit: WorldEdit) : GogolCommand {

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
