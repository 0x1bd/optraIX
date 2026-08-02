package org.kvxd.optraix.command.server

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import org.kvxd.optraix.command.suggestMatching

class TpsCommand : ServerCommand {

    override val name = "tps"

    override val description = "show current and target tps"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name)
                .runs { info(it.source) }
                .then(literal("info").runs { info(it.source) })
                .then(literal("unlimited").runs { target(it.source, 0) })
                .then(
                    argument("rate", IntegerArgumentType.integer(1))
                        .suggests { _, builder -> builder.suggestMatching(TpsSuggestions) }
                        .runs { target(it.source, IntegerArgumentType.getInteger(it, "rate")) }
                )
        )
    }

    private fun info(source: CommandSource) {
        val server = source.server
        source.heading("tps")
        source.reply("  current: ${format(server.measuredTps)}")
        source.reply("  target:  ${server.tpsLabel()}")
        source.reply("  mspt:    ${format(server.averageMspt)}")
    }

    private fun target(source: CommandSource, rate: Int) {
        source.server.targetTps = rate
        source.server.broadcastMessage(
            "target tps set to ${source.server.tpsLabel()} by ${source.player.name}"
        )
    }

    private fun format(value: Double) = String.format("%.2f", value)

    companion object {
        private val TpsSuggestions = listOf("20", "60", "120", "240", "1000")
    }
}