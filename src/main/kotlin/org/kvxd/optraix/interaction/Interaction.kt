package org.kvxd.optraix.interaction

import net.lenni0451.mcstructs.nbt.NbtTag
import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.BlockFace
import org.kvxd.optraix.block.property.BlockFacing
import org.kvxd.optraix.block.BlockKind
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.block.property.ComparatorMode
import org.kvxd.optraix.block.property.HopperFacing
import org.kvxd.optraix.block.property.Instrument
import org.kvxd.optraix.block.Item
import org.kvxd.optraix.block.ItemStack
import org.kvxd.optraix.block.property.LeverFace
import org.kvxd.optraix.block.property.SlabType
import org.kvxd.optraix.block.property.TrapdoorHalf
import org.kvxd.optraix.redstone.RedstoneEngine
import org.kvxd.optraix.redstone.WorldMutationContext
import org.kvxd.optraix.worldedit.Directions
import org.kvxd.optraix.world.BlockEntities
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockEntityNbt
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.WORLD_HEIGHT
import org.kvxd.optraix.world.WORLD_MIN_Y
import org.kvxd.optraix.world.World
import kotlin.math.floor

class Interaction(private val redstone: RedstoneEngine) {

    fun playerDirection(yaw: Float): BlockDirection =
        when (floor(yaw / 90.0f + 0.5f).toInt() and 3) {
            0 -> BlockDirection.South
            1 -> BlockDirection.West
            2 -> BlockDirection.North
            else -> BlockDirection.East
        }

    fun onUse(state: Int, world: World, pos: BlockPos, itemInHand: Item?): ActionResult {
        if (redstone.onUse(world, pos)) return ActionResult.Success

        return when (BlockStates.kindOf(state)) {
            BlockKind.SeaPickle -> {
                val pickles = BlockStates.level[state].toInt()
                if (itemInHand?.name == "minecraft:sea_pickle" && pickles < 4) {
                    val type = Blocks.typeOf(state)
                    redstone.mutate(world) {
                        setBlock(
                            pos,
                            type.withValue(state, type.requireProperty("pickles"), (pickles + 1).toString()),
                        )
                    }
                }
                ActionResult.Success
            }
            BlockKind.EndPortalFrame -> {
                if (itemInHand?.name == "minecraft:ender_eye" && !BlockStates.eye[state]) {
                    val type = Blocks.typeOf(state)
                    redstone.mutate(world) {
                        setBlock(pos, type.withValue(state, type.requireProperty("eye"), "true"))
                        redstone.updateSurroundingBlocks(this, pos)
                    }
                    ActionResult.Success
                } else {
                    ActionResult.Pass
                }
            }
            else -> if (BlockStates.hasBlockEntity(state)) ActionResult.Success else ActionResult.Pass
        }
    }

    private fun signPlacement(context: UseOnBlockContext, wood: String): Int {
        val rotation = (floor((180.0f + context.yaw) * 16.0f / 360.0f + 0.5f).toInt()) and 15
        return when (context.blockFace) {
            BlockFace.Bottom -> Blocks.airState
            BlockFace.Top -> {
                val type = Blocks.require("minecraft:${wood}_sign")
                type.stateOf(mapOf("rotation" to rotation.toString(), "waterlogged" to "false"))
            }
            else -> {
                val type = Blocks.require("minecraft:${wood}_wall_sign")
                type.stateOf(
                    mapOf(
                        "facing" to context.blockFace.unwrapDirection().name.lowercase(),
                        "waterlogged" to "false",
                    )
                )
            }
        }
    }

    fun getStateForPlacement(world: World, pos: BlockPos, item: Item, context: UseOnBlockContext): Int {
        val name = item.name
        val simpleBlock = item.simplePlacement

        val block = when {
            name.endsWith("_sign") -> {
                val wood = name.removePrefix("minecraft:").removeSuffix("_sign")
                if (wood in BlockStates.signNames) signPlacement(context, wood) else Blocks.airState
            }
            name == "minecraft:sea_pickle" -> BlockStates.seaPickleType.stateOf(
                mapOf("pickles" to "1", "waterlogged" to "false")
            )
            name == "minecraft:furnace" -> BlockStates.furnaceType.stateOf(
                mapOf("facing" to playerDirection(context.yaw).opposite().name.lowercase(), "lit" to "false")
            )
            name.endsWith("_pressure_plate") -> {
                val type = Blocks.byName(name)
                if (type != null && BlockStates.kindOf(type.defaultStateId) == BlockKind.PressurePlate) {
                    type.stateOf(mapOf("powered" to "false"))
                } else {
                    Blocks.airState
                }
            }
            name == "minecraft:lever" -> {
                val face = when (context.blockFace) {
                    BlockFace.Top -> LeverFace.Floor
                    BlockFace.Bottom -> LeverFace.Ceiling
                    else -> LeverFace.Wall
                }
                val facing = if (face == LeverFace.Wall) context.blockFace.unwrapDirection()
                else playerDirection(context.yaw)
                BlockStates.leverState(face, facing, false)
            }
            name == "minecraft:redstone_torch" -> when (context.blockFace) {
                BlockFace.Top, BlockFace.Bottom -> BlockStates.torchState(true)
                else -> BlockStates.wallTorchState(true, context.blockFace.unwrapDirection())
            }
            name == "minecraft:tripwire_hook" -> when (context.blockFace) {
                BlockFace.Bottom, BlockFace.Top -> Blocks.airState
                else -> BlockStates.tripwireHookType.stateOf(
                    mapOf(
                        "facing" to context.blockFace.unwrapDirection().name.lowercase(),
                        "powered" to "false",
                        "attached" to "false",
                    )
                )
            }
            name.endsWith("_button") -> {
                val face = when (context.blockFace) {
                    BlockFace.Top -> LeverFace.Floor
                    BlockFace.Bottom -> LeverFace.Ceiling
                    else -> LeverFace.Wall
                }
                val facing = if (face == LeverFace.Wall) context.blockFace.unwrapDirection()
                else playerDirection(context.yaw)
                BlockStates.buttonStateFor(Blocks.require(name), face, facing, false)
            }
            name == "minecraft:redstone_lamp" ->
                BlockStates.lampState(redstone.redstoneLampShouldBeLit(world, pos))
            name == "minecraft:hopper" -> BlockStates.hopperType.stateOf(
                mapOf("enabled" to "false", "facing" to HopperFacing.Down.name.lowercase())
            )
            name == "minecraft:repeater" -> redstone.repeaterStateForPlacement(
                world, pos, playerDirection(context.yaw).opposite()
            )
            name == "minecraft:comparator" -> BlockStates.comparatorState(
                playerDirection(context.yaw).opposite(), ComparatorMode.Compare, false
            )
            name == "minecraft:redstone" -> redstone.wireStateForPlacement(world, pos)
            name == "minecraft:barrel" -> BlockStates.barrelType.stateOf(
                mapOf("facing" to BlockFacing.Up.name.lowercase(), "open" to "false")
            )
            name == "minecraft:target" -> BlockStates.targetType.stateOf(mapOf("power" to "0"))
            name == "minecraft:smooth_stone_slab" || name == "minecraft:quartz_slab" ->
                Blocks.require(name).stateOf(
                    mapOf("type" to SlabType.Top.name.lowercase(), "waterlogged" to "false")
                )
            name == "minecraft:iron_trapdoor" -> when (context.blockFace) {
                BlockFace.Bottom -> BlockStates.trapdoorState(
                    playerDirection(context.yaw).opposite(), TrapdoorHalf.Top, false, false, false
                )
                BlockFace.Top -> BlockStates.trapdoorState(
                    playerDirection(context.yaw).opposite(), TrapdoorHalf.Bottom, false, false, false
                )
                else -> BlockStates.trapdoorState(
                    context.blockFace.unwrapDirection(),
                    if (context.cursorY > 0.5f) TrapdoorHalf.Top else TrapdoorHalf.Bottom,
                    false,
                    false,
                    false,
                )
            }
            name == "minecraft:note_block" -> BlockStates.noteBlockState(Instrument.Harp, 0, false)
            name == "minecraft:observer" -> BlockStates.observerType.stateOf(
                mapOf(
                    "facing" to Directions.facing(context.yaw, context.pitch).name.lowercase(),
                    "powered" to "false",
                )
            )
            name == "minecraft:bone_block" ->
                Blocks.require(name).stateOf(mapOf("axis" to "y"))
            name == "minecraft:hay_block" ->
                Blocks.require(name).stateOf(mapOf("axis" to "y"))
            name == "minecraft:end_portal_frame" -> BlockStates.endPortalFrameType.stateOf(
                mapOf("eye" to "false", "facing" to playerDirection(context.yaw).opposite().name.lowercase())
            )
            else -> Blocks.airState
        }

        val result = if (simpleBlock >= 0) simpleBlock else block
        return if (isValidPosition(result, world, pos)) result else Blocks.airState
    }

    fun placeInWorld(state: Int, world: World, pos: BlockPos, nbt: NbtTag?) {
        redstone.mutate(world) {
            placeInWorld(state, this, pos, nbt)
        }
    }

    private fun placeInWorld(
        state: Int,
        mutation: WorldMutationContext,
        pos: BlockPos,
        nbt: NbtTag?,
    ) {
        mutation.setBlock(pos, state)
        if (BlockStates.hasBlockEntity(state)) {
            val fromItem = nbt?.let { BlockEntityNbt.fromItemTag(it, Blocks.nameOf(state)) }
            if (fromItem != null) mutation.setBlockEntity(pos, fromItem)
            else BlockEntities.ensure(mutation, pos)
        }
        changeSurroundingBlocks(mutation, pos)
        if (BlockStates.kindOf(state) == BlockKind.RedstoneWire) {
            redstone.updateWireNeighbors(mutation, pos)
        } else {
            redstone.updateSurroundingBlocks(mutation, pos)
        }
    }

    fun destroy(state: Int, world: World, pos: BlockPos) {
        redstone.mutate(world) {
            destroy(state, this, pos)
        }
    }

    private fun destroy(state: Int, mutation: WorldMutationContext, pos: BlockPos) {
        if (BlockStates.hasBlockEntity(state)) mutation.deleteBlockEntity(pos)

        when (BlockStates.kindOf(state)) {
            BlockKind.RedstoneWire -> {
                mutation.setBlock(pos, Blocks.airState)
                changeSurroundingBlocks(mutation, pos)
                redstone.updateWireNeighbors(mutation, pos)
            }
            BlockKind.Lever -> {
                val face = BlockStates.leverFaceOf(state)
                val facing = BlockStates.directionOf(state) ?: BlockDirection.North
                mutation.setBlock(pos, Blocks.airState)
                val target = when (face) {
                    LeverFace.Ceiling -> pos.offset(BlockFace.Top)
                    LeverFace.Floor -> pos.offset(BlockFace.Bottom)
                    LeverFace.Wall -> pos.offset(facing.opposite().blockFace())
                }
                changeSurroundingBlocks(mutation, target)
                redstone.updateSurroundingBlocks(mutation, target)
            }
            else -> {
                mutation.setBlock(pos, Blocks.airState)
                changeSurroundingBlocks(mutation, pos)
                redstone.updateSurroundingBlocks(mutation, pos)
            }
        }
    }

    fun isValidPosition(state: Int, world: World, pos: BlockPos): Boolean {
        val kind = BlockStates.kindOf(state)
        val checkBottom = when (kind) {
            BlockKind.RedstoneWire, BlockKind.Comparator, BlockKind.Repeater, BlockKind.RedstoneTorch -> true
            BlockKind.Lever, BlockKind.Button -> BlockStates.leverFaceOf(state) == LeverFace.Floor
            BlockKind.Sign -> true
            else -> false
        }

        val checkTop = when (kind) {
            BlockKind.Lever, BlockKind.Button -> BlockStates.leverFaceOf(state) == LeverFace.Ceiling
            else -> false
        }

        val checkParent = BlockStates.wallSignFacing(state) ?: when (kind) {
            BlockKind.TripwireHook -> BlockStates.directionOf(state)
            BlockKind.Button, BlockKind.Lever ->
                if (BlockStates.leverFaceOf(state) == LeverFace.Wall) BlockStates.directionOf(state) else null
            else -> null
        }

        return when {
            checkBottom -> BlockStates.isCube(world.getBlock(pos.offset(BlockFace.Bottom)))
            checkTop -> BlockStates.isCube(world.getBlock(pos.offset(BlockFace.Top)))
            checkParent != null ->
                BlockStates.isCube(world.getBlock(pos.offset(checkParent.opposite().blockFace())))
            else -> true
        }
    }

    private fun change(
        state: Int,
        mutation: WorldMutationContext,
        pos: BlockPos,
        direction: BlockFace,
    ) {
        if (!isValidPosition(state, mutation, pos)) {
            destroy(state, mutation, pos)
            return
        }
        if (BlockStates.kindOf(state) == BlockKind.RedstoneWire) {
            val newState = redstone.wireStateOnNeighborChanged(mutation, pos, state, direction)
            if (mutation.setBlock(pos, newState)) redstone.updateWireNeighbors(mutation, pos)
        }
    }

    fun changeSurroundingBlocks(world: World, pos: BlockPos) {
        redstone.mutate(world) {
            changeSurroundingBlocks(this, pos)
        }
    }

    internal fun changeSurroundingBlocks(mutation: WorldMutationContext, pos: BlockPos) {
        for (direction in BlockFace.All) {
            val neighborPos = pos.offset(direction)
            change(mutation.getBlock(neighborPos), mutation, neighborPos, direction)

            val upPos = neighborPos.offset(BlockFace.Top)
            change(mutation.getBlock(upPos), mutation, upPos, direction)

            val downPos = neighborPos.offset(BlockFace.Bottom)
            change(mutation.getBlock(downPos), mutation, downPos, direction)
        }
    }

    fun useItemOnBlock(item: ItemStack, world: World, context: UseOnBlockContext): UseResult {
        val usePos = context.blockPos
        val useBlock = world.getBlock(usePos)
        val blockPos = context.blockPos.offset(context.blockFace)
        val topPos = BlockPos(context.playerPos.x, context.playerPos.y + 1, context.playerPos.z)
        if (blockPos == context.playerPos || blockPos == topPos) return UseResult(false)

        val canPlace = item.item.isBlock && BlockStates.canPlaceIn(world.getBlock(blockPos))

        if (!context.crouching && onUse(useBlock, world, usePos, item.item) == ActionResult.Success) {
            val container = if (BlockEntities.ensure(world, usePos) is BlockEntity.Container) usePos else null
            return UseResult(false, openContainerAt = container)
        }

        if (canPlace && blockPos.y >= WORLD_MIN_Y && blockPos.y < WORLD_MIN_Y + WORLD_HEIGHT) {
            var needsSignEditor = false
            redstone.mutate(world) {
                val state = getStateForPlacement(this, blockPos, item.item, context)
                needsSignEditor = (BlockStates.isSign(state) || BlockStates.isWallSign(state)) &&
                    !BlockEntityNbt.hasBlockEntityTag(item.nbt)
                placeInWorld(state, this, blockPos, item.nbt)
            }
            return UseResult(false, openSignEditorAt = if (needsSignEditor) blockPos else null)
        }
        return UseResult(true)
    }
}
