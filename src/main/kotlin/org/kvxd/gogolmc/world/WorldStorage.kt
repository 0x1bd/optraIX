package org.kvxd.gogolmc.world

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

object WorldStorage {

    private const val Magic = 0x474F474F
    private const val Version = 2

    fun save(world: GameWorld, file: File): Int {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        var written = 0

        DataOutputStream(GZIPOutputStream(temporary.outputStream().buffered())).use { output ->
            output.writeInt(Magic)
            output.writeInt(Version)
            val chunks = world.snapshotChunks()
            output.writeInt(chunks.size)
            for (chunk in chunks) {
                writeChunk(output, chunk)
                written++
            }

            val ticks = world.snapshotTicks()
            output.writeInt(ticks.size)
            for (tick in ticks) {
                output.writeInt(tick.pos.x)
                output.writeInt(tick.pos.y)
                output.writeInt(tick.pos.z)
                output.writeInt(tick.ticksLeft)
                output.writeByte(tick.priority.ordinal)
            }
        }

        if (file.exists()) file.delete()
        temporary.renameTo(file)
        return written
    }

    fun load(world: GameWorld, file: File): Int {
        if (!file.isFile) return 0
        var restored = 0
        DataInputStream(GZIPInputStream(file.inputStream().buffered())).use { input ->
            if (input.readInt() != Magic) throw IllegalStateException("${file.name} is not a gogolmc world")
            val version = input.readInt()
            if (version !in 1..Version) throw IllegalStateException("unsupported world version $version")
            val count = input.readInt()
            repeat(count) {
                readChunk(input, world)
                restored++
            }

            if (version >= 2) {
                val tickCount = input.readInt()
                val entries = ArrayList<TickEntry>(tickCount)
                repeat(tickCount) {
                    val pos = BlockPos(input.readInt(), input.readInt(), input.readInt())
                    val ticksLeft = input.readInt()
                    val priority = TickPriority.entries[input.readByte().toInt()]
                    entries.add(TickEntry(ticksLeft, priority, pos))
                }
                world.restoreTicks(entries)
            }
        }
        return restored
    }

    private fun writeChunk(output: DataOutputStream, chunk: Chunk) {
        output.writeInt(chunk.x)
        output.writeInt(chunk.z)
        for (index in 0 until SECTION_COUNT) {
            val section = chunk.sections[index]
            if (section == null) {
                output.writeBoolean(false)
                continue
            }
            output.writeBoolean(true)
            output.writeByte(section.bitsPerEntry)
            output.writeInt(section.blockCount)
            if (section.isDirect) {
                output.writeInt(0)
            } else {
                output.writeInt(section.paletteSize)
                for (entry in 0 until section.paletteSize) output.writeInt(section.palette[entry])
            }
            output.writeInt(section.data.size)
            for (value in section.data) output.writeLong(value)
        }

        output.writeInt(chunk.blockEntities.size)
        for ((key, entity) in chunk.blockEntities) {
            output.writeInt(key)
            when (entity) {
                is BlockEntity.Comparator -> {
                    output.writeByte(0)
                    output.writeInt(entity.outputStrength)
                }
                is BlockEntity.Sign -> {
                    output.writeByte(1)
                    for (row in 0 until 4) output.writeUTF(entity.frontRows.getOrElse(row) { "" })
                    for (row in 0 until 4) output.writeUTF(entity.backRows.getOrElse(row) { "" })
                }
                is BlockEntity.Container -> {
                    output.writeByte(2)
                    output.writeByte(entity.kind.ordinal)
                    output.writeInt(entity.comparatorOverride)
                    output.writeInt(entity.inventory.size)
                    for (item in entity.inventory) {
                        output.writeInt(item.id)
                        output.writeInt(item.slot)
                        output.writeInt(item.count)
                    }
                }
            }
        }
    }

    private fun readChunk(input: DataInputStream, world: GameWorld) {
        val chunkX = input.readInt()
        val chunkZ = input.readInt()
        val chunk = world.replaceChunk(chunkX, chunkZ)

        for (index in 0 until SECTION_COUNT) {
            if (!input.readBoolean()) continue
            val bits = input.readByte().toInt()
            val blockCount = input.readInt()
            val paletteSize = input.readInt()
            val palette = IntArray(paletteSize) { input.readInt() }
            val data = LongArray(input.readInt()) { input.readLong() }
            chunk.sections[index] = ChunkSection.restore(bits, palette, paletteSize, data, blockCount)
        }

        val entityCount = input.readInt()
        repeat(entityCount) {
            val key = input.readInt()
            val entity: BlockEntity = when (input.readByte().toInt()) {
                0 -> BlockEntity.Comparator(input.readInt())
                1 -> BlockEntity.Sign(
                    List(4) { input.readUTF() },
                    List(4) { input.readUTF() },
                )
                else -> {
                    val kind = ContainerKind.entries[input.readByte().toInt()]
                    val override = input.readInt()
                    val size = input.readInt()
                    val inventory = List(size) {
                        InventoryEntry(input.readInt(), input.readInt(), input.readInt(), null)
                    }
                    BlockEntity.Container(kind, override, inventory)
                }
            }
            chunk.blockEntities[key] = entity
        }
    }
}
