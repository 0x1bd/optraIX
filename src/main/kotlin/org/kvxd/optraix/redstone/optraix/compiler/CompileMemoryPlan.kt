package org.kvxd.optraix.redstone.optraix.compiler

internal data class CompileMemoryPlan(
    val components: Long,
    val wires: Long,
    val requiredBytes: Long,
    val heapAvailableBytes: Long,
    val systemAvailableBytes: Long,
) {
    val strategy: CompileMemoryStrategy
        get() = if (heapAvailableBytes - (heapAvailableBytes / 5) >= requiredBytes) {
            CompileMemoryStrategy.InMemory
        } else {
            CompileMemoryStrategy.Spill
        }

    val failure: String?
        get() = when {
            systemAvailableBytes >= 0 && systemAvailableBytes < MinimumSystemBytes ->
                "compile needs at least ${mib(MinimumSystemBytes)} MiB of available system memory for bounded buffers, but only ${mib(systemAvailableBytes)} MiB is available"
            else -> null
        }

    private fun mib(bytes: Long): Long = bytes / Mib + if (bytes % Mib == 0L) 0 else 1

    private companion object {
        const val Mib = 1024L * 1024L
        const val MinimumSystemBytes = 128L * Mib
    }
}
