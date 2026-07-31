package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.argument
import org.kvxd.gogolmc.command.argument.BlockStateArgumentType
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs
import org.kvxd.gogolmc.worldedit.BlockMask

class CountCommand(private val worldEdit: WorldEdit) : GogolCommand {

    override val name = "/count"

    override val description = "count matching blocks in the selection"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("/count").then(
                argument("block", BlockStateArgumentType.blockState()).runs { context ->
                    count(context.source, BlockStateArgumentType.blockState(context, "block"))
                }
            )
        )
    }

    private fun count(source: CommandSource, state: Int) {
        val region = worldEdit.regionOf(source.player)
        if (region == null) {
            source.error("make a selection first")
            return
        }
        val mask = BlockMask.exact(state)
        var total = 0
        region.forEach { pos -> if (mask.matches(source.world.getBlock(pos))) total++ }
        source.reply("counted $total blocks")
    }
}
