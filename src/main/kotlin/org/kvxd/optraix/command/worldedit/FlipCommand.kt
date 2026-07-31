package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.block.property.BlockFacing
import org.kvxd.optraix.block.property.FlipDirection
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.argument.DirectionArgumentType
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import org.kvxd.optraix.worldedit.Directions

class FlipCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/flip"

    override val description = "mirror the clipboard horizontally"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("/flip")
                .runs { flip(it.source, null) }
                .then(
                    argument("direction", DirectionArgumentType.direction()).runs {
                        flip(it.source, DirectionArgumentType.direction(it, "direction"))
                    }
                )
        )
    }

    private fun flip(source: CommandSource, direction: String?) {
        val clipboard = source.player.clipboard
        if (clipboard == null) {
            source.error("clipboard is empty")
            return
        }
        val player = source.player
        val facing = if (direction == null) Directions.facing(player.yaw, player.pitch)
        else Directions.parse(direction, player.yaw, player.pitch)
        val axis = when (facing) {
            BlockFacing.East, BlockFacing.West -> FlipDirection.FlipX
            BlockFacing.North, BlockFacing.South -> FlipDirection.FlipZ
            else -> {
                source.error("can only flip horizontally")
                return
            }
        }
        player.clipboard = clipboard.flip(axis)
        source.success("clipboard flipped")
    }
}
