package org.kvxd.gogolmc.block


class Item(
    val name: String,
    val protocolId: Int,
    val maxStackSize: Int,
    val isBlock: Boolean,
    val simplePlacement: Int,
) {
    val simpleName: String = name.removePrefix("minecraft:")

    override fun toString(): String = name
}
