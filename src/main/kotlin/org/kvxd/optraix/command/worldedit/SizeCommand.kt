package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class SizeCommand(private val worldEdit: WorldEdit) : ServerCommand {

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
