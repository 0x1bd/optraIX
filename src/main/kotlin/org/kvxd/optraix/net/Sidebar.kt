package org.kvxd.optraix.net

import net.lenni0451.mcstructs.nbt.NbtTag
import net.lenni0451.mcstructs.nbt.tags.StringTag
import org.kvxd.optraix.nbt.compoundOf
import org.kvxd.optraix.nbt.listOfTags
import org.kvxd.optraix.player.Player
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundResetScorePacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundScoreboardDisplayObjectivePacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundScoreboardObjectivePacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundScoreboardScorePacket

class Sidebar(private val title: String = "optraix") {

    private var lines: List<Line> = emptyList()

    class Line(val key: String, val label: String, val value: String, val color: String)

    fun install(player: Player) {
        player.connection.send(
            ClientboundScoreboardObjectivePacket(
                name = Objective,
                action = ActionCreate,
                displayText = Text.bold(title, Text.Gold),
                type = TypeInteger,
                number_format = FormatBlank,
                styling = null,
            )
        )
        for ((index, line) in lines.withIndex()) player.connection.send(scorePacket(index, line))
        setVisible(player, player.showSidebar)
    }

    fun setVisible(player: Player, visible: Boolean) {
        player.connection.send(
            ClientboundScoreboardDisplayObjectivePacket(
                position = SlotSidebar,
                name = if (visible) Objective else "",
            )
        )
    }

    fun update(players: List<Player>, next: List<Line>) {
        if (players.isEmpty()) {
            lines = next
            return
        }
        val previous = lines
        for ((index, line) in next.withIndex()) {
            val old = previous.getOrNull(index)
            if (old != null && old.key == line.key && old.value == line.value && old.color == line.color) continue
            val packet = scorePacket(index, line)
            for (player in players) player.connection.send(packet)
        }
        for (index in next.size until previous.size) {
            val packet = ClientboundResetScorePacket(entityOf(index), Objective)
            for (player in players) player.connection.send(packet)
        }
        lines = next
    }

    private fun scorePacket(index: Int, line: Line): ClientboundScoreboardScorePacket =
        ClientboundScoreboardScorePacket(
            itemName = entityOf(index),
            scoreName = Objective,
            value = MaxLines - index,
            display_name = render(line),
            number_format = FormatBlank,
            styling = null,
        )

    private fun render(line: Line): NbtTag {
        val parts = listOf(
            compoundOf("text" to StringTag(line.label), "color" to StringTag(Text.Gray)),
            compoundOf("text" to StringTag(line.value), "color" to StringTag(line.color)),
        )
        return compoundOf("text" to StringTag(""), "extra" to listOfTags(parts))
    }

    private fun entityOf(index: Int): String = "optraix.$index"

    companion object {
        const val Objective = "optraix"
        const val MaxLines = 15
        private const val ActionCreate: Byte = 0
        private const val SlotSidebar = 1
        private const val TypeInteger = 0
        private const val FormatBlank = 0
    }
}
