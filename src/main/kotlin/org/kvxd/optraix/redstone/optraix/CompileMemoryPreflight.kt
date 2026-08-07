package org.kvxd.optraix.redstone.optraix

import com.sun.management.OperatingSystemMXBean
import java.lang.management.ManagementFactory
import java.nio.file.Files
import java.nio.file.Path
import org.kvxd.optraix.block.BlockKind
import org.kvxd.optraix.block.BlockStates
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
        const val SystemReserveBytes = 1024L * Mib
    }
}

internal object CompileMemoryPreflight {
    fun evaluate(world: GameWorld): CompileMemoryPlan {
        var components = 0L
        var wires = 0L
        for (chunk in world.snapshotChunks()) {
            for (section in chunk.sections) {
                if (section == null || section.blockCount == 0) continue
                if (!sectionHasCandidates(section)) continue
                section.forEachState { _, state ->
                    if (isComponentCandidate(state)) {
                        if (BlockStates.kindOf(state) == BlockKind.RedstoneWire) wires++ else components++
                    }
                }
            }
        }
        val runtime = Runtime.getRuntime()
        val usedHeap = runtime.totalMemory() - runtime.freeMemory()
        val heapAvailable = (runtime.maxMemory() - usedHeap).coerceAtLeast(0)
        return evaluate(components, wires, heapAvailable, physicalAvailableBytes())
    }

    fun evaluate(
        components: Long,
        wires: Long,
        heapAvailableBytes: Long,
        systemAvailableBytes: Long,
    ): CompileMemoryPlan {
        val required = saturatedAdd(
            BaseBytes,
            saturatedAdd(
                saturatedMultiply(components, BytesPerComponent),
                saturatedMultiply(wires, BytesPerWire),
            ),
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

    private const val BaseBytes = 128L * 1024L * 1024L
    private const val BytesPerComponent = 640L
    private const val BytesPerWire = 48L
}
