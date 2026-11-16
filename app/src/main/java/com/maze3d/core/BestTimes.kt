package com.maze3d.core

/** Fastest completion per surface, shape and difficulty, kept in the same store as [Progress]. */
class BestTimes(private val store: ProgressStore) {
    class Entry(val surface: String, val shape: ShapeKind, val difficulty: Difficulty, val seconds: Float)

    private fun key(surface: String, shape: ShapeKind, d: Difficulty) = "bt|$surface|${shape.name}|${d.name}"

    fun best(surface: String, shape: ShapeKind, d: Difficulty): Float? = store.get(key(surface, shape, d))?.toFloatOrNull()

    /** Records a finish; returns the previous best (null = first time) when this beat it, or -1 when it did not. */
    fun record(surface: String, shape: ShapeKind, d: Difficulty, seconds: Float): Float? {
        val old = best(surface, shape, d)
        if (old != null && seconds >= old) return -1f
        store.put(key(surface, shape, d), seconds.toString())
        store.put("bt_index", ((store.get("bt_index") ?: "").split(";").filter { it.isNotEmpty() } + key(surface, shape, d)).distinct().joinToString(";"))
        return old
    }

    fun all(): List<Entry> = (store.get("bt_index") ?: "").split(";").filter { it.isNotEmpty() }.mapNotNull { k ->
        val p = k.split("|")
        if (p.size != 4) return@mapNotNull null
        val shape = ShapeKind.values().firstOrNull { it.name == p[2] } ?: return@mapNotNull null
        val d = Difficulty.values().firstOrNull { it.name == p[3] } ?: return@mapNotNull null
        val t = store.get(k)?.toFloatOrNull() ?: return@mapNotNull null
        Entry(p[1], shape, d, t)
    }

    companion object {
        fun format(seconds: Float): String { val s = seconds.toInt(); return "%d:%02d".format(s / 60, s % 60) }
    }
}
