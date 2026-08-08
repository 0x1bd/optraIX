package org.kvxd.optraix.net

import com.viaversion.viaversion.ViaManagerImpl
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.platform.ViaPlatformLoader
import com.viaversion.viaversion.api.protocol.packet.State as ViaState
import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.commands.ViaCommandHandler
import com.viaversion.viaversion.configuration.AbstractViaConfig
import com.viaversion.viaversion.connection.UserConnectionImpl
import com.viaversion.viaversion.exception.CancelDecoderException
import com.viaversion.viaversion.exception.CancelEncoderException
import com.viaversion.viaversion.platform.NoopInjector
import com.viaversion.viaversion.platform.UserConnectionViaVersionPlatform
import com.viaversion.viaversion.protocol.ProtocolPipelineImpl
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInboundHandlerAdapter
import io.netty.channel.embedded.EmbeddedChannel
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.logging.Logger
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.middleware.ProtocolMiddleware
import org.kvxd.kmcprotocol.network.middleware.ProtocolMiddlewareContext
import org.kvxd.kmcprotocol.network.middleware.ProtocolMiddlewareSession
import org.kvxd.kmcprotocol.network.middleware.RawProtocolPacket

class ViaVersionRuntime private constructor(
    private val manager: ViaManagerImpl,
    private val dataDirectory: File,
) : AutoCloseable {

    val middleware: ProtocolMiddleware = ProtocolMiddleware { context ->
        ViaVersionSession(context)
    }

    private val closed = AtomicBoolean(false)

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        runCatching { manager.destroy() }
        runCatching {
            dataDirectory.walkBottomUp().forEach(File::delete)
        }
    }

    companion object {
        const val Version = "5.11.0"
        const val ServerProtocol = 765

        fun start(): ViaVersionRuntime {
            check(!Via.isLoaded()) { "ViaVersion is already initialized in this process" }

            val dataDirectory = Files.createTempDirectory("optraix-viaversion-").toFile()
            val platform = OptraIxViaPlatform(dataDirectory)
            val injector = OptraIxViaInjector()
            val manager = ViaManagerImpl.initAndLoad(
                platform,
                injector,
                ViaCommandHandler(false),
                ViaPlatformLoader.NOOP,
            ) as ViaManagerImpl

            return try {
                while (!manager.protocolManager.hasLoadedMappings()) {
                    manager.protocolManager.checkForMappingCompletion(true)
                    if (!manager.protocolManager.hasLoadedMappings()) Thread.sleep(10)
                }
                ViaVersionRuntime(manager, dataDirectory)
            } catch (cause: Throwable) {
                runCatching { manager.destroy() }
                runCatching { dataDirectory.walkBottomUp().forEach(File::delete) }
                throw cause
            }
        }
    }
}

private class ViaVersionSession(
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

private class KmcViaUserConnection(
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

private class OptraIxViaPlatform(
    dataDirectory: File,
) : UserConnectionViaVersionPlatform(dataDirectory) {

    override fun getPlatformName(): String = "optraIX"

    override fun getPlatformVersion(): String = "1.0"

    override fun isProxy(): Boolean = false

    override fun createLogger(name: String): Logger = Logger.getLogger("org.kvxd.optraix.$name")

    override fun createConfig(): AbstractViaConfig = object : AbstractViaConfig(
        File(getDataFolder(), "viaversion.yml"),
        getLogger(),
    ) {
        override fun isCheckForUpdates(): Boolean = false
    }
}

private class OptraIxViaInjector : NoopInjector() {
    override fun getServerProtocolVersion(): ProtocolVersion = ProtocolVersion.v1_20_3
}

private fun ByteBuf.copyBytes(): ByteArray {
    val bytes = ByteArray(readableBytes())
    getBytes(readerIndex(), bytes)
    return bytes
}

private fun ProtocolState.toViaState(): ViaState = when (this) {
    ProtocolState.Handshake -> ViaState.HANDSHAKE
    ProtocolState.Status -> ViaState.STATUS
    ProtocolState.Login -> ViaState.LOGIN
    ProtocolState.Configuration -> ViaState.CONFIGURATION
    ProtocolState.Play -> ViaState.PLAY
}