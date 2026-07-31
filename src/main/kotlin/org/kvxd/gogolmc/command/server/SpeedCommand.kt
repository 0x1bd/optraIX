package org.kvxd.gogolmc.command.server

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.argument
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class SpeedCommand : GogolCommand {

    override val name = "speed"

    override val aliases = listOf("s")

    override val description = "movement speed, 0.1 to 100"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        val node = dispatcher.register(
            literal("speed")
                .runs { show(it.source) }
                .then(
                    argument("multiplier", FloatArgumentType.floatArg(0.1f, 100.0f))
                        .runs { apply(it.source, FloatArgumentType.getFloat(it, "multiplier")) }
                )
        )
        dispatcher.register(
            literal("s")
                .runs { show(it.source) }
                .then(
                    argument("multiplier", FloatArgumentType.floatArg(0.1f, 100.0f))
                        .runs { apply(it.source, FloatArgumentType.getFloat(it, "multiplier")) }
                )
        )
        check(node.name == "speed")
    }

    private fun show(source: CommandSource) {
        source.reply("speed is ${format(source.player.speedMultiplier)}x")
    }

    private fun apply(source: CommandSource, multiplier: Float) {
        source.player.speedMultiplier = multiplier
        source.server.sendAbilities(source.player)
        source.success("speed set to ${format(multiplier)}x")
    }

    private fun format(value: Float) = String.format("%.2f", value)
}
