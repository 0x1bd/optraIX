package org.kvxd.optraix

import java.io.File
import java.util.UUID
import java.util.concurrent.locks.LockSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue
import net.lenni0451.mcstructs.nbt.tags.IntTag
import org.kvxd.optraix.block.mcData
import org.kvxd.optraix.command.worldedit.SchematicFiles
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.nbt.NbtIo
import org.kvxd.optraix.nbt.compound
import org.kvxd.optraix.nbt.int
import org.kvxd.optraix.nbt.compoundOf
import org.kvxd.optraix.nbt.tag
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.ContainerKind
import org.kvxd.optraix.world.InventoryEntry
import org.kvxd.optraix.worldedit.clipboard.Clipboard
import org.kvxd.optraix.worldedit.schematic.Schematic
import org.kvxd.optraix.worldedit.schematic.SchematicException
import net.lenni0451.mcstructs.nbt.tags.IntArrayTag

class SchematicExportTest {

    @Test
    fun exportedSpongeV3SchematicRoundTrips() {
        val file = File("build/tmp/schematic-export-test/round-trip.schem")
        val air = Blocks.Air.defaultState
        val stone = Blocks.Stone.defaultState
        val litLamp = checkNotNull(mcData.blockState("minecraft:redstone_lamp[lit=true]"))
        val clipboard = Clipboard(
            sizeX = 5,
            sizeY = 1,
            sizeZ = 1,
            offset = BlockPos(-4, 2, 7),
            blocks = intArrayOf(
                stone,
                air,
                litLamp,
                Blocks.Comparator.defaultState,
                Blocks.Chest.defaultState,
            ),
        )
        clipboard.blockEntities[clipboard.index(3, 0, 0)] = BlockEntity.Comparator(11)
        clipboard.blockEntities[clipboard.index(4, 0, 0)] = BlockEntity.Container(
            ContainerKind.Chest,
            1,
            listOf(
                InventoryEntry(
                    org.kvxd.optraix.mcdata.v1_20_4.Items.Redstone.id,
                    slot = 2,
                    count = 17,
                    nbt = compoundOf("CustomModelData" to IntTag(42)),
                )
            ),
        )

        Schematic.save(file, clipboard)
        val schematicTag = checkNotNull(
            file.inputStream().use(NbtIo::readCompressedOrPlain).compound("Schematic")
        )
        assertEquals(Schematic.EXPORT_FORMAT_VERSION, schematicTag.int("Version"))
        assertTrue(schematicTag.compound("Blocks") != null)
        assertTrue(schematicTag.tag("Offset") is IntArrayTag)
        assertTrue(
            (schematicTag.tag("Offset") as IntArrayTag).value.contentEquals(intArrayOf(-4, 2, 7))
        )
        val loaded = Schematic.load(file)

        assertEquals(5, loaded.sizeX)
        assertEquals(1, loaded.sizeY)
        assertEquals(1, loaded.sizeZ)
        assertEquals(BlockPos(-4, 2, 7), loaded.offset)
        assertEquals(stone, loaded[0, 0, 0])
        assertEquals(air, loaded[1, 0, 0])
        assertEquals(litLamp, loaded[2, 0, 0])
        assertEquals(Blocks.Comparator.defaultState, loaded[3, 0, 0])
        assertEquals(11, assertIs<BlockEntity.Comparator>(loaded.blockEntities[3]).outputStrength)
        val container = assertIs<BlockEntity.Container>(loaded.blockEntities[4])
        assertEquals(17, container.inventory.single().count)
        assertEquals(42, (container.inventory.single().nbt as? net.lenni0451.mcstructs.nbt.tags.CompoundTag)?.int("CustomModelData"))
    }

    @Test
    fun schemExportUsesThePlayersSelection() {
        val runDirectory = File("build/tmp/schematic-export-test/command")
        val server = OptraIxServer(ServerConfig(port = 0, runDirectory = runDirectory))
        val player = Player(1, UUID.randomUUID(), "Tester", RecordingSink())
        val first = BlockPos(5, 2, 7)
        val second = BlockPos(6, 2, 7)
        player.selectionOne = first
        player.selectionTwo = second
        player.x = 3.0
        player.y = 1.0
        player.z = 4.0
        server.world.setBlock(first, Blocks.Stone.defaultState)
        server.world.setBlock(second, Blocks.Comparator.defaultState)
        server.world.setBlockEntity(second, BlockEntity.Comparator(6))

        val file = File(server.config.schematicsDirectory, "selected.schem")
        file.delete()
        server.commands.execute(player, "/schem export selected")
        var attempts = 0
        while (!file.isFile && attempts++ < 1_000) {
            server.runSubmittedTasks()
            LockSupport.parkNanos(1_000_000L)
        }
        server.runSubmittedTasks()
        server.commands.shutdownWorldEdit()

        assertTrue(file.isFile, "export command did not create ${file.path}")
        val loaded = Schematic.load(file)
        assertEquals(Blocks.Stone.defaultState, loaded[0, 0, 0])
        assertEquals(Blocks.Comparator.defaultState, loaded[1, 0, 0])
        assertEquals(BlockPos(2, 1, 3), loaded.offset)
        assertEquals(6, assertIs<BlockEntity.Comparator>(loaded.blockEntities[1]).outputStrength)
    }

    @Test
    fun schematicNamesCannotEscapeTheConfiguredDirectory() {
        val directory = File("build/tmp/schematic-export-test/schematics")

        assertFailsWith<SchematicException> {
            SchematicFiles.resolve(directory, "../outside")
        }
        assertFailsWith<SchematicException> {
            SchematicFiles.resolve(directory, "nested/outside")
        }
        assertFailsWith<SchematicException> {
            SchematicFiles.resolveExport(directory, "legacy.schematic")
        }
    }
}
