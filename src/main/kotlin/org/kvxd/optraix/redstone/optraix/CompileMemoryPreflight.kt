package org.kvxd.optraix.redstone.optraix

import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import org.kvxd.optraix.world.GameWorld

internal data class CompileMemoryPlan(
    val requiredBytes: Long,
    val heapAvailableBytes: Long,
    val systemAvailableBytes: Long,
) {
    val failure: String?
        get() = when {
            heapAvailableBytes < requiredBytes ->
                "compile needs about ${mib(requiredBytes)} MiB of additional heap, but only ${mib(heapAvailableBytes)} MiB is available"
            systemAvailableBytes >= 0 && systemAvailableBytes < requiredSystemBytes ->
                "compile needs about ${mib(requiredBytes)} MiB plus ${mib(SystemReserveBytes)} MiB of system reserve, but only ${mib(systemAvailableBytes)} MiB is available"
            else -> null
        }

    private val requiredSystemBytes: Long
        get() = if (requiredBytes > Long.MAX_VALUE - SystemReserveBytes) Long.MAX_VALUE else requiredBytes + SystemReserveBytes

    private fun mib(bytes: Long): Long = bytes / Mib + if (bytes % Mib == 0L) 0 else 1

    private companion object {
        const val Mib = 1024L * 1024L
        const val SystemReserveBytes = 4L * 1024L * Mib
    }
}

internal object CompileMemoryPreflight {
    fun evaluate(world: GameWorld): CompileMemoryPlan {
        var blocks = 0L
        var sections = 0L
        for (chunk in world.snapshotChunks()) {
            for (section in chunk.sections) {
                if (section == null || section.blockCount == 0) continue
                blocks += section.blockCount
                sections++
            }
        }
        val runtime = Runtime.getRuntime()
        val usedHeap = runtime.totalMemory() - runtime.freeMemory()
        val heapAvailable = (runtime.maxMemory() - usedHeap).coerceAtLeast(0)
        return evaluate(blocks, sections, heapAvailable, physicalAvailableBytes())
    }

    fun evaluate(
        blocks: Long,
        sections: Long,
        heapAvailableBytes: Long,
        systemAvailableBytes: Long,
    ): CompileMemoryPlan {
        val required = saturatedAdd(
            BaseBytes,
            saturatedAdd(saturatedMultiply(blocks, BytesPerBlock), saturatedMultiply(sections, BytesPerSection)),
        )
        return CompileMemoryPlan(required, heapAvailableBytes.coerceAtLeast(0), systemAvailableBytes)
    }

    private fun saturatedMultiply(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE / right) Long.MAX_VALUE else left * right

    private fun saturatedAdd(left: Long, right: Long): Long =
        if (left > Long.MAX_VALUE - right) Long.MAX_VALUE else left + right

    private fun physicalAvailableBytes(): Long {
        val linuxAvailable = runCatching {
            Files.newBufferedReader(Path.of("/proc/meminfo")).useLines { lines ->
                val value = lines.firstOrNull { it.startsWith("MemAvailable:") }
                    ?.substringAfter(':')
                    ?.trim()
                    ?.substringBefore(' ')
                    ?.toLongOrNull()
                value?.times(1024)
            }
        }.getOrNull()
        if (linuxAvailable != null) return linuxAvailable
        val operatingSystem = ManagementFactory.getOperatingSystemMXBean() as? OperatingSystemMXBean
        return operatingSystem?.freeMemorySize ?: -1
    }

    private const val BaseBytes = 256L * 1024L * 1024L
    private const val BytesPerBlock = 256L
    private const val BytesPerSection = 4096L
}
