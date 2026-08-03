package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs
import org.kvxd.optraix.redstone.optraix.OptraIxEngine

class PasteCommand(private val worldEdit: WorldEdit) : ServerCommand {

    override val name = "/paste"

    override val description = "paste the clipboard; -a includes clipboard air"

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
        val engine = source.server.engine as? OptraIxEngine
        if (engine?.manualCompileRequired == true) {
            source.reply("automatic compilation skipped for this bulk paste; run /optraix compile when ready")
        }
    }
}
