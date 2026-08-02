package org.kvxd.optraix.command.server

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.FloatArgumentType
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import org.kvxd.optraix.command.suggestMatching

class SpeedCommand : ServerCommand {

    override val name = "speed"

    override val aliases = listOf("s")

    override val description = "movement speed, 0.1 to 100"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        for (alias in listOf(name) + aliases) {
            dispatcher.register(
                literal(alias)
                    .runs { show(it.source) }
                    .then(
                        argument("multiplier", FloatArgumentType.floatArg(0.1f, 100.0f))
                            .suggests { _, builder -> builder.suggestMatching(SpeedSuggestions) }
                            .runs {
                                apply(
                                    it.source,
                                    FloatArgumentType.getFloat(it, "multiplier"),
                                )
                            }
                    )
            )
        }
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

    companion object {
        private val SpeedSuggestions = listOf("0.5", "1", "2", "5", "10", "20", "50", "100")
    }
}