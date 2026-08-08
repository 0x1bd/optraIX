package org.kvxd.optraix.net

import net.lenni0451.mcstructs.nbt.NbtTag
import net.lenni0451.mcstructs.nbt.tags.ByteTag
import net.lenni0451.mcstructs.nbt.tags.CompoundTag
import net.lenni0451.mcstructs.nbt.tags.StringTag
import org.kvxd.optraix.nbt.compoundOf
import org.kvxd.optraix.nbt.listOfTags

object Text {

    fun of(text: String): NbtTag = compoundOf("text" to StringTag(text))

    fun colored(text: String, color: String): NbtTag =
        compoundOf("text" to StringTag(text), "color" to StringTag(color))

    fun bold(text: String, color: String): NbtTag = compoundOf(
        "text" to StringTag(text),
        "color" to StringTag(color),
        "bold" to ByteTag(1),
    )

    fun columns(left: String, right: String, targetWidth: Int): NbtTag {
        val (spaces, boldSpaces) = ChatFont.padding(targetWidth - ChatFont.width(left))
        val parts = ArrayList<CompoundTag>(4)
        parts += compoundOf("text" to StringTag(left), "color" to StringTag(Aqua))
        if (spaces > 0) parts += compoundOf("text" to StringTag(" ".repeat(spaces)))
        if (boldSpaces > 0) {
            parts += compoundOf("text" to StringTag(" ".repeat(boldSpaces)), "bold" to ByteTag(1))
        }
        parts += compoundOf("text" to StringTag(right), "color" to StringTag(Gray))
        return compoundOf("text" to StringTag(""), "extra" to listOfTags(parts))
    }

    const val Gray = "gray"
    const val Red = "red"
    const val Green = "green"
    const val Yellow = "yellow"
    const val Aqua = "aqua"
    const val White = "white"
    const val Gold = "gold"
}
