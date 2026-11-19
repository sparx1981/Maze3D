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
    private var touchSens = 0.5f
    private var gyroSens = 0.5f
    private var hapticsOn = true
    private var qualityLevel = 2
    private var profile = com.maze3d.gl.QualityProfile.HIGH
    private lateinit var customPanel: LinearLayout
    private var simLevel = 1
    private lateinit var menuSettings: LinearLayout
    private var camPill: TextView? = null
    private var ctrlPill: TextView? = null
    private var controlMode = 0
    private lateinit var pulseView: PulseView
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
    private var diffMode = 0         // 0 = normal start, 1 = Keys & Gates
    private var selDifficulty = 1    // 0 easy, 1 medium, 2 hard, 3 ultra
    private lateinit var menuDiff: LinearLayout
    private lateinit var menuBest: LinearLayout
    private lateinit var menuBall: LinearLayout
    private lateinit var menuStats: LinearLayout
    private lateinit var menuRace: LinearLayout
    private lateinit var menuNew: LinearLayout
    private var settingsHolder: View? = null
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
        val progPrefs = getSharedPreferences("maze3d_progress", MODE_PRIVATE)
        progPrefsRef = progPrefs
        progress = com.maze3d.core.Progress(object : com.maze3d.core.ProgressStore {
            override fun get(key: String) = progPrefs.getString(key, null)
            override fun put(key: String, value: String) { progPrefs.edit().putString(key, value).apply() }
        })
        renderer.progress = progress
        stats = com.maze3d.core.Stats(object : com.maze3d.core.ProgressStore {
            override fun get(key: String) = progPrefs.getString(key, null)
            override fun put(key: String, value: String) { progPrefs.edit().putString(key, value).apply() }
        })
        stats.add("sessions", 1.0)
        bestTimes = com.maze3d.core.BestTimes(object : com.maze3d.core.ProgressStore {
            override fun get(key: String) = progPrefs.getString(key, null)
            override fun put(key: String, value: String) { progPrefs.edit().putString(key, value).apply() }
        })
        renderer.ballChoice = ui.getInt("ball", -1).let { if (it >= 0 && !progress.ballUnlocked(it)) -1 else it }
        cameraMode = ui.getInt("camera2", 2).let { if (it == 0) 2 else it }                     // chase is the default view
        touchSens = ui.getFloat("touchSens2", 0.5f)
        gyroSens = ui.getFloat("gyroSens2", 0.5f)
        hapticsOn = ui.getBoolean("haptics", true)
        qualityLevel = ui.getInt("quality", 2)
        simLevel = ui.getInt("sim", 1)
        renderer.simQuality = simLevel
        controlMode = ui.getInt("ctrl", 0).let { if (cameraMode == 2 && it == 2) 0 else it }
        renderer.controlMode = controlMode
        renderer.setCameraMode(cameraMode)
        renderer.touchSens = touchSens
        renderer.gyroSens = gyroSens
        profile = com.maze3d.gl.QualityProfile.decode(ui.getString("qprofile", null)) ?: com.maze3d.gl.QualityProfile.PRESETS[qualityLevel.coerceIn(0, 2)]
        if (!ui.getBoolean("motionOffV1", false)) {                   // motion blur is now off by default, including for saved settings
            profile = profile.copy(motion = false)
            ui.edit().putBoolean("motionOffV1", true).putString("qprofile", profile.encode()).apply()
        }
        renderer.applyProfileSetting(profile)
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
        pulseView = PulseView(this, { Triple(renderer.ballScreenX, renderer.ballScreenY, renderer.ballOnScreen) }, { accent })
        root.addView(pulseView, FrameLayout.LayoutParams(-1, -1))
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
    private lateinit var pctText: TextView
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
        pctText = TextView(this).apply {
            setTextColor(Color.argb(210, 255, 255, 255)); textSize = 18f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(10f, 0f, 2f, Color.argb(170, 0, 0, 0)); setPadding(dp(14f), 0, 0, dp(4f))
        }
        val clockRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM }
        clockRow.addView(clock); clockRow.addView(pctText)
        col.addView(title); col.addView(clockRow); col.addView(finishName)
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
        val findBtn = pill("Find ball") { pulseView.start() }
        for (v in listOf(menuBtn, findBtn)) column.addView(v, LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 0, 0, dp(8f)) })
        layer.addView(column, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(36f), dp(18f), 0) })

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val ctrlNames = arrayOf("Joystick", "Tilt", "Gravity")
        val tilt = pill("Ctrl: " + ctrlNames[controlMode], { controlMode != 0 }) { v ->
            val allowed = if (cameraMode == 2) intArrayOf(0, 1) else intArrayOf(0, 1, 2)
            controlMode = allowed[(allowed.indexOf(controlMode) + 1) % allowed.size]
            applyControl(); (v as TextView).text = ("Ctrl: " + ctrlNames[controlMode]).uppercase()
            toastText(when (controlMode) { 0 -> "Joystick: drag anywhere to steer"; 1 -> "Tilt: steer with the gyro, the piece stays put"; else -> "Gravity: drag to turn the piece and the ball rolls downhill" })
        }
        ctrlPill = tilt
        val hint = pill("Hint", { renderer.hintActive }) { glView.queueEvent { renderer.requestHint() } }
        hintPill = hint
        val camNames = arrayOf("Cam: Follow", "Cam: Follow", "Cam: Chase")
        val follow = pill(camNames[cameraMode], { cameraMode == 2 }) { v ->
            cameraMode = if (cameraMode == 2) 1 else 2
            v.text = camNames[cameraMode].uppercase()
            getSharedPreferences("maze3d_ui", MODE_PRIVATE).edit().putInt("camera2", cameraMode).apply()
            val m = cameraMode
            glView.queueEvent { renderer.setCameraMode(m) }
            if (m != 2) joystick.hide()
            if (m == 2 && controlMode == 2) { controlMode = 0; applyControl() }
            ctrlPill?.text = ("Ctrl: " + ctrlNames[controlMode]).uppercase()
            toastText(if (m == 1) "Follow camera: the view tracks the ball" else "Chase camera: drag to steer, twist to look around")
        }
        camPill = follow
        val ball = pill("Restart") { confirmRestart() }
        for (b in listOf(tilt, hint, follow, ball)) {
            bar.addView(b, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(3f), 0, dp(3f), 0) })
        }
        layer.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(0, 0, 0, dp(30f)) })
        return layer
    }

    // ---- touch: drag to rotate, pinch to zoom -------------------------------------------
    private fun onTouch(e: MotionEvent): Boolean {
        if (cameraMode == 2 || controlMode != 2) return onChaseTouch(e)
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
                if (controlMode == 0) { joystick.show(e.x, e.y); glView.queueEvent { renderer.setChaseInput(0f, 0f, true) } }
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
                    if (cameraMode == 2) glView.queueEvent { renderer.chaseYawBy(d) } else glView.queueEvent { renderer.twist(-d) }
                } else if (!multiTouch && controlMode == 0) {
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
    private fun applyControl() {
        getSharedPreferences("maze3d_ui", MODE_PRIVATE).edit().putInt("ctrl", controlMode).apply()
        renderer.controlMode = controlMode
        if (controlMode != 0) joystick.hide()
        setTilt(controlMode == 1)
    }

    private fun setTilt(on: Boolean) {
        // Tilting must not rotate the screen: hold whatever orientation we are in when tilt is switched on.
        requestedOrientation = if (on) android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_FULL_USER
        sensors.unregisterListener(this)
        if (on) {
            baseline = null
            smoothed = null
            gravitySensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        } else {
            renderer.deviceGravity = Vec3(0f, -1f, 0f)
            renderer.tiltPitch = 0f
        }
    }

    private var smoothed: Vec3? = null

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        baseline = null; smoothed = null            // "level" is measured again in the new orientation
        if (menuSettings.visibility == View.VISIBLE) buildSettings()      // the settings layout depends on the width
        else if (menuBest.visibility == View.VISIBLE) showBest()
    }

    override fun onSensorChanged(ev: SensorEvent) {
        // Device axes equal screen axes (portrait locked). Gravity "down" is minus the sensor vector.
        // Sensor axes belong to the device's natural orientation; rotate them to the screen when it is in landscape.
        val vx = ev.values[0]; val vy = ev.values[1]
        @Suppress("DEPRECATION") val rot = windowManager.defaultDisplay.rotation
        val (sx, sy) = when (rot) { android.view.Surface.ROTATION_90 -> -vy to vx; android.view.Surface.ROTATION_180 -> -vx to -vy; android.view.Surface.ROTATION_270 -> vy to -vx; else -> vx to vy }
        val raw = Vec3(-sx, -sy, -ev.values[2]).normalized()
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
        // Signed forward/back tilt for the chase view: rotation about the screen's horizontal axis (gravity's y change alone is never negative).
        val ax = if (axis.lengthSq() < 1e-10f) 0f else axis.normalized().x
        renderer.tiltPitch = ax * minOf(angle * TILT_GAIN * gyroSens, 2.0f)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    // ---- game pads (USB-C or Bluetooth) ---------------------------------------------------------
    private var padActive = false

    /** Asks before putting the ball back: last checkpoint, the start of the maze, or cancel. */
    private fun confirmRestart() {
        if (restartDialog?.isShowing == true) return
        glView.queueEvent { renderer.paused = true }
        val d = android.app.AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Restart the ball?")
            .setItems(arrayOf("Last checkpoint", "Start of maze", "Cancel")) { _, which ->
                when (which) {
                    0 -> glView.queueEvent { renderer.resetMarble(true) }
                    1 -> glView.queueEvent { renderer.resetMarble(false) }
                }
            }
            .setOnDismissListener { glView.queueEvent { renderer.paused = false } }
            .create()
        restartDialog = d
        d.show()
    }
    private var restartDialog: android.app.AlertDialog? = null

    private fun axis(ev: MotionEvent, a: Int): Float {
        val v = ev.getAxisValue(a)
        return if (kotlin.math.abs(v) < 0.14f) 0f else v
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.source and android.view.InputDevice.SOURCE_JOYSTICK == android.view.InputDevice.SOURCE_JOYSTICK && ev.action == MotionEvent.ACTION_MOVE) {
            var x = axis(ev, MotionEvent.AXIS_X); var y = axis(ev, MotionEvent.AXIS_Y)
            if (x == 0f && y == 0f) { x = axis(ev, MotionEvent.AXIS_HAT_X); y = axis(ev, MotionEvent.AXIS_HAT_Y) }
            var rx = axis(ev, MotionEvent.AXIS_Z); if (rx == 0f) rx = axis(ev, MotionEvent.AXIS_RX)
            val menuUp = menuLayer.visibility == View.VISIBLE
            glView.queueEvent { renderer.padLook(if (menuUp) 0f else rx) }
            if (!menuUp) {
                val on = x != 0f || y != 0f
                if (on || padActive) {
                    padActive = on
                    glView.queueEvent { renderer.padInput(x, y, on) }
                }
                return true
            }
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchKeyEvent(ev: android.view.KeyEvent): Boolean {
        val pad = ev.source and android.view.InputDevice.SOURCE_GAMEPAD == android.view.InputDevice.SOURCE_GAMEPAD
        if (pad && menuLayer.visibility != View.VISIBLE) {
            if (ev.action == android.view.KeyEvent.ACTION_DOWN && ev.repeatCount == 0) {
                when (ev.keyCode) {
                    android.view.KeyEvent.KEYCODE_BUTTON_A -> { glView.queueEvent { renderer.requestHint() }; return true }
                    android.view.KeyEvent.KEYCODE_BUTTON_B -> { runOnUiThread { confirmRestart() }; return true }
                    android.view.KeyEvent.KEYCODE_BUTTON_X -> { camPill?.performClick(); return true }
                    android.view.KeyEvent.KEYCODE_BUTTON_Y, android.view.KeyEvent.KEYCODE_BUTTON_START -> { showMenu(); return true }
                    android.view.KeyEvent.KEYCODE_BUTTON_L1 -> { glView.queueEvent { renderer.nudgeZoom(-0.1f) }; return true }
                    android.view.KeyEvent.KEYCODE_BUTTON_R1 -> { glView.queueEvent { renderer.nudgeZoom(0.1f) }; return true }
                }
            }
        }
        if (pad && menuLayer.visibility == View.VISIBLE && ev.action == android.view.KeyEvent.ACTION_DOWN && ev.keyCode == android.view.KeyEvent.KEYCODE_BUTTON_B) {
            onBackPressed(); return true
        }
        return super.dispatchKeyEvent(ev)
    }

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
        isFocusable = true
        setOnFocusChangeListener { v, f -> v.scaleX = if (f) 1.05f else 1f; v.scaleY = if (f) 1.05f else 1f; v.alpha = if (f) 1f else 0.92f }
        setOnClickListener { engine.uiClick(); onClick() }
    }

    private var presetBox: LinearLayout? = null

    /** The Low / Medium / High / Custom chips; rebuilt whenever the settings change so the right one is lit. */
    private fun buildPresetRow() {
        val box = presetBox ?: return
        box.removeAllViews()
        box.addView(segmented(listOf("Low", "Medium", "High", "Custom"), { presetIndex() }, { i ->
            if (i < 3) { applyProfile(com.maze3d.gl.QualityProfile.PRESETS[i]); rebuildCustom() }
            else customPanel.visibility = View.VISIBLE
        }, fill = true))
    }

    private fun presetIndex(): Int { val i = com.maze3d.gl.QualityProfile.PRESETS.indexOf(profile); return if (i >= 0) i else 3 }

    private fun applyProfile(p: com.maze3d.gl.QualityProfile) {
        profile = p
        getSharedPreferences("maze3d_ui", MODE_PRIVATE).edit().putString("qprofile", p.encode()).apply()
        glView.queueEvent { renderer.applyProfileSetting(p) }
        buildPresetRow()
    }

    /** The "tweak anything" list under the quality presets; rebuilt whenever the preset changes so the chips match. */
    private fun rebuildCustom() {
        customPanel.removeAllViews()
        fun add(v: View) { customPanel.addView(v, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(12f) }) }
        fun onOff(label: String, get: () -> Boolean, set: (Boolean) -> com.maze3d.gl.QualityProfile) =
            add(settingRow(label, segmented(listOf("On", "Off"), { if (get()) 0 else 1 }, { applyProfile(set(it == 0)) })))
        fun levels(label: String, opts: List<String>, get: () -> Int, set: (Int) -> com.maze3d.gl.QualityProfile) =
            add(settingRow(label, segmented(opts, get, { applyProfile(set(it)) })))
        levels("Shadows", listOf("Off", "Low", "High"), { profile.shadows }) { profile.copy(shadows = it) }
        levels("Ball reflections", listOf("Off", "Low", "High"), { profile.reflections }) { profile.copy(reflections = it) }
        onOff("Motion blur", { profile.motion }) { profile.copy(motion = it) }
        onOff("Bloom glow", { profile.bloom }) { profile.copy(bloom = it) }
        onOff("Depth of field", { profile.dof }) { profile.copy(dof = it) }
        onOff("Edge smoothing", { profile.fxaa }) { profile.copy(fxaa = it) }
        levels("Particles & rain", listOf("Fewer", "Normal"), { profile.particles }) { profile.copy(particles = it) }
        levels("Resolution", listOf("Auto", "75%", "Full"), { profile.resolution }) { profile.copy(resolution = it) }
        levels("Frame rate", listOf("30", "60", "Max"), { profile.fps }) { profile.copy(fps = it) }
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

    private fun chipRow(options: List<String>, get: () -> Int, set: (Int) -> Unit, locked: ((Int) -> String?)? = null): LinearLayout = chipGrid(options, options.size.coerceAtLeast(1), get, set, locked)

    private fun chipGrid(options: List<String>, columns: Int, get: () -> Int, set: (Int) -> Unit, locked: ((Int) -> String?)? = null): LinearLayout {
        val usePreview = !noPreview
        noPreview = false
        val grid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        var row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = ArrayList<TextView>()
        fun refresh() { chips.forEachIndexed { i, c -> c.background = glass(get() == i) } }
        options.forEachIndexed { i, label ->
            if (i > 0 && i % columns == 0) { grid.addView(row, LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(6f)) }); row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL } }
            val c = TextView(this).apply {
                val lockMsg = locked?.invoke(i)
                text = (if (lockMsg != null) "\uD83D\uDD12 " else "") + label.uppercase(); setTextColor(Color.WHITE); textSize = 12f; letterSpacing = 0.1f
                if (lockMsg != null) alpha = 0.5f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
                gravity = Gravity.CENTER
                setPadding(dp(4f), dp(12f), dp(4f), dp(12f))
                background = glass(get() == i)
                isFocusable = true
                setOnFocusChangeListener { v, f -> v.scaleX = if (f) 1.06f else 1f; v.scaleY = if (f) 1.06f else 1f }
                setOnClickListener {
                    val msg = locked?.invoke(i)
                    if (msg != null) { engine.uiToggle(false); onToast(msg) }
                    else { set(i); refresh(); engine.uiClick(); if (usePreview) previewSelection() }
                }
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
    private fun isStormSel() = com.maze3d.core.Progress.MENU_KEYS.getOrNull(selSurface) == "storm"
    private fun selectedTerrain(): Terrain? = when (selSurface) { 0 -> null; 2 -> Terrain.SAND; 3 -> Terrain.MUD; else -> Terrain.NONE }
    private fun selectedMedium(): Medium? = if (isStormSel()) Medium.STORM else when (selSurface) { 0 -> null; 4 -> Medium.WATER; 5 -> Medium.FOG; 6 -> Medium.GAS; else -> Medium.NONE }
    /** null = random, -1 = a classic finish, 0.. = surface theme. */
    private fun selectedTheme(): Int? = if (isStormSel()) null else when (selSurface) { 0 -> null; in 1..6 -> -1; else -> selSurface - 7 }

    /** Show the chosen level behind the menu as soon as an option changes. */
    private fun previewSelection() {
        val sh = selectedShape(); val tr = selectedTerrain(); val th = selectedTheme(); val md = selectedMedium(); val plain = selHazards == 1
        gameStarted = false          // previewing replaces the level, so there is nothing to resume any more
        val df = com.maze3d.core.Difficulty.values()[selDifficulty]
        glView.queueEvent { renderer.difficulty = df; renderer.previewLevel(sh, tr, plain, th, md) }
    }

    private fun buildMenu(): View {
        menuLayer = FrameLayout(this).apply {
            setBackgroundColor(Color.argb(150, 4, 6, 12)); isClickable = true; visibility = View.GONE
        }
        val sans = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        fun header(sub: String): LinearLayout {
            val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            box.addView(TextView(this).apply {
                text = "MARBLIX"; setTextColor(Color.WHITE); textSize = 30f; letterSpacing = 0.22f
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
        menuMain.addView(header("3D marble ball maze"))
        resumeBtn = bigButton("Resume", primary = true) { hideMenu(); glView.queueEvent { renderer.paused = false } }
        menuMain.addView(resumeBtn, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(12f)) })
        changeBallBtn = bigButton("Change ball") { showBall() }
        menuMain.addView(changeBallBtn, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(12f)) })
        menuMain.addView(bigButton("Quick start", primary = true) { diffMode = 0; showDifficulty() },
            LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(12f)) })
        menuMain.addView(bigButton("New game") { showNewGame() }, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(12f)) })
        menuMain.addView(bigButton("Stats") { showStats() }, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(12f)) })
        menuMain.addView(bigButton("Settings") { showSettings() }, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(12f)) })
        menuMain.addView(bigButton("Exit") { finishAffinity() }, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(6f)) })

        // ---- level select screen
        menuLevels = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        menuLevels.addView(header("select level"))
        levelPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        rebuildLevelPanel()
        menuLevels.addView(levelPanel, LinearLayout.LayoutParams(maxW, -2))
        menuLevels.addView(bigButton("Start", primary = true) { startGame(selectedShape(), selectedTerrain(), selHazards == 1, selectedTheme(), selectedMedium()) },
            LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, dp(28f), 0, dp(12f)) })
        menuLevels.addView(bigButton("Back") { showNewGame() }, LinearLayout.LayoutParams(maxW, -2))

        // ---- difficulty screen (Quick start asks every time)
        menuDiff = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        menuDiff.addView(header("choose difficulty"))
        for (d in com.maze3d.core.Difficulty.values()) {
            menuDiff.addView(bigButton(d.label, primary = d == com.maze3d.core.Difficulty.MEDIUM) {
                val lock = difficultyLock(d)
                if (lock != null) onToast(lock) else { selDifficulty = d.ordinal; if (diffMode == 1) startKeys() else startGame(null, null, false) }
            }.also { diffButtons.add(it to d) },
                LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(4f)) })
            menuDiff.addView(TextView(this).apply { text = d.blurb; setTextColor(Color.argb(150, 255, 255, 255)); textSize = 11f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12f)) },
                LinearLayout.LayoutParams(maxW, -2))
        }
        menuDiff.addView(bigButton("Back") { if (diffMode == 1) showNewGame() else showMainMenu() }, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, dp(10f), 0, 0) })

        // ---- change ball / stats / race screens (filled when shown)
        menuBall = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        menuBall.setPadding(0, dp(36f), 0, dp(32f))
        menuStats = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        menuStats.setPadding(0, dp(36f), 0, dp(32f))
        menuRace = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        menuRace.setPadding(0, dp(36f), 0, dp(32f))
        menuNew = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        menuNew.addView(header("new game"))
        for ((label, hint, act) in listOf<Triple<String, String, () -> Unit>>(
            Triple("Select level", "Pick the shape, surface, difficulty and ball", { showLevelSelect() }),
            Triple("Keys & Gates", "Find the keys to open the gates, then the exit", { diffMode = 1; showDifficulty() }),
            Triple("Daily maze", "One maze for everyone, every day", { showDaily() }),
            Triple("Race", "Beat the ghost of your best run", { showRace() }))) {
            menuNew.addView(bigButton(label) { act() }, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, 0, 0, dp(4f)) })
            menuNew.addView(TextView(this).apply { text = hint; setTextColor(Color.argb(150, 255, 255, 255)); textSize = 11f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(12f)) }, LinearLayout.LayoutParams(maxW, -2))
        }
        menuNew.addView(bigButton("Back") { showMainMenu() }, LinearLayout.LayoutParams(maxW, -2).apply { setMargins(0, dp(8f), 0, 0) })

        // ---- best times
        menuBest = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        menuBest.setPadding(0, dp(36f), 0, dp(32f))

        // ---- settings screen (built on demand so it can adapt to the current orientation)
        menuSettings = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; visibility = View.GONE }
        menuSettings.setPadding(0, dp(36f), 0, dp(32f))

        val holder = FrameLayout(this)
        holder.addView(menuMain, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        holder.addView(menuLevels, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        holder.addView(menuDiff, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        holder.addView(menuSettings, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        holder.addView(menuBest, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        holder.addView(menuBall, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        holder.addView(menuStats, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        holder.addView(menuRace, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        holder.addView(menuNew, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        val menuScroll = android.widget.ScrollView(this).apply { isVerticalScrollBarEnabled = false; isFillViewport = true; addView(holder, FrameLayout.LayoutParams(-1, -2)) }
        menuLayer.addView(menuScroll, FrameLayout.LayoutParams(-1, -1))
        return menuLayer
    }

    private var changeBallBtn: TextView? = null

    private fun showMainMenu() {
        resumeBtn?.visibility = if (gameStarted) View.VISIBLE else View.GONE
        changeBallBtn?.visibility = if (gameStarted) View.VISIBLE else View.GONE
        menuBall.visibility = View.GONE
        menuStats.visibility = View.GONE
        menuRace.visibility = View.GONE
        menuNew.visibility = View.GONE
        menuLevels.visibility = View.GONE
        menuSettings.visibility = View.GONE
        menuDiff.visibility = View.GONE
        menuBest.visibility = View.GONE
        menuMain.visibility = View.VISIBLE
        menuMain.post { for (k in 0 until menuMain.childCount) { val v = menuMain.getChildAt(k); if (v.isFocusable && v.visibility == View.VISIBLE) { v.requestFocus(); break } } }
    }

    /** The grid of ball styles (locked ones show a padlock); used on Select level and on the in-game Change ball screen. */
    private fun ballGrid(cols: Int): View {
        val names = listOf("Automatic") + com.maze3d.core.Progress.SKIN_NAMES
        noPreview = true
        return chipGrid(names, cols, { renderer.ballChoice + 1 }, { i ->
            renderer.ballChoice = i - 1
            getSharedPreferences("maze3d_ui", MODE_PRIVATE).edit().putInt("ball", i - 1).apply()
            ballPickDone?.invoke()
        }, { i ->
            if (i == 0 || progress.ballUnlocked(i - 1)) null else "Finish a maze on this surface to unlock this ball"
        })
    }

    // ---- settings layout: grouped cards of label / control rows, one or two columns depending on width ------------------
    private fun segmented(options: List<String>, get: () -> Int, set: (Int) -> Unit, fill: Boolean = false): LinearLayout {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val chips = ArrayList<TextView>()
        fun refresh() { chips.forEachIndexed { i, c -> c.background = glass(get() == i) } }
        options.forEachIndexed { i, label ->
            val c = TextView(this).apply {
                text = label.uppercase(); setTextColor(Color.WHITE); textSize = 11f; letterSpacing = 0.08f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); gravity = Gravity.CENTER
                setPadding(dp(12f), dp(10f), dp(12f), dp(10f)); minWidth = dp(48f)
                background = glass(get() == i)
                isFocusable = true
                setOnFocusChangeListener { v, f -> v.scaleX = if (f) 1.06f else 1f; v.scaleY = if (f) 1.06f else 1f }
                setOnClickListener { set(i); refresh(); engine.uiClick() }
            }
            chips.add(c)
            row.addView(c, if (fill) LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(2f), 0, dp(2f), 0) } else LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4f), 0, 0, 0) })
        }
        return row
    }

    private var settingsNarrow = false

    private fun settingRow(label: String, control: View, hint: String? = null): LinearLayout {
        // On a narrow (portrait) screen a switch with three or more options cannot sit beside its label: stack them and let the chips share the width.
        val stack = settingsNarrow && control is LinearLayout && control.childCount >= 3
        if (stack) {
            val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            col.addView(TextView(this).apply { text = label; setTextColor(Color.WHITE); textSize = 14f })
            if (hint != null) col.addView(TextView(this).apply { text = hint; setTextColor(Color.argb(140, 255, 255, 255)); textSize = 11f; setPadding(0, dp(2f), 0, 0) })
            val lc = control as LinearLayout
            for (i in 0 until lc.childCount) lc.getChildAt(i).layoutParams = LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(if (i == 0) 0 else dp(4f), 0, 0, 0) }
            col.addView(control, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(8f) })
            return col
        }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        box.addView(TextView(this).apply { text = label; setTextColor(Color.WHITE); textSize = 14f; typeface = Typeface.create("sans-serif", Typeface.NORMAL) })
        if (hint != null) box.addView(TextView(this).apply { text = hint; setTextColor(Color.argb(140, 255, 255, 255)); textSize = 11f; setPadding(0, dp(2f), 0, 0) })
        row.addView(box, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(12f), 0) })
        row.addView(control, LinearLayout.LayoutParams(-2, -2))
        return row
    }

    private fun settingsCard(title: String, vararg rows: View): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16f), dp(14f), dp(16f), dp(16f))
            background = GradientDrawable().apply { cornerRadius = dp(18f).toFloat(); setColor(Color.argb(95, 10, 14, 26)); setStroke(dp(1f), Color.argb(46, 255, 255, 255)) }
        }
        card.addView(TextView(this).apply {
            text = title.uppercase(); setTextColor(Color.argb(160, 255, 255, 255)); textSize = 11f; letterSpacing = 0.25f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(0, 0, 0, dp(12f))
        })
        rows.forEachIndexed { i, r -> card.addView(r, LinearLayout.LayoutParams(-1, -2).apply { if (i > 0) topMargin = dp(14f) }) }
        return card
    }

    private fun sliderRow(label: String, get: () -> Float, set: (Float) -> Unit): LinearLayout {
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(TextView(this).apply { text = label; setTextColor(Color.WHITE); textSize = 14f })
        col.addView(sensitivitySlider(get, set))
        return col
    }

    private fun buildSettings() {
        menuSettings.removeAllViews()
        val dm = resources.displayMetrics
        val widthDp = dm.widthPixels / dm.density
        val wide = widthDp >= 600f
        settingsNarrow = !wide
        val panelW = if (wide) minOf(dp(760f), dm.widthPixels - dp(48f)) else minOf(dp(420f), dm.widthPixels - dp(32f))
        val prefs = getSharedPreferences("maze3d_ui", MODE_PRIVATE)

        // header: back on the left, title beside it
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "\u2039  BACK"; setTextColor(Color.WHITE); textSize = 12f; letterSpacing = 0.12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); gravity = Gravity.CENTER
            setPadding(dp(16f), dp(11f), dp(18f), dp(11f)); background = glass(false); isFocusable = true
            setOnFocusChangeListener { v, f -> v.scaleX = if (f) 1.06f else 1f; v.scaleY = if (f) 1.06f else 1f }
            setOnClickListener { engine.uiClick(); showMainMenu() }
        }, LinearLayout.LayoutParams(-2, -2))
        top.addView(TextView(this).apply {
            text = "SETTINGS"; setTextColor(Color.WHITE); textSize = 22f; letterSpacing = 0.2f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); setPadding(dp(16f), 0, 0, 0)
        }, LinearLayout.LayoutParams(-2, -2))
        menuSettings.addView(top, LinearLayout.LayoutParams(panelW, -2).apply { setMargins(0, 0, 0, dp(20f)) })

        val controls = settingsCard("Controls",
            sliderRow("Tilt sensitivity", { gyroSens }) { gyroSens = it; renderer.gyroSens = it; prefs.edit().putFloat("gyroSens2", it).apply() },
            sliderRow("Touch sensitivity", { touchSens }) { touchSens = it; renderer.touchSens = it; prefs.edit().putFloat("touchSens2", it).apply() })
        val sound = settingsCard("Sound & feel",
            settingRow("Music", segmented(listOf("On", "Off"), { if (engine.musicOn) 0 else 1 }, { engine.setMusic(it == 0) })),
            settingRow("Sound effects", segmented(listOf("On", "Off"), { if (engine.sfxOn) 0 else 1 }, { engine.setSfx(it == 0) })),
            settingRow("Impact sounds", segmented(listOf("On", "Off"), { if (engine.impactOn) 0 else 1 }, { engine.setImpact(it == 0) })),
            settingRow("Haptics on impacts", segmented(listOf("On", "Off"), { if (hapticsOn) 0 else 1 }, { hapticsOn = it == 0; prefs.edit().putBoolean("haptics", hapticsOn).apply() })))
        presetBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        customPanel = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; visibility = if (presetIndex() == 3) View.VISIBLE else View.GONE }
        buildPresetRow()
        rebuildCustom()
        val disclosure = TextView(this).apply {
            fun label() = (if (customPanel.visibility == View.VISIBLE) "TWEAK THE DETAILS  \u25B4" else "TWEAK THE DETAILS  \u25BE")
            text = label(); setTextColor(Color.argb(190, 255, 255, 255)); textSize = 11f; letterSpacing = 0.12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); setPadding(0, dp(6f), 0, dp(6f)); isFocusable = true
            setOnClickListener { customPanel.visibility = if (customPanel.visibility == View.VISIBLE) View.GONE else View.VISIBLE; text = label(); engine.uiClick() }
        }
        val graphics = settingsCard("Graphics", presetBox!!, disclosure, customPanel)
        val sim = settingsCard("Simulation",
            settingRow("Sand, mud, water, gas", segmented(listOf("Basic", "Detailed", "Ultra"), { simLevel }, { simLevel = it; prefs.edit().putInt("sim", it).apply(); renderer.simQuality = it }), "How finely loose materials react"))

        val gap = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(16f)) }
        if (wide) {
            val cols = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val left = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            val right = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            left.addView(controls, gap); left.addView(sound, gap); left.addView(sim, gap)
            right.addView(graphics, gap)
            cols.addView(left, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(0, 0, dp(8f), 0) })
            cols.addView(right, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(8f), 0, 0, 0) })
            menuSettings.addView(cols, LinearLayout.LayoutParams(panelW, -2))
        } else {
            val one = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            for (v in listOf(controls, sound, graphics, sim)) one.addView(v, gap)
            menuSettings.addView(one, LinearLayout.LayoutParams(panelW, -2))
        }
    }

    private fun showBest() {
        menuBest.removeAllViews()
        val dm = resources.displayMetrics
        val w = minOf(dp(460f), dm.widthPixels - dp(32f))
        menuBest.addView(backHeader("Stats", w), LinearLayout.LayoutParams(w, -2).apply { setMargins(0, 0, 0, dp(14f)) })
        menuBest.addView(tabStrip(1), LinearLayout.LayoutParams(w, -2).apply { setMargins(0, 0, 0, dp(18f)) })
        val entries = bestTimes.all()
        if (entries.isEmpty()) {
            menuBest.addView(TextView(this).apply { text = "Finish a maze to set your first time."; setTextColor(Color.argb(170, 255, 255, 255)); textSize = 14f; gravity = Gravity.CENTER; setPadding(0, dp(40f), 0, 0) }, LinearLayout.LayoutParams(w, -2))
        } else {
            val order = com.maze3d.core.Progress.SURFACE_CHAIN
            for (surface in entries.map { it.surface }.distinct().sortedBy { order.indexOf(it) }) {
                val i = com.maze3d.core.Progress.MENU_KEYS.indexOf(surface)
                val name = if (i > 0) surfaceNames[i] else surface
                val rows = entries.filter { it.surface == surface }.sortedWith(compareBy({ ShapeKind.values().indexOf(it.shape) }, { it.difficulty.ordinal })).map { e ->
                    val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
                    row.addView(TextView(this).apply { text = e.shape.label; setTextColor(Color.WHITE); textSize = 14f }, LinearLayout.LayoutParams(0, -2, 1f))
                    row.addView(TextView(this).apply { text = e.difficulty.label.uppercase(); setTextColor(Color.argb(150, 255, 255, 255)); textSize = 11f; letterSpacing = 0.12f; setPadding(0, 0, dp(16f), 0) }, LinearLayout.LayoutParams(-2, -2))
                    row.addView(TextView(this).apply { text = com.maze3d.core.BestTimes.format(e.seconds); setTextColor(Color.WHITE); textSize = 18f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); minWidth = dp(56f); gravity = Gravity.END }, LinearLayout.LayoutParams(-2, -2))
                    row as View
                }
                menuBest.addView(settingsCard(name, *rows.toTypedArray()), LinearLayout.LayoutParams(w, -2).apply { setMargins(0, 0, 0, dp(16f)) })
            }
        }
        menuMain.visibility = View.GONE; menuNew.visibility = View.GONE
        menuStats.visibility = View.GONE
        menuBest.visibility = View.VISIBLE
    }

    /** Two tabs that share one place: Stats and Best times. */
    private fun tabStrip(active: Int): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        listOf("Stats", "Best times").forEachIndexed { i, label ->
            row.addView(TextView(this).apply {
                text = label.uppercase(); setTextColor(Color.WHITE); textSize = 12f; letterSpacing = 0.12f
                typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); gravity = Gravity.CENTER
                setPadding(dp(8f), dp(12f), dp(8f), dp(12f)); background = glass(i == active); isFocusable = true
                setOnClickListener { if (i != active) { engine.uiClick(); if (i == 0) showStats() else showBest() } }
            }, LinearLayout.LayoutParams(0, -2, 1f).apply { setMargins(dp(3f), 0, dp(3f), 0) })
        }
        return row
    }

    private fun backHeader(title: String, w: Int, back: () -> Unit = { showMainMenu() }): View {
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(TextView(this).apply {
            text = "\u2039  BACK"; setTextColor(Color.WHITE); textSize = 12f; letterSpacing = 0.12f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL); gravity = Gravity.CENTER
            setPadding(dp(16f), dp(11f), dp(18f), dp(11f)); background = glass(false); isFocusable = true
            setOnClickListener { engine.uiClick(); back() }
        }, LinearLayout.LayoutParams(-2, -2))
        top.addView(TextView(this).apply { text = title.uppercase(); setTextColor(Color.WHITE); textSize = 22f; letterSpacing = 0.2f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); setPadding(dp(16f), 0, 0, 0) }, LinearLayout.LayoutParams(-2, -2))
        return top
    }

    private fun showBall() {
        menuBall.removeAllViews()
        val w = minOf(dp(420f), resources.displayMetrics.widthPixels - dp(32f))
        menuBall.addView(backHeader("Change ball", w), LinearLayout.LayoutParams(w, -2).apply { setMargins(0, 0, 0, dp(20f)) })
        ballPickDone = null
        menuBall.addView(settingsCard("Choose a style", ballGrid(2)), LinearLayout.LayoutParams(w, -2))
        menuBall.addView(TextView(this).apply { text = "Locked balls unlock when you finish a maze on their surface."; setTextColor(Color.argb(140, 255, 255, 255)); textSize = 11f; gravity = Gravity.CENTER; setPadding(0, dp(14f), 0, 0) }, LinearLayout.LayoutParams(w, -2))
        menuMain.visibility = View.GONE; menuNew.visibility = View.GONE
        menuBall.visibility = View.VISIBLE
    }

    private fun statRow(label: String, value: String): View {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        row.addView(TextView(this).apply { text = label; setTextColor(Color.WHITE); textSize = 14f }, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(TextView(this).apply { text = value; setTextColor(Color.WHITE); textSize = 18f; typeface = Typeface.create("sans-serif-light", Typeface.NORMAL); gravity = Gravity.END }, LinearLayout.LayoutParams(-2, -2))
        return row
    }

    private fun showStats() {
        synchronized(stats) { stats.flush() }
        menuStats.removeAllViews()
        val w = minOf(dp(460f), resources.displayMetrics.widthPixels - dp(32f))
        menuStats.addView(backHeader("Stats", w), LinearLayout.LayoutParams(w, -2).apply { setMargins(0, 0, 0, dp(14f)) })
        menuStats.addView(tabStrip(0), LinearLayout.LayoutParams(w, -2).apply { setMargins(0, 0, 0, dp(18f)) })
        fun n(v: Double) = "%,d".format(v.toLong())
        val gap = LinearLayout.LayoutParams(w, -2).apply { setMargins(0, 0, 0, dp(16f)) }
        val dm = com.maze3d.core.Difficulty.values()
        val surfacesOpen = com.maze3d.core.Progress.SURFACE_CHAIN.count { progress.surfaceUnlocked(it) }
        val shapesOpen = ShapeKind.values().count { progress.shapeUnlocked(it) }
        val ballsOpen = (0..20).count { progress.ballUnlocked(it) }
        menuStats.addView(settingsCard("Play",
            statRow("Play time", com.maze3d.core.Stats.duration(stats.get("playSeconds"))),
            statRow("Sessions", n(stats.get("sessions"))),
            statRow("Mazes finished", n(progress.completed.toDouble())),
            statRow("Distance rolled", n(stats.get("distance") * 0.1) + " m"),
            statRow("Ball resets", n(stats.get("resets"))),
            statRow("Falls", n(stats.get("falls"))),
            statRow("Hammer hits", n(stats.get("hammer"))),
            statRow("Spring pad launches", n(stats.get("springs"))),
            statRow("Sweeper hits", n(stats.get("sweeper"))),
            statRow("Checkpoints reached", n(stats.get("checkpoints")))), gap)
        menuStats.addView(settingsCard("Unlocked",
            statRow("Surfaces", "$surfacesOpen / ${com.maze3d.core.Progress.SURFACE_CHAIN.size}"),
            statRow("Shapes", "$shapesOpen / ${ShapeKind.values().size}"),
            statRow("Difficulties", "${dm.count { progress.difficultyUnlocked(it) }} / ${dm.size}"),
            statRow("Balls", "$ballsOpen / 21")), gap)
        val played = stats.surfacesPlayed()
        val rows = if (played.isEmpty()) listOf(TextView(this).apply { text = "No levels played yet."; setTextColor(Color.argb(150, 255, 255, 255)); textSize = 13f }) else
            played.mapIndexed { i, (key, count) ->
                val idx = com.maze3d.core.Progress.MENU_KEYS.indexOf(key)
                statRow("${i + 1}.  ${if (idx > 0) surfaceNames[idx] else key}", "$count")
            }
        menuStats.addView(settingsCard("Surfaces played", *rows.toTypedArray()), gap)
        menuMain.visibility = View.GONE; menuNew.visibility = View.GONE
        menuBest.visibility = View.GONE
        menuStats.visibility = View.VISIBLE
    }

    private fun showRace() {
        menuRace.removeAllViews()
        val w = minOf(dp(460f), resources.displayMetrics.widthPixels - dp(32f))
        menuRace.addView(backHeader("Race", w, ::showNewGame), LinearLayout.LayoutParams(w, -2).apply { setMargins(0, 0, 0, dp(14f)) })
        menuRace.addView(TextView(this).apply { text = "Race the ghost of your fastest run on a maze you have finished."; setTextColor(Color.argb(150, 255, 255, 255)); textSize = 12f; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(14f)) }, LinearLayout.LayoutParams(w, -2))
        val entries = bestTimes.all().mapNotNull { e ->
            com.maze3d.core.Ghost.decode(progPrefsRef.getString(ghostKey(e.surface, e.shape, e.difficulty), null))?.let { e to it }
        }
        if (entries.isEmpty()) {
            menuRace.addView(TextView(this).apply { text = "No ghosts yet. Finish a maze and your run is saved here."; setTextColor(Color.argb(170, 255, 255, 255)); textSize = 14f; gravity = Gravity.CENTER; setPadding(0, dp(30f), 0, 0) }, LinearLayout.LayoutParams(w, -2))
        } else {
            val order = com.maze3d.core.Progress.SURFACE_CHAIN
            for ((e, g) in entries.sortedWith(compareBy({ order.indexOf(it.first.surface) }, { ShapeKind.values().indexOf(it.first.shape) }, { it.first.difficulty.ordinal }))) {
                val idx = com.maze3d.core.Progress.MENU_KEYS.indexOf(e.surface)
                val name = if (idx > 0) surfaceNames[idx] else e.surface
                val row = bigButton("$name \u00B7 ${e.shape.label} \u00B7 ${e.difficulty.label}   ${com.maze3d.core.BestTimes.format(e.seconds)}") {
                    selDifficulty = e.difficulty.ordinal
                    gameStarted = true
                    hideMenu()
                    glView.queueEvent { renderer.startRace(g) }
                }
                row.textSize = 13f; row.letterSpacing = 0.05f
                menuRace.addView(row, LinearLayout.LayoutParams(w, -2).apply { setMargins(0, 0, 0, dp(8f)) })
            }
        }
        menuMain.visibility = View.GONE; menuNew.visibility = View.GONE
        menuRace.visibility = View.VISIBLE
    }

    private fun showSettings() {
        buildSettings()
        menuMain.visibility = View.GONE; menuNew.visibility = View.GONE
        menuSettings.visibility = View.VISIBLE
    }

    private val diffButtons = ArrayList<Pair<TextView, com.maze3d.core.Difficulty>>()

    private fun showNewGame() {
        menuMain.visibility = View.GONE; menuNew.visibility = View.GONE
        menuLevels.visibility = View.GONE; menuDiff.visibility = View.GONE; menuRace.visibility = View.GONE
        menuNew.visibility = View.VISIBLE
        menuNew.post { for (k in 0 until menuNew.childCount) { val v = menuNew.getChildAt(k); if (v.isFocusable && v.visibility == View.VISIBLE) { v.requestFocus(); break } } }
    }

    private fun showDifficulty() {
        menuNew.visibility = View.GONE
        for ((b, d) in diffButtons) { val lock = difficultyLock(d) != null; b.alpha = if (lock) 0.5f else 1f; b.text = (if (lock) "\uD83D\uDD12 " else "") + d.label.uppercase() }
        menuMain.visibility = View.GONE; menuNew.visibility = View.GONE
        menuDiff.visibility = View.VISIBLE
    }

    private lateinit var levelPanel: LinearLayout
    private lateinit var progress: com.maze3d.core.Progress
    private lateinit var bestTimes: com.maze3d.core.BestTimes
    private lateinit var stats: com.maze3d.core.Stats
    private lateinit var progPrefsRef: android.content.SharedPreferences
    private var lastStatFlush = 0L
    private val surfaceNames = listOf("Random", "Classic", "Sand", "Mud", "Water", "Fog", "Gas", "Candy", "Dead", "Racing", "Football", "Space", "Food", "Science", "Ice", "Egypt", "Neon", "Jungle", "Glow", "Fall", "Toys", "Craft", "Prehist.", "Tools", "Lava", "Jelly", "Storm")

    private fun mazesText(n: Int) = if (n <= 1) "Finish 1 more maze" else "Finish $n more mazes"

    /** The shape / surface / difficulty chips, with padlocks on whatever has not been earned yet. */
    /** A level option that shows only its current choice; tap it to open the other choices, and picking one folds it away again. */
    private fun collapsible(title: String, current: () -> String, build: (() -> Unit) -> View): View {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        val value = TextView(this).apply { setTextColor(Color.WHITE); textSize = 15f; gravity = Gravity.END }
        val head = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; background = glass(false); isFocusable = true; setPadding(dp(16f), dp(13f), dp(14f), dp(13f)) }
        head.addView(TextView(this).apply { text = title.uppercase(); setTextColor(Color.argb(170, 255, 255, 255)); textSize = 11f; letterSpacing = 0.22f; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL) }, LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(value, LinearLayout.LayoutParams(-2, -2))
        box.addView(head, LinearLayout.LayoutParams(-1, -2))
        var content: View? = null
        fun refreshValue() { value.text = current() + (if (content?.visibility == View.VISIBLE) "   \u25B4" else "   \u25BE") }
        val done = { content?.visibility = View.GONE; refreshValue() }
        content = build(done).apply { visibility = View.GONE; setPadding(0, dp(8f), 0, dp(2f)) }
        box.addView(content, LinearLayout.LayoutParams(-1, -2))
        refreshValue()
        head.setOnClickListener { engine.uiClick(); content.visibility = if (content.visibility == View.VISIBLE) View.GONE else View.VISIBLE; refreshValue() }
        box.layoutParams = LinearLayout.LayoutParams(-1, -2).apply { setMargins(0, 0, 0, dp(8f)) }
        return box
    }

    private fun rebuildLevelPanel() {
        levelPanel.removeAllViews()
        val shapes = ShapeKind.values()
        val shapeNames = listOf("Random") + shapes.map { it.label.replace("Rounded ", "").replace("Trefoil knot", "Knot") }
        levelPanel.addView(collapsible("Shape", { shapeNames[selShape] }) { done ->
            chipGrid(shapeNames, 4, { selShape }, { selShape = it; done() }, { i ->
                if (i == 0 || progress.shapeUnlocked(shapes[i - 1])) null else mazesText(progress.shapeMazesToGo(shapes[i - 1])) + " to unlock this shape"
            })
        })
        levelPanel.addView(collapsible("Surface", { surfaceNames[selSurface] }) { done ->
            chipGrid(surfaceNames, 4, { selSurface }, { selSurface = it; done() }, { i ->
                val key = com.maze3d.core.Progress.MENU_KEYS[i]
                if (key == "random" || progress.surfaceUnlocked(key)) null else mazesText(progress.surfaceMazesToGo(key)) + " to unlock this surface"
            })
        })
        levelPanel.addView(collapsible("Difficulty", { com.maze3d.core.Difficulty.values()[selDifficulty].label }) { done ->
            chipRow(listOf("Easy", "Medium", "Hard", "Ultra"), { selDifficulty }, { selDifficulty = it; done(); previewSelection() }, { i -> difficultyLock(com.maze3d.core.Difficulty.values()[i]) })
        })
        levelPanel.addView(collapsible("Ball", { (listOf("Automatic") + com.maze3d.core.Progress.SKIN_NAMES).getOrElse(renderer.ballChoice + 1) { "Automatic" } }) { done ->
            LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; addView(ballGrid(2).also { g -> ballPickDone = done }) }
        })
        levelPanel.addView(collapsible("Hazards", { listOf("Random", "None")[selHazards] }) { done ->
            chipRow(listOf("Random", "None"), { selHazards }, { selHazards = it; done() })
        })
    }

    private var ballPickDone: (() -> Unit)? = null

    private fun difficultyLock(d: com.maze3d.core.Difficulty): String? =
        if (progress.difficultyUnlocked(d)) null else when (d) { com.maze3d.core.Difficulty.MEDIUM -> "Finish a maze to unlock Medium"; com.maze3d.core.Difficulty.HARD -> "Finish a Medium maze to unlock Hard"; else -> "Finish a Hard maze to unlock Ultra" }

    private fun showLevelSelect() {
        rebuildLevelPanel()
        menuMain.visibility = View.GONE; menuNew.visibility = View.GONE
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
        val df = com.maze3d.core.Difficulty.values()[selDifficulty]
        glView.queueEvent { renderer.difficulty = df; renderer.gameMode = 0; renderer.startGame(shape, terrain, plain, theme, medium) }
    }

    private fun startKeys() {
        gameStarted = true
        hideMenu()
        val df = com.maze3d.core.Difficulty.values()[selDifficulty]
        glView.queueEvent { renderer.difficulty = df; renderer.gameMode = 1; renderer.startGame(null, null, false) }
        onToast("Keys & Gates: collect the keys to open the gates, then reach the exit", 3600)
    }

    private fun startDaily() {
        gameStarted = true
        hideMenu()
        val day = com.maze3d.core.Daily.dayKey()
        val seed = com.maze3d.core.Daily.seedFor(day)
        val shape = com.maze3d.core.Daily.shapeFor(seed)
        val theme = com.maze3d.core.Daily.themeFor(seed, com.maze3d.gl.Palette.themes.size)
        glView.queueEvent {
            renderer.difficulty = com.maze3d.core.Difficulty.MEDIUM; renderer.gameMode = 2; renderer.forcedSeed = seed
            renderer.startGame(shape, Terrain.NONE, false, theme, Medium.NONE)
        }
    }

    private fun dailyName(): String = progPrefsRef.getString("daily_name", "") ?: ""

    private fun askName(then: (String) -> Unit) {
        val input = android.widget.EditText(this).apply {
            filters = arrayOf(android.text.InputFilter.AllCaps(), android.text.InputFilter.LengthFilter(8))
            setText(dailyName()); hint = "ARCADE NAME"; setSingleLine(true); inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        }
        val dlg = android.app.AlertDialog.Builder(this).setTitle("Enter your name (3-8 letters)").setView(input)
            .setPositiveButton("OK", null).setNegativeButton("Skip") { _, _ -> then("") }.create()
        dlg.setOnShowListener {
            dlg.getButton(android.app.AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val n = input.text.toString().trim().uppercase()
                if (com.maze3d.core.Daily.validName(n)) { progPrefsRef.edit().putString("daily_name", n).apply(); dlg.dismiss(); then(n) } else onToast("Use 3 to 8 letters or digits")
            }
        }
        dlg.show()
    }

    private fun showDaily() {
        menuRace.removeAllViews()
        val w = minOf(dp(460f), resources.displayMetrics.widthPixels - dp(32f))
        val day = com.maze3d.core.Daily.dayKey()
        val seed = com.maze3d.core.Daily.seedFor(day)
        val shape = com.maze3d.core.Daily.shapeFor(seed)
        val theme = com.maze3d.gl.Palette.themes[com.maze3d.core.Daily.themeFor(seed, com.maze3d.gl.Palette.themes.size)]
        menuRace.addView(backHeader("Daily maze", w, ::showNewGame), LinearLayout.LayoutParams(w, -2).apply { setMargins(0, 0, 0, dp(14f)) })
        fun note(t: String, size: Float = 12f, alpha: Int = 170) = TextView(this).apply { text = t; setTextColor(Color.argb(alpha, 255, 255, 255)); textSize = size; gravity = Gravity.CENTER; setPadding(0, 0, 0, dp(10f)) }
        menuRace.addView(note("$day  ·  ${shape.label}  ·  ${theme.name}", 14f, 230), LinearLayout.LayoutParams(w, -2))
        menuRace.addView(note("The same maze for everyone today. One fresh maze every day (UTC)."), LinearLayout.LayoutParams(w, -2))
        val best = progPrefsRef.getFloat("daily_best_$day", -1f)
        menuRace.addView(bigButton("Play today's maze", primary = true) { startDaily() }, LinearLayout.LayoutParams(w, -2).apply { setMargins(0, 0, 0, dp(10f)) })
        if (best > 0f) menuRace.addView(note("Your best today: ${com.maze3d.core.BestTimes.format(best)}"), LinearLayout.LayoutParams(w, -2))
        val nm = dailyName()
        menuRace.addView(bigButton(if (nm.isEmpty()) "Set your name" else "Name: $nm  (change)") { askName { showDaily() } }, LinearLayout.LayoutParams(w, -2).apply { setMargins(0, 0, 0, dp(14f)) })
        val board = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        menuRace.addView(note("TODAY'S TOP TIMES", 12f, 200), LinearLayout.LayoutParams(w, -2))
        menuRace.addView(board, LinearLayout.LayoutParams(w, -2))
        board.addView(note(if (com.maze3d.core.Leaderboard.enabled) "Loading..." else "The online leaderboard is not connected yet."))
        com.maze3d.core.Leaderboard.top(day) { rows ->
            runOnUiThread {
                board.removeAllViews()
                if (rows == null) board.addView(note("Could not reach the leaderboard."))
                else if (rows.isEmpty()) board.addView(note("No times yet today. Be the first!"))
                else rows.take(15).forEachIndexed { i, r ->
                    board.addView(note("%2d.  %-8s  %s".format(i + 1, r.name, com.maze3d.core.BestTimes.format(r.seconds)), 14f, if (r.name == nm) 255 else 190).apply { typeface = android.graphics.Typeface.MONOSPACE })
                }
            }
        }
        menuMain.visibility = View.GONE; menuNew.visibility = View.GONE
        menuRace.visibility = View.VISIBLE
    }

    override fun onModeSolved(mode: Int, seconds: Float, difficulty: com.maze3d.core.Difficulty, seed: Long) {
        if (mode == 1) {
            val key = "keys_best_${difficulty.ordinal}"
            val prev = progPrefsRef.getFloat(key, -1f)
            val better = prev <= 0f || seconds < prev
            if (better) progPrefsRef.edit().putFloat(key, seconds).apply()
            onStat("keysDone", 1f)
            onToast("Exit reached in ${com.maze3d.core.BestTimes.format(seconds)}" + if (prev <= 0f) "  (first time)" else if (better) "  (new best, was ${com.maze3d.core.BestTimes.format(prev)})" else "  (best ${com.maze3d.core.BestTimes.format(prev)})", 3600)
        } else if (mode == 2) {
            val day = com.maze3d.core.Daily.dayKey()
            val prev = progPrefsRef.getFloat("daily_best_$day", -1f)
            val better = prev <= 0f || seconds < prev
            if (better) progPrefsRef.edit().putFloat("daily_best_$day", seconds).apply()
            onStat("dailyDone", 1f)
            runOnUiThread {
                val finish = { name: String? ->
                    if (!name.isNullOrEmpty() && better) com.maze3d.core.Leaderboard.submit(day, name, seconds) { }
                    showMenu(); showDaily()
                    onToast("Daily maze: ${com.maze3d.core.BestTimes.format(seconds)}" + if (better) "" else "  (best ${com.maze3d.core.BestTimes.format(prev)})", 3600)
                }
                val nm = dailyName()
                if (nm.isEmpty() && com.maze3d.core.Leaderboard.enabled && better) askName { finish(it) } else finish(nm.ifEmpty { null })
            }
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            menuLayer.visibility == View.VISIBLE && (menuSettings.visibility == View.VISIBLE || (menuDiff.visibility == View.VISIBLE && diffMode == 0) || menuBest.visibility == View.VISIBLE || menuBall.visibility == View.VISIBLE || menuStats.visibility == View.VISIBLE) -> showMainMenu()
            menuLayer.visibility == View.VISIBLE && (menuRace.visibility == View.VISIBLE || menuLevels.visibility == View.VISIBLE || menuDiff.visibility == View.VISIBLE && diffMode == 1) -> showNewGame()
            menuLayer.visibility == View.VISIBLE && menuNew.visibility == View.VISIBLE -> showMainMenu()
            menuLayer.visibility == View.VISIBLE && gameStarted -> { hideMenu(); glView.queueEvent { renderer.paused = false } }
            menuLayer.visibility == View.VISIBLE -> finishAffinity()
            else -> showMenu()
        }
    }

    override fun onResume() {
        super.onResume()
        engine.onResume()
        glView.onResume()
        if (controlMode == 1) setTilt(true)
    }

    override fun onPause() {
        super.onPause()
        synchronized(stats) { stats.flush() }
        engine.onPause()
        sensors.unregisterListener(this)
        glView.onPause()
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.release()
    }

    // ---- renderer callbacks (GL thread) -------------------------------------------------
    private var wrongWay: TextView? = null
    private var wrongEdge: View? = null

    override fun onHud(shape: String, seconds: Int, solved: Boolean, percent: Int, stray: Int) {
        runOnUiThread {
            title.text = shape.uppercase()
            clock.text = "%d:%02d".format(seconds / 60, seconds % 60)
            pctText.text = "$percent%"
            // easy mode: the further you stray from the route, the hotter the percentage glows
            val (col, glow) = when { stray >= 12 -> Color.rgb(255, 70, 60) to 1f; stray >= 6 -> Color.rgb(255, 150, 40) to 0.8f; stray >= 2 -> Color.rgb(255, 214, 64) to 0.6f; else -> Color.argb(210, 255, 255, 255) to 0f }
            pctText.setTextColor(col)
            pctText.setShadowLayer(if (glow > 0f) 22f * glow else 10f, 0f, 0f, if (glow > 0f) col else Color.argb(170, 0, 0, 0))
            // a big, unmissable warning once the ball is clearly heading the wrong way, plus a red glow round the screen edge
            val ww = wrongWay ?: TextView(this).apply {
                text = "WRONG WAY"; setTextColor(Color.WHITE); textSize = 26f; letterSpacing = 0.18f
                typeface = Typeface.create("sans-serif-black", Typeface.BOLD); gravity = Gravity.CENTER
                setPadding(dp(22f), dp(10f), dp(22f), dp(10f)); alpha = 0f
                background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = dp(30f).toFloat(); setColor(Color.argb(210, 220, 40, 40)) }
                (hudLayer as? FrameLayout)?.addView(this, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER_HORIZONTAL or Gravity.TOP).apply { topMargin = dp(150f) })
                wrongWay = this
            }
            val edge = wrongEdge ?: View(this).apply {
                background = android.graphics.drawable.GradientDrawable(android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Color.argb(150, 255, 30, 30), Color.TRANSPARENT, Color.TRANSPARENT, Color.argb(150, 255, 30, 30)))
                alpha = 0f
                (hudLayer as? FrameLayout)?.addView(this, 0, FrameLayout.LayoutParams(-1, -1))
                wrongEdge = this
            }
            val warn = stray >= 4 && !solved
            if (warn) {
                val pulse = 0.65f + 0.35f * kotlin.math.abs(kotlin.math.sin(System.currentTimeMillis() / 180.0)).toFloat()
                ww.alpha = pulse; ww.scaleX = 0.95f + 0.1f * pulse; ww.scaleY = ww.scaleX
                edge.alpha = (if (stray >= 12) 1f else 0.6f) * pulse
                pctText.scaleX = 1.35f; pctText.scaleY = 1.35f
            } else {
                ww.alpha = 0f; edge.alpha = 0f; pctText.scaleX = 1f; pctText.scaleY = 1f
            }
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

    private fun ghostKey(surface: String, shape: ShapeKind, d: com.maze3d.core.Difficulty) = "ghost|$surface|${shape.name}|${d.name}"

    override fun onSolved(surfaceKey: String, shape: ShapeKind, difficulty: com.maze3d.core.Difficulty, seconds: Float, ghost: com.maze3d.core.Ghost, ghostSeconds: Float) {
        val u = progress.record(surfaceKey, shape, difficulty)
        val prev = bestTimes.record(surfaceKey, shape, difficulty, seconds)
        if (prev == null || prev >= 0f) progPrefsRef.edit().putString(ghostKey(surfaceKey, shape, difficulty), ghost.encode()).apply()   // the fastest run becomes the ghost
        if (ghostSeconds > 0f) {
            val diff = seconds - ghostSeconds
            onToast(if (diff < 0f) "You beat your ghost by %.1fs".format(-diff) else "Your ghost won by %.1fs".format(diff), 4200)
        }
        val recordText = when {
            prev == null -> "First time set: ${com.maze3d.core.BestTimes.format(seconds)}"
            prev >= 0f -> "New best ${com.maze3d.core.BestTimes.format(seconds)}  (was ${com.maze3d.core.BestTimes.format(prev)})"
            else -> null
        }
        if (!u.any) { recordText?.let { onToast(it, 3200) }; return }
        val parts = ArrayList<String>()
        u.surface?.let { k -> val i = com.maze3d.core.Progress.MENU_KEYS.indexOf(k); if (i > 0) parts.add("surface ${surfaceNames[i]}") }
        u.shape?.let { parts.add("shape ${it.label}") }
        u.difficulty?.let { parts.add("${it.label} difficulty") }
        u.ball?.let { k -> parts.add("ball: ${com.maze3d.core.Progress.SKIN_NAMES[com.maze3d.core.Progress.skinForKey(k)]}") }
        onToast((recordText?.let { it + "\n" } ?: "") + "Unlocked  " + parts.joinToString("  ·  "), 4200)
    }

    override fun onStat(key: String, amount: Float) {
        synchronized(stats) {
            stats.add(key, amount.toDouble())
            val now = System.currentTimeMillis()
            if (now - lastStatFlush > 15000L) { lastStatFlush = now; stats.flush() }
        }
    }

    override fun onToast(message: String) = onToast(message, 1400)

    private fun onToast(message: String, holdMs: Long) {
        runOnUiThread {
            toast.text = message
            toast.animate().cancel()
            toast.alpha = 1f
            toast.animate().alpha(0f).setStartDelay(holdMs).setDuration(500).start()
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
        if (!hapticsOn) return
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
