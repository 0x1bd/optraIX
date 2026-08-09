package org.kvxd.optraix.command.worldedit

import org.kvxd.optraix.block.BlockStates
import org.kvxd.optraix.block.property.BlockFacing
import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import org.kvxd.optraix.net.ChunkPackets
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.redstone.RecompilePolicy
import org.kvxd.optraix.redstone.WorldMutationContext
import org.kvxd.optraix.redstone.WorldMutationOptions
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.worldedit.Clipboard
import org.kvxd.optraix.worldedit.Region
import org.kvxd.optraix.worldedit.UndoEntry

class WorldEdit(private val server: OptraIxServer) {

    fun regionOf(player: Player): Region? {
        val one = player.selectionOne ?: return null
        val two = player.selectionTwo ?: return null
        return Region(one, two)
    }

    fun setPositionOne(player: Player, pos: BlockPos) {
        player.selectionOne = pos
        player.connection.sendMessage("first position set to $pos${sizeSuffix(player)}")
    }

    fun setPositionTwo(player: Player, pos: BlockPos) {
        player.selectionTwo = pos
        player.connection.sendMessage("second position set to $pos${sizeSuffix(player)}")
    }

    private fun sizeSuffix(player: Player): String {
        val region = regionOf(player) ?: return ""
        return " (${region.volume} blocks)"
    }

    fun apply(player: Player, region: Region, mutator: (BlockPos) -> Int?): Int {
        val world = server.worldFor(player)
        val positions = ArrayList<Long>()
        val previous = ArrayList<Int>()
        var changed = 0
        server.engineFor(player).mutate(world, mutationOptions(region.volume)) {
            region.forEach { pos ->
                val next = mutator(pos) ?: return@forEach
                val current = getBlock(pos)
                if (current == next) return@forEach
                positions.add(pos.asLong())
                previous.add(current)
                setBlockSilent(pos, next)
                changed++
            }
            if (changed > 0) refresh(player, this, positions)
        }
        if (changed > 0) {
            player.pushUndo(UndoEntry(positions.toLongArray(), previous.toIntArray()))
            resend(player, region.min, region.max)
        }
        return changed
    }

    private fun refresh(player: Player, mutation: WorldMutationContext, positions: Collection<Long>) {
        if (positions.isEmpty()) return
        if (positions.size > MaxRefreshedBlocks) return

        val interaction = server.interactionFor(player)
        val engine = server.engineFor(player)

        for (packed in positions) {
            interaction.changeSurroundingBlocks(mutation, BlockPos.unpack(packed))
        }
        for (packed in positions) {
            val pos = BlockPos.unpack(packed)
            val state = mutation.getBlock(pos)
            if (BlockStates.isType(state, Blocks.RedstoneWire)) engine.updateWireNeighbors(mutation, pos)
            else engine.updateSurroundingBlocks(mutation, pos)
        }
    }

    private fun refresh(player: Player, mutation: WorldMutationContext, positions: LongArray, size: Int) {
        if (size == 0) return
        if (size > MaxRefreshedBlocks) return

        val interaction = server.interactionFor(player)
        val engine = server.engineFor(player)

        for (index in 0 until size) {
            interaction.changeSurroundingBlocks(mutation, BlockPos.unpack(positions[index]))
        }
        for (index in 0 until size) {
            val pos = BlockPos.unpack(positions[index])
            val state = mutation.getBlock(pos)
            if (BlockStates.isType(state, Blocks.RedstoneWire)) engine.updateWireNeighbors(mutation, pos)
            else engine.updateSurroundingBlocks(mutation, pos)
        }
    }

    private fun resend(player: Player, min: BlockPos, max: BlockPos) {
        val runtime = server.runtimeFor(player)
        val world = runtime.world
        for (chunkX in (min.x shr 4)..(max.x shr 4)) {
            for (chunkZ in (min.z shr 4)..(max.z shr 4)) {
                val key = (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)
                val packet = ChunkPackets.encode(world.chunkAt(chunkX, chunkZ))
                for (target in server.players) {
                    if (server.runtimeFor(target) === runtime && key in target.loadedChunks) {
                        target.connection.send(packet)
                    }
                }
            }
        }
    }

    fun copy(player: Player, region: Region): Clipboard {
        val world = server.worldFor(player)
        val origin = player.blockPos
        val clipboard = Clipboard(
            sizeX = region.sizeX,
            sizeY = region.sizeY,
            sizeZ = region.sizeZ,
            offset = BlockPos(region.min.x - origin.x, region.min.y - origin.y, region.min.z - origin.z),
            blocks = IntArray(region.volume.toInt()),
        )
        for (y in 0 until region.sizeY) {
            for (z in 0 until region.sizeZ) {
                for (x in 0 until region.sizeX) {
                    val pos = BlockPos(region.min.x + x, region.min.y + y, region.min.z + z)
                    clipboard[x, y, z] = world.getBlock(pos)
                    world.getBlockEntity(pos)?.let {
                        clipboard.blockEntities[clipboard.index(x, y, z)] = it
                    }
                }
            }
        }
        player.clipboard = clipboard
        return clipboard
    }

    fun paste(player: Player, clipboard: Clipboard, includeAir: Boolean): Int {
        val world = server.worldFor(player)
        val origin = player.blockPos
        val base = BlockPos(
            origin.x + clipboard.offset.x,
            origin.y + clipboard.offset.y,
            origin.z + clipboard.offset.z,
        )
        val maximumChanges = if (!includeAir) clipboard.storedBlockCount else clipboard.volume
        val undo = if (maximumChanges <= MaxUndoBlocks) UndoAccumulator(maximumChanges) else null
        val changedChunks = HashSet<Long>()
        var undoEntry: UndoEntry? = null
        var changed = 0
        if (maximumChanges > 0) {
            server.engineFor(player).mutate(world, mutationOptions(maximumChanges.toLong())) {
                fun paste(index: Int, state: Int) {
                    val x = index % clipboard.sizeX
                    val z = (index / clipboard.sizeX) % clipboard.sizeZ
                    val y = index / (clipboard.sizeX * clipboard.sizeZ)
                    val pos = BlockPos(base.x + x, base.y + y, base.z + z)
                    val current = getBlock(pos)
                    if (current == state) return
                    if (!setBlockSilent(pos, state)) return
                    undo?.add(pos.asLong(), current)
                    changedChunks.add(chunkKey(pos.x shr 4, pos.z shr 4))
                    changed++
                }

                if (includeAir) {
                    for (y in 0 until clipboard.sizeY) {
                        for (z in 0 until clipboard.sizeZ) {
                            for (x in 0 until clipboard.sizeX) {
                                paste(clipboard.index(x, y, z), clipboard[x, y, z])
                            }
                        }
                    }
                } else {
                    clipboard.forEachNonAir(::paste)
                }

                for ((index, entity) in clipboard.blockEntities) {
                    val x = index % clipboard.sizeX
                    val z = (index / clipboard.sizeX) % clipboard.sizeZ
                    val y = index / (clipboard.sizeX * clipboard.sizeZ)
                    setBlockEntity(BlockPos(base.x + x, base.y + y, base.z + z), entity)
                }

                if (changed > 0) {
                    undoEntry = undo?.build()
                    undoEntry?.let { entry -> refresh(player, this, entry.positions, entry.size) }
                }
            }
        }

        if (changed > 0) {
            undoEntry?.let(player::pushUndo)
            resendChunks(player, changedChunks)
        }
        return changed
    }

    fun stack(player: Player, region: Region, count: Int, facing: BlockFacing): Int {
        val world = server.worldFor(player)
        val step = regionOffset(facing, region)
        val positions = ArrayList<Long>()
        val previous = ArrayList<Int>()
        var changed = 0
        server.engineFor(player).mutate(world, mutationOptions(region.volume * count)) {
            for (iteration in 1..count) {
                val shift = BlockPos(step.x * iteration, step.y * iteration, step.z * iteration)
                region.forEach { pos ->
                    val state = getBlock(pos)
                    val target = BlockPos(pos.x + shift.x, pos.y + shift.y, pos.z + shift.z)
                    val current = getBlock(target)
                    if (current != state) {
                        positions.add(target.asLong())
                        previous.add(current)
                        setBlockSilent(target, state)
                        changed++
                    }
                    getBlockEntity(pos)?.let { setBlockEntity(target, it) }
                }
            }
            if (changed > 0) refresh(player, this, positions)
        }
        if (changed > 0) {
            player.pushUndo(UndoEntry(positions.toLongArray(), previous.toIntArray()))
            resendSpan(player, region, BlockPos(step.x * count, step.y * count, step.z * count))
        }
        return changed
    }

    fun move(player: Player, region: Region, count: Int, facing: BlockFacing): Int {
        val world = server.worldFor(player)
        val step = unitOffset(facing)
        val shift = BlockPos(step.x * count, step.y * count, step.z * count)
        val positions = ArrayList<Long>()
        val previous = ArrayList<Int>()
        val air = Blocks.Air.defaultState

        server.engineFor(player).mutate(world, mutationOptions(region.volume)) {
            val snapshot = HashMap<Long, Int>()
            region.forEach { pos -> snapshot[pos.asLong()] = getBlock(pos) }

            region.forEach { pos ->
                val current = getBlock(pos)
                if (current != air) {
                    positions.add(pos.asLong())
                    previous.add(current)
                    setBlockSilent(pos, air)
                }
            }
            for ((packed, state) in snapshot) {
                val pos = BlockPos.unpack(packed)
                val target = BlockPos(pos.x + shift.x, pos.y + shift.y, pos.z + shift.z)
                val current = getBlock(target)
                if (current != state) {
                    positions.add(target.asLong())
                    previous.add(current)
                    setBlockSilent(target, state)
                }
            }
            if (positions.isNotEmpty()) refresh(player, this, positions)
        }

        player.pushUndo(UndoEntry(positions.toLongArray(), previous.toIntArray()))
        refreshSelectionAfterMove(player, region, shift)
        resendSpan(player, region, shift)
        return region.volume.toInt()
    }

    private fun refreshSelectionAfterMove(player: Player, region: Region, shift: BlockPos) {
        player.selectionOne = BlockPos(region.min.x + shift.x, region.min.y + shift.y, region.min.z + shift.z)
        player.selectionTwo = BlockPos(region.max.x + shift.x, region.max.y + shift.y, region.max.z + shift.z)
    }

    fun undo(player: Player): Int? {
        val entry = player.undoStack.poll() ?: return null
        player.redoStack.push(swap(player, entry))
        return entry.size
    }

    fun redo(player: Player): Int? {
        val entry = player.redoStack.poll() ?: return null
        player.undoStack.push(swap(player, entry))
        return entry.size
    }

    private fun swap(player: Player, entry: UndoEntry): UndoEntry {
        val world = server.worldFor(player)
        val replaced = IntArray(entry.positions.size)
        if (entry.size == 0) return UndoEntry(entry.positions, replaced, 0)
        val changedChunks = HashSet<Long>()
        server.engineFor(player).mutate(world, mutationOptions(entry.size.toLong())) {
            for (index in 0 until entry.size) {
                val pos = BlockPos.unpack(entry.positions[index])
                replaced[index] = getBlock(pos)
                setBlockSilent(pos, entry.states[index])
                changedChunks.add(chunkKey(pos.x shr 4, pos.z shr 4))
            }
            refresh(player, this, entry.positions, entry.size)
        }
        resendChunks(player, changedChunks)
        return UndoEntry(entry.positions, replaced, entry.size)
    }

    private fun resendSpan(player: Player, region: Region, shift: BlockPos) {
        resend(
            player,
            BlockPos(
                minOf(region.min.x, region.min.x + shift.x),
                minOf(region.min.y, region.min.y + shift.y),
                minOf(region.min.z, region.min.z + shift.z),
            ),
            BlockPos(
                maxOf(region.max.x, region.max.x + shift.x),
                maxOf(region.max.y, region.max.y + shift.y),
                maxOf(region.max.z, region.max.z + shift.z),
            ),
        )
    }

    fun unitOffset(facing: BlockFacing): BlockPos = when (facing) {
        BlockFacing.North -> BlockPos(0, 0, -1)
        BlockFacing.South -> BlockPos(0, 0, 1)
        BlockFacing.East -> BlockPos(1, 0, 0)
        BlockFacing.West -> BlockPos(-1, 0, 0)
        BlockFacing.Up -> BlockPos(0, 1, 0)
        BlockFacing.Down -> BlockPos(0, -1, 0)
    }

    private fun mutationOptions(changeCount: Long): WorldMutationOptions = WorldMutationOptions(
        recompilePolicy = if (changeCount > MaxAutomaticCompileBlocks) {
            RecompilePolicy.Manual
        } else {
            RecompilePolicy.Automatic
        },
    )

    private companion object {
        const val MaxRefreshedBlocks = 250_000
        const val MaxUndoBlocks = 1_000_000
        const val MaxAutomaticCompileBlocks = 250_000

        fun chunkKey(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }

    private fun resendChunks(player: Player, chunks: Collection<Long>) {
        val runtime = server.runtimeFor(player)
        val world = runtime.world
        for (key in chunks) {
            val targets = server.players.filter {
                server.runtimeFor(it) === runtime && key in it.loadedChunks
            }
            if (targets.isEmpty()) continue
            val packet = ChunkPackets.encode(world.chunkAt((key shr 32).toInt(), key.toInt()))
            for (target in targets) target.connection.send(packet)
        }
    }

    fun regionOffset(facing: BlockFacing, region: Region): BlockPos = when (facing) {
        BlockFacing.North -> BlockPos(0, 0, -region.sizeZ)
        BlockFacing.South -> BlockPos(0, 0, region.sizeZ)
        BlockFacing.East -> BlockPos(region.sizeX, 0, 0)
        BlockFacing.West -> BlockPos(-region.sizeX, 0, 0)
        BlockFacing.Up -> BlockPos(0, region.sizeY, 0)
        BlockFacing.Down -> BlockPos(0, -region.sizeY, 0)
    }
}

private class UndoAccumulator(initialCapacity: Int) {
    private var positions = LongArray(initialCapacity.coerceAtLeast(1))
    private var states = IntArray(initialCapacity.coerceAtLeast(1))
    var size: Int = 0
        private set

    fun add(position: Long, state: Int) {
        if (size == positions.size) {
            val capacity = if (size < 1 shl 20) size * 2 else size + (size shr 1)
            positions = positions.copyOf(capacity)
            states = states.copyOf(capacity)
        }
        positions[size] = position
        states[size] = state
        size++
    }

    fun build(): UndoEntry = UndoEntry(positions, states, size)
}
