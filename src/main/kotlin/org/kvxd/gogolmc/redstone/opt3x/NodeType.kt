package org.kvxd.gogolmc.redstone.opt3x

object NodeType {

    const val Wire = 0
    const val Repeater = 1
    const val Comparator = 2
    const val Torch = 3
    const val WallTorch = 4
    const val Lamp = 5
    const val Lever = 6
    const val Button = 7
    const val PressurePlate = 8
    const val Constant = 9
    const val Trapdoor = 10
    const val NoteBlock = 11
    const val Chain = 12

    const val Count = 13

    val names = arrayOf(
        "wire", "repeater", "comparator", "torch", "wall_torch", "lamp",
        "lever", "button", "pressure_plate", "constant", "trapdoor", "note_block", "chain",
    )

    fun isSource(type: Int): Boolean =
        type == Lever || type == Button || type == PressurePlate || type == Constant

    fun isSink(type: Int): Boolean =
        type == Lamp || type == Trapdoor || type == NoteBlock

    fun isIo(type: Int): Boolean =
        type == Lamp || type == Trapdoor || type == NoteBlock ||
            type == Lever || type == Button || type == PressurePlate

    fun isDiode(type: Int): Boolean = type == Repeater || type == Comparator
}
