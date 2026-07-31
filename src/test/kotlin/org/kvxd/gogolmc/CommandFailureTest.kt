package org.kvxd.gogolmc

import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag
import org.kvxd.gogolmc.command.CommandSource
import org.kvxd.gogolmc.command.GogolCommand
import org.kvxd.gogolmc.command.literal
import org.kvxd.gogolmc.command.runs
import org.kvxd.gogolmc.net.GogolServer
import org.kvxd.gogolmc.player.Player
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.PrintStream
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertTrue

class CommandFailureTest {

    private class BoomCommand : GogolCommand {
        override val name = "boom"
        override val description = "always fails"
        override fun register(dispatcher: com.mojang.brigadier.CommandDispatcher<CommandSource>) {
            dispatcher.register(literal(name).runs { throw NoClassDefFoundError("org/kvxd/gogolmc/Missing") })
        }
    }

    private fun plain(tag: NbtTag): String {
        if (tag !is NbtCompound) return ""
        val head = (tag["text"] as? NbtString)?.value ?: ""
        val extra = tag["extra"] as? NbtList<*> ?: return head
        return head + extra.joinToString("") { plain(it) }
    }

    @Test
    fun failingCommandLogsAStackTraceAndTellsThePlayer() {
        val server = GogolServer(ServerConfig(port = 0, runDirectory = File("build/tmp/failure-test")))
        BoomCommand().register(server.commands.dispatcher)

        val sink = RecordingSink()
        val player = Player(1, UUID.randomUUID(), "Tester", sink)

        val captured = ByteArrayOutputStream()
        val original = System.err
        System.setErr(PrintStream(captured))
        try {
            server.commands.execute(player, "boom")
        } finally {
            System.setErr(original)
        }

        val console = captured.toString()
        assertTrue(console.contains("[command]"), "console line missing:\n$console")
        assertTrue(console.contains("Tester ran '/boom'"), "console must name player and command:\n$console")
        assertTrue(
            console.contains("NoClassDefFoundError"),
            "console must name the throwable type:\n$console",
        )
        assertTrue(
            console.contains("CommandFailureTest"),
            "console must carry a real stack trace:\n$console",
        )

        val replies = sink.messages.map { plain(it) }
        assertTrue(
            replies.any { it.contains("NoClassDefFoundError") && it.contains("Missing") },
            "player should see the cause, got $replies",
        )
    }

    @Test
    fun syntaxErrorsStayQuietOnTheConsole() {
        val server = GogolServer(ServerConfig(port = 0, runDirectory = File("build/tmp/failure-test")))
        val sink = RecordingSink()
        val player = Player(1, UUID.randomUUID(), "Tester", sink)

        val captured = ByteArrayOutputStream()
        val original = System.err
        System.setErr(PrintStream(captured))
        try {
            server.commands.execute(player, "definitely-not-a-command")
        } finally {
            System.setErr(original)
        }

        assertTrue(captured.toString().isEmpty(), "typos should not spam the console")
        assertTrue(sink.messages.isNotEmpty(), "the player should still be told")
    }
}
