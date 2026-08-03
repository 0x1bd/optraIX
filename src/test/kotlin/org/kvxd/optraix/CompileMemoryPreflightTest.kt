package org.kvxd.optraix

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.kvxd.optraix.redstone.optraix.CompileMemoryPreflight
import org.kvxd.optraix.redstone.optraix.OptraIxEngine
import org.kvxd.optraix.world.GameWorld

class CompileMemoryPreflightTest {

    @Test
    fun rejectsCompilationWhenHeapIsInsufficient() {
        val plan = CompileMemoryPreflight.evaluate(
            blocks = 14_646_595,
            sections = 300_000,
            heapAvailableBytes = 512L * 1024 * 1024,
            systemAvailableBytes = 16L * 1024 * 1024 * 1024,
        )

        assertNotNull(plan.failure)
    }

    @Test
    fun acceptsCompilationWhenBothBudgetsAreSufficient() {
        val plan = CompileMemoryPreflight.evaluate(
            blocks = 100_000,
            sections = 10_000,
            heapAvailableBytes = 2L * 1024 * 1024 * 1024,
            systemAvailableBytes = 8L * 1024 * 1024 * 1024,
        )

        assertNull(plan.failure)
    }

    @Test
    fun successfulExplicitCompileClearsManualRequirement() {
        val world = GameWorld()
        val engine = OptraIxEngine()
        engine.worldEdited(world, requireManualCompile = true)

        assertTrue(engine.manualCompileRequired)
        assertTrue(engine.compile(world))
        assertTrue(!engine.manualCompileRequired)
    }
}
