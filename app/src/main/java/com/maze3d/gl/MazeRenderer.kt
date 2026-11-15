package com.maze3d.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.maze3d.core.Marble
import com.maze3d.core.Maze
import com.maze3d.core.Quat
import com.maze3d.core.ShapeKind
import com.maze3d.core.Vec3
import java.util.Random
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.PI
import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.tan

/**
 * Renders the labyrinth and runs the game loop. All state is touched on the GL thread only;
 * the activity talks to it through [GLSurfaceView.queueEvent] or the @Volatile inputs below.
 */
class MazeRenderer(private val host: Host) : GLSurfaceView.Renderer {

    interface Host {
        fun onHud(shape: String, seconds: Int, solved: Boolean)
        fun onImpact(strength: Float)
        fun onFinish(name: String, accent: Vec3)
    }

    private enum class Phase { GROW, PLAY, SOLVED, SINK }

    // ---- inputs (written from the UI thread) --------------------------------------------
    @Volatile var tiltMode = false
    @Volatile var hintOn = true
    @Volatile var followOn = false
    @Volatile var deviceGravity = Vec3(0f, -1f, 0f)

    // ---- GL resources ---------------------------------------------------------------------
    private var sceneProg = 0
    private var shadowSceneProg = 0
    private var shadowMarbleProg = 0
    private var shadow: ShadowMap? = null
    @Volatile var quality = 2
    private var appliedQuality = -1
    private val lightDir = Vec3(0.45f, 0.82f, 0.55f).normalized()
    private val lightVP = FloatArray(16)
    private var marbleProg = 0
    private var bgProg = 0
    private var emptyVao = IntArray(1)
    private var sphere: GlMesh? = null
    private var floorMesh: GlMesh? = null
    private var wallMesh: GlMesh? = null
    private var markerMesh: GlMesh? = null
    private var threadMesh: GlMesh? = null

    // ---- game state -----------------------------------------------------------------------
    private val rnd = Random()
    private var kind = ShapeKind.SPHERE
    private var maze: Maze? = null
    private var marble: Marble? = null
    private var pal = Palette.random(rnd)
    private var builder: SceneBuilder? = null
    private var phase = Phase.GROW
    private var growT = 0f
    private var solvedTimer = 0f
    private var playSeconds = 0f
    private var threadCell = -1
    private var rollQ = Quat.IDENTITY

    // ---- camera / orientation -------------------------------------------------------------
    private var orient = Quat.fromAxisAngle(Vec3(1f, 0.3f, 0f), 0.5f)
    private var angVel = Vec3.ZERO
    private var dragging = false
    private var zoom = 1.6f + (0.55f - 1.6f) * 0.67f
    private var width = 1
    private var height = 1
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private var camDist = 3.2f
    private var lastNanos = 0L
    private var time = 0f
    private var lastHud = -1
    private var lastImpactAt = 0f
    private var nextKind: ShapeKind? = null

    // ---- surface callbacks ----------------------------------------------------------------
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        sceneProg = Shaders.program(Shaders.SCENE_VS, Shaders.SCENE_FS)
        marbleProg = Shaders.program(Shaders.MARBLE_VS, Shaders.MARBLE_FS)
        bgProg = Shaders.program(Shaders.BG_VS, Shaders.BG_FS)
        shadowSceneProg = Shaders.program(Shaders.SCENE_VS, Shaders.SHADOW_FS)
        shadowMarbleProg = Shaders.program(Shaders.MARBLE_VS, Shaders.SHADOW_FS)
        locs.clear(); shadow = null; appliedQuality = -1
        ShadowMap.lightMatrix(lightDir, 1.75f, lightVP)
        GLES30.glGenVertexArrays(1, emptyVao, 0)
        sphere = GlMesh.unitSphere()
        floorMesh = GlMesh(GlMesh.SCENE)
        wallMesh = GlMesh(GlMesh.SCENE)
        markerMesh = GlMesh(GlMesh.SCENE)
        threadMesh = GlMesh(GlMesh.SCENE)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glEnable(GLES30.GL_CULL_FACE).also { GLES30.glDisable(GLES30.GL_CULL_FACE) }
        newLabyrinth(kind)
        lastNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = max(w, 1); height = max(h, 1)
        GLES30.glViewport(0, 0, width, height)
        updateCamera()
    }

    private fun updateCamera() {
        val fovY = 38f
        val aspect = width.toFloat() / height
        Matrix.perspectiveM(proj, 0, fovY, aspect, 0.1f, 40f)
        val tanY = tan(Math.toRadians(fovY / 2.0)).toFloat()
        val tanMin = min(tanY, tanY * aspect)
        val r = kind.outerRadius * 1.12f
        camDist = (r / tanMin + r) * zoom
        Matrix.setLookAtM(view, 0, 0f, 0f, camDist, 0f, 0f, 0f, 0f, 1f, 0f)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
    }

    // ---- game control (GL thread) ---------------------------------------------------------
    fun newLabyrinth(k: ShapeKind) {
        kind = k
        val seed = rnd.nextLong()
        val m = Maze(k.createMesh(), seed)
        maze = m
        marble = Marble(m)
        pal = Palette.random(rnd, pal)
        host.onFinish(pal.name, pal.accent)
        val b = SceneBuilder(m, pal)
        builder = b
        floorMesh!!.let { val mb = b.floor(); it.upload(mb.vertices(), mb.indices()) }
        wallMesh!!.let { val mb = b.walls(); it.upload(mb.vertices(), mb.indices()) }
        markerMesh!!.let { val mb = b.markers(); it.upload(mb.vertices(), mb.indices()) }
        threadCell = -1
        rollQ = Quat.IDENTITY
        growT = 0f
        phase = Phase.GROW
        playSeconds = 0f
        lastHud = -1
        updateCamera()
    }

    /** Request a fresh labyrinth; walls sink first. */
    fun requestNew(k: ShapeKind? = null) {
        nextKind = k ?: ShapeKind.values().filter { it != kind }.let { it[rnd.nextInt(it.size)] }
        if (phase != Phase.SINK) phase = Phase.SINK
    }

    fun resetMarble() { marble?.reset(); rollQ = Quat.IDENTITY; threadCell = -1 }

    fun resetHint() { threadCell = -1 }

    fun setQualityLevel(q: Int) { quality = q.coerceIn(0, 2) }

    /** [v] in 0..1: 1 is closest. */
    fun setZoomLevel(v: Float) { zoom = 1.6f + (0.55f - 1.6f) * v; updateCamera() }

    /** Rotate about the view axis (two-finger twist). */
    fun twist(angle: Float) { orient = (Quat.fromAxisAngle(Vec3(0f, 0f, 1f), angle) * orient).normalized() }

    // ---- touch ---------------------------------------------------------------------------
    fun dragStart() { dragging = true; angVel = Vec3.ZERO }

    fun drag(dx: Float, dy: Float, dtSec: Float) {
        val scale = 0.0075f * zoom
        val axis = Vec3(dy, dx, 0f)
        val angle = axis.length() * scale
        if (angle > 1e-6f) {
            orient = (Quat.fromAxisAngle(axis, angle) * orient).normalized()
            if (dtSec > 1e-4f) angVel = axis.normalized() * min(angle / dtSec, 9f)
        }
    }

    fun dragEnd() { dragging = false }

    // ---- frame ---------------------------------------------------------------------------
    override fun onDrawFrame(gl: GL10?) {
        val now = System.nanoTime()
        val dt = ((now - lastNanos) / 1e9f).coerceIn(0.001f, 0.05f)
        lastNanos = now
        time += dt
        update(dt)
        draw()
    }

    private fun update(dt: Float) {
        val m = maze ?: return
        val mb = marble ?: return

        // inertia after a flick
        if (!dragging) {
            val sp = angVel.length()
            if (sp > 0.01f) {
                orient = (Quat.fromAxisAngle(angVel, sp * dt) * orient).normalized()
                angVel *= exp(-2.4f * dt)
            }
        }

        // optionally keep the marble on the visible hemisphere
        if (followOn && !dragging && phase == Phase.PLAY) {
            val w = orient.rotate(mb.position.normalized())
            if (w.z < 0.35f) {
                val q = Quat.fromTo(w, Vec3(0f, 0f, 1f))
                val ang = 2f * kotlin.math.acos(q.w.coerceIn(-1f, 1f))
                val step = min(ang, 0.9f * dt)
                val axis = Vec3(q.x, q.y, q.z)
                if (axis.lengthSq() > 1e-8f) orient = (Quat.fromAxisAngle(axis, step) * orient).normalized()
            }
        }

        when (phase) {
            Phase.GROW -> {
                growT += dt / 1.1f
                if (growT >= 1.35f) { growT = 1.35f; phase = Phase.PLAY }
            }
            Phase.PLAY -> {
                playSeconds += dt
                val worldG = if (tiltMode) deviceGravity else Vec3(0f, -1f, 0f)
                val gLocal = orient.inverseRotate(worldG.normalized()) * GRAVITY
                mb.step(gLocal, dt)
                rollMarble(mb, dt)
                if (mb.lastImpact > 0.9f && time - lastImpactAt > 0.12f) {
                    lastImpactAt = time
                    host.onImpact(min(mb.lastImpact / 4f, 1f))
                }
                if (mb.atGoal) { phase = Phase.SOLVED; solvedTimer = 0f }
            }
            Phase.SOLVED -> {
                solvedTimer += dt
                if (solvedTimer > 1.6f) { phase = Phase.SINK; nextKind = pickNext() }
            }
            Phase.SINK -> {
                growT -= dt / 0.8f
                if (growT <= 0f) { newLabyrinth(nextKind ?: pickNext()); nextKind = null }
            }
        }

        val sec = playSeconds.toInt()
        val code = sec * 4 + (if (phase == Phase.SOLVED) 1 else 0)
        if (code != lastHud) {
            lastHud = code
            host.onHud(kind.label, sec, phase == Phase.SOLVED)
        }
        if (hintOn && mb.cell != threadCell && phase != Phase.SINK) {
            threadCell = mb.cell
            builder?.thread(mb.cell)?.let { t -> threadMesh?.upload(t.vertices(), t.indices(), dynamic = true) }
        }
    }

    private fun pickNext(): ShapeKind {
        val options = ShapeKind.values().filter { it != kind }
        return options[rnd.nextInt(options.size)]
    }

    private fun rollMarble(mb: Marble, dt: Float) {
        val n = mb.surface.normal(mb.position)
        val omega = (n cross mb.velocity) / mb.radius
        val sp = omega.length()
        if (sp > 1e-4f) rollQ = (Quat.fromAxisAngle(omega, sp * dt) * rollQ).normalized()
    }

    private fun applyQuality() {
        if (appliedQuality == quality) return
        appliedQuality = quality
        shadow?.release()
        shadow = when (quality) { 2 -> ShadowMap(2048); 1 -> ShadowMap(1024); else -> null }
        if (shadow?.ok == false) { shadow?.release(); shadow = null }
    }

    private fun drawScene(prog: Int, vp: FloatArray, shadowPass: Boolean) {
        GLES30.glUseProgram(prog)
        GLES30.glUniformMatrix4fv(loc(prog, "uVP"), 1, false, vp, 0)
        GLES30.glUniformMatrix4fv(loc(prog, "uModel"), 1, false, model, 0)
        GLES30.glUniform1f(loc(prog, "uGrowT"), growT)
        if (shadowPass) { floorMesh?.draw(); wallMesh?.draw() }
    }

    private fun draw() {
        applyQuality()
        GLES30.glClearColor(0f, 0f, 0f, 1f)
        val m = maze ?: return
        val mb = marble ?: return
        orient.toMatrix(model)

        // ---- shadow pass ------------------------------------------------------------------
        val sm = shadow
        if (sm != null) {
            sm.begin()
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
            GLES30.glDepthFunc(GLES30.GL_LEQUAL)
            GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
            GLES30.glPolygonOffset(2f, 4f)
            drawScene(shadowSceneProg, lightVP, true)
            GLES30.glUseProgram(shadowMarbleProg)
            GLES30.glUniformMatrix4fv(loc(shadowMarbleProg, "uVP"), 1, false, lightVP, 0)
            GLES30.glUniformMatrix4fv(loc(shadowMarbleProg, "uModel"), 1, false, model, 0)
            GLES30.glUniform3f(loc(shadowMarbleProg, "uCenter"), mb.position.x, mb.position.y, mb.position.z)
            GLES30.glUniform1f(loc(shadowMarbleProg, "uRadius"), mb.radius)
            sphere?.draw()
            GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
            sm.end(width, height)
        }

        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // ---- backdrop ---------------------------------------------------------------------
        GLES30.glDisable(GLES30.GL_DEPTH_TEST)
        GLES30.glUseProgram(bgProg)
        GLES30.glUniform3f(loc(bgProg, "uTop"), pal.bgTop.x, pal.bgTop.y, pal.bgTop.z)
        GLES30.glUniform3f(loc(bgProg, "uBottom"), pal.bgBottom.x, pal.bgBottom.y, pal.bgBottom.z)
        GLES30.glUniform1f(loc(bgProg, "uAspect"), width.toFloat() / height)
        GLES30.glBindVertexArray(emptyVao[0])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glBindVertexArray(0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)

        // ---- main scene -------------------------------------------------------------------
        GLES30.glUseProgram(sceneProg)
        GLES30.glUniformMatrix4fv(loc(sceneProg, "uVP"), 1, false, vp, 0)
        GLES30.glUniformMatrix4fv(loc(sceneProg, "uModel"), 1, false, model, 0)
        GLES30.glUniformMatrix4fv(loc(sceneProg, "uLightVP"), 1, false, lightVP, 0)
        GLES30.glUniform3f(loc(sceneProg, "uCam"), 0f, 0f, camDist)
        GLES30.glUniform3f(loc(sceneProg, "uLightDir"), lightDir.x, lightDir.y, lightDir.z)
        GLES30.glUniform1f(loc(sceneProg, "uTime"), time)
        GLES30.glUniform1f(loc(sceneProg, "uGrowT"), growT)
        GLES30.glUniform1f(loc(sceneProg, "uShadowOn"), if (sm != null) 1f else 0f)
        GLES30.glUniform1f(loc(sceneProg, "uTexel"), if (sm != null) 1f / sm.size else 0f)
        if (sm != null) { sm.bind(0); GLES30.glUniform1i(loc(sceneProg, "uShadow"), 0) }
        else { GLES30.glUniform1i(loc(sceneProg, "uShadow"), 0) }

        scene(pal.floorMat, pal.floorRough, 0f, 0f); floorMesh?.draw()
        scene(pal.wallMat, pal.wallRough, 1f, 0f); wallMesh?.draw()
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-2f, -2f)
        scene(Mat.FLAT, 0.5f, 0f, 0.9f); markerMesh?.draw()
        if (hintOn) { scene(Mat.FLAT, 0.5f, 0f, 0.55f); threadMesh?.draw() }
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)

        // ---- marble -----------------------------------------------------------------------
        GLES30.glUseProgram(marbleProg)
        GLES30.glUniformMatrix4fv(loc(marbleProg, "uVP"), 1, false, vp, 0)
        GLES30.glUniformMatrix4fv(loc(marbleProg, "uModel"), 1, false, model, 0)
        GLES30.glUniform3f(loc(marbleProg, "uCam"), 0f, 0f, camDist)
        GLES30.glUniform3f(loc(marbleProg, "uLightDir"), lightDir.x, lightDir.y, lightDir.z)
        GLES30.glUniform3f(loc(marbleProg, "uCenter"), mb.position.x, mb.position.y, mb.position.z)
        GLES30.glUniform1f(loc(marbleProg, "uRadius"), mb.radius)
        GLES30.glUniform3f(loc(marbleProg, "uAccent"), pal.accent.x, pal.accent.y, pal.accent.z)
        val bm = FloatArray(16)
        rollQ.conjugate().toMatrix(bm)
        val body = floatArrayOf(bm[0], bm[1], bm[2], bm[4], bm[5], bm[6], bm[8], bm[9], bm[10])
        GLES30.glUniformMatrix3fv(loc(marbleProg, "uBody"), 1, false, body, 0)
        GLES30.glUniform1f(loc(marbleProg, "uGhost"), 0f)
        sphere?.draw()

        // ghost where the labyrinth hides the marble
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthFunc(GLES30.GL_GREATER)
        GLES30.glDepthMask(false)
        GLES30.glUniform1f(loc(marbleProg, "uGhost"), 1f)
        sphere?.draw()
        GLES30.glDepthMask(true)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun scene(mat: Int, rough: Float, metal: Float, emissive: Float) {
        GLES30.glUniform1i(loc(sceneProg, "uMat"), mat)
        GLES30.glUniform1f(loc(sceneProg, "uRough"), rough)
        GLES30.glUniform1f(loc(sceneProg, "uMetal"), metal)
        GLES30.glUniform1f(loc(sceneProg, "uEmissive"), emissive)
    }

    private val locs = HashMap<String, Int>()
    private fun loc(prog: Int, name: String): Int =
        locs.getOrPut("$prog:$name") { GLES30.glGetUniformLocation(prog, name) }

    companion object { const val GRAVITY = 3.2f }
}
