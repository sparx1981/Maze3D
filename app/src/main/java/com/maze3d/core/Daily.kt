package com.maze3d.core

import java.util.Calendar
import java.util.TimeZone

/** The daily maze: everyone gets the same maze on the same UTC day, so the seed comes from the date alone. */
object Daily {
    fun dayKey(): String {
        val c = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        return "%04d-%02d-%02d".format(c.get(Calendar.YEAR), c.get(Calendar.MONTH) + 1, c.get(Calendar.DAY_OF_MONTH))
    }

    fun seedFor(day: String): Long {
        var h = 1125899906842597L
        for (ch in day) h = 31L * h + ch.code
        h = h xor (h ushr 29); h *= -0x61c8864680b583ebL; h = h xor (h ushr 32)
        return h
    }

    private fun pick(seed: Long, salt: Int, n: Int) = (((seed ushr salt) and 0x7fffffffL) % n).toInt()
    fun shapeFor(seed: Long): ShapeKind = ShapeKind.values()[pick(seed, 5, ShapeKind.values().size)]
    /** Index into the palette themes (the classic finishes are skipped: they would depend on the previous level). */
    fun themeFor(seed: Long, themeCount: Int): Int = pick(seed, 11, themeCount)

    /** True for 3 to 8 letters or digits. */
    fun validName(n: String) = n.length in 3..8 && n.all { it in 'A'..'Z' || it in '0'..'9' }
}
