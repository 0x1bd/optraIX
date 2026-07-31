package org.kvxd.optraix.redstone.optraix

import org.kvxd.optraix.block.BlockKind
import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.block.property.BlockFace
import org.kvxd.optraix.redstone.RedstoneEngine
import org.kvxd.optraix.redstone.RedstoneStats
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.World

class OptraIxEngine : RedstoneEngine {

    override val name: String = "optraix"

    override val stats: RedstoneStats = RedstoneStats()

    var circuit: OptraIxCircuit? = null
        private set

    var compileMillis: Long = 0
        private set

    var lastError: String? = null
        private set

    val compiled: Boolean get() = circuit != null

    var paused: Boolean = false
        private set

    fun pause(world: GameWorld) {
        decompile(world)
        paused = true
    }

    fun resume() {
        paused = false
    }

    fun compile(world: GameWorld): Boolean {
        paused = false
        decompile(world)
        val started = System.nanoTime()
        return try {
            val built = OptraIxCompiler.compile(world)
            built.settle()
            built.flush(world)
            world.clearTicks()
            circuit = built
            compileMillis = (System.nanoTime() - started) / 1_000_000
            lastError = null
            true
        } catch (cause: OptraIxCompileException) {
            lastError = cause.message
            circuit = null
            false
        }
    }

    fun decompile(world: GameWorld) {
        val active = circuit ?: return
        circuit = null
        active.writeAll(world)
        active.exportPendingTicks(world)
    }

    var changeCounter: Long = 0
        private set

    private fun invalidate(world: World) {
        changeCounter++
        if (circuit != null && world is GameWorld) decompile(world)
    }

    override fun tickWorld(world: GameWorld) {
        val active = circuit
        if (active == null) {
            world.tickScheduled { pos -> MchprsRedstone.tick(world, pos) }
            return
        }
        active.tick()
        active.flush(world, ioOnly = true)
        stats.blockUpdates = active.nodeUpdates
        stats.scheduledTicks = active.nodeTicks
        stats.wireUpdates = 0
    }

    override fun onUse(world: World, pos: BlockPos): Boolean {
        val active = circuit
        if (active != null) {
            val node = active.nodeAt(pos)
            if (node >= 0) {
                when (active.typeOf(node)) {
                    NodeType.Lever -> {
                        active.setSource(node, !active.isOn(node))
                        active.flush(world, ioOnly = true)
                        return true
                    }
                    NodeType.Button -> {
                        active.pressButton(node)
                        active.flush(world, ioOnly = true)
                        return true
                    }
                }
            }
            if (!mutatesRedstone(world.getBlock(pos))) return MchprsRedstone.onUse(world, pos)
            invalidate(world)
        }
        return MchprsRedstone.onUse(world, pos)
    }

    private fun mutatesRedstone(state: Int): Boolean = when (BlockStates.kindOf(state)) {
        BlockKind.Repeater, BlockKind.Comparator, BlockKind.Lever,
        BlockKind.Button, BlockKind.RedstoneWire, BlockKind.NoteBlock -> true
        else -> false
    }

    override fun setPressurePlate(world: GameWorld, pos: BlockPos, powered: Boolean) {
        val active = circuit
        if (active != null) {
            val node = active.nodeAt(pos)
            if (node >= 0 && active.typeOf(node) == NodeType.PressurePlate) {
                active.setSource(node, powered)
                active.flush(world, ioOnly = true)
                return
            }
        }
        val state = world.getBlock(pos)
        if ((BlockStates.pressurePlatePowered(state) ?: return) == powered) return
        world.setBlock(pos, BlockStates.withPowered(state, powered))
        MchprsRedstone.updateSurroundingBlocks(world, pos)
        MchprsRedstone.updateSurroundingBlocks(world, pos.offset(BlockFace.Bottom))
    }

    override fun update(world: World, pos: BlockPos) {
        invalidate(world)
        MchprsRedstone.update(world, pos)
    }

    override fun tick(world: World, pos: BlockPos) {
        invalidate(world)
        MchprsRedstone.tick(world, pos)
    }

    override fun updateSurroundingBlocks(world: World, pos: BlockPos) {
        invalidate(world)
        MchprsRedstone.updateSurroundingBlocks(world, pos)
    }

    override fun updateWireNeighbors(world: World, pos: BlockPos) {
        invalidate(world)
        MchprsRedstone.updateWireNeighbors(world, pos)
    }

    override fun wireStateOnNeighborChanged(world: World, pos: BlockPos, state: Int, side: BlockFace): Int {
        invalidate(world)
        return MchprsRedstone.wireStateOnNeighborChanged(world, pos, state, side)
    }

    override fun wireStateForPlacement(world: World, pos: BlockPos): Int {
        invalidate(world)
        return MchprsRedstone.wireStateForPlacement(world, pos)
    }

    override fun repeaterStateForPlacement(world: World, pos: BlockPos, facing: BlockDirection): Int {
        invalidate(world)
        return MchprsRedstone.repeaterStateForPlacement(world, pos, facing)
    }

    override fun redstoneLampShouldBeLit(world: World, pos: BlockPos): Boolean =
        MchprsRedstone.redstoneLampShouldBeLit(world, pos)

    override fun getRedstonePower(world: World, pos: BlockPos, facing: BlockFace): Int =
        MchprsRedstone.getRedstonePower(world, pos, facing)

    override fun isDiode(state: Int): Boolean = MchprsRedstone.isDiode(state)
}
