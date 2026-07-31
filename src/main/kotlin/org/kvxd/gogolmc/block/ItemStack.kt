package org.kvxd.gogolmc.block

import net.benwoodworth.knbt.NbtTag

class ItemStack(
    val item: Item,
    val count: Int = 1,
    val nbt: NbtTag? = null,
)
