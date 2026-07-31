package org.kvxd.gogolmc.redstone.mchprs

import org.kvxd.gogolmc.block.property.BlockDirection
import org.kvxd.gogolmc.block.property.BlockFace
import org.kvxd.gogolmc.block.BlockKind
import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.block.property.LeverFace
import org.kvxd.gogolmc.block.property.WireSide
import org.kvxd.gogolmc.redstone.RedstoneEngine
import org.kvxd.gogolmc.redstone.RedstoneStats
import org.kvxd.gogolmc.world.BlockEntity
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.TickPriority
import org.kvxd.gogolmc.world.World

object MchprsRedstone : RedstoneEngine {

    override val name: String = "mchprs"

    override val stats: RedstoneStats = RedstoneStats()

    fun boolToSs(value: Boolean): Int = if (value) 15 else 0

    fun getWeakPower(state: Int, world: World, pos: BlockPos, side: BlockFace, dustPower: Boolean): Int {
        BlockStates.pressurePlatePowered(state)?.let { if (it) return 15 }

        return when (BlockStates.kindOf(state)) {
            BlockKind.RedstoneTorch ->
                if (BlockStates.lit[state] && side != BlockFace.Top) 15 else 0
            BlockKind.RedstoneWallTorch -> {
                val facing = BlockStates.directionOf(state)
                if (BlockStates.lit[state] && facing != null && facing.blockFace() != side) 15 else 0
            }
            BlockKind.RedstoneBlock -> 15
            BlockKind.Lever -> if (BlockStates.powered[state]) 15 else 0
            BlockKind.Button -> if (BlockStates.powered[state]) 15 else 0
            BlockKind.Repeater -> {
                val facing = BlockStates.directionOf(state)
                if (facing != null && facing.blockFace() == side && BlockStates.powered[state]) 15 else 0
            }
            BlockKind.Comparator -> {
                val facing = BlockStates.directionOf(state)
                if (facing != null && facing.blockFace() == side) {
                    (world.getBlockEntity(pos) as? BlockEntity.Comparator)?.outputStrength ?: 0
                } else 0
            }
            BlockKind.RedstoneWire -> {
                if (!dustPower) 0
                else when (side) {
                    BlockFace.Top -> BlockStates.wirePower[state].toInt()
                    BlockFace.Bottom -> 0
                    else -> {
                        val direction = side.unwrapDirection()
                        val regulated = Wire.getRegulatedSides(state, world, pos)
                        if (Wire.getCurrentSide(regulated, direction.opposite()).isNone) 0
                        else BlockStates.wirePower[state].toInt()
                    }
                }
            }
            else -> 0
        }
    }

    fun getStrongPower(state: Int, world: World, pos: BlockPos, side: BlockFace, dustPower: Boolean): Int {
        BlockStates.pressurePlatePowered(state)?.let { if (it && side == BlockFace.Top) return 15 }

        return when (BlockStates.kindOf(state)) {
            BlockKind.RedstoneTorch ->
                if (BlockStates.lit[state] && side == BlockFace.Bottom) 15 else 0
            BlockKind.RedstoneWallTorch ->
                if (BlockStates.lit[state] && side == BlockFace.Bottom) 15 else 0
            BlockKind.Lever, BlockKind.Button -> {
                val face = BlockStates.leverFaceOf(state)
                val facing = BlockStates.directionOf(state)
                val matches = when (side) {
                    BlockFace.Top -> face == LeverFace.Floor
                    BlockFace.Bottom -> face == LeverFace.Ceiling
                    else -> face == LeverFace.Wall && facing == side.unwrapDirection()
                }
                boolToSs(matches && BlockStates.powered[state])
            }
            BlockKind.RedstoneWire, BlockKind.Repeater, BlockKind.Comparator ->
                getWeakPower(state, world, pos, side, dustPower)
            else -> 0
        }
    }

    private fun getMaxStrongPower(world: World, pos: BlockPos, dustPower: Boolean): Int {
        var max = 0
        for (side in BlockFace.All) {
            val offset = pos.offset(side)
            val state = world.getBlock(offset)
            max = maxOf(max, getStrongPower(state, world, offset, side, dustPower))
        }
        return max
    }

    override fun getRedstonePower(world: World, pos: BlockPos, facing: BlockFace): Int =
        getRedstonePower(world.getBlock(pos), world, pos, facing)

    fun getRedstonePower(state: Int, world: World, pos: BlockPos, facing: BlockFace): Int =
        if (BlockStates.isSolid(state)) getMaxStrongPower(world, pos, true)
        else getWeakPower(state, world, pos, facing, true)

    fun getRedstonePowerNoDust(state: Int, world: World, pos: BlockPos, facing: BlockFace): Int =
        if (BlockStates.isSolid(state)) getMaxStrongPower(world, pos, false)
        else getWeakPower(state, world, pos, facing, false)

    fun torchShouldBeOff(world: World, pos: BlockPos): Boolean {
        val bottom = pos.offset(BlockFace.Bottom)
        return getRedstonePower(world.getBlock(bottom), world, bottom, BlockFace.Top) > 0
    }

    fun wallTorchShouldBeOff(world: World, pos: BlockPos, direction: BlockDirection): Boolean {
        val wallPos = pos.offset(direction.opposite().blockFace())
        return getRedstonePower(
            world.getBlock(wallPos), world, wallPos, direction.opposite().blockFace()
        ) > 0
    }

    override fun redstoneLampShouldBeLit(world: World, pos: BlockPos): Boolean {
        for (face in BlockFace.All) {
            val neighbor = pos.offset(face)
            if (getRedstonePower(world.getBlock(neighbor), world, neighbor, face) > 0) return true
        }
        return false
    }

    fun diodeGetInputStrength(world: World, pos: BlockPos, facing: BlockDirection): Int {
        val inputPos = pos.offset(facing.blockFace())
        val inputState = world.getBlock(inputPos)
        var power = getRedstonePower(inputState, world, inputPos, facing.blockFace())
        if (power == 0 && BlockStates.kindOf(inputState) == BlockKind.RedstoneWire) {
            power = BlockStates.wirePower[inputState].toInt()
        }
        return power
    }

    override fun update(world: World, pos: BlockPos) {
        update(world.getBlock(pos), world, pos)
    }

    fun update(state: Int, world: World, pos: BlockPos) {
        stats.blockUpdates++
        when (BlockStates.kindOf(state)) {
            BlockKind.RedstoneWire -> Wire.onNeighborUpdated(state, world, pos)
            BlockKind.RedstoneTorch -> {
                if (BlockStates.lit[state] == torchShouldBeOff(world, pos) && !world.pendingTickAt(pos)) {
                    world.scheduleTick(pos, 1, TickPriority.Normal)
                    stats.scheduledTicks++
                }
            }
            BlockKind.RedstoneWallTorch -> {
                val facing = BlockStates.directionOf(state) ?: return
                if (BlockStates.lit[state] == wallTorchShouldBeOff(world, pos, facing) &&
                    !world.pendingTickAt(pos)
                ) {
                    world.scheduleTick(pos, 1, TickPriority.Normal)
                    stats.scheduledTicks++
                }
            }
            BlockKind.Repeater -> Repeater.onNeighborUpdated(state, world, pos)
            BlockKind.Comparator -> Comparator.update(state, world, pos)
            BlockKind.RedstoneLamp -> {
                val lit = BlockStates.lit[state]
                val shouldBeLit = redstoneLampShouldBeLit(world, pos)
                if (lit && !shouldBeLit) {
                    world.scheduleTick(pos, 2, TickPriority.Normal)
                    stats.scheduledTicks++
                } else if (!lit && shouldBeLit) {
                    world.setBlock(pos, BlockStates.lampState(true))
                }
            }
            BlockKind.IronTrapdoor -> {
                val powered = BlockStates.powered[state]
                val shouldBePowered = redstoneLampShouldBeLit(world, pos)
                if (powered != shouldBePowered) {
                    val type = Blocks.typeOf(state)
                    var newState = type.withValue(
                        state, type.requireProperty("powered"), if (shouldBePowered) "true" else "false"
                    )
                    newState = type.withValue(
                        newState, type.requireProperty("open"), if (shouldBePowered) "true" else "false"
                    )
                    world.setBlock(pos, newState)
                }
            }
            BlockKind.NoteBlock -> {
                val note = BlockStates.note[state].toInt()
                val shouldBePowered = redstoneLampShouldBeLit(world, pos)
                val live = world.getBlock(pos)
                if (BlockStates.kindOf(live) != BlockKind.NoteBlock) return
                val powered = BlockStates.powered[live]
                if (powered != shouldBePowered) {
                    val instrument = NoteBlock.instrumentAt(world, pos)
                    val newState = BlockStates.noteBlockState(instrument, note, shouldBePowered)
                    if (shouldBePowered && NoteBlock.isUnblocked(world, pos)) {
                        NoteBlock.playNote(world, pos, instrument, note)
                    }
                    world.setBlock(pos, newState)
                }
            }
            else -> Unit
        }
    }

    override fun tick(world: World, pos: BlockPos) {
        tick(world.getBlock(pos), world, pos)
    }

    fun tick(state: Int, world: World, pos: BlockPos) {
        when (BlockStates.kindOf(state)) {
            BlockKind.Repeater -> Repeater.tick(state, world, pos)
            BlockKind.Comparator -> Comparator.tick(state, world, pos)
            BlockKind.RedstoneTorch -> {
                val lit = BlockStates.lit[state]
                val shouldBeOff = torchShouldBeOff(world, pos)
                if (lit && shouldBeOff) {
                    world.setBlock(pos, BlockStates.torchState(false))
                    updateSurroundingBlocks(world, pos)
                } else if (!lit && !shouldBeOff) {
                    world.setBlock(pos, BlockStates.torchState(true))
                    updateSurroundingBlocks(world, pos)
                }
            }
            BlockKind.RedstoneWallTorch -> {
                val facing = BlockStates.directionOf(state) ?: return
                val lit = BlockStates.lit[state]
                val shouldBeOff = wallTorchShouldBeOff(world, pos, facing)
                if (lit && shouldBeOff) {
                    world.setBlock(pos, BlockStates.wallTorchState(false, facing))
                    updateSurroundingBlocks(world, pos)
                } else if (!lit && !shouldBeOff) {
                    world.setBlock(pos, BlockStates.wallTorchState(true, facing))
                    updateSurroundingBlocks(world, pos)
                }
            }
            BlockKind.RedstoneLamp -> {
                if (BlockStates.lit[state] && !redstoneLampShouldBeLit(world, pos)) {
                    world.setBlock(pos, BlockStates.lampState(false))
                }
            }
            BlockKind.Button -> {
                if (BlockStates.powered[state]) {
                    val face = BlockStates.leverFaceOf(state)
                    val facing = BlockStates.directionOf(state) ?: BlockDirection.North
                    world.setBlock(pos, BlockStates.withPowered(state, false))
                    updateSurroundingBlocks(world, pos)
                    updateAttachedFace(world, pos, face, facing)
                }
            }
            else -> Unit
        }
    }

    private fun updateAttachedFace(world: World, pos: BlockPos, face: LeverFace, facing: BlockDirection) {
        when (face) {
            LeverFace.Ceiling -> updateSurroundingBlocks(world, pos.offset(BlockFace.Top))
            LeverFace.Floor -> updateSurroundingBlocks(world, pos.offset(BlockFace.Bottom))
            LeverFace.Wall -> updateSurroundingBlocks(world, pos.offset(facing.opposite().blockFace()))
        }
    }

    override fun updateWireNeighbors(world: World, pos: BlockPos) {
        for (direction in BlockFace.All) {
            val neighborPos = pos.offset(direction)
            update(world.getBlock(neighborPos), world, neighborPos)
            for (nDirection in BlockFace.All) {
                val nNeighborPos = neighborPos.offset(nDirection)
                update(world.getBlock(nNeighborPos), world, nNeighborPos)
            }
        }
    }

    override fun updateSurroundingBlocks(world: World, pos: BlockPos) {
        for (direction in BlockFace.All) {
            val neighborPos = pos.offset(direction)
            update(world.getBlock(neighborPos), world, neighborPos)

            val upPos = neighborPos.offset(BlockFace.Top)
            update(world.getBlock(upPos), world, upPos)

            val downPos = neighborPos.offset(BlockFace.Bottom)
            update(world.getBlock(downPos), world, downPos)
        }
    }

    override fun isDiode(state: Int): Boolean = when (BlockStates.kindOf(state)) {
        BlockKind.Repeater, BlockKind.Comparator -> true
        else -> false
    }

    override fun wireStateOnNeighborChanged(world: World, pos: BlockPos, state: Int, side: BlockFace): Int =
        Wire.onNeighborChanged(state, world, pos, side)

    override fun wireStateForPlacement(world: World, pos: BlockPos): Int =
        Wire.getStateForPlacement(world, pos)

    override fun repeaterStateForPlacement(world: World, pos: BlockPos, facing: BlockDirection): Int =
        Repeater.getStateForPlacement(world, pos, facing)

    override fun onUse(world: World, pos: BlockPos): Boolean {
        val state = world.getBlock(pos)
        return when (BlockStates.kindOf(state)) {
            BlockKind.Repeater -> {
                var delay = BlockStates.delay[state] + 1
                if (delay > 4) delay -= 4
                val facing = BlockStates.directionOf(state) ?: BlockDirection.North
                world.setBlock(
                    pos,
                    BlockStates.repeaterState(
                        delay, facing, BlockStates.locked[state], BlockStates.powered[state]
                    )
                )
                true
            }
            BlockKind.Comparator -> {
                val facing = BlockStates.directionOf(state) ?: BlockDirection.North
                val mode = BlockStates.comparatorModeOf(state).toggle()
                val newState = BlockStates.comparatorState(facing, mode, BlockStates.powered[state])
                Comparator.tick(newState, world, pos)
                world.setBlock(pos, newState)
                true
            }
            BlockKind.Lever -> {
                val face = BlockStates.leverFaceOf(state)
                val facing = BlockStates.directionOf(state) ?: BlockDirection.North
                world.setBlock(pos, BlockStates.leverState(face, facing, !BlockStates.powered[state]))
                updateSurroundingBlocks(world, pos)
                updateAttachedFace(world, pos, face, facing)
                true
            }
            BlockKind.Button -> {
                if (!BlockStates.powered[state]) {
                    val face = BlockStates.leverFaceOf(state)
                    val facing = BlockStates.directionOf(state) ?: BlockDirection.North
                    world.setBlock(pos, BlockStates.withPowered(state, true))
                    world.scheduleTick(pos, BlockStates.buttonDuration(state), TickPriority.Normal)
                    updateSurroundingBlocks(world, pos)
                    updateAttachedFace(world, pos, face, facing)
                }
                true
            }
            BlockKind.RedstoneWire -> {
                if (Wire.isDot(state) || Wire.isCross(state)) {
                    val power = BlockStates.wirePower[state].toInt()
                    var newState = if (Wire.isCross(state)) {
                        BlockStates.wireState(WireSide.None, WireSide.None, WireSide.None, WireSide.None, power)
                    } else {
                        Wire.makeCross(power)
                    }
                    newState = Wire.getRegulatedSides(newState, world, pos)
                    if (state != newState) {
                        world.setBlock(pos, newState)
                        updateWireNeighbors(world, pos)
                        return true
                    }
                }
                false
            }
            BlockKind.NoteBlock -> {
                val note = (BlockStates.note[state] + 1) % 25
                val instrument = NoteBlock.instrumentAt(world, pos)
                world.setBlock(
                    pos, BlockStates.noteBlockState(instrument, note, BlockStates.powered[state])
                )
                if (NoteBlock.isUnblocked(world, pos)) {
                    NoteBlock.playNote(world, pos, instrument, note)
                }
                true
            }
            else -> false
        }
    }
}
