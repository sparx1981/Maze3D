package com.maze3d.core

enum class ShapeKind(val label: String, val outerRadius: Float) {
    SPHERE("Sphere", 1f),
    TORUS("Torus", 1.35f),
    CUBE("Rounded cube", 1.1f),
    CAPSULE("Capsule", 1.25f),
    BARREL("Barrel", 1.15f),
    BRICK("Brick", 1.25f),
    COIN("Slab", 1.2f),
    PYRAMID("Pyramid", 1.2f),
    GEM("Gem", 1.1f),
    KNOT("Trefoil knot", 1.7f);

    fun createMesh(scale: Float = 1f): CellMesh {
        fun f(n: Int) = Math.round(n * scale).coerceAtLeast(3)
        return when (this) {
            SPHERE -> CellMesh.cubeBased(SphereSurface(1f), f(11), 1f)
            TORUS -> CellMesh.torus(TorusSurface(1f, 0.33f), f(40), f(12))
            CUBE -> RoundedCubeSurface(0.78f, 0.34f).let { CellMesh.cubeBased(it, f(10), it.half) }
            CAPSULE -> CellMesh.cubeRays(CapsuleSurface(0.72f, 0.42f), f(6), f(12), f(6), Vec3(0.42f, 1.14f, 0.42f))
            BARREL -> CellMesh.cubeRays(BarrelSurface(0.5f, 0.5f, 0.42f), f(11), f(11), f(11), Vec3(0.92f, 0.92f, 0.92f))
            BRICK -> CellMesh.cubeRays(BrickSurface(Vec3(1.15f, 0.5f, 0.62f), 0.3f), f(15), f(7), f(8), Vec3(1.15f, 0.5f, 0.62f))
            COIN -> CellMesh.cubeRays(BrickSurface(Vec3(1.02f, 0.4f, 1.02f), 0.3f, "Slab"), f(11), f(5), f(11), Vec3(1.02f, 0.4f, 1.02f))
            PYRAMID -> CellMesh.cubeRays(PyramidSurface(0.66f, 0.3f), f(11), f(11), f(11), Vec3(1f, 1f, 1f))
            GEM -> CellMesh.cubeRays(GemSurface(0.66f, 0.34f), f(11), f(11), f(11), Vec3(1f, 1f, 1f))
            KNOT -> CellMesh.knot(KnotSurface(0.45f, 0.16f), f(170), Math.round(6 * scale.coerceAtLeast(0.8f)))
        }
    }
}
