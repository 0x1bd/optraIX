package org.kvxd.optraix.redstone.optraix

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.property.BlockDirection
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.block.property.BlockFace
import org.kvxd.optraix.redstone.RedstoneEngine
import org.kvxd.optraix.redstone.RedstoneStats
import org.kvxd.optraix.redstone.RecompilePolicy
import org.kvxd.optraix.redstone.WorldMutationContext
import org.kvxd.optraix.redstone.WorldMutationOptions
import org.kvxd.optraix.redstone.mchprs.MchprsRedstone
import org.kvxd.optraix.redstone.mchprs.Wire
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.SECTION_COUNT
import org.kvxd.optraix.world.WORLD_MIN_Y
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

    var manualCompileRequired: Boolean = false
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
        val memoryFailure = CompileMemoryPreflight.evaluate(world).failure
        if (memoryFailure != null) {
            lastError = memoryFailure
            circuit = null
            return false
        }
        val started = System.nanoTime()
        return try {
            val built = OptraIxCompiler.compile(world)
            built.settle()
            built.flush(world, ioOnly = true)
            world.clearTicks()
            circuit = built
            compileMillis = (System.nanoTime() - started) / 1_000_000
            lastError = null
            manualCompileRequired = false
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
        materializeWires(world)
        active.exportPendingTicks(world)
    }

    private fun materializeWires(world: GameWorld) {
        val wires = LongBuffer()
        for (chunk in world.snapshotChunks()) {
            for (sectionIndex in 0 until SECTION_COUNT) {
                val section = chunk.sections[sectionIndex] ?: continue
                if (section.blockCount == 0 || !sectionHasCandidates(section)) continue
                section.forEachState { slot, state ->
                    if (!BlockStates.isType(state, Blocks.RedstoneWire)) return@forEachState
                    wires.add(
                        BlockPos.pack(
                            chunk.x * 16 + (slot and 15),
                            WORLD_MIN_Y + (sectionIndex shl 4) + (slot shr 8),
                            chunk.z * 16 + ((slot shr 4) and 15),
                        )
                    )
                }
            }
        }

        repeat(16) {
            var changed = false
            for (index in 0 until wires.size) {
                val pos = BlockPos.unpack(wires[index])
                val state = world.getBlock(pos)
                if (!BlockStates.isType(state, Blocks.RedstoneWire)) continue
                val power = Wire.calculatePower(world, pos)
                if (BlockStates.wirePower[state].toInt() == power) continue
                world.setBlock(pos, BlockStates.wireWithPower(state, power))
                changed = true
            }
            if (!changed) return
        }
    }

    var mutationCounter: Long = 0
        private set

    override fun beginMutation(
        world: World,
        options: WorldMutationOptions,
    ): WorldMutationContext {
        mutationCounter++
        if (options.recompilePolicy == RecompilePolicy.Manual) manualCompileRequired = true
        if (circuit != null) {
            val gameWorld = world as? GameWorld
                ?: error("compiled circuit mutations require a GameWorld")
            decompile(gameWorld)
        }
        return WorldMutationContext(world, options)
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
            return mutate(world) { MchprsRedstone.onUse(this, pos) }
        }
        return MchprsRedstone.onUse(world, pos)
    }

    private fun mutatesRedstone(state: Int): Boolean = BlockStates.isButton(state) || when (BlockStates.typeOf(state)) {
        Blocks.Repeater, Blocks.Comparator, Blocks.Lever, Blocks.RedstoneWire, Blocks.NoteBlock -> true
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
        MchprsRedstone.update(world, pos)
    }

    override fun tick(world: World, pos: BlockPos) {
        MchprsRedstone.tick(world, pos)
    }

    override fun updateSurroundingBlocks(world: World, pos: BlockPos) {
        MchprsRedstone.updateSurroundingBlocks(world, pos)
    }

    override fun updateWireNeighbors(world: World, pos: BlockPos) {
        MchprsRedstone.updateWireNeighbors(world, pos)
    }

    override fun wireStateOnNeighborChanged(world: World, pos: BlockPos, state: Int, side: BlockFace): Int =
        MchprsRedstone.wireStateOnNeighborChanged(world, pos, state, side)

    override fun wireStateForPlacement(world: World, pos: BlockPos): Int =
        MchprsRedstone.wireStateForPlacement(world, pos)

    override fun repeaterStateForPlacement(world: World, pos: BlockPos, facing: BlockDirection): Int =
        MchprsRedstone.repeaterStateForPlacement(world, pos, facing)

    override fun redstoneLampShouldBeLit(world: World, pos: BlockPos): Boolean =
        MchprsRedstone.redstoneLampShouldBeLit(world, pos)

    override fun getRedstonePower(world: World, pos: BlockPos, facing: BlockFace): Int =
        MchprsRedstone.getRedstonePower(world, pos, facing)

    override fun isDiode(state: Int): Boolean = MchprsRedstone.isDiode(state)
}
