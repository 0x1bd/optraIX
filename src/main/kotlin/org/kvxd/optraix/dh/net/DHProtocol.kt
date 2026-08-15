package org.kvxd.optraix.dh.net

import org.kvxd.optraix.dh.lod.DHSectionPos
import org.kvxd.optraix.dh.io.DHByteReader
import org.kvxd.optraix.dh.io.DHByteWriter

internal object DHProtocol {
    const val Channel = "distant_horizons:msg"
    const val Version = 15
    const val ChunkPayloadSize = 16 * 1024

    fun decode(data: ByteArray): DHMessage {
        val input = DHByteReader(data)
        val version = input.unsignedShort()
        require(version == Version) { "unsupported DH protocol $version" }
        return when (val type = input.unsignedShort()) {
            3 -> DHMessage.LevelInitRequest(input.shortString())
            4 -> decodeRemoteConfig(input)
            5 -> DHMessage.Cancel(input.int())
            7 -> DHMessage.FullDataRequest(
                tracker = input.int(),
                worldName = input.shortString(),
                position = DHSectionPos.unpack(input.long()),
                timestamp = input.optionalLong(),
            )

            else -> error("unsupported DH message type $type")
        }
    }

    fun closeReason(reason: String): ByteArray = message(1) { string(reason) }

    fun levelInit(worldKey: String, serverKey: String, levelKey: String, time: Long): ByteArray =
        message(2) {
            shortString(worldKey)
            shortString(serverKey)
            shortString(levelKey)
            long(time)
        }

    fun remoteConfig(renderDistance: Int, concurrency: Int): ByteArray = message(4) {
        boolean(true)
        int(renderDistance)
        int(0)
        int(0)
        int(0)
        int(concurrency)
        boolean(false)
        int(0)
        boolean(true)
        int(renderDistance)
        int(concurrency)
        int(0)
    }

    fun exception(tracker: Int, type: Int, reason: String): ByteArray = message(6) {
        int(tracker)
        int(type)
        shortString(reason)
    }

    fun fullDataChunk(bufferId: Int, data: ByteArray, offset: Int, length: Int, first: Boolean): ByteArray =
        message(10, length + 16) {
            int(bufferId)
            int(length)
            bytes(data, offset, length)
            boolean(first)
        }

    fun fullDataResponse(tracker: Int, bufferId: Int?, beacons: ByteArray): ByteArray = message(8) {
        int(tracker)
        boolean(bufferId != null)
        if (bufferId != null) {
            int(bufferId)
            bytes(beacons)
        }
    }

    private fun decodeRemoteConfig(input: DHByteReader): DHMessage.RemoteConfig {
        val distantGeneration = input.boolean()
        val renderDistance = input.int()
        input.int()
        input.int()
        input.int()
        val concurrency = input.int()
        input.boolean()
        input.int()
        input.boolean()
        input.int()
        input.int()
        input.int()
        return DHMessage.RemoteConfig(distantGeneration, renderDistance, concurrency)
    }

    private inline fun message(type: Int, initialSize: Int = 128, body: DHByteWriter.() -> Unit): ByteArray =
        DHByteWriter(initialSize).apply {
            short(Version)
            short(type)
            body()
        }.toByteArray()
}
