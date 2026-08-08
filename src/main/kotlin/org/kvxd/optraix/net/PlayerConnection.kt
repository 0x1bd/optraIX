package org.kvxd.optraix.net

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import org.kvxd.optraix.Log
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.lenni0451.mcstructs.nbt.NbtTag
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.network.server.ServerSession
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundSystemChatPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundKickDisconnectPacket

class PlayerConnection(val session: ServerSession, scope: CoroutineScope) : PacketSink {

    private val outgoing = Channel<MinecraftPacket>(Channel.UNLIMITED)

    @Volatile
    override var closed: Boolean = false
        private set

    val remoteAddress: String
        get() = session.remoteAddress

    init {
        scope.launch {
            try {
                while (scope.isActive) {
                    val packet = outgoing.receive()
                    if (closed) break
                    session.send(packet)
                }
            } catch (cause: Throwable) {
                if (cause !is kotlinx.coroutines.CancellationException && !closed) {
                    Log.error("net", "send failed for ${session.remoteAddress}", cause)
                }
            } finally {
                close()
            }
        }
    }

    override fun send(packet: MinecraftPacket) {
        if (!closed) outgoing.trySend(packet)
    }

    override fun sendMessage(content: NbtTag) {
        send(ClientboundSystemChatPacket(content, false))
    }

    override fun sendActionBar(content: NbtTag) {
        send(ClientboundSystemChatPacket(content, true))
    }

    override suspend fun disconnect(reason: NbtTag) {
        if (closed) return
        try {
            session.send(ClientboundKickDisconnectPacket(reason))
        } finally {
            close()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        outgoing.close()
        runCatching { session.close() }
    }
}
