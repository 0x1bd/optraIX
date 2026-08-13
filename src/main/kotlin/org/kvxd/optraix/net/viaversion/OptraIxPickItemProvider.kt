package org.kvxd.optraix.net.viaversion

import com.viaversion.viaversion.api.connection.UserConnection
import com.viaversion.viaversion.api.minecraft.BlockPosition
import com.viaversion.viaversion.protocols.v1_21_2to1_21_4.provider.PickItemProvider
import org.kvxd.optraix.net.OptraIxServer
import org.kvxd.optraix.world.BlockPos

internal class OptraIxPickItemProvider(
    private val server: OptraIxServer,
) : PickItemProvider() {
    override fun pickItemFromBlock(
        connection: UserConnection,
        blockPosition: BlockPosition,
        includeData: Boolean,
    ) {
        val uuid = connection.protocolInfo.uuid ?: return
        server.submit {
            server.pickItemFromBlock(
                uuid,
                BlockPos(blockPosition.x(), blockPosition.y(), blockPosition.z()),
                includeData,
            )
        }
    }
}
