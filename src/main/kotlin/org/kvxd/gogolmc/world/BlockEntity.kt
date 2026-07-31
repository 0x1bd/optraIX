package org.kvxd.gogolmc.world


sealed interface BlockEntity {

    data class Comparator(val outputStrength: Int) : BlockEntity

    class Sign(val frontRows: List<String>, val backRows: List<String>) : BlockEntity

    class Container(
        val kind: ContainerKind,
        val comparatorOverride: Int,
        val inventory: List<InventoryEntry>,
    ) : BlockEntity
}
