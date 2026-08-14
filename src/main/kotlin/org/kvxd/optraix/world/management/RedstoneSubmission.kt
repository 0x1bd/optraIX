package org.kvxd.optraix.world.management

internal class RedstoneSubmission(
    val mode: RedstoneMode,
    private var completion: ((Boolean) -> Unit)? = null,
) {
    fun complete(success: Boolean) {
        val callback = completion
        completion = null
        callback?.invoke(success)
    }
}
