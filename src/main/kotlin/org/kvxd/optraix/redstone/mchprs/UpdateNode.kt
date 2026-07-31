package org.kvxd.optraix.redstone.mchprs

import org.kvxd.optraix.world.BlockPos

internal class UpdateNode(val pos: BlockPos, var state: Int) {
    var neighbors: IntArray? = null
    var visited = false
    var xbias = 0
    var zbias = 0
    var layer = 0
}
