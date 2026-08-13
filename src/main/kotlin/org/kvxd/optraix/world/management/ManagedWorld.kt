package org.kvxd.optraix.world.management

import java.io.File
import org.kvxd.optraix.interaction.Interaction
import org.kvxd.optraix.redstone.RedstoneEngine
import org.kvxd.optraix.redstone.optraix.OptraIxEngine
import org.kvxd.optraix.world.GameWorld

class ManagedWorld(
    val name: String,
    val file: File,
    val world: GameWorld = GameWorld(),
) {
    var engine: RedstoneEngine = OptraIxEngine()
        private set

    var interaction: Interaction = Interaction(engine)
        private set

    var compiling: Boolean = false
    var lastMutationCounter: Long = 0L
    var lastMutationAt: Long = 0L
    val plateHeldUntil = HashMap<Long, Long>()

    fun useEngine(next: RedstoneEngine) {
        engine = next
        interaction = Interaction(next)
        lastMutationCounter = (next as? OptraIxEngine)?.mutationCounter ?: 0L
        lastMutationAt = 0L
    }
}
