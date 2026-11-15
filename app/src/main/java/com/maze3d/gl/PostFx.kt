package com.maze3d.gl

import android.opengl.GLES30
import android.util.Log

/** Parameters for one frame of post-processing. */
class PostParams {
    var near = 0.1f; var far = 40f; var p00 = 1f; var p11 = 1f
    var focus = 3f; var focusRange = 1.4f
    var aoAmt = 0f; var dofAmt = 0f; var bloomAmt = 0.3f
    var cloudOn = false
    var contrast = 0.35f; var saturation = 1.08f; var warmth = 0.02f
    var vignette = 0.35f
    var time = 0f; var grain = 0.035f; var fxaa = true
    var ssao = true; var dof = true
}

/**
 * Offscreen HDR-ish pipeline: scene is rendered into a 4x MSAA target, resolved to textures, then
 * SSAO, bloom, depth of field, clouds, colour grading and FXAA are applied before it reaches the screen.
 * [ok] is false when the device cannot build the framebuffers; the renderer then draws straight to the screen.
 */
class PostFx {
    var ok = false
        private set
    var sceneW = 0; private set
    var sceneH = 0; private set
    private var screenW = 0
    private var screenH = 0

    private var msaaFbo = 0; private var msaaColor = 0; private var msaaDepth = 0
    private var resFbo = 0; private var colorTex = 0; private var depthTex = 0
    private var compFbo = 0; private var compTex = 0
    private var aoFbo = 0; private var aoTex = 0; private var aoTmpFbo = 0; private var aoTmpTex = 0
    private var downFbo = 0; private var downTex = 0
    private var blurAFbo = 0; private var blurATex = 0; private var blurBFbo = 0; private var blurBTex = 0
    private var bloomAFbo = 0; private var bloomATex = 0; private var bloomBFbo = 0; private var bloomBTex = 0
    private var cloudFbo = 0; private var cloudTex = 0
    private var cloudW = 0; private var cloudH = 0

    private var downP = 0; private var blurP = 0; private var brightP = 0; private var ssaoP = 0
    private var compositeP = 0; private var fxaaP = 0; private var cloudP = 0; private var blitP = 0
    private val vao = IntArray(1)
    private val locs = HashMap<String, Int>()
    private var samples = 4

    fun init() {
        downP = Shaders.program(Shaders.BG_VS, PostShaders.DOWN_FS)
        blurP = Shaders.program(Shaders.BG_VS, PostShaders.BLUR_FS)
        brightP = Shaders.program(Shaders.BG_VS, PostShaders.BRIGHT_FS)
        ssaoP = Shaders.program(Shaders.BG_VS, PostShaders.SSAO_FS)
        compositeP = Shaders.program(Shaders.BG_VS, PostShaders.COMPOSITE_FS)
        fxaaP = Shaders.program(Shaders.BG_VS, PostShaders.FXAA_FS)
        cloudP = Shaders.program(Shaders.BG_VS, PostShaders.CLOUD_FS)
        blitP = Shaders.program(Shaders.BG_VS, BLIT_FS)
        GLES30.glGenVertexArrays(1, vao, 0)
        val mx = IntArray(1)
        GLES30.glGetIntegerv(GLES30.GL_MAX_SAMPLES, mx, 0)
        samples = minOf(4, maxOf(mx[0], 1))
        locs.clear()
        msaaFbo = 0; resFbo = 0; compFbo = 0; sceneW = 0; sceneH = 0; ok = false
    }

    // ---- resources -----------------------------------------------------------------------
    private fun tex(w: Int, h: Int, depth: Boolean = false, linear: Boolean = true): Int {
        val t = IntArray(1)
        GLES30.glGenTextures(1, t, 0)
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t[0])
        if (depth) GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_DEPTH_COMPONENT24, w, h, 0, GLES30.GL_DEPTH_COMPONENT, GLES30.GL_UNSIGNED_INT, null)
        else GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA8, w, h, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, null)
        val f = if (linear) GLES30.GL_LINEAR else GLES30.GL_NEAREST
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, f)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, f)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        return t[0]
    }

    private fun fboWith(color: Int, depth: Int = 0): Int {
        val f = IntArray(1)
        GLES30.glGenFramebuffers(1, f, 0)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, f[0])
        if (color != 0) GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_TEXTURE_2D, color, 0)
        if (depth != 0) GLES30.glFramebufferTexture2D(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_TEXTURE_2D, depth, 0)
        return f[0]
    }

    private fun complete() = GLES30.glCheckFramebufferStatus(GLES30.GL_FRAMEBUFFER) == GLES30.GL_FRAMEBUFFER_COMPLETE

    private fun freeAll() {
        val tex = intArrayOf(colorTex, depthTex, compTex, aoTex, aoTmpTex, downTex, blurATex, blurBTex, bloomATex, bloomBTex).filter { it != 0 }.toIntArray()
        if (tex.isNotEmpty()) GLES30.glDeleteTextures(tex.size, tex, 0)
        val fbo = intArrayOf(msaaFbo, resFbo, compFbo, aoFbo, aoTmpFbo, downFbo, blurAFbo, blurBFbo, bloomAFbo, bloomBFbo).filter { it != 0 }.toIntArray()
        if (fbo.isNotEmpty()) GLES30.glDeleteFramebuffers(fbo.size, fbo, 0)
        val rb = intArrayOf(msaaColor, msaaDepth).filter { it != 0 }.toIntArray()
        if (rb.isNotEmpty()) GLES30.glDeleteRenderbuffers(rb.size, rb, 0)
        colorTex = 0; depthTex = 0; compTex = 0; aoTex = 0; aoTmpTex = 0; downTex = 0; blurATex = 0; blurBTex = 0
        bloomATex = 0; bloomBTex = 0; msaaFbo = 0; resFbo = 0; compFbo = 0; aoFbo = 0; aoTmpFbo = 0; downFbo = 0
        blurAFbo = 0; blurBFbo = 0; bloomAFbo = 0; bloomBFbo = 0; msaaColor = 0; msaaDepth = 0
    }

    /** (Re)build all buffers for a screen of [sw] x [sh] rendered at [scale]. */
    fun resize(sw: Int, sh: Int, scale: Float): Boolean {
        val w = maxOf(64, (sw * scale).toInt()); val h = maxOf(64, (sh * scale).toInt())
        screenW = sw; screenH = sh
        if (w == sceneW && h == sceneH && ok) return true
        freeAll()
        sceneW = w; sceneH = h
        val hw = maxOf(w / 2, 1); val hh = maxOf(h / 2, 1); val qw = maxOf(w / 4, 1); val qh = maxOf(h / 4, 1)

        val rb = IntArray(2)
        GLES30.glGenRenderbuffers(2, rb, 0)
        msaaColor = rb[0]; msaaDepth = rb[1]
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, msaaColor)
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_RGBA8, w, h)
        GLES30.glBindRenderbuffer(GLES30.GL_RENDERBUFFER, msaaDepth)
        GLES30.glRenderbufferStorageMultisample(GLES30.GL_RENDERBUFFER, samples, GLES30.GL_DEPTH_COMPONENT24, w, h)
        val f = IntArray(1)
        GLES30.glGenFramebuffers(1, f, 0)
        msaaFbo = f[0]
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, msaaFbo)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_COLOR_ATTACHMENT0, GLES30.GL_RENDERBUFFER, msaaColor)
        GLES30.glFramebufferRenderbuffer(GLES30.GL_FRAMEBUFFER, GLES30.GL_DEPTH_ATTACHMENT, GLES30.GL_RENDERBUFFER, msaaDepth)
        var good = complete()

        colorTex = tex(w, h); depthTex = tex(w, h, depth = true, linear = false)
        resFbo = fboWith(colorTex, depthTex); good = good && complete()
        compTex = tex(w, h); compFbo = fboWith(compTex); good = good && complete()
        aoTex = tex(hw, hh); aoFbo = fboWith(aoTex); good = good && complete()
        aoTmpTex = tex(hw, hh); aoTmpFbo = fboWith(aoTmpTex); good = good && complete()
        downTex = tex(hw, hh); downFbo = fboWith(downTex); good = good && complete()
        blurATex = tex(hw, hh); blurAFbo = fboWith(blurATex); good = good && complete()
        blurBTex = tex(hw, hh); blurBFbo = fboWith(blurBTex); good = good && complete()
        bloomATex = tex(qw, qh); bloomAFbo = fboWith(bloomATex); good = good && complete()
        bloomBTex = tex(qw, qh); bloomBFbo = fboWith(bloomBTex); good = good && complete()
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)

        resizeClouds(sw, sh)
        ok = good
        if (!good) Log.w("Maze3D", "post-processing framebuffers incomplete; falling back to direct rendering")
        return good
    }

    /** Clouds are soft, so they live in a small texture of their own. */
    fun resizeClouds(sw: Int, sh: Int) {
        screenW = sw; screenH = sh
        val cw = maxOf(sw / 4, 1); val ch = maxOf(sh / 4, 1)
        if (cloudTex != 0 && cw == cloudW && ch == cloudH) return
        if (cloudTex != 0) { GLES30.glDeleteTextures(1, intArrayOf(cloudTex), 0); GLES30.glDeleteFramebuffers(1, intArrayOf(cloudFbo), 0) }
        cloudW = cw; cloudH = ch
        cloudTex = tex(cw, ch); cloudFbo = fboWith(cloudTex)
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0)
    }

    /** Free the scene buffers (low quality draws straight to the screen). */
    fun dropScene() { freeAll(); sceneW = 0; sceneH = 0; ok = false }

    fun release() { freeAll(); if (cloudTex != 0) { GLES30.glDeleteTextures(1, intArrayOf(cloudTex), 0); GLES30.glDeleteFramebuffers(1, intArrayOf(cloudFbo), 0); cloudTex = 0 } }

    // ---- passes --------------------------------------------------------------------------
    private fun loc(p: Int, n: String) = locs.getOrPut("$p:$n") { GLES30.glGetUniformLocation(p, n) }

    private fun bindTex(unit: Int, t: Int) { GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit); GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, t) }

    private fun pass(fbo: Int, w: Int, h: Int, prog: Int, setup: () -> Unit) {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, fbo)
        GLES30.glViewport(0, 0, w, h)
        GLES30.glUseProgram(prog)
        setup()
        GLES30.glBindVertexArray(vao[0])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glBindVertexArray(0)
    }

    fun beginScene() {
        GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, msaaFbo)
        GLES30.glViewport(0, 0, sceneW, sceneH)
    }

    /** Renders the cloud layer into its small texture. Call before [beginScene]. */
    fun renderClouds(time: Float, aspect: Float, amount: Float, density: Float, windX: Float, windY: Float,
                     tint: FloatArray, shade: FloatArray) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST); GLES30.glDisable(GLES30.GL_BLEND)
        pass(cloudFbo, cloudW, cloudH, cloudP) {
            GLES30.glUniform1f(loc(cloudP, "uTime"), time)
            GLES30.glUniform1f(loc(cloudP, "uAspect"), aspect)
            GLES30.glUniform1f(loc(cloudP, "uAmount"), amount)
            GLES30.glUniform1f(loc(cloudP, "uDensity"), density)
            GLES30.glUniform2f(loc(cloudP, "uWind"), windX, windY)
            GLES30.glUniform3fv(loc(cloudP, "uTint"), 1, tint, 0)
            GLES30.glUniform3fv(loc(cloudP, "uShade"), 1, shade, 0)
        }
    }

    /** Draws the cloud texture straight onto the current target (used by the no-post fallback). */
    fun blitClouds() {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glUseProgram(blitP)
        bindTex(0, cloudTex)
        GLES30.glUniform1i(loc(blitP, "uTex"), 0)
        GLES30.glBindVertexArray(vao[0]); GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3); GLES30.glBindVertexArray(0)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    /** Resolve MSAA, run all effects and write the final image to the screen. */
    fun finish(p: PostParams) {
        GLES30.glDisable(GLES30.GL_DEPTH_TEST); GLES30.glDisable(GLES30.GL_BLEND)
        val w = sceneW; val h = sceneH
        val hw = maxOf(w / 2, 1); val hh = maxOf(h / 2, 1); val qw = maxOf(w / 4, 1); val qh = maxOf(h / 4, 1)

        GLES30.glBindFramebuffer(GLES30.GL_READ_FRAMEBUFFER, msaaFbo)
        GLES30.glBindFramebuffer(GLES30.GL_DRAW_FRAMEBUFFER, resFbo)
        GLES30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT, GLES30.GL_NEAREST)

        // SSAO at half resolution, then blur
        if (p.ssao && p.aoAmt > 0f) {
            pass(aoFbo, hw, hh, ssaoP) {
                bindTex(0, depthTex); GLES30.glUniform1i(loc(ssaoP, "uDepth"), 0)
                GLES30.glUniform4f(loc(ssaoP, "uProj"), p.near, p.far, p.p00, p.p11)
                GLES30.glUniform2f(loc(ssaoP, "uTexel"), 1f / w, 1f / h)
                GLES30.glUniform1f(loc(ssaoP, "uRadius"), 0.075f)
                GLES30.glUniform1f(loc(ssaoP, "uStrength"), 1.6f)
            }
            blur(aoTex, aoTmpFbo, hw, hh, 1f / hw, 0f)
            blur(aoTmpTex, aoFbo, hw, hh, 0f, 1f / hh)
        }

        // half-res copy of the scene feeds both depth of field and bloom
        pass(downFbo, hw, hh, downP) {
            bindTex(0, colorTex); GLES30.glUniform1i(loc(downP, "uTex"), 0)
            GLES30.glUniform2f(loc(downP, "uTexel"), 1f / w, 1f / h)
        }
        if (p.dof && p.dofAmt > 0f) {
            blur(downTex, blurAFbo, hw, hh, 1f / hw, 0f)
            blur(blurATex, blurBFbo, hw, hh, 0f, 1f / hh)
        }
        if (p.bloomAmt > 0f) {
            pass(bloomAFbo, qw, qh, brightP) {
                bindTex(0, downTex); GLES30.glUniform1i(loc(brightP, "uTex"), 0)
                GLES30.glUniform2f(loc(brightP, "uTexel"), 1f / hw, 1f / hh)
                GLES30.glUniform1f(loc(brightP, "uThreshold"), 0.78f)
            }
            blur(bloomATex, bloomBFbo, qw, qh, 1f / qw, 0f)
            blur(bloomBTex, bloomAFbo, qw, qh, 0f, 1f / qh)
            blur(bloomATex, bloomBFbo, qw, qh, 2f / qw, 0f)
            blur(bloomBTex, bloomAFbo, qw, qh, 0f, 2f / qh)
        }

        pass(compFbo, w, h, compositeP) {
            bindTex(0, colorTex); GLES30.glUniform1i(loc(compositeP, "uScene"), 0)
            bindTex(1, aoTex); GLES30.glUniform1i(loc(compositeP, "uAO"), 1)
            bindTex(2, blurBTex); GLES30.glUniform1i(loc(compositeP, "uBlur"), 2)
            bindTex(3, depthTex); GLES30.glUniform1i(loc(compositeP, "uDepth"), 3)
            bindTex(4, bloomATex); GLES30.glUniform1i(loc(compositeP, "uBloom"), 4)
            bindTex(5, cloudTex); GLES30.glUniform1i(loc(compositeP, "uCloud"), 5)
            GLES30.glUniform4f(loc(compositeP, "uProj"), p.near, p.far, p.p00, p.p11)
            GLES30.glUniform1f(loc(compositeP, "uFocus"), p.focus)
            GLES30.glUniform1f(loc(compositeP, "uFocusRange"), p.focusRange)
            GLES30.glUniform1f(loc(compositeP, "uAOAmt"), if (p.ssao) p.aoAmt else 0f)
            GLES30.glUniform1f(loc(compositeP, "uDofAmt"), if (p.dof) p.dofAmt else 0f)
            GLES30.glUniform1f(loc(compositeP, "uBloomAmt"), p.bloomAmt)
            GLES30.glUniform1f(loc(compositeP, "uCloudOn"), if (p.cloudOn) 1f else 0f)
            GLES30.glUniform3f(loc(compositeP, "uGrade"), p.contrast, p.saturation, p.warmth)
            GLES30.glUniform1f(loc(compositeP, "uVignette"), p.vignette)
        }
        bindTex(0, 0)

        pass(0, screenW, screenH, fxaaP) {
            bindTex(0, compTex); GLES30.glUniform1i(loc(fxaaP, "uTex"), 0)
            GLES30.glUniform2f(loc(fxaaP, "uTexel"), 1f / w, 1f / h)
            GLES30.glUniform1f(loc(fxaaP, "uTime"), p.time)
            GLES30.glUniform1f(loc(fxaaP, "uGrain"), p.grain)
            GLES30.glUniform1f(loc(fxaaP, "uFxaa"), if (p.fxaa) 1f else 0f)
        }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
    }

    private fun blur(src: Int, dst: Int, w: Int, h: Int, dx: Float, dy: Float) {
        pass(dst, w, h, blurP) {
            bindTex(0, src); GLES30.glUniform1i(loc(blurP, "uTex"), 0)
            GLES30.glUniform2f(loc(blurP, "uDir"), dx, dy)
        }
    }

    companion object {
        private const val BLIT_FS = "#version 300 es\nprecision mediump float;\nuniform sampler2D uTex;\nin vec2 vUv;\nout vec4 outColor;\nvoid main() { outColor = texture(uTex, vUv); }\n"
    }
}
