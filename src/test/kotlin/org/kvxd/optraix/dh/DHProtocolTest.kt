package org.kvxd.optraix.dh

import org.kvxd.optraix.dh.io.DHByteWriter
import org.kvxd.optraix.dh.lod.DHSectionPos
import org.kvxd.optraix.dh.net.DHMessage
import org.kvxd.optraix.dh.net.DHProtocol
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class DHProtocolTest {
    @Test
    fun sectionPositionRoundTripsSignedCoordinates() {
        val expected = DHSectionPos(6, -123456, 7654321)
        assertEquals(expected, DHSectionPos.unpack(expected.packed))
    }

    @Test
    fun decodesFullDataRequest() {
        val position = DHSectionPos(6, -20, 31)
        val payload = DHByteWriter().apply {
            short(DHProtocol.Version)
            short(7)
            int(42)
            shortString("optraix:default")
            long(position.packed)
            boolean(true)
            long(1234L)
        }.toByteArray()

        val request = assertIs<DHMessage.FullDataRequest>(DHProtocol.decode(payload))
        assertEquals(42, request.tracker)
        assertEquals("optraix:default", request.worldName)
        assertEquals(position, request.position)
        assertEquals(1234L, request.timestamp)
    }

    @Test
    fun outgoingMessagesUseProtocolFifteen() {
        val input = java.io.DataInputStream(DHProtocol.remoteConfig(512, 12).inputStream())
        assertEquals(15, input.readUnsignedShort())
        assertEquals(4, input.readUnsignedShort())
        assertEquals(true, input.readBoolean())
        assertEquals(512, input.readInt())
    }
}
