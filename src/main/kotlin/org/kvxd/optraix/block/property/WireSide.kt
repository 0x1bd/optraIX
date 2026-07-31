package org.kvxd.optraix.block.property

enum class WireSide {
    Up,
    Side,
    None;

    val isNone: Boolean
        get() = this == None
}
