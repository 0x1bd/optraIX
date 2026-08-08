package org.kvxd.optraix.world

import kotlin.math.floor
import net.lenni0451.mcstructs.nbt.NbtTag
import net.lenni0451.mcstructs.nbt.tags.ByteTag
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import net.lenni0451.mcstructs.nbt.tags.IntTag
import net.lenni0451.mcstructs.nbt.tags.StringTag
import org.kvxd.optraix.block.Items
import org.kvxd.optraix.nbt.asIntOrNull
import org.kvxd.optraix.nbt.asStringOrNull
import org.kvxd.optraix.nbt.compound
import org.kvxd.optraix.nbt.compoundOf
import org.kvxd.optraix.nbt.list
import org.kvxd.optraix.nbt.listOfTags
import org.kvxd.optraix.nbt.tag

object BlockEntityNbt {

    fun hasBlockEntityTag(nbt: NbtTag?): Boolean =
        nbt is CompoundTag && nbt.contains("BlockEntityTag")

    fun fromItemTag(nbt: NbtTag, blockName: String): BlockEntity? {
        if (nbt !is CompoundTag) return null
        val tag = nbt.compound("BlockEntityTag") ?: return null
        val id = (tag.tag("Id") ?: tag.tag("id"))?.asStringOrNull() ?: blockName
        return fromNbt(id, tag)
    }

    fun fromNbt(id: String, nbt: CompoundTag): BlockEntity? =
        when (id.removePrefix("minecraft:")) {
            "comparator" -> BlockEntity.Comparator(nbt.tag("OutputSignal")?.asIntOrNull() ?: 0)
            "furnace" -> loadContainer(nbt, ContainerKind.Furnace)
            "barrel" -> loadContainer(nbt, ContainerKind.Barrel)
            "chest" -> loadContainer(nbt, ContainerKind.Chest)
            "hopper" -> loadContainer(nbt, ContainerKind.Hopper)
            "sign" -> loadSign(nbt)
            else -> null
        }

    private fun loadSign(nbt: CompoundTag): BlockEntity {
        if (nbt.contains("Text1")) {
            val rows = (1..4).map { nbt.tag("Text$it")?.asStringOrNull() ?: "" }
            return BlockEntity.Sign(rows, listOf("", "", "", ""))
        }
        fun side(key: String): List<String> {
            val compound = nbt.compound(key) ?: return listOf("", "", "", "")
            val messages = compound.list("messages") ?: return listOf("", "", "", "")
            return (0 until 4).map { messages.value.getOrNull(it)?.asStringOrNull() ?: "" }
        }
        return BlockEntity.Sign(side("front_text"), side("back_text"))
    }

    private fun loadContainer(nbt: CompoundTag, kind: ContainerKind): BlockEntity {
        val items = nbt.list("Items") ?: return BlockEntity.Container(kind, 0, emptyList())
        var fullnessSum = 0.0f
        val inventory = ArrayList<InventoryEntry>(items.size())
        for (entry in items) {
            val compound = entry as? CompoundTag ?: continue
            val count = (compound.tag("Count") ?: compound.tag("count"))?.asIntOrNull() ?: continue
            val slot = (compound.tag("Slot") ?: compound.tag("slot"))?.asIntOrNull() ?: continue
            val name = (compound.tag("Id") ?: compound.tag("id"))?.asStringOrNull() ?: continue
            val item = Items.byName(name)
            inventory += InventoryEntry(
                id = item?.protocolId ?: (Items.protocolIdOf("minecraft:redstone") ?: 0),
                slot = slot,
                count = count,
                nbt = compound.tag("tag"),
            )
            fullnessSum += count.toFloat() / (item?.maxStackSize ?: 64).toFloat()
        }
        val override = floor(
            (if (fullnessSum > 0.0f) 1.0f else 0.0f) + (fullnessSum / kind.slots.toFloat()) * 14.0f
        ).toInt()
        return BlockEntity.Container(kind, override, inventory)
    }

    fun toNbt(entity: BlockEntity): CompoundTag = when (entity) {
        is BlockEntity.Comparator -> compoundOf(
            "OutputSignal" to IntTag(entity.outputStrength),
            "id" to StringTag("minecraft:comparator"),
        )
        is BlockEntity.Sign -> compoundOf(
            "is_waxed" to ByteTag(0),
            "front_text" to compoundOf(
                "has_glowing_text" to ByteTag(0),
                "color" to StringTag("black"),
                "messages" to listOfTags(entity.frontRows.map { StringTag(jsonText(it)) }),
            ),
            "back_text" to compoundOf(
                "has_glowing_text" to ByteTag(0),
                "color" to StringTag("black"),
                "messages" to listOfTags(entity.backRows.map { StringTag(jsonText(it)) }),
            ),
            "id" to StringTag("minecraft:sign"),
        )
        is BlockEntity.Container -> compoundOf(
            "id" to StringTag(entity.kind.id),
            "Items" to listOfTags(
                entity.inventory.map {
                    compoundOf(
                        "Count" to ByteTag(it.count.toByte()),
                        "id" to StringTag(Items.nameOf(it.id)),
                        "Slot" to ByteTag(it.slot.toByte()),
                    )
                }
            ),
        )
    }

    fun typeId(entity: BlockEntity): Int = when (entity) {
        is BlockEntity.Comparator -> 18
        is BlockEntity.Sign -> 7
        is BlockEntity.Container -> when (entity.kind) {
            ContainerKind.Furnace -> 0
            ContainerKind.Barrel -> 26
            ContainerKind.Chest -> 1
            ContainerKind.Hopper -> 17
        }
    }

    private fun jsonText(text: String): String =
        if (text.startsWith("{") || text.startsWith("[")) text
        else "{\"text\":\"" + text.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}"
}
