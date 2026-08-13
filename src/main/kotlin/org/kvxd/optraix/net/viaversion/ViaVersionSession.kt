package org.kvxd.optraix.net.viaversion

import com.viaversion.viaversion.exception.CancelDecoderException
import com.viaversion.viaversion.exception.CancelEncoderException
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import org.kvxd.kmcprotocol.network.middleware.ProtocolMiddlewareContext
import org.kvxd.kmcprotocol.network.middleware.ProtocolMiddlewareSession
import org.kvxd.kmcprotocol.network.middleware.RawProtocolPacket

internal class ViaVersionSession(
    private val context: ProtocolMiddlewareContext,
) : ProtocolMiddlewareSession {
    private val channel = EmbeddedChannel(ChannelInboundHandlerAdapter())
    private val connection = KmcViaUserConnection(context, channel)

    init {
        ProtocolPipelineImpl(connection)
    }

    override suspend fun inbound(packet: RawProtocolPacket): List<RawProtocolPacket> {
        connection.syncInboundState(packet.state)
        if (!connection.checkIncomingPacket(packet.bytes.size)) return emptyList()
        if (!connection.shouldTransformPacket()) return listOf(packet)

        val buffer = channel.alloc().buffer(packet.bytes.size)
        buffer.writeBytes(packet.bytes)
        connection.beginInbound(packet)
        return try {
            connection.transformIncoming(buffer, CancelDecoderException::generate)
            drainChannelTasks()
            connection.finish(packet.withBytes(buffer.copyBytes()))
        } catch (_: CancelDecoderException) {
            drainChannelTasks()
            connection.finish(null)
        } catch (cause: Throwable) {
            connection.abort()
            throw cause
        } finally {
            buffer.release()
        }
    }

    override suspend fun outbound(packet: RawProtocolPacket): List<RawProtocolPacket> {
        connection.syncOutboundState(packet.state)
        if (!connection.checkOutgoingPacket()) return emptyList()
        if (!connection.shouldTransformPacket()) return listOf(packet)

        val buffer = channel.alloc().buffer(packet.bytes.size)
        buffer.writeBytes(packet.bytes)
        connection.beginOutbound(packet)
        return try {
            connection.transformOutgoing(buffer, CancelEncoderException::generate)
            drainChannelTasks()
            connection.finish(packet.withBytes(buffer.copyBytes()))
        } catch (_: CancelEncoderException) {
            drainChannelTasks()
            connection.finish(null)
        } catch (cause: Throwable) {
            connection.abort()
            throw cause
        } finally {
            buffer.release()
        }
    }

    override fun close() {
        connection.setActive(false)
        connection.abort()
        runCatching { channel.close() }
        runCatching { drainChannelTasks() }
    }

    private fun drainChannelTasks() {
        connection.runScheduled {
            channel.runPendingTasks()
            channel.checkException()
        }
    }
}
