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
import com.maze3d.core.Terrain
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

    private enum class Phase { GROW, PLAY, SOLVED, SINK, MENU }

    private class Door(val mid: Vec3, val normal: Vec3, var closed: Boolean)

    // ---- inputs (written from the UI thread) --------------------------------------------
    @Volatile var tiltMode = false
    @Volatile var hintActive = false
    /** True while the menu is open mid-game: physics, timers and hazards stand still. */
    @Volatile var paused = false
    /** True before a game has started: the level just turns slowly behind the menu. */
    @Volatile var menuMode = true
    private var pendingTerrain: Terrain? = null
    private var pendingPlain = false
    @Volatile var followOn = false
    /** User settings (menu): 0.4 .. 2.5, 1 = default. */
    @Volatile var touchSens = 1f
    @Volatile var gyroSens = 1f
    /** 0 = orbit (turn the piece by hand), 1 = orbit that follows the marble, 2 = third-person chase camera. */
    @Volatile var cameraMode = 0
        private set
    @Volatile private var chaseIx = 0f
    @Volatile private var chaseIy = 0f
    @Volatile private var joyActive = false
    @Volatile private var pendingYaw = 0f
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
    private var trailProg = 0
    private val trail = TrailBuffer()
    private var terrain = Terrain.NONE
    private var dirt = 0f
    private var dustAt = 0f
    private var rainProg = 0
    private var puddleMesh: GlMesh? = null
    private var rainVbo = IntArray(1)
    private var rainVao = IntArray(1)
    private var rain: Rain? = null
    private var rainWet = 0f
    private var dropAcc = 0f
    private var splashAcc = 0f
    private var lastWakeAt = 0f
    private val rippleLocal = Array(32) { Vec3.ZERO }
    private val rippleStart = FloatArray(32) { -100f }
    private val rippleAmp = FloatArray(32)
    private var rippleNext = 0
    private val ripUniform = FloatArray(32 * 4)
    private val levelUniform = FloatArray(24)
    private val puddleUp = FloatArray(24)
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
    // chase camera state (all in the piece's local space until the final transform)
    private var chaseBlend = 0f
    private var chaseF = Vec3(0f, 0f, 1f)
    private var chaseInit = false
    private var eyeS = Vec3.ZERO
    private var tgtS = Vec3.ZERO
    private var upS = Vec3(0f, 1f, 0f)
    private var chaseSpeed = 0f
    private var eye = Vec3(0f, 0f, 3f)
    private var camRight = Vec3(1f, 0f, 0f)
    private var camUp = Vec3(0f, 1f, 0f)
    private var camFwd = Vec3(0f, 0f, -1f)
    private var fovNow = 38f
    private var nearNow = 0.1f
    private var tanHalf = tan(Math.toRadians(19.0)).toFloat()
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
        trailProg = Shaders.program(Shaders.TRAIL_VS, Shaders.TRAIL_FS)
        trail.init()
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
            GLES30.glEnableVertexAttribArray(0); GLES30.glVertexAttribPointer(0, 4, GLES30.GL_FLOAT, false, 16, 0); GLES30.glVertexAttribDivisor(0, 1)
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

    /** Orbit distance that frames the whole piece; the per-frame camera pose is built in [computeCamera]. */
    private fun updateCamera() {
        val aspect = width.toFloat() / height
        val tanY = tan(Math.toRadians(19.0)).toFloat()
        val tanMin = min(tanY, tanY * aspect)
        val r = kind.outerRadius * 1.12f
        camDist = (r / tanMin + r) * zoom * zoomFollow
        computeCamera(0f)
    }

    private fun ease01(x: Float): Float { val c = x.coerceIn(0f, 1f); return c * c * (3f - 2f * c) }

    /** Points the chase camera down the first stretch of the solution. */
    private fun initChase() {
        val m = maze ?: return
        val mb = marble ?: return
        val path = m.solution
        val n = mb.surface.normal(mb.position)
        val to = if (path.size > 1) m.centers[path[1]] - m.centers[path[0]] else Vec3(0f, 0f, 1f)
        var f = to - n * (to dot n)
        if (f.lengthSq() < 1e-8f) f = Vec3(1f, 0f, 0f) - n * n.x
        chaseF = f.normalized()
        chaseInit = false
    }

    /**
     * Builds the camera for this frame. Orbit mode looks at the piece from a distance; chase mode sits behind and
     * above the marble and looks down the track ahead. Switching blends smoothly between the two poses.
     */
    private fun computeCamera(dt: Float) {
        val aspect = width.toFloat() / height
        val wantChase = cameraMode == 2 && phase != Phase.MENU
        chaseBlend += ((if (wantChase) 1f else 0f) - chaseBlend) * min(1f, dt * 5f)
        if (!wantChase && chaseBlend < 0.003f) chaseBlend = 0f
        var eyeV = Vec3(0f, 0f, camDist)
        var tgt = Vec3.ZERO
        var up = Vec3(0f, 1f, 0f)
        var fov = 38f
        var near = 0.1f
        val mb = marble
        val m = maze
        if (chaseBlend > 0.002f && mb != null && m != null) {
            val cs = m.cellSize
            val n = mb.surface.normal(mb.position)
            var f = chaseF - n * (chaseF dot n)
            f = if (f.lengthSq() < 1e-8f) (Vec3(1f, 0f, 0f) - n * n.x).normalized() else f.normalized()
            val yaw = pendingYaw
            if (yaw != 0f) { pendingYaw = 0f; f = Quat.fromAxisAngle(n, yaw).rotate(f) }
            val v = mb.velocity
            val vt = v - n * (v dot n)
            val sp = vt.length()
            if (sp > cs * 0.25f && dt > 0f) {                      // swing round behind the direction of travel
                val a = 1f - exp(-3.2f * dt * min(1f, sp / (cs * 1.5f)))
                f = (f * (1f - a) + (vt / sp) * a).normalized()
            }
            chaseF = f
            chaseSpeed = (sp / (cs * 4f)).coerceIn(0f, 1f)
            val zf = (zoom / 0.9f).coerceIn(0.6f, 1.7f)
            val back = cs * 1.9f * zf + cs * 0.8f * chaseSpeed
            val h = cs * 1.8f * zf
            val eyeL = mb.position + n * h - f * back
            val tgtL = mb.position + f * (cs * 1.15f) - n * (cs * 0.05f)
            if (!chaseInit) { eyeS = eyeL; tgtS = tgtL; upS = n; chaseInit = true }
            else if (dt > 0f) {
                val a = 1f - exp(-9f * dt)
                eyeS += (eyeL - eyeS) * a
                tgtS += (tgtL - tgtS) * a
                upS = (upS + (n - upS) * (1f - exp(-6f * dt))).normalized()
            }
            val k = ease01(chaseBlend)
            eyeV = Vec3.lerp(eyeV, orient.rotate(eyeS), k)
            tgt = Vec3.lerp(tgt, orient.rotate(tgtS), k)
            up = Vec3.lerp(up, orient.rotate(upS), k).normalized()
            fov = 38f + (72f + 12f * chaseSpeed - 38f) * k
            near = 0.1f + (0.03f - 0.1f) * k
        } else chaseInit = false
        eye = eyeV; fovNow = fov; nearNow = near
        tanHalf = tan(Math.toRadians(fov / 2.0)).toFloat()
        camFwd = (tgt - eyeV).normalized()
        camRight = (camFwd cross up).normalized()
        camUp = camRight cross camFwd
        Matrix.perspectiveM(proj, 0, fov, aspect, near, 40f)
        Matrix.setLookAtM(view, 0, eyeV.x, eyeV.y, eyeV.z, tgt.x, tgt.y, tgt.z, camUp.x, camUp.y, camUp.z)
        Matrix.multiplyMM(vp, 0, proj, 0, view, 0)
        p00 = proj[0]; p11 = proj[5]
    }

    // ---- camera / chase controls (any thread) ---------------------------------------------
    fun setCameraMode(mode: Int) {
        cameraMode = mode.coerceIn(0, 2)
        followOn = cameraMode == 1
        if (cameraMode == 2) chaseInit = false
    }

    /** Chase-mode steering: [ix] right, [iy] forward, each -1..1, relative to where the camera is looking. */
    fun setChaseInput(ix: Float, iy: Float, active: Boolean) { chaseIx = ix; chaseIy = iy; joyActive = active }

    /** Two-finger twist in chase mode swings the camera round the marble. */
    fun chaseYawBy(angle: Float) { pendingYaw += angle }

    // ---- game control (GL thread) ---------------------------------------------------------
    fun newLabyrinth(k: ShapeKind, forcedTerrain: Terrain? = null, plain: Boolean = false) {
        kind = k
        val seed = rnd.nextLong()
        val m = Maze(k.createMesh(), seed)
        maze = m
        marble = Marble(m)
        pal = Palette.random(rnd, pal)
        val tr = rnd.nextFloat()
        terrain = forcedTerrain ?: (if (tr < 0.18f) Terrain.SAND else if (tr < 0.34f) Terrain.MUD else Terrain.NONE)
        pal = when (terrain) {
            Terrain.SAND -> pal.withFloor(Vec3(0.84f, 0.72f, 0.50f), Mat.SAND, 0.96f)
            Terrain.MUD -> pal.withFloor(Vec3(0.30f, 0.20f, 0.13f), Mat.MUD, 0.4f)
            else -> pal
        }
        dirt = 0f
        trail.clear()
        val b = SceneBuilder(m, pal)
        builder = b
        floorMesh!!.let { val mb = b.floor(); it.upload(mb.vertices(), mb.indices()) }
        wallMesh!!.let { val mb = b.walls(); it.upload(mb.vertices(), mb.indices()) }
        var wantDoors = rnd.nextFloat() < 0.5f
        var wantHoles = rnd.nextFloat() < 0.5f
        useClouds = rnd.nextFloat() < 0.45f
        val wantRain = !plain && rnd.nextFloat() < 0.35f
        if (plain) { wantDoors = false; wantHoles = false; useClouds = false }
        cloudDensity = 0.3f + 0.7f * rnd.nextFloat()                  // from a few wisps to full overcast
        if (wantRain && rnd.nextFloat() < 0.75f) useClouds = true
        if (!plain && !wantDoors && !wantHoles && !useClouds && !wantRain && terrain == Terrain.NONE) when (rnd.nextInt(4)) { 0 -> wantDoors = true; 1 -> wantHoles = true; 2 -> useClouds = true; else -> { } }
        val rainNow = wantRain || (!plain && !wantDoors && !wantHoles && !useClouds && terrain == Terrain.NONE)
        val hz = Hazards(m, rnd, wantDoors, wantHoles)
        rain = if (rainNow) Rain(m, rnd) else null
        marble!!.rain = rain
        marble!!.terrain = terrain
        rippleStart.fill(-100f); rainWet = 0f
        rain?.let { rn ->
            val mbp = b.puddles(rn.cells, rn.maxRadius * 0.9f)
            puddleMesh!!.upload(mbp.vertices(), mbp.indices())
            if (useClouds) cloudDensity = max(cloudDensity, 0.7f)
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
        val wa = rnd.nextFloat() * 6.2831853f
        val ws = 0.14f + 0.16f * rnd.nextFloat()
        windX = kotlin.math.cos(wa) * ws; windY = kotlin.math.sin(wa) * ws * 0.5f
        fallTimer = 0f
        hintLeft = 0f; hintCool = 0f
        goalBursted = false
        host.onFinish(pal.name + listOfNotNull(terrain.label.ifEmpty { null }, if (hz.hasDoors) "Doors" else null, if (hz.hasHoles) "Holes" else null, if (useClouds) "Clouds" else null, if (rain != null) "Rain" else null).joinToString("") { " · $it" }, pal.accent)
        threadCell = -1
        rollQ = Quat.IDENTITY
        initChase()
        growT = 0f
        phase = Phase.GROW
        audio.levelGrow()
        playSeconds = 0f
        lastHud = -1
        updateCamera()
    }

    /** Request a fresh labyrinth; walls sink first. */
    fun requestNew(k: ShapeKind? = null, terrain: Terrain? = null, plain: Boolean = false) {
        nextKind = k ?: ShapeKind.values().filter { it != kind }.let { it[rnd.nextInt(it.size)] }
        pendingTerrain = terrain; pendingPlain = plain
        if (phase != Phase.SINK) { phase = Phase.SINK; audio.levelSink() }
    }

    /** Begin a real game. null shape / terrain mean "random". */
    fun startGame(shape: ShapeKind?, terrain: Terrain?, plain: Boolean) {
        menuMode = false; paused = false
        requestNew(shape, terrain, plain)
    }

    /** Menu backdrop: build the chosen level right away so the player can see what they picked. */
    fun previewLevel(shape: ShapeKind?, terrain: Terrain?, plain: Boolean) {
        menuMode = true; paused = false
        newLabyrinth(shape ?: ShapeKind.values()[rnd.nextInt(ShapeKind.values().size)], terrain, plain)
    }

    fun resetMarble() { marble?.reset(); rollQ = Quat.IDENTITY; threadCell = -1; initChase() }

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
        val scale = 0.0075f * zoom * touchSens
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
        computeCamera(dt)
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

        if (cameraMode == 2 && phase != Phase.MENU) {                  // chase view works in the piece's own frame
            val a = 1f - exp(-6f * dt)
            val q = orient
            val dotq = q.w
            val tx = if (dotq < 0f) -1f else 1f
            orient = Quat(q.x * (1f - a), q.y * (1f - a), q.z * (1f - a), q.w * (1f - a) + tx * a).normalized()
            angVel = Vec3.ZERO
        }
        // orbit follow: smoothly turn the piece so the marble stays centred and facing us
        val followTarget = if (followOn) 0.8f else 1f
        if (kotlin.math.abs(zoomFollow - followTarget) > 0.002f) {
            zoomFollow += (followTarget - zoomFollow) * min(1f, dt * 2.5f)
            updateCamera()
        }
        if (followOn && !dragging && phase == Phase.PLAY && !paused && time - lastTouchAt > 1.0f) {
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
                if (growT >= 1.35f) { growT = 1.35f; phase = if (menuMode) Phase.MENU else Phase.PLAY }
            }
            Phase.MENU -> {
                if (!dragging) orient = (Quat.fromAxisAngle(Vec3(0f, 1f, 0.35f), 0.22f * dt) * orient).normalized()
            }
            Phase.PLAY -> if (paused) { audio.roll(0f, 0) } else {
                playSeconds += dt
                val worldG = if (tiltMode) deviceGravity else Vec3(0f, -1f, 0f)
                val gLocal = if (cameraMode == 2) chaseGravity(mb) else orient.inverseRotate(worldG.normalized()) * GRAVITY
                if (fallTimer > 0f) {
                    // marble is dropping into a hole; physics paused
                    fallTimer -= dt
                    if (fallTimer <= 0f) {
                        mb.reset(); rollQ = Quat.IDENTITY; threadCell = -1; initChase()
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
                    audio.roll((mb.velocity.length() / (m.cellSize * 4f)).coerceIn(0f, 1f),
                        when { terrain == Terrain.SAND -> 2; terrain == Terrain.MUD -> 3; pal.floorMat == Mat.WOOD -> 1; else -> 0 })
                    if (terrain != Terrain.NONE) layTrail(mb, m, dt)
                    if (mb.lastImpact > 0.3f && time - lastSparkAt > 0.05f) {
                        lastSparkAt = time
                        audio.impact(min(mb.lastImpact / 3f, 1f), panOf(mb.impactPos))
                        if (mb.lastImpact > 0.9f) dust.puff(mb.impactPos, mb.impactDir, 3, 0.05f, Vec3(0.78f, 0.78f, 0.8f))
                        // sparks only for a really hard knock
                        if (mb.lastImpact > HARD_HIT) sparks.impact(mb.impactPos, mb.impactDir, ((mb.lastImpact - HARD_HIT) / 2.5f + 0.25f).coerceIn(0.25f, 1f), pal.wall * 1.2f)
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
                if (growT <= 0f) { newLabyrinth(nextKind ?: pickNext(), pendingTerrain, pendingPlain); nextKind = null; pendingTerrain = null; pendingPlain = false }
            }
        }

        updateRain(dt)
        if (phase != Phase.PLAY || fallTimer > 0f) audio.roll(0f, 0)
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

    /** Steering force for chase mode: joystick (or tilt) in the camera's frame, laid along the surface. */
    private fun chaseGravity(mb: Marble): Vec3 {
        var ix = chaseIx; var iy = chaseIy
        if (!joyActive && tiltMode) {
            val d = deviceGravity - Vec3(0f, -1f, 0f)
            ix = (d.x * 1.4f * gyroSens).coerceIn(-1f, 1f); iy = (d.y * 1.4f * gyroSens).coerceIn(-1f, 1f)
        }
        val len = kotlin.math.sqrt(ix * ix + iy * iy)
        if (len > 1f) { ix /= len; iy /= len }
        val n = mb.surface.normal(mb.position)
        var f = chaseF - n * (chaseF dot n)
        f = if (f.lengthSq() < 1e-8f) Vec3(1f, 0f, 0f) - n * n.x else f
        f = f.normalized()
        val r = f cross n
        return (r * ix + f * iy) * GRAVITY
    }

    private fun panOf(local: Vec3): Float {
        val rel = orient.rotate(local) - eye
        val scale = 1.2f + (0.45f - 1.2f) * ease01(chaseBlend)
        return ((rel dot camRight) / scale).coerceIn(-1f, 1f)
    }

    /** Loudness of something at [local] as heard from the marble: near is loud, the far side of the piece is muffled. */
    private fun proximity(local: Vec3, from: Vec3): Float {
        val d = (local - from).length()
        val back = if (chaseBlend < 0.5f && orient.rotate(local).z < -0.1f) 0.5f else 1f
        return (kotlin.math.exp(-d / 0.55f) * 1.1f * back).coerceIn(0f, 1f)
    }

    /** Footprints, kicked-up dust or mud, and the dirt that builds up on the marble. */
    private fun layTrail(mb: Marble, m: Maze, dt: Float) {
        val n = mb.surface.normal(mb.position)
        val under = mb.position - n * mb.radius
        val size = mb.radius * 1.15f
        val last = trail.last
        if (last == null || (under - last).length() > size * 0.45f) trail.add(under, n, size, time)
        val spd = mb.velocity.length() / (m.cellSize * 4f)
        dirt = (dirt + spd * dt * (if (terrain == Terrain.MUD) 0.35f else 0.18f)).coerceAtMost(1f)
        if (spd > 0.15f && time - dustAt > 0.05f) {
            dustAt = time
            if (terrain == Terrain.SAND) dust.puff(under, n, 1, 0.045f, Vec3(0.85f, 0.72f, 0.5f))
            else {
                dust.puff(under, n, 1, 0.03f, Vec3(0.22f, 0.14f, 0.09f))
                if (spd > 0.4f) sparks.emit(under, (n + Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f)).normalized() * (0.25f * spd), 0.3f, Vec3(0.3f, 0.2f, 0.12f), 0.01f)
            }
        }
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
        if (terrain == Terrain.NONE) dirt = max(0f, dirt - dt * 0.05f)
        if (rn == null) { rainWet = max(0f, rainWet - dt * 0.2f); audio.rain(0f); return }
        val target = if (phase == Phase.SINK) 0f else 1f
        rn.intensity += (target - rn.intensity) * min(1f, dt * 0.45f)
        for (i in rn.cells.indices) puddleUp[i] = orient.rotate(mb.surface.normal(m.centers[rn.cells[i]])).y
        rn.update(dt, rn.intensity > 0.05f, puddleUp)
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

        // drops landing on the visible side of the maze: tiny splashes
        if (rn.intensity > 0.15f && phase != Phase.SINK) {
            splashAcc += dt * 55f * rn.intensity
            var guard = 0
            while (splashAcc >= 1f && guard++ < 12) {
                splashAcc -= 1f
                val lp = Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f)
                val chase = chaseBlend > 0.5f
                val q = if (chase) mb.surface.project(mb.position + lp * (m.cellSize * 5f)) else mb.surface.project(lp.normalized() * 1.2f)
                val nl = mb.surface.normal(q)
                if (chase || orient.rotate(nl).z > 0.15f) {
                    sparks.emit(q + nl * 0.003f, nl * (0.12f + 0.1f * rnd.nextFloat()) + Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f) * 0.1f,
                        0.16f, Vec3(0.65f, 0.78f, 0.95f), 0.0055f)
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
        drawTrail(vpm, cam)
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

        renderWorld(vp, eye, sm)

        // ---- marble -----------------------------------------------------------------------
        GLES30.glUseProgram(marbleProg)
        GLES30.glUniformMatrix4fv(loc(marbleProg, "uVP"), 1, false, vp, 0)
        GLES30.glUniformMatrix4fv(loc(marbleProg, "uModel"), 1, false, model, 0)
        GLES30.glUniform3f(loc(marbleProg, "uCam"), eye.x, eye.y, eye.z)
        GLES30.glUniform3f(loc(marbleProg, "uLightDir"), lightDir.x, lightDir.y, lightDir.z)
        var drawPos = mb.position
        var drawR = mb.radius
        if (fallTimer > 0f) {
            val k = 1f - fallTimer / 0.55f
            val e = k * k
            drawPos = Vec3.lerp(fallFrom, fallTo, e)
            drawR = mb.radius * (1f - 0.85f * e)
        }
        if (terrain != Terrain.NONE) drawPos -= mb.surface.normal(mb.position) * (mb.radius * (if (terrain == Terrain.MUD) 0.16f else 0.07f))
        GLES30.glUniform1f(loc(marbleProg, "uDirt"), if (terrain == Terrain.NONE) dirt else dirt)
        if (terrain == Terrain.MUD) GLES30.glUniform3f(loc(marbleProg, "uDirtCol"), 0.14f, 0.08f, 0.045f) else GLES30.glUniform3f(loc(marbleProg, "uDirtCol"), 0.62f, 0.46f, 0.26f)
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
        GLES30.glUniform1f(loc(sparkProg, "uScale"), rh / (2f * tanHalf))
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE)
        GLES30.glDepthMask(false)
        sparks.draw(sparkProg, 1f)
        GLES30.glDepthMask(true)

        // soft dust and puffs blend normally
        GLES30.glUseProgram(smokeProg)
        GLES30.glUniformMatrix4fv(loc(smokeProg, "uVP"), 1, false, vp, 0)
        GLES30.glUniformMatrix4fv(loc(smokeProg, "uModel"), 1, false, model, 0)
        GLES30.glUniform1f(loc(smokeProg, "uScale"), rh / (2f * tanHalf))
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        dust.draw(smokeProg, 1f)
        val rnI = rain?.intensity ?: 0f
        if (rnI > 0.02f) {
            val aspect = width.toFloat() / height
            val k = ease01(chaseBlend)
            GLES30.glUseProgram(rainProg)
            GLES30.glUniformMatrix4fv(loc(rainProg, "uVP"), 1, false, vp, 0)
            GLES30.glUniform1f(loc(rainProg, "uTime"), time)
            GLES30.glUniform1f(loc(rainProg, "uIntensity"), rnI)
            GLES30.glUniform2f(loc(rainProg, "uViewport"), rw.toFloat(), rh.toFloat())
            GLES30.glUniform1f(loc(rainProg, "uWidthPx"), 2.2f * rh / 1000f * 2f)
            GLES30.glUniform3f(loc(rainProg, "uCamPos"), eye.x, eye.y, eye.z)
            GLES30.glUniform3f(loc(rainProg, "uRight"), camRight.x, camRight.y, camRight.z)
            GLES30.glUniform3f(loc(rainProg, "uUp"), camUp.x, camUp.y, camUp.z)
            GLES30.glUniform3f(loc(rainProg, "uFwd"), camFwd.x, camFwd.y, camFwd.z)
            GLES30.glUniform1f(loc(rainProg, "uDepth0"), 0.9f + (0.10f - 0.9f) * k)
            GLES30.glUniform1f(loc(rainProg, "uSpan"), (camDist + 1.1f) + (0.9f - (camDist + 1.1f)) * k)
            GLES30.glUniform1f(loc(rainProg, "uTravel"), 1.9f + (0.32f - 1.9f) * k)
            GLES30.glUniform1f(loc(rainProg, "uLenScale"), 1f + (0.16f - 1f) * k)
            GLES30.glUniform1f(loc(rainProg, "uScaleW"), 1f + (0.1f - 1f) * k)
            GLES30.glUniform2f(loc(rainProg, "uTan"), tanHalf * aspect, tanHalf)
            GLES30.glBindVertexArray(rainVao[0])
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 6, RAIN_DROPS)
            GLES30.glBindVertexArray(0)
        }
        GLES30.glDepthMask(true)

        GLES30.glDisable(GLES30.GL_BLEND)
        if (postOn) {
            pp.near = nearNow; pp.far = 40f; pp.p00 = p00; pp.p11 = p11
            val kk = ease01(chaseBlend)
            val mbw = marble?.let { orient.rotate(it.position) }
            val chaseFocus = if (mbw != null) (eye - mbw).length() else camDist
            pp.focus = camDist + (chaseFocus - camDist) * kk
            pp.focusRange = kind.outerRadius * 1.1f + (0.9f - kind.outerRadius * 1.1f) * kk
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

    private fun drawTrail(vpm: FloatArray, cam: Vec3) {
        if (terrain == Terrain.NONE || trail.count == 0) return
        GLES30.glUseProgram(trailProg)
        GLES30.glUniformMatrix4fv(loc(trailProg, "uVP"), 1, false, vpm, 0)
        GLES30.glUniformMatrix4fv(loc(trailProg, "uModel"), 1, false, model, 0)
        GLES30.glUniform1f(loc(trailProg, "uTime"), time)
        GLES30.glUniform1f(loc(trailProg, "uKind"), if (terrain == Terrain.MUD) 1f else 0f)
        GLES30.glUniform3f(loc(trailProg, "uCam"), cam.x, cam.y, cam.z)
        GLES30.glUniform3f(loc(trailProg, "uLightDir"), lightDir.x, lightDir.y, lightDir.z)
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-1.2f, -1.2f)
        GLES30.glDepthMask(false)
        trail.draw(trailProg, loc(trailProg, "uPass"))
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(sceneProg)
    }

    private fun drawPuddles(vpm: FloatArray, cam: Vec3) {
        val rn = rain ?: return
        if (rn.cells.isEmpty() || rn.level.maxOrNull()?.let { it < 0.01f } != false) return
        GLES30.glUseProgram(puddleProg)
        GLES30.glUniformMatrix4fv(loc(puddleProg, "uVP"), 1, false, vpm, 0)
        GLES30.glUniformMatrix4fv(loc(puddleProg, "uModel"), 1, false, model, 0)
        GLES30.glUniform1f(loc(puddleProg, "uGrowT"), growT)
        GLES30.glUniform1f(loc(puddleProg, "uTime"), time)
        GLES30.glUniform1f(loc(puddleProg, "uRain"), rain?.intensity ?: 0f)
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

    companion object { const val HARD_HIT = 1.7f; const val RAIN_DROPS = 900; const val GRAVITY = 3.2f; const val HINT_SECONDS = 6f; const val HINT_COOLDOWN = 20f }
}
