package org.kvxd.optraix.redstone


class RedstoneStats {
    var blockUpdates: Long = 0
    var scheduledTicks: Long = 0
    var wireUpdates: Long = 0

    fun reset() {
        blockUpdates = 0
        scheduledTicks = 0
        wireUpdates = 0
    }
}
