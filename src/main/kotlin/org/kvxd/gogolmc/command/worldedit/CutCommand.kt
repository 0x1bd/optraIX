package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class CutCommand(private val worldEdit: WorldEdit) : GogolCommand {

    override val name = "/cut"

    override val description = "copy the selection then clear it"


    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(literal("/cut").runs { context -> cut(context.source) })
    }

    private fun cut(source: CommandSource) {
        val region = worldEdit.regionOf(source.player)
        if (region == null) {
            source.error("make a selection first")
            return
        }
        worldEdit.copy(source.player, region)
        val air = Blocks.airState
        val changed = worldEdit.apply(source.player, region) { air }
        source.success("cut $changed blocks")
    }
}
