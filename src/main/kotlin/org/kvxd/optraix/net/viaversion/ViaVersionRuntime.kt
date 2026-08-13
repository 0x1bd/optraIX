package org.kvxd.optraix.net.viaversion

import com.viaversion.viaversion.ViaManagerImpl
import com.viaversion.viaversion.api.Via
import com.viaversion.viaversion.api.platform.ViaPlatformLoader
import com.viaversion.viaversion.api.protocol.packet.State as ViaState
import com.viaversion.viaversion.commands.ViaCommandHandler
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.provider.PickItemProvider
import io.netty.buffer.ByteBuf
import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import org.kvxd.kmcprotocol.core.ProtocolState
import org.kvxd.kmcprotocol.network.middleware.ProtocolMiddleware
import org.kvxd.optraix.net.OptraIxServer

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

        fun start(server: OptraIxServer): ViaVersionRuntime {
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
                manager.providers.use(PickItemProvider::class.java, OptraIxPickItemProvider(server))
                ViaVersionRuntime(manager, dataDirectory)
            } catch (cause: Throwable) {
                runCatching { manager.destroy() }
                runCatching { dataDirectory.walkBottomUp().forEach(File::delete) }
                throw cause
            }
        }
    }
}

internal fun ByteBuf.copyBytes(): ByteArray {
    val bytes = ByteArray(readableBytes())
    getBytes(readerIndex(), bytes)
    return bytes
}

internal fun ProtocolState.toViaState(): ViaState = when (this) {
    ProtocolState.Handshake -> ViaState.HANDSHAKE
    ProtocolState.Status -> ViaState.STATUS
    ProtocolState.Login -> ViaState.LOGIN
    ProtocolState.Configuration -> ViaState.CONFIGURATION
    ProtocolState.Play -> ViaState.PLAY
}
