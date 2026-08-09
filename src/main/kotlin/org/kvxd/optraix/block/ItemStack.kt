package org.kvxd.optraix.block

import net.lenni0451.mcstructs.nbt.NbtTag
import org.kvxd.kmcprotocol.data.ItemData

class ItemStack(
    val item: ItemData,
    val count: Int = 1,
    val nbt: NbtTag? = null,
)
