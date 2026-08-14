package org.kvxd.optraix

import kotlin.test.Test
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.kvxd.optraix.redstone.optraix.compiler.CompileMemoryPreflight
import org.kvxd.optraix.redstone.optraix.compiler.CompileMemoryStrategy

class CompileMemoryPreflightTest {

    @Test
    fun keepsTheLargeReferenceCompileWithin512MiB() {
        val plan = CompileMemoryPreflight.evaluate(
            components = 4_000_000,
            wires = 10_646_595,
            heapAvailableBytes = 512L * 1024 * 1024,
            systemAvailableBytes = 16L * 1024 * 1024 * 1024,
        )

        assertNull(plan.failure)
        assertTrue(plan.requiredBytes < 512L * 1024 * 1024)
        assertTrue(plan.strategy == CompileMemoryStrategy.Spill)
    }

    @Test
    fun acceptsCompilationWhenBothBudgetsAreSufficient() {
        val plan = CompileMemoryPreflight.evaluate(
            components = 100_000,
            wires = 1_000_000,
            heapAvailableBytes = 2L * 1024 * 1024 * 1024,
            systemAvailableBytes = 8L * 1024 * 1024 * 1024,
        )

        assertNull(plan.failure)
    }

    @Test
    fun acceptsARealisticLargeCompileWithA256MiBBudget() {
        val plan = CompileMemoryPreflight.evaluate(
            components = 1_000_000,
            wires = 10_000_000,
            heapAvailableBytes = 256L * 1024 * 1024,
            systemAvailableBytes = 256L * 1024 * 1024,
        )

        assertNull(plan.failure)
        assertTrue(plan.requiredBytes < 512L * 1024 * 1024)
        assertTrue(plan.strategy == CompileMemoryStrategy.InMemory)
    }
}
