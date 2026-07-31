package org.kvxd.gogolmc

import org.kvxd.gogolmc.net.Sidebar
import org.kvxd.gogolmc.net.Text
import org.kvxd.kmcprotocol.core.MinecraftPacket
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.core.encoding.MinecraftEncoder
import org.kvxd.kmcprotocol.core.encoding.PacketWriter
import org.kvxd.kmcprotocol.generated.Protocols
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundResetScorePacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundScoreboardDisplayObjectivePacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundScoreboardObjectivePacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundScoreboardScorePacket
import kotlin.test.Test
import kotlin.test.assertTrue

class SidebarPacketTest {

    private val data = Protocols.requireMinecraftVersion("1.20.4").protocolData(ProtocolState.Play)

    private fun encoded(packet: MinecraftPacket): ByteArray {
        val metadata = data.registry.getPacketMetadata(ProtocolState.Play, packet)
            ?: error("no registration for ${packet::class.simpleName}")
        val writer = PacketWriter()
        writer.writeVarInt(metadata.id)
        metadata.serializer.serialize(MinecraftEncoder(writer, data.codec, data.serializersModule), packet)
        return writer.toByteArray()
    }

    @Test
    fun sidebarPacketsEncode() {
        val objective = ClientboundScoreboardObjectivePacket(
            name = Sidebar.Objective,
            action = 0,
            displayText = Text.bold("gogolmc", Text.Gold),
            type = 0,
            number_format = 0,
            styling = null,
        )
        assertTrue(encoded(objective).size > 4, "objective packet should encode")

        val display = ClientboundScoreboardDisplayObjectivePacket(position = 1, name = Sidebar.Objective)
        assertTrue(encoded(display).size > 2, "display packet should encode")

        val score = ClientboundScoreboardScorePacket(
            itemName = "gogol.0",
            scoreName = Sidebar.Objective,
            value = 15,
            display_name = Text.of("tps 20.0"),
            number_format = 0,
            styling = null,
        )
        assertTrue(encoded(score).size > 8, "score packet should encode")

        val reset = ClientboundResetScorePacket(entity_name = "gogol.0", objective_name = Sidebar.Objective)
        assertTrue(encoded(reset).size > 4, "reset packet should encode")
    }
}
