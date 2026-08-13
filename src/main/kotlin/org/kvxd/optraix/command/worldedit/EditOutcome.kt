package org.kvxd.optraix.command.worldedit

sealed interface EditOutcome {
    data class Completed(val changed: Int) : EditOutcome
    data class Cancelled(val restored: Int) : EditOutcome
    data class Failed(val message: String) : EditOutcome
}
