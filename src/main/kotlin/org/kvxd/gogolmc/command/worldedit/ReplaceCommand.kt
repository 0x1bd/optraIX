package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.argument
import org.kvxd.gogolmc.command.argument.BlockStateArgumentType
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs
import org.kvxd.gogolmc.worldedit.BlockMask

class ReplaceCommand(private val worldEdit: WorldEdit) : GogolCommand {

    override val name = "/replace"

    override val description = "swap one block for another"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("/replace").then(
                argument("from", BlockStateArgumentType.blockState()).then(
                    argument("to", BlockStateArgumentType.blockState()).runs { context ->
                        replace(
                            context.source,
                            BlockStateArgumentType.blockState(context, "from"),
                            BlockStateArgumentType.blockState(context, "to"),
                        )
                    }
                )
            )
        )
    }

    private fun replace(source: CommandSource, from: Int, to: Int) {
        val region = worldEdit.regionOf(source.player)
        if (region == null) {
            source.error("make a selection first")
            return
        }
        val mask = BlockMask.exact(from)
        val changed = worldEdit.apply(source.player, region) { pos ->
            if (mask.matches(source.world.getBlock(pos))) to else null
        }
        source.success("$changed blocks replaced")
    }
}
