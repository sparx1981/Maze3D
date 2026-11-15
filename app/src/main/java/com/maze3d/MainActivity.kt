package com.maze3d

import android.app.Activity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import kotlin.math.atan2
import kotlin.math.acos
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.maze3d.audio.Audio
import com.maze3d.audio.SoundEngine
import com.maze3d.core.Medium
import com.maze3d.core.ShapeKind
import com.maze3d.core.Terrain
import com.maze3d.core.Vec3
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import com.maze3d.gl.MazeRenderer

class MainActivity : Activity(), MazeRenderer.Host, SensorEventListener {

    private lateinit var engine: SoundEngine
    private var loading: LoadingView? = null
    private lateinit var joystick: JoystickView
    private var cameraMode = 2
    private var touchSens = 1f
    private var gyroSens = 1f
    private var qualityLevel = 2
    private var simLevel = 1
    private lateinit var menuSettings: LinearLayout
    private var camPill: TextView? = null
    private var joyOriginX = 0f
    private var joyOriginY = 0f
    private lateinit var hudLayer: View
    private lateinit var menuLayer: FrameLayout
    private lateinit var menuMain: LinearLayout
    private lateinit var menuLevels: LinearLayout
    private var resumeBtn: TextView? = null
    private var gameStarted = false
    private var selShape = 0      // 0 random, 1 sphere, 2 torus, 3 cube
    private var selSurface = 0    // 0 random, 1 standard, 2 sand, 3 mud
    private var selHazards = 0    // 0 random, 1 none
    @Volatile private var rendererReady = false
    override val audio: Audio get() = engine

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: MazeRenderer
    private lateinit var hud: TextView
    private lateinit var banner: TextView
    private lateinit var sensors: SensorManager
    private var gravitySensor: Sensor? = null
    private var baseline: Vec3? = null

    private var twistAngle = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var lastT = 0L
    private var multiTouch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        engine = SoundEngine(applicationContext)
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        gravitySensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        renderer = MazeRenderer(this)
        val ui = getSharedPreferences("maze3d_ui", MODE_PRIVATE)
        cameraMode = ui.getInt("camera2", 2)                     // chase is the default view
        touchSens = ui.getFloat("touchSens", 1f)
        gyroSens = ui.getFloat("gyroSens", 1f)
        qualityLevel = ui.getInt("quality", 2)
        simLevel = ui.getInt("sim", 1)
        renderer.simQuality = simLevel
        renderer.setCameraMode(cameraMode)
        renderer.touchSens = touchSens
        renderer.gyroSens = gyroSens
        renderer.setQualityLevel(qualityLevel)
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(MsaaChooser())
            preserveEGLContextOnPause = true
            setRenderer(renderer)
        }
        glView.setOnTouchListener { _, e -> onTouch(e) }

        val root = FrameLayout(this)
        root.addView(glView)
        hudLayer = buildOverlay()
        root.addView(hudLayer)
        joystick = JoystickView(this)
        root.addView(joystick, FrameLayout.LayoutParams(-1, -1))
        root.addView(buildMenu())
        loading = LoadingView(this).also { root.addView(it, FrameLayout.LayoutParams(-1, -1)) }
        setContentView(root)
        requestHighRefresh()
        val h = Handler(Looper.getMainLooper())
        h.post(object : Runnable {
            override fun run() {
                val lv = loading ?: return
                val fadeStart = 3.2f
                if (rendererReady && lv.seconds >= fadeStart) {
                    val k = ((lv.seconds - fadeStart) / 0.5f).coerceIn(0f, 1f)
                    lv.alpha = 1f - k
                    if (k >= 1f) { (lv.parent as? android.view.ViewGroup)?.removeView(lv); loading = null; showMenu(); return }
                }
                h.postDelayed(this, 16)
            }
        })
        if (Build.VERSION.SDK_INT >= 28) window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode = android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        @Suppress("DEPRECATION")
        root.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION)
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private var accent = Color.rgb(120, 200, 255)
    private val toggles = ArrayList<Pair<TextView, () -> Boolean>>()

    private fun glass(active: Boolean) = GradientDrawable().apply {
        cornerRadius = dp(20f).toFloat()
        if (active) {
            setColor(Color.argb(210, Color.red(accent), Color.green(accent), Color.blue(accent)))
            setStroke(dp(1f), Color.argb(160, 255, 255, 255))
        } else {
            setColor(Color.argb(110, 12, 14, 20))
            setStroke(dp(1f), Color.argb(60, 255, 255, 255))
        }
    }

    private fun pill(text: String, toggle: (() -> Boolean)? = null, onClick: (TextView) -> Unit): TextView = TextView(this).apply {
        this.text = text.uppercase()
        setTextColor(Color.WHITE)
        textSize = 11.5f
        letterSpacing = 0.12f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setPadding(dp(11f), dp(10f), dp(11f), dp(10f))
        background = glass(toggle?.invoke() ?: false)
        if (toggle != null) toggles.add(this to toggle)
        setOnClickListener {
            onClick(this)
            toggle?.let { t -> background = glass(t()) }
            if (toggle != null) engine.uiToggle(toggle()) else engine.uiClick()
            performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        }
    }

    private fun refreshToggles() { for ((v, t) in toggles) v.background = glass(t()) }

    private lateinit var toast: TextView
    private var hintPill: TextView? = null
    private lateinit var title: TextView
    private lateinit var clock: TextView
    private lateinit var finishName: TextView

    private fun buildOverlay(): View {
        val layer = FrameLayout(this)
        val sans = Typeface.create("sans-serif-medium", Typeface.NORMAL)

        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        title = TextView(this).apply {
            setTextColor(Color.argb(230, 255, 255, 255)); textSize = 12f; letterSpacing = 0.22f; typeface = sans
            setShadowLayer(8f, 0f, 2f, Color.argb(160, 0, 0, 0))
        }
        clock = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 34f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(12f, 0f, 3f, Color.argb(170, 0, 0, 0))
        }
        finishName = TextView(this).apply {
            setTextColor(Color.argb(150, 255, 255, 255)); textSize = 11f; letterSpacing = 0.08f; typeface = sans
            setShadowLayer(8f, 0f, 2f, Color.argb(160, 0, 0, 0))
        }
        col.addView(title); col.addView(clock); col.addView(finishName)
        layer.addView(col, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(22f), dp(34f), 0, 0)
        })

        banner = TextView(this).apply {
            text = "SOLVED"
            setTextColor(Color.WHITE); textSize = 30f; letterSpacing = 0.35f
            typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
            setShadowLayer(18f, 0f, 4f, Color.argb(200, 0, 0, 0))
            alpha = 0f
        }
        layer.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        toast = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; letterSpacing = 0.04f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(18f), dp(10f), dp(18f), dp(10f))
            background = GradientDrawable().apply { cornerRadius = dp(20f).toFloat(); setColor(Color.argb(190, 120, 30, 30)) }
            alpha = 0f
        }
        layer.addView(toast, FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { setMargins(0, 0, 0, dp(100f)) })

        val slider = ZoomSlider(this) { v -> glView.queueEvent { renderer.setZoomLevel(v) } }
        layer.addView(slider, FrameLayout.LayoutParams(dp(48f), dp(260f), Gravity.END or Gravity.CENTER_VERTICAL).apply { setMargins(0, 0, dp(6f), 0) })

        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        val menuBtn = pill("Menu") { showMenu() }
        for (v in listOf(menuBtn)) column.addView(v, LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 0, 0, dp(8f)) })
        layer.addView(column, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(36f), dp(18f), 0) })

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val tilt = pill("Tilt", { renderer.tiltMode }) { setTilt(!renderer.tiltMode) }
        val hint = pill("Hint", { renderer.hintActive }) { glView.queueEvent { renderer.requestHint() } }
        hintPill = hint
        val camNames = arrayOf("Cam: Orbit", "Cam: Follow", "Cam: Chase")
        val follow = pill(camNames[cameraMode], { cameraMode == 2 }) { v ->
            cameraMode = (cameraMode + 1) % 3
            v.text = camNames[cameraMode].uppercase()
            getSharedPreferences("maze3d_ui", MODE_PRIVATE).edit().putInt("camera2", cameraMode).apply()
            val m = cameraMode
            glView.queueEvent { renderer.setCameraMode(m) }
            if (m != 2) joystick.hide()
            toastText(when (m) { 0 -> "Orbit camera: drag to turn the maze"; 1 -> "Follow camera: the view tracks the ball"; else -> "Chase camera: drag to steer, twist to look around" })
        }
        camPill = follow
        val ball = pill("Ball") { glView.queueEvent { renderer.resetMarble() } }
        val next = pill("New") { glView.queueEvent { renderer.requestNew() } }
        for (b in listOf(tilt, hint, follow, ball, next)) {
            bar.addView(b, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(3f), 0, dp(3f), 0) })
        }
        layer.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(0, 0, 0, dp(30f)) })
        return layer
    }

    // ---- touch: drag to rotate, pinch to zoom -------------------------------------------
    private fun onTouch(e: MotionEvent): Boolean {
        if (cameraMode == 2) return onChaseTouch(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y; lastT = e.eventTime; multiTouch = false
                glView.queueEvent { renderer.dragStart() }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                if (e.pointerCount == 2) twistAngle = pointerAngle(e)
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2) {
                    // two fingers twist the model about the view axis
                    val a = pointerAngle(e)
                    var d = a - twistAngle
                    if (d > Math.PI) d -= (2 * Math.PI).toFloat()
                    if (d < -Math.PI) d += (2 * Math.PI).toFloat()
                    twistAngle = a
                    glView.queueEvent { renderer.twist(-d) }
                } else if (!multiTouch) {
                    val dx = e.x - lastX; val dy = e.y - lastY
                    val dt = (e.eventTime - lastT) / 1000f
                    glView.queueEvent { renderer.drag(dx, dy, dt) }
                }
                lastX = e.x; lastY = e.y; lastT = e.eventTime
            }
            MotionEvent.ACTION_POINTER_UP -> {
                if (e.pointerCount == 3) twistAngle = pointerAngle(e)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> glView.queueEvent { renderer.dragEnd() }
        }
        return true
    }

    /** Chase camera: one finger is a steering stick (up = forward), two fingers twist the camera round the ball. */
    private fun onChaseTouch(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                multiTouch = false
                joyOriginX = e.x; joyOriginY = e.y
                joystick.show(e.x, e.y)
                glView.queueEvent { renderer.setChaseInput(0f, 0f, true) }
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                multiTouch = true
                joystick.hide()
                glView.queueEvent { renderer.setChaseInput(0f, 0f, false) }
                if (e.pointerCount == 2) twistAngle = pointerAngle(e)
            }
            MotionEvent.ACTION_MOVE -> {
                if (e.pointerCount >= 2) {
                    val a = pointerAngle(e)
                    var d = a - twistAngle
                    if (d > Math.PI) d -= (2 * Math.PI).toFloat()
                    if (d < -Math.PI) d += (2 * Math.PI).toFloat()
                    twistAngle = a
                    glView.queueEvent { renderer.chaseYawBy(d) }
                } else if (!multiTouch) {
                    val r = joystick.radius / touchSens
                    var ix = (e.x - joyOriginX) / r
                    var iy = -(e.y - joyOriginY) / r
                    val len = kotlin.math.sqrt(ix * ix + iy * iy)
                    if (len > 1f) { ix /= len; iy /= len }
                    joystick.move(e.x, e.y)
                    glView.queueEvent { renderer.setChaseInput(ix, iy, true) }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                joystick.hide()
                glView.queueEvent { renderer.setChaseInput(0f, 0f, false) }
            }
        }
        return true
    }

    private fun pointerAngle(e: MotionEvent) = atan2(e.getY(1) - e.getY(0), e.getX(1) - e.getX(0))

    // ---- tilt control -------------------------------------------------------------------
    private fun setTilt(on: Boolean) {
        renderer.tiltMode = on
        sensors.unregisterListener(this)
        if (on) {
            baseline = null
            smoothed = null
            gravitySensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        } else {
            renderer.deviceGravity = Vec3(0f, -1f, 0f)
        }
    }

    private var smoothed: Vec3? = null

    override fun onSensorChanged(ev: SensorEvent) {
        // Device axes equal screen axes (portrait locked). Gravity "down" is minus the sensor vector.
        val raw = Vec3(-ev.values[0], -ev.values[1], -ev.values[2]).normalized()
        val down = smoothed?.let { Vec3.lerp(it, raw, 0.3f).normalized() } ?: raw
        smoothed = down
        // Whatever angle the phone is held at when Tilt is enabled counts as "level".
        val base = baseline ?: down.also { baseline = it }
        val dev = com.maze3d.core.Quat.fromTo(base, down)
        val angle = 2f * acos(dev.w.coerceIn(-1f, 1f))
        val axis = Vec3(dev.x, dev.y, dev.z)
        // Amplify: a gentle phone tilt becomes a strong push on the marble.
        val boosted = if (axis.lengthSq() < 1e-10f) com.maze3d.core.Quat.IDENTITY
            else com.maze3d.core.Quat.fromAxisAngle(axis, minOf(angle * TILT_GAIN * gyroSens, 2.0f))
        renderer.deviceGravity = boosted.rotate(Vec3(0f, -1f, 0f))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---- menu ----------------------------------------------------------------------------------
    private fun bigButton(text: String, primary: Boolean = false, onClick: () -> Unit): TextView = TextView(this).apply {
        this.text = text.uppercase()
        setTextColor(Color.WHITE)
        textSize = 15f
        letterSpacing = 0.2f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setPadding(dp(20f), dp(15f), dp(20f), dp(15f))
        background = glass(primary)
        setOnClickListener { engine.uiClick(); onClick() }
    }

    private fun sectionLabel(text: String) = TextView(this).apply {
        this.text = text.uppercase()
        setTextColor(Color.argb(170, 255, 255, 255))
        textSize = 11f; letterSpacing = 0.25f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setPadding(dp(4f), dp(18f), 0, dp(8f))
    }

    /** A row of single-choice chips. */
    private var noPreview = false
    private fun settingsChips(o: List<String>, get: () -> Int, set: (Int) -> Unit): LinearLayout { noPreview = true; return chipRow(o, get, set) }

    /** Slider for a 0.4x .. 2.5x sensitivity with its current value shown beside it. */
    private fun sensitivitySlider(get: () -> Float, set: (Float) -> Unit): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val value = TextView(this).apply { setTextColor(Color.WHITE); textSize = 13f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); minWidth = dp(44f); gravity = Gravity.END }
        fun toP(v: Float) = (Math.log((v / 0.4f).toDouble()) / Math.log(2.5 / 0.4) * 100).toInt().coerceIn(0, 100)
        fun fromP(p: Int) = (0.4 * Math.pow(2.5 / 0.4, p / 100.0)).toFloat()
        val bar = android.widget.SeekBar(this).apply {
            max = 100; progress = toP(get())
            progressTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            thumbTintList = android.content.res.ColorStateList.valueOf(Color.WHITE)
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, p: Int, fromUser: Boolean) {
                    val v = fromP(p); value.text = "%.1fx".format(v)
                    if (fromUser) set(v)
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) { engine.uiClick() }
            })
        }
        value.text = "%.1fx".format(get())
        row.addView(bar, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(value, LinearLayout.LayoutParams(-2, -2))
        return row
    }

    private fun chipRow(options: List<String>, get: () -> Int, set: (Int) -> Unit): LinearLayout = chipGrid(options, options.size.coerceAtLeast(1), get, set)

    private fun chipGrid(options: List<String>, columns: Int, get: () -> Int, set: (Int) -> Unit): LinearLayout {
        val usePreview = !noPreview
        noPreview = false
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = ArrayList<TextView>()
        fun refresh() { chips.forEachIndexed { i, c -> c.background = glass(get() == i) } }
        options.forEachIndexed { i, label ->
            if (i > 0 && i % columns == 0) { grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(6f)) }); row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL } }
            val c = TextView(this).apply {
                text = label.uppercase(); setTextColor(Color.WHITE); textSize = 12f; letterSpacing = 0.1f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(dp(4f), dp(12f), dp(4f), dp(12f))
                background = glass(get() == i)
                setOnClickListener { set(i); refresh(); engine.uiClick(); if (usePreview) previewSelection() }
            }
            chips.add(c)
            row.addView(c, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3f), 0, dp(3f), 0) })
        }
        // pad the last row so the chips keep their width
        while (options.size % columns != 0 && row.childCount < columns) { row.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)); if (row.childCount == columns) break }
        grid.addView(row, LinearLayout.LayoutParams(-1, -2))
        return grid
    }

    private fun selectedShape(): ShapeKind? = if (selShape == 0) null else ShapeKind.values()[selShape - 1]
    private fun selectedTerrain(): Terrain? = when (selSurface) { 0 -> null; 2 -> Terrain.SAND; 3 -> Terrain.MUD; else -> Terrain.NONE }
    private fun selectedMedium(): Medium? = when (selSurface) { 0 -> null; 4 -> Medium.WATER; 5 -> Medium.FOG; 6 -> Medium.GAS; else -> Medium.NONE }
    /** null = random, -1 = a classic finish, 0.. = surface theme. */
    private fun selectedTheme(): Int? = when (selSurface) { 0 -> null; in 1..6 -> -1; else -> selSurface - 7 }

    /** Show the chosen level behind the menu as soon as an option changes. */
    private fun previewSelection() {
        val sh = selectedShape(); val tr = selectedTerrain(); val th = selectedTheme(); val md = selectedMedium(); val plain = selHazards == 1
        gameStarted = false          // previewing replaces the level, so there is nothing to resume any more
        glView.queueEvent { renderer.previewLevel(sh, tr, plain, th, md) }
    }

    private fun buildMenu(): View {
        menuLayer = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(150, 4, 6, 12)); isClickable = true; visibility = View.GONE
        }
        val sans = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        fun header(sub: String): LinearLayout {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            box.addView(TextView(this).apply {
                text = "ROLLABYRINTH"; setTextColor(Color.WHITE); textSize = 30f; letterSpacing = 0.22f
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                setShadowLayer(16f, 0f, 3f, Color.argb(180, 0, 0, 0))
            })
            box.addView(TextView(this).apply {
                text = sub.uppercase(); setTextColor(Color.argb(170, 255, 255, 255)); textSize = 11f; letterSpacing = 0.35f
                typeface = sans; setPadding(0, dp(6f), 0, dp(26f))
            })
            return box
        }
        val maxW = dp(340f)

        // ---- main screen
        menuMain = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        menuMain.addView(header("3D marble maze"))
        resumeBtn = bigButton("Resume", primary = true) { hideMenu(); glView.queueEvent { renderer.paused = false } }
        menuMain.addView(resumeBtn, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(12f)) })
        menuMain.addView(bigButton("Quick start", primary = true) { startGame(null, null, false) },
            LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(12f)) })
        menuMain.addView(bigButton("Select level") { showLevelSelect() }, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(12f)) })
        menuMain.addView(bigButton("Settings") { showSettings() }, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(12f)) })
        menuMain.addView(bigButton("Exit") { finishAffinity() }, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(6f)) })

        // ---- level select screen
        menuLevels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        menuLevels.addView(header("select level"))
        val panel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        panel.addView(sectionLabel("Shape"))
        panel.addView(chipGrid(listOf("Random") + ShapeKind.values().map { it.label.replace("Rounded ", "").replace("Trefoil knot", "Knot") }, 4, { selShape }, { selShape = it }))
        panel.addView(sectionLabel("Surface"))
        panel.addView(chipGrid(listOf("Random", "Classic", "Sand", "Mud", "Water", "Fog", "Gas", "Candy", "Dead", "Racing", "Football", "Space", "Food", "Science", "Ice", "Egypt", "Neon", "Jungle"), 4, { selSurface }, { selSurface = it }))
        panel.addView(sectionLabel("Hazards"))
        panel.addView(chipRow(listOf("Random", "None"), { selHazards }) { selHazards = it })
        menuLevels.addView(panel, LinearLayout.LayoutParams(maxW, -2))
        menuLevels.addView(bigButton("Start", primary = true) { startGame(selectedShape(), selectedTerrain(), selHazards == 1, selectedTheme(), selectedMedium()) },
            LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, dp(28f), 0, dp(12f)) })
        menuLevels.addView(bigButton("Back") { showMainMenu() }, LinearLayout.LayoutParams(maxW, -2))

        // ---- settings screen (the only place these options live)
        menuSettings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        menuSettings.addView(header("settings"))
        val sp = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        sp.addView(sectionLabel("Gyro sensitivity"))
        sp.addView(sensitivitySlider({ gyroSens }) { gyroSens = it; renderer.gyroSens = it; getSharedPreferences("maze3d_ui", MODE_PRIVATE).edit().putFloat("gyroSens", it).apply() })
        sp.addView(sectionLabel("Touch sensitivity"))
        sp.addView(sensitivitySlider({ touchSens }) { touchSens = it; renderer.touchSens = it; getSharedPreferences("maze3d_ui", MODE_PRIVATE).edit().putFloat("touchSens", it).apply() })
        sp.addView(sectionLabel("Music"))
        sp.addView(settingsChips(listOf("On", "Off"), { if (engine.musicOn) 0 else 1 }) { engine.setMusic(it == 0) })
        sp.addView(sectionLabel("Sound effects"))
        sp.addView(settingsChips(listOf("On", "Off"), { if (engine.sfxOn) 0 else 1 }) { engine.setSfx(it == 0) })
        sp.addView(sectionLabel("Quality"))
        sp.addView(settingsChips(listOf("Low", "Medium", "High"), { qualityLevel }) {
            qualityLevel = it
            getSharedPreferences("maze3d_ui", MODE_PRIVATE).edit().putInt("quality", it).apply()
            glView.queueEvent { renderer.setQualityLevel(it) }
        })
        sp.addView(sectionLabel("Simulation detail (sand, mud, water, gas)"))
        sp.addView(settingsChips(listOf("Basic", "Detailed", "Ultra"), { simLevel }) {
            simLevel = it
            getSharedPreferences("maze3d_ui", MODE_PRIVATE).edit().putInt("sim", it).apply()
            renderer.simQuality = it
        })
        menuSettings.addView(sp, LinearLayout.LayoutParams(maxW, -2))
        menuSettings.addView(bigButton("Back") { showMainMenu() }, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, dp(28f), 0, 0) })

        val holder = FrameLayout(this)
        holder.addView(menuMain, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        holder.addView(menuLevels, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        holder.addView(menuSettings, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        menuLayer.addView(holder, FrameLayout.LayoutParams(-1, -1))
        return menuLayer
    }

    private fun showMainMenu() {
        resumeBtn?.visibility = if (gameStarted) View.VISIBLE else View.GONE
        menuLevels.visibility = View.GONE
        menuSettings.visibility = View.GONE
        menuMain.visibility = View.VISIBLE
    }

    private fun showSettings() {
        menuMain.visibility = View.GONE
        menuSettings.visibility = View.VISIBLE
    }

    private fun showLevelSelect() {
        menuMain.visibility = View.GONE
        menuLevels.visibility = View.VISIBLE
        previewSelection()
    }

    private fun showMenu() {
        if (menuLayer.visibility == View.VISIBLE) return
        if (gameStarted) glView.queueEvent { renderer.paused = true }
        hudLayer.visibility = View.INVISIBLE
        showMainMenu()
        menuLayer.alpha = 0f
        menuLayer.visibility = View.VISIBLE
        menuLayer.animate().alpha(1f).setDuration(220).start()
    }

    private fun hideMenu() {
        menuLayer.animate().alpha(0f).setDuration(180).withEndAction { menuLayer.visibility = View.GONE }.start()
        hudLayer.visibility = View.VISIBLE
    }

    private fun startGame(shape: ShapeKind?, terrain: Terrain?, plain: Boolean, theme: Int? = null, medium: Medium? = null) {
        gameStarted = true
        hideMenu()
        glView.queueEvent { renderer.startGame(shape, terrain, plain, theme, medium) }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            menuLayer.visibility == View.VISIBLE && (menuLevels.visibility == View.VISIBLE || menuSettings.visibility == View.VISIBLE) -> showMainMenu()
            menuLayer.visibility == View.VISIBLE && gameStarted -> { hideMenu(); glView.queueEvent { renderer.paused = false } }
            menuLayer.visibility == View.VISIBLE -> finishAffinity()
            else -> showMenu()
        }
    }

    override fun onResume() {
        super.onResume()
        engine.onResume()
        glView.onResume()
        if (renderer.tiltMode) setTilt(true)
    }

    override fun onPause() {
        super.onPause()
        engine.onPause()
        sensors.unregisterListener(this)
        glView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.release()
    }

    // ---- renderer callbacks (GL thread) -------------------------------------------------
    override fun onHud(shape: String, seconds: Int, solved: Boolean) {
        runOnUiThread {
            title.text = shape.uppercase()
            clock.text = "%d:%02d".format(seconds / 60, seconds % 60)
            banner.animate().alpha(if (solved) 1f else 0f).setDuration(350).start()
        }
    }

    override fun onHint(cooldown: Int, active: Boolean) {
        runOnUiThread {
            hintPill?.text = (if (cooldown > 0 && !active) "Hint $cooldown" else "Hint").uppercase()
            hintPill?.alpha = if (cooldown > 0 && !active) 0.6f else 1f
            refreshToggles()
        }
    }

    override fun onReady() { rendererReady = true }

    /** Ask for the display's fastest refresh rate at the current resolution (90/120 Hz phones). */
    private fun requestHighRefresh() {
        try {
            @Suppress("DEPRECATION") val d = windowManager.defaultDisplay
            val cur = d.mode
            val best = d.supportedModes.filter { it.physicalWidth == cur.physicalWidth && it.physicalHeight == cur.physicalHeight }.maxByOrNull { it.refreshRate }
            if (best != null) {
                window.attributes = window.attributes.apply { preferredDisplayModeId = best.modeId }
                renderer.refreshHz = best.refreshRate
            } else renderer.refreshHz = d.refreshRate
        } catch (e: Exception) { /* keep the default */ }
    }

    private fun toastText(message: String) = onToast(message)

    override fun onToast(message: String) {
        runOnUiThread {
            toast.text = message
            toast.animate().cancel()
            toast.alpha = 1f
            toast.animate().alpha(0f).setStartDelay(1400).setDuration(500).start()
        }
    }

    override fun onFinish(name: String, accent: Vec3) {
        runOnUiThread {
            this.accent = Color.rgb((accent.x * 255).toInt(), (accent.y * 255).toInt(), (accent.z * 255).toInt())
            finishName.text = name
            refreshToggles()
        }
    }

    override fun onImpact(strength: Float) {
        runOnUiThread { glView.performHapticFeedback(if (strength > 0.5f) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK) }
    }

    /** Picks a 4x MSAA config when the device offers one, falling back gracefully. */
    private class MsaaChooser : GLSurfaceView.EGLConfigChooser {
        override fun chooseConfig(egl: EGL10, display: EGLDisplay): EGLConfig {
            fun attempt(samples: Int): EGLConfig? {
                val attribs = intArrayOf(
                    EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8, EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                    EGL10.EGL_DEPTH_SIZE, 24, EGL10.EGL_STENCIL_SIZE, 0,
                    0x3040 /* EGL_RENDERABLE_TYPE */, 0x40 /* EGL_OPENGL_ES3_BIT */,
                    EGL10.EGL_SAMPLE_BUFFERS, if (samples > 0) 1 else 0, EGL10.EGL_SAMPLES, samples,
                    EGL10.EGL_NONE
                )
                val n = IntArray(1)
                val out = arrayOfNulls<EGLConfig>(1)
                return if (egl.eglChooseConfig(display, attribs, out, 1, n) && n[0] > 0) out[0] else null
            }
            return attempt(4) ?: attempt(2) ?: attempt(0) ?: throw IllegalStateException("No suitable EGL config")
        }
    }

    companion object { const val TILT_GAIN = 3.0f }
}
