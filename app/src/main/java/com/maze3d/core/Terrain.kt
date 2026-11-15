package com.maze3d.core

/** Ground covering of a whole level. It changes how fast the marble rolls. */
enum class Terrain(val label: String, val drag: Float, val speedCap: Float, val accel: Float) {
    NONE("", 0f, 1f, 1f),
    SAND("Sand", 1.9f, 0.78f, 0.92f),
    MUD("Mud", 4.4f, 0.5f, 0.78f)
}
