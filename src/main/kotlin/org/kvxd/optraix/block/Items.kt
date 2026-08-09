package org.kvxd.optraix.block


object Items {

    private val blockAliases = mapOf("redstone" to "redstone_wire")

    private val complexPlacement = hashSetOf("cauldron", "pumpkin")

    private val byProtocolId: Array<Item?>
    private val byName: HashMap<String, Item>

    val unknown = Item("minecraft:air", 0, 64, false, -1)

    init {
        val data = mcData
        val items = data.items
        byName = HashMap(items.size * 2)
        byProtocolId = arrayOfNulls(items.maxOf { it.id } + 1)

        for (entry in items) {
            val block = data.block(blockAliases[entry.name] ?: entry.name)
            val simple = if (block != null && block.states.isEmpty() && entry.name !in complexPlacement) {
                block.defaultState
            } else {
                -1
            }
            val item = Item(
                name = "minecraft:${entry.name}",
                protocolId = entry.id,
                maxStackSize = entry.stackSize,
                isBlock = block != null,
                simplePlacement = simple,
            )
            byProtocolId[entry.id] = item
            byName[item.name] = item
        }
    }

    fun byProtocolId(id: Int): Item =
        (if (id in byProtocolId.indices) byProtocolId[id] else null) ?: unknown

    fun byName(name: String): Item? =
        byName[if (name.startsWith("minecraft:")) name else "minecraft:$name"]

    fun protocolIdOf(name: String): Int? = byName(name)?.protocolId

    fun nameOf(protocolId: Int): String = byProtocolId(protocolId).name
}
