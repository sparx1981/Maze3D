package com.maze3d.gl

/** Every graphics option the player can tune; Low / Medium / High are just starting points. */
data class QualityProfile(
    val shadows: Int,          // 0 off, 1 low, 2 high
    val reflections: Int,      // live reflections on the ball: 0 off, 1 low, 2 high
    val ssao: Boolean,
    val dof: Boolean,
    val bloom: Boolean,
    val fxaa: Boolean,
    val grain: Boolean,
    val motion: Boolean,
    val fog: Boolean,
    val clouds: Boolean,
    val particles: Int,        // 0 fewer, 1 normal
    val resolution: Int,       // 0 auto, 1 75 %, 2 full
    val fps: Int               // 0 = 30, 1 = 60, 2 = display maximum
) {
    /** Any post-processing effect needs the off-screen pipeline. */
    val usePost get() = ssao || dof || bloom || fxaa || grain || motion || clouds

    fun encode() = listOf(shadows, reflections, b(ssao), b(dof), b(bloom), b(fxaa), b(grain), b(motion), b(fog), b(clouds), particles, resolution, fps).joinToString(",")

    companion object {
        private fun b(v: Boolean) = if (v) 1 else 0
        val LOW = QualityProfile(0, 0, false, false, false, false, false, false, true, false, 0, 2, 2)
        val MEDIUM = QualityProfile(1, 1, false, false, true, true, false, false, true, true, 1, 0, 2)
        val HIGH = QualityProfile(2, 2, false, true, true, true, false, false, true, true, 1, 0, 2)
        val PRESETS = listOf(LOW, MEDIUM, HIGH)

        fun decode(s: String?): QualityProfile? {
            val p = s?.split(",")?.mapNotNull { it.trim().toIntOrNull() } ?: return null
            if (p.size != 13) return null
            return QualityProfile(p[0].coerceIn(0, 2), p[1].coerceIn(0, 2), p[2] == 1, p[3] == 1, p[4] == 1, p[5] == 1, false, p[7] == 1, p[8] == 1, p[9] == 1,
                p[10].coerceIn(0, 1), p[11].coerceIn(0, 2), p[12].coerceIn(0, 2))
        }
    }
}
