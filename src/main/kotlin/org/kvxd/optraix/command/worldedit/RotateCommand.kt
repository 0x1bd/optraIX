package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import org.kvxd.optraix.block.property.RotateAmount
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.argument
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class RotateCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/rotate"

    override val description = "turn the clipboard"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("/rotate").then(
                argument("degrees", IntegerArgumentType.integer()).runs { context ->
                    rotate(context.source, IntegerArgumentType.getInteger(context, "degrees"))
                }
            )
        )
    }

    private fun rotate(source: CommandSource, degrees: Int) {
        val clipboard = source.player.clipboard
        if (clipboard == null) {
            source.error("clipboard is empty")
            return
        }
        val amount = when (Math.floorMod(degrees, 360)) {
            90 -> RotateAmount.Rotate90
            180 -> RotateAmount.Rotate180
            270 -> RotateAmount.Rotate270
            else -> {
                source.error("rotation must be 90, 180 or 270")
                return
            }
        }
        source.player.clipboard = clipboard.rotate(amount)
        source.success("clipboard rotated $degrees degrees")
    }
}
