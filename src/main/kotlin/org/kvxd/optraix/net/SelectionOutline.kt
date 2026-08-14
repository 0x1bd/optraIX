package org.kvxd.optraix.net

import kotlin.math.ceil
import kotlin.math.max
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundWorldParticlesPacket
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.worldedit.Region

object SelectionOutline {

    private const val HappyVillagerParticleId = 38
    private const val MaxPoints = 576
    private const val Spacing = 0.1

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

        val sizeX = maxX - minX
        val sizeY = maxY - minY
        val sizeZ = maxZ - minZ

        val totalLength = 4.0 * (sizeX + sizeY + sizeZ)
        val spacing = max(Spacing, totalLength / MaxPoints)

        for (y in listOf(minY, maxY)) {
            for (z in listOf(minZ, maxZ)) edgeX(player, minX, maxX, y, z, spacing)
            for (x in listOf(minX, maxX)) edgeZ(player, x, y, minZ, maxZ, spacing)
        }

        for (x in listOf(minX, maxX)) {
            for (z in listOf(minZ, maxZ)) edgeY(player, x, minY, maxY, z, spacing)
        }
    }

    private fun steps(from: Double, to: Double, spacing: Double): Int =
        ceil((to - from) / spacing).toInt().coerceAtLeast(1)

    private fun edgeX(
        player: Player,
        from: Double,
        to: Double,
        y: Double,
        z: Double,
        spacing: Double,
    ) {
        val steps = steps(from, to, spacing)
        for (i in 0..steps) {
            point(player, from + (to - from) * i / steps, y, z)
        }
    }

    private fun edgeY(
        player: Player,
        x: Double,
        from: Double,
        to: Double,
        z: Double,
        spacing: Double,
    ) {
        val steps = steps(from, to, spacing)
        for (i in 0..steps) {
            point(player, x, from + (to - from) * i / steps, z)
        }
    }

    private fun edgeZ(
        player: Player,
        x: Double,
        y: Double,
        from: Double,
        to: Double,
        spacing: Double,
    ) {
        val steps = steps(from, to, spacing)
        for (i in 0..steps) {
            point(player, x, y, from + (to - from) * i / steps)
        }
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