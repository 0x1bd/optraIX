package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.block.property.BlockFacing
import org.kvxd.gogolmc.block.property.FlipDirection
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.argument
import org.kvxd.gogolmc.command.argument.DirectionArgumentType
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs
import org.kvxd.gogolmc.worldedit.Directions

class FlipCommand(private val worldEdit: WorldEdit) : GogolCommand {

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
