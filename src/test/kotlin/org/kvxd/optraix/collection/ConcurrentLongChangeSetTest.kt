package org.kvxd.optraix.collection

import java.util.concurrent.CountDownLatch
import kotlin.concurrent.thread
import kotlin.test.Test
import kotlin.test.assertEquals

class ConcurrentLongChangeSetTest {
    @Test
    fun concurrentWritesAreNotLostWhileDraining() {
        val changes = ConcurrentLongChangeSet()
        val started = CountDownLatch(1)
        val finished = CountDownLatch(1)
        val expected = 100_000
        val writer = thread(start = true) {
            started.countDown()
            repeat(expected) { changes += it.toLong() }
            finished.countDown()
        }
        val drained = HashSet<Long>()

        started.await()
        while (finished.count > 0 || changes.isNotEmpty()) {
            drained.addAll(changes.drain().asIterable())
        }
        writer.join()
        drained.addAll(changes.drain().asIterable())

        assertEquals(expected, drained.size)
    }
}
