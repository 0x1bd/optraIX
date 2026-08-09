package org.kvxd.optraix

import org.kvxd.optraix.block.ItemStack
import org.kvxd.optraix.block.mcData
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.player.PlayerProfileStore
import java.io.DataOutputStream
import java.io.File
import java.nio.file.Files
import java.util.UUID
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PlayerProfileStoreTest {

    private fun tempFile(): File {
        val directory = Files.createTempDirectory("optraix-profile").toFile()
        directory.deleteOnExit()
        return File(directory, "players.dat")
    }

    private fun player(name: String = "Tester") =
        Player(1, UUID.randomUUID(), name, RecordingSink())

    @Test
    fun profileRoundTripRestoresPlayerState() {
        val file = tempFile()
        val original = player()
        original.x = -123.25
        original.y = 72.5
        original.z = 4096.75
        original.yaw = 91.0f
        original.pitch = -32.5f
        original.flying = true
        original.speedMultiplier = 4.5f
        original.selectedSlot = 7
        original.showSelection = false
        original.showSidebar = false
        original.inventory[36] = ItemStack(assertNotNull(mcData.item("minecraft:redstone")), 42, null)

        PlayerProfileStore(file).also {
            it.put(original)
            assertEquals(1, it.save())
        }

        val store = PlayerProfileStore(file)
        assertEquals(1, store.load())
        val restored = player()
        assertNotNull(store[restored.name]).applyTo(restored)

        assertEquals(original.x, restored.x)
        assertEquals(original.y, restored.y)
        assertEquals(original.z, restored.z)
        assertEquals(original.yaw, restored.yaw)
        assertEquals(original.pitch, restored.pitch)
        assertTrue(restored.flying)
        assertEquals(original.speedMultiplier, restored.speedMultiplier)
        assertEquals(original.selectedSlot, restored.selectedSlot)
        assertFalse(restored.showSelection)
        assertFalse(restored.showSidebar)
        assertEquals("minecraft:redstone", restored.inventory[36]?.item?.name)
        assertEquals(42, restored.inventory[36]?.count)
    }

    @Test
    fun versionOneProfilesKeepDefaultVisibility() {
        val file = tempFile()
        DataOutputStream(GZIPOutputStream(file.outputStream().buffered())).use { output ->
            output.writeInt(0x47504C52)
            output.writeInt(1)
            output.writeInt(1)
            output.writeUTF("Legacy")
            output.writeInt(3)
            output.writeFloat(2.0f)
            output.writeDouble(10.0)
            output.writeDouble(64.0)
            output.writeDouble(-20.0)
            output.writeFloat(45.0f)
            output.writeFloat(12.0f)
            output.writeBoolean(true)
            output.writeInt(0)
        }

        val store = PlayerProfileStore(file)
        assertEquals(1, store.load())
        val restored = player("Legacy")
        assertNotNull(store[restored.name]).applyTo(restored)

        assertEquals(64.0, restored.y)
        assertTrue(restored.showSelection)
        assertTrue(restored.showSidebar)
    }

    @Test
    fun joiningDoesNotOverwriteRestoredPositionOrVisibility() {
        val server = org.kvxd.optraix.net.OptraIxServer(
            ServerConfig(port = 0, runDirectory = Files.createTempDirectory("optraix-join").toFile())
        )
        val saved = player()
        saved.x = 8.5
        saved.y = 99.0
        saved.z = -12.5
        saved.showSidebar = false
        server.profiles.put(saved)

        val joining = player()
        server.addPlayer(joining)

        assertEquals(saved.x, joining.x)
        assertEquals(saved.y, joining.y)
        assertEquals(saved.z, joining.z)
        assertFalse(joining.showSidebar)
    }
}
