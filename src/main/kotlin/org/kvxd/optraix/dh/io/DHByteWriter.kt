package org.kvxd.optraix.dh.io

import java.io.ByteArrayOutputStream
import java.io.DataOutputStream

internal class DHByteWriter(initialSize: Int = 128) {
    private val bytes = ByteArrayOutputStream(initialSize)
    private val output = DataOutputStream(bytes)

    fun byte(value: Int) = output.writeByte(value)

    fun boolean(value: Boolean) = output.writeBoolean(value)

    fun short(value: Int) = output.writeShort(value)

    fun int(value: Int) = output.writeInt(value)

    fun long(value: Long) = output.writeLong(value)

    fun bytes(value: ByteArray) = output.write(value)

    fun bytes(value: ByteArray, offset: Int, length: Int) = output.write(value, offset, length)

    fun shortString(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        require(encoded.size <= UShort.MAX_VALUE.toInt()) { "DH string is too long" }
        short(encoded.size)
        bytes(encoded)
    }

    fun string(value: String) {
        val encoded = value.toByteArray(Charsets.UTF_8)
        int(encoded.size)
        bytes(encoded)
    }

    fun toByteArray(): ByteArray = bytes.toByteArray()
}
