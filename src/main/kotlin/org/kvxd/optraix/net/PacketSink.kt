package org.kvxd.optraix.net

import net.benwoodworth.knbt.NbtTag
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundKickDisconnectPacket

interface PacketSink {

    val closed: Boolean

    fun send(packet: MinecraftPacket)

    fun sendMessage(content: NbtTag)

    fun sendMessage(text: String) = sendMessage(Text.of(text))

    fun sendActionBar(content: NbtTag)

    suspend fun disconnect(reason: NbtTag) {
        send(ClientboundKickDisconnectPacket(reason))
        close()
    }

    fun close()
}
