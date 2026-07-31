package org.kvxd.gogolmc.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs

class PasteCommand(private val worldEdit: WorldEdit) : GogolCommand {

    override val name = "/paste"

    override val description = "paste the clipboard, -a keeps air"

    override fun register(dispatcher: CommandDispatcher<CommandSource>) {
        dispatcher.register(
            literal(name)
                .runs { paste(it.source, includeAir = false) }
                .then(literal("-a").runs { paste(it.source, includeAir = true) })
        )
    }

    private fun paste(source: CommandSource, includeAir: Boolean) {
        val clipboard = source.player.clipboard
        if (clipboard == null) {
            source.error("clipboard is empty")
            return
        }
        val changed = worldEdit.paste(source.player, clipboard, includeAir)
        source.success("pasted $changed blocks")
    }
}
