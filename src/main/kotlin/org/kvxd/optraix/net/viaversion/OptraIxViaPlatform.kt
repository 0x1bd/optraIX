package org.kvxd.optraix.net.viaversion

import com.viaversion.viaversion.configuration.AbstractViaConfig
import com.viaversion.viaversion.platform.UserConnectionViaVersionPlatform
import java.io.File
import java.util.logging.Logger

internal class OptraIxViaPlatform(
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
