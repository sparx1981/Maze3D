package com.maze3d.gl

import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import com.maze3d.audio.Audio
import com.maze3d.audio.NoAudio
import com.maze3d.core.Hazards
import com.maze3d.core.Medium
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
        fun onModeSolved(mode: Int, seconds: Float, difficulty: com.maze3d.core.Difficulty, seed: Long)
        fun onHud(shape: String, seconds: Int, solved: Boolean, percent: Int, stray: Int)
        fun onImpact(strength: Float)
        fun onFinish(name: String, accent: Vec3)
        fun onToast(message: String)
        fun onReady()
        /** Lifetime statistics: [key] such as "distance", "resets", "falls", "started:<surface>". */
        fun onStat(key: String, amount: Float)
        /** A maze was completed: [surfaceKey] is the surface it was played on. */
        fun onSolved(surfaceKey: String, shape: ShapeKind, difficulty: com.maze3d.core.Difficulty, seconds: Float, ghost: com.maze3d.core.Ghost, ghostSeconds: Float)
        /** [cooldown] seconds until the next hint (0 = ready); [active] while the thread is showing. */
        fun onHint(cooldown: Int, active: Boolean)
        val audio: Audio
    }

    private enum class Phase { GROW, PLAY, SOLVED, SINK, MENU }

    private class Door(val mid: Vec3, val normal: Vec3, var closed: Boolean)

    // ---- inputs (written from the UI thread) --------------------------------------------
    /** 0 = joystick, 1 = tilt (gyro only), 2 = gravity (turn the piece; follow camera only). */
    @Volatile var controlMode = 0
    val tiltMode get() = controlMode == 1
    @Volatile var ballScreenX = 0.5f
    @Volatile var ballScreenY = 0.5f
    @Volatile var ballOnScreen = false
    @Volatile var hintActive = false
    /** True while the menu is open mid-game: physics, timers and hazards stand still. */
    @Volatile var paused = false
    /** True before a game has started: the level just turns slowly behind the menu. */
    @Volatile var menuMode = true
    private var pendingTerrain: Terrain? = null
    private var pendingTheme: Int? = null
    private var pendingMedium: Medium? = null
    /** Simulation detail for sand and mud: 0 basic stamped trails, 1 detailed height field, 2 ultra (height field + particles). */
    @Volatile var simQuality = 1
    @Volatile var difficulty = com.maze3d.core.Difficulty.MEDIUM
    private var medium = Medium.NONE
    private var pendingPlain = false
    @Volatile var followOn = false
    /** User settings (menu): 0.4 .. 2.5, 1 = default. */
    @Volatile var touchSens = 0.5f
    @Volatile var gyroSens = 0.5f
    /** 0 = orbit (turn the piece by hand), 1 = orbit that follows the marble, 2 = third-person chase camera. */
    @Volatile var cameraMode = 2
        private set
    @Volatile private var chaseIx = 0f
    @Volatile private var chaseIy = 0f
    @Volatile private var joyActive = false
    @Volatile private var pendingYaw = 0f
    @Volatile var deviceGravity = Vec3(0f, -1f, 0f)
    /** Boosted tilt about the screen's horizontal axis in radians: positive leans forward (ball goes ahead), negative leans back (ball reverses). */
    @Volatile var tiltPitch = 0f

    // ---- GL resources ---------------------------------------------------------------------
    private var sceneProg = 0
    private var shadowSceneProg = 0
    private var holeProg = 0
    private var lidProg = 0
    private var lidMesh: GlMesh? = null
    private val holeUniform = FloatArray(48 * 4)
    private var holeCount = 0
    private var smokeProg = 0
    private var cube: ReflectionCube? = null
    private var cubeFace = 0
    private var cubeFresh = 6
    private val dust = Sparks()
    private var trailAt = 0f
    private var puddleProg = 0
    @Volatile var progress: com.maze3d.core.Progress? = null
    /** -1 = the ball that matches the level, 0.. = a chosen (unlocked) skin. */
    @Volatile var ballChoice = -1
    private var surfaceKey = "classic"
    private var checkpoints = IntArray(0)
    private var cpReached = -1
    private var solvedReported = false
    @Volatile private var padLookX = 0f
    private val lampUniform = FloatArray(48)
    private var lamps: List<Vec3> = emptyList()
    private var sandField: com.maze3d.core.SandField? = null
    private var sandTex = 0
    private var sandStampAt: Vec3? = null
    private var sandBuf: java.nio.FloatBuffer? = null
    private var pctCell = -1
    private var pct = 0
    private var pctBest = 0
    private var statDist = 0f
    private var statTime = 0f
    private var mediumProg = 0
    private var mediumMesh: GlMesh? = null
    private var mediumGeo: MediumMesh? = null
    private var field: com.maze3d.core.MediumField? = null
    private var mediumAcc = 0f
    private var waves: com.maze3d.core.WaveField? = null
    private var waveTex = 0
    private var waveBuf: java.nio.FloatBuffer? = null
    private var foamTex = 0
    private var waveDropAcc = 0f
    private var poison = 0f
    private var trailProg = 0
    private val trail = TrailBuffer()
    private val glowTrail = TrailBuffer(900)
    private val haloBuf = TrailBuffer(400)
    private var haloProg = 0
    private var myceliumMesh: GlMesh? = null
    private var lampIdxList: List<Int> = emptyList()
    private var lampColList: List<Vec3> = emptyList()
    private var lampReachList: List<Float> = emptyList()
    private val lampColUniform = FloatArray(36)
    private val lampIdxUniform = FloatArray(12)
    private var sporeAcc = 0f
    private val leaves = TrailBuffer(4000)
    private var glowProg = 0
    private var leafProg = 0
    private var glowStampAt: Vec3? = null
    private var leafAcc = 0f
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
    private var bridgePitMesh: GlMesh? = null
    private var bridgeMesh: GlMesh? = null
    private var swingPitMesh: GlMesh? = null
    private var swingGantryMesh: GlMesh? = null
    private var swingSeatMesh: GlMesh? = null
    private var swingFrames = emptyList<FloatArray>()
    private var swingDirT = emptyList<Vec3>()
    private var swingLat = emptyList<Vec3>()
    private var bridgeBoardMesh: GlMesh? = null
    private var bridgeDirT = emptyList<Vec3>()
    private var gantryMesh: GlMesh? = null
    private var hammerMesh: GlMesh? = null
    private var bridgeFrames = emptyList<FloatArray>()
    private var bridgeLat = emptyList<Vec3>()
    private var hammerFrames = emptyList<FloatArray>()
    private var hammerAxis = emptyList<Vec3>()
    private var hammerCooldown = 0f
    private var bridgeHalfWidth = 0.36f
    private val sparks = Sparks()
    private var hazards: Hazards? = null
    private var fxFrames = emptyList<FloatArray>()
    private var fxDirT = emptyList<Vec3>()
    private var fxLat = emptyList<Vec3>()
    private val fxMeshes = arrayOfNulls<GlMesh>(11)   // 0 pad,1 oil,2 belt,3 chevron,4 crusher frame,5 ram,6 sweeper,7 seesaw base,8 plank
    private var padKick = FloatArray(0)
    private var fxCooldown = 0f
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
    @Volatile var profile = QualityProfile.HIGH
    private var appliedProfile: QualityProfile? = null
    private var appliedQuality = -1
    private val lightDir = Vec3(0.45f, 0.82f, 0.55f).normalized()
    private val lightVP = FloatArray(16)
    private var marbleProg = 0
    private var bgProg = 0
    private var emptyVao = IntArray(1)
    private var sphere: GlMesh? = null
    private var floorMesh: GlMesh? = null
    private var wallMesh: GlMesh? = null
    private var propMesh: GlMesh? = null
    private var markerMesh: GlMesh? = null
    private var threadMesh: GlMesh? = null

    // ---- game state -----------------------------------------------------------------------
    private var rnd = Random()
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
    private var zoomV = 0.67f
    private var width = 1
    private var height = 1
    private val proj = FloatArray(16)
    private val view = FloatArray(16)
    private val vp = FloatArray(16)
    private val model = FloatArray(16)
    private var camDist = 3.2f
    // chase camera state (all in the piece's local space until the final transform)
    private var chaseBlend = 0f
    private val chaseCam = com.maze3d.core.ChaseCam()
    private var chaseF: Vec3 get() = chaseCam.f; set(v) { chaseCam.f = v }
    private var chaseInit: Boolean get() = chaseCam.init; set(v) { chaseCam.init = v }
    private val eyeS get() = chaseCam.eyeS
    private val tgtS get() = chaseCam.tgtS
    private val upS get() = chaseCam.upS
    private val chaseSpeed get() = chaseCam.speed
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
        holeProg = Shaders.program(Shaders.SCENE_VS, Shaders.PIT_FS)
        lidProg = Shaders.program(Shaders.LID_VS, Shaders.LID_FS)
        sparkProg = Shaders.program(Shaders.SPARK_VS, Shaders.SPARK_FS)
        post.init()
        sparks.init()
        dust.init()
        smokeProg = Shaders.program(Shaders.SPARK_VS, PostShaders.SMOKE_FS)
        puddleProg = Shaders.program(Shaders.SCENE_VS, Shaders.PUDDLE_FS)
        trailProg = Shaders.program(Shaders.TRAIL_VS, Shaders.TRAIL_FS)
        trail.init()
        glowTrail.init(); leaves.init(); haloBuf.init()
        haloProg = Shaders.program(Shaders.HALO_VS, Shaders.HALO_FS)
        myceliumMesh = GlMesh(GlMesh.SCENE)
        glowProg = Shaders.program(Shaders.TRAIL_VS, Shaders.GLOW_FS)
        leafProg = Shaders.program(Shaders.LEAF_VS, Shaders.LEAF_FS)
        rainProg = Shaders.program(Shaders.RAIN_VS, Shaders.RAIN_FS)
        puddleMesh = GlMesh(GlMesh.SCENE)
        mediumProg = Shaders.program(Shaders.SCENE_VS, Shaders.MEDIUM_FS)
        mediumMesh = GlMesh(GlMesh.SCENE_TILE)
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
        bridgePitMesh = GlMesh(GlMesh.SCENE)
        swingPitMesh = GlMesh(GlMesh.SCENE); swingGantryMesh = GlMesh(GlMesh.SCENE); swingSeatMesh = GlMesh(GlMesh.SCENE)
        bridgeMesh = GlMesh(GlMesh.SCENE); bridgeBoardMesh = GlMesh(GlMesh.SCENE); gantryMesh = GlMesh(GlMesh.SCENE); hammerMesh = GlMesh(GlMesh.SCENE)
        lidMesh = GlMesh(GlMesh.SCENE)
        animMeshes.clear()
        gateMesh = GlMesh(GlMesh.SCENE); for (i in keyMeshes.indices) keyMeshes[i] = GlMesh(GlMesh.SCENE)
        for (i in fxMeshes.indices) fxMeshes[i] = GlMesh(GlMesh.SCENE)
        locs.clear(); shadow = null; appliedQuality = -1; postOn = false
        ShadowMap.lightMatrix(lightDir, 1.75f, lightVP)
        GLES30.glGenVertexArrays(1, emptyVao, 0)
        sphere = GlMesh.unitSphere()
        floorMesh = GlMesh(GlMesh.SCENE_TILE)
        wallMesh = GlMesh(GlMesh.SCENE)
        propMesh = GlMesh(GlMesh.SCENE)
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
    /** Follow camera: from the whole piece in view (slider low) down to a few cells from the ball (slider high). */
    private fun followDist(): Float {
        val mb = marble ?: return camDist
        val m = maze ?: return camDist
        val ballZ = orient.rotate(mb.position).z
        val aspect = width.toFloat() / height
        val tanMin = min(tan(Math.toRadians(19.0)).toFloat(), tan(Math.toRadians(19.0)).toFloat() * aspect)
        val r = kind.outerRadius * 1.12f
        val far = (r / tanMin + r) * 1.6f
        val close = ballZ + m.cellSize * 2.6f                 // the camera ends up a few cells above the ball itself
        val t = zoomV * zoomV * (3f - 2f * zoomV)
        return far + (close - far) * t
    }

    private fun computeCamera(dt: Float) {
        val aspect = width.toFloat() / height
        if (cameraMode == 1 && marble != null && phase != Phase.MENU) camDist = followDist()
        val wantChase = cameraMode == 2 && phase != Phase.MENU
        chaseBlend += ((if (wantChase) 1f else 0f) - chaseBlend) * min(1f, dt * 5f)
        if (!wantChase && chaseBlend < 0.003f) chaseBlend = 0f
        var eyeV = Vec3(0f, 0f, camDist)
        var tgt = Vec3.ZERO
        if (cameraMode == 1 && marble != null && phase != Phase.MENU) {
            // follow view: zooming in closes on the ball, not on the middle of the piece (the view axis slides over to the ball)
            val w = orient.rotate(marble!!.position)
            val zt = zoomV * zoomV * (3f - 2f * zoomV)
            eyeV = Vec3(w.x * zt, w.y * zt, camDist)
            tgt = Vec3(w.x * zt, w.y * zt, 0f)
        }
        var up = Vec3(0f, 1f, 0f)
        var fov = 38f
        var near = 0.1f
        val mb = marble
        val m = maze
        if (cameraMode == 1 && m != null && phase != Phase.MENU) near = min(0.1f, m.cellSize * 0.9f)
        if (chaseBlend > 0.002f && mb != null && m != null) {
            val cs = m.cellSize
            val n = mb.surface.normal(mb.position)
            val yaw = pendingYaw; pendingYaw = 0f
            chaseCam.update(mb.position, n, mb.velocity, cs, zoomV, dt, yaw, aspect)
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
        marble?.let { mbc ->
            val w4 = orient.rotate(mbc.position)
            val inp = floatArrayOf(w4.x, w4.y, w4.z, 1f); val out = FloatArray(4)
            Matrix.multiplyMV(out, 0, vp, 0, inp, 0)
            if (out[3] > 1e-4f) { ballScreenX = out[0] / out[3] * 0.5f + 0.5f; ballScreenY = 0.5f - out[1] / out[3] * 0.5f; ballOnScreen = true } else ballOnScreen = false
        }
    }

    // ---- camera / chase controls (any thread) ---------------------------------------------
    fun setCameraMode(mode: Int) {
        cameraMode = mode.coerceIn(1, 2)
        followOn = cameraMode == 1
        if (cameraMode == 2) chaseInit = false
    }

    /** Chase-mode steering: [ix] right, [iy] forward, each -1..1, relative to where the camera is looking. */
    @Volatile private var padX = 0f
    @Volatile private var padY = 0f
    @Volatile private var padOn = false

    /** Game-pad stick: steers in chase view, turns the piece like a drag in the other views. */
    fun padInput(x: Float, y: Float, on: Boolean) {
        padX = x; padY = y; padOn = on
        if (cameraMode == 2 || controlMode != 2) { chaseIx = x; chaseIy = -y; joyActive = on }
    }

    fun padLook(x: Float) { padLookX = x }

    fun nudgeZoom(d: Float) { zoomV = (zoomV + d).coerceIn(0f, 1f); zoom = 1.6f + (0.55f - 1.6f) * zoomV; updateCamera() }

    fun setChaseInput(ix: Float, iy: Float, active: Boolean) { chaseIx = ix; chaseIy = iy; joyActive = active }

    /** Two-finger twist in chase mode swings the camera round the marble. */
    fun chaseYawBy(angle: Float) { pendingYaw += angle }

    // ---- game control (GL thread) ---------------------------------------------------------
    /** 0 = normal play, 1 = Keys & Gates, 2 = the daily maze. */
    @Volatile var gameMode = 0
    @Volatile var forcedSeed: Long? = null
    private var keyGates: com.maze3d.core.KeyGates? = null
    private var gateMesh: GlMesh? = null
    private val keyMeshes = arrayOfNulls<GlMesh>(3)
    private var keyFrames = emptyList<FloatArray>()
    private val keyCols = arrayOf(Vec3(0.95f, 0.2f, 0.2f), Vec3(0.2f, 0.5f, 1f), Vec3(0.2f, 0.85f, 0.35f))
    private val keyNames = arrayOf("Red", "Blue", "Green")

    private fun rebuildGates() {
        val kg = keyGates ?: return
        val b = builder ?: return
        gateMesh?.let { val mb = b.gates(kg.gateEdges, kg.gateOpen, keyCols); if (mb.vertexCount > 0) it.upload(mb.vertices(), mb.indices()) else it.upload(FloatArray(MeshBuilder.STRIDE), IntArray(0)) }
    }

    private class RaceSetup(val ghost: com.maze3d.core.Ghost)
    @Volatile private var raceSetup: RaceSetup? = null
    private var raceGhost: com.maze3d.core.Ghost? = null
    private var lvlSeed = 0L
    private var lvlPalIndex = 0
    private var lvlPlain = false
    private val runSamples = ArrayList<Float>()

    /** Starts a race against a saved run: the same maze again, with the ghost running alongside. */
    fun startRace(g: com.maze3d.core.Ghost) {
        raceSetup = RaceSetup(g); gameMode = 0
        difficulty = g.difficulty
        startGame(g.shape, g.terrain, g.plain, null, g.medium)
    }

    fun newLabyrinth(k: ShapeKind, forcedTerrain: Terrain? = null, plain: Boolean = false, forcedTheme: Int? = null, forcedMedium: Medium? = null) {
        val setup = raceSetup; raceSetup = null
        raceGhost = setup?.ghost
        val seed = setup?.ghost?.seed ?: forcedSeed ?: rnd.nextLong()
        forcedSeed = null
        val saved = rnd
        rnd = Random(seed)
        try { newLabyrinthImpl(k, forcedTerrain, plain, forcedTheme, forcedMedium, seed, setup) } finally { rnd = saved }
    }

    private fun newLabyrinthImpl(k: ShapeKind, forcedTerrain: Terrain?, plain: Boolean, forcedTheme: Int?, forcedMedium: Medium?, seed: Long, setup: RaceSetup?) {
        kind = k
        lvlSeed = seed; lvlPlain = plain; runSamples.clear()
        val keysOn = gameMode == 1 && !menuMode
        val m = Maze(k.createMesh(if (keysOn) minOf(difficulty.scale, 0.8f) else difficulty.scale), seed)
        maze = m
        marble = Marble(m)
        followAim = null; followDirS = null
        val tr = rnd.nextFloat()
        val mr = rnd.nextFloat()
        val pg = progress
        fun open(key: String) = pg == null || pg.surfaceUnlocked(key)
        medium = forcedMedium ?: (if (plain) Medium.NONE else if (mr < 0.10f) Medium.WATER else if (mr < 0.18f) Medium.FOG else if (mr < 0.26f) Medium.GAS else if (mr < 0.33f) Medium.STORM else Medium.NONE)
        if (forcedMedium == null && medium != Medium.NONE && !open(medium.name.lowercase())) medium = Medium.NONE
        terrain = forcedTerrain ?: (if (medium != Medium.NONE) Terrain.NONE else if (tr < 0.14f) Terrain.SAND else if (tr < 0.28f) Terrain.MUD else Terrain.NONE)
        if (forcedTerrain == null && terrain != Terrain.NONE && !open(terrain.name.lowercase())) terrain = Terrain.NONE
        pal = if (setup != null) Palette.all[setup.ghost.palIndex.coerceIn(0, Palette.all.size - 1)] else when {
            terrain != Terrain.NONE -> Palette.random(rnd, pal)                           // sand/mud floors keep a classic wall finish
            forcedTheme == null -> Palette.randomAny(rnd, pal)
            forcedTheme < 0 -> Palette.random(rnd, pal)
            else -> Palette.themes[forcedTheme.coerceIn(0, Palette.themes.size - 1)]
        }
        if (setup == null && forcedTheme == null && pal.themeId >= 0 && !open("t${pal.themeId}")) pal = Palette.random(rnd, pal)
        if (terrain == Terrain.NONE && medium == Medium.NONE && (pal.themeId == 8 || pal.themeId == 7)) terrain = Terrain.SAND     // temple sand / powder snow
        lvlPalIndex = Palette.all.indexOf(pal).coerceAtLeast(0)
        pal = when (terrain) {
            Terrain.SAND -> pal.withFloor(when (pal.themeId) { 8 -> Vec3(0.88f, 0.70f, 0.44f); 7 -> Vec3(0.76f, 0.84f, 0.95f); else -> Vec3(0.84f, 0.72f, 0.50f) }, Mat.SAND, 0.96f)
            Terrain.MUD -> pal.withFloor(Vec3(0.30f, 0.20f, 0.13f), Mat.MUD, 0.4f)
            else -> pal
        }
        dirt = 0f
        trail.clear(); glowTrail.clear(); leaves.clear(); glowStampAt = null; leafAcc = 0f
        val b = SceneBuilder(m, pal)
        b.wallDetail = when { profile.shadows >= 2 -> 1f; profile.shadows >= 1 -> 0.7f; else -> 0.45f }
        builder = b
        floorMesh!!.let { val mb = b.floor(true); it.upload(mb.vertices(), mb.indices()) }
        setupSand(m)
        wallMesh!!.let { val mb = b.walls(); it.upload(mb.vertices(), mb.indices()) }
        b.planProps(kind.outerRadius)
        lamps = b.lamps.toList()
        lampIdxList = b.lampIdx.toList()
        lampColList = b.lampCol.toList(); lampReachList = b.lampReach.toList()
        animInsts = b.anims.toList()
        haloBuf.clear()
        myceliumMesh!!.let { mm ->
            if (pal.themeId == 11) {
                for ((i, lp) in lamps.withIndex()) { val fp = m.mesh.surface.project(lp); haloBuf.add(fp, m.mesh.surface.normal(fp), m.cellSize * 1.45f, lampIdxList.getOrElse(i) { 0 }.toFloat()) }
                val mb = b.mycelium(); if (mb.vertexCount > 0) mm.upload(mb.vertices(), mb.indices()) else mm.upload(FloatArray(MeshBuilder.STRIDE), IntArray(0))
            } else mm.upload(FloatArray(MeshBuilder.STRIDE), IntArray(0))
        }
        propMesh!!.let { val mb = b.props(); if (mb.vertexCount > 0) it.upload(mb.vertices(), mb.indices()) else it.upload(FloatArray(MeshBuilder.STRIDE), IntArray(0)) }
        marble?.setObstacles(b.obstacles.map { Triple(it.cell, it.pos, it.radius) })
        rnd = Random(seed xor 0x5DEECE66DL)                      // hazards come from their own stream so a race rebuilds them exactly
        var wantDoors = rnd.nextFloat() < 0.5f
        var wantHoles = rnd.nextFloat() < 0.5f
        useClouds = false                                   // clouds, rain and distance haze were removed from the game
        val wantRain = medium == Medium.STORM                      // a storm brings driving rain, puddles, lightning and gusts
        if (plain) { wantDoors = false; wantHoles = false; useClouds = false }
        cloudDensity = 0.3f + 0.7f * rnd.nextFloat()                  // from a few wisps to full overcast
        if (wantRain && rnd.nextFloat() < 0.75f) { }
        if (!plain && !wantDoors && !wantHoles && !useClouds && !wantRain && terrain == Terrain.NONE) when (rnd.nextInt(4)) { 0 -> wantDoors = true; 1 -> wantHoles = true; 2 -> if (medium == Medium.NONE) medium = Medium.FOG; else -> { } }   // the old 'clouds' roll now brings real fog
        val rainNow = wantRain
        val hardBoost = if (difficulty.scale >= 1.7f) 0.15f else 0f
        val wantBridges = !plain && rnd.nextFloat() < 0.26f + hardBoost
        val wantHammers = !plain && rnd.nextFloat() < 0.26f + hardBoost
        val wantSwings = !plain && rnd.nextFloat() < 0.22f + hardBoost
        val jelly = pal.themeId == 18 && !plain
        val wantFx = jelly || (!plain && Random(seed xor 0x7A3F1C9L).nextFloat() < 0.45f + hardBoost)
        val hz = Hazards(m, rnd, wantDoors, wantHoles, wantBridges, wantHammers, if (difficulty.scale >= 1.7f) 1.5f else 1f, wantSwings, if (wantFx) Random(seed xor 0x2B7E1516L) else null,
            if (jelly) listOf(Hazards.FX_BUMPER) else null, if (jelly) (m.cellCount / 22).coerceIn(5, 16) else -1)
        rain = if (rainNow) Rain(m, rnd) else null
        marble!!.rain = rain
        marble!!.terrain = terrain
        marble!!.simDetail = simQuality
        poison = 0f
        field = if (medium != Medium.NONE && medium != Medium.STORM) com.maze3d.core.MediumField(m, medium, rnd, k.outerRadius).also { it.warmUp(if (medium == Medium.WATER) 8f else 5f) } else null
        marble!!.field = field
        mediumGeo = field?.let { MediumMesh(m, medium, b.wallHeight, when { profile.shadows >= 2 -> 9; profile.shadows >= 1 -> 6; else -> 4 }) }
        mediumGeo?.let { g -> mediumMesh!!.upload(g.update(field!!.level), g.indices, true) }
        surfaceKey = when { pal.themeId >= 0 -> "t${pal.themeId}"; medium != Medium.NONE -> medium.name.lowercase(); terrain == Terrain.SAND -> "sand"; terrain == Terrain.MUD -> "mud"; else -> "classic" }
        solvedReported = false
        if (!menuMode) host.onStat("started:$surfaceKey", 1f)
        mediumAcc = 0f
        waves = null
        if (medium == Medium.WATER) {
            val wf = com.maze3d.core.WaveField(m, if (simQuality >= 2) 16 else 12)
            waves = wf
            if (foamTex == 0) { val id2 = IntArray(1); GLES30.glGenTextures(1, id2, 0); foamTex = id2[0] }
            if (waveTex == 0) { val id = IntArray(1); GLES30.glGenTextures(1, id, 0); waveTex = id[0] }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, waveTex)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R16F, wf.atlasW, wf.atlasH, 0, GLES30.GL_RED, GLES30.GL_FLOAT, null)
            for ((pn, pv) in listOf(GLES30.GL_TEXTURE_MIN_FILTER to GLES30.GL_LINEAR, GLES30.GL_TEXTURE_MAG_FILTER to GLES30.GL_LINEAR, GLES30.GL_TEXTURE_WRAP_S to GLES30.GL_CLAMP_TO_EDGE, GLES30.GL_TEXTURE_WRAP_T to GLES30.GL_CLAMP_TO_EDGE)) GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, pn, pv)
            val zero = java.nio.ByteBuffer.allocateDirect(wf.atlasW * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
            for (row in 0 until wf.atlasH) { zero.position(0); GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, row, wf.atlasW, 1, GLES30.GL_RED, GLES30.GL_FLOAT, zero) }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, foamTex)
            GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R16F, wf.atlasW, wf.atlasH, 0, GLES30.GL_RED, GLES30.GL_FLOAT, null)
            for ((pn, pv) in listOf(GLES30.GL_TEXTURE_MIN_FILTER to GLES30.GL_LINEAR, GLES30.GL_TEXTURE_MAG_FILTER to GLES30.GL_LINEAR, GLES30.GL_TEXTURE_WRAP_S to GLES30.GL_CLAMP_TO_EDGE, GLES30.GL_TEXTURE_WRAP_T to GLES30.GL_CLAMP_TO_EDGE)) GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, pn, pv)
            for (row in 0 until wf.atlasH) { zero.position(0); GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, row, wf.atlasW, 1, GLES30.GL_RED, GLES30.GL_FLOAT, zero) }
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
            waveBuf = java.nio.ByteBuffer.allocateDirect(wf.tile * wf.tile * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        }
        rippleStart.fill(-100f); rainWet = 0f
        rain?.let { rn ->
            val mbp = b.puddles(rn.cells, rn.maxRadius * 0.9f)
            puddleMesh!!.upload(mbp.vertices(), mbp.indices())
            if (useClouds) cloudDensity = max(cloudDensity, 0.7f)
        }
        hazards = hz
        marble!!.hazards = hz
        keyGates = null; marble!!.gates = null
        keyFrames = emptyList()
        marble!!.studPitch = if (pal.floorMat == Mat.TOY) studPitch() else 0f
        doors = hz.doorEdges.map { e ->
            val mid = m.mesh.surface.project((m.mesh.vertices[m.edgeA[e]] + m.mesh.vertices[m.edgeB[e]]) * 0.5f)
            Door(mid, m.mesh.surface.normal(mid), false)
        }
        doorMesh!!.let { val mb = b.doors(hz.doorEdges, hz.doorPhase); it.upload(mb.vertices(), mb.indices()) }
        holeMesh!!.let { val mb = b.pits(hz.holeCells, hz.holePhase, hz.holeRadius, m.cellSize * 1.0f); it.upload(mb.vertices(), mb.indices()) }
        lidMesh!!.let { val mb = b.lids(hz.holeCells, hz.holePhase, hz.holeRadius); it.upload(mb.vertices(), mb.indices()) }
        // bridges: permanently open pits with a wobbling plank across them
        bridgePitMesh!!.let {
            if (hz.hasBridges) { val mb = b.pits(hz.bridgeCells, FloatArray(hz.bridgeCells.size) { 0.25f }, m.cellSize * 0.5f, m.cellSize * 1.0f); it.upload(mb.vertices(), mb.indices()) }
            else it.upload(FloatArray(MeshBuilder.STRIDE), IntArray(0))
        }
        bridgeHalfWidth = (m.marbleRadius * 1.55f / m.cellSize)
        fun frameFor(cell: Int, dir: Vec3, lift: Float): Pair<FloatArray, Vec3> {
            val c = m.centers[cell]; val n = m.mesh.surface.normal(c)
            val x = (dir - n * (dir dot n)).normalized(); val y = n cross x
            val cs = m.cellSize
            return floatArrayOf(x.x * cs, x.y * cs, x.z * cs, 0f, y.x * cs, y.y * cs, y.z * cs, 0f, n.x * cs, n.y * cs, n.z * cs, 0f,
                c.x + n.x * lift, c.y + n.y * lift, c.z + n.z * lift, 1f) to y
        }
        val bf = hz.bridgeCells.indices.map { frameFor(hz.bridgeCells[it], hz.bridgeDir[it], m.cellSize * 0.004f) }
        bridgeFrames = bf.map { it.first }; bridgeLat = bf.map { it.second }
        bridgeDirT = hz.bridgeCells.indices.map { val c = m.centers[hz.bridgeCells[it]]; val n = m.mesh.surface.normal(c); val d = hz.bridgeDir[it]; (d - n * (d dot n)).normalized() }
        val hf = hz.hammerCells.indices.map { frameFor(hz.hammerCells[it], hz.hammerDir[it], 0f) }
        hammerFrames = hf.map { it.first }
        hammerAxis = hz.hammerCells.indices.map { val c = m.centers[hz.hammerCells[it]]; val n = m.mesh.surface.normal(c); val d = hz.hammerDir[it]; (d - n * (d dot n)).normalized() }
        // swings: three overlapping pits make one wide pit; the seat and its frame are drawn separately
        val swingPits = ArrayList<Vec3>(); val swingPitCells = ArrayList<Int>()
        val sf = hz.swingCells.indices.map { frameFor(hz.swingCells[it], hz.swingDir[it], 0f) }
        swingFrames = sf.map { it.first }; swingLat = sf.map { it.second }
        swingDirT = hz.swingCells.indices.map { val c = m.centers[hz.swingCells[it]]; val n = m.mesh.surface.normal(c); val d = hz.swingDir[it]; (d - n * (d dot n)).normalized() }
        for (i in hz.swingCells.indices) for (o in -2..2) {          // five overlapping cut-outs make one long stadium hole
            val c = m.centers[hz.swingCells[i]]
            swingPits.add(m.mesh.surface.project(c + swingDirT[i] * (o * 0.25f * m.cellSize)))
        }
        swingPitMesh!!.let {
            if (hz.hasSwings) {
                val ctrs = hz.swingCells.map { m.centers[it] }
                val mb = b.pits(hz.swingCells, FloatArray(hz.swingCells.size) { 0.25f }, m.cellSize * 0.5f, m.cellSize * 1.2f, ctrs, swingDirT, m.cellSize * Hazards.SWING_HALFPIT)
                it.upload(mb.vertices(), mb.indices())
            } else it.upload(FloatArray(MeshBuilder.STRIDE), IntArray(0))
        }
        fun upload(mesh: GlMesh?, pm: PropMesh?) {
            val mb = MeshBuilder()
            if (pm != null) {
                for (i in 0 until pm.vertexCount) mb.vertex(Vec3(pm.pos[i * 3], pm.pos[i * 3 + 1], pm.pos[i * 3 + 2]), Vec3(pm.nrm[i * 3], pm.nrm[i * 3 + 1], pm.nrm[i * 3 + 2]), Vec3(pm.col[i * 3], pm.col[i * 3 + 1], pm.col[i * 3 + 2]))
                var q = 0; while (q < pm.idx.size) { mb.tri(pm.idx[q], pm.idx[q + 1], pm.idx[q + 2]); q += 3 }
                mesh!!.upload(mb.vertices(), mb.indices())
            } else mesh!!.upload(FloatArray(MeshBuilder.STRIDE), IntArray(0))
        }
        upload(bridgeMesh, if (hz.hasBridges) HazardModels.bridgeFrame(bridgeHalfWidth) else null)
        upload(bridgeBoardMesh, if (hz.hasBridges) HazardModels.bridgeBoard(bridgeHalfWidth) else null)
        upload(gantryMesh, if (hz.hasHammers) HazardModels.gantry(0.5f) else null)
        upload(hammerMesh, if (hz.hasHammers) HazardModels.hammer() else null)
        upload(swingGantryMesh, if (hz.hasSwings) HazardModels.swingGantry(0.56f) else null)
        upload(swingSeatMesh, if (hz.hasSwings) HazardModels.swingSeat(Hazards.SWING_HALF) else null)
        hammerCooldown = 0f
        if (keysOn) {
            val avoid = HashSet<Int>().apply { hz.holeCells.forEach { add(it) }; hz.bridgeCells.forEach { add(it) }; hz.hammerCells.forEach { add(it) }; hz.fxCell.forEach { add(it) }
                for (sc in hz.swingCells) { add(sc); for (o in m.links[sc]) add(o) }; for (e in hz.doorEdges) { add(m.edgeCellL[e]); add(m.edgeCellR[e]) } }
            val kg = com.maze3d.core.KeyGates(m, Random(seed xor 0x6B657931L), when { difficulty.scale < 0.7f -> 1; difficulty.scale < 1.5f -> 2; else -> 3 }, avoid)
            keyGates = kg; marble!!.gates = kg
            rebuildGates()
            keyFrames = kg.keyCells.map { frameFor(it, Vec3(1f, 0f, 0f), m.cellSize * 0.3f).first }
            for (i in keyMeshes.indices) { val cc = keyCols[i]; upload(keyMeshes[i], HazardModels.keyModel((cc.x * 255).toInt(), (cc.y * 255).toInt(), (cc.z * 255).toInt())) }
        } else gateMesh?.upload(FloatArray(MeshBuilder.STRIDE), IntArray(0))
        run {
            val ff = hz.fxCell.indices.map { frameFor(hz.fxCell[it], hz.fxDir[it], m.cellSize * 0.002f) }
            fxFrames = ff.map { it.first }; fxLat = ff.map { it.second }
            fxDirT = hz.fxCell.indices.map { val c = m.centers[hz.fxCell[it]]; val n = m.mesh.surface.normal(c); val d = hz.fxDir[it]; (d - n * (d dot n)).normalized() }
            padKick = FloatArray(hz.fxCell.size)
            fxCooldown = 0f
            val has = { k: Int -> hz.fxKind.any { it == k } }
            upload(fxMeshes[0], if (has(Hazards.FX_BUMPER)) HazardModels.springPad() else null)
            upload(fxMeshes[1], if (has(Hazards.FX_OIL)) HazardModels.oil() else null)
            upload(fxMeshes[2], if (has(Hazards.FX_CONVEYOR)) HazardModels.belt() else null)
            upload(fxMeshes[3], if (has(Hazards.FX_CONVEYOR)) HazardModels.chevron() else null)
            upload(fxMeshes[4], if (has(Hazards.FX_CRUSHER)) HazardModels.crusherFrame() else null)
            upload(fxMeshes[5], if (has(Hazards.FX_CRUSHER)) HazardModels.crusherRam() else null)
            upload(fxMeshes[6], if (has(Hazards.FX_SWEEPER)) HazardModels.sweeper() else null)
            upload(fxMeshes[7], if (has(Hazards.FX_SEESAW)) HazardModels.seesawBase() else null)
            upload(fxMeshes[8], if (has(Hazards.FX_SEESAW)) HazardModels.seesawPlank() else null)
        }
        holeCount = minOf(hz.holeCells.size, 48)
        holeUniform.fill(0f)
        for (i in 0 until holeCount) {
            val c = m.centers[hz.holeCells[i]]
            holeUniform[i * 4] = c.x; holeUniform[i * 4 + 1] = c.y; holeUniform[i * 4 + 2] = c.z; holeUniform[i * 4 + 3] = hz.holeRadius * 1.03f
        }
        for (p in swingPits) {
            if (holeCount >= 48) break
            holeUniform[holeCount * 4] = p.x; holeUniform[holeCount * 4 + 1] = p.y; holeUniform[holeCount * 4 + 2] = p.z; holeUniform[holeCount * 4 + 3] = m.cellSize * 0.5f * 1.03f
            holeCount++
        }
        for (j in hz.bridgeCells.indices) {
            if (holeCount >= 48) break
            val c = m.centers[hz.bridgeCells[j]]
            holeUniform[holeCount * 4] = c.x; holeUniform[holeCount * 4 + 1] = c.y; holeUniform[holeCount * 4 + 2] = c.z; holeUniform[holeCount * 4 + 3] = m.cellSize * 0.5f * 1.03f
            holeCount++
        }
        run {
            val path = m.solution
            val avoid = HashSet<Int>().apply { add(m.start); add(m.goal); hz.holeCells.forEach { add(it) }; hz.bridgeCells.forEach { add(it) }; hz.hammerCells.forEach { add(it) }; hz.fxCell.forEach { add(it) }; for (sc in hz.swingCells) { add(sc); for (o in m.links[sc]) add(o) } }
            val picks = ArrayList<Int>()
            for (f in floatArrayOf(0.25f, 0.5f, 0.75f)) {
                val base = (path.size * f).toInt().coerceIn(1, path.size - 2)
                val cell = (0..4).flatMap { d -> listOf(base + d, base - d) }.map { it.coerceIn(1, path.size - 2) }.map { path[it] }.firstOrNull { it !in avoid && it !in picks }
                if (cell != null && path.size > 12) picks.add(cell)
            }
            checkpoints = picks.sortedBy { c -> path.indexOf(c) }.toIntArray()
            cpReached = -1
        }
        if (pal.themeId == 12) {                                       // a first carpet of fallen leaves
            val vs = m.mesh.vertices
            for (i in 0 until minOf(m.cellCount * 8, 2400)) {
                val c = rnd.nextInt(m.cellCount)
                val a = vs[m.mesh.cellVertex(c, 0)]; val bq = vs[m.mesh.cellVertex(c, 1)]; val cq = vs[m.mesh.cellVertex(c, 3)]
                val p = m.mesh.surface.project(a + (bq - a) * rnd.nextFloat() + (cq - a) * rnd.nextFloat())
                leaves.add(p, m.mesh.surface.normal(p), m.marbleRadius * (0.55f + 0.4f * rnd.nextFloat()), time - 30f)
            }
        }
        markerMesh!!.let { val mb = b.markers(hz.doorEdges, checkpoints, cpReached); it.upload(mb.vertices(), mb.indices()) }
        cloudAmt = 0f
        val wa = rnd.nextFloat() * 6.2831853f
        val ws = 0.14f + 0.16f * rnd.nextFloat()
        windX = kotlin.math.cos(wa) * ws; windY = kotlin.math.sin(wa) * ws * 0.5f
        fallTimer = 0f
        hintLeft = 0f; hintCool = 0f
        pctCell = -1; pct = 0; pctBest = 0; lastHud = -1
        goalBursted = false
        host.onFinish(pal.name + listOfNotNull(terrain.label.ifEmpty { null }, medium.label.ifEmpty { null }, if (hz.hasDoors) "Doors" else null, if (hz.hasHoles) "Holes" else null, if (hz.hasBridges) "Bridges" else null, if (hz.hasHammers) "Hammers" else null, if (hz.hasSwings) "Swings" else null, if (keyGates != null) "Keys & Gates" else null, if (useClouds) "Clouds" else null, if (rain != null) "Rain" else null).joinToString("") { " · $it" }, pal.accent)
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
    fun requestNew(k: ShapeKind? = null, terrain: Terrain? = null, plain: Boolean = false, theme: Int? = null, medium: Medium? = null) {
        nextKind = k ?: pickNext()
        pendingTerrain = terrain; pendingPlain = plain; pendingTheme = theme; pendingMedium = medium
        if (phase != Phase.SINK) { phase = Phase.SINK; audio.levelSink() }
    }

    /** Begin a real game. null shape / terrain mean "random". */
    fun startGame(shape: ShapeKind?, terrain: Terrain?, plain: Boolean, theme: Int? = null, medium: Medium? = null) {
        menuMode = false; paused = false
        requestNew(shape, terrain, plain, theme, medium)
    }

    /** Menu backdrop: build the chosen level right away so the player can see what they picked. */
    fun previewLevel(shape: ShapeKind?, terrain: Terrain?, plain: Boolean, theme: Int? = null, medium: Medium? = null) {
        menuMode = true; paused = false
        newLabyrinth(shape ?: ShapeKind.values().filter { progress?.shapeUnlocked(it) != false }.ifEmpty { listOf(ShapeKind.SPHERE) }.let { it[rnd.nextInt(it.size)] }, terrain, plain, theme, medium)
    }

    private fun respawnCell() = if (cpReached >= 0) checkpoints[cpReached] else (maze?.start ?: 0)

    /** Puts the ball back at the last checkpoint or the start of the maze. */
    fun resetMarble(toCheckpoint: Boolean = false) {
        host.onStat("resets", 1f)
        marble?.reset(if (toCheckpoint) respawnCell() else (maze?.start ?: 0)); rollQ = Quat.IDENTITY; threadCell = -1; initChase()
        if (!toCheckpoint) { cpReached = -1; refreshMarkers() }
    }

    private fun refreshMarkers() {
        val b = builder ?: return
        val hz = hazards ?: return
        markerMesh?.let { val mb = b.markers(hz.doorEdges, checkpoints, cpReached); it.upload(mb.vertices(), mb.indices()) }
    }

    /** Timed hint: shows the golden thread for [HINT_SECONDS], then it fades; usable again after [HINT_COOLDOWN]. */
    fun requestHint() {
        if (difficulty == com.maze3d.core.Difficulty.ULTRA) { host.onToast("Hints are off on Ultra"); return }
        if (phase != Phase.PLAY || hintCool > 0f) { if (hintCool > 0f) host.onToast("Hint ready in ${kotlin.math.ceil(hintCool).toInt()}s"); return }
        hintLeft = HINT_SECONDS
        hintCool = HINT_COOLDOWN
        threadCell = -1
    }

    fun applyProfileSetting(p: QualityProfile) { profile = p }

    /** [v] in 0..1: 1 is closest. */
    fun setZoomLevel(v: Float) { zoomV = v; zoom = 1.6f + (0.55f - 1.6f) * v; updateCamera() }

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
        if (padLookX != 0f && cameraMode == 2) pendingYaw += padLookX * dt * 2.2f
        if (padOn && cameraMode != 2 && controlMode == 2 && phase == Phase.PLAY && !paused) drag(padX * dt * 700f, padY * dt * 700f, dt)
        update(dt)
        computeCamera(dt)
        draw()
        if (++framesDrawn == 4) host.onReady()
        val capNs = when (profile.fps) { 0 -> 33_000_000L; 1 -> 16_000_000L; else -> 0L }
        if (capNs > 0) { val spent = System.nanoTime() - now; if (spent < capNs) try { Thread.sleep((capNs - spent) / 1_000_000L) } catch (_: InterruptedException) { } }
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
            // re-think the viewing side a few times a second (sticky, so it does not flip between near-equal views), then ease toward it
            if (time - followPickT > 0.25f || followAim == null) {
                followPickT = time
                followAim = com.maze3d.core.FollowView.pickSticky(mb.surface, mb.position, n, orient.inverseRotate(Vec3(0f, 0f, 1f)), followAim)
            }
            val aim = followAim!!
            followDirS = followDirS?.let { (it + (aim - it) * (1f - exp(-3f * dt))).normalized() } ?: aim
            val local = followDirS!!
            val w = orient.rotate(local)
            val dotz = w.z.coerceIn(-1f, 1f)
            val ang = kotlin.math.acos(dotz)
            if (ang > 0.01f) {
                val axis = w cross Vec3(0f, 0f, 1f)
                val step = min(ang * (1f - exp(-4.5f * dt)), 3.4f * dt)
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
                val gLocal = when {
                    cameraMode == 2 -> chaseGravity(mb)
                    controlMode == 2 -> orient.inverseRotate(Vec3(0f, -1f, 0f)) * GRAVITY         // gravity mode: the piece's own tilt moves the ball
                    else -> orient.inverseRotate(screenSteer()) * GRAVITY                             // joystick or tilt, in the screen's frame
                }
                if (fallTimer > 0f) {
                    // marble is dropping into a hole; physics paused
                    fallTimer -= dt
                    if (fallTimer <= 0f) {
                        mb.reset(respawnCell()); rollQ = Quat.IDENTITY; threadCell = -1; initChase(); poison = 0f
                        val sc = m.centers[respawnCell()]
                        sparks.burst(sc, mb.surface.normal(sc), 28, 0.9f, Vec3(0.4f, 0.8f, 1f))
                        audio.respawn(panOf(sc))
                    }
                } else {
                    mb.clock = time
                    val sIdx = (playSeconds * 10f).toInt()
                    while (runSamples.size / 3 <= sIdx) { runSamples.add(mb.position.x); runSamples.add(mb.position.y); runSamples.add(mb.position.z) }
                    val before = mb.position
                    mb.step(gLocal, dt)
                    if (gustAmt > 0.01f) {                               // storm gust: the wind shoves the ball along the surface
                        val n = mb.surface.normal(mb.position)
                        val w = gustDir - n * (gustDir dot n)
                        if (w.lengthSq() > 1e-6f) mb.kick(w.normalized() * (GRAVITY * 0.2f * gustAmt * dt))
                    }
                    statDist += (mb.position - before).length() / m.cellSize
                    statTime += dt
                    if (statTime >= 3f) { host.onStat("playSeconds", statTime); host.onStat("distance", statDist); statTime = 0f; statDist = 0f }
                    updateMedium(mb, m, dt)
                    rollMarble(mb, dt)
                    val spd = mb.velocity.length() / (m.cellSize * 4f)
                    if (spd > 0.3f && time - trailAt > 0.025f) {
                        trailAt = time
                        val under = mb.position - mb.surface.normal(mb.position) * (mb.radius * 0.8f)
                        sparks.emit(under, Vec3.ZERO, 0.38f, pal.accent, 0.012f + 0.01f * spd)
                    }
                    if (pal.themeId == 7) snowfall(mb, m)
                    if (pal.themeId == 11) { glowStamp(mb); glowAtmosphere(mb, m, dt) }
                    if (pal.themeId == 17) lavaAtmosphere(mb, m, dt)
                    if (pal.themeId == 12) fallLeaves(mb, m, dt)
                    audio.roll((mb.velocity.length() / (m.cellSize * 4f)).coerceIn(0f, 1f),
                        when { terrain == Terrain.SAND -> 2; terrain == Terrain.MUD -> 3; pal.floorMat == Mat.WOOD -> 1; else -> 0 })
                    if (terrain != Terrain.NONE) layTrail(mb, m, dt)
                    sandField?.let { stampSand(it, mb, m) }
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
                    val hzb = hazards
                    if (hzb != null && fallTimer <= 0f) {
                        val cs = m.cellSize
                        val bi = hzb.bridgeAt(mb.cell)
                        if (bi >= 0) {
                            val ctr = m.centers[mb.cell]
                            val lateral = (mb.position - ctr) dot bridgeLat[bi]
                            val off = hzb.bridgeOffset(bi, time) * cs
                            val along = (((mb.position - ctr) dot bridgeDirT[bi]) / cs + 0.5f).coerceIn(0f, 0.999f)
                            val k = (along * Hazards.BOARDS).toInt()
                            fun gone(j: Int) = j in 0 until Hazards.BOARDS && hzb.boardScale(bi, j, time) < 0.35f
                            val overGap = gone(k) && (gone(k - 1) || gone(k + 1))
                            if (kotlin.math.abs(lateral - off) > bridgeHalfWidth * cs + mb.radius * 0.2f || overGap) {
                                fallTimer = 0.55f; host.onStat("falls", 1f)
                                audio.fall(panOf(ctr))
                                host.onToast("Fell through the bridge")
                                fallFrom = mb.position
                                fallTo = ctr - mb.surface.normal(ctr) * (cs * 0.8f)
                            }
                        }
                        for (si in swingFrames.indices) {
                            if (fallTimer > 0f) break
                            val sc = hzb.swingCells[si]
                            val c = m.centers[sc]
                            if ((c - mb.position).length() > cs * 2f) continue
                            val d = swingDirT[si]
                            val rel = mb.position - c
                            val along = (rel dot d) / cs
                            val lt = (rel dot swingLat[si]) / cs
                            val ax = kotlin.math.abs(along)
                            val dxp = maxOf(0f, ax - Hazards.SWING_HALFPIT)
                            if (kotlin.math.sqrt(dxp * dxp + lt * lt) < 0.5f) {
                                val x = hzb.swingX(si, time)
                                val g = Hazards.SWING_HALF + 0.03f
                                if (kotlin.math.abs(along - x) < g && kotlin.math.abs(lt) < g) {
                                    // on the platform: carried with it; the ball's own roll still counts, so you can step off at either lip
                                    val vd = hzb.swingV(si, time) * cs
                                    val vAlong = mb.velocity dot d
                                    mb.kick(d * ((vd - vAlong) * kotlin.math.min(1f, 9f * dt)))
                                } else {
                                    fallTimer = 0.55f; host.onStat("falls", 1f)
                                    audio.fall(panOf(c))
                                    host.onToast("Fell into the pit")
                                    fallFrom = mb.position
                                    fallTo = c - mb.surface.normal(c) * (cs * 1.0f)
                                }
                            }
                        }
                        hammerCooldown -= dt
                        for (i in hammerFrames.indices) {
                            if (hammerCooldown > 0f) break
                            val c = m.centers[hzb.hammerCells[i]]
                            if ((c - mb.position).length() > cs * 1.6f) continue
                            val n = mb.surface.normal(c)
                            val a = hzb.hammerAngle(i, time)
                            val dir = hammerAxis[i]
                            val head = c - dir * (HazardModels.HAMMER_LENGTH * kotlin.math.sin(a) * cs) + n * ((HazardModels.HAMMER_PIVOT - HazardModels.HAMMER_LENGTH * kotlin.math.cos(a)) * cs)
                            if ((head - mb.position).length() < HazardModels.HAMMER_HEAD * cs + mb.radius) {
                                val sign = if (hzb.hammerRate(i, time) >= 0f) -1f else 1f      // the head moves toward -dir while the angle grows
                                mb.kick(dir * (sign * cs * 6f) + n * (cs * 0.6f))
                                hammerCooldown = 0.7f
                                host.onStat("hammer", 1f)
                                audio.impact(1f, panOf(head))
                                host.onImpact(1f)
                                sparks.burst(head, n, 12, 0.6f, Vec3(1f, 0.7f, 0.3f))
                                host.onToast("Knocked back by a hammer")
                            }
                        }
                    }
                    if (hzb != null && hzb.hasFx && fallTimer <= 0f) updateFx(hzb, m, mb, dt)
                    keyGates?.let { kg ->
                        for (j in kg.keyCells.indices) {
                            if (kg.keyTaken[j]) continue
                            val kc = m.centers[kg.keyCells[j]]
                            if ((kc - mb.position).length() < m.cellSize * 0.42f && kg.take(kg.keyCells[j]) >= 0) {
                                rebuildGates()
                                host.onStat("keys", 1f)
                                audio.respawn(panOf(kc))
                                sparks.burst(kc, mb.surface.normal(kc), 22, 0.8f, keyCols[j % 3])
                                host.onToast(if (kg.collected == kg.count) "${keyNames[j % 3]} key! All gates are open — find the exit" else "${keyNames[j % 3]} key! ${kg.collected} of ${kg.count} — a gate has opened")
                            }
                        }
                    }
                    val hz = hazards
                    if (hz != null && hz.hasHoles) {
                        val under = mb.position - mb.surface.normal(mb.position) * mb.radius
                        val h = hz.fellInto(mb.cell, under, time)
                        if (h >= 0) {
                            fallTimer = 0.55f; host.onStat("falls", 1f)
                            audio.fall(panOf(m.centers[hz.holeCells[h]]))
                            host.onToast("Fell into a hole")
                            fallFrom = mb.position
                            fallTo = m.centers[hz.holeCells[h]] - mb.surface.normal(m.centers[hz.holeCells[h]]) * (m.cellSize * 0.8f)
                            sparks.burst(m.centers[hz.holeCells[h]], mb.surface.normal(m.centers[hz.holeCells[h]]), 10, 0.5f, pal.accent)
                        }
                    }
                    if (cpReached + 1 < checkpoints.size && mb.cell == checkpoints[cpReached + 1]) {
                        cpReached++
                        host.onStat("checkpoints", 1f)
                        refreshMarkers()
                        val c = m.centers[mb.cell]
                        sparks.burst(c, mb.surface.normal(c), 18, 0.7f, Vec3(0.4f, 1f, 0.55f))
                        audio.respawn(panOf(c))
                        host.onToast("Checkpoint ${cpReached + 1} of ${checkpoints.size}")
                    }
                    if (mb.atGoal) {
                        phase = Phase.SOLVED; solvedTimer = 0f
                        if (!solvedReported && gameMode != 0 && !menuMode) { solvedReported = true; host.onModeSolved(gameMode, playSeconds, difficulty, lvlSeed) }
                        else if (!solvedReported) { solvedReported = true; host.onSolved(surfaceKey, kind, difficulty, playSeconds,
                            com.maze3d.core.Ghost(lvlSeed, kind, difficulty, surfaceKey, lvlPalIndex, terrain, medium, lvlPlain, playSeconds, runSamples.toFloatArray()), raceGhost?.seconds ?: 0f) }
                    }
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
                if (solvedTimer > 1.6f && gameMode != 2) { phase = Phase.SINK; nextKind = pickNext(); audio.levelSink() }
            }
            Phase.SINK -> {
                growT -= dt / 0.8f
                if (growT <= 0f) { newLabyrinth(nextKind ?: pickNext(), pendingTerrain, pendingPlain, pendingTheme, pendingMedium); nextKind = null; pendingTerrain = null; pendingPlain = false; pendingTheme = null; pendingMedium = null }
            }
        }

        updateRain(dt)
        if (phase != Phase.PLAY || fallTimer > 0f) audio.roll(0f, 0)
        updateStorm(dt)
        audio.wind(maxOf(cloudAmt, gustAmt))
        val gl = orient.inverseRotate(Vec3(0f, -1f, 0f)) * GRAVITY
        sparks.update(dt, gl)
        dust.update(dt, gl * -0.5f)
        val cloudTarget = if (useClouds && phase != Phase.SINK) 1f else 0f
        cloudAmt += (cloudTarget - cloudAmt) * min(1f, dt * 0.8f)

        val sec = playSeconds.toInt()
        val mbP = marble
        if (mbP != null && maze != null && mbP.cell != pctCell) {
            pctCell = mbP.cell
            val total = maze!!.solution.size.coerceAtLeast(2)
            val left = maze!!.pathFrom(mbP.cell).size
            pct = (100f * (1f - (left - 1f) / (total - 1f))).toInt().coerceIn(0, 100)
            if (pct > pctBest) pctBest = pct
        }
        if (phase == Phase.SOLVED) pct = 100
        val stray = if (difficulty == com.maze3d.core.Difficulty.EASY) (pctBest - pct).coerceIn(0, 60) else 0
        val code = ((sec * 4 + (if (phase == Phase.SOLVED) 1 else 0)) * 128 + pct) * 64 + stray
        if (code != lastHud) {
            lastHud = code
            host.onHud(kind.label, sec, phase == Phase.SOLVED, pct, stray)
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
    /** Steering input (right, forward) from the joystick or, in tilt mode, from the gyro alone. */
    private fun steerInput(): Pair<Float, Float> {
        if (controlMode == 1) {
            val d = deviceGravity - Vec3(0f, -1f, 0f)
            return (d.x * 1.4f * gyroSens).coerceIn(-1f, 1f) to (tiltPitch * 1.4f).coerceIn(-1f, 1f)
        }
        return if (joyActive) chaseIx to chaseIy else 0f to 0f
    }

    /** Follow camera: a push in the screen plane, so the piece itself never needs to turn. */
    private fun screenSteer(): Vec3 {
        var (ix, iy) = steerInput()
        val len = kotlin.math.sqrt(ix * ix + iy * iy)
        if (len > 1f) { ix /= len; iy /= len }
        return camRight * ix + camUp * iy
    }

    private fun chaseGravity(mb: Marble): Vec3 {
        var (ix, iy) = steerInput()
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

    /** Footprints, kicked-up dust or mud, flying grains and clumps, and the dirt that builds up on the marble. */
    private fun layTrail(mb: Marble, m: Maze, dt: Float) {
        mb.simDetail = simQuality
        val q = simQuality.coerceIn(0, 2)
        val n = mb.surface.normal(mb.position)
        val under = mb.position - n * mb.radius
        val size = mb.radius * (if (terrain == Terrain.MUD) 1.25f else 1.1f)
        val last = trail.last
        val spd = mb.velocity.length() / (m.cellSize * 4f)
        // stamp densely along the path so the groove is continuous; fast rolling digs a slightly wider track
        if (sandField == null && (last == null || (under - last).length() > size * (if (q == 0) 0.4f else if (q == 1) 0.24f else 0.14f))) trail.add(under, n, size * (1f + 0.25f * spd.coerceAtMost(1f)), time)
        dirt = (dirt + spd * dt * (if (terrain == Terrain.MUD) 0.35f else 0.18f)).coerceAtMost(1f)
        val vdir = if (mb.velocity.lengthSq() > 1e-8f) mb.velocity.normalized() else Vec3.ZERO
        if (q == 2 && spd > 0.15f && rnd.nextFloat() < 0.5f) {
            // ultra: a spray of fine grains / droplets thrown from both sides of the wheel track
            val side = (n cross vdir).normalized()
            val col = if (terrain == Terrain.MUD) Vec3(0.13f, 0.08f, 0.05f) else if (pal.themeId == 7) Vec3(0.85f, 0.92f, 1f) else Vec3(0.8f, 0.66f, 0.42f)
            repeat(3) {
                val sd = if (rnd.nextBoolean()) 1f else -1f
                val v = (n * 0.8f + side * sd * 0.7f - vdir * 0.2f + Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f) * 0.4f).normalized() * (0.2f + 0.4f * spd * rnd.nextFloat())
                sparks.emit(under, v, 0.4f, col, 0.0035f)
            }
        }
        if (spd > 0.12f && time - dustAt > 0.04f) {
            dustAt = time
            if (terrain == Terrain.SAND) {
                // a soft plume behind the marble, plus grains thrown up and back
                dust.puff(under - vdir * (mb.radius * 0.5f), n, 1, 0.05f + 0.03f * spd, (if (pal.themeId == 7) Vec3(0.9f, 0.95f, 1f) else Vec3(0.88f, 0.75f, 0.54f)))
                repeat(1 + (spd * 2f).toInt() * (if (q == 2) 3 else 1)) {
                    val v = (n * 0.9f - vdir * 0.5f + Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f) * 0.7f).normalized() * (0.18f + 0.3f * spd * rnd.nextFloat())
                    sparks.emit(under, v, 0.32f, if (pal.themeId == 7) Vec3(0.75f, 0.85f, 1f) else Vec3(0.55f, 0.43f, 0.26f), 0.0045f)
                }
            } else {
                // heavy clumps flung to the sides; when fast, some land as splats beside the track
                dust.puff(under, n, 1, 0.028f, Vec3(0.16f, 0.10f, 0.06f))
                if (spd > 0.3f && q >= 1) {
                    val side = (n cross vdir).normalized() * (if (rnd.nextBoolean()) 1f else -1f)
                    val v = (n * 0.7f + side * (0.6f + rnd.nextFloat() * 0.6f) - vdir * 0.3f).normalized() * (0.22f + 0.35f * spd)
                    dust.emit(under, v, 0.45f, Vec3(0.12f, 0.075f, 0.045f), 0.016f + 0.01f * rnd.nextFloat())
                    if (sandField == null && rnd.nextFloat() < 0.35f * spd) {
                        val land = mb.surface.project(under + side * (mb.radius * (1.6f + 2.2f * rnd.nextFloat())) - vdir * (mb.radius * 1.0f * rnd.nextFloat()))
                        trail.add(land, mb.surface.normal(land), mb.radius * (0.22f + 0.35f * rnd.nextFloat()), time)
                    }
                }
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
        val options = ShapeKind.values().filter { it != kind && (progress?.shapeUnlocked(it) != false) }.ifEmpty { listOf(ShapeKind.SPHERE) }
        return options[rnd.nextInt(options.size)]
    }

    private fun rollMarble(mb: Marble, dt: Float) {
        val n = mb.surface.normal(mb.position)
        val omega = (n cross mb.velocity) / mb.radius
        val sp = omega.length()
        if (sp > 1e-4f) rollQ = (Quat.fromAxisAngle(omega, sp * dt) * rollQ).normalized()
    }

    private fun applyQuality() {
        val p = profile
        if (appliedProfile == p) return
        appliedProfile = p
        shadow?.release()
        shadow = when (p.shadows) { 2 -> ShadowMap(2048); 1 -> ShadowMap(1024); else -> null }
        if (shadow?.ok == false) { shadow?.release(); shadow = null }
        cube?.release()
        cube = when (p.reflections) { 2 -> ReflectionCube(128); 1 -> ReflectionCube(64); else -> null }
        if (cube?.ok == false) { cube?.release(); cube = null }
        cubeFresh = 6
        if (p.usePost) {
            renderScale = if (p.resolution == 1) 0.75f else 1f
            postOn = post.resize(width, height, renderScale)
        } else {
            post.dropScene(); postOn = false
        }
        rw = if (postOn) post.sceneW else width
        rh = if (postOn) post.sceneH else height
    }

    /** Keeps the frame rate at the display's refresh rate by trading render resolution when the GPU is struggling. */
    private fun adaptResolution(dt: Float) {
        if (!postOn || profile.resolution != 0) return
        val cap = when (profile.fps) { 0 -> 30f; 1 -> 60f; else -> 144f }
        val target = 1f / minOf(refreshHz, cap).coerceIn(30f, 144f)
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
            floorMesh?.draw(); wallMesh?.draw(); propMesh?.draw()
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
        GLES30.glUniform3f(loc(sceneProg, "uFloor2"), pal.floor2.x, pal.floor2.y, pal.floor2.z)
        val night = pal.themeId == 7 || pal.themeId == 9 || pal.themeId == 11 || pal.themeId == 17
        GLES30.glUniform1f(loc(sceneProg, "uNight"), if (night) 1f else if (medium == Medium.STORM) 0.62f else 0f)
        GLES30.glUniform1f(loc(sceneProg, "uExposure"), exposure())
        run {                                                   // follow view: see through the shape where it hides the ball
            val mbc = marble
            var want = 0f
            val cs = maze?.cellSize ?: 0.1f
            if (cameraMode == 1 && mbc != null && phase != Phase.MENU && chaseBlend < 0.5f) {
                // is any of the shape actually between the camera and the ball? (checked in the piece's own space)
                val sf = mbc.surface
                val eyeL = orient.inverseRotate(eye)
                val from = mbc.position + sf.normal(mbc.position) * (mbc.radius * 1.5f)
                for (k in 1..24) {
                    val q = from + (eyeL - from) * (k / 24f)
                    if ((q - sf.core(q)).length() < sf.radius * 0.99f) { want = 1f; break }
                }
            }
            cutAmt += (want - cutAmt) * 0.15f
            if (cutAmt > 0.02f && mbc != null) {
                val w = orient.rotate(mbc.position)
                GLES30.glUniform4f(loc(sceneProg, "uCutA"), eye.x, eye.y, eye.z, cutAmt)
                GLES30.glUniform4f(loc(sceneProg, "uCutB"), w.x, w.y, w.z, cs * 1.8f)
            } else GLES30.glUniform4f(loc(sceneProg, "uCutA"), 0f, 0f, 0f, 0f)
        }
        run { val sp = studPitch(); GLES30.glUniform2f(loc(sceneProg, "uStud"), 1f / sp, sp * 0.12f) }
        GLES30.glUniform1f(loc(sceneProg, "uHedgeSnow"), if (pal.themeId == 7) 1f else 0f)
        GLES30.glUniform1f(loc(sceneProg, "uHiero"), if (pal.themeId == 8) 1f else 0f)
        lampUniform.fill(0f); lampColUniform.fill(0f)
        // the lamps nearest the ball (or the view centre) light the scene; how many depends on the quality setting
        val lampCap = when { profile.shadows >= 2 -> 12; profile.shadows >= 1 -> 8; else -> 4 }
        if (lamps.isNotEmpty()) {
            val from = marble?.position ?: Vec3.ZERO
            val order = lamps.indices.sortedBy { (lamps[it] - from).lengthSq() }.take(lampCap)
            val cs = maze?.cellSize ?: 0.1f
            order.forEachIndexed { i, li ->
                val p = lamps[li]; val c = lampColList.getOrElse(li) { Vec3(0.55f, 0.72f, 1f) }
                val flick = if (pal.themeId == 17 || pal.themeId == 1) 0.85f + 0.15f * kotlin.math.sin(time * 9f + li * 1.7f) * kotlin.math.sin(time * 5.3f + li) else 1f
                lampUniform[i * 4] = p.x; lampUniform[i * 4 + 1] = p.y; lampUniform[i * 4 + 2] = p.z
                lampUniform[i * 4 + 3] = cs * (lampReachList.getOrElse(li) { 4.2f } + (if (pal.themeId == 11) 1f else 0f))
                lampColUniform[i * 3] = c.x * flick; lampColUniform[i * 3 + 1] = c.y * flick; lampColUniform[i * 3 + 2] = c.z * flick
            }
        }
        GLES30.glUniform1f(loc(sceneProg, "uLampGain"), if (lamps.isEmpty()) 0f else if (night) 1f else 0.6f)
        GLES30.glUniform3fv(loc(sceneProg, "uLampC"), 12, lampColUniform, 0)
        GLES30.glUniform4fv(loc(sceneProg, "uLamp"), 12, lampUniform, 0)
        run {
            val sf = sandField
            GLES30.glUniform1f(loc(sceneProg, "uSandOn"), if (sf != null) 1f else 0f)
            if (sf != null) {
                GLES30.glActiveTexture(GLES30.GL_TEXTURE5)
                GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sandTex)
                GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
                GLES30.glUniform1i(loc(sceneProg, "uSandTex"), 5)
                GLES30.glUniform4f(loc(sceneProg, "uSandP"), sf.t.toFloat(), sf.tile.toFloat(), sf.perRow.toFloat(), 0f)
                GLES30.glUniform2f(loc(sceneProg, "uSandAtlas"), sf.atlasW.toFloat(), sf.atlasH.toFloat())
            } else GLES30.glUniform1i(loc(sceneProg, "uSandTex"), 5)
        }
        val mbL = marble
        if (mbL != null && phase != Phase.MENU) {
            val wp = orient.rotate(mbL.position)
            GLES30.glUniform3f(loc(sceneProg, "uMarbleL"), wp.x, wp.y, wp.z)
            GLES30.glUniform1f(loc(sceneProg, "uMarbleR"), mbL.radius)
            if (pal.themeId == 11) GLES30.glUniform3f(loc(sceneProg, "uMarbleC"), 0.4f, 1f, 0.55f) else if (pal.themeId == 9) GLES30.glUniform3f(loc(sceneProg, "uMarbleC"), 1f, 0.4f, 0.9f) else GLES30.glUniform3f(loc(sceneProg, "uMarbleC"), (0.45f + pal.accent.x * 0.3f) * (1f - poison) + 0.5f * poison, (0.6f + pal.accent.y * 0.25f) * (1f - poison) + 1.0f * poison, 0.9f * (1f - poison) + 0.1f * poison)
        } else GLES30.glUniform1f(loc(sceneProg, "uMarbleR"), 0f)
        val fogK = ease01(chaseBlend)
        GLES30.glUniform1f(loc(sceneProg, "uFog"), 0f)
        GLES30.glUniform3f(loc(sceneProg, "uFogCol"), pal.bgTop.x * 0.5f, pal.bgTop.y * 0.5f, pal.bgTop.z * 0.55f)
        GLES30.glUniform1f(loc(sceneProg, "uShadowOn"), if (sm != null) 1f else 0f)
        GLES30.glUniform1f(loc(sceneProg, "uTexel"), if (sm != null) 1f / sm.size else 0f)
        if (sm != null) { sm.bind(0); GLES30.glUniform1i(loc(sceneProg, "uShadow"), 0) }
        else { GLES30.glUniform1i(loc(sceneProg, "uShadow"), 0) }

        GLES30.glUniform1i(loc(sceneProg, "uHoleN"), holeCount)
        if (holeCount > 0) GLES30.glUniform4fv(loc(sceneProg, "uHoleC"), 48, holeUniform, 0)
        scene(pal.floorMat, pal.floorRough, 0f, 0f); floorMesh?.draw()
        if ((pal.floorMat == Mat.TURF && profile.shadows >= 1) || pal.floorMat == Mat.TOY) {      // short 3D grass / raised studs: the ground drawn again as layers climbing up
            val toy = pal.floorMat == Mat.TOY
            val shells = if (toy) (if (profile.shadows >= 2) 5 else 3) else if (profile.shadows >= 2) 8 else 5
            GLES30.glUniform1f(loc(sceneProg, "uShellH"), if (toy) studPitch() * 0.15f else (maze?.cellSize ?: 0.1f) * 0.075f)
            for (k in 1..shells) { GLES30.glUniform1f(loc(sceneProg, "uShell"), k / (shells + 1f)); floorMesh?.draw() }
            GLES30.glUniform1f(loc(sceneProg, "uShell"), 0f)
        }
        GLES30.glUniform1i(loc(sceneProg, "uHoleN"), 0)
        scene(pal.wallMat, pal.wallRough, pal.wallMetal, 0f); wallMesh?.draw()
        if (keyGates != null) { scene(Mat.PROP, 0.35f, 0.1f, 0.25f); gateMesh?.draw() }
        if (pal.themeId >= 0) { scene(Mat.PROP, 0.42f, 0f, 0f); propMesh?.draw(); drawAnimProps() }
        drawHazardObjects()
        GLES30.glUniform1f(loc(sceneProg, "uDoorMode"), 1f)
        doorMesh?.draw()
        GLES30.glUniform1f(loc(sceneProg, "uDoorMode"), 0f)
        drawTrail(vpm, cam)
        drawGlowAndLeaves(vpm, cam)
        drawHoles(vpm, cam)
        drawPuddles(vpm, cam)
        drawMedium(vpm, cam)
        GLES30.glEnable(GLES30.GL_POLYGON_OFFSET_FILL)
        GLES30.glPolygonOffset(-2f, -2f)
        scene(Mat.FLAT, 0.5f, 0f, 0.9f); markerMesh?.draw()
        if (pal.themeId == 11) { scene(Mat.FLAT, 0.5f, 0f, 0.55f); myceliumMesh?.draw() }
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
        val cloudsOn = cloudAmt > 0.01f && profile.clouds
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
        GLES30.glUniform1f(loc(bgProg, "uExposure"), exposure())
        GLES30.glUniform3f(loc(bgProg, "uTop"), pal.bgTop.x, pal.bgTop.y, pal.bgTop.z)
        GLES30.glUniform3f(loc(bgProg, "uBottom"), pal.bgBottom.x, pal.bgBottom.y, pal.bgBottom.z)
        GLES30.glUniform1f(loc(bgProg, "uAspect"), width.toFloat() / height)
        GLES30.glUniform1f(loc(bgProg, "uTheme"), pal.backdrop.toFloat())
        GLES30.glUniform1f(loc(bgProg, "uTime"), time)
        GLES30.glBindVertexArray(emptyVao[0])
        GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
        GLES30.glBindVertexArray(0)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)

        renderWorld(vp, eye, sm)

        // ---- marble -----------------------------------------------------------------------
        GLES30.glUseProgram(marbleProg)
        GLES30.glUniform1f(loc(marbleProg, "uExposure"), exposure())
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
            drawR = mb.radius * (1f - 0.35f * e)
        }
        if (terrain != Terrain.NONE) drawPos -= mb.surface.normal(mb.position) * (mb.radius * (if (terrain == Terrain.MUD) 0.3f else 0.12f))
        GLES30.glUniform1f(loc(marbleProg, "uDirt"), if (terrain == Terrain.NONE) dirt else dirt)
        if (terrain == Terrain.MUD) GLES30.glUniform3f(loc(marbleProg, "uDirtCol"), 0.14f, 0.08f, 0.045f) else GLES30.glUniform3f(loc(marbleProg, "uDirtCol"), 0.62f, 0.46f, 0.26f)
        GLES30.glUniform3f(loc(marbleProg, "uCenter"), drawPos.x, drawPos.y, drawPos.z)
        GLES30.glUniform1f(loc(marbleProg, "uRadius"), drawR)
        GLES30.glUniform3f(loc(marbleProg, "uAccent"), pal.accent.x, pal.accent.y, pal.accent.z)
        GLES30.glUniform1i(loc(marbleProg, "uSkin"), ballSkin())
        GLES30.glUniform1f(loc(marbleProg, "uTime"), time)
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

        // the racing ghost: a translucent copy of the ball following the saved run
        raceGhost?.let { g ->
            if (phase == Phase.PLAY || phase == Phase.SOLVED) g.positionAt(playSeconds)?.let { gp ->
                GLES30.glEnable(GLES30.GL_BLEND)
                GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
                GLES30.glDepthMask(false)
                GLES30.glUniform3f(loc(marbleProg, "uCenter"), gp.x, gp.y, gp.z)
                GLES30.glUniform1f(loc(marbleProg, "uRadius"), mb.radius)
                GLES30.glUniform3f(loc(marbleProg, "uAccent"), 0.55f, 0.85f, 1f)
                GLES30.glUniform1f(loc(marbleProg, "uGhost"), 1f)
                sphere?.draw()
                GLES30.glDepthMask(true)
                GLES30.glUniform3f(loc(marbleProg, "uAccent"), pal.accent.x, pal.accent.y, pal.accent.z)
                GLES30.glUniform3f(loc(marbleProg, "uCenter"), drawPos.x, drawPos.y, drawPos.z)
                GLES30.glUniform1f(loc(marbleProg, "uRadius"), drawR)
            }
        }

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
            GLES30.glDrawArraysInstanced(GLES30.GL_TRIANGLES, 0, 6, if (profile.particles == 0) (RAIN_DROPS * 0.45f).toInt() else RAIN_DROPS)
            GLES30.glBindVertexArray(0)
        }
        GLES30.glDepthMask(true)

        GLES30.glDisable(GLES30.GL_BLEND)
        if (postOn) {
            pp.near = nearNow; pp.far = 40f; pp.p00 = p00; pp.p11 = p11
            val kk = ease01(chaseBlend)
            val mbw = marble?.let { orient.rotate(it.position) }
            val chaseFocus = if (mbw != null) (eye - mbw).length() else camDist
            pp.focus = if (cameraMode == 1 && mbw != null && phase != Phase.MENU) chaseFocus else camDist + (chaseFocus - camDist) * kk
            pp.focusRange = kind.outerRadius * 1.1f + (0.9f - kind.outerRadius * 1.1f) * kk
            pp.time = time
            pp.cloudOn = cloudsOn
            val qp = profile
            pp.ssao = false; pp.dof = qp.dof                 // screen-space AO was dropped: it darkened edges more than it helped
            pp.aoAmt = 0.85f; pp.dofAmt = 0.85f
            pp.bloomAmt = if (!qp.bloom) 0f else if (qp.shadows >= 2) 0.34f else 0.26f
            pp.fxaa = qp.fxaa
            pp.grain = 0f
            pp.motion = if (qp.motion) ease01(chaseBlend) * chaseSpeed * 0.9f else 0f
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

    /** Ball skin for the current level: themed surfaces get a matching ball, sand and water levels a beach ball. */
    private fun ballSkin(): Int = if (ballChoice >= 0) ballChoice else when {
        pal.themeId >= 0 -> pal.themeId + 1
        terrain == Terrain.SAND || medium == Medium.WATER -> 20
        else -> 0
    }

    /** Neon trail: a glowing disc every little while along the ball's path; it fades over a few seconds in the shader. */
    private fun glowStamp(mb: Marble) {
        val n = mb.surface.normal(mb.position)
        val under = mb.position - n * (mb.radius * 0.97f)
        val last = glowStampAt
        if (mb.velocity.length() > mb.radius * 0.5f && (last == null || (under - last).length() > mb.radius * 0.35f)) {
            glowStampAt = under
            glowTrail.add(under, n, mb.radius * 0.95f, time)
        }
    }

    private var sporeBurstAcc = 0f
    private var fireAcc = 0f
    private var smokeAcc = 0f

    /** Lava level: flames licking up from braziers, spires and vents near the ball, with dark smoke rolling off them. */
    private fun lavaAtmosphere(mb: Marble, m: Maze, dt: Float) {
        if (lamps.isEmpty()) return
        val cs = m.cellSize
        val rate = if (profile.particles >= 1) 1f else 0.5f
        fireAcc += dt * rate; smokeAcc += dt * rate
        while (fireAcc > 0.025f) {
            fireAcc -= 0.025f
            val lp = lamps[rnd.nextInt(lamps.size)]
            if ((lp - mb.position).length() > cs * 8f) continue
            val n = mb.surface.normal(lp)
            val j = Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f)
            val col = Vec3.lerp(Vec3(1f, 0.32f, 0.05f), Vec3(1f, 0.8f, 0.25f), rnd.nextFloat())
            sparks.emit(lp + j * (cs * 0.12f), n * (cs * (1.2f + 1.4f * rnd.nextFloat())) + j * (cs * 0.4f), 0.5f + 0.5f * rnd.nextFloat(), col, 0.008f + 0.012f * rnd.nextFloat())
        }
        while (smokeAcc > 0.12f) {
            smokeAcc -= 0.12f
            val lp = lamps[rnd.nextInt(lamps.size)]
            if ((lp - mb.position).length() > cs * 9f) continue
            val n = mb.surface.normal(lp)
            dust.emit(lp + n * (cs * 0.25f), n * (cs * (0.5f + 0.4f * rnd.nextFloat())) + Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f) * (cs * 0.25f),
                2.2f + rnd.nextFloat(), Vec3(0.16f, 0.13f, 0.13f), 0.03f + 0.03f * rnd.nextFloat())
        }
    }

    /** Spores drifting up from the mushrooms and a few fireflies, as in the spore demo. */
    private fun glowAtmosphere(mb: Marble, m: Maze, dt: Float) {
        // every so often a mushroom near the ball puffs a plume of glowing spores high into the air
        sporeBurstAcc += dt
        if (sporeBurstAcc > 0.8f && lamps.isNotEmpty()) {
            sporeBurstAcc = 0f
            val near = lamps.filter { (it - mb.position).length() < m.cellSize * 7f }
            if (near.isNotEmpty()) {
                val lp = near[rnd.nextInt(near.size)]
                val pn = mb.surface.normal(lp)
                val base = when (rnd.nextInt(4)) { 0 -> Vec3(0.55f, 1f, 0.36f); 1 -> Vec3(0.3f, 1f, 0.75f); 2 -> Vec3(0.85f, 1f, 0.35f); else -> Vec3(0.25f, 0.88f, 1f) }
                repeat(if (profile.particles >= 1) 22 else 10) {
                    val j = Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f)
                    sparks.emit(lp, pn * (m.cellSize * (2.2f + 2.5f * rnd.nextFloat())) + j * (m.cellSize * 0.9f), 2.5f + 1.5f * rnd.nextFloat(), base * (0.8f + 0.4f * rnd.nextFloat()), 0.005f + 0.006f * rnd.nextFloat())
                }
            }
        }
        sporeAcc += dt
        if (sporeAcc < 0.09f) return
        sporeAcc = 0f
        val n = mb.surface.normal(mb.position)
        if (lamps.isNotEmpty()) {
            val lp = lamps[rnd.nextInt(lamps.size)]
            if ((lp - mb.position).length() < m.cellSize * 9f) {
                val pn = mb.surface.normal(lp)
                val col = when (lampIdxList.getOrElse(0) { 0 }.let { rnd.nextInt(4) }) { 0 -> Vec3(0.55f, 1f, 0.36f); 1 -> Vec3(0.3f, 1f, 0.75f); 2 -> Vec3(0.85f, 1f, 0.35f); else -> Vec3(0.25f, 0.88f, 1f) }
                val drift = pn * (m.cellSize * 0.18f) + Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f) * (m.cellSize * 0.1f)
                sparks.emit(lp, drift, 4f, col, 0.005f)
            }
        }
        if (rnd.nextFloat() < 0.3f) {                               // a firefly
            val a = Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f)
            val off = (a - n * (a dot n)).normalized() * (m.cellSize * 7f * rnd.nextFloat())
            val p = mb.surface.project(mb.position + off) + n * (m.cellSize * (0.3f + 0.8f * rnd.nextFloat()))
            sparks.emit(p, Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f) * (m.cellSize * 0.12f), 5f, Vec3(0.91f, 1f, 0.54f), 0.0065f)
        }
    }

    /** Leaves keep falling around the ball and pile up where they land. */
    private fun fallLeaves(mb: Marble, m: Maze, dt: Float) {
        // the rolling ball sweeps fallen leaves aside
        val spd = mb.velocity.length() / (m.cellSize * 4f)
        if (spd > 0.02f) {
            val under = mb.position - mb.surface.normal(mb.position) * mb.radius
            leaves.nudge(under, mb.radius * 2.6f, mb.radius * (0.18f + 0.5f * spd.coerceAtMost(1f)), time, 3.6f) { mb.surface.project(it) }
        }
        leafAcc += dt * 16f
        while (leafAcc >= 1f) {
            leafAcc -= 1f
            val n = mb.surface.normal(mb.position)
            val a = Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f)
            val off = (a - n * (a dot n)).normalized() * (m.cellSize * 7f * rnd.nextFloat())
            val p = mb.surface.project(mb.position + off)
            leaves.add(p, mb.surface.normal(p), mb.radius * (0.55f + 0.4f * rnd.nextFloat()), time)
        }
    }

    private fun drawGlowAndLeaves(vpm: FloatArray, cam: Vec3) {
        if (pal.themeId == 11 && haloBuf.count > 0) {
            GLES30.glUseProgram(haloProg)
            GLES30.glUniformMatrix4fv(loc(haloProg, "uVP"), 1, false, vpm, 0)
            GLES30.glUniformMatrix4fv(loc(haloProg, "uModel"), 1, false, model, 0)
            GLES30.glUniform1f(loc(haloProg, "uTime"), time)
            GLES30.glEnable(GLES30.GL_BLEND); GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE)
            GLES30.glDepthMask(false)
            haloBuf.draw(haloProg, loc(haloProg, "uPass"))
            GLES30.glDepthMask(true); GLES30.glDisable(GLES30.GL_BLEND)
            GLES30.glUseProgram(sceneProg)
        }
        if (pal.themeId != 11 && pal.themeId != 12) return
        val prog = if (pal.themeId == 11) glowProg else leafProg
        val buf = if (pal.themeId == 11) glowTrail else leaves
        if (buf.count == 0) return
        GLES30.glUseProgram(prog)
        GLES30.glUniformMatrix4fv(loc(prog, "uVP"), 1, false, vpm, 0)
        GLES30.glUniformMatrix4fv(loc(prog, "uModel"), 1, false, model, 0)
        GLES30.glUniform1f(loc(prog, "uTime"), time)
        GLES30.glUniform3f(loc(prog, "uLightDir"), lightDir.x, lightDir.y, lightDir.z)
        GLES30.glEnable(GLES30.GL_BLEND)
        if (pal.themeId == 11) GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE) else GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        buf.draw(prog, loc(prog, "uPass"))
        GLES30.glDepthMask(true)
        GLES30.glDisable(GLES30.GL_BLEND)
        GLES30.glUseProgram(sceneProg)
    }

    /** Soft powder drifting through the air around the ball on the ice level (after the example's windblown snow). */
    private fun snowfall(mb: Marble, m: Maze) {
        if (profile.particles == 0 && rnd.nextBoolean()) return
        if (rnd.nextFloat() > 0.6f) return
        val n = mb.surface.normal(mb.position)
        repeat(1) {
            val a = Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f)
            val off = (a - n * (a dot n)) * (m.cellSize * 7f)
            val p = mb.surface.project(mb.position + off) + n * (m.cellSize * (0.1f + 0.9f * rnd.nextFloat()))
            val drift = Vec3(windX, windY, 0f) * 0.25f - n * (m.cellSize * (0.12f + 0.2f * rnd.nextFloat()))
            dust.emit(p, drift, 3.2f, Vec3(0.57f, 0.68f, 0.84f), 0.0035f + 0.003f * rnd.nextFloat())
        }
    }

    /** Creates (or drops) the sand / mud height field for the new level; Basic simulation keeps the cheap decal tracks. */
    private fun setupSand(m: Maze) {
        sandField = null
        sandStampAt = null
        if (terrain == Terrain.NONE || simQuality < 1) return
        val f = com.maze3d.core.SandField(m, terrain == Terrain.MUD, if (simQuality >= 2) 22 else 14)
        sandField = f
        if (sandTex == 0) { val id = IntArray(1); GLES30.glGenTextures(1, id, 0); sandTex = id[0] }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sandTex)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_R16F, f.atlasW, f.atlasH, 0, GLES30.GL_RED, GLES30.GL_FLOAT, null)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_CLAMP_TO_EDGE)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_CLAMP_TO_EDGE)
        // start flat: a zero-filled atlas
        val zero = java.nio.ByteBuffer.allocateDirect(f.atlasW * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
        for (row in 0 until f.atlasH) { zero.position(0); GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, 0, row, f.atlasW, 1, GLES30.GL_RED, GLES30.GL_FLOAT, zero) }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
        sandBuf = java.nio.ByteBuffer.allocateDirect(f.tile * f.tile * 4).order(java.nio.ByteOrder.nativeOrder()).asFloatBuffer()
    }

    /** The ball digs the surface as it rolls; slumping and texture upload follow. */
    private fun stampSand(f: com.maze3d.core.SandField, mb: Marble, m: Maze) {
        val n = mb.surface.normal(mb.position)
        val under = mb.position - n * mb.radius
        val spd = mb.velocity.length() / (m.cellSize * 4f)
        val last = sandStampAt
        if (spd > 0.02f && (last == null || (under - last).length() > mb.radius * 0.3f)) {
            sandStampAt = under
            val dir = mb.velocity.normalized()
            f.stamp(mb.cell, under, dir, mb.radius * 1.1f, 0.75f + 0.5f * spd.coerceAtMost(1f))
        }
        f.update(1f / 60f)
        val buf = sandBuf ?: return
        if (f.dirty.isEmpty()) return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, sandTex)
        var n2 = 0
        val it = f.dirty.iterator()
        while (it.hasNext() && n2 < 48) {
            val c = it.next(); it.remove(); n2++
            val (ox, oy) = f.tileOrigin(c)
            buf.clear(); buf.put(f.h, c * f.tile * f.tile, f.tile * f.tile); buf.position(0)
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, ox, oy, f.tile, f.tile, GLES30.GL_RED, GLES30.GL_FLOAT, buf)
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }

    /** Advances the flood / gas / fog and applies its effect on the ball. */
    private fun updateMedium(mb: Marble, m: Maze, dt: Float) {
        val f = field ?: return
        f.update(dt)
        mediumAcc += dt
        if (mediumAcc >= 0.05f) {
            mediumAcc = 0f
            mediumGeo?.let { g -> mediumMesh?.updateVertices(g.update(f.level)) }
        }
        waves?.let { stepWaves(it, f, mb, m, dt) }
        if (f.kind == Medium.GAS && fallTimer <= 0f) {
            val lv = f.levelAt(mb.cell)
            poison = (poison + (if (lv > 0.3f) lv * dt * 0.5f else -dt * 0.35f)).coerceIn(0f, 1f)
            if (poison >= 1f) {
                poison = 0f
                fallTimer = 0.55f; host.onStat("falls", 1f)
                fallFrom = mb.position; fallTo = mb.position
                audio.fall(panOf(mb.position))
                host.onToast("Poisoned by the gas - back to the start")
            }
        }
    }

    /** Rain drops and the ball stir the water; the wave field is stepped and its changed tiles uploaded. */
    private fun stepWaves(wf: com.maze3d.core.WaveField, f: com.maze3d.core.MediumField, mb: Marble, m: Maze, dt: Float) {
        val rn = rain
        if (rn != null && rn.intensity > 0.05f) {
            waveDropAcc += dt * 26f * rn.intensity
            var guard = 0
            while (waveDropAcc >= 1f && guard++ < 10) {
                waveDropAcc -= 1f
                var c = rnd.nextInt(m.cellCount)
                var tries = 0
                while (tries++ < 8 && (m.centers[c] - mb.position).length() > m.cellSize * 9f) c = rnd.nextInt(m.cellCount)
                if ((m.centers[c] - mb.position).length() > m.cellSize * 9f || f.levelAt(c) < 0.15f) continue
                val p = m.mesh.surface.project(m.centers[c] + (m.mesh.vertices[m.mesh.cellVertex(c, 1)] - m.mesh.vertices[m.mesh.cellVertex(c, 0)]) * (rnd.nextFloat() - 0.5f) * 0.7f + (m.mesh.vertices[m.mesh.cellVertex(c, 3)] - m.mesh.vertices[m.mesh.cellVertex(c, 0)]) * (rnd.nextFloat() - 0.5f) * 0.7f)
                wf.disturb(c, p, m.cellSize * 0.07f, -0.010f * (0.5f + rnd.nextFloat()))
            }
        }
        val lv = f.levelAt(mb.cell)
        if (lv > 0.12f && fallTimer <= 0f) {
            val n = mb.surface.normal(mb.position)
            val under = mb.position - n * mb.radius
            val spd = (mb.velocity.length() / (m.cellSize * 4f)).coerceIn(0f, 1.5f)
            // a ball displaces water: a dent where it is, a bow wave pushed ahead of it
            wf.disturb(mb.cell, under, mb.radius * 1.2f, -0.006f * (0.3f + spd) * lv * dt * 60f)
            if (spd > 0.05f) wf.foam(mb.cell, under, mb.radius * 1.05f, (0.25f + 0.9f * spd) * lv * dt * 60f)
            if (spd > 0.08f) {
                val ahead = mb.surface.project(under + mb.velocity.normalized() * (mb.radius * 1.5f))
                wf.disturb(mb.cell, ahead, mb.radius * 0.9f, 0.004f * spd * lv * dt * 60f)
            }
        }
        wf.update(dt)
        val buf = waveBuf ?: return
        if (wf.dirty.isEmpty()) return
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, waveTex)
        var cnt = 0
        val it = wf.dirty.iterator()
        while (it.hasNext() && cnt < 40) {
            val c = it.next(); it.remove(); cnt++
            val (ox, oy) = wf.tileOrigin(c)
            buf.clear(); buf.put(wf.h, c * wf.tile * wf.tile, wf.tile * wf.tile); buf.position(0)
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, ox, oy, wf.tile, wf.tile, GLES30.GL_RED, GLES30.GL_FLOAT, buf)
            foamUpload.add(c)
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, foamTex)
        for (c in foamUpload) {
            val (ox, oy) = wf.tileOrigin(c)
            buf.clear(); buf.put(wf.f, c * wf.tile * wf.tile, wf.tile * wf.tile); buf.position(0)
            GLES30.glTexSubImage2D(GLES30.GL_TEXTURE_2D, 0, ox, oy, wf.tile, wf.tile, GLES30.GL_RED, GLES30.GL_FLOAT, buf)
        }
        foamUpload.clear()
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    }
    private val foamUpload = ArrayList<Int>()

    private fun drawMedium(vpm: FloatArray, cam: Vec3) {
        val f = field ?: return
        val mb = marble
        GLES30.glUseProgram(mediumProg)
        GLES30.glUniformMatrix4fv(loc(mediumProg, "uVP"), 1, false, vpm, 0)
        GLES30.glUniformMatrix4fv(loc(mediumProg, "uModel"), 1, false, model, 0)
        GLES30.glUniform1f(loc(mediumProg, "uGrowT"), growT)
        GLES30.glUniform1f(loc(mediumProg, "uTime"), time)
        GLES30.glUniform1f(loc(mediumProg, "uDoorMode"), 0f)
        GLES30.glUniform3f(loc(mediumProg, "uCam"), cam.x, cam.y, cam.z)
        GLES30.glUniform3f(loc(mediumProg, "uLightDir"), lightDir.x, lightDir.y, lightDir.z)
        GLES30.glUniform1f(loc(mediumProg, "uExposure"), exposure())
        run {                                                  // the fog only stirs while the ball rolls
            val dtF = (time - fogLast).coerceIn(0f, 0.1f); fogLast = time
            val spd = ((mb?.velocity?.length() ?: 0f) / ((maze?.cellSize ?: 0.1f) * 3f)).coerceIn(0f, 1.5f)
            if (!paused) fogClock += dtF * spd * 2.5f
            GLES30.glUniform1f(loc(mediumProg, "uFogT"), fogClock)
        }
        GLES30.glUniform1f(loc(mediumProg, "uLayers"), (mediumGeo?.layers ?: 1).toFloat())
        GLES30.glUniform1f(loc(mediumProg, "uKind"), when (f.kind) { Medium.WATER -> 1f; Medium.GAS -> 2f; else -> 3f })
        val cs = maze?.cellSize ?: 0.1f
        GLES30.glUniform1f(loc(mediumProg, "uScale"), 1f / cs)
        GLES30.glUniform1f(loc(mediumProg, "uNear"), cs * 1.6f)
        val wp = mb?.position ?: Vec3.ZERO
        GLES30.glUniform3f(loc(mediumProg, "uMarbleP"), wp.x, wp.y, wp.z)
        GLES30.glUniform1f(loc(mediumProg, "uMarbleSp"), ((mb?.velocity?.length() ?: 0f) / (cs * 4f)).coerceIn(0f, 1f))
        val wf = waves
        GLES30.glUniform1f(loc(mediumProg, "uWaveOn"), if (wf != null) 1f else 0f)
        if (wf != null) {
            GLES30.glActiveTexture(GLES30.GL_TEXTURE5)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, waveTex)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glUniform1i(loc(mediumProg, "uWaveTex"), 5)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE6)
            GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, foamTex)
            GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
            GLES30.glUniform1i(loc(mediumProg, "uFoamTex"), 6)
            GLES30.glUniform4f(loc(mediumProg, "uWaveP"), wf.t.toFloat(), wf.tile.toFloat(), wf.perRow.toFloat(), 0f)
            GLES30.glUniform2f(loc(mediumProg, "uWaveAtlas"), wf.atlasW.toFloat(), wf.atlasH.toFloat())
        } else { GLES30.glUniform1i(loc(mediumProg, "uWaveTex"), 5); GLES30.glUniform1i(loc(mediumProg, "uFoamTex"), 6) }
        GLES30.glEnable(GLES30.GL_BLEND)
        GLES30.glBlendFunc(GLES30.GL_SRC_ALPHA, GLES30.GL_ONE_MINUS_SRC_ALPHA)
        GLES30.glDepthMask(false)
        mediumMesh?.draw()
        GLES30.glDepthMask(true)
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

    /** Spring pads, oil, conveyors, crushers, sweepers and seesaws acting on the marble. */
    private fun updateFx(hz: Hazards, m: Maze, mb: Marble, dt: Float) {
        val cs = m.cellSize
        fxCooldown -= dt
        var slick = 0f
        for (i in hz.fxKind.indices) {
            if (padKick[i] > 0f) padKick[i] = maxOf(0f, padKick[i] - dt * 3f)
            val c = m.centers[hz.fxCell[i]]
            val rel = mb.position - c
            if (rel.length() > cs * 0.75f) continue
            val n = mb.surface.normal(c)
            val d = fxDirT[i]
            val along = (rel dot d) / cs; val lat = (rel dot fxLat[i]) / cs
            when (hz.fxKind[i]) {
                Hazards.FX_BUMPER -> if (rel.length() < cs * 0.3f && fxCooldown <= 0f) {
                    val v = mb.velocity; val sp = v.length()
                    val dir = if (sp > cs * 0.15f) v * (1f / sp) else (rel - n * (rel dot n)).normalized()
                    mb.kick(dir * (cs * 5.5f) + n * (cs * 0.3f))
                    padKick[i] = 1f; fxCooldown = 0.35f
                    audio.impact(0.7f, panOf(c)); host.onStat("springs", 1f)
                    sparks.burst(c, n, 8, 0.4f, Vec3(1f, 0.5f, 0.4f))
                }
                Hazards.FX_OIL -> if (rel.length() < cs * 0.34f) slick = 1f
                Hazards.FX_CONVEYOR -> if (kotlin.math.abs(along) < 0.5f && kotlin.math.abs(lat) < 0.31f) mb.kick(d * (GRAVITY * 0.38f * dt))
                Hazards.FX_SEESAW -> if (kotlin.math.abs(along) < 0.5f && kotlin.math.abs(lat) < 0.3f)
                    mb.kick(d * (kotlin.math.sin(hz.seesawAngle(i, time)) * GRAVITY * 2.5f * dt))
                Hazards.FX_SWEEPER -> {
                    val a = hz.sweepAngle(i, time)
                    val x = (m.mesh.surface.normal(c) cross d).normalized()
                    val ax = d * kotlin.math.cos(a) + x * kotlin.math.sin(a)             // arm direction
                    val dist = (rel - n * (rel dot n))
                    val t = (dist dot ax).coerceIn(-Hazards.SWEEP_REACH * cs, Hazards.SWEEP_REACH * cs)
                    val off = (dist - ax * t).length()
                    if (off < cs * 0.08f + mb.radius && fxCooldown <= 0f) {
                        val tang = (n cross ax) * (if (t >= 0f) 1f else -1f)            // direction the arm sweeps there
                        mb.kick(tang * (cs * 5f) + n * (cs * 0.25f))
                        fxCooldown = 0.3f
                        audio.impact(0.9f, panOf(c)); host.onImpact(0.6f); host.onStat("sweeper", 1f)
                        sparks.burst(mb.position, n, 8, 0.5f, Vec3(1f, 0.8f, 0.5f))
                    }
                }
                Hazards.FX_CRUSHER -> if (hz.crushH(i, time) > 0.92f && kotlin.math.abs(along) < 0.4f && kotlin.math.abs(lat) < 0.4f) {
                    fallTimer = 0.55f; host.onStat("falls", 1f)
                    audio.impact(1f, panOf(c)); host.onImpact(1f)
                    host.onToast("Crushed!")
                    fallFrom = mb.position; fallTo = c - n * (cs * 0.15f)
                }
            }
        }
        mb.slick = slick
    }

    private val turfTrail = FloatArray(64)
    private var turfHead = 0
    private var turfT = 0f

    // ---- moving props: spinning machines, swaying plants, bobbing animals, flickering flames and circling birds
    private var animInsts = emptyList<SceneBuilder.AnimInst>()
    private val animMeshes = HashMap<PropMesh, GlMesh>()
    private var animLast = 0f
    private val aL = FloatArray(16); private val aT = FloatArray(16); private val aW = FloatArray(16); private val aF = FloatArray(16)
    private fun meshFor(pm: PropMesh): GlMesh = animMeshes.getOrPut(pm) {
        GlMesh(GlMesh.SCENE).also { g ->
            val mb = MeshBuilder()
            for (i in 0 until pm.vertexCount) mb.vertex(Vec3(pm.pos[i * 3], pm.pos[i * 3 + 1], pm.pos[i * 3 + 2]), Vec3(pm.nrm[i * 3], pm.nrm[i * 3 + 1], pm.nrm[i * 3 + 2]), Vec3(pm.col[i * 3], pm.col[i * 3 + 1], pm.col[i * 3 + 2]))
            var q = 0; while (q < pm.idx.size) { mb.tri(pm.idx[q], pm.idx[q + 1], pm.idx[q + 2]); q += 3 }
            g.upload(mb.vertices(), mb.indices())
        }
    }
    private fun drawPart(uModel: Int, frame: FloatArray, local: FloatArray, pm: PropMesh) {
        Matrix.multiplyMM(aT, 0, frame, 0, local, 0)
        Matrix.multiplyMM(aW, 0, model, 0, aT, 0)
        GLES30.glUniformMatrix4fv(uModel, 1, false, aW, 0)
        meshFor(pm).draw()
    }

    private fun drawAnimProps() {
        if (animInsts.isEmpty()) return
        val uModel = loc(sceneProg, "uModel")
        val t = time
        val deg = 57.29578f
        val dtA = (t - animLast).coerceIn(0f, 0.1f); animLast = t
        val ball = marble
        val csA = maze?.cellSize ?: 0.1f
        val ballFast = ball != null && ball.velocity.length() > csA * 0.6f
        for (a in animInsts) {
            val g = ease01((growT - a.delay) / 0.35f)
            if (g <= 0.01f) continue
            val ph = a.phase
            // ball interaction: props shake, hop or (for birds) flap up and away when the ball rolls close by
            if (ball != null && phase == Phase.PLAY) {
                val dx = ball.position.x - a.frame[12]; val dy = ball.position.y - a.frame[13]; val dz = ball.position.z - a.frame[14]
                val reach = csA * (if (a.bird != null) 2.2f else 1.25f)
                if (ballFast && dx * dx + dy * dy + dz * dz < reach * reach) a.react = 1f
            }
            a.react = maxOf(0f, a.react - dtA * (if (a.bird != null) 0.35f else 1.1f))
            val rk = a.react * a.react
            val bd = a.bird
            if (bd != null) {
                val omega = (if (bd.glide) 0.9f else 1.3f) / a.radius
                val ang = t * omega * a.dir + ph
                Matrix.setIdentityM(aL, 0)
                Matrix.translateM(aL, 0, a.radius * kotlin.math.cos(ang), a.radius * kotlin.math.sin(ang), a.height + 0.25f * kotlin.math.sin(t * 0.8f + ph) + 1.8f * rk)
                Matrix.rotateM(aL, 0, (ang + a.dir * PI.toFloat() / 2f) * deg, 0f, 0f, 1f)
                Matrix.rotateM(aL, 0, -a.dir * 14f, 1f, 0f, 0f)
                Matrix.scaleM(aL, 0, bd.size * g, bd.size * g, bd.size * g)
                drawPart(uModel, a.frame, aL, bd.body)
                val burst = maxOf(rk, if (bd.glide) (0.15f + 0.85f * maxOf(0f, kotlin.math.sin(t * 0.7f + ph))) else 1f)
                val flap = if (bd.flap > 0f) 38f * burst * kotlin.math.sin(t * bd.flap * (1f + rk) + ph) else 0f
                System.arraycopy(aL, 0, aF, 0, 16); Matrix.rotateM(aF, 0, flap, 1f, 0f, 0f); drawPart(uModel, a.frame, aF, bd.wingL)
                System.arraycopy(aL, 0, aF, 0, 16); Matrix.rotateM(aF, 0, -flap, 1f, 0f, 0f); drawPart(uModel, a.frame, aF, bd.wingR)
                continue
            }
            val sp = a.spec ?: continue
            for (off in sp.at) {
                Matrix.setIdentityM(aL, 0)
                Matrix.translateM(aL, 0, off.x, off.y, off.z + sp.pivotZ)
                if (rk > 0.001f && sp.whole) {                       // startled: a quick hop and a shake
                    Matrix.translateM(aL, 0, 0f, 0f, 0.12f * rk * kotlin.math.abs(kotlin.math.sin(t * 11f)))
                    Matrix.rotateM(aL, 0, 14f * rk * kotlin.math.sin(t * 17f + ph), 1f, 0f, 0f)
                    Matrix.rotateM(aL, 0, 10f * rk * kotlin.math.sin(t * 13f + ph), 0f, 1f, 0f)
                }
                when (sp.type) {
                    PropLibrary.Anim.SPIN -> Matrix.rotateM(aL, 0, (t * sp.speed * 1.4f + ph) * deg, 0f, 0f, 1f)
                    PropLibrary.Anim.BOB -> {
                        val hop = kotlin.math.abs(kotlin.math.sin(t * sp.speed * 2.6f + ph))
                        Matrix.translateM(aL, 0, 0f, 0f, 0.1f * sp.amp * hop)
                        Matrix.rotateM(aL, 0, 9f * kotlin.math.sin(t * sp.speed * 1.3f + ph), 0f, 0f, 1f)
                        val sq = 0.07f * sp.amp * (1f - hop)
                        Matrix.scaleM(aL, 0, 1f + sq, 1f + sq, 1f - sq * 1.6f)
                    }
                    PropLibrary.Anim.SWAY -> {
                        Matrix.rotateM(aL, 0, sp.amp * 4.5f * kotlin.math.sin(t * sp.speed * 1.3f + ph), 1f, 0f, 0f)
                        Matrix.rotateM(aL, 0, sp.amp * 3.5f * kotlin.math.sin(t * sp.speed * 0.9f + ph * 1.7f), 0f, 1f, 0f)
                    }
                    PropLibrary.Anim.FLICKER -> {
                        val k = ph + off.x * 13f
                        val xy = 1f + 0.12f * kotlin.math.sin(t * 17f * sp.speed + k)
                        val z = 1f + 0.22f * kotlin.math.sin(t * 11.3f * sp.speed + k * 2f) * kotlin.math.sin(t * 7.1f + k)
                        Matrix.rotateM(aL, 0, 6f * kotlin.math.sin(t * 5.3f + k), 1f, 0f, 0f)
                        Matrix.scaleM(aL, 0, xy, xy, if (sp.amp < 0f) -z else z)
                    }
                    PropLibrary.Anim.WOBBLE -> {
                        val k = 0.07f * sp.amp * kotlin.math.sin(t * sp.speed * 4.2f + ph)
                        Matrix.scaleM(aL, 0, 1f + k, 1f + k, 1f - k * 1.8f)
                        Matrix.rotateM(aL, 0, sp.amp * 3f * kotlin.math.sin(t * sp.speed * 2.1f + ph), 1f, 0f, 0f)
                    }
                    PropLibrary.Anim.CROWD -> {
                        // fans bob and cheer; every so often a Mexican wave rolls along the stand (rows rise one after another)
                        val cheer = 0.02f * maxOf(0f, kotlin.math.sin(t * 7f + off.z * 31f + ph))
                        val wave = maxOf(0f, kotlin.math.sin(t * 0.9f + ph - off.z * 6f)).let { val w = it * it * it * it; w * w } * 0.07f
                        Matrix.translateM(aL, 0, 0f, 0f, cheer + wave)
                    }
                    PropLibrary.Anim.SCREW -> {
                        // the nut winds up the thread and back down: height and turn stay locked together like a real screw
                        val u = 0.5f - 0.5f * kotlin.math.cos(t * sp.speed + ph)
                        Matrix.translateM(aL, 0, 0f, 0f, u * sp.amp)
                        Matrix.rotateM(aL, 0, u * sp.amp * 2400f, 0f, 0f, 1f)
                    }
                    PropLibrary.Anim.NOD -> {
                        Matrix.rotateM(aL, 0, 18f * sp.amp * kotlin.math.sin(t * sp.speed * 0.5f + ph), 0f, 0f, 1f)
                        Matrix.rotateM(aL, 0, 6f * sp.amp * kotlin.math.sin(t * sp.speed * 1.6f + ph), 0f, 1f, 0f)
                    }
                }
                Matrix.translateM(aL, 0, 0f, 0f, -sp.pivotZ)
                Matrix.scaleM(aL, 0, g, g, g)
                drawPart(uModel, a.frame, aL, sp.part)
            }
        }
        GLES30.glUniformMatrix4fv(uModel, 1, false, model, 0)
    }

    /** Bright pastel levels are toned down so they do not wash out. */
    private var fogClock = 0f
    // ---- storm: lightning flashes, thunder and wind gusts
    private var stormFlash = 0f
    private var nextStrike = 4f
    private var thunderIn = -1f
    private var gustAmt = 0f
    private var gustT = 0f
    private var nextGust = 6f
    private var gustDir = Vec3(1f, 0f, 0f)
    private fun updateStorm(dt: Float) {
        if (medium != Medium.STORM || phase == Phase.MENU || paused) { stormFlash = 0f; gustAmt = maxOf(0f, gustAmt - dt); return }
        val mb = marble ?: return
        val cs = maze?.cellSize ?: 0.1f
        stormFlash = maxOf(0f, stormFlash - dt * 3.2f)
        nextStrike -= dt
        if (nextStrike <= 0f) {
            stormFlash = 1f; nextStrike = 3.5f + rnd.nextFloat() * 6f
            thunderIn = 0.4f + rnd.nextFloat() * 1.2f
            if (rnd.nextFloat() < 0.4f) nextStrike = 0.18f              // a quick double flicker
        }
        if (thunderIn > 0f) { thunderIn -= dt; if (thunderIn <= 0f) { audio.impact(1f, rnd.nextFloat() * 2f - 1f); host.onImpact(0.35f) } }
        nextGust -= dt
        if (nextGust <= 0f && gustT <= 0f) {
            gustT = 2.2f + rnd.nextFloat() * 1.5f; nextGust = 7f + rnd.nextFloat() * 6f
            gustDir = Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f).normalized()
            if (phase == Phase.PLAY) host.onToast("Gust of wind!")
        }
        if (gustT > 0f) { gustT -= dt; gustAmt += (1f - gustAmt) * minOf(1f, dt * 3f) } else gustAmt += (0f - gustAmt) * minOf(1f, dt * 2f)
        if (gustAmt > 0.15f && phase == Phase.PLAY) {                      // streaks of spray blown past the ball show the wind
            val n = mb.surface.normal(mb.position)
            val w = (gustDir - n * (gustDir dot n)).normalized()
            repeat(2) {
                val j = Vec3(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f)
                sparks.emit(mb.position - w * (cs * 2f) + (j - n * (j dot n)) * (cs * 3f) + n * (cs * (0.2f + 0.5f * rnd.nextFloat())), w * (cs * 5f * gustAmt), 0.5f, Vec3(0.55f, 0.62f, 0.75f), 0.004f)
            }
        }
    }
    private var followAim: Vec3? = null
    private var followDirS: Vec3? = null
    private var followPickT = -10f
    private var cutAmt = 0f
    private var fogLast = 0f

    private fun exposure() = (if (medium == Medium.STORM) 0.85f * (1f + 2.4f * stormFlash) else 1f) * when (pal.themeId) { 0 -> 0.66f; 6 -> 0.64f; 18 -> 0.62f; 12, 13, 14, 5, 2, 3 -> 0.8f; 7, 9, 11, 17 -> 1f; else -> 0.86f }

    private fun studPitch() = (maze?.marbleRadius ?: 0.02f) * 1.7f

    private val objM = FloatArray(16)
    private val swingM = FloatArray(16)
    private val boardM = FloatArray(16)

    /** Plank bridges and pendulum hammers: small meshes drawn with their own transform each frame. */
    private fun drawHazardObjects() {
        val hz = hazards ?: return
        if (!hz.hasBridges && !hz.hasHammers && !hz.hasSwings && !hz.hasFx && keyGates == null) return
        val uModel = loc(sceneProg, "uModel")
        scene(Mat.PROP, 0.55f, 0f, 0f)
        val cs = maze?.cellSize ?: 0.1f
        for (i in bridgeFrames.indices) {
            val f = bridgeFrames[i].copyOf()
            val off = hz.bridgeOffset(i, time)          // across the corridor, in cells
            val lat = bridgeLat[i]
            f[12] += lat.x * off * cs; f[13] += lat.y * off * cs; f[14] += lat.z * off * cs
            android.opengl.Matrix.multiplyMM(objM, 0, model, 0, f, 0)
            GLES30.glUniformMatrix4fv(uModel, 1, false, objM, 0)
            bridgeMesh?.draw()
            for (k in 0 until Hazards.BOARDS) {                  // boards shrink away and come back: the gaps are the hazard
                val sc = hz.boardScale(i, k, time)
                if (sc < 0.05f) continue
                System.arraycopy(f, 0, boardM, 0, 16)
                android.opengl.Matrix.translateM(boardM, 0, -0.5f + (k + 0.5f) / Hazards.BOARDS, 0f, 0f)
                android.opengl.Matrix.scaleM(boardM, 0, sc, sc, sc)
                android.opengl.Matrix.multiplyMM(objM, 0, model, 0, boardM, 0)
                GLES30.glUniformMatrix4fv(uModel, 1, false, objM, 0)
                bridgeBoardMesh?.draw()
            }
        }
        for (i in hammerFrames.indices) {
            android.opengl.Matrix.multiplyMM(objM, 0, model, 0, hammerFrames[i], 0)
            GLES30.glUniformMatrix4fv(uModel, 1, false, objM, 0)
            gantryMesh?.draw()
            // swing about the lateral (y) axis at the pivot
            val a = hz.hammerAngle(i, time)
            val ca = kotlin.math.cos(a); val sa = kotlin.math.sin(a)
            android.opengl.Matrix.setIdentityM(swingM, 0)
            swingM[0] = ca; swingM[2] = -sa; swingM[8] = sa; swingM[10] = ca; swingM[14] = HazardModels.HAMMER_PIVOT
            val tmp = FloatArray(16)
            android.opengl.Matrix.multiplyMM(tmp, 0, objM, 0, swingM, 0)
            GLES30.glUniformMatrix4fv(uModel, 1, false, tmp, 0)
            hammerMesh?.draw()
        }
        for (i in swingFrames.indices) {
            android.opengl.Matrix.multiplyMM(objM, 0, model, 0, swingFrames[i], 0)
            GLES30.glUniformMatrix4fv(uModel, 1, false, objM, 0)
            swingGantryMesh?.draw()
            val x = hz.swingX(i, time)
            val a = -kotlin.math.asin((x / HazardModels.SWING_LENGTH).coerceIn(-0.99f, 0.99f))
            val ca = kotlin.math.cos(a); val sa = kotlin.math.sin(a)
            android.opengl.Matrix.setIdentityM(swingM, 0)
            swingM[0] = ca; swingM[2] = -sa; swingM[8] = sa; swingM[10] = ca; swingM[14] = HazardModels.SWING_LENGTH
            val tmp = FloatArray(16)
            android.opengl.Matrix.multiplyMM(tmp, 0, objM, 0, swingM, 0)
            GLES30.glUniformMatrix4fv(uModel, 1, false, tmp, 0)
            swingSeatMesh?.draw()
        }
        if (hz.hasFx) drawFx(hz, uModel)
        drawKeys(uModel)
        GLES30.glUniformMatrix4fv(uModel, 1, false, model, 0)
    }

    private fun drawKeys(uModel: Int) {
        val kg = keyGates ?: return
        val tmp = FloatArray(16); val m2 = FloatArray(16)
        scene(Mat.PROP, 0.3f, 0.5f, 0.35f)
        for (i in kg.keyCells.indices) {
            if (kg.keyTaken[i]) continue
            val a = time * 1.6f + i; val ca = kotlin.math.cos(a); val sa = kotlin.math.sin(a)
            android.opengl.Matrix.setIdentityM(m2, 0)
            m2[0] = ca; m2[1] = sa; m2[4] = -sa; m2[5] = ca; m2[14] = 0.04f * kotlin.math.sin(time * 2.2f + i)
            android.opengl.Matrix.multiplyMM(tmp, 0, keyFrames[i], 0, m2, 0)
            android.opengl.Matrix.multiplyMM(objM, 0, model, 0, tmp, 0)
            GLES30.glUniformMatrix4fv(uModel, 1, false, objM, 0)
            keyMeshes[i % keyMeshes.size]?.draw()
        }
    }

    private fun drawFx(hz: Hazards, uModel: Int) {
        val tmp = FloatArray(16); val m2 = FloatArray(16)
        fun put(base: FloatArray, local: FloatArray, mesh: GlMesh?) {
            android.opengl.Matrix.multiplyMM(tmp, 0, base, 0, local, 0)
            android.opengl.Matrix.multiplyMM(objM, 0, model, 0, tmp, 0)
            GLES30.glUniformMatrix4fv(uModel, 1, false, objM, 0)
            mesh?.draw()
        }
        val id = FloatArray(16).also { android.opengl.Matrix.setIdentityM(it, 0) }
        for (i in hz.fxKind.indices) {
            val f = fxFrames[i]
            when (hz.fxKind[i]) {
                Hazards.FX_BUMPER -> {
                    scene(Mat.PROP, 0.45f, 0f, 0f)
                    System.arraycopy(id, 0, m2, 0, 16); val sq = 1f - 0.5f * padKick[i]; m2[10] = sq
                    put(f, m2, fxMeshes[0])
                }
                Hazards.FX_OIL -> { scene(Mat.PROP, 0.04f, 0.9f, 0f); put(f, id, fxMeshes[1]) }
                Hazards.FX_CONVEYOR -> {
                    scene(Mat.PROP, 0.6f, 0.1f, 0f); put(f, id, fxMeshes[2])
                    scene(Mat.PROP, 0.5f, 0f, 0.6f)
                    for (k in 0 until 3) {
                        val ph = ((time * 0.55f + k / 3f) % 1f)
                        val sc = kotlin.math.sin(ph * Math.PI.toFloat()).coerceIn(0f, 1f)
                        System.arraycopy(id, 0, m2, 0, 16)
                        m2[12] = -0.4f + ph * 0.8f; m2[0] = sc; m2[5] = sc
                        put(f, m2, fxMeshes[3])
                    }
                }
                Hazards.FX_CRUSHER -> {
                    scene(Mat.PROP, 0.5f, 0.3f, 0f); put(f, id, fxMeshes[4])
                    System.arraycopy(id, 0, m2, 0, 16); m2[14] = 0.75f - 0.62f * hz.crushH(i, time)
                    put(f, m2, fxMeshes[5])
                }
                Hazards.FX_SWEEPER -> {
                    scene(Mat.PROP, 0.5f, 0.2f, 0f)
                    val a = hz.sweepAngle(i, time); val ca = kotlin.math.cos(a); val sa = kotlin.math.sin(a)
                    System.arraycopy(id, 0, m2, 0, 16); m2[0] = ca; m2[1] = sa; m2[4] = -sa; m2[5] = ca
                    put(f, m2, fxMeshes[6])
                }
                Hazards.FX_SEESAW -> {
                    scene(Mat.PROP, 0.6f, 0.2f, 0f); put(f, id, fxMeshes[7])
                    val a = hz.seesawAngle(i, time); val ca = kotlin.math.cos(a); val sa = kotlin.math.sin(a)
                    System.arraycopy(id, 0, m2, 0, 16); m2[0] = ca; m2[2] = -sa; m2[8] = sa; m2[10] = ca
                    put(f, m2, fxMeshes[8])
                }
            }
        }
    }

    private fun drawHoles(vpm: FloatArray, cam: Vec3) {
        val hz = hazards ?: return
        if (!hz.hasHoles && !hz.hasBridges && !hz.hasSwings) return
        GLES30.glUseProgram(holeProg)
        GLES30.glUniformMatrix4fv(loc(holeProg, "uVP"), 1, false, vpm, 0)
        GLES30.glUniformMatrix4fv(loc(holeProg, "uModel"), 1, false, model, 0)
        GLES30.glUniform1f(loc(holeProg, "uGrowT"), 1.35f)
        GLES30.glUniform1f(loc(holeProg, "uTime"), time)
        GLES30.glUniform1f(loc(holeProg, "uDoorMode"), 0f)
        GLES30.glUniform3f(loc(holeProg, "uAccent"), pal.accent.x, pal.accent.y, pal.accent.z)
        GLES30.glUniform3f(loc(holeProg, "uLightDir"), lightDir.x, lightDir.y, lightDir.z)
        holeMesh?.draw(); bridgePitMesh?.draw(); swingPitMesh?.draw()
        GLES30.glUseProgram(lidProg)
        GLES30.glUniformMatrix4fv(loc(lidProg, "uVP"), 1, false, vpm, 0)
        GLES30.glUniformMatrix4fv(loc(lidProg, "uModel"), 1, false, model, 0)
        GLES30.glUniform1f(loc(lidProg, "uTime"), time)
        GLES30.glUniform3f(loc(lidProg, "uCam"), cam.x, cam.y, cam.z)
        GLES30.glUniform3f(loc(lidProg, "uAccent"), pal.accent.x, pal.accent.y, pal.accent.z)
        GLES30.glUniform3f(loc(lidProg, "uLightDir"), lightDir.x, lightDir.y, lightDir.z)
        lidMesh?.draw()
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
