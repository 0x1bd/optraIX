package org.kvxd.optraix.worldedit.history

import org.kvxd.optraix.world.BlockEntity

data class UndoRecord(val position: Long, val state: Int, val entity: BlockEntity?)
