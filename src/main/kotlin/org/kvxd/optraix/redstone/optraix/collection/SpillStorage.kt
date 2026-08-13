package org.kvxd.optraix.redstone.optraix.collection

import java.io.File

internal object SpillStorage {

    fun create(prefix: String): File {
        val directory = File(System.getProperty("java.io.tmpdir"), "optraix-compile")
        directory.mkdirs()
        check(directory.usableSpace >= MinimumFreeBytes) {
            "compile spill needs at least 256 MiB of temporary disk space in ${directory.absolutePath}"
        }
        return File.createTempFile(prefix, ".buffer", directory)
    }

    private const val MinimumFreeBytes = 256L * 1024L * 1024L
}
