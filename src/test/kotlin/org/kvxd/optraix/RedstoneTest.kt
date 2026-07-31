package org.kvxd.optraix

import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.BlockFace
import org.kvxd.optraix.block.BlockKind
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.block.property.ComparatorMode
import org.kvxd.optraix.block.property.LeverFace
import org.kvxd.optraix.block.property.WireSide
import org.kvxd.optraix.interaction.Interaction
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.redstone.mchprs.Wire
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WorldGenerator
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RedstoneTest {

    private val stone = Blocks.require("minecraft:stone").defaultStateId
    private val interaction = Interaction(MchprsRedstone)

    private fun emptyWorld(): GameWorld = GameWorld(WorldGenerator(Blocks.airState, 0))

    private fun runTicks(world: GameWorld, count: Int) {
        repeat(count) { world.tickScheduled { pos -> MchprsRedstone.tick(world, pos) } }
    }

    private fun floorLever(world: GameWorld, pos: BlockPos): BlockPos {
        world.setBlock(pos.offset(BlockFace.Bottom), stone)
        interaction.placeInWorld(
            BlockStates.leverState(LeverFace.Floor, BlockDirection.North, false), world, pos, null
        )
        return pos
    }

    @Test
    fun blockStateIdsMatchVanilla() {
        assertEquals(1, Blocks.require("minecraft:stone").defaultStateId)
        assertEquals(535, Blocks.require("minecraft:sandstone").defaultStateId)
        assertEquals(2978, Blocks.require("minecraft:redstone_wire").minStateId)
        assertEquals(5881, Blocks.require("minecraft:repeater").minStateId)
        assertEquals(9175, Blocks.require("minecraft:comparator").minStateId)
        assertEquals(26644, Blocks.stateCount)
    }

    @Test
    fun repeaterStateRoundTrips() {
        val state = BlockStates.repeaterState(3, BlockDirection.West, locked = true, powered = false)
        assertEquals(BlockKind.Repeater, BlockStates.kindOf(state))
        assertEquals(3, BlockStates.delay[state].toInt())
        assertEquals(BlockDirection.West, BlockStates.directionOf(state))
        assertTrue(BlockStates.locked[state])
        assertTrue(!BlockStates.powered[state])
    }

    @Test
    fun comparatorStateRoundTrips() {
        val state = BlockStates.comparatorState(BlockDirection.West, ComparatorMode.Subtract, false)
        assertEquals(BlockKind.Comparator, BlockStates.kindOf(state))
        assertEquals(BlockDirection.West, BlockStates.directionOf(state))
        assertEquals(ComparatorMode.Subtract, BlockStates.comparatorModeOf(state))
    }

    @Test
    fun slabFlagsAreDynamic() {
        val type = Blocks.require("minecraft:smooth_stone_slab")
        val property = type.requireProperty("type")
        val top = type.withValue(type.defaultStateId, property, "top")
        val bottom = type.withValue(type.defaultStateId, property, "bottom")
        val double = type.withValue(type.defaultStateId, property, "double")
        assertTrue(BlockStates.isCube(top) && !BlockStates.isSolid(top))
        assertTrue(!BlockStates.isCube(bottom) && BlockStates.isTransparent(bottom))
        assertTrue(BlockStates.isSolid(double) && !BlockStates.isTransparent(double))
    }

    @Test
    fun leverPowersWireLine() {
        val world = emptyWorld()
        for (x in 0..5) world.setBlock(BlockPos(x, 0, 0), stone)
        for (x in 1..5) {
            val pos = BlockPos(x, 1, 0)
            interaction.placeInWorld(MchprsRedstone.wireStateForPlacement(world, pos), world, pos, null)
        }

        val leverPos = floorLever(world, BlockPos(0, 1, 0))
        assertEquals(0, Wire.power(world.getBlock(BlockPos(1, 1, 0))))

        MchprsRedstone.onUse(world, leverPos)
        runTicks(world, 2)

        assertEquals(15, Wire.power(world.getBlock(BlockPos(1, 1, 0))))
        assertEquals(14, Wire.power(world.getBlock(BlockPos(2, 1, 0))))
        assertEquals(11, Wire.power(world.getBlock(BlockPos(5, 1, 0))))

        MchprsRedstone.onUse(world, leverPos)
        runTicks(world, 2)
        assertEquals(0, Wire.power(world.getBlock(BlockPos(1, 1, 0))))
    }

    @Test
    fun torchInvertsAndDelays() {
        val world = emptyWorld()
        world.setBlock(BlockPos(1, 0, 0), stone)

        val torchPos = BlockPos(1, 1, 0)
        interaction.placeInWorld(BlockStates.torchState(true), world, torchPos, null)
        assertTrue(BlockStates.lit[world.getBlock(torchPos)])

        val leverPos = BlockPos(2, 0, 0)
        interaction.placeInWorld(
            BlockStates.leverState(LeverFace.Wall, BlockDirection.East, false), world, leverPos, null
        )

        MchprsRedstone.onUse(world, leverPos)
        runTicks(world, 1)
        assertTrue(!BlockStates.lit[world.getBlock(torchPos)])

        MchprsRedstone.onUse(world, leverPos)
        runTicks(world, 1)
        assertTrue(BlockStates.lit[world.getBlock(torchPos)])
    }

    @Test
    fun repeaterDelaysSignal() {
        val world = emptyWorld()
        for (x in 0..3) world.setBlock(BlockPos(x, 0, 0), stone)

        val leverPos = floorLever(world, BlockPos(0, 1, 0))

        val repeaterPos = BlockPos(1, 1, 0)
        interaction.placeInWorld(
            BlockStates.repeaterState(4, BlockDirection.West, locked = false, powered = false),
            world, repeaterPos, null,
        )

        val wirePos = BlockPos(2, 1, 0)
        interaction.placeInWorld(MchprsRedstone.wireStateForPlacement(world, wirePos), world, wirePos, null)

        MchprsRedstone.onUse(world, leverPos)
        runTicks(world, 3)
        assertTrue(!BlockStates.powered[world.getBlock(repeaterPos)])
        runTicks(world, 1)
        assertTrue(BlockStates.powered[world.getBlock(repeaterPos)])
        assertEquals(15, Wire.power(world.getBlock(wirePos)))
    }

    @Test
    fun repeaterLocksFromSide() {
        val world = emptyWorld()
        for (x in -1..1) for (z in -3..1) world.setBlock(BlockPos(x, 0, z), stone)

        val mainPos = BlockPos(0, 1, 0)
        interaction.placeInWorld(
            BlockStates.repeaterState(1, BlockDirection.West, locked = false, powered = false),
            world, mainPos, null,
        )

        val sidePos = BlockPos(0, 1, -1)
        interaction.placeInWorld(
            BlockStates.repeaterState(1, BlockDirection.North, locked = false, powered = false),
            world, sidePos, null,
        )

        val leverPos = floorLever(world, BlockPos(0, 1, -2))
        assertTrue(!BlockStates.locked[world.getBlock(mainPos)])

        MchprsRedstone.onUse(world, leverPos)
        runTicks(world, 4)

        assertTrue(BlockStates.powered[world.getBlock(sidePos)], "side repeater should latch on")
        assertTrue(BlockStates.locked[world.getBlock(mainPos)], "a powered side diode locks the repeater")

        MchprsRedstone.onUse(world, leverPos)
        runTicks(world, 4)
        assertTrue(!BlockStates.locked[world.getBlock(mainPos)], "unlocks once the side input drops")
    }

    @Test
    fun wirePlacesAsCrossWhenAlone() {
        val world = emptyWorld()
        world.setBlock(BlockPos(0, 0, 0), stone)
        assertTrue(Wire.isCross(MchprsRedstone.wireStateForPlacement(world, BlockPos(0, 1, 0))))
    }

    @Test
    fun wireConnectsTowardsNeighbour() {
        val world = emptyWorld()
        world.setBlock(BlockPos(0, 0, 0), stone)
        world.setBlock(BlockPos(1, 0, 0), stone)

        for (x in 0..1) {
            val pos = BlockPos(x, 1, 0)
            interaction.placeInWorld(MchprsRedstone.wireStateForPlacement(world, pos), world, pos, null)
        }

        assertEquals(WireSide.Side, Wire.east(world.getBlock(BlockPos(0, 1, 0))))
        assertEquals(WireSide.Side, Wire.west(world.getBlock(BlockPos(1, 1, 0))))
    }

    @Test
    fun comparatorPassesFullSignal() {
        val world = emptyWorld()
        for (x in -1..2) for (z in -1..1) world.setBlock(BlockPos(x, 0, z), stone)

        val comparatorPos = BlockPos(0, 1, 0)
        interaction.placeInWorld(
            BlockStates.comparatorState(BlockDirection.East, ComparatorMode.Subtract, false),
            world, comparatorPos, null,
        )

        val inputPos = BlockPos(1, 1, 0)
        world.setBlock(inputPos, Blocks.require("minecraft:redstone_block").defaultStateId)
        MchprsRedstone.updateSurroundingBlocks(world, inputPos)
        runTicks(world, 4)

        val entity = world.getBlockEntity(comparatorPos)
        assertTrue(entity is BlockEntity.Comparator, "expected a comparator block entity, got $entity")
        assertEquals(15, entity.outputStrength)
        assertTrue(BlockStates.powered[world.getBlock(comparatorPos)])
    }

    @Test
    fun comparatorSubtractsSideInput() {
        val world = emptyWorld()
        for (x in -1..2) for (z in -5..2) world.setBlock(BlockPos(x, 0, z), stone)

        val comparatorPos = BlockPos(0, 1, 0)
        interaction.placeInWorld(
            BlockStates.comparatorState(BlockDirection.East, ComparatorMode.Subtract, false),
            world, comparatorPos, null,
        )
        world.setBlock(BlockPos(1, 1, 0), Blocks.require("minecraft:redstone_block").defaultStateId)

        for (z in -3..-1) {
            val pos = BlockPos(0, 1, z)
            interaction.placeInWorld(MchprsRedstone.wireStateForPlacement(world, pos), world, pos, null)
        }
        val leverPos = floorLever(world, BlockPos(0, 1, -4))

        MchprsRedstone.onUse(world, leverPos)
        runTicks(world, 4)

        assertEquals(13, Wire.power(world.getBlock(BlockPos(0, 1, -1))), "three wires from the lever")

        val entity = world.getBlockEntity(comparatorPos) as? BlockEntity.Comparator
        assertEquals(2, entity?.outputStrength, "input 15 minus a side input of 13 should be 2")
    }

    @Test
    fun comparatorCompareModeKeepsFullSignal() {
        val world = emptyWorld()
        for (x in -1..2) for (z in -5..2) world.setBlock(BlockPos(x, 0, z), stone)

        val comparatorPos = BlockPos(0, 1, 0)
        interaction.placeInWorld(
            BlockStates.comparatorState(BlockDirection.East, ComparatorMode.Compare, false),
            world, comparatorPos, null,
        )
        world.setBlock(BlockPos(1, 1, 0), Blocks.require("minecraft:redstone_block").defaultStateId)

        for (z in -3..-1) {
            val pos = BlockPos(0, 1, z)
            interaction.placeInWorld(MchprsRedstone.wireStateForPlacement(world, pos), world, pos, null)
        }
        val leverPos = floorLever(world, BlockPos(0, 1, -4))

        MchprsRedstone.onUse(world, leverPos)
        runTicks(world, 4)

        val entity = world.getBlockEntity(comparatorPos) as? BlockEntity.Comparator
        assertEquals(15, entity?.outputStrength, "compare mode passes the input when it beats the side")
    }

    @Test
    fun placementValidityMatchesMchprs() {
        val world = emptyWorld()
        val glass = Blocks.require("minecraft:glass").defaultStateId

        world.setBlock(BlockPos(0, 0, 0), stone)
        world.setBlock(BlockPos(1, 0, 0), glass)

        val wire = MchprsRedstone.wireStateForPlacement(world, BlockPos(0, 1, 0))
        assertTrue(interaction.isValidPosition(wire, world, BlockPos(0, 1, 0)))
        assertTrue(interaction.isValidPosition(wire, world, BlockPos(1, 1, 0)), "glass is a cube in mchprs")
        assertTrue(!interaction.isValidPosition(wire, world, BlockPos(5, 1, 0)), "no support below")
    }

    @Test
    fun torchBelowNonCubeIsRejected() {
        val world = emptyWorld()
        assertTrue(!interaction.isValidPosition(BlockStates.torchState(true), world, BlockPos(0, 5, 0)))
    }

    @Test
    fun weakPowerThroughSolidBlock() {
        val world = emptyWorld()
        world.setBlock(BlockPos(0, 1, 0), stone)
        world.setBlock(BlockPos(0, 2, 0), BlockStates.leverState(LeverFace.Floor, BlockDirection.North, true))
        assertEquals(15, MchprsRedstone.getRedstonePower(world, BlockPos(0, 1, 0), BlockFace.Top))
    }

    @Test
    fun destroyingWireClearsNeighbourConnections() {
        val world = emptyWorld()
        world.setBlock(BlockPos(0, 0, 0), stone)
        world.setBlock(BlockPos(1, 0, 0), stone)

        for (x in 0..1) {
            val pos = BlockPos(x, 1, 0)
            interaction.placeInWorld(MchprsRedstone.wireStateForPlacement(world, pos), world, pos, null)
        }

        val target = BlockPos(1, 1, 0)
        interaction.destroy(world.getBlock(target), world, target)

        assertEquals(Blocks.airState, world.getBlock(target))
        assertTrue(Wire.isCross(world.getBlock(BlockPos(0, 1, 0))), "lone wire should fall back to a cross")
    }
}
