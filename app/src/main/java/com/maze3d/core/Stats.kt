package com.maze3d.core

/** Lifetime play statistics. Counts are held in memory and written out by [flush]. */
class Stats(private val store: ProgressStore) {
    private val pending = HashMap<String, Double>()

    fun get(key: String): Double = (store.get("st|$key")?.toDoubleOrNull() ?: 0.0) + (pending[key] ?: 0.0)

    fun add(key: String, amount: Double) { pending[key] = (pending[key] ?: 0.0) + amount }

    fun flush() {
        for ((k, v) in pending) store.put("st|$k", ((store.get("st|$k")?.toDoubleOrNull() ?: 0.0) + v).toString())
        pending.clear()
    }

    /** Surface keys with how often each was played, most played first. */
    fun surfacesPlayed(): List<Pair<String, Int>> =
        Progress.SURFACE_CHAIN.map { it to get("started:$it").toInt() }.filter { it.second > 0 }.sortedByDescending { it.second }

    companion object {
        fun duration(seconds: Double): String {
            val s = seconds.toLong(); val h = s / 3600; val m = (s % 3600) / 60
            return if (h > 0) "${h}h ${m}m" else if (m > 0) "${m}m ${s % 60}s" else "${s}s"
        }
    }
}
