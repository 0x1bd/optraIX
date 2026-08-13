package org.kvxd.optraix.world

import org.kvxd.optraix.mcdata.v1_20_4.Blocks
import java.util.concurrent.ConcurrentHashMap

class GameWorld(
    val generator: WorldGenerator = WorldGenerator(Blocks.Sandstone.defaultState, 0),
) : World {

    private val chunks = ConcurrentHashMap<Long, Chunk>()
    private val tickQueue = ArrayList<TickEntry>()
    private val pendingPositions = HashMap<Long, Int>()

    val changedBlocks = HashSet<Long>()
    val changedBlockEntities = HashSet<Long>()

    var soundListener: ((BlockPos, Int, Int, Float, Float) -> Unit)? = null

    val loadedChunks: Int
        get() = chunks.size

    val scheduledTicks: Int
        get() = tickQueue.size

    fun chunkAt(chunkX: Int, chunkZ: Int): Chunk {
        val key = ChunkPos.key(chunkX, chunkZ)
        chunks[key]?.let { return it }
        val chunk = Chunk(chunkX, chunkZ)
        generator.generate(chunk)
        chunks[key] = chunk
        return chunk
    }

    fun chunkIfLoaded(chunkX: Int, chunkZ: Int): Chunk? = chunks[ChunkPos.key(chunkX, chunkZ)]

    fun replaceChunk(chunkX: Int, chunkZ: Int): Chunk {
        val chunk = Chunk(chunkX, chunkZ)
        chunks[ChunkPos.key(chunkX, chunkZ)] = chunk
        return chunk
    }

    fun snapshotChunks(): List<Chunk> = chunks.values.toList()

    fun copyForSave(): GameWorld {
        val copy = GameWorld(generator)

        for (chunk in chunks.values) {
            val target = copy.replaceChunk(chunk.x, chunk.z)

            for (sectionIndex in 0 until SECTION_COUNT) {
                val section = chunk.sections[sectionIndex] ?: continue

                target.sections[sectionIndex] = section.snapshotCopy()
            }

            target.blockEntities.putAll(chunk.blockEntities)
        }

        copy.restoreTicks(snapshotTicks())
        return copy
    }

    override fun getBlock(pos: BlockPos): Int {
        if (pos.y < WORLD_MIN_Y || pos.y >= WORLD_MIN_Y + WORLD_HEIGHT) return Blocks.Air.defaultState
        val chunk = chunkAt(pos.x shr 4, pos.z shr 4)
        return chunk.getBlock(pos.x and 15, pos.y, pos.z and 15)
    }

    override fun setBlock(pos: BlockPos, state: Int): Boolean {
        if (pos.y < WORLD_MIN_Y || pos.y >= WORLD_MIN_Y + WORLD_HEIGHT) return false
        val chunk = chunkAt(pos.x shr 4, pos.z shr 4)
        val changed = chunk.setBlock(pos.x and 15, pos.y, pos.z and 15, state)
        if (changed) changedBlocks += pos.asLong()
        return changed
    }

    override fun playSound(pos: BlockPos, soundId: Int, category: Int, volume: Float, pitch: Float) {
        soundListener?.invoke(pos, soundId, category, volume, pitch)
    }

    fun setBlockSilent(pos: BlockPos, state: Int): Boolean {
        if (pos.y < WORLD_MIN_Y || pos.y >= WORLD_MIN_Y + WORLD_HEIGHT) return false
        val chunk = chunkAt(pos.x shr 4, pos.z shr 4)
        return chunk.setBlock(pos.x and 15, pos.y, pos.z and 15, state)
    }

    fun setBlockEntitySilent(pos: BlockPos, entity: BlockEntity) {
        val chunk = chunkAt(pos.x shr 4, pos.z shr 4)
        chunk.blockEntities[
            chunk.blockEntityKey(pos.x and 15, pos.y, pos.z and 15)
        ] = entity
    }

    override fun getBlockEntity(pos: BlockPos): BlockEntity? {
        val chunk = chunkIfLoaded(pos.x shr 4, pos.z shr 4) ?: return null
        return chunk.blockEntities[chunk.blockEntityKey(pos.x and 15, pos.y, pos.z and 15)]
    }

    override fun setBlockEntity(pos: BlockPos, entity: BlockEntity) {
        val chunk = chunkAt(pos.x shr 4, pos.z shr 4)
        chunk.blockEntities[chunk.blockEntityKey(pos.x and 15, pos.y, pos.z and 15)] = entity
        changedBlockEntities += pos.asLong()
    }

    override fun deleteBlockEntity(pos: BlockPos) {
        val chunk = chunkIfLoaded(pos.x shr 4, pos.z shr 4) ?: return
        chunk.blockEntities.remove(chunk.blockEntityKey(pos.x and 15, pos.y, pos.z and 15))
        changedBlockEntities += pos.asLong()
    }

    @Synchronized
    override fun scheduleTick(pos: BlockPos, delay: Int, priority: TickPriority) {
        tickQueue += TickEntry(delay, priority, pos)
        val key = pos.asLong()
        pendingPositions[key] = (pendingPositions[key] ?: 0) + 1
    }

    override fun pendingTickAt(pos: BlockPos): Boolean = pendingPositions.containsKey(pos.asLong())

    @Synchronized
    fun tickScheduled(runner: (BlockPos) -> Unit) {
        if (tickQueue.isEmpty()) return
        tickQueue.sortWith(compareBy({ it.ticksLeft }, { it.priority.ordinal }))
        for (entry in tickQueue) entry.ticksLeft = maxOf(0, entry.ticksLeft - 1)
        while (tickQueue.isNotEmpty() && tickQueue[0].ticksLeft == 0) {
            val entry = tickQueue.removeAt(0)
            val key = entry.pos.asLong()
            val count = pendingPositions[key]
            if (count == null || count <= 1) pendingPositions.remove(key) else pendingPositions[key] = count - 1
            runner(entry.pos)
        }
    }

    @Synchronized
    fun clearTicks() {
        tickQueue.clear()
        pendingPositions.clear()
    }

    @Synchronized
    fun snapshotTicks(): List<TickEntry> = tickQueue.map { TickEntry(it.ticksLeft, it.priority, it.pos) }

    @Synchronized
    fun restoreTicks(entries: List<TickEntry>) {
        clearTicks()
        for (entry in entries) scheduleTick(entry.pos, entry.ticksLeft, entry.priority)
    }

    fun forEachBlockIn(first: BlockPos, second: BlockPos, action: (BlockPos) -> Unit) {
        val minX = minOf(first.x, second.x)
        val maxX = maxOf(first.x, second.x)
        val minY = maxOf(minOf(first.y, second.y), WORLD_MIN_Y)
        val maxY = minOf(maxOf(first.y, second.y), WORLD_MIN_Y + WORLD_HEIGHT - 1)
        val minZ = minOf(first.z, second.z)
        val maxZ = maxOf(first.z, second.z)
        for (x in minX..maxX) {
            for (y in minY..maxY) {
                for (z in minZ..maxZ) action(BlockPos(x, y, z))
            }
        }
    }
}
