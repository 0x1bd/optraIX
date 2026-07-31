package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.argument
import org.kvxd.gogolmc.command.argument.DirectionArgumentType
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class ShiftCommand(private val worldEdit: WorldEdit) : GogolCommand {

    override val name = "/shift"

    override val description = "move the selection box only"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("/shift").then(
                argument("amount", IntegerArgumentType.integer(1))
                    .runs { run(it.source, IntegerArgumentType.getInteger(it, "amount"), null) }
                    .then(
                        argument("direction", DirectionArgumentType.direction()).runs {
                            run(
                                it.source,
                                IntegerArgumentType.getInteger(it, "amount"),
                                DirectionArgumentType.direction(it, "direction"),
                            )
                        }
                    )
            )
        )
    }

    private fun run(source: CommandSource, amount: Int, direction: String?) {
        SelectionResize.shift(source, worldEdit, amount, direction)
    }
}
