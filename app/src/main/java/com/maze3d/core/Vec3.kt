package com.maze3d.core

import kotlin.math.sqrt

/** Minimal immutable 3D vector. Pure Kotlin so the game logic is unit-testable on the JVM. */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Float) = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)
    infix fun dot(o: Vec3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: Vec3) = Vec3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(x * x + y * y + z * z)
    fun lengthSq() = x * x + y * y + z * z
    fun normalized(): Vec3 {
        val l = length()
        return if (l < 1e-9f) ZERO else this / l
    }

    companion object {
        val ZERO = Vec3(0f, 0f, 0f)
        fun lerp(a: Vec3, b: Vec3, t: Float) = a + (b - a) * t
    }
}

/** Unit quaternion used for the orientation of the labyrinth. */
data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {
    operator fun times(o: Quat) = Quat(
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
        w * o.w - x * o.x - y * o.y - z * o.z
    )

    fun normalized(): Quat {
        val l = sqrt(x * x + y * y + z * z + w * w)
        return if (l < 1e-9f) IDENTITY else Quat(x / l, y / l, z / l, w / l)
    }

    fun conjugate() = Quat(-x, -y, -z, w)

    fun rotate(v: Vec3): Vec3 {
        // v' = v + 2w(q x v) + 2 q x (q x v)
        val q = Vec3(x, y, z)
        val t = (q cross v) * 2f
        return v + t * w + (q cross t)
    }

    fun inverseRotate(v: Vec3): Vec3 = conjugate().rotate(v)

    /** Column-major 4x4 rotation matrix for OpenGL. */
    fun toMatrix(out: FloatArray) {
        val xx = x * x; val yy = y * y; val zz = z * z
        val xy = x * y; val xz = x * z; val yz = y * z
        val wx = w * x; val wy = w * y; val wz = w * z
        out[0] = 1 - 2 * (yy + zz); out[1] = 2 * (xy + wz); out[2] = 2 * (xz - wy); out[3] = 0f
        out[4] = 2 * (xy - wz); out[5] = 1 - 2 * (xx + zz); out[6] = 2 * (yz + wx); out[7] = 0f
        out[8] = 2 * (xz + wy); out[9] = 2 * (yz - wx); out[10] = 1 - 2 * (xx + yy); out[11] = 0f
        out[12] = 0f; out[13] = 0f; out[14] = 0f; out[15] = 1f
    }

    companion object {
        val IDENTITY = Quat(0f, 0f, 0f, 1f)

        fun fromAxisAngle(axis: Vec3, angle: Float): Quat {
            val a = axis.normalized()
            val s = kotlin.math.sin(angle / 2)
            return Quat(a.x * s, a.y * s, a.z * s, kotlin.math.cos(angle / 2))
        }

        /** Shortest rotation taking unit vector [from] onto unit vector [to]. */
        fun fromTo(from: Vec3, to: Vec3): Quat {
            val f = from.normalized()
            val t = to.normalized()
            val d = f dot t
            if (d > 0.99999f) return IDENTITY
            if (d < -0.99999f) {
                var axis = Vec3(1f, 0f, 0f) cross f
                if (axis.lengthSq() < 1e-6f) axis = Vec3(0f, 1f, 0f) cross f
                return fromAxisAngle(axis, Math.PI.toFloat())
            }
            val c = f cross t
            return Quat(c.x, c.y, c.z, 1f + d).normalized()
        }
    }
}
