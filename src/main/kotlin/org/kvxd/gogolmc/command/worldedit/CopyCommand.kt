package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class CopyCommand(private val worldEdit: WorldEdit) : GogolCommand {

    override val name = "/copy"

    override val description = "copy the selection to the clipboard"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(literal("/copy").runs { context -> copy(context.source) })
    }

    private fun copy(source: CommandSource) {
        val region = worldEdit.regionOf(source.player)
        if (region == null) {
            source.error("make a selection first")
            return
        }
        worldEdit.copy(source.player, region)
        source.success("copied ${region.volume} blocks")
    }
}
