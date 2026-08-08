package org.kvxd.optraix.block

import net.lenni0451.mcstructs.nbt.NbtTag

class ItemStack(
    val item: Item,
    val count: Int = 1,
    val nbt: NbtTag? = null,
)
