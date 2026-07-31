package org.kvxd.optraix.tools

import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtLongArray
import org.kvxd.optraix.block.Blocks
import org.kvxd.optraix.nbt.NbtIo
import org.kvxd.optraix.nbt.compound
import org.kvxd.optraix.nbt.int
import org.kvxd.optraix.nbt.list
import org.kvxd.optraix.nbt.string
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WORLD_HEIGHT
import org.kvxd.optraix.world.WorldGenerator
import org.kvxd.optraix.world.WorldStorage
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream

object AnvilImport {

    class Stats {
        var chunks = 0
        var sections = 0
        var blocks = 0L
        var comparators = 0
        var containers = 0
        var filledContainers = 0
        var signs = 0
        var minY = Int.MAX_VALUE
        var maxY = Int.MIN_VALUE
        var minX = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var minZ = Int.MAX_VALUE
        var maxZ = Int.MIN_VALUE
        val unknown = HashMap<String, Int>()
        val redstone = HashMap<String, Int>()
    }

    private val redstoneNames = setOf(
        "minecraft:redstone_wire", "minecraft:repeater", "minecraft:comparator",
        "minecraft:redstone_torch", "minecraft:redstone_wall_torch", "minecraft:redstone_lamp",
        "minecraft:lever", "minecraft:stone_button", "minecraft:redstone_block",
        "minecraft:observer", "minecraft:target", "minecraft:tripwire_hook",
        "minecraft:note_block", "minecraft:iron_trapdoor", "minecraft:sticky_piston",
        "minecraft:piston", "minecraft:slime_block", "minecraft:honey_block",
    )

    fun regionFiles(worldDir: File): List<File> =
        (File(worldDir, "region").listFiles()?.filter { it.name.endsWith(".mca") } ?: emptyList()).sorted()

    private fun readChunk(file: RandomAccessFile, offset: Int, length: Int): NbtCompound? {
        if (offset <= 0 || length <= 0) return null
        file.seek(offset * 4096L)
        val declared = file.readInt()
        if (declared <= 0) return null
        val compression = file.readByte().toInt()
        val payload = ByteArray(declared - 1)
        file.readFully(payload)
        val stream = when (compression) {
            1 -> GZIPInputStream(ByteArrayInputStream(payload))
            2 -> InflaterInputStream(ByteArrayInputStream(payload))
            3 -> ByteArrayInputStream(payload)
            else -> return null
        }
        return runCatching { NbtIo.readNamed(DataInputStream(stream.buffered())) }.getOrNull()
    }

    private fun stateOf(entry: NbtCompound, cache: HashMap<String, Int>, stats: Stats): Int {
        val name = entry.string("Name") ?: return Blocks.airState
        val properties = entry.compound("Properties")
        val key = if (properties == null || properties.isEmpty()) {
            name
        } else {
            buildString {
                append(name).append('[')
                properties.entries.sortedBy { it.key }.joinTo(this, ",") { (k, v) ->
                    "$k=${v.toString().trim('"')}"
                }
                append(']')
            }
        }
        return cache.getOrPut(key) {
            val parsed = Blocks.parse(key)
            if (parsed == null) {
                stats.unknown[name] = (stats.unknown[name] ?: 0) + 1
                Blocks.byName(name)?.defaultStateId ?: Blocks.airState
            } else {
                parsed
            }
        }
    }

    fun import(worldDir: File, yShift: Int, world: GameWorld, stats: Stats) {
        val cache = HashMap<String, Int>()
        for (region in regionFiles(worldDir)) {
            val parts = region.name.split('.')
            val regionX = parts[1].toInt()
            val regionZ = parts[2].toInt()
            RandomAccessFile(region, "r").use { handle ->
                val header = ByteArray(4096)
                handle.readFully(header)
                for (index in 0 until 1024) {
                    val base = index * 4
                    val offset = ((header[base].toInt() and 0xFF) shl 16) or
                        ((header[base + 1].toInt() and 0xFF) shl 8) or
                        (header[base + 2].toInt() and 0xFF)
                    val sectors = header[base + 3].toInt() and 0xFF
                    val root = readChunk(handle, offset, sectors) ?: continue
                    val chunkX = regionX * 32 + (index and 31)
                    val chunkZ = regionZ * 32 + (index shr 5)
                    importChunk(root, chunkX, chunkZ, yShift, world, cache, stats)
                }
            }
        }
    }

    private fun importChunk(
        root: NbtCompound,
        chunkX: Int,
        chunkZ: Int,
        yShift: Int,
        world: GameWorld,
        cache: HashMap<String, Int>,
        stats: Stats,
    ) {
        val sections = root.list("sections") ?: return
        var placed = false
        for (raw in sections) {
            val section = raw as? NbtCompound ?: continue
            val sectionY = section.int("Y") ?: continue
            val states = section.compound("block_states") ?: continue
            val palette = states.list("palette") ?: continue
            if (palette.isEmpty()) continue
            val ids = IntArray(palette.size) { stateOf(palette[it] as NbtCompound, cache, stats) }
            val data = (states["data"] as? NbtLongArray)?.toLongArray()

            if (data == null) {
                val single = ids[0]
                if (single == Blocks.airState) continue
                for (local in 0 until 4096) {
                    val y = (sectionY shl 4) + (local shr 8) + yShift
                    if (y < 0 || y >= WORLD_HEIGHT) continue
                    val x = chunkX * 16 + (local and 15)
                    val z = chunkZ * 16 + ((local shr 4) and 15)
                    world.setBlockSilent(BlockPos(x, y, z), single)
                    placed = true
                    stats.blocks++
                    record(stats, x, y, z, single)
                }
                stats.sections++
                continue
            }

            var bits = 32 - Integer.numberOfLeadingZeros(maxOf(1, palette.size - 1))
            if (bits < 4) bits = 4
            val perLong = 64 / bits
            val mask = (1L shl bits) - 1L
            for (local in 0 until 4096) {
                val longIndex = local / perLong
                if (longIndex >= data.size) break
                val shift = (local % perLong) * bits
                val id = ((data[longIndex] ushr shift) and mask).toInt()
                if (id >= ids.size) continue
                val state = ids[id]
                if (state == Blocks.airState) continue
                val y = (sectionY shl 4) + (local shr 8) + yShift
                if (y < 0 || y >= WORLD_HEIGHT) continue
                val x = chunkX * 16 + (local and 15)
                val z = chunkZ * 16 + ((local shr 4) and 15)
                world.setBlockSilent(BlockPos(x, y, z), state)
                placed = true
                stats.blocks++
                record(stats, x, y, z, state)
            }
            stats.sections++
        }

        for (raw in root.list("block_entities") ?: NbtList(emptyList<NbtCompound>())) {
            val entity = raw as? NbtCompound ?: continue
            val id = entity.string("id") ?: continue
            val x = entity.int("x") ?: continue
            val y = (entity.int("y") ?: continue) + yShift
            val z = entity.int("z") ?: continue
            if (y < 0 || y >= WORLD_HEIGHT) continue
            val loaded = org.kvxd.optraix.world.BlockEntityNbt.fromNbt(id.removePrefix("minecraft:"), entity)
                ?: continue
            world.setBlockEntity(BlockPos(x, y, z), loaded)
            when (loaded) {
                is BlockEntity.Comparator -> stats.comparators++
                is BlockEntity.Container -> {
                    stats.containers++
                    if (loaded.inventory.isNotEmpty()) stats.filledContainers++
                }
                is BlockEntity.Sign -> stats.signs++
            }
        }
        if (placed) stats.chunks++
    }

    private fun record(stats: Stats, x: Int, y: Int, z: Int, state: Int) {
        if (y < stats.minY) stats.minY = y
        if (y > stats.maxY) stats.maxY = y
        if (x < stats.minX) stats.minX = x
        if (x > stats.maxX) stats.maxX = x
        if (z < stats.minZ) stats.minZ = z
        if (z > stats.maxZ) stats.maxZ = z
        val name = Blocks.nameOf(state)
        if (name in redstoneNames) stats.redstone[name] = (stats.redstone[name] ?: 0) + 1
    }

    @JvmStatic
    fun main(args: Array<String>) {
        val source = File(args[0])
        val target = File(args[1])
        val yShift = args.getOrNull(2)?.toInt() ?: 0
        println("importing ${source.name} (yShift=$yShift)")
        val started = System.nanoTime()
        val world = GameWorld(WorldGenerator(Blocks.airState, 0))
        val stats = Stats()
        import(source, yShift, world, stats)
        println("chunks    ${stats.chunks}")
        println("sections  ${stats.sections}")
        println("blocks    ${stats.blocks}")
        println("x range   ${stats.minX}..${stats.maxX}")
        println("y range   ${stats.minY}..${stats.maxY}")
        println("z range   ${stats.minZ}..${stats.maxZ}")
        println("comparators ${stats.comparators}")
        println("containers  ${stats.containers} (${stats.filledContainers} with items)")
        println("signs       ${stats.signs}")
        println("redstone:")
        for ((name, count) in stats.redstone.entries.sortedByDescending { it.value }) {
            println("  $name  $count")
        }
        if (stats.unknown.isNotEmpty()) {
            println("unmapped block states:")
            for ((name, count) in stats.unknown.entries.sortedByDescending { it.value }.take(20)) {
                println("  $name  $count")
            }
        }
        target.parentFile?.mkdirs()
        val written = WorldStorage.save(world, target)
        println("wrote $written chunks to ${target.path} in ${(System.nanoTime() - started) / 1_000_000}ms")
    }
}
