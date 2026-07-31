package org.kvxd.gogolmc

import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.block.property.WireSide
import org.kvxd.gogolmc.command.worldedit.WorldEdit
import org.kvxd.gogolmc.net.GogolServer
import org.kvxd.gogolmc.player.Player
import org.kvxd.gogolmc.redstone.mchprs.MchprsRedstone
import org.kvxd.gogolmc.redstone.mchprs.Wire
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.worldedit.Region
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WorldEditTest {

    private fun server(): GogolServer =
        GogolServer(ServerConfig(port = 0, runDirectory = File("build/tmp/worldedit-test")))

    private fun player(server: GogolServer): Player =
        Player(1, UUID.randomUUID(), "Tester", RecordingSink())

    private fun tick(server: GogolServer, times: Int) {
        repeat(times) {
            server.world.tickScheduled { pos -> server.engine.tick(server.world, pos) }
        }
    }

    @Test
    fun setRefreshesRedstoneWireShape() {
        val server = server()
        val worldEdit = WorldEdit(server)
        val player = player(server)
        val stone = Blocks.require("minecraft:stone").defaultStateId
        val wire = Blocks.require("minecraft:redstone_wire").defaultStateId

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
        val stone = Blocks.require("minecraft:stone").defaultStateId

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
        val redstoneBlock = Blocks.require("minecraft:redstone_block").defaultStateId
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
        val stone = Blocks.require("minecraft:stone").defaultStateId

        for (x in 0..4) server.world.setBlock(BlockPos(x, 0, 0), stone)
        for (x in 1..4) {
            val pos = BlockPos(x, 1, 0)
            server.interaction.placeInWorld(
                MchprsRedstone.wireStateForPlacement(server.world, pos), server.world, pos, null
            )
        }

        player.selectionOne = BlockPos(0, 1, 0)
        player.selectionTwo = BlockPos(0, 1, 0)
        val redstoneBlock = Blocks.require("minecraft:redstone_block").defaultStateId
        worldEdit.apply(player, Region(player.selectionOne!!, player.selectionTwo!!)) { redstoneBlock }
        tick(server, 2)
        assertEquals(15, Wire.power(server.world.getBlock(BlockPos(1, 1, 0))))

        worldEdit.undo(player)
        tick(server, 2)

        assertEquals(Blocks.airState, server.world.getBlock(BlockPos(0, 1, 0)))
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
        val stone = Blocks.require("minecraft:stone").defaultStateId

        for (x in 0..2) for (z in -1..1) server.world.setBlock(BlockPos(x, 0, z), stone)
        val comparatorPos = BlockPos(1, 1, 0)
        server.world.setBlock(
            comparatorPos,
            BlockStates.comparatorState(
                org.kvxd.gogolmc.block.property.BlockDirection.East,
                org.kvxd.gogolmc.block.property.ComparatorMode.Subtract,
                false,
            ),
        )
        server.world.setBlockEntity(
            comparatorPos, org.kvxd.gogolmc.world.BlockEntity.Comparator(7)
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
            Blocks.typeOf(server.world.getBlock(target)) == Blocks.require("minecraft:comparator"),
            "expected a comparator at $target, got ${Blocks.describe(server.world.getBlock(target))}",
        )
        val pasted = server.world.getBlockEntity(target)
        assertTrue(
            pasted is org.kvxd.gogolmc.world.BlockEntity.Comparator,
            "pasted comparator lost its block entity",
        )
    }
}
