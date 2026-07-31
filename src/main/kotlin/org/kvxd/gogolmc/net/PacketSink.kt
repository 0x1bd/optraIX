package org.kvxd.gogolmc.net

import net.benwoodworth.knbt.NbtTag
import org.kvxd.kmcprotocol.core.MinecraftPacket

interface PacketSink {

    val closed: Boolean

    fun send(packet: MinecraftPacket)

    fun sendMessage(content: NbtTag)

    fun sendMessage(text: String) = sendMessage(Text.of(text))

    fun sendActionBar(content: NbtTag)

    fun close()
}
