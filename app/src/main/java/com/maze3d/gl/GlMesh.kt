package com.maze3d.gl

import android.opengl.GLES30
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Indexed triangle mesh in a VAO. [stride] floats per vertex, attributes described by [attribs]. */
class GlMesh(private val attribs: IntArray) {
    private val vao = IntArray(1)
    private val buf = IntArray(2)
    var indexCount = 0
        private set
    private val stride = attribs.sum()

    init {
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(2, buf, 0)
    }

    fun upload(vertices: FloatArray, indices: IntArray, dynamic: Boolean = false) {
        indexCount = indices.size
        val usage = if (dynamic) GLES30.GL_DYNAMIC_DRAW else GLES30.GL_STATIC_DRAW
        GLES30.glBindVertexArray(vao[0])
        val vb = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        vb.put(vertices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buf[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, vertices.size * 4, vb, usage)
        val ib = ByteBuffer.allocateDirect(indices.size * 4).order(ByteOrder.nativeOrder()).asIntBuffer()
        ib.put(indices).position(0)
        GLES30.glBindBuffer(GLES30.GL_ELEMENT_ARRAY_BUFFER, buf[1])
        GLES30.glBufferData(GLES30.GL_ELEMENT_ARRAY_BUFFER, indices.size * 4, ib, usage)
        var offset = 0
        for ((loc, size) in attribs.withIndex()) {
            GLES30.glEnableVertexAttribArray(loc)
            GLES30.glVertexAttribPointer(loc, size, GLES30.GL_FLOAT, false, stride * 4, offset * 4)
            offset += size
        }
        GLES30.glBindVertexArray(0)
    }

    private var vcache: java.nio.FloatBuffer? = null
    /** Replaces the vertex data in place (same layout and count as the last upload); cheap enough for 10 Hz updates. */
    fun updateVertices(vertices: FloatArray) {
        var fb = vcache
        if (fb == null || fb.capacity() < vertices.size) {
            fb = ByteBuffer.allocateDirect(vertices.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer(); vcache = fb
        }
        fb!!.clear(); fb.put(vertices); fb.position(0)
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, buf[0])
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, vertices.size * 4, fb)
    }

    fun draw() {
        if (indexCount == 0) return
        GLES30.glBindVertexArray(vao[0])
        GLES30.glDrawElements(GLES30.GL_TRIANGLES, indexCount, GLES30.GL_UNSIGNED_INT, 0)
        GLES30.glBindVertexArray(0)
    }

    fun release() {
        GLES30.glDeleteBuffers(2, buf, 0)
        GLES30.glDeleteVertexArrays(1, vao, 0)
    }

    companion object {
        val SCENE = intArrayOf(3, 3, 3, 3, 1)
        /** Scene vertices plus a per-vertex sand-tile coordinate (u, v, cell). */
        val SCENE_TILE = intArrayOf(3, 3, 3, 3, 1, 3)

        fun unitSphere(): GlMesh {
            val rings = 28; val segs = 48
            val v = ArrayList<Float>()
            for (i in 0..rings) {
                val th = Math.PI * i / rings
                for (j in 0..segs) {
                    val ph = 2 * Math.PI * j / segs
                    v.add((Math.sin(th) * Math.cos(ph)).toFloat())
                    v.add(Math.cos(th).toFloat())
                    v.add((Math.sin(th) * Math.sin(ph)).toFloat())
                }
            }
            val idx = ArrayList<Int>()
            for (i in 0 until rings) for (j in 0 until segs) {
                val a = i * (segs + 1) + j; val b = a + segs + 1
                idx.add(a); idx.add(b); idx.add(a + 1)
                idx.add(a + 1); idx.add(b); idx.add(b + 1)
            }
            return GlMesh(intArrayOf(3)).also { it.upload(v.toFloatArray(), idx.toIntArray()) }
        }
    }
}
