package org.kvxd.optraix.interaction

import org.kvxd.optraix.world.BlockPos

class UseResult(
    val cancelled: Boolean,
    val openSignEditorAt: BlockPos? = null,
    val openContainerAt: BlockPos? = null,
)
