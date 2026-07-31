package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.argument
import org.kvxd.gogolmc.command.argument.BlockStateArgumentType
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class SetCommand(private val worldEdit: WorldEdit) : GogolCommand {

    override val name = "/set"

    override val description = "fill the selection"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("/set").then(
                argument("block", BlockStateArgumentType.blockState()).runs { context ->
                    fill(context.source, BlockStateArgumentType.blockState(context, "block"))
                }
            )
        )
    }

    private fun fill(source: CommandSource, state: Int) {
        val region = worldEdit.regionOf(source.player)
        if (region == null) {
            source.error("make a selection first")
            return
        }
        val changed = worldEdit.apply(source.player, region) { state }
        source.success("$changed blocks changed")
    }
}
