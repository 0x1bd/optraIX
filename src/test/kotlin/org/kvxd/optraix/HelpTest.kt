package org.kvxd.optraix

import net.benwoodworth.knbt.NbtByte
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag
import org.kvxd.optraix.command.CommandRegistry
import org.kvxd.optraix.net.ChatFont
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.player.Player
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HelpTest {

    private fun run(): Pair<List<NbtTag>, CommandRegistry> {
        val server = OptraIxServer(ServerConfig(port = 0, runDirectory = File("build/tmp/help-test")))
        val sink = RecordingSink()
        val player = Player(1, UUID.randomUUID(), "Tester", sink)
        server.commands.execute(player, "help")
        return sink.messages to server.commands
    }

    private fun plain(tag: NbtTag): String {
        if (tag !is NbtCompound) return ""
        val head = (tag["text"] as? NbtString)?.value ?: ""
        val extra = tag["extra"] as? NbtList<*> ?: return head
        return head + extra.joinToString("") { plain(it) }
    }

    private fun leftColumn(tag: NbtTag): String? {
        val extra = (tag as? NbtCompound)?.get("extra") as? NbtList<*> ?: return null
        val first = extra.firstOrNull() as? NbtCompound ?: return null
        return (first["text"] as? NbtString)?.value
    }

    private fun pixelOffsetOfDescription(tag: NbtTag): Int? {
        val extra = (tag as? NbtCompound)?.get("extra") as? NbtList<*> ?: return null
        if (extra.size < 2) return null
        var width = 0
        for (index in 0 until extra.size - 1) {
            val part = extra[index] as? NbtCompound ?: continue
            val text = (part["text"] as? NbtString)?.value ?: continue
            val bold = (part["bold"] as? NbtByte)?.value?.toInt() == 1
            width += ChatFont.width(text, bold)
        }
        return width
    }

    @Test
    fun everyUsageLineNamesItsCommand() {
        val (messages, _) = run()
        val usages = messages.mapNotNull { leftColumn(it) }
        assertTrue(usages.isNotEmpty(), "help produced no rows")
        for (usage in usages) {
            assertTrue(
                usage.length > 1 && usage != "/" && !usage.startsWith("/<") && !usage.startsWith("/ "),
                "usage line lost its command name: '$usage'",
            )
        }
    }

    @Test
    fun knownCommandsAppearWithTheirArguments() {
        val (messages, _) = run()
        val text = messages.map { plain(it) }
        fun has(prefix: String) = text.any { it.trimStart().startsWith(prefix) }

        val dump = text.joinToString("\n")
        assertTrue(has("//set <block>"), "missing //set <block> in:\n$dump")
        assertTrue(has("//paste [-a]"), "paste should show its worldedit flag:\n$dump")
        assertTrue(has("/tps [info | unlimited | max | <rate>]"), "tps branches missing:\n$dump")
        assertTrue(has("/speed [<multiplier>]"), "speed argument missing:\n$dump")
        assertTrue(has("/tp <x> <y> <z>"), "missing /tp <x> <y> <z>:\n$dump")
        assertTrue(has("//stack <count> [<direction>]"), "stack direction missing:\n$dump")
        assertTrue(has("//schem (list | load <name>)"), "schem branches missing:\n$dump")
    }

    @Test
    fun descriptionsStartAtTheSamePixelOffset() {
        val (messages, _) = run()
        val offsets = messages.mapNotNull { pixelOffsetOfDescription(it) }.toSet()
        assertEquals(
            1, offsets.size,
            "descriptions must all start at one pixel offset, got $offsets",
        )
    }

    @Test
    fun aliasesAreGroupedOntoOneRow() {
        val (messages, _) = run()
        val text = messages.map { plain(it) }
        assertTrue(
            text.any { it.contains("/speed") && it.contains("(/s)") },
            "/s should be shown as an alias of /speed:\n" + text.joinToString("\n"),
        )
        assertTrue(
            text.none { it.trimStart().startsWith("/s ") },
            "aliases must not get their own rows",
        )
        assertTrue(
            text.any { it.contains("//pos1") && it.contains("//1") && it.contains("//hpos1") },
            "pos1 aliases should be grouped",
        )
    }

    @Test
    fun eachCommandGetsExactlyOneRow() {
        val (messages, registry) = run()
        val usages = messages.mapNotNull { leftColumn(it) }
        assertEquals(
            registry.commands.size + 1, usages.size,
            "expected one row per command plus /help, got:\n" + usages.joinToString("\n"),
        )
    }

    @Test
    fun fontWidthsMatchVanillaGlyphs() {
        assertEquals(4, ChatFont.width(" "))
        assertEquals(2, ChatFont.width("i"))
        assertEquals(3, ChatFont.width("l"))
        assertEquals(6, ChatFont.width("a"))
        assertEquals(4, ChatFont.width("I"))
        assertEquals(ChatFont.width("abc") + 3, ChatFont.width("abc", bold = true))
    }

    @Test
    fun paddingHitsTheExactDeficit() {
        for (deficit in 12..80) {
            val (spaces, bold) = ChatFont.padding(deficit)
            assertEquals(
                deficit,
                spaces * ChatFont.SpaceWidth + bold * ChatFont.BoldSpaceWidth,
                "cannot pad $deficit pixels exactly",
            )
        }
    }
}
