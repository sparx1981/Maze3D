package com.maze3d.core

/** A saved run: everything needed to rebuild the exact maze, plus the ball's path sampled every tenth of a second. */
class Ghost(
    val seed: Long, val shape: ShapeKind, val difficulty: Difficulty, val surfaceKey: String, val palIndex: Int,
    val terrain: Terrain, val medium: Medium, val plain: Boolean, val seconds: Float, val samples: FloatArray
) {
    fun encode(): String =
        listOf(seed, shape.name, difficulty.name, surfaceKey, palIndex, terrain.name, medium.name, plain, seconds).joinToString("|") +
            "#" + samples.joinToString(",") { "%.4f".format(java.util.Locale.US, it) }

    /** Ball position (maze-local) at [t] seconds into the run; stays at the end point once the run is over. */
    fun positionAt(t: Float): Vec3? {
        val n = samples.size / 3
        if (n == 0) return null
        val f = (t * 10f).coerceIn(0f, (n - 1).toFloat())
        val i = f.toInt(); val j = minOf(i + 1, n - 1); val k = f - i
        fun s(a: Int, c: Int) = samples[a * 3 + c]
        return Vec3(s(i, 0) + (s(j, 0) - s(i, 0)) * k, s(i, 1) + (s(j, 1) - s(i, 1)) * k, s(i, 2) + (s(j, 2) - s(i, 2)) * k)
    }

    companion object {
        fun decode(text: String?): Ghost? {
            try {
                if (text == null) return null
                val (head, body) = text.split("#", limit = 2)
                val p = head.split("|")
                if (p.size != 9) return null
                val samples = if (body.isEmpty()) FloatArray(0) else body.split(",").map { it.toFloat() }.toFloatArray()
                return Ghost(p[0].toLong(), ShapeKind.valueOf(p[1]), Difficulty.valueOf(p[2]), p[3], p[4].toInt(), Terrain.valueOf(p[5]), Medium.valueOf(p[6]), p[7].toBoolean(), p[8].toFloat(), samples)
            } catch (e: Exception) { return null }
        }
    }
}
