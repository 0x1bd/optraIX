package org.kvxd.optraix.block

import org.kvxd.kmcprotocol.data.BlockData
import org.kvxd.kmcprotocol.data.ItemData
import org.kvxd.optraix.mcdata.v1_20_4.GeneratedMinecraftData
import org.kvxd.optraix.mcdata.v1_20_4.Items as GeneratedItems

val mcData = GeneratedMinecraftData.data

private val complexPlacementItems = setOf("cauldron", "pumpkin")

val ItemData.minecraftName: String
    get() = "minecraft:$name"

fun ItemData.blockData(): BlockData? =
    mcData.block(if (name == "redstone") "redstone_wire" else name)

val ItemData.isBlock: Boolean
    get() = blockData() != null

val ItemData.simplePlacement: Int
    get() {
        val block = blockData() ?: return -1
        return if (block.states.isEmpty() && name !in complexPlacementItems) block.defaultState else -1
    }

fun itemByProtocolId(id: Int): ItemData = mcData.item(id) ?: GeneratedItems.Air

fun itemName(protocolId: Int): String = itemByProtocolId(protocolId).minecraftName
