package org.kvxd.gogolmc.block.property

enum class ComparatorMode {
    Compare,
    Subtract;

    fun toggle(): ComparatorMode = if (this == Compare) Subtract else Compare
}
