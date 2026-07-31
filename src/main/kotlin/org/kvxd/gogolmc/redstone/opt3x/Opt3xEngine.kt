package org.kvxd.gogolmc.redstone.opt3x

import org.kvxd.gogolmc.block.property.BlockDirection
import org.kvxd.gogolmc.block.property.BlockFace
import org.kvxd.gogolmc.redstone.RedstoneEngine
import org.kvxd.gogolmc.redstone.RedstoneStats
import org.kvxd.gogolmc.redstone.mchprs.MchprsRedstone
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.GameWorld
import org.kvxd.gogolmc.world.World

class Opt3xEngine : RedstoneEngine {

    override val name: String = "opt3x"

    override val stats: RedstoneStats = RedstoneStats()

    var circuit: Opt3xCircuit? = null
        private set

    var compileMillis: Long = 0
        private set

    var lastError: String? = null
        private set

    val compiled: Boolean get() = circuit != null

    fun compile(world: GameWorld): Boolean {
        decompile(world)
        val started = System.nanoTime()
        return try {
            val built = Opt3xCompiler.compile(world)
            built.settle()
            built.flush(world)
            world.clearTicks()
            circuit = built
            compileMillis = (System.nanoTime() - started) / 1_000_000
            lastError = null
            true
        } catch (cause: Opt3xCompileException) {
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

    private fun invalidate(world: World) {
        if (circuit != null && world is GameWorld) decompile(world)
    }

    override fun tickWorld(world: GameWorld) {
        val active = circuit
        if (active == null) {
            world.tickScheduled { pos -> MchprsRedstone.tick(world, pos) }
            return
        }
        active.tick()
        active.flush(world)
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
                        active.flush(world)
                        return true
                    }
                    NodeType.Button -> {
                        active.pressButton(node)
                        active.flush(world)
                        return true
                    }
                }
            }
            invalidate(world)
        }
        return MchprsRedstone.onUse(world, pos)
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
