package org.kvxd.gogolmc.block


class BlockType(
    val name: String,
    val minStateId: Int,
    val maxStateId: Int,
    val defaultStateId: Int,
    val attributes: Set<String>,
    val properties: Array<BlockProperty>,
) {
    val simpleName: String = name.removePrefix("minecraft:")

    fun property(name: String): BlockProperty? = properties.firstOrNull { it.name == name }

    fun requireProperty(name: String): BlockProperty =
        property(name) ?: throw IllegalArgumentException("$this has no property $name")

    fun valueIndex(stateId: Int, property: BlockProperty): Int =
        ((stateId - minStateId) / property.stride) % property.values.size

    fun value(stateId: Int, property: BlockProperty): String =
        property.values[valueIndex(stateId, property)]

    fun withIndex(stateId: Int, property: BlockProperty, index: Int): Int =
        stateId + (index - valueIndex(stateId, property)) * property.stride

    fun withValue(stateId: Int, property: BlockProperty, value: String): Int {
        val index = property.indexOf(value)
        return if (index < 0) stateId else withIndex(stateId, property, index)
    }

    fun stateOf(values: Map<String, String>): Int {
        var state = minStateId
        for (property in properties) {
            val value = values[property.name] ?: continue
            val index = property.indexOf(value)
            if (index >= 0) state += index * property.stride
        }
        return state
    }

    override fun toString(): String = name
}
