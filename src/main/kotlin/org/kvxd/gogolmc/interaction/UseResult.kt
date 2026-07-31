package org.kvxd.gogolmc.interaction

import org.kvxd.gogolmc.world.BlockPos

class UseResult(
    val cancelled: Boolean,
    val openSignEditorAt: BlockPos? = null,
    val openContainerAt: BlockPos? = null,
)
