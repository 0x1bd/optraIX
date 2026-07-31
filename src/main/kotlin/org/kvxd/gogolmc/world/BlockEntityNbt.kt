package org.kvxd.gogolmc.world

import kotlin.math.floor
import net.benwoodworth.knbt.NbtByte
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtInt
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag
import org.kvxd.gogolmc.block.Items
import org.kvxd.gogolmc.nbt.asIntOrNull
import org.kvxd.gogolmc.nbt.asStringOrNull

object BlockEntityNbt {

    fun hasBlockEntityTag(nbt: NbtTag?): Boolean =
        nbt is NbtCompound && nbt.containsKey("BlockEntityTag")

    fun fromItemTag(nbt: NbtTag, blockName: String): BlockEntity? {
        if (nbt !is NbtCompound) return null
        val tag = nbt["BlockEntityTag"] as? NbtCompound ?: return null
        val id = (nbt["Id"] ?: nbt["id"])?.asStringOrNull() ?: blockName
        return fromNbt(id, tag)
    }

    fun fromNbt(id: String, nbt: NbtCompound): BlockEntity? =
        when (id.removePrefix("minecraft:")) {
            "comparator" -> BlockEntity.Comparator(nbt["OutputSignal"]?.asIntOrNull() ?: 0)
            "furnace" -> loadContainer(nbt, ContainerKind.Furnace)
            "barrel" -> loadContainer(nbt, ContainerKind.Barrel)
            "hopper" -> loadContainer(nbt, ContainerKind.Hopper)
            "sign" -> loadSign(nbt)
            else -> null
        }

    private fun loadSign(nbt: NbtCompound): BlockEntity {
        if (nbt.containsKey("Text1")) {
            val rows = (1..4).map { nbt["Text$it"]?.asStringOrNull() ?: "" }
            return BlockEntity.Sign(rows, listOf("", "", "", ""))
        }
        fun side(key: String): List<String> {
            val compound = nbt[key] as? NbtCompound ?: return listOf("", "", "", "")
            val messages = compound["messages"] as? NbtList<*> ?: return listOf("", "", "", "")
            return (0 until 4).map { messages.getOrNull(it)?.asStringOrNull() ?: "" }
        }
        return BlockEntity.Sign(side("front_text"), side("back_text"))
    }

    private fun loadContainer(nbt: NbtCompound, kind: ContainerKind): BlockEntity {
        val items = nbt["Items"] as? NbtList<*> ?: return BlockEntity.Container(kind, 0, emptyList())
        var fullnessSum = 0.0f
        val inventory = ArrayList<InventoryEntry>(items.size)
        for (entry in items) {
            val compound = entry as? NbtCompound ?: continue
            val count = (compound["Count"] ?: compound["count"])?.asIntOrNull() ?: continue
            val slot = (compound["Slot"] ?: compound["slot"])?.asIntOrNull() ?: continue
            val name = (compound["Id"] ?: compound["id"])?.asStringOrNull() ?: continue
            val item = Items.byName(name)
            inventory += InventoryEntry(
                id = item?.protocolId ?: (Items.protocolIdOf("minecraft:redstone") ?: 0),
                slot = slot,
                count = count,
                nbt = compound["tag"],
            )
            fullnessSum += count.toFloat() / (item?.maxStackSize ?: 64).toFloat()
        }
        val override = floor(
            (if (fullnessSum > 0.0f) 1.0f else 0.0f) + (fullnessSum / kind.slots.toFloat()) * 14.0f
        ).toInt()
        return BlockEntity.Container(kind, override, inventory)
    }

    fun toNbt(entity: BlockEntity): NbtCompound = when (entity) {
        is BlockEntity.Comparator -> NbtCompound(
            mapOf(
                "OutputSignal" to NbtInt(entity.outputStrength),
                "id" to NbtString("minecraft:comparator"),
            )
        )
        is BlockEntity.Sign -> NbtCompound(
            mapOf(
                "is_waxed" to NbtByte(0),
                "front_text" to NbtCompound(
                    mapOf(
                        "has_glowing_text" to NbtByte(0),
                        "color" to NbtString("black"),
                        "messages" to NbtList(entity.frontRows.map { NbtString(jsonText(it)) }),
                    )
                ),
                "back_text" to NbtCompound(
                    mapOf(
                        "has_glowing_text" to NbtByte(0),
                        "color" to NbtString("black"),
                        "messages" to NbtList(entity.backRows.map { NbtString(jsonText(it)) }),
                    )
                ),
                "id" to NbtString("minecraft:sign"),
            )
        )
        is BlockEntity.Container -> NbtCompound(
            mapOf(
                "id" to NbtString(entity.kind.id),
                "Items" to NbtList(
                    entity.inventory.map {
                        NbtCompound(
                            mapOf(
                                "Count" to NbtByte(it.count.toByte()),
                                "id" to NbtString(Items.nameOf(it.id)),
                                "Slot" to NbtByte(it.slot.toByte()),
                            )
                        )
                    }
                ),
            )
        )
    }

    fun typeId(entity: BlockEntity): Int = when (entity) {
        is BlockEntity.Comparator -> 18
        is BlockEntity.Sign -> 7
        is BlockEntity.Container -> when (entity.kind) {
            ContainerKind.Furnace -> 0
            ContainerKind.Barrel -> 26
            ContainerKind.Hopper -> 17
        }
    }

    private fun jsonText(text: String): String =
        if (text.startsWith("{") || text.startsWith("[")) text
        else "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}"
}
