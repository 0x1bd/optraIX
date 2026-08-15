package org.kvxd.optraix.dh.net

import org.kvxd.optraix.dh.lod.DHSectionPos

internal sealed interface DHMessage {
    data class LevelInitRequest(val worldKey: String) : DHMessage

    data class RemoteConfig(
        val distantGeneration: Boolean,
        val renderDistance: Int,
        val requestConcurrency: Int,
    ) : DHMessage

    data class Cancel(val tracker: Int) : DHMessage

    data class FullDataRequest(
        val tracker: Int,
        val worldName: String,
        val position: DHSectionPos,
        val timestamp: Long?,
    ) : DHMessage
}
