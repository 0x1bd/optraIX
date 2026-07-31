package org.kvxd.optraix.net

import net.benwoodworth.knbt.NbtByte
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag

object Text {

    fun of(text: String): NbtTag = NbtCompound(mapOf("text" to NbtString(text)))

    fun colored(text: String, color: String): NbtTag = NbtCompound(
        mapOf("text" to NbtString(text), "color" to NbtString(color))
    )

    fun bold(text: String, color: String): NbtTag = NbtCompound(
        mapOf(
            "text" to NbtString(text),
            "color" to NbtString(color),
            "bold" to NbtByte(1),
        )
    )

    fun columns(left: String, right: String, targetWidth: Int): NbtTag {
        val (spaces, boldSpaces) = ChatFont.padding(targetWidth - ChatFont.width(left))
        val parts = ArrayList<NbtCompound>(4)
        parts += NbtCompound(mapOf("text" to NbtString(left), "color" to NbtString(Aqua)))
        if (spaces > 0) parts += NbtCompound(mapOf("text" to NbtString(" ".repeat(spaces))))
        if (boldSpaces > 0) {
            parts += NbtCompound(
                mapOf("text" to NbtString(" ".repeat(boldSpaces)), "bold" to NbtByte(1))
            )
        }
        parts += NbtCompound(mapOf("text" to NbtString(right), "color" to NbtString(Gray)))
        return NbtCompound(mapOf("text" to NbtString(""), "extra" to NbtList(parts)))
    }

    const val Gray = "gray"
    const val Red = "red"
    const val Green = "green"
    const val Yellow = "yellow"
    const val Aqua = "aqua"
    const val White = "white"
    const val Gold = "gold"
}
