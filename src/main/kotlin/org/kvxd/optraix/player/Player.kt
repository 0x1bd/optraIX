package org.kvxd.optraix.player

import org.kvxd.optraix.block.ItemStack
import org.kvxd.optraix.net.PacketSink
import org.kvxd.optraix.world.BlockPos
import org.kvxd.optraix.world.management.DefaultWorldName
import org.kvxd.optraix.worldedit.clipboard.Clipboard
import org.kvxd.optraix.worldedit.history.UndoEntry
import java.util.ArrayDeque
import java.util.UUID

const val DefaultFlyingSpeed = 0.05f
const val DefaultWalkingSpeed = 0.1f

class Player(
    val entityId: Int,
    val uuid: UUID,
    val name: String,
    val connection: PacketSink,
) {

    var worldName: String = DefaultWorldName

    var x: Double = 0.5
    var y: Double = 1.0
    var z: Double = 0.5
    var yaw: Float = 0.0f
    var pitch: Float = 0.0f
    var onGround: Boolean = false
    var crouching: Boolean = false
    var flying: Boolean = false

    var speedMultiplier: Float = 1.0f
    var teleportId: Int = 0
    var lastKeepAlive: Long = 0
    var latency: Int = 0

    var pendingBlockAck: Int = -1

    var moved: Boolean = false
    var lastChunkX: Int = Int.MIN_VALUE
    var lastChunkZ: Int = Int.MIN_VALUE

    val loadedChunks = HashSet<Long>()
    val inventory = arrayOfNulls<ItemStack>(46)
    var selectedSlot: Int = 0
    var carriedItem: ItemStack? = null
    var openContainer: BlockPos? = null

    var selectionOne: BlockPos? = null
    var selectionTwo: BlockPos? = null
    var clipboard: Clipboard? = null
    var showSelection: Boolean = true
    var showSidebar: Boolean = true
    val undoStack = ArrayDeque<UndoEntry>()
    val redoStack = ArrayDeque<UndoEntry>()

    val blockPos: BlockPos
        get() = BlockPos(
            Math.floor(x).toInt(),
            Math.floor(y).toInt(),
            Math.floor(z).toInt(),
        )

    val heldItem: ItemStack?
        get() = inventory[36 + selectedSlot]

    val flyingSpeed: Float
        get() = DefaultFlyingSpeed * speedMultiplier

    val walkingSpeed: Float
        get() = DefaultWalkingSpeed * speedMultiplier

    fun pushUndo(entry: UndoEntry) {
        pushBounded(undoStack, entry)
        while (redoStack.isNotEmpty()) redoStack.pop().close()
    }

    fun pushUndoFromRedo(entry: UndoEntry) {
        pushBounded(undoStack, entry)
    }

    fun pushRedo(entry: UndoEntry) {
        pushBounded(redoStack, entry)
    }

    fun clearHistory() {
        while (undoStack.isNotEmpty()) undoStack.pop().close()
        while (redoStack.isNotEmpty()) redoStack.pop().close()
    }

    private fun pushBounded(stack: ArrayDeque<UndoEntry>, entry: UndoEntry) {
        stack.push(entry)
        while (stack.size > 32) stack.removeLast().close()
    }
}
