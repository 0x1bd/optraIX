package org.kvxd.optraix

import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundHeldItemSlotPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundWindowItemsPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.serverbound.ServerboundPickItemPacket
import org.kvxd.optraix.block.ItemStack
import org.kvxd.optraix.block.Items
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.mcdata.v1_20_4.Blocks

class PickBlockTest {

    @Test
    fun picksBlockIntoHotbar() {
        val server = server()
        val sink = RecordingSink()
        val player = Player(1, UUID.randomUUID(), "Tester", sink)
        player.x = 0.5
        player.y = 1.0
        player.z = 0.5
        server.players += player

        val pos = BlockPos(1, 1, 0)
        server.world.setBlockSilent(pos, Blocks.RedstoneWire.defaultState)
        server.pickItemFromBlock(player.uuid, pos, false)

        assertEquals("minecraft:redstone", player.heldItem?.item?.name)
        assertEquals(0, player.selectedSlot)
        assertEquals(1, sink.countOf<ClientboundWindowItemsPacket>())
        assertEquals(1, sink.countOf<ClientboundHeldItemSlotPacket>())
    }

    @Test
    fun selectsMatchingHotbarItemWithoutReplacingIt() {
        val server = server()
        val sink = RecordingSink()
        val player = Player(1, UUID.randomUUID(), "Tester", sink)
        player.x = 0.5
        player.y = 1.0
        player.z = 0.5
        server.players += player

        val pos = BlockPos(1, 1, 0)
        val state = Blocks.Sandstone.defaultState
        server.world.setBlockSilent(pos, state)
        val sandstone = assertNotNull(org.kvxd.optraix.block.Items.byName("minecraft:sandstone"))
        player.inventory[40] = org.kvxd.optraix.block.ItemStack(sandstone)

        server.pickItemFromBlock(player.uuid, pos, false)

        assertEquals(4, player.selectedSlot)
        assertEquals("minecraft:sandstone", player.heldItem?.item?.name)
        assertEquals(0, sink.countOf<ClientboundWindowItemsPacket>())
        assertEquals(1, sink.countOf<ClientboundHeldItemSlotPacket>())
    }

    @Test
    fun handlesCanonicalPickItemPacket() {
        val server = server()
        val sink = RecordingSink()
        val player = Player(1, UUID.randomUUID(), "Tester", sink)
        server.players += player
        val sandstone = assertNotNull(Items.byName("minecraft:sandstone"))
        player.inventory[10] = ItemStack(sandstone)

        server.handlePlayPacket(player, ServerboundPickItemPacket(10))

        assertEquals("minecraft:sandstone", player.inventory[36]?.item?.name)
        assertEquals(null, player.inventory[10])
        assertEquals(0, player.selectedSlot)
        assertEquals(1, sink.countOf<ClientboundWindowItemsPacket>())
        assertEquals(1, sink.countOf<ClientboundHeldItemSlotPacket>())
    }

    @Test
    fun rejectsOutOfRangePick() {
        val server = server()
        val sink = RecordingSink()
        val player = Player(1, UUID.randomUUID(), "Tester", sink)
        server.players += player

        val pos = BlockPos(100, 1, 0)
        server.world.setBlockSilent(pos, Blocks.Sandstone.defaultState)
        server.pickItemFromBlock(player.uuid, pos, false)

        assertTrue(player.inventory.all { it == null })
        assertTrue(sink.packets.isEmpty())
    }

    private fun server(): OptraIxServer {
        val runDirectory = Files.createTempDirectory("optraix-pick-test-").toFile()
        return OptraIxServer(ServerConfig(runDirectory = runDirectory))
    }
}
