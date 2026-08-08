package org.kvxd.optraix

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.player.Player
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundKickDisconnectPacket
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.Socket
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ServerShutdownTest {

    private fun server() = OptraIxServer(
        ServerConfig(port = 0, runDirectory = Files.createTempDirectory("optraix-stop").toFile())
    )

    @Test
    fun stopCommandSignalsShutdown() = runBlocking {
        val server = server()
        val player = Player(1, UUID.randomUUID(), "Tester", RecordingSink())

        server.commands.execute(player, "stop")

        withTimeout(1_000) { server.awaitStop() }
        assertFalse(server.running)
        server.shutdown()
    }

    @Test
    fun shutdownIsIdempotentAndClosesPlayers() {
        val server = server()
        val sink = RecordingSink()
        server.players += Player(1, UUID.randomUUID(), "Tester", sink)

        val first = server.shutdown()
        val second = server.shutdown()

        assertEquals(first, second)
        assertEquals(1, sink.countOf<ClientboundKickDisconnectPacket>())
        assertTrue(sink.closed)
        assertFalse(server.running)
    }

    @Test
    fun shutdownReleasesListeningPort() = runBlocking {
        val firstScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val first = server()
        first.start(firstScope)
        val port = first.boundPort

        first.requestStop()
        withTimeout(1_000) { first.awaitStop() }
        first.shutdown()
        firstScope.cancel()

        val secondScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val second = OptraIxServer(
            ServerConfig(
                host = "127.0.0.1",
                port = port,
                runDirectory = Files.createTempDirectory("optraix-rebind").toFile(),
            )
        )
        try {
            second.start(secondScope)
            assertEquals(port, second.boundPort)
        } finally {
            second.shutdown()
            secondScope.cancel()
        }
    }

    @Test
    fun closingSessionsDuringShutdownDoesNotLogNetworkFailures() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val server = server()
        server.start(scope)
        val client = Socket("127.0.0.1", server.boundPort)
        delay(100)

        val captured = ByteArrayOutputStream()
        val original = System.err
        System.setErr(PrintStream(captured))
        try {
            server.shutdown()
            delay(100)
        } finally {
            System.setErr(original)
            client.close()
            scope.cancel()
        }

        assertFalse(
            captured.toString().contains("failed in"),
            "normal shutdown must not report cancelled sessions as failures:\n$captured",
        )
    }
}
