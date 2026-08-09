package org.kvxd.optraix.net

import org.kvxd.optraix.block.Items
import org.kvxd.optraix.player.Player
import org.kvxd.optraix.world.BlockEntities
import org.kvxd.optraix.world.BlockEntity
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.ContainerKind
import org.kvxd.optraix.redstone.WorldMutationContext
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundOpenWindowPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.play.clientbound.ClientboundWindowItemsPacket
import org.kvxd.kmcprotocol.packets.generated.v1_20_4.types.Slot
import kotlin.math.ceil

object ContainerScreens {

    const val WindowId = 7

    fun open(player: Player, pos: BlockPos, container: BlockEntity.Container) {
        player.openContainer = pos
        player.connection.send(
            ClientboundOpenWindowPacket(
                windowId = WindowId,
                inventoryType = container.kind.windowType,
                windowTitle = Text.of(container.kind.id.removePrefix("minecraft:")),
            )
        )

        val slots = arrayOfNulls<Slot>(container.kind.slots)
        for (entry in container.inventory) {
            if (entry.slot in slots.indices) {
                slots[entry.slot] = Slot(true, entry.id, entry.count.toByte(), null)
            }
        }
        player.connection.send(
            ClientboundWindowItemsPacket(
                windowId = WindowId.toShort(),
                stateId = 0,
                items = slots.map { it ?: Slot(false, null, null, null) },
                carriedItem = Slot(false, null, null, null),
            )
        )
    }

    fun applyClick(world: WorldMutationContext, player: Player, slot: Int, item: Slot): Boolean {
        val pos = player.openContainer ?: return false
        val existing = BlockEntities.ensure(world, pos) as? BlockEntity.Container ?: return false
        if (slot !in 0 until existing.kind.slots) return false

        val entries = existing.inventory.filter { it.slot != slot }.toMutableList()
        val itemId = item.itemId
        if (item.present && itemId != null) {
            entries += org.kvxd.optraix.world.InventoryEntry(
                id = itemId,
                slot = slot,
                count = item.itemCount?.toInt() ?: 1,
                nbt = null,
            )
        }
        world.setBlockEntity(pos, rebuild(existing.kind, entries))
        return true
    }

    fun close(player: Player) {
        player.openContainer = null
    }

    private fun rebuild(
        kind: ContainerKind,
        entries: List<org.kvxd.optraix.world.InventoryEntry>,
    ): BlockEntity.Container {
        var fullness = 0.0f
        for (entry in entries) {
            val maxStack = Items.byName(Items.nameOf(entry.id))?.maxStackSize ?: 64
            fullness += entry.count.toFloat() / maxStack.toFloat()
        }
        val override = if (entries.isEmpty()) 0
        else ceil((if (fullness > 0.0f) 1.0f else 0.0f) + (fullness / kind.slots) * 14.0f).toInt()
        return BlockEntity.Container(kind, override.coerceIn(0, 15), entries)
    }
}
