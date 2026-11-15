package com.maze3d.gl

import android.opengl.GLES30
import com.maze3d.core.Vec3
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Random
import kotlin.math.max

/** Small CPU particle system, simulated in the labyrinth's local space so sparks stay attached to it. */
class Sparks {
    private class P(var pos: Vec3, var vel: Vec3, var life: Float, var maxLife: Float, var col: Vec3, var size: Float)

    private val ps = ArrayList<P>()
    private val rnd = Random()
    private val buf = FloatArray(MAX * STRIDE)
    private val vao = IntArray(1)
    private val vbo = IntArray(1)
    private val bb = ByteBuffer.allocateDirect(MAX * STRIDE * 4).order(ByteOrder.nativeOrder())

    fun init() {
        GLES30.glGenVertexArrays(1, vao, 0)
        GLES30.glGenBuffers(1, vbo, 0)
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, MAX * STRIDE * 4, null, GLES30.GL_DYNAMIC_DRAW)
        val sizes = intArrayOf(3, 3, 1, 1)
        var off = 0
        for ((i, sz) in sizes.withIndex()) {
            GLES30.glEnableVertexAttribArray(i)
            GLES30.glVertexAttribPointer(i, sz, GLES30.GL_FLOAT, false, STRIDE * 4, off * 4)
            off += sz
        }
        GLES30.glBindVertexArray(0)
        ps.clear()
    }

    private fun spread(n: Vec3, amount: Float): Vec3 {
        val r = Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f)
        return (n + r * amount).normalized()
    }

    /** Sparks flying off a surface point: [dir] is the push direction away from the wall. */
    fun impact(pos: Vec3, dir: Vec3, strength: Float, col: Vec3) {
        val n = (6 + strength * 26).toInt().coerceAtMost(30)
        repeat(n) {
            val v = spread(dir, 1.6f) * (0.35f + rnd.nextFloat() * 0.9f * (0.5f + strength))
            add(P(pos, v, 0.35f + rnd.nextFloat() * 0.45f, 0f, Vec3.lerp(col, Vec3(1f, 0.85f, 0.5f), rnd.nextFloat()), 0.018f + rnd.nextFloat() * 0.018f))
        }
    }

    /** Radial burst on the surface (goal, respawn, door slam). */
    fun burst(pos: Vec3, normal: Vec3, count: Int, speed: Float, col: Vec3) {
        repeat(count) {
            val t = Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f)
            val tan = (t - normal * (t dot normal)).normalized()
            val v = (tan + normal * (0.4f + rnd.nextFloat() * 0.8f)).normalized() * (speed * (0.4f + rnd.nextFloat()))
            add(P(pos, v, 0.5f + rnd.nextFloat() * 0.7f, 0f, col, 0.02f + rnd.nextFloat() * 0.025f))
        }
    }

    /** A single free particle (trail glow, dust puff, droplet). */
    fun emit(pos: Vec3, vel: Vec3, life: Float, col: Vec3, size: Float) { add(P(pos, vel, life, 0f, col, size)) }

    /** Soft dust cloud: a few slow, large particles around [pos]. */
    fun puff(pos: Vec3, dir: Vec3, count: Int, size: Float, col: Vec3) {
        repeat(count) {
            val v = spread(dir, 1.4f) * (0.03f + rnd.nextFloat() * 0.09f)
            add(P(pos, v, 0.5f + rnd.nextFloat() * 0.6f, 0f, col, size * (0.7f + rnd.nextFloat() * 0.8f)))
        }
    }

    private fun add(p: P) {
        p.maxLife = p.life
        if (ps.size >= MAX) ps.removeAt(0)
        ps.add(p)
    }

    fun update(dt: Float, gravityLocal: Vec3) {
        val it = ps.iterator()
        while (it.hasNext()) {
            val p = it.next()
            p.life -= dt
            if (p.life <= 0f) { it.remove(); continue }
            p.vel = p.vel + gravityLocal * (0.12f * dt)
            p.vel = p.vel * (1f - 1.6f * dt).coerceAtLeast(0f)
            p.pos = p.pos + p.vel * dt
        }
    }

    fun draw(prog: Int, uScale: Float) {
        if (ps.isEmpty()) return
        var o = 0
        for (p in ps) {
            val l = max(p.life / p.maxLife, 0f)
            buf[o++] = p.pos.x; buf[o++] = p.pos.y; buf[o++] = p.pos.z
            buf[o++] = p.col.x; buf[o++] = p.col.y; buf[o++] = p.col.z
            buf[o++] = l * l; buf[o++] = p.size * (0.5f + l)
        }
        bb.clear(); bb.asFloatBuffer().put(buf, 0, o)
        GLES30.glBindVertexArray(vao[0])
        GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, vbo[0])
        GLES30.glBufferSubData(GLES30.GL_ARRAY_BUFFER, 0, o * 4, bb)
        GLES30.glDrawArrays(GLES30.GL_POINTS, 0, ps.size)
        GLES30.glBindVertexArray(0)
    }

    companion object { const val MAX = 400; const val STRIDE = 8 }
}
