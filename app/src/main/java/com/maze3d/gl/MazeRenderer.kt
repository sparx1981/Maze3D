package com.maze3d.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.maze3d.audio.Audio
import com.maze3d.audio.NoAudio
import com.maze3d.core.Hazards
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
        fun onToast(message: String)
        val audio: Audio
    }

    private enum class Phase { GROW, PLAY, SOLVED, SINK }

    private class Door(val mid: Vec3, val normal: Vec3, var closed: Boolean)

    // ---- inputs (written from the UI thread) --------------------------------------------
    @Volatile var tiltMode = false
    @Volatile var hintOn = true
    @Volatile var followOn = false
    @Volatile var deviceGravity = Vec3(0f, -1f, 0f)

    // ---- GL resources ---------------------------------------------------------------------
    private var sceneProg = 0
    private var shadowSceneProg = 0
    private var holeProg = 0
    private var sparkProg = 0
    private var cloudProg = 0
    private var doorMesh: GlMesh? = null
    private var holeMesh: GlMesh? = null
    private val sparks = Sparks()
    private var hazards: Hazards? = null
    private var doors: List<Door> = emptyList()
    private var useClouds = false
    private var cloudAmt = 0f
    private var fallTimer = 0f
    private var fallTo = Vec3.ZERO
    private var fallFrom = Vec3.ZERO
    private var goalBursted = false
    private var lastSparkAt = 0f
    private var impactSparkSeed = 0
    private var lastTouchAt = -10f
    private var zoomFollow = 1f
    private val audio: Audio get() = host.audio
    private var doorPrevG = FloatArray(0)
    private var holePrevOpen = FloatArray(0)
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
        holeProg = Shaders.program(Shaders.SCENE_VS, Shaders.HOLE_FS)
        sparkProg = Shaders.program(Shaders.SPARK_VS, Shaders.SPARK_FS)
        cloudProg = Shaders.program(Shaders.BG_VS, Shaders.CLOUD_FS)
        sparks.init()
        doorMesh = GlMesh(GlMesh.SCENE)
        holeMesh = GlMesh(GlMesh.SCENE)
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
        camDist = (r / tanMin + r) * zoom * zoomFollow
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
        val b = SceneBuilder(m, pal)
        builder = b
        floorMesh!!.let { val mb = b.floor(); it.upload(mb.vertices(), mb.indices()) }
        wallMesh!!.let { val mb = b.walls(); it.upload(mb.vertices(), mb.indices()) }
        var wantDoors = rnd.nextFloat() < 0.5f
        var wantHoles = rnd.nextFloat() < 0.5f
        useClouds = rnd.nextFloat() < 0.45f
        if (!wantDoors && !wantHoles && !useClouds) when (rnd.nextInt(3)) { 0 -> wantDoors = true; 1 -> wantHoles = true; else -> useClouds = true }
        val hz = Hazards(m, rnd, wantDoors, wantHoles)
        hazards = hz
        marble!!.hazards = hz
        doors = hz.doorEdges.map { e ->
            val mid = m.mesh.surface.project((m.mesh.vertices[m.edgeA[e]] + m.mesh.vertices[m.edgeB[e]]) * 0.5f)
            Door(mid, m.mesh.surface.normal(mid), false)
        }
        doorMesh!!.let { val mb = b.doors(hz.doorEdges, hz.doorPhase); it.upload(mb.vertices(), mb.indices()) }
        holeMesh!!.let { val mb = b.holes(hz.holeCells, hz.holePhase, hz.holeRadius); it.upload(mb.vertices(), mb.indices()) }
        markerMesh!!.let { val mb = b.markers(hz.doorEdges); it.upload(mb.vertices(), mb.indices()) }
        cloudAmt = 0f
        fallTimer = 0f
        goalBursted = false
        host.onFinish(pal.name + listOfNotNull(if (hz.hasDoors) "Doors" else null, if (hz.hasHoles) "Holes" else null, if (useClouds) "Clouds" else null).joinToString("") { " · $it" }, pal.accent)
        threadCell = -1
        rollQ = Quat.IDENTITY
        growT = 0f
        phase = Phase.GROW
        audio.levelGrow()
        playSeconds = 0f
        lastHud = -1
        updateCamera()
    }

    /** Request a fresh labyrinth; walls sink first. */
    fun requestNew(k: ShapeKind? = null) {
        nextKind = k ?: ShapeKind.values().filter { it != kind }.let { it[rnd.nextInt(it.size)] }
        if (phase != Phase.SINK) { phase = Phase.SINK; audio.levelSink() }
    }

    fun resetMarble() { marble?.reset(); rollQ = Quat.IDENTITY; threadCell = -1 }

    fun resetHint() { threadCell = -1 }

    fun setQualityLevel(q: Int) { quality = q.coerceIn(0, 2) }

    /** [v] in 0..1: 1 is closest. */
    fun setZoomLevel(v: Float) { zoom = 1.6f + (0.55f - 1.6f) * v; updateCamera() }

    /** Rotate about the view axis (two-finger twist). */
    fun twist(angle: Float) { lastTouchAt = time; orient = (Quat.fromAxisAngle(Vec3(0f, 0f, 1f), angle) * orient).normalized() }

    // ---- touch ---------------------------------------------------------------------------
    fun dragStart() { dragging = true; angVel = Vec3.ZERO; lastTouchAt = time }

    fun drag(dx: Float, dy: Float, dtSec: Float) {
        val scale = 0.0075f * zoom
        val axis = Vec3(dy, dx, 0f)
        val angle = axis.length() * scale
        if (angle > 1e-6f) {
            lastTouchAt = time
            orient = (Quat.fromAxisAngle(axis, angle) * orient).normalized()
            if (dtSec > 1e-4f) angVel = axis.normalized() * min(angle / dtSec, 9f)
        }
    }

    fun dragEnd() { dragging = false; lastTouchAt = time }

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

        // chase camera: smoothly turn the piece so the marble stays centred and facing us
        val followTarget = if (followOn) 0.8f else 1f
        if (kotlin.math.abs(zoomFollow - followTarget) > 0.002f) {
            zoomFollow += (followTarget - zoomFollow) * min(1f, dt * 2.5f)
            updateCamera()
        }
        if (followOn && !dragging && phase != Phase.SINK && time - lastTouchAt > 1.0f) {
            val n = mb.surface.normal(mb.position)
            val local = (mb.position.normalized() * 0.5f + n * 0.5f).normalized()
            val w = orient.rotate(local)
            val dotz = w.z.coerceIn(-1f, 1f)
            val ang = kotlin.math.acos(dotz)
            if (ang > 0.01f) {
                val axis = w cross Vec3(0f, 0f, 1f)
                val step = min(ang * (1f - exp(-3.2f * dt)), 2.4f * dt)
                if (axis.lengthSq() > 1e-10f) orient = (Quat.fromAxisAngle(axis, step) * orient).normalized()
                angVel = Vec3.ZERO
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
                if (fallTimer > 0f) {
                    // marble is dropping into a hole; physics paused
                    fallTimer -= dt
                    if (fallTimer <= 0f) {
                        mb.reset(); rollQ = Quat.IDENTITY; threadCell = -1
                        val sc = m.centers[m.start]
                        sparks.burst(sc, mb.surface.normal(sc), 28, 0.9f, Vec3(0.4f, 0.8f, 1f))
                        audio.respawn(panOf(sc))
                    }
                } else {
                    mb.clock = time
                    mb.step(gLocal, dt)
                    rollMarble(mb, dt)
                    audio.roll((mb.velocity.length() / (m.cellSize * 4f)).coerceIn(0f, 1f), pal.floorMat == Mat.WOOD)
                    if (mb.lastImpact > 0.3f && time - lastSparkAt > 0.05f) {
                        lastSparkAt = time
                        sparks.impact(mb.impactPos, mb.impactDir, min(mb.lastImpact / 3f, 1f), pal.wall * 1.2f)
                        audio.impact(min(mb.lastImpact / 3f, 1f), panOf(mb.impactPos))
                    }
                    if (mb.lastImpact > 0.9f && time - lastImpactAt > 0.12f) {
                        lastImpactAt = time
                        host.onImpact(min(mb.lastImpact / 4f, 1f))
                    }
                    val hz = hazards
                    if (hz != null && hz.hasHoles) {
                        val under = mb.position - mb.surface.normal(mb.position) * mb.radius
                        val h = hz.fellInto(mb.cell, under, time)
                        if (h >= 0) {
                            fallTimer = 0.55f
                            audio.fall(panOf(m.centers[hz.holeCells[h]]))
                            host.onToast("Fell into a hole - back to the start")
                            fallFrom = mb.position
                            fallTo = m.centers[hz.holeCells[h]] - mb.surface.normal(m.centers[hz.holeCells[h]]) * (mb.radius * 2.5f)
                            sparks.burst(m.centers[hz.holeCells[h]], mb.surface.normal(m.centers[hz.holeCells[h]]), 10, 0.5f, pal.accent)
                        }
                    }
                    if (mb.atGoal) { phase = Phase.SOLVED; solvedTimer = 0f }
                }
                updateDoors(m)
            }
            Phase.SOLVED -> {
                if (!goalBursted) {
                    goalBursted = true
                    audio.goal()
                    val gc = m.centers[m.goal]
                    sparks.burst(gc, mb.surface.normal(gc), 70, 1.5f, Vec3(1f, 0.85f, 0.35f))
                    sparks.burst(gc, mb.surface.normal(gc), 40, 0.8f, Vec3(0.6f, 1f, 0.7f))
                }
                solvedTimer += dt
                if (solvedTimer > 1.6f) { phase = Phase.SINK; nextKind = pickNext(); audio.levelSink() }
            }
            Phase.SINK -> {
                growT -= dt / 0.8f
                if (growT <= 0f) { newLabyrinth(nextKind ?: pickNext()); nextKind = null }
            }
        }

        if (phase != Phase.PLAY || fallTimer > 0f) audio.roll(0f, false)
        audio.wind(cloudAmt)
        val gl = orient.inverseRotate(Vec3(0f, -1f, 0f)) * GRAVITY
        sparks.update(dt, gl)
        val cloudTarget = if (useClouds && phase != Phase.SINK) 1f else 0f
        cloudAmt += (cloudTarget - cloudAmt) * min(1f, dt * 0.8f)

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

    /** Door/hole state changes: slam sparks and the matching sounds, positioned relative to the marble. */
    private fun updateDoors(m: Maze) {
        val hz = hazards ?: return
        val mb = marble ?: return
        if (doorPrevG.size != doors.size) doorPrevG = FloatArray(doors.size) { hz.doorHeight(it, time) }
        for (i in doors.indices) {
            val g = hz.doorHeight(i, time)
            val closed = g > 0.5f
            if (closed && !doors[i].closed && phase == Phase.PLAY) {
                sparks.burst(doors[i].mid, doors[i].normal, 6, 0.5f, pal.door)
            }
            doors[i].closed = closed
            val prev = doorPrevG[i]
            if (phase == Phase.PLAY) {
                if (prev <= 0.02f && g > 0.02f) audio.doorClose(proximity(doors[i].mid, mb.position), panOf(doors[i].mid))
                else if (prev >= 0.98f && g < 0.98f) audio.doorOpen(proximity(doors[i].mid, mb.position), panOf(doors[i].mid))
            }
            doorPrevG[i] = g
        }
        if (holePrevOpen.size != hz.holeCells.size) holePrevOpen = FloatArray(hz.holeCells.size) { hz.holeOpen(it, time) }
        for (i in hz.holeCells.indices) {
            val o = hz.holeOpen(i, time)
            val c = m.centers[hz.holeCells[i]]
            if (phase == Phase.PLAY) {
                if (holePrevOpen[i] < 0.05f && o >= 0.05f) audio.holeOpen(proximity(c, mb.position) * 0.8f, panOf(c))
                else if (holePrevOpen[i] > 0.6f && o <= 0.6f) audio.holeClose(proximity(c, mb.position) * 0.7f, panOf(c))
            }
            holePrevOpen[i] = o
        }
    }

    private fun panOf(local: Vec3): Float = (orient.rotate(local).x / 1.2f).coerceIn(-1f, 1f)

    /** Loudness of something at [local] as heard from the marble: near is loud, the far side of the piece is muffled. */
    private fun proximity(local: Vec3, from: Vec3): Float {
        val d = (local - from).length()
        val back = if (orient.rotate(local).z < -0.1f) 0.5f else 1f
        return (kotlin.math.exp(-d / 0.55f) * 1.1f * back).coerceIn(0f, 1f)
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
        GLES30.glUniform1f(loc(prog, "uTime"), time)
        if (shadowPass) {
            GLES30.glUniform1f(loc(prog, "uDoorMode"), 0f)
            floorMesh?.draw(); wallMesh?.draw()
            GLES30.glUniform1f(loc(prog, "uDoorMode"), 1f)
            doorMesh?.draw()
            GLES30.glUniform1f(loc(prog, "uDoorMode"), 0f)
        }
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
        GLES30.glUniform1f(loc(sceneProg, "uDoorMode"), 0f)
        GLES30.glUniform1f(loc(sceneProg, "uShadowOn"), if (sm != null) 1f else 0f)
        GLES30.glUniform1f(loc(sceneProg, "uTexel"), if (sm != null) 1f / sm.size else 0f)
        if (sm != null) { sm.bind(0); GLES30.glUniform1i(loc(sceneProg, "uShadow"), 0) }
        else { GLES30.glUniform1i(loc(sceneProg, "uShadow"), 0) }

        scene(pal.floorMat, pal.floorRough, 0f, 0f); floorMesh?.draw()
        scene(pal.wallMat, pal.wallRough, 1f, 0f); wallMesh?.draw()
        GLES30.glUniform1f(loc(sceneProg, "uDoorMode"), 1f)
        doorMesh?.draw()
        GLES30.glUniform1f(loc(sceneProg, "uDoorMode"), 0f)
        drawHoles()
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
        var drawPos = mb.position
        var drawR = mb.radius
        if (fallTimer > 0f) {
            val k = 1f - fallTimer / 0.55f
            val e = k * k
            drawPos = Vec3.lerp(fallFrom, fallTo, e)
            drawR = mb.radius * (1f - 0.85f * e)
        }
        GLES30.glUniform3f(loc(marbleProg, "uCenter"), drawPos.x, drawPos.y, drawPos.z)
        GLES30.glUniform1f(loc(marbleProg, "uRadius"), drawR)
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

        // sparks: additive glow points
        GLES30.glUseProgram(sparkProg)
        GLES30.glUniformMatrix4fv(loc(sparkProg, "uVP"), 1, false, vp, 0)
        GLES30.glUniformMatrix4fv(loc(sparkProg, "uModel"), 1, false, model, 0)
        GLES30.glUniform1f(loc(sparkProg, "uScale"), height / (2f * tan(Math.toRadians(19.0)).toFloat()))
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        sparks.draw(sparkProg, 1f)
        GLES30.glDepthMask(true)

        // drifting clouds over everything
        if (cloudAmt > 0.01f) {
            GLES30.glDisable(GLES30.GL_DEPTH_TEST)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glUseProgram(cloudProg)
            GLES30.glUniform1f(loc(cloudProg, "uTime"), time)
            GLES30.glUniform1f(loc(cloudProg, "uAspect"), width.toFloat() / height)
            GLES30.glUniform1f(loc(cloudProg, "uAmount"), cloudAmt)
            GLES30.glUniform3f(loc(cloudProg, "uTint"), pal.bgTop.x * 1.6f, pal.bgTop.y * 1.6f, pal.bgTop.z * 1.6f)
            GLES30.glBindVertexArray(emptyVao[0])
            GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
            GLES30.glBindVertexArray(0)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        }
        GLES30.glDisable(GLES30.GL_BLEND)
    }

    private fun drawHoles() {
        val hz = hazards ?: return
        if (!hz.hasHoles) return
        GLES30.glUseProgram(holeProg)
        GLES30.glUniformMatrix4fv(loc(holeProg, "uVP"), 1, false, vp, 0)
        GLES30.glUniformMatrix4fv(loc(holeProg, "uModel"), 1, false, model, 0)
        GLES30.glUniform1f(loc(holeProg, "uGrowT"), growT)
        GLES30.glUniform1f(loc(holeProg, "uTime"), time)
        GLES30.glUniform1f(loc(holeProg, "uDoorMode"), 0f)
        GLES30.glUniform1f(loc(holeProg, "uAppear"), (growT - 0.2f).coerceIn(0f, 1f))
        GLES30.glUniform3f(loc(holeProg, "uAccent"), pal.accent.x, pal.accent.y, pal.accent.z)
        GLES30.glUniform3f(loc(holeProg, "uLightDir"), lightDir.x, lightDir.y, lightDir.z)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-2f, -2f)
        holeMesh?.draw()
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(sceneProg)
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
