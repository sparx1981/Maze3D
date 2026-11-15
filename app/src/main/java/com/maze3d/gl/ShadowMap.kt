package com.maze3d.gl

import android.opengl.GLES30
import android.opengl.Matrix

/** Depth-only render target for the key light's shadow map. */
class ShadowMap(val size: Int) {
    private val tex = IntArray(1)
    private val fbo = IntArray(1)
    var ok = false
        private set

    init {
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24, size, size, 0,
            GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_MODE, GLES30.GL_COMPARE_REF_TO_TEXTURE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_COMPARE_FUNC, GLES30.GL_LEQUAL)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, tex[0], 0)
        GLES30.glDrawBuffers(1, intArrayOf(GLES30.GL_NONE), 0)
        GLES30.glReadBuffer(GLES30.GL_NONE)
        ok = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun begin() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glViewport(0, 0, size, size)
        GLES30.glClear(GLES30.GL_DEPTH_BUFFER_BIT)
    }

    fun end(width: Int, height: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
        GLES30.glViewport(0, 0, width, height)
    }

    fun bind(unit: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, tex[0])
    }

    fun release() {
        GLES30.glDeleteTextures(1, tex, 0)
        GLES30.glDeleteFramebuffers(1, fbo, 0)
    }

    companion object {
        /** Orthographic light-space matrix looking at the origin from [dir] (unit, towards the light). */
        fun lightMatrix(dir: com.maze3d.core.Vec3, radius: Float, out: FloatArray) {
            val view = FloatArray(16); val proj = FloatArray(16)
            val e = dir * 6f
            val up = if (kotlin.math.abs(dir.y) > 0.95f) floatArrayOf(0f, 0f, 1f) else floatArrayOf(0f, 1f, 0f)
            Matrix.setLookAtM(view, 0, e.x, e.y, e.z, 0f, 0f, 0f, up[0], up[1], up[2])
            Matrix.orthoM(proj, 0, -radius, radius, -radius, radius, 3f, 9f)
            Matrix.multiplyMM(out, 0, proj, 0, view, 0)
        }
    }
}
