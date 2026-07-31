package org.kvxd.optraix.net

object ChatFont {

    const val SpaceWidth = 4
    const val BoldSpaceWidth = 5

    private val widths = IntArray(128) { 6 }

    init {
        fun set(chars: String, width: Int) {
            for (character in chars) widths[character.code] = width
        }
        set(" ", 4)
        set("!.,:;i|", 2)
        set("'l`", 3)
        set("I[]t", 4)
        set("\"()*<>fk", 5)
        set("{}", 5)
        set("@~", 7)
    }

    fun width(text: String, bold: Boolean = false): Int {
        var total = 0
        for (character in text) {
            val code = character.code
            total += if (code < 128) widths[code] else 6
            if (bold) total += 1
        }
        return total
    }

    fun padding(deficit: Int): Pair<Int, Int> {
        if (deficit <= 0) return 0 to 0
        for (bold in 0..deficit / BoldSpaceWidth) {
            val remainder = deficit - bold * BoldSpaceWidth
            if (remainder % SpaceWidth == 0) return remainder / SpaceWidth to bold
        }
        return deficit / SpaceWidth to 0
    }
}
