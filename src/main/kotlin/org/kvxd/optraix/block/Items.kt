package org.kvxd.optraix.block


object Items {

    private val byProtocolId = HashMap<Int, Item>()
    private val byName = HashMap<String, Item>()
    private val allNames = HashMap<Int, String>()
    private val allIds = HashMap<String, Int>()

    val unknown = Item("minecraft:air", 0, 64, false, -1)

    init {
        val idStream = Items::class.java.getResourceAsStream("/data/item_ids.txt")
            ?: throw IllegalStateException("missing /data/item_ids.txt")
        idStream.bufferedReader().forEachLine { line ->
            if (line.isNotBlank()) {
                val parts = line.split('|')
                val id = parts[0].toInt()
                allNames[id] = parts[1]
                allIds[parts[1]] = id
            }
        }
        val stream = Items::class.java.getResourceAsStream("/data/items.txt")
            ?: throw IllegalStateException("missing /data/items.txt")
        val lines = stream.bufferedReader().readLines()
        val count = lines[0].trim().toInt()
        for (i in 0 until count) {
            val parts = lines[1 + i].split('|')
            val item = Item(
                name = parts[0],
                protocolId = parts[1].toInt(),
                maxStackSize = parts[2].toInt(),
                isBlock = parts[3] == "1",
                simplePlacement = parts[4].toInt(),
            )
            byProtocolId[item.protocolId] = item
            byName[item.name] = item
        }
    }

    fun byProtocolId(id: Int): Item = byProtocolId[id] ?: Item(
        name = allNames[id] ?: "minecraft:air",
        protocolId = id,
        maxStackSize = 64,
        isBlock = false,
        simplePlacement = -1,
    )

    fun byName(name: String): Item? {
        val normalized = if (name.startsWith("minecraft:")) name else "minecraft:$name"
        return byName[normalized]
    }

    fun protocolIdOf(name: String): Int? =
        allIds[if (name.startsWith("minecraft:")) name else "minecraft:$name"]

    fun nameOf(protocolId: Int): String = allNames[protocolId] ?: "minecraft:air"
}
