package org.kvxd.optraix.block


class BlockProperty(
    val name: String,
    val stride: Int,
    val values: Array<String>,
) {
    fun indexOf(value: String): Int = values.indexOf(value)
}
