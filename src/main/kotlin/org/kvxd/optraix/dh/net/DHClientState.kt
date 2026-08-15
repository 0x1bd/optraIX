package org.kvxd.optraix.dh.net

import org.kvxd.optraix.dh.DHService
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

internal class DHClientState {
    @Volatile
    var enabled: Boolean = true

    @Volatile
    var concurrencyLimit: Int = DHService.DefaultRequestConcurrency

    val activeRequests = AtomicInteger()
    val generation = AtomicInteger()
    val activeTrackers = ConcurrentHashMap.newKeySet<Int>()
    val cancelledTrackers = ConcurrentHashMap.newKeySet<Int>()
}
