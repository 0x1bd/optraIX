package org.kvxd.gogolmc.command.worldedit

import org.kvxd.gogolmc.block.BlockKind
import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.block.Blocks
import org.kvxd.gogolmc.block.property.BlockFacing
import org.kvxd.gogolmc.net.ChunkPackets
import org.kvxd.gogolmc.net.GogolServer
import org.kvxd.gogolmc.player.Player
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.worldedit.Clipboard
import org.kvxd.gogolmc.worldedit.Region
import org.kvxd.gogolmc.worldedit.UndoEntry

class WorldEdit(private val server: GogolServer) {

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
        val positions = ArrayList<Long>()
        val previous = ArrayList<Int>()
        var changed = 0
        region.forEach { pos ->
            val next = mutator(pos) ?: return@forEach
            val current = server.world.getBlock(pos)
            if (current == next) return@forEach
            positions.add(pos.asLong())
            previous.add(current)
            server.world.setBlockSilent(pos, next)
            changed++
        }
        if (changed > 0) {
            player.pushUndo(UndoEntry(positions.toLongArray(), previous.toIntArray()))
            refresh(positions)
            resend(region.min, region.max)
        }
        return changed
    }

    fun refresh(positions: Collection<Long>) {
        if (positions.isEmpty()) return
        if (positions.size > MaxRefreshedBlocks) return

        val interaction = server.interaction
        val engine = server.engine
        val world = server.world

        for (packed in positions) {
            interaction.changeSurroundingBlocks(world, BlockPos.unpack(packed))
        }
        for (packed in positions) {
            val pos = BlockPos.unpack(packed)
            val state = world.getBlock(pos)
            if (BlockStates.kindOf(state) == BlockKind.RedstoneWire) engine.updateWireNeighbors(world, pos)
            else engine.updateSurroundingBlocks(world, pos)
        }
    }

    fun resend(min: BlockPos, max: BlockPos) {
        for (chunkX in (min.x shr 4)..(max.x shr 4)) {
            for (chunkZ in (min.z shr 4)..(max.z shr 4)) {
                val key = (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)
                val packet = ChunkPackets.encode(server.world.chunkAt(chunkX, chunkZ))
                for (target in server.players) {
                    if (key in target.loadedChunks) target.connection.send(packet)
                }
            }
        }
    }

    fun copy(player: Player, region: Region): Clipboard {
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
                    clipboard[x, y, z] = server.world.getBlock(pos)
                    server.world.getBlockEntity(pos)?.let {
                        clipboard.blockEntities[clipboard.index(x, y, z)] = it
                    }
                }
            }
        }
        player.clipboard = clipboard
        return clipboard
    }

    fun paste(player: Player, clipboard: Clipboard, includeAir: Boolean): Int {
        val origin = player.blockPos
        val base = BlockPos(
            origin.x + clipboard.offset.x,
            origin.y + clipboard.offset.y,
            origin.z + clipboard.offset.z,
        )
        val positions = ArrayList<Long>()
        val previous = ArrayList<Int>()
        var changed = 0
        for (y in 0 until clipboard.sizeY) {
            for (z in 0 until clipboard.sizeZ) {
                for (x in 0 until clipboard.sizeX) {
                    val state = clipboard[x, y, z]
                    if (!includeAir && state == Blocks.airState) continue
                    val pos = BlockPos(base.x + x, base.y + y, base.z + z)
                    val current = server.world.getBlock(pos)
                    if (current != state) {
                        positions.add(pos.asLong())
                        previous.add(current)
                        server.world.setBlockSilent(pos, state)
                        changed++
                    }
                    clipboard.blockEntities[clipboard.index(x, y, z)]?.let {
                        server.world.setBlockEntity(pos, it)
                    }
                }
            }
        }
        if (changed > 0) {
            player.pushUndo(UndoEntry(positions.toLongArray(), previous.toIntArray()))
            refresh(positions)
            resend(
                base,
                BlockPos(
                    base.x + clipboard.sizeX - 1,
                    base.y + clipboard.sizeY - 1,
                    base.z + clipboard.sizeZ - 1,
                ),
            )
        }
        return changed
    }

    fun stack(player: Player, region: Region, count: Int, facing: BlockFacing): Int {
        val step = regionOffset(facing, region)
        val positions = ArrayList<Long>()
        val previous = ArrayList<Int>()
        var changed = 0
        for (iteration in 1..count) {
            val shift = BlockPos(step.x * iteration, step.y * iteration, step.z * iteration)
            region.forEach { pos ->
                val state = server.world.getBlock(pos)
                val target = BlockPos(pos.x + shift.x, pos.y + shift.y, pos.z + shift.z)
                val current = server.world.getBlock(target)
                if (current != state) {
                    positions.add(target.asLong())
                    previous.add(current)
                    server.world.setBlockSilent(target, state)
                    changed++
                }
                server.world.getBlockEntity(pos)?.let { server.world.setBlockEntity(target, it) }
            }
        }
        if (changed > 0) {
            player.pushUndo(UndoEntry(positions.toLongArray(), previous.toIntArray()))
            refresh(positions)
            resendSpan(region, BlockPos(step.x * count, step.y * count, step.z * count))
        }
        return changed
    }

    fun move(player: Player, region: Region, count: Int, facing: BlockFacing): Int {
        val step = unitOffset(facing)
        val shift = BlockPos(step.x * count, step.y * count, step.z * count)
        val snapshot = HashMap<Long, Int>()
        region.forEach { pos -> snapshot[pos.asLong()] = server.world.getBlock(pos) }

        val positions = ArrayList<Long>()
        val previous = ArrayList<Int>()
        val air = Blocks.airState

        region.forEach { pos ->
            val current = server.world.getBlock(pos)
            if (current != air) {
                positions.add(pos.asLong())
                previous.add(current)
                server.world.setBlockSilent(pos, air)
            }
        }
        for ((packed, state) in snapshot) {
            val pos = BlockPos.unpack(packed)
            val target = BlockPos(pos.x + shift.x, pos.y + shift.y, pos.z + shift.z)
            val current = server.world.getBlock(target)
            if (current != state) {
                positions.add(target.asLong())
                previous.add(current)
                server.world.setBlockSilent(target, state)
            }
        }
        player.pushUndo(UndoEntry(positions.toLongArray(), previous.toIntArray()))
        refresh(positions)
        resendSpan(region, shift)

        player.selectionOne = BlockPos(region.min.x + shift.x, region.min.y + shift.y, region.min.z + shift.z)
        player.selectionTwo = BlockPos(region.max.x + shift.x, region.max.y + shift.y, region.max.z + shift.z)
        return region.volume.toInt()
    }

    fun undo(player: Player): Int? {
        val entry = player.undoStack.poll() ?: return null
        player.redoStack.push(swap(entry))
        return entry.positions.size
    }

    fun redo(player: Player): Int? {
        val entry = player.redoStack.poll() ?: return null
        player.undoStack.push(swap(entry))
        return entry.positions.size
    }

    private fun swap(entry: UndoEntry): UndoEntry {
        val replaced = IntArray(entry.positions.size)
        if (entry.positions.isEmpty()) return UndoEntry(entry.positions, replaced)
        var min = BlockPos.unpack(entry.positions[0])
        var max = min
        for (index in entry.positions.indices) {
            val pos = BlockPos.unpack(entry.positions[index])
            replaced[index] = server.world.getBlock(pos)
            server.world.setBlockSilent(pos, entry.states[index])
            min = min.min(pos)
            max = max.max(pos)
        }
        refresh(entry.positions.toList())
        resend(min, max)
        return UndoEntry(entry.positions, replaced)
    }

    private fun resendSpan(region: Region, shift: BlockPos) {
        resend(
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

    private companion object {
        const val MaxRefreshedBlocks = 250_000
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
