package org.kvxd.optraix

import net.lenni0451.mcstructs.nbt.NbtTag
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import net.lenni0451.mcstructs.nbt.tags.StringTag
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundUnloadChunkPacket
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.nbt.list
import org.kvxd.optraix.nbt.tag
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.ChunkPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.TickPriority
import org.kvxd.optraix.world.WorldStorage
import org.kvxd.optraix.worldedit.history.UndoEntry
import java.nio.file.Files
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.test.assertTrue

class WorldCommandTest {

    @Test
    fun resetRequiresConfirmationAndErasesWorldData() {
        val directory = Files.createTempDirectory("optraix-world-reset").toFile()
        val server = OptraIxServer(
            ServerConfig(port = 0, viewDistance = 0, runDirectory = directory),
        )
        val original = server.worlds.default
        val edited = BlockPos(4, 5, 4)
        original.world.setBlock(edited, Blocks.Stone.defaultState)
        original.world.scheduleTick(edited, 20, TickPriority.Normal)
        WorldStorage.save(original.world, original.file)

        val sink = RecordingSink()
        val player = Player(1, UUID.randomUUID(), "Tester", sink)
        player.loadedChunks += ChunkPos.key(0, 0)
        player.selectionOne = edited
        player.undoStack.push(UndoEntry(longArrayOf(edited.asLong()), intArrayOf(Blocks.Air.defaultState)))
        server.players += player

        server.commands.execute(player, "world reset optraix")

        assertSame(original, server.worlds.default)
        assertEquals(Blocks.Stone.defaultState, original.world.getBlock(edited))
        assertTrue(sink.messages.any { plain(it).contains("confirm") })

        server.commands.execute(player, "world reset optraix confirm")

        val reset = server.worlds.default
        assertNotSame(original, reset)
        assertEquals(Blocks.Air.defaultState, reset.world.getBlock(edited))
        assertEquals(0, reset.world.scheduledTicks)
        assertEquals(null, player.selectionOne)
        assertTrue(player.undoStack.isEmpty())
        assertEquals(setOf(ChunkPos.key(0, 0)), player.loadedChunks)
        assertEquals(1, sink.countOf<ClientboundUnloadChunkPacket>())
        assertTrue(sink.messages.any { plain(it) == "reset world 'optraix'" })

        val persisted = GameWorld()
        assertEquals(0, WorldStorage.load(persisted, reset.file))
        assertEquals(Blocks.Air.defaultState, persisted.getBlock(edited))
        assertEquals(0, persisted.scheduledTicks)
    }

    private fun plain(tag: NbtTag): String {
        if (tag !is CompoundTag) return ""
        val head = (tag.tag("text") as? StringTag)?.value ?: ""
        val extra = tag.list("extra") ?: return head
        return head + extra.joinToString("") { plain(it) }
    }
}
