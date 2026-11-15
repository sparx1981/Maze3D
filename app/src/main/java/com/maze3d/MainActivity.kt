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
import com.maze3d.core.Vec3
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLDisplay
import com.maze3d.gl.MazeRenderer

class MainActivity : Activity(), MazeRenderer.Host, SensorEventListener {

    private lateinit var engine: SoundEngine
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
        renderer.followOn = getSharedPreferences("maze3d_ui", MODE_PRIVATE).getBoolean("follow", false)
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(MsaaChooser())
            preserveEGLContextOnPause = true
            setRenderer(renderer)
        }
        glView.setOnTouchListener { _, e -> onTouch(e) }

        val root = FrameLayout(this)
        root.addView(glView)
        root.addView(buildOverlay())
        setContentView(root)
        root.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
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

        val qualityNames = arrayOf("Low", "Medium", "High")
        var quality = 2
        val q = pill("Quality " + qualityNames[quality]) { v ->
            quality = (quality + 2) % 3
            v.text = ("Quality " + qualityNames[quality]).uppercase()
            val level = quality
            glView.queueEvent { renderer.setQualityLevel(level) }
        }
        val sound = pill("Sound", { engine.sfxOn }) { engine.setSfx(!engine.sfxOn) }
        val music = pill("Music", { engine.musicOn }) { engine.setMusic(!engine.musicOn) }
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.END }
        for (v in listOf(q, sound, music)) column.addView(v, LinearLayout.LayoutParams(-2, -2).apply { setMargins(0, 0, 0, dp(8f)) })
        layer.addView(column, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.END).apply { setMargins(0, dp(36f), dp(18f), 0) })

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val tilt = pill("Tilt", { renderer.tiltMode }) { setTilt(!renderer.tiltMode) }
        val hint = pill("Hint", { renderer.hintOn }) {
            renderer.hintOn = !renderer.hintOn
            glView.queueEvent { renderer.resetHint() }
        }
        val follow = pill("Follow cam", { renderer.followOn }) {
            renderer.followOn = !renderer.followOn
            getSharedPreferences("maze3d_ui", MODE_PRIVATE).edit().putBoolean("follow", renderer.followOn).apply()
        }
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
            else com.maze3d.core.Quat.fromAxisAngle(axis, minOf(angle * TILT_GAIN, 2.0f))
        renderer.deviceGravity = boosted.rotate(Vec3(0f, -1f, 0f))
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

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
