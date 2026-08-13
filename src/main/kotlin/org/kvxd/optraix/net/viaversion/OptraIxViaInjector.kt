package org.kvxd.optraix.net.viaversion

import com.viaversion.viaversion.api.protocol.version.ProtocolVersion
import com.viaversion.viaversion.platform.NoopInjector

internal class OptraIxViaInjector : NoopInjector() {
    override fun getServerProtocolVersion(): ProtocolVersion = ProtocolVersion.v1_20_3
}
