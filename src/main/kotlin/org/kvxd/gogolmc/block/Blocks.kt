package org.kvxd.gogolmc.block


object Blocks {

    val types: Array<BlockType>
    val stateCount: Int

    private val byName: HashMap<String, BlockType>
    private val stateOwner: Array<BlockType>

    init {
        val stream = Blocks::class.java.getResourceAsStream("/data/blocks.txt")
            ?: throw IllegalStateException("missing /data/blocks.txt")
        val lines = stream.bufferedReader().readLines()
        stateCount = lines[0].trim().toInt()
        val count = lines[1].trim().toInt()
        val list = ArrayList<BlockType>(count)
        for (i in 0 until count) {
            val parts = lines[2 + i].split('|')
            val properties = ArrayList<BlockProperty>(parts.size - 5)
            for (j in 5 until parts.size) {
                val propParts = parts[j].split('=')
                properties += BlockProperty(
                    propParts[0],
                    propParts[1].toInt(),
                    propParts[2].split(',').toTypedArray(),
                )
            }
            list += BlockType(
                name = parts[0],
                minStateId = parts[1].toInt(),
                maxStateId = parts[2].toInt(),
                defaultStateId = parts[3].toInt(),
                attributes = if (parts[4].isEmpty()) emptySet() else parts[4].split(',').toHashSet(),
                properties = properties.toTypedArray(),
            )
        }
        types = list.toTypedArray()
        byName = HashMap(types.size * 2)
        for (type in types) {
            byName[type.name] = type
            byName[type.simpleName] = type
        }
        val air = byName.getValue("minecraft:air")
        val owners = arrayOfNulls<BlockType>(stateCount)
        for (type in types) {
            for (state in type.minStateId..type.maxStateId) owners[state] = type
        }
        @Suppress("UNCHECKED_CAST")
        stateOwner = Array(stateCount) { owners[it] ?: air }
    }

    val air: BlockType = byName.getValue("minecraft:air")

    val airState: Int = air.defaultStateId

    fun byName(name: String): BlockType? = byName[normalize(name)]

    fun require(name: String): BlockType =
        byName(name) ?: throw IllegalArgumentException("unknown block $name")

    fun typeOf(stateId: Int): BlockType =
        if (stateId in 0 until stateCount) stateOwner[stateId] else air

    fun nameOf(stateId: Int): String = typeOf(stateId).name

    fun propertiesOf(stateId: Int): Map<String, String> {
        val type = typeOf(stateId)
        if (type.properties.isEmpty()) return emptyMap()
        val map = LinkedHashMap<String, String>(type.properties.size * 2)
        for (property in type.properties) map[property.name] = type.value(stateId, property)
        return map
    }

    fun describe(stateId: Int): String {
        val type = typeOf(stateId)
        val properties = propertiesOf(stateId)
        if (properties.isEmpty()) return type.name
        return type.name + properties.entries.joinToString(",", "[", "]") { "${it.key}=${it.value}" }
    }

    fun parse(text: String): Int? {
        val trimmed = text.trim()
        val bracket = trimmed.indexOf('[')
        val name = if (bracket < 0) trimmed else trimmed.substring(0, bracket)
        val type = byName(name) ?: return null
        if (bracket < 0) return type.defaultStateId
        val body = trimmed.substring(bracket + 1).removeSuffix("]")
        if (body.isBlank()) return type.defaultStateId
        var state = type.defaultStateId
        for (entry in body.split(',')) {
            val idx = entry.indexOf('=')
            if (idx < 0) continue
            val property = type.property(entry.substring(0, idx).trim()) ?: continue
            state = type.withValue(state, property, entry.substring(idx + 1).trim())
        }
        return state
    }

    private fun normalize(name: String): String {
        val trimmed = name.trim().lowercase()
        return if (trimmed.startsWith("minecraft:")) trimmed else "minecraft:$trimmed"
    }
}
