package org.kvxd.optraix.player

import org.kvxd.optraix.block.ItemStack

class PlayerProfile(
    val inventory: Array<ItemStack?>,
    val worldName: String,
    val selectedSlot: Int,
    val speedMultiplier: Float,
    val x: Double,
    val y: Double,
    val z: Double,
    val yaw: Float,
    val pitch: Float,
    val flying: Boolean,
    val showSelection: Boolean,
    val showSidebar: Boolean,
) {

    fun applyTo(player: Player) {
        inventory.copyInto(player.inventory)
        player.worldName = worldName
        player.selectedSlot = selectedSlot
        player.speedMultiplier = speedMultiplier
        player.x = x
        player.y = y
        player.z = z
        player.yaw = yaw
        player.pitch = pitch
        player.flying = flying
        player.showSelection = showSelection
        player.showSidebar = showSidebar
    }

    companion object {

        fun of(player: Player): PlayerProfile = PlayerProfile(
            inventory = player.inventory.copyOf(),
            worldName = player.worldName,
            selectedSlot = player.selectedSlot,
            speedMultiplier = player.speedMultiplier,
            x = player.x,
            y = player.y,
            z = player.z,
            yaw = player.yaw,
            pitch = player.pitch,
            flying = player.flying,
            showSelection = player.showSelection,
            showSidebar = player.showSidebar,
        )
    }
}
