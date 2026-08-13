package org.kvxd.optraix.redstone.optraix.compiler

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
