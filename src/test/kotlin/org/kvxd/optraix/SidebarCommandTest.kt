package org.kvxd.optraix

import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.net.Sidebar
import org.kvxd.optraix.player.Player
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundScoreboardDisplayObjectivePacket
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SidebarCommandTest {

    private fun fixture(): Triple<OptraIxServer, Player, RecordingSink> {
        val server = OptraIxServer(ServerConfig(port = 0, runDirectory = File("build/tmp/sidebar-command-test")))
        val sink = RecordingSink()
        val player = Player(1, UUID.randomUUID(), "Tester", sink)
        return Triple(server, player, sink)
    }

    private fun plain(tag: NbtTag): String {
        if (tag !is NbtCompound) return ""
        val head = (tag["text"] as? NbtString)?.value ?: ""
        val extra = tag["extra"] as? NbtList<*> ?: return head
        return head + extra.joinToString("") { plain(it) }
    }

    @Test
    fun statusReportsCurrentVisibility() {
        val (server, player, sink) = fixture()

        server.commands.execute(player, "sidebar")
        assertTrue(sink.messages.any { plain(it) == "sidebar is shown" })

        player.showSidebar = false
        server.commands.execute(player, "board")
        assertTrue(sink.messages.any { plain(it) == "sidebar is hidden" })
    }

    @Test
    fun aliasesShowAndHideSidebar() {
        val (server, player, sink) = fixture()

        server.commands.execute(player, "sc hide")
        assertFalse(player.showSidebar)
        assertEquals("", sink.packets.filterIsInstance<ClientboundScoreboardDisplayObjectivePacket>().last().name)

        server.commands.execute(player, "board show")
        assertTrue(player.showSidebar)
        assertEquals(
            Sidebar.Objective,
            sink.packets.filterIsInstance<ClientboundScoreboardDisplayObjectivePacket>().last().name,
        )
    }
}
