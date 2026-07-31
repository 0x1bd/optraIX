package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

class CutCommand(private val worldEdit: WorldEdit) : ServerCommand {

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
