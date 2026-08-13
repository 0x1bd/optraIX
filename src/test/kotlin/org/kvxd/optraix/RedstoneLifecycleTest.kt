package org.kvxd.optraix

import java.io.File
import java.util.concurrent.locks.LockSupport
import kotlin.test.Test
import kotlin.test.assertTrue
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.redstone.optraix.OptraIxEngine
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
}
