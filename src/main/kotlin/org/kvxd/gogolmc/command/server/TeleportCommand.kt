package org.kvxd.gogolmc.command.server

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.DoubleArgumentType
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.argument
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class TeleportCommand : GogolCommand {

    override val name = "tp"

    override val description = "teleport to a position"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("tp").then(
                argument("x", DoubleArgumentType.doubleArg()).then(
                    argument("y", DoubleArgumentType.doubleArg()).then(
                        argument("z", DoubleArgumentType.doubleArg()).runs { context ->
                            move(
                                context.source,
                                DoubleArgumentType.getDouble(context, "x"),
                                DoubleArgumentType.getDouble(context, "y"),
                                DoubleArgumentType.getDouble(context, "z"),
                            )
                        }
                    )
                )
            )
        )
    }

    private fun move(source: CommandSource, x: Double, y: Double, z: Double) {
        val player = source.player
        player.x = x
        player.y = y
        player.z = z
        player.moved = true
        source.server.sendPosition(player)
        source.server.updateChunks(player, force = true)
        source.success("teleported to $x $y $z")
    }
}
