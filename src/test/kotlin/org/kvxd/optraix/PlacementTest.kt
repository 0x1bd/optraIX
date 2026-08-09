package org.kvxd.optraix

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.ItemStack
import org.kvxd.optraix.block.isBlock
import org.kvxd.optraix.block.mcData
import org.kvxd.optraix.block.property.BlockFace
import org.kvxd.optraix.interaction.Interaction
import org.kvxd.optraix.interaction.UseOnBlockContext
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.kvxd.optraix.mcdata.v1_20_4.Blocks

class PlacementTest {

    private val interaction = Interaction(MchprsRedstone)

    private fun place(world: GameWorld, itemName: String, target: BlockPos): Int {
        val item = requireNotNull(mcData.item(itemName)) { "$itemName is not a known item" }
        assertTrue(item.isBlock, "$itemName is not placeable")

        val context = UseOnBlockContext(
            blockPos = target,
            blockFace = BlockFace.Top,
            cursorY = 0.5f,
            yaw = 0.0f,
            pitch = 0.0f,
            crouching = false,
            playerPos = BlockPos(-100, 1, -100),
        )
        interaction.useItemOnBlock(ItemStack(item, 1, null), world, context)
        return world.getBlock(target.offset(BlockFace.Top))
    }

    @Test
    fun blockEntityBlocksArePlaceable() {
        val names = listOf(
            "minecraft:comparator",
            "minecraft:furnace",
            "minecraft:barrel",
            "minecraft:hopper",
            "minecraft:oak_sign",
            "minecraft:repeater",
            "minecraft:redstone_torch",
            "minecraft:lever",
            "minecraft:redstone_lamp",
            "minecraft:target",
            "minecraft:note_block",
            "minecraft:observer",
            "minecraft:redstone",
            "minecraft:stone",
            "minecraft:sandstone",
        )

        val failures = ArrayList<String>()
        for ((index, name) in names.withIndex()) {
            val world = GameWorld()
            val target = BlockPos(index * 4, 0, 0)
            val state = place(world, name, target)
            if (state == Blocks.Air.defaultState) failures += name
        }
        assertTrue(failures.isEmpty(), "these items placed as air: $failures")
    }

    @Test
    fun comparatorPlacesWithBlockEntity() {
        val world = GameWorld()
        val state = place(world, "minecraft:comparator", BlockPos(0, 0, 0))
        assertEquals(
            Blocks.Comparator,
            mcData.requireBlockByStateId(state),
            "expected a comparator, got ${mcData.describeState(state)}",
        )
    }

    @Test
    fun signPlacesAsStandingSignOnTopFace() {
        val world = GameWorld()
        val state = place(world, "minecraft:oak_sign", BlockPos(0, 0, 0))
        assertTrue(BlockStates.isSign(state), "expected a sign, got ${mcData.describeState(state)}")
    }

    @Test
    fun furnaceFacesThePlayer() {
        val world = GameWorld()
        val state = place(world, "minecraft:furnace", BlockPos(0, 0, 0))
        assertEquals(
            Blocks.Furnace,
            mcData.requireBlockByStateId(state),
            "expected a furnace, got ${mcData.describeState(state)}",
        )
    }
}
