package org.kvxd.optraix.dh.io

import java.io.ByteArrayInputStream
import java.io.DataInputStream

internal class DHByteReader(data: ByteArray) {
    private val bytes = ByteArrayInputStream(data)
    private val input = DataInputStream(bytes)

    fun unsignedShort(): Int = input.readUnsignedShort()

    fun int(): Int = input.readInt()

    fun long(): Long = input.readLong()

    fun boolean(): Boolean = input.readBoolean()

    fun shortString(): String = readString(unsignedShort())

    fun optionalLong(): Long? = if (boolean()) long() else null

    private fun readString(length: Int): String {
        require(length <= bytes.available()) { "DH string length $length exceeds remaining payload" }
        return ByteArray(length).also(input::readFully).toString(Charsets.UTF_8)
    }
}
