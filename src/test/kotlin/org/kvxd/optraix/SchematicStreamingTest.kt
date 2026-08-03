package org.kvxd.optraix

import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.GZIPOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.block.property.RotateAmount
import org.kvxd.optraix.command.worldedit.WorldEdit
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.redstone.optraix.CompileMemoryPreflight
import org.kvxd.optraix.redstone.optraix.OptraIxEngine
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.worldedit.Schematic

class SchematicStreamingTest {

    @Test
    fun sparseSchematicLoadsAndPastesOnlyStoredBlocks() {
        val directory = File("build/tmp/schematic-streaming-test").apply { mkdirs() }
        val file = File(directory, "sparse.schem")
        writeSparseSchematic(file, 30_000)

        val clipboard = Schematic.load(file)
        val stone = Blocks.require("minecraft:stone").defaultStateId
        assertTrue(clipboard.isSparse)
        assertEquals(2, clipboard.storedBlockCount)
        assertEquals(stone, clipboard[0, 0, 0])
        assertEquals(Blocks.airState, clipboard[15_000, 0, 0])
        assertEquals(stone, clipboard[29_999, 0, 0])

        val rotated = clipboard.rotate(RotateAmount.Rotate90)
        assertTrue(rotated.isSparse)
        assertEquals(2, rotated.storedBlockCount)
        assertEquals(stone, rotated[0, 0, 0])
        assertEquals(stone, rotated[0, 0, 29_999])

        val server = OptraIxServer(ServerConfig(port = 0, runDirectory = File(directory, "run")))
        val player = Player(1, UUID.randomUUID(), "Tester", RecordingSink())
        player.y = 0.0
        val changed = WorldEdit(server).paste(player, clipboard, includeAir = false)

        assertEquals(2, changed)
        assertTrue(server.world.loadedChunks < 10)
        assertEquals(stone, server.world.getBlock(BlockPos(0, 0, 0)))
        assertEquals(stone, server.world.getBlock(BlockPos(29_999, 0, 0)))
        assertFalse(server.world.getBlock(BlockPos(15_000, 0, 0)) == stone)
    }

    @Test
    fun configuredSm83SchematicLoadsSparsely() {
        val path = System.getenv("OPTRAIX_SM83_SCHEM") ?: return
        val clipboard = Schematic.load(File(path))

        assertTrue(clipboard.isSparse)
        assertEquals(2556, clipboard.sizeX)
        assertEquals(327, clipboard.sizeY)
        assertEquals(2216, clipboard.sizeZ)
        assertEquals(14_646_595, clipboard.storedBlockCount)

        val directory = File("build/tmp/sm83-streaming-test")
        val server = OptraIxServer(ServerConfig(port = 0, runDirectory = directory))
        val engine = OptraIxEngine()
        server.useEngine(engine)
        val player = Player(1, UUID.randomUUID(), "Tester", RecordingSink())
        player.y = 100.0
        val changed = WorldEdit(server).paste(player, clipboard, includeAir = false)

        assertEquals(14_646_595, changed)
        assertTrue(player.undoStack.isEmpty())
        assertTrue(engine.manualCompileRequired)
        assertTrue(!engine.compiled)
        assertNotNull(CompileMemoryPreflight.evaluate(server.world).failure)
    }

    private fun writeSparseSchematic(file: File, width: Int) {
        DataOutputStream(GZIPOutputStream(file.outputStream())).use { output ->
            output.writeByte(10)
            output.writeUTF("Schematic")
            int(output, "Version", 2)
            short(output, "Width", width)
            short(output, "Height", 1)
            short(output, "Length", 1)
            output.writeByte(10)
            output.writeUTF("Palette")
            int(output, "minecraft:air", 0)
            int(output, "minecraft:stone", 1)
            output.writeByte(0)
            output.writeByte(7)
            output.writeUTF("BlockData")
            output.writeInt(width)
            output.writeByte(1)
            repeat(width - 2) { output.writeByte(0) }
            output.writeByte(1)
            output.writeByte(9)
            output.writeUTF("BlockEntities")
            output.writeByte(10)
            output.writeInt(0)
            output.writeByte(0)
        }
    }

    private fun int(output: DataOutputStream, name: String, value: Int) {
        output.writeByte(3)
        output.writeUTF(name)
        output.writeInt(value)
    }

    private fun short(output: DataOutputStream, name: String, value: Int) {
        output.writeByte(2)
        output.writeUTF(name)
        output.writeShort(value)
    }
}
