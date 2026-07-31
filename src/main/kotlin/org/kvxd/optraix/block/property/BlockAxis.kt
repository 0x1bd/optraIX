package org.kvxd.optraix.block.property

enum class BlockAxis {
    X,
    Y,
    Z;

    fun rotate(): BlockAxis = when (this) {
        X -> Z
        Z -> X
        Y -> Y
    }
}
