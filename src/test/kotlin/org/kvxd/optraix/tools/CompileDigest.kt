package org.kvxd.optraix.tools

import org.kvxd.optraix.redstone.optraix.OptraIxCircuit
import org.kvxd.optraix.redstone.optraix.OptraIxCompiler
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.GameWorld
import org.kvxd.optraix.world.WorldStorage
import java.io.File
import java.security.MessageDigest

object CompileDigest {

    @JvmStatic
    fun main(args: Array<String>) {
        val path = args.getOrElse(0) { "run/world_1/optraix.world" }
        val world = GameWorld()
        WorldStorage.load(world, File(path))
        val circuit = OptraIxCompiler.compile(world)

        val rows = ArrayList<String>(circuit.count)
        for (node in 0 until circuit.count) {
            val outgoing = ArrayList<String>()
            for (slot in circuit.edgeStart[node] until circuit.edgeStart[node + 1]) {
                val packed = circuit.edges[slot]
                val target = packed and OptraIxCircuit.TargetMask
                val weight = (packed ushr OptraIxCircuit.WeightShift) and 0xF
                val side = (packed and OptraIxCircuit.SideBit) != 0
                val solo = (packed and OptraIxCircuit.SoloBit) != 0
                outgoing += "${circuit.posKey[target]}:$weight:$side:$solo"
            }
            outgoing.sort()
            rows += "${BlockPos.unpack(circuit.posKey[node])}|${circuit.typeOf(node)}|" +
                "${circuit.isOn(node)}|${outgoing.joinToString(",")}"
        }
        rows.sort()

        val digest = MessageDigest.getInstance("SHA-256")
        for (row in rows) digest.update(row.toByteArray())
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        println("nodes=${circuit.count} edges=${circuit.edgeCount} digest=$hex")
    }
}
