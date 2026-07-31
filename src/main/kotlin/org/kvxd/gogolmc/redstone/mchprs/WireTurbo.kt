package org.kvxd.gogolmc.redstone.mchprs

import org.kvxd.gogolmc.block.property.BlockFace
import org.kvxd.gogolmc.block.BlockKind
import org.kvxd.gogolmc.block.BlockStates
import org.kvxd.gogolmc.world.BlockPos
import org.kvxd.gogolmc.world.World

class WireTurbo private constructor() {

    private val nodes = ArrayList<UpdateNode>()
    private val nodeCache = HashMap<Long, Int>()
    private var queue0 = ArrayList<Int>()
    private var queue1 = ArrayList<Int>()
    private var queue2 = ArrayList<Int>()
    private var currentWalkLayer = 0

    private fun computeAllNeighbors(pos: BlockPos): Array<BlockPos> {
        val x = pos.x
        val y = pos.y
        val z = pos.z
        return arrayOf(
            BlockPos(x - 1, y, z),
            BlockPos(x + 1, y, z),
            BlockPos(x, y - 1, z),
            BlockPos(x, y + 1, z),
            BlockPos(x, y, z - 1),
            BlockPos(x, y, z + 1),
            BlockPos(x - 2, y, z),
            BlockPos(x - 1, y - 1, z),
            BlockPos(x - 1, y + 1, z),
            BlockPos(x - 1, y, z - 1),
            BlockPos(x - 1, y, z + 1),
            BlockPos(x + 2, y, z),
            BlockPos(x + 1, y - 1, z),
            BlockPos(x + 1, y + 1, z),
            BlockPos(x + 1, y, z - 1),
            BlockPos(x + 1, y, z + 1),
            BlockPos(x, y - 2, z),
            BlockPos(x, y - 1, z - 1),
            BlockPos(x, y - 1, z + 1),
            BlockPos(x, y + 2, z),
            BlockPos(x, y + 1, z - 1),
            BlockPos(x, y + 1, z + 1),
            BlockPos(x, y, z - 2),
            BlockPos(x, y, z + 2),
        )
    }

    private fun computeHeading(rx: Int, rz: Int): Int = when ((rx + 1) + 3 * (rz + 1)) {
        0 -> North
        1 -> North
        2 -> East
        3 -> West
        4 -> West
        5 -> East
        6 -> South
        7 -> South
        8 -> South
        else -> throw IllegalStateException("invalid heading")
    }

    private fun nodeFor(world: World, pos: BlockPos): Int {
        val key = pos.asLong()
        nodeCache[key]?.let { return it }
        val id = nodes.size
        nodeCache[key] = id
        nodes.add(UpdateNode(pos, world.getBlock(pos)))
        return id
    }

    private fun identifyNeighbors(world: World, upd1: Int) {
        val pos = nodes[upd1].pos
        val positions = computeAllNeighbors(pos)
        val neighborNodes = IntArray(24)
        val visited = BooleanArray(24)

        for (i in 0 until 24) {
            val id = nodeFor(world, positions[i])
            neighborNodes[i] = id
            visited[i] = nodes[id].visited
        }

        val fromWest = visited[0] || visited[7] || visited[8]
        val fromEast = visited[1] || visited[12] || visited[13]
        val fromNorth = visited[4] || visited[17] || visited[20]
        val fromSouth = visited[5] || visited[18] || visited[21]

        var cx = 0
        var cz = 0
        if (fromWest) cx += 1
        if (fromEast) cx -= 1
        if (fromNorth) cz += 1
        if (fromSouth) cz -= 1

        val xbias = nodes[upd1].xbias
        val zbias = nodes[upd1].zbias

        val heading: Int
        if (cx == 0 && cz == 0) {
            heading = computeHeading(xbias, zbias)
            for (id in neighborNodes) {
                nodes[id].xbias = xbias
                nodes[id].zbias = zbias
            }
        } else {
            if (cx != 0 && cz != 0) {
                if (xbias != 0) cz = 0
                if (zbias != 0) cx = 0
            }
            heading = computeHeading(cx, cz)
            for (id in neighborNodes) {
                nodes[id].xbias = cx
                nodes[id].zbias = cz
            }
        }

        val reorder = Reordering[heading]
        val oriented = IntArray(24) { neighborNodes[reorder[it]] }
        nodes[upd1].neighbors = oriented
    }

    private fun propagateChanges(world: World, upd1: Int, layer: Int) {
        if (nodes[upd1].neighbors == null) identifyNeighbors(world, upd1)
        val neighbors = nodes[upd1].neighbors!!

        val layer1 = layer + 1
        for (id in neighbors) {
            val node = nodes[id]
            if (layer1 > node.layer) {
                node.layer = layer1
                queue1.add(id)
            }
        }

        val layer2 = layer + 2
        for (i in 0 until 4) {
            val id = neighbors[i]
            val node = nodes[id]
            if (layer2 > node.layer) {
                node.layer = layer2
                queue2.add(id)
            }
        }
    }

    private fun breadthFirstWalk(world: World) {
        shiftQueue()
        currentWalkLayer = 1

        while (queue0.isNotEmpty() || queue1.isNotEmpty()) {
            val current = queue0
            for (index in current.indices) {
                val id = current[index]
                val state = nodes[id].state
                if (BlockStates.kindOf(state) == BlockKind.RedstoneWire) {
                    updateNode(world, id, currentWalkLayer)
                } else {
                    MchprsRedstone.update(state, world, nodes[id].pos)
                }
            }
            shiftQueue()
            currentWalkLayer += 1
        }

        currentWalkLayer = 0
    }

    private fun shiftQueue() {
        val previous = queue0
        queue0 = queue1
        queue1 = queue2
        previous.clear()
        queue2 = previous
    }

    private fun updateNode(world: World, upd1: Int, layer: Int) {
        val node = nodes[upd1]
        node.visited = true
        val oldPower = Wire.power(node.state)

        val newState = calculateCurrentChanges(world, upd1)
        if (oldPower != Wire.power(newState)) {
            nodes[upd1].state = BlockStates.wireWithPower(nodes[upd1].state, Wire.power(newState))
            propagateChanges(world, upd1, layer)
        }
    }

    private fun getMaxCurrentStrength(upd: Int, strength: Int): Int {
        val state = nodes[upd].state
        return if (BlockStates.kindOf(state) == BlockKind.RedstoneWire) {
            maxOf(Wire.power(state), strength)
        } else {
            strength
        }
    }

    private fun calculateCurrentChanges(world: World, upd: Int): Int {
        var wire = nodes[upd].state
        val i = Wire.power(wire)
        var blockPower = 0

        if (nodes[upd].neighbors == null) identifyNeighbors(world, upd)

        val pos = nodes[upd].pos

        var wirePower = 0
        for (side in BlockFace.All) {
            val neighborPos = pos.offset(side)
            val neighborId = nodeCache[neighborPos.asLong()] ?: nodeFor(world, neighborPos)
            val neighbor = nodes[neighborId].state
            wirePower = maxOf(
                wirePower,
                MchprsRedstone.getRedstonePowerNoDust(neighbor, world, neighborPos, side),
            )
        }

        if (wirePower < 15) {
            val neighbors = nodes[upd].neighbors!!
            val centerUp = nodes[neighbors[1]].state

            for (m in 0 until 4) {
                val n = RsNeighbors[m]
                val neighborId = neighbors[n]
                val neighbor = nodes[neighborId].state
                blockPower = getMaxCurrentStrength(neighborId, blockPower)

                if (!BlockStates.isSolid(neighbor)) {
                    blockPower = getMaxCurrentStrength(neighbors[RsNeighborsDown[m]], blockPower)
                } else if (!BlockStates.isSolid(centerUp) && !BlockStates.isTransparent(neighbor)) {
                    blockPower = getMaxCurrentStrength(neighbors[RsNeighborsUp[m]], blockPower)
                }
            }
        }

        var j = maxOf(0, blockPower - 1)
        if (wirePower > j) j = wirePower
        if (i != j) {
            wire = BlockStates.wireWithPower(wire, j)
            world.setBlock(pos, wire)
            MchprsRedstone.stats.wireUpdates++
        }
        return wire
    }

    companion object {
        private const val North = 0
        private const val East = 1
        private const val South = 2
        private const val West = 3

        private val RsNeighbors = intArrayOf(4, 5, 6, 7)
        private val RsNeighborsUp = intArrayOf(9, 11, 13, 15)
        private val RsNeighborsDown = intArrayOf(8, 10, 12, 14)

        private val Reordering = arrayOf(
            intArrayOf(2, 3, 16, 19, 0, 4, 1, 5, 7, 8, 17, 20, 12, 13, 18, 21, 6, 9, 22, 14, 11, 10, 23, 15),
            intArrayOf(2, 3, 16, 19, 4, 1, 5, 0, 17, 20, 12, 13, 18, 21, 7, 8, 22, 14, 11, 15, 23, 9, 6, 10),
            intArrayOf(2, 3, 16, 19, 1, 5, 0, 4, 12, 13, 18, 21, 7, 8, 17, 20, 11, 15, 23, 10, 6, 14, 22, 9),
            intArrayOf(2, 3, 16, 19, 5, 0, 4, 1, 18, 21, 7, 8, 17, 20, 12, 13, 23, 10, 6, 9, 22, 15, 11, 14),
        )

        fun updateSurroundingNeighbors(world: World, pos: BlockPos) {
            val turbo = WireTurbo()
            val root = UpdateNode(pos, world.getBlock(pos))
            root.visited = true
            turbo.nodeCache[pos.asLong()] = 0
            turbo.nodes.add(root)
            turbo.propagateChanges(world, 0, 0)
            turbo.breadthFirstWalk(world)
        }
    }
}
