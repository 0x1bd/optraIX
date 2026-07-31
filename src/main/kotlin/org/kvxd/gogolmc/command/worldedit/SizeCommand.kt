package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class SizeCommand(private val worldEdit: WorldEdit) : GogolCommand {

    override val name = "/size"

    override val description = "measure the current selection"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(literal("/size").runs { context ->
            val region = worldEdit.regionOf(context.source.player)
            if (region == null) {
                context.source.error("make a selection first")
            } else {
                context.source.reply(
                    "size ${region.sizeX}x${region.sizeY}x${region.sizeZ} = ${region.volume} blocks"
                )
            }
        })
    }
}
