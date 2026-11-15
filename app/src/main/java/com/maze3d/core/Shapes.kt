package com.maze3d.core

enum class ShapeKind(val label: String) {
    SPHERE("Sphere"), TORUS("Torus"), CUBE("Rounded cube");

    fun createMesh(): CellMesh = when (this) {
        SPHERE -> CellMesh.cubeBased(SphereSurface(1f), 11, 1f)
        TORUS -> CellMesh.torus(TorusSurface(1f, 0.33f), 40, 12)
        CUBE -> RoundedCubeSurface(0.78f, 0.34f).let { CellMesh.cubeBased(it, 10, it.half) }
    }

    /** Distance from the origin to the farthest surface point, used to frame the camera. */
    val outerRadius: Float
        get() = when (this) {
            SPHERE -> 1f
            TORUS -> 1.35f
            CUBE -> 1.1f
        }
}
