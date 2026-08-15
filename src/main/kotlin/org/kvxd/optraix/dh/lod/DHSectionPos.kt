package org.kvxd.optraix.dh.lod

internal data class DHSectionPos(val detailLevel: Int, val x: Int, val z: Int) {
    val packed: Long
        get() =
            (detailLevel.toLong() and DetailMask) or
                    ((x.toLong() and CoordinateMask) shl XOffset) or
                    ((z.toLong() and CoordinateMask) shl ZOffset)

    companion object {
        const val SupportedDetailLevel = 6
        const val Width = 1 shl SupportedDetailLevel

        private const val XOffset = 8
        private const val ZOffset = 36
        private const val DetailMask = 0xFFL
        private const val CoordinateMask = 0x0FFFFFFFL

        fun unpack(value: Long): DHSectionPos = DHSectionPos(
            detailLevel = (value and DetailMask).toInt(),
            x = signedCoordinate(value ushr XOffset),
            z = signedCoordinate(value ushr ZOffset),
        )

        private fun signedCoordinate(value: Long): Int {
            val raw = (value and CoordinateMask).toInt()
            return if (raw and (1 shl 27) != 0) raw or CoordinateMask.toInt().inv() else raw
        }
    }
}