package org.kvxd.optraix

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.kvxd.optraix.redstone.RecompilePolicy
import org.kvxd.optraix.redstone.WorldMutationOptions
import org.kvxd.optraix.redstone.optraix.CompileMemoryPreflight
import org.kvxd.optraix.redstone.optraix.OptraIxEngine
import org.kvxd.optraix.world.GameWorld

class CompileMemoryPreflightTest {

    @Test
    fun rejectsCompilationWhenHeapIsInsufficient() {
        val plan = CompileMemoryPreflight.evaluate(
            components = 4_000_000,
            wires = 10_646_595,
            heapAvailableBytes = 512L * 1024 * 1024,
            systemAvailableBytes = 16L * 1024 * 1024 * 1024,
        )

        assertNotNull(plan.failure)
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
    fun successfulExplicitCompileClearsManualRequirement() {
        val world = GameWorld()
        val engine = OptraIxEngine()
        engine.mutate(
            world,
            WorldMutationOptions(recompilePolicy = RecompilePolicy.Manual),
        ) {}

        assertTrue(engine.manualCompileRequired)
        assertTrue(engine.compile(world))
        assertTrue(!engine.manualCompileRequired)
    }
}
