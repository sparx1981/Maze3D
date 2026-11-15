package com.maze3d.gl

import android.opengl.GLES30
import com.maze3d.core.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Ring buffer of footprint decals (sand and mud trails), drawn as instanced quads. */
class TrailBuffer(private val max: Int = 3600) {
    private val data = FloatArray(max * 8)
    private val vao = IntArray(1)
    private val vbo = IntArray(1)
    private val bb = ByteBuffer.allocateDirect(max * 8 * 4).order(ByteOrder.nativeOrder())
    var count = 0
        private set
    private var head = 0
    private var dirty = false
    var last: Vec3? = null

    fun init() {
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, max * 8 * 4, null, GLES30.GL_DYNAMIC_DRAW)
        for (i in 0..1) {
            GLES30.glEnableVertexAttribArray(i)
            GLES30.glVertexAttribPointer(i, 4, GLES30.GL_FLOAT, false, 32, i * 16)
            GLES30.glVertexAttribDivisor(i, 1)
        }
        GLES30.glBindVertexArray(0)
        clear()
    }

    fun clear() { count = 0; head = 0; last = null; dirty = false }

    fun add(pos: Vec3, normal: Vec3, halfSize: Float, time: Float) {
        val o = head * 8
        data[o] = pos.x; data[o + 1] = pos.y; data[o + 2] = pos.z; data[o + 3] = time
        data[o + 4] = normal.x; data[o + 5] = normal.y; data[o + 6] = normal.z; data[o + 7] = halfSize
        head = (head + 1) % max
        if (count < max) count++
        last = pos
        dirty = true
    }

    fun draw(prog: Int, passLoc: Int) {
        if (count == 0) return
        GLES30.glBindVertexArray(vao[0])
        if (dirty) {
            GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
            bb.clear(); bb.asFloatBuffer().put(data, 0, count.coerceAtMost(max) * 8)
            GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, count * 32, bb)
            dirty = false
        }
        for (pass in 0..1) {
            GLES30.glUniform1f(passLoc, pass.toFloat())
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 6, count)
        }
        GLES30.glBindVertexArray(0)
    }
}
