package org.kvxd.gogolmc.block.property

enum class WireSide {
    Up,
    Side,
    None;

    val isNone: Boolean
        get() = this == None
}
