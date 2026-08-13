package org.kvxd.optraix

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.command.worldedit.WorldEdit
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.redstone.optraix.OptraIxEngine
import org.kvxd.optraix.redstone.mchprs.Wire
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.worldedit.Region
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.block.mcData
import org.kvxd.optraix.block.property.WireSide

class WorldEditTest {

    @Test
    fun bulkPasteRequiresExplicitOptraIxCompilation() {
        val server = server()
        val engine = OptraIxEngine()
        server.useEngine(engine)
        val player = player(server)
        player.y = 10.0
        val stone = Blocks.Stone.defaultState
        val blocks = IntArray(250_001) { stone }
        val clipboard = org.kvxd.optraix.worldedit.clipboard.Clipboard(
            250_001,
            1,
            1,
            BlockPos(0, 0, 0),
            blocks,
        )

        assertEquals(250_001, WorldEdit(server).paste(player, clipboard, includeAir = false))
        assertTrue(engine.manualCompileRequired)
        assertTrue(!engine.compiled)
        server.compileRedstone(engine)
        assertTrue(!engine.compiled)
        assertTrue(engine.compile(server.world))
        assertTrue(engine.compiled)
        assertTrue(!engine.manualCompileRequired)
    }

    private fun server(): OptraIxServer =
        OptraIxServer(ServerConfig(port = 0, runDirectory = File("build/tmp/worldedit-test")))

    private fun player(server: OptraIxServer): Player =
        Player(1, UUID.randomUUID(), "Tester", RecordingSink())

    private fun tick(server: OptraIxServer, times: Int) {
        repeat(times) {
            server.world.tickScheduled { pos -> server.engine.tick(server.world, pos) }
        }
    }

    @Test
    fun setRefreshesRedstoneWireShape() {
        val server = server()
        val worldEdit = WorldEdit(server)
        val player = player(server)
        val stone = Blocks.Stone.defaultState
        val wire = Blocks.RedstoneWire.defaultState

        for (x in 0..3) server.world.setBlock(BlockPos(x, 0, 0), stone)

        player.selectionOne = BlockPos(0, 1, 0)
        player.selectionTwo = BlockPos(3, 1, 0)
        val region = Region(player.selectionOne!!, player.selectionTwo!!)
        worldEdit.apply(player, region) { wire }

        val first = server.world.getBlock(BlockPos(0, 1, 0))
        val middle = server.world.getBlock(BlockPos(1, 1, 0))
        assertEquals(WireSide.Side, Wire.east(first), "a //set line of dust must connect up")
        assertEquals(WireSide.Side, Wire.west(middle))
        assertEquals(WireSide.Side, Wire.east(middle))
    }

    @Test
    fun setPoweringABlockLightsTheCircuit() {
        val server = server()
        val worldEdit = WorldEdit(server)
        val player = player(server)
        val stone = Blocks.Stone.defaultState

        for (x in 0..4) server.world.setBlock(BlockPos(x, 0, 0), stone)
        for (x in 1..4) {
            val pos = BlockPos(x, 1, 0)
            server.interaction.placeInWorld(
                MchprsRedstone.wireStateForPlacement(server.world, pos), server.world, pos, null
            )
        }
        assertEquals(0, Wire.power(server.world.getBlock(BlockPos(1, 1, 0))))

        player.selectionOne = BlockPos(0, 1, 0)
        player.selectionTwo = BlockPos(0, 1, 0)
        val redstoneBlock = Blocks.RedstoneBlock.defaultState
        worldEdit.apply(player, Region(player.selectionOne!!, player.selectionTwo!!)) { redstoneBlock }
        tick(server, 2)

        assertEquals(
            15, Wire.power(server.world.getBlock(BlockPos(1, 1, 0))),
            "//set of a power source must update the circuit without a manual poke",
        )
        assertEquals(12, Wire.power(server.world.getBlock(BlockPos(4, 1, 0))))
    }

    @Test
    fun undoRestoresAndRefreshes() {
        val server = server()
        val worldEdit = WorldEdit(server)
        val player = player(server)
        val stone = Blocks.Stone.defaultState

        for (x in 0..4) server.world.setBlock(BlockPos(x, 0, 0), stone)
        for (x in 1..4) {
            val pos = BlockPos(x, 1, 0)
            server.interaction.placeInWorld(
                MchprsRedstone.wireStateForPlacement(server.world, pos), server.world, pos, null
            )
        }

        player.selectionOne = BlockPos(0, 1, 0)
        player.selectionTwo = BlockPos(0, 1, 0)
        val redstoneBlock = Blocks.RedstoneBlock.defaultState
        worldEdit.apply(player, Region(player.selectionOne!!, player.selectionTwo!!)) { redstoneBlock }
        tick(server, 2)
        assertEquals(15, Wire.power(server.world.getBlock(BlockPos(1, 1, 0))))

        worldEdit.undo(player)
        tick(server, 2)

        assertEquals(Blocks.Air.defaultState, server.world.getBlock(BlockPos(0, 1, 0)))
        assertEquals(
            0, Wire.power(server.world.getBlock(BlockPos(1, 1, 0))),
            "undo must also settle the circuit back down",
        )
    }

    @Test
    fun pasteRestoresComparatorBlockEntities() {
        val server = server()
        val worldEdit = WorldEdit(server)
        val player = player(server)
        val stone = Blocks.Stone.defaultState

        for (x in 0..2) for (z in -1..1) server.world.setBlock(BlockPos(x, 0, z), stone)
        val comparatorPos = BlockPos(1, 1, 0)
        server.world.setBlock(
            comparatorPos,
            BlockStates.comparatorState(
                org.kvxd.optraix.block.property.BlockDirection.East,
                org.kvxd.optraix.block.property.ComparatorMode.Subtract,
                false,
            ),
        )
        server.world.setBlockEntity(
            comparatorPos, org.kvxd.optraix.world.BlockEntity.Comparator(7)
        )

        player.selectionOne = BlockPos(1, 1, 0)
        player.selectionTwo = BlockPos(1, 1, 0)
        worldEdit.copy(player, Region(player.selectionOne!!, player.selectionTwo!!))

        player.x = 20.0
        player.y = 1.0
        player.z = 0.0
        val clipboard = player.clipboard!!
        assertTrue(clipboard.blockEntities.isNotEmpty(), "the comparator entity must be copied")

        worldEdit.paste(player, clipboard, includeAir = false)
        val target = BlockPos(
            player.blockPos.x + clipboard.offset.x,
            player.blockPos.y + clipboard.offset.y,
            player.blockPos.z + clipboard.offset.z,
        )
        assertTrue(
            mcData.requireBlockByStateId(server.world.getBlock(target)) == Blocks.Comparator,
            "expected a comparator at $target, got ${mcData.describeState(server.world.getBlock(target))}",
        )
        val pasted = server.world.getBlockEntity(target)
        assertTrue(
            pasted is org.kvxd.optraix.world.BlockEntity.Comparator,
            "pasted comparator lost its block entity",
        )
    }
}
