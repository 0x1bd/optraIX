package org.kvxd.optraix

import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundBlockChangePacket
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.world.BlockPos

class WorldPublishingTest {

    @Test
    fun unlimitedClientUpdatesPublishConsecutiveStates() {
        val server = OptraIxServer(
            ServerConfig(
                port = 0,
                clientUpdateRate = 0,
                runDirectory = File("build/tmp/world-publishing-unlimited-test"),
            ),
        )
        val sink = RecordingSink()
        val player = Player(1, UUID.randomUUID(), "Tester", sink)
        player.loadedChunks += 0L
        server.players += player
        val pos = BlockPos(0, 1, 0)
        val states = listOf(
            Blocks.RedstoneBlock.defaultState,
            Blocks.Stone.defaultState,
            Blocks.RedstoneBlock.defaultState,
        )

        for (state in states) {
            server.world.setBlock(pos, state)
            server.publishWorldChanges()
        }

        assertEquals(
            states,
            sink.packets.filterIsInstance<ClientboundBlockChangePacket>().map { it.type },
        )
        server.shutdown()
    }

    @Test
    fun cappedClientUpdatesCoalesceRapidChanges() {
        val server = OptraIxServer(
            ServerConfig(
                port = 0,
                clientUpdateRate = 1,
                runDirectory = File("build/tmp/world-publishing-capped-test"),
            ),
        )
        val sink = RecordingSink()
        val player = Player(1, UUID.randomUUID(), "Tester", sink)
        player.loadedChunks += 0L
        server.players += player
        val pos = BlockPos(0, 1, 0)

        server.world.setBlock(pos, Blocks.RedstoneBlock.defaultState)
        server.publishWorldChanges()
        server.world.setBlock(pos, Blocks.Stone.defaultState)
        server.publishWorldChanges()

        assertEquals(
            listOf(Blocks.RedstoneBlock.defaultState),
            sink.packets.filterIsInstance<ClientboundBlockChangePacket>().map { it.type },
        )
        server.shutdown()
    }
}
