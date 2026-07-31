package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class WandCommand : GogolCommand {

    override val name = "/wand"

    override val description = "explain the wand and toggle the selection outline"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal("/wand")
                .runs { explain(it.source) }
                .then(literal("show").runs { toggle(it.source, true) })
                .then(literal("hide").runs { toggle(it.source, false) })
        )
    }

    private fun explain(source: CommandSource) {
        source.reply("hold a wooden axe: left click sets pos1, right click sets pos2")
        source.reply("//wand show and //wand hide control the selection outline")
    }

    private fun toggle(source: CommandSource, show: Boolean) {
        source.player.showSelection = show
        source.success(if (show) "selection outline shown" else "selection outline hidden")
    }
}
