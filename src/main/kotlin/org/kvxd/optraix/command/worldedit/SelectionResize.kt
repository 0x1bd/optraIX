package org.kvxd.optraix.command.worldedit

import org.kvxd.optraix.command.CommandSource
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.worldedit.Directions
import org.kvxd.optraix.worldedit.Region

object SelectionResize {

    fun apply(source: CommandSource, worldEdit: WorldEdit, amount: Int, direction: String?, grow: Boolean) {
        val player = source.player
        val one = player.selectionOne
        val two = player.selectionTwo
        if (one == null || two == null) {
            source.error("make a selection first")
            return
        }

        val facing = if (direction == null) Directions.facing(player.yaw, player.pitch)
        else Directions.parse(direction, player.yaw, player.pitch)
        if (facing == null) {
            source.error("unknown direction: $direction")
            return
        }

        val step = worldEdit.unitOffset(facing)
        val signed = if (grow) amount else -amount
        val region = Region(one, two)

        if (step.x > 0 || step.y > 0 || step.z > 0) {
            player.selectionOne = region.min
            player.selectionTwo = BlockPos(
                region.max.x + step.x * signed,
                region.max.y + step.y * signed,
                region.max.z + step.z * signed,
            )
        } else {
            player.selectionOne = BlockPos(
                region.min.x + step.x * signed,
                region.min.y + step.y * signed,
                region.min.z + step.z * signed,
            )
            player.selectionTwo = region.max
        }

        val updated = worldEdit.regionOf(player)
        val verb = if (grow) "expanded" else "contracted"
        source.success("$verb selection to ${updated?.volume ?: 0} blocks")
    }

    fun shift(source: CommandSource, worldEdit: WorldEdit, amount: Int, direction: String?) {
        val player = source.player
        val one = player.selectionOne
        val two = player.selectionTwo
        if (one == null || two == null) {
            source.error("make a selection first")
            return
        }

        val facing = if (direction == null) Directions.facing(player.yaw, player.pitch)
        else Directions.parse(direction, player.yaw, player.pitch)
        if (facing == null) {
            source.error("unknown direction: $direction")
            return
        }

        val step = worldEdit.unitOffset(facing)
        player.selectionOne = BlockPos(
            one.x + step.x * amount, one.y + step.y * amount, one.z + step.z * amount
        )
        player.selectionTwo = BlockPos(
            two.x + step.x * amount, two.y + step.y * amount, two.z + step.z * amount
        )
        source.success("selection shifted")
    }
}
