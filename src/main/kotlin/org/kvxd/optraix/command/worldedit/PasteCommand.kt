package org.kvxd.optraix.command.worldedit

import com.mojang.brigadier.CommandDispatcher
import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.command.ServerCommand
import org.kvxd.optraix.command.literal
import org.kvxd.optraix.command.runs

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
        val submission = worldEdit.submitPaste(
            source.player,
            clipboard,
            includeAir,
            source::reply,
        ) { outcome ->
            when (outcome) {
                is EditOutcome.Completed -> source.success("pasted ${outcome.changed} blocks")
                is EditOutcome.Cancelled -> source.reply("paste cancelled; restored ${outcome.restored} blocks")
                is EditOutcome.Failed -> source.error("paste failed: ${outcome.message}")
            }
        }
        if (!submission.completed) source.reply("paste #${submission.jobId} started; use //cancel to roll it back")
    }
}
