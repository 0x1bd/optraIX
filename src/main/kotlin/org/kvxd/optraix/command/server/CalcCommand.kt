package org.kvxd.optraix.command.server

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.StringArgumentType
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import java.util.Locale

class CalcCommand : ServerCommand {

    override val name = "calc"

    override val aliases = listOf("c")

    override val description = "evaluate decimal, hex, binary, bus, and boolean expressions"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        for (command in listOf(name) + aliases) {
            dispatcher.register(
                literal(command).then(
                    argument("expression", StringArgumentType.greedyString())
                        .suggests { _, builder ->
                            val completion = Calculator.completions(builder.remaining)
                            val partial = builder.remaining.substring(completion.replaceFrom)
                            builder.createOffset(builder.start + completion.replaceFrom).apply {
                                completion.candidates
                                    .filter { it.lowercase(Locale.ROOT).startsWith(partial.lowercase(Locale.ROOT)) }
                                    .forEach(::suggest)
                            }.buildFuture()
                        }
                        .runs { calculate(it.source, StringArgumentType.getString(it, "expression")) }
                )
            )
        }
    }

    private fun calculate(source: CommandSource, expression: String) {
        try {
            Calculator.format(Calculator.evaluate(expression)).forEach(source::reply)
        } catch (cause: Calculator.CalculatorException) {
            source.error("calc: ${cause.message}")
        }
    }
}
