package org.kvxd.gogolmc.nbt

import net.benwoodworth.knbt.NbtByte
import net.benwoodworth.knbt.NbtByteArray
import net.benwoodworth.knbt.NbtCompound
import net.benwoodworth.knbt.NbtInt
import net.benwoodworth.knbt.NbtList
import net.benwoodworth.knbt.NbtLong
import net.benwoodworth.knbt.NbtShort
import net.benwoodworth.knbt.NbtString
import net.benwoodworth.knbt.NbtTag

fun NbtTag.asIntOrNull(): Int? = when (this) {
    is NbtByte -> value.toInt()
    is NbtShort -> value.toInt()
    is NbtInt -> value
    is NbtLong -> value.toInt()
    else -> null
}

fun NbtTag.asStringOrNull(): String? = (this as? NbtString)?.value

fun NbtCompound.compound(key: String): NbtCompound? = this[key] as? NbtCompound

fun NbtCompound.list(key: String): NbtList<*>? = this[key] as? NbtList<*>

fun NbtCompound.int(key: String): Int? = this[key]?.asIntOrNull()

fun NbtCompound.string(key: String): String? = this[key]?.asStringOrNull()

fun NbtCompound.byteArray(key: String): ByteArray? = (this[key] as? NbtByteArray)?.toByteArray()
