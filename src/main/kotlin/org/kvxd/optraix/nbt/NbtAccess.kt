package org.kvxd.optraix.nbt

import net.lenni0451.mcstructs.nbt.NbtTag
import net.lenni0451.mcstructs.nbt.tags.ByteArrayTag
import net.lenni0451.mcstructs.nbt.tags.ByteTag
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import net.lenni0451.mcstructs.nbt.tags.IntTag
import net.lenni0451.mcstructs.nbt.tags.ListTag
import net.lenni0451.mcstructs.nbt.tags.LongTag
import net.lenni0451.mcstructs.nbt.tags.ShortTag
import net.lenni0451.mcstructs.nbt.tags.StringTag

fun NbtTag.asIntOrNull(): Int? = when (this) {
    is ByteTag -> value.toInt()
    is ShortTag -> value.toInt()
    is IntTag -> value
    is LongTag -> value.toInt()
    else -> null
}

fun NbtTag.asStringOrNull(): String? = (this as? StringTag)?.value

fun CompoundTag.tag(key: String): NbtTag? = get<NbtTag>(key)

fun CompoundTag.compound(key: String): CompoundTag? = tag(key) as? CompoundTag

fun CompoundTag.list(key: String): ListTag<*>? = tag(key) as? ListTag<*>

fun CompoundTag.int(key: String): Int? = tag(key)?.asIntOrNull()

fun CompoundTag.string(key: String): String? = tag(key)?.asStringOrNull()

fun CompoundTag.byteArray(key: String): ByteArray? = (tag(key) as? ByteArrayTag)?.value

fun compoundOf(vararg entries: Pair<String, NbtTag>): CompoundTag = CompoundTag(linkedMapOf(*entries))

fun <T : NbtTag> listOfTags(values: List<T>): ListTag<T> = ListTag(values.toMutableList())
