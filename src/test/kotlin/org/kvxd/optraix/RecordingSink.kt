package org.kvxd.optraix

import net.lenni0451.mcstructs.nbt.NbtTag
import org.kvxd.optraix.net.PacketSink
import org.kvxd.kmcprotocol.core.MinecraftPacket

class RecordingSink : PacketSink {

    val packets = ArrayList<MinecraftPacket>()
    val messages = ArrayList<NbtTag>()

    override var closed: Boolean = false
        private set

    override fun send(packet: MinecraftPacket) {
        packets.add(packet)
    }

    override fun sendMessage(content: NbtTag) {
        messages.add(content)
    }

    override fun sendActionBar(content: NbtTag) {
        messages.add(content)
    }

    override fun close() {
        closed = true
    }

    inline fun <reified T : MinecraftPacket> countOf(): Int = packets.count { it is T }
}
