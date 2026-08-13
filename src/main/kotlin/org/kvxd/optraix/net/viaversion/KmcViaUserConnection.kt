package org.kvxd.optraix.net.viaversion

import com.viaversion.viaversion.connection.UserConnectionImpl
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFuture
import io.netty.channel.embedded.EmbeddedChannel
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.middleware.ProtocolMiddlewareContext
import org.kvxd.kmcprotocol.network.middleware.RawProtocolPacket

internal class KmcViaUserConnection(
    private val context: ProtocolMiddlewareContext,
    private val embeddedChannel: EmbeddedChannel,
) : UserConnectionImpl(embeddedChannel, false) {
    private enum class Flow {
        Inbound,
        Outbound,
    }

    private data class Capture(
        val flow: Flow,
        val packet: RawProtocolPacket,
        val thread: Thread,
        val before: MutableList<RawProtocolPacket> = ArrayList(),
        val after: MutableList<RawProtocolPacket> = ArrayList(),
    )

    private val captureLock = Any()
    private var capture: Capture? = null
    private var scheduledThread: Thread? = null

    fun syncInboundState(state: ProtocolState) {
        val expected = state.toViaState()
        if (protocolInfo.clientState != expected) protocolInfo.clientState = expected
    }

    fun syncOutboundState(state: ProtocolState) {
        val expected = state.toViaState()
        if (protocolInfo.serverState != expected) protocolInfo.serverState = expected
    }

    fun beginInbound(packet: RawProtocolPacket) = begin(Flow.Inbound, packet)

    fun beginOutbound(packet: RawProtocolPacket) = begin(Flow.Outbound, packet)

    fun finish(packet: RawProtocolPacket?): List<RawProtocolPacket> = synchronized(captureLock) {
        val current = checkNotNull(capture) { "ViaVersion packet capture is not active" }
        check(current.thread === Thread.currentThread()) { "ViaVersion packet capture changed threads" }
        capture = null
        buildList(current.before.size + current.after.size + if (packet == null) 0 else 1) {
            addAll(current.before)
            if (packet != null) add(packet)
            addAll(current.after)
        }
    }

    fun abort() {
        synchronized(captureLock) {
            capture = null
            scheduledThread = null
        }
    }

    fun runScheduled(block: () -> Unit) {
        synchronized(captureLock) {
            scheduledThread = Thread.currentThread()
        }
        try {
            block()
        } finally {
            synchronized(captureLock) {
                if (scheduledThread === Thread.currentThread()) scheduledThread = null
            }
        }
    }

    override fun sendRawPacket(packet: ByteBuf) {
        emit(packet, outbound = true, scheduled = isRunningScheduled())
    }

    override fun scheduleSendRawPacket(packet: ByteBuf) {
        emit(packet, outbound = true, scheduled = true)
    }

    override fun sendRawPacketFuture(packet: ByteBuf): ChannelFuture {
        return if (emit(packet, outbound = true, scheduled = isRunningScheduled())) {
            embeddedChannel.newSucceededFuture()
        } else {
            embeddedChannel.newFailedFuture(IllegalStateException("KMCP middleware pipeline is closed"))
        }
    }

    override fun sendRawPacketToServer(packet: ByteBuf) {
        emit(packet, outbound = false, scheduled = isRunningScheduled())
    }

    override fun scheduleSendRawPacketToServer(packet: ByteBuf) {
        emit(packet, outbound = false, scheduled = true)
    }

    private fun begin(flow: Flow, packet: RawProtocolPacket) {
        synchronized(captureLock) {
            check(capture == null) { "ViaVersion packet capture is already active" }
            capture = Capture(flow, packet, Thread.currentThread())
        }
    }

    private fun isRunningScheduled(): Boolean = synchronized(captureLock) {
        scheduledThread === Thread.currentThread()
    }

    private fun emit(packet: ByteBuf, outbound: Boolean, scheduled: Boolean): Boolean {
        return try {
            val bytes = packet.copyBytes()
            val captured = synchronized(captureLock) {
                capture?.takeIf { it.thread === Thread.currentThread() }
            }
            val raw = RawProtocolPacket(
                bytes = bytes,
                state = captured?.packet?.state ?: context.protocolData.state,
                direction = if (outbound) context.outboundDirection else context.inboundDirection,
            )
            if (captured != null && isSameFlow(captured.flow, outbound)) {
                synchronized(captureLock) {
                    if (scheduled) captured.after += raw else captured.before += raw
                }
                true
            } else if (outbound) {
                context.emitter.scheduleOutbound(raw)
            } else {
                context.emitter.scheduleInbound(raw)
            }
        } finally {
            packet.release()
        }
    }

    private fun isSameFlow(flow: Flow, outbound: Boolean): Boolean = when (flow) {
        Flow.Inbound -> !outbound
        Flow.Outbound -> outbound
    }
}
