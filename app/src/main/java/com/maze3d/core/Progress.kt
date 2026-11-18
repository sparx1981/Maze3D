package com.maze3d.core

/** Where progress is kept (SharedPreferences in the app, a map in tests). */
interface ProgressStore { fun get(key: String): String?; fun put(key: String, value: String) }

/**
 * What the player has unlocked. Finishing any maze unlocks the next surface in a fixed chain, finishing two mazes the next shape,
 * and a maze on a surface also unlocks that surface's ball. Medium opens after one completed maze, Hard after a completed Medium.
 */
class Progress(private val store: ProgressStore) {
    @Volatile var completed = store.get("completed")?.toIntOrNull() ?: 0
        private set
    @Volatile private var byDiff = (store.get("byDiff") ?: "0,0,0,0").split(",").mapNotNull { it.toIntOrNull() }.let { l -> IntArray(4) { l.getOrElse(it) { 0 } } }
    @Volatile private var balls: Set<String> = (store.get("balls") ?: "").split(",").filter { it.isNotEmpty() }.toSet()

    class Unlocked(val surface: String?, val shape: ShapeKind?, val difficulty: Difficulty?, val ball: String?) {
        val any get() = surface != null || shape != null || difficulty != null || ball != null
    }

    fun surfaceUnlocked(key: String): Boolean { val i = SURFACE_CHAIN.indexOf(key); return i >= 0 && i < 2 + completed }
    fun shapeUnlocked(s: ShapeKind): Boolean { val i = SHAPE_CHAIN.indexOf(s); return i >= 0 && i < shapeCount() }
    fun difficultyUnlocked(d: Difficulty) = when (d) { Difficulty.EASY -> true; Difficulty.MEDIUM -> completed >= 1; Difficulty.HARD -> byDiff[1] >= 1; Difficulty.ULTRA -> byDiff[2] >= 1 }
    private fun shapeCount() = 2 + completed / 2

    /** Mazes still to finish before [key] opens (0 = open). */
    fun surfaceMazesToGo(key: String): Int { val i = SURFACE_CHAIN.indexOf(key); return if (i < 0) 0 else maxOf(0, i - 1 - completed) }
    fun shapeMazesToGo(s: ShapeKind): Int { val i = SHAPE_CHAIN.indexOf(s); return if (i < 0) 0 else maxOf(0, (i - 1) * 2 - completed) }

    fun ballUnlocked(skin: Int): Boolean = skin == 0 || keysForSkin(skin).any { it in balls }

    fun record(surfaceKey: String, shape: ShapeKind, diff: Difficulty): Unlocked {
        val beforeSurf = 2 + completed; val beforeShape = shapeCount(); val diffBefore = Difficulty.values().filter { difficultyUnlocked(it) }
        completed++
        byDiff[diff.ordinal]++
        val newBall = if (skinForKey(surfaceKey) > 0 && surfaceKey !in balls) surfaceKey else null
        if (newBall != null) balls = balls + surfaceKey
        store.put("completed", completed.toString()); store.put("byDiff", byDiff.joinToString(",")); store.put("balls", balls.joinToString(","))
        return Unlocked(SURFACE_CHAIN.getOrNull(beforeSurf), SHAPE_CHAIN.getOrNull(beforeShape).takeIf { shapeCount() > beforeShape },
            Difficulty.values().firstOrNull { difficultyUnlocked(it) && it !in diffBefore }, newBall)
    }

    companion object {
        /** Order in which surfaces open. */
        val SURFACE_CHAIN = listOf("classic", "sand", "mud", "t0", "water", "t3", "t2", "fog", "t5", "t1", "t6", "t4", "t7", "t8", "t9", "t10", "t11", "t12", "t13", "t14", "t15", "t16", "t17", "t18", "gas")
        val SHAPE_CHAIN = listOf(ShapeKind.SPHERE, ShapeKind.CUBE, ShapeKind.CAPSULE, ShapeKind.BARREL, ShapeKind.TORUS, ShapeKind.BRICK,
            ShapeKind.COIN, ShapeKind.PYRAMID, ShapeKind.GEM, ShapeKind.KNOT)
        /** Menu surface chip index (1 = Classic ...) to key. */
        val MENU_KEYS = listOf("random", "classic", "sand", "mud", "water", "fog", "gas", "t0", "t1", "t2", "t3", "t4", "t5", "t6", "t7", "t8", "t9", "t10", "t11", "t12", "t13", "t14", "t15", "t16", "t17", "t18")
        fun skinForKey(key: String): Int = when { key.startsWith("t") -> key.drop(1).toInt() + 1; key == "sand" || key == "water" -> 20; else -> 0 }
        fun keysForSkin(skin: Int): List<String> = if (skin == 20) listOf("sand", "water") else listOf("t${skin - 1}")
        val SKIN_NAMES = listOf("Glass marble", "Gobstopper", "Sugar skull", "Tyre", "Soccer ball", "Plasma ball", "Bun", "Science orb", "Snowball", "Gold scarab", "Neon globe", "Mossy stone", "Glow orb", "Autumn leaf", "Toy ball", "Yarn ball", "Amber", "Ball bearing", "Molten core", "Gummy ball", "Beach ball")
    }
}
