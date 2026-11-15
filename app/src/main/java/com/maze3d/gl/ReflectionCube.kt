package com.maze3d.gl

import android.opengl.GLES30
import com.maze3d.core.Vec3

/** Small live cube map of the labyrinth around the marble; the glass marble reflects and refracts it. */
class ReflectionCube(val size: Int) {
    private val tex = IntArray(1)
    private val fbo = IntArray(1)
    private val depth = IntArray(1)
    var ok = false
        private set

    init {
        GLES30.glGenTextures(1, tex, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, tex[0])
        for (i in 0 until 6) GLES30.glTexImage2D(GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + i, 0, GLES30.GL_RGBA8, size, size, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_CUBE_MAP, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glGenRenderbuffers(1, depth, 0)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, depth[0])
        GLES30.glRenderbufferStorage(GLES30.GL_RENDERBUFFER, GLES30.GL_DEPTH_COMPONENT16, size, size)
        GLES30.glGenFramebuffers(1, fbo, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, depth[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X, tex[0], 0)
        ok = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    fun begin(face: Int) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo[0])
        GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_CUBE_MAP_POSITIVE_X + face, tex[0], 0)
        GLES30.glViewport(0, 0, size, size)
        GLES30.glClearColor(0f, 0f, 0f, 0f)
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)
    }

    fun bind(unit: Int) {
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_CUBE_MAP, tex[0])
    }

    fun release() {
        GLES30.glDeleteTextures(1, tex, 0); GLES30.glDeleteFramebuffers(1, fbo, 0); GLES30.glDeleteRenderbuffers(1, depth, 0)
    }

    companion object {
        /** GL cube-map face directions and up vectors (+X, -X, +Y, -Y, +Z, -Z). */
        val DIRS = arrayOf(Vec3(1f, 0f, 0f), Vec3(-1f, 0f, 0f), Vec3(0f, 1f, 0f), Vec3(0f, -1f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, 0f, -1f))
        val UPS = arrayOf(Vec3(0f, -1f, 0f), Vec3(0f, -1f, 0f), Vec3(0f, 0f, 1f), Vec3(0f, 0f, -1f), Vec3(0f, -1f, 0f), Vec3(0f, -1f, 0f))
    }
}
