package com.maze3d.gl

import com.maze3d.core.Vec3

/** Growable float/int buffers for building static scene meshes. */
class MeshBuilder {
    private var v = FloatArray(1 shl 14)
    private var vn = 0
    private var i = IntArray(1 shl 14)
    private var inn = 0
    val vertexCount get() = vn / STRIDE

    /** Vertex: pos(3) normal(3) colour(3) lift(3) delay(1). Wall tops are lifted by `lift * grow`. */
    fun vertex(p: Vec3, n: Vec3, c: Vec3, lift: Vec3 = Vec3.ZERO, delay: Float = -1f): Int {
        if (vn + STRIDE > v.size) v = v.copyOf(v.size * 2)
        v[vn++] = p.x; v[vn++] = p.y; v[vn++] = p.z
        v[vn++] = n.x; v[vn++] = n.y; v[vn++] = n.z
        v[vn++] = c.x; v[vn++] = c.y; v[vn++] = c.z
        v[vn++] = lift.x; v[vn++] = lift.y; v[vn++] = lift.z
        v[vn++] = delay
        return vertexCount - 1
    }

    fun tri(a: Int, b: Int, c: Int) {
        if (inn + 3 > i.size) i = i.copyOf(i.size * 2)
        i[inn++] = a; i[inn++] = b; i[inn++] = c
    }

    fun quad(a: Int, b: Int, c: Int, d: Int) { tri(a, b, c); tri(a, c, d) }

    fun vertices(): FloatArray = v.copyOf(vn)
    fun indices(): IntArray = i.copyOf(inn)

    companion object { const val STRIDE = 13 }
}
