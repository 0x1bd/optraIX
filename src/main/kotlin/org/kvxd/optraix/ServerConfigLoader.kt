package org.kvxd.optraix

import java.io.File
import java.net.URI

object ServerConfigLoader {

    fun load(args: Array<String>, file: File = defaultFile()): ServerConfig {
        val persisted = if (file.exists()) {
            ServerConfigCodec.read(file)
        } else {
            ServerConfig().also { ServerConfigCodec.write(it, file) }
        }
        return ServerConfigCodec.applyArguments(persisted, args)
    }

    fun defaultFile(): File = File(executableDirectory(), "optraix.cfg")

    private fun executableDirectory(): File {
        System.getProperty("optraix.executable.dir")?.takeIf { it.isNotBlank() }?.let {
            return File(it).absoluteFile
        }

        if (System.getProperty("org.graalvm.nativeimage.imagecode") != null) {
            ProcessHandle.current().info().command().orElse(null)?.let {
                return File(it).absoluteFile.parentFile
            }
        }

        runCatching {
            val uri: URI = ServerConfigLoader::class.java.protectionDomain.codeSource.location.toURI()
            val location = File(uri).absoluteFile
            if (location.isFile) {
                val parent = location.parentFile
                if (parent.name == "lib") {
                    val bin = File(parent.parentFile, "bin")
                    if (bin.isDirectory) return bin
                }
                return parent
            }
        }

        return File(System.getProperty("user.dir", ".")).absoluteFile
    }
}
