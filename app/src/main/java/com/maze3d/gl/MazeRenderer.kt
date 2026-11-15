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
import com.maze3d.core.Rain
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
        fun onReady()
        /** [cooldown] seconds until the next hint (0 = ready); [active] while the thread is showing. */
        fun onHint(cooldown: Int, active: Boolean)
        val audio: Audio
    }

    private enum class Phase { GROW, PLAY, SOLVED, SINK }

    private class Door(val mid: Vec3, val normal: Vec3, var closed: Boolean)

    // ---- inputs (written from the UI thread) --------------------------------------------
    @Volatile var tiltMode = false
    @Volatile var hintActive = false
    @Volatile var followOn = false
    @Volatile var deviceGravity = Vec3(0f, -1f, 0f)

    // ---- GL resources ---------------------------------------------------------------------
    private var sceneProg = 0
    private var shadowSceneProg = 0
    private var holeProg = 0
    private var smokeProg = 0
    private var cube: ReflectionCube? = null
    private var cubeFace = 0
    private var cubeFresh = 6
    private val dust = Sparks()
    private var trailAt = 0f
    private var puddleProg = 0
    private var rainProg = 0
    private var puddleMesh: GlMesh? = null
    private var rainVbo = IntArray(1)
    private var rainVao = IntArray(1)
    private var rain: Rain? = null
    private var rainWet = 0f
    private var dropAcc = 0f
    private var lastWakeAt = 0f
    private val rippleLocal = Array(32) { Vec3.ZERO }
    private val rippleStart = FloatArray(32) { -100f }
    private val rippleAmp = FloatArray(32)
    private var rippleNext = 0
    private val ripUniform = FloatArray(32 * 4)
    private val levelUniform = FloatArray(24)
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
    private var hintLeft = 0f
    private var hintCool = 0f
    private var lastHintCode = -1
    private var framesDrawn = 0
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
    private val post = PostFx()
    private val pp = PostParams()
    private var postOn = false
    private var renderScale = 1f
    private var rw = 1
    private var rh = 1
    private var slowFrames = 0f
    private var fastFrames = 0f
    @Volatile var refreshHz = 60f
    private var p00 = 1f
    private var p11 = 1f
    private var cloudDensity = 0.8f
    private var windX = 0.05f
    private var windY = 0.02f

    // ---- surface callbacks ----------------------------------------------------------------
    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        sceneProg = Shaders.program(Shaders.SCENE_VS, Shaders.SCENE_FS)
        marbleProg = Shaders.program(Shaders.MARBLE_VS, Shaders.MARBLE_FS)
        bgProg = Shaders.program(Shaders.BG_VS, Shaders.BG_FS)
        shadowSceneProg = Shaders.program(Shaders.SCENE_VS, Shaders.SHADOW_FS)
        shadowMarbleProg = Shaders.program(Shaders.MARBLE_VS, Shaders.SHADOW_FS)
        holeProg = Shaders.program(Shaders.SCENE_VS, Shaders.HOLE_FS)
        sparkProg = Shaders.program(Shaders.SPARK_VS, Shaders.SPARK_FS)
        post.init()
        sparks.init()
        dust.init()
        smokeProg = Shaders.program(Shaders.SPARK_VS, PostShaders.SMOKE_FS)
        puddleProg = Shaders.program(Shaders.SCENE_VS, Shaders.PUDDLE_FS)
        rainProg = Shaders.program(Shaders.RAIN_VS, Shaders.RAIN_FS)
        puddleMesh = GlMesh(GlMesh.SCENE)
        run {
            val r = java.util.Random(99)
            val seeds = FloatArray(RAIN_DROPS * 4) { r.nextFloat() }
            GLES30.glGenVertexArrays(1, rainVao, 0); GLES30.glGenBuffers(1, rainVbo, 0)
            GLES30.glBindVertexArray(rainVao[0]); GLES30.glBindBuffer(GLES30.GL_ARRAY_BUFFER, rainVbo[0])
            val bb = java.nio.ByteBuffer.allocateDirect(seeds.size * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
            bb.put(seeds).position(0)
            GLES30.glBufferData(GLES30.GL_ARRAY_BUFFER, seeds.size * 4, bb, GLES30.GL_STATIC_DRAW)
            GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 16, 0)
            GLES30.glBindVertexArray(0)
        }
        doorMesh = GlMesh(GlMesh.SCENE)
        holeMesh = GlMesh(GlMesh.SCENE)
        locs.clear(); shadow = null; appliedQuality = -1; postOn = false
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
        post.resizeClouds(width, height)
        appliedQuality = -1
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
        p00 = proj[0]; p11 = proj[5]
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
        val wantRain = rnd.nextFloat() < 0.35f
        if (wantRain && rnd.nextFloat() < 0.75f) useClouds = true
        if (!wantDoors && !wantHoles && !useClouds && !wantRain) when (rnd.nextInt(4)) { 0 -> wantDoors = true; 1 -> wantHoles = true; 2 -> useClouds = true; else -> { } }
        val rainNow = wantRain || (!wantDoors && !wantHoles && !useClouds)
        val hz = Hazards(m, rnd, wantDoors, wantHoles)
        rain = if (rainNow) Rain(m, rnd) else null
        marble!!.rain = rain
        rippleStart.fill(-100f); rainWet = 0f
        rain?.let { rn ->
            val mbp = b.puddles(rn.cells, rn.maxRadius * 0.9f)
            puddleMesh!!.upload(mbp.vertices(), mbp.indices())
            if (useClouds) cloudDensity = max(cloudDensity, 0.75f)
        }
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
        cloudDensity = 0.5f + 0.3f * rnd.nextFloat()
        val wa = rnd.nextFloat() * 6.2831853f
        windX = kotlin.math.cos(wa) * 0.07f; windY = kotlin.math.sin(wa) * 0.035f
        fallTimer = 0f
        hintLeft = 0f; hintCool = 0f
        goalBursted = false
        host.onFinish(pal.name + listOfNotNull(if (hz.hasDoors) "Doors" else null, if (hz.hasHoles) "Holes" else null, if (useClouds) "Clouds" else null, if (rain != null) "Rain" else null).joinToString("") { " · $it" }, pal.accent)
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

    /** Timed hint: shows the golden thread for [HINT_SECONDS], then it fades; usable again after [HINT_COOLDOWN]. */
    fun requestHint() {
        if (phase != Phase.PLAY || hintCool > 0f) { if (hintCool > 0f) host.onToast("Hint ready in ${kotlin.math.ceil(hintCool).toInt()}s"); return }
        hintLeft = HINT_SECONDS
        hintCool = HINT_COOLDOWN
        threadCell = -1
    }

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
        val raw = (now - lastNanos) / 1e9f
        val dt = raw.coerceIn(0.001f, 0.05f)
        lastNanos = now
        time += dt
        adaptResolution(raw.coerceIn(0.001f, 0.2f))
        update(dt)
        draw()
        if (++framesDrawn == 4) host.onReady()
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
                    val spd = mb.velocity.length() / (m.cellSize * 4f)
                    if (spd > 0.3f && time - trailAt > 0.025f) {
                        trailAt = time
                        val under = mb.position - mb.surface.normal(mb.position) * (mb.radius * 0.8f)
                        sparks.emit(under, Vec3.ZERO, 0.38f, pal.accent, 0.012f + 0.01f * spd)
                    }
                    audio.roll((mb.velocity.length() / (m.cellSize * 4f)).coerceIn(0f, 1f), pal.floorMat == Mat.WOOD)
                    if (mb.lastImpact > 0.3f && time - lastSparkAt > 0.05f) {
                        lastSparkAt = time
                        sparks.impact(mb.impactPos, mb.impactDir, min(mb.lastImpact / 3f, 1f), pal.wall * 1.2f)
                        audio.impact(min(mb.lastImpact / 3f, 1f), panOf(mb.impactPos))
                        dust.puff(mb.impactPos, mb.impactDir, 3, 0.05f, Vec3(0.78f, 0.78f, 0.8f))
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

        updateRain(dt)
        if (phase != Phase.PLAY || fallTimer > 0f) audio.roll(0f, false)
        audio.wind(cloudAmt)
        val gl = orient.inverseRotate(Vec3(0f, -1f, 0f)) * GRAVITY
        sparks.update(dt, gl)
        dust.update(dt, gl * -0.5f)
        val cloudTarget = if (useClouds && phase != Phase.SINK) 1f else 0f
        cloudAmt += (cloudTarget - cloudAmt) * min(1f, dt * 0.8f)

        val sec = playSeconds.toInt()
        val code = sec * 4 + (if (phase == Phase.SOLVED) 1 else 0)
        if (code != lastHud) {
            lastHud = code
            host.onHud(kind.label, sec, phase == Phase.SOLVED)
        }
        if (hintLeft > 0f) hintLeft = max(0f, hintLeft - dt)
        if (hintCool > 0f) hintCool = max(0f, hintCool - dt)
        if (phase == Phase.SINK) { hintLeft = 0f; hintCool = 0f }
        val hintCode = kotlin.math.ceil(hintCool).toInt() * 2 + (if (hintLeft > 0f) 1 else 0)
        if (hintCode != lastHintCode) {
            lastHintCode = hintCode
            hintActive = hintLeft > 0f
            host.onHint(kotlin.math.ceil(hintCool).toInt(), hintActive)
        }
        if (hintLeft > 0f && mb.cell != threadCell && phase != Phase.SINK) {
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

    private fun addRipple(local: Vec3, amp: Float) {
        rippleLocal[rippleNext] = local; rippleStart[rippleNext] = time; rippleAmp[rippleNext] = amp
        rippleNext = (rippleNext + 1) % rippleLocal.size
    }

    /** Rain intensity, puddles, drop ripples and the marble's wake. */
    private fun updateRain(dt: Float) {
        val rn = rain
        val m = maze ?: return
        val mb = marble ?: return
        if (rn == null) { rainWet = max(0f, rainWet - dt * 0.2f); audio.rain(0f); return }
        val target = if (phase == Phase.SINK) 0f else 1f
        rn.intensity += (target - rn.intensity) * min(1f, dt * 0.45f)
        rn.update(dt, rn.intensity > 0.05f)
        rainWet += (rn.intensity - rainWet) * min(1f, dt * 0.5f)
        audio.rain(rn.intensity)

        // raindrops landing in puddles
        val wet = rn.cells.indices.filter { rn.level[it] > 0.12f }
        if (wet.isNotEmpty() && rn.intensity > 0.2f) {
            dropAcc += dt * (5f + 9f * rn.intensity) * (0.4f + 0.6f * wet.size.coerceAtMost(8) / 8f)
            while (dropAcc >= 1f) {
                dropAcc -= 1f
                val i = wet[rnd.nextInt(wet.size)]
                val c = m.centers[rn.cells[i]]
                val n = mb.surface.normal(c)
                var t = Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f)
                t = (t - n * (t dot n)).normalized()
                val p = mb.surface.project(c + t * (rn.radius(i) * 0.8f * kotlin.math.sqrt(rnd.nextFloat())))
                addRipple(p + mb.surface.normal(p) * 0.002f, 0.55f + 0.5f * rnd.nextFloat())
                if (rnd.nextFloat() < 0.5f) repeat(2) {
                    val v = (n + Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f) * 1.1f).normalized() * (0.12f + 0.12f * rnd.nextFloat())
                    sparks.emit(p, v, 0.22f, Vec3(0.55f, 0.7f, 0.9f), 0.006f)
                }
            }
        }

        // the marble's wake while it rolls through water
        if (phase == Phase.PLAY && fallTimer <= 0f && mb.wet > 0.12f) {
            val spd = mb.velocity.length() / (m.cellSize * 4f)
            if (spd > 0.06f && time - lastWakeAt > 0.07f) {
                lastWakeAt = time
                val under = mb.position - mb.surface.normal(mb.position) * mb.radius
                addRipple(under + mb.surface.normal(under) * 0.002f, 0.7f + 1.4f * spd.coerceAtMost(1f))
                if (spd > 0.3f) {
                    val n = mb.surface.normal(under)
                    repeat(2) {
                        val v = (n + Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f)).normalized() * (0.2f + 0.3f * spd)
                        sparks.emit(under, v, 0.28f, Vec3(0.6f, 0.78f, 1f), 0.007f)
                    }
                    audio.splash(0.25f + 0.6f * spd, panOf(under))
                }
            }
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
        cube?.release()
        cube = when (quality) { 2 -> ReflectionCube(128); 1 -> ReflectionCube(64); else -> null }
        if (cube?.ok == false) { cube?.release(); cube = null }
        cubeFresh = 6
        if (quality >= 1) {
            renderScale = 1f
            postOn = post.resize(width, height, renderScale)
        } else {
            post.dropScene(); postOn = false
        }
        rw = if (postOn) post.sceneW else width
        rh = if (postOn) post.sceneH else height
    }

    /** Keeps the frame rate at the display's refresh rate by trading render resolution when the GPU is struggling. */
    private fun adaptResolution(dt: Float) {
        if (!postOn || quality < 1) return
        val target = 1f / refreshHz.coerceIn(30f, 144f)
        if (dt > target * 1.3f) { slowFrames += dt; fastFrames = 0f } else { fastFrames += dt; slowFrames = max(0f, slowFrames - dt * 0.5f) }
        var next = renderScale
        if (slowFrames > 1.2f && renderScale > 0.61f) { next = max(0.6f, renderScale - 0.1f); slowFrames = 0f }
        else if (fastFrames > 6f && renderScale < 0.99f && dt < target * 1.12f) { next = min(1f, renderScale + 0.1f); fastFrames = 0f }
        if (next != renderScale) {
            renderScale = next
            postOn = post.resize(width, height, renderScale)
            rw = if (postOn) post.sceneW else width
            rh = if (postOn) post.sceneH else height
        }
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

    /** Draws the labyrinth itself (floor, walls, doors, holes, markers, hint thread) with the given camera. */
    private fun renderWorld(vpm: FloatArray, cam: Vec3, sm: ShadowMap?) {
        GLES30.glUseProgram(sceneProg)
        GLES30.glUniformMatrix4fv(loc(sceneProg, "uVP"), 1, false, vpm, 0)
        GLES30.glUniformMatrix4fv(loc(sceneProg, "uModel"), 1, false, model, 0)
        GLES30.glUniformMatrix4fv(loc(sceneProg, "uLightVP"), 1, false, lightVP, 0)
        GLES30.glUniform3f(loc(sceneProg, "uCam"), cam.x, cam.y, cam.z)
        GLES30.glUniform3f(loc(sceneProg, "uLightDir"), lightDir.x, lightDir.y, lightDir.z)
        GLES30.glUniform1f(loc(sceneProg, "uTime"), time)
        GLES30.glUniform1f(loc(sceneProg, "uGrowT"), growT)
        GLES30.glUniform1f(loc(sceneProg, "uDoorMode"), 0f)
        GLES30.glUniform1f(loc(sceneProg, "uAlpha"), 1f)
        GLES30.glUniform1f(loc(sceneProg, "uWet"), rainWet)
        GLES30.glUniform1f(loc(sceneProg, "uShadowOn"), if (sm != null) 1f else 0f)
        GLES30.glUniform1f(loc(sceneProg, "uTexel"), if (sm != null) 1f / sm.size else 0f)
        if (sm != null) { sm.bind(0); GLES30.glUniform1i(loc(sceneProg, "uShadow"), 0) }
        else { GLES30.glUniform1i(loc(sceneProg, "uShadow"), 0) }

        scene(pal.floorMat, pal.floorRough, 0f, 0f); floorMesh?.draw()
        scene(pal.wallMat, pal.wallRough, 1f, 0f); wallMesh?.draw()
        GLES30.glUniform1f(loc(sceneProg, "uDoorMode"), 1f)
        doorMesh?.draw()
        GLES30.glUniform1f(loc(sceneProg, "uDoorMode"), 0f)
        drawHoles(vpm)
        drawPuddles(vpm, cam)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-2f, -2f)
        scene(Mat.FLAT, 0.5f, 0f, 0.9f); markerMesh?.draw()
        if (hintLeft > 0f) {
            val alpha = min(min((HINT_SECONDS - hintLeft) / 0.35f, hintLeft / 1.3f), 1f).coerceIn(0f, 1f)
            GLES30.glEnable(GLES30.GL_BLEND)
            GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
            GLES30.glUniform1f(loc(sceneProg, "uAlpha"), alpha)
            scene(Mat.FLAT, 0.5f, 0f, 0.55f); threadMesh?.draw()
            GLES30.glUniform1f(loc(sceneProg, "uAlpha"), 1f)
            GLES30.glDisable(GLES30.GL_BLEND)
        }
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
    }

    private val cubeP = FloatArray(16)
    private val cubeV = FloatArray(16)
    private val cubeVP = FloatArray(16)

    /** Refreshes the marble's reflection map: all six faces right after a level starts, then a face per frame. */
    private fun updateCube(mb: Marble, sm: ShadowMap?) {
        val c = cube ?: return
        val center = orient.rotate(mb.position)
        Matrix.perspectiveM(cubeP, 0, 90f, 1f, 0.02f, 12f)
        val n = if (cubeFresh > 0) cubeFresh else 1
        repeat(n) {
            val f = cubeFace
            cubeFace = (cubeFace + 1) % 6
            val d = ReflectionCube.DIRS[f]; val u = ReflectionCube.UPS[f]
            Matrix.setLookAtM(cubeV, 0, center.x, center.y, center.z, center.x + d.x, center.y + d.y, center.z + d.z, u.x, u.y, u.z)
            Matrix.multiplyMM(cubeVP, 0, cubeP, 0, cubeV, 0)
            c.begin(f)
            GLES30.glEnable(GLES30.GL_DEPTH_TEST); GLES30.glDepthMask(true); GLES30.glDepthFunc(GLES30.GL_LEQUAL)
            renderWorld(cubeVP, center, sm)
        }
        cubeFresh = 0
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
        }

        // clouds render into their own small texture first
        val cloudsOn = cloudAmt > 0.01f
        if (cloudsOn) {
            val storm = rain != null
            val tint = if (storm) floatArrayOf(0.50f, 0.55f, 0.64f) else floatArrayOf(0.78f + pal.bgTop.x * 0.35f, 0.82f + pal.bgTop.y * 0.3f, 0.9f + pal.bgTop.z * 0.2f)
            val shade = if (storm) floatArrayOf(0.10f, 0.12f, 0.16f) else floatArrayOf(0.16f + pal.bgTop.x * 0.7f, 0.2f + pal.bgTop.y * 0.7f, 0.28f + pal.bgTop.z * 0.7f)
            post.renderClouds(time, width.toFloat() / height, cloudAmt, cloudDensity, windX, windY, tint, shade)
        }
        updateCube(mb, sm)
        if (postOn) post.beginScene() else { GLES30.glBindFramebuffer(GLES30.GL_FRAMEBUFFER, 0); GLES30.glViewport(0, 0, width, height) }
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthMask(true)
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

        renderWorld(vp, Vec3(0f, 0f, camDist), sm)

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
        val cb = cube
        if (cb != null) { cb.bind(1); GLES30.glUniform1i(loc(marbleProg, "uCube"), 1); GLES30.glUniform1f(loc(marbleProg, "uCubeOn"), 1f) }
        else GLES30.glUniform1f(loc(marbleProg, "uCubeOn"), 0f)
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
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
        GLES30.glUniform1f(loc(sparkProg, "uScale"), rh / (2f * tan(Math.toRadians(19.0)).toFloat()))
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        sparks.draw(sparkProg, 1f)
        GLES30.glDepthMask(true)

        // soft dust and puffs blend normally
        GLES30.glUseProgram(smokeProg)
        GLES30.glUniformMatrix4fv(loc(smokeProg, "uVP"), 1, false, vp, 0)
        GLES30.glUniformMatrix4fv(loc(smokeProg, "uModel"), 1, false, model, 0)
        GLES30.glUniform1f(loc(smokeProg, "uScale"), rh / (2f * tan(Math.toRadians(19.0)).toFloat()))
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        dust.draw(smokeProg, 1f)
        val rnI = rain?.intensity ?: 0f
        if (rnI > 0.02f) {
            GLES30.glUseProgram(rainProg)
            GLES30.glUniformMatrix4fv(loc(rainProg, "uVP"), 1, false, vp, 0)
            GLES30.glUniform1f(loc(rainProg, "uTime"), time)
            GLES30.glUniform1f(loc(rainProg, "uScale"), rh / (2f * tan(Math.toRadians(19.0)).toFloat()))
            GLES30.glUniform1f(loc(rainProg, "uIntensity"), rnI)
            GLES30.glBindVertexArray(rainVao[0])
            GLES30.glDrawArrays(GLES30.GL_POINTS, 0, RAIN_DROPS)
            GLES30.glBindVertexArray(0)
        }
        GLES30.glDepthMask(true)

        GLES30.glDisable(GLES30.GL_BLEND)
        if (postOn) {
            pp.near = 0.1f; pp.far = 40f; pp.p00 = p00; pp.p11 = p11
            pp.focus = camDist; pp.focusRange = kind.outerRadius * 1.1f
            pp.time = time
            pp.cloudOn = cloudsOn
            pp.ssao = quality >= 2; pp.dof = quality >= 2
            pp.aoAmt = 0.85f; pp.dofAmt = 0.85f
            pp.bloomAmt = if (quality >= 2) 0.34f else 0.26f
            pp.fxaa = true
            post.finish(pp)
        } else if (cloudsOn) {
            post.blitClouds()
        }
    }

    private fun drawPuddles(vpm: FloatArray, cam: Vec3) {
        val rn = rain ?: return
        if (rn.cells.isEmpty() || rn.level.maxOrNull()?.let { it < 0.01f } != false) return
        GLES30.glUseProgram(puddleProg)
        GLES30.glUniformMatrix4fv(loc(puddleProg, "uVP"), 1, false, vpm, 0)
        GLES30.glUniformMatrix4fv(loc(puddleProg, "uModel"), 1, false, model, 0)
        GLES30.glUniform1f(loc(puddleProg, "uGrowT"), growT)
        GLES30.glUniform1f(loc(puddleProg, "uTime"), time)
        GLES30.glUniform1f(loc(puddleProg, "uDoorMode"), 0f)
        GLES30.glUniform3f(loc(puddleProg, "uCam"), cam.x, cam.y, cam.z)
        GLES30.glUniform3f(loc(puddleProg, "uLightDir"), lightDir.x, lightDir.y, lightDir.z)
        levelUniform.fill(0f)
        for (i in rn.level.indices) if (i < levelUniform.size) levelUniform[i] = rn.level[i]
        GLES30.glUniform1fv(loc(puddleProg, "uLevel"), 24, levelUniform, 0)
        for (i in rippleLocal.indices) {
            val age = time - rippleStart[i]
            val w = orient.rotate(rippleLocal[i])
            ripUniform[i * 4] = w.x; ripUniform[i * 4 + 1] = w.y; ripUniform[i * 4 + 2] = w.z
            ripUniform[i * 4 + 3] = if (age in 0f..2.4f) age else -1f
        }
        GLES30.glUniform4fv(loc(puddleProg, "uRip"), 32, ripUniform, 0)
        GLES30.glUniform1fv(loc(puddleProg, "uRipA"), 32, rippleAmp, 0)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-1.5f, -1.5f)
        puddleMesh?.draw()
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(sceneProg)
    }

    private fun drawHoles(vpm: FloatArray) {
        val hz = hazards ?: return
        if (!hz.hasHoles) return
        GLES30.glUseProgram(holeProg)
        GLES30.glUniformMatrix4fv(loc(holeProg, "uVP"), 1, false, vpm, 0)
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

    companion object { const val RAIN_DROPS = 900; const val GRAVITY = 3.2f; const val HINT_SECONDS = 6f; const val HINT_COOLDOWN = 20f }
}
