package org.kvxd.optraix.net

import org.kvxd.optraix.player.Player
import org.kvxd.optraix.worldedit.Region
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundWorldParticlesPacket

object SelectionOutline {

    private const val HappyVillagerParticleId = 38
    private const val MaxPointsPerEdge = 48
    private const val Spacing = 0.5

    fun draw(player: Player) {
        val one = player.selectionOne ?: return
        val two = player.selectionTwo ?: return
        val region = Region(one, two)

        val minX = region.min.x.toDouble()
        val minY = region.min.y.toDouble()
        val minZ = region.min.z.toDouble()
        val maxX = region.max.x + 1.0
        val maxY = region.max.y + 1.0
        val maxZ = region.max.z + 1.0

        for (y in listOf(minY, maxY)) {
            for (z in listOf(minZ, maxZ)) edgeX(player, minX, maxX, y, z)
            for (x in listOf(minX, maxX)) edgeZ(player, x, y, minZ, maxZ)
        }
        for (x in listOf(minX, maxX)) {
            for (z in listOf(minZ, maxZ)) edgeY(player, x, minY, maxY, z)
        }
    }

    private fun steps(from: Double, to: Double): Int =
        ((to - from) / Spacing).toInt().coerceIn(1, MaxPointsPerEdge)

    private fun edgeX(player: Player, from: Double, to: Double, y: Double, z: Double) {
        val count = steps(from, to)
        for (index in 0..count) point(player, from + (to - from) * index / count, y, z)
    }

    private fun edgeY(player: Player, x: Double, from: Double, to: Double, z: Double) {
        val count = steps(from, to)
        for (index in 0..count) point(player, x, from + (to - from) * index / count, z)
    }

    private fun edgeZ(player: Player, x: Double, y: Double, from: Double, to: Double) {
        val count = steps(from, to)
        for (index in 0..count) point(player, x, y, from + (to - from) * index / count)
    }

    private fun point(player: Player, x: Double, y: Double, z: Double) {
        player.connection.send(
            ClientboundWorldParticlesPacket(
                particleId = HappyVillagerParticleId,
                longDistance = true,
                x = x,
                y = y,
                z = z,
                offsetX = 0.0f,
                offsetY = 0.0f,
                offsetZ = 0.0f,
                particleData = 0.0f,
                particles = 1,
                data = ClientboundWorldParticlesPacket.Data(
                    null, null, null, null, null, null, null, null, null
                ),
            )
        )
    }
}
