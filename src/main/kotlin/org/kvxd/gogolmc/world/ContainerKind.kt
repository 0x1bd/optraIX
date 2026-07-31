package org.kvxd.gogolmc.world


enum class ContainerKind(val id: String, val slots: Int, val windowType: Int) {
    Furnace("minecraft:furnace", 3, 14),
    Barrel("minecraft:barrel", 27, 2),
    Hopper("minecraft:hopper", 5, 16)
}
