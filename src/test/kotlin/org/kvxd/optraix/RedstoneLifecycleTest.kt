package org.kvxd.optraix

import java.io.File
import java.util.concurrent.locks.LockSupport
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.redstone.optraix.OptraIxEngine
import org.kvxd.optraix.world.management.RedstoneMode
import org.kvxd.optraix.world.management.RedstoneStage

class RedstoneLifecycleTest {
    @Test
    fun backgroundCompileActivatesOnTheServerThread() {
        val server = OptraIxServer(
            ServerConfig(port = 0, runDirectory = File("build/tmp/redstone-lifecycle-test")),
        )
        val engine = server.engine as OptraIxEngine

        server.requestCompile(engine)

        assertTrue(server.worlds.default.redstoneFrozen)
        repeat(200) {
            server.runSubmittedTasks()
            if (!engine.compiled) LockSupport.parkNanos(1_000_000L)
        }
        assertTrue(engine.compiled)
        assertTrue(!server.worlds.default.redstoneFrozen)
        assertTrue(server.worlds.default.redstoneStage == RedstoneStage.Compiled)
        server.shutdown()
    }

    @Test
    fun pauseReconciliationRunsOutsideTheServerThread() {
        val server = OptraIxServer(
            ServerConfig(port = 0, runDirectory = File("build/tmp/redstone-pause-lifecycle-test")),
        )
        val engine = server.engine as OptraIxEngine
        server.requestCompile(engine)
        repeat(200) {
            server.runSubmittedTasks()
            if (!engine.compiled) LockSupport.parkNanos(1_000_000L)
        }
        assertTrue(engine.compiled)

        server.requestPause(serverPlayer(server), engine)

        assertTrue(server.worlds.default.redstoneFrozen)
        assertTrue(server.worlds.default.redstoneStage == RedstoneStage.Reconciling)
        val pendingChange = 1L
        server.worlds.default.world.changedBlocks += pendingChange
        server.publishWorldChanges()
        assertTrue(pendingChange in server.worlds.default.world.changedBlocks)
        repeat(200) {
            server.runSubmittedTasks()
            if (server.worlds.default.redstoneFrozen) LockSupport.parkNanos(1_000_000L)
        }
        assertTrue(engine.paused)
        assertTrue(!server.worlds.default.redstoneFrozen)
        assertTrue(server.worlds.default.redstoneStage == RedstoneStage.Interpreted)
        server.publishWorldChanges()
        assertEquals(0, server.worlds.default.world.changedBlocks.size)
        server.shutdown()
    }

    @Test
    fun compileSupersedesPendingPause() {
        val server = OptraIxServer(
            ServerConfig(port = 0, runDirectory = File("build/tmp/redstone-transition-test")),
        )
        val engine = server.engine as OptraIxEngine
        server.requestCompile(engine)
        repeat(200) {
            server.runSubmittedTasks()
            if (server.worlds.default.redstoneFrozen) LockSupport.parkNanos(1_000_000L)
        }
        assertTrue(engine.compiled)
        val player = serverPlayer(server)
        var pauseCompleted = false
        var compileCompleted = false

        server.requestPause(player, engine) { pauseCompleted = true }
        server.worlds.default.desiredMode = RedstoneMode.Compiled
        server.requestCompile(player, engine) { compileCompleted = it }

        repeat(200) {
            server.runSubmittedTasks()
            if (server.worlds.default.redstoneFrozen) LockSupport.parkNanos(1_000_000L)
        }
        assertTrue(compileCompleted)
        assertFalse(pauseCompleted)
        assertTrue(engine.compiled)
        assertFalse(engine.paused)
        assertFalse(server.worlds.default.redstoneFrozen)
        assertTrue(server.worlds.default.redstoneStage == RedstoneStage.Compiled)
        server.shutdown()
    }

    private fun serverPlayer(server: OptraIxServer): org.kvxd.optraix.player.Player =
        org.kvxd.optraix.player.Player(1, java.util.UUID.randomUUID(), "Tester", RecordingSink()).also {
            server.players += it
        }
}
