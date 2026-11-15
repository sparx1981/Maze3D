package com.maze3d.core

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * A closed surface described as the set of points at distance [radius] from a simple
 * "core" (a point, a circle or a box). This gives exact closest-point projections and
 * normals for sphere, torus and rounded cube, which is what the marble needs to stay
 * glued to the surface on every side.
 */
abstract class Surface(val name: String, val radius: Float) {
    /** Closest point of the core to [p]. */
    abstract fun core(p: Vec3): Vec3

    fun project(p: Vec3): Vec3 {
        val c = core(p)
        var d = p - c
        if (d.lengthSq() < 1e-12f) d = fallbackDir(c)
        return c + d.normalized() * radius
    }

    fun normal(p: Vec3): Vec3 {
        val c = core(p)
        var d = p - c
        if (d.lengthSq() < 1e-12f) d = fallbackDir(c)
        return d.normalized()
    }

    protected open fun fallbackDir(c: Vec3) = Vec3(0f, 1f, 0f)
}

class SphereSurface(radius: Float = 1f) : Surface("Sphere", radius) {
    override fun core(p: Vec3) = Vec3.ZERO
    override fun fallbackDir(c: Vec3) = Vec3(0f, 1f, 0f)
}

class TorusSurface(val majorRadius: Float = 1f, minor: Float = 0.4f) : Surface("Torus", minor) {
    override fun core(p: Vec3): Vec3 {
        val l = kotlin.math.sqrt(p.x * p.x + p.z * p.z)
        return if (l < 1e-6f) Vec3(majorRadius, 0f, 0f) else Vec3(p.x / l * majorRadius, 0f, p.z / l * majorRadius)
    }
}

class RoundedCubeSurface(val half: Float = 0.8f, rounding: Float = 0.3f) : Surface("Rounded cube", rounding) {
    private val inner = half - rounding
    override fun core(p: Vec3) = Vec3(
        max(-inner, min(inner, p.x)),
        max(-inner, min(inner, p.y)),
        max(-inner, min(inner, p.z))
    )

    override fun fallbackDir(c: Vec3): Vec3 {
        val ax = abs(c.x); val ay = abs(c.y); val az = abs(c.z)
        return when {
            ax >= ay && ax >= az -> Vec3(if (c.x >= 0) 1f else -1f, 0f, 0f)
            ay >= az -> Vec3(0f, if (c.y >= 0) 1f else -1f, 0f)
            else -> Vec3(0f, 0f, if (c.z >= 0) 1f else -1f)
        }
    }
}
