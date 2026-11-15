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
import android.view.ScaleGestureDetector
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.maze3d.core.Vec3
import com.maze3d.gl.MazeRenderer

class MainActivity : Activity(), MazeRenderer.Host, SensorEventListener {

    private lateinit var glView: GLSurfaceView
    private lateinit var renderer: MazeRenderer
    private lateinit var hud: TextView
    private lateinit var banner: TextView
    private lateinit var sensors: SensorManager
    private var gravitySensor: Sensor? = null
    private var baseline: Vec3? = null
    private var tiltBtn: TextView? = null

    private lateinit var scale: ScaleGestureDetector
    private var lastX = 0f
    private var lastY = 0f
    private var lastT = 0L
    private var multiTouch = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        sensors = getSystemService(SENSOR_SERVICE) as SensorManager
        gravitySensor = sensors.getDefaultSensor(Sensor.TYPE_GRAVITY)
            ?: sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        renderer = MazeRenderer(this)
        glView = GLSurfaceView(this).apply {
            setEGLContextClientVersion(3)
            setEGLConfigChooser(8, 8, 8, 8, 24, 0)
            preserveEGLContextOnPause = true
            setRenderer(renderer)
        }
        scale = ScaleGestureDetector(this, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(d: ScaleGestureDetector): Boolean {
                val s = d.scaleFactor
                glView.queueEvent { renderer.nudgeZoom(s) }
                return true
            }
        })
        glView.setOnTouchListener { _, e -> onTouch(e) }

        val root = FrameLayout(this)
        root.addView(glView)
        root.addView(buildOverlay())
        setContentView(root)
        root.systemUiVisibility = (View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    private fun dp(v: Float) = (v * resources.displayMetrics.density).toInt()

    private fun pill(text: String, onClick: (TextView) -> Unit): TextView = TextView(this).apply {
        this.text = text
        setTextColor(Color.WHITE)
        textSize = 14f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        gravity = Gravity.CENTER
        setPadding(dp(16f), dp(10f), dp(16f), dp(10f))
        background = GradientDrawable().apply {
            cornerRadius = dp(22f).toFloat()
            setColor(Color.argb(120, 20, 22, 28))
            setStroke(dp(1f), Color.argb(70, 255, 255, 255))
        }
        setOnClickListener { onClick(this); performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY) }
    }

    private fun setActive(v: TextView, on: Boolean) {
        (v.background as GradientDrawable).setColor(if (on) Color.argb(200, 70, 120, 220) else Color.argb(120, 20, 22, 28))
    }

    private fun buildOverlay(): View {
        val layer = FrameLayout(this)
        hud = TextView(this).apply {
            setTextColor(Color.WHITE)
            textSize = 15f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setShadowLayer(6f, 0f, 2f, Color.BLACK)
        }
        layer.addView(hud, FrameLayout.LayoutParams(-2, -2, Gravity.TOP or Gravity.START).apply {
            setMargins(dp(20f), dp(36f), 0, 0)
        })
        banner = TextView(this).apply {
            text = "Solved!"
            setTextColor(Color.WHITE)
            textSize = 34f
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setShadowLayer(14f, 0f, 3f, Color.BLACK)
            alpha = 0f
        }
        layer.addView(banner, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))

        val bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER }
        val tilt = pill("Tilt") { v ->
            val on = !renderer.tiltMode
            setTilt(on); setActive(v, on)
        }
        tiltBtn = tilt
        val hint = pill("Hint") { v ->
            renderer.hintOn = !renderer.hintOn; setActive(v, renderer.hintOn)
            glView.queueEvent { renderer.resetHint() }
        }
        setActive(hint, true)
        val follow = pill("Follow") { v -> renderer.followOn = !renderer.followOn; setActive(v, renderer.followOn) }
        val ball = pill("Ball") { glView.queueEvent { renderer.resetMarble() } }
        val next = pill("New") { glView.queueEvent { renderer.requestNew() } }
        for (b in listOf(tilt, hint, follow, ball, next)) {
            bar.addView(b, LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(4f), 0, dp(4f), 0) })
        }
        layer.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(0, 0, 0, dp(28f)) })
        return layer
    }

    // ---- touch: drag to rotate, pinch to zoom -------------------------------------------
    private fun onTouch(e: MotionEvent): Boolean {
        scale.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x; lastY = e.y; lastT = e.eventTime; multiTouch = false
                glView.queueEvent { renderer.dragStart() }
            }
            MotionEvent.ACTION_POINTER_DOWN -> multiTouch = true
            MotionEvent.ACTION_MOVE -> {
                if (!multiTouch && !scale.isInProgress) {
                    val dx = e.x - lastX; val dy = e.y - lastY
                    val dt = (e.eventTime - lastT) / 1000f
                    glView.queueEvent { renderer.drag(dx, dy, dt) }
                }
                lastX = e.x; lastY = e.y; lastT = e.eventTime
            }
            MotionEvent.ACTION_POINTER_UP -> { lastX = e.x; lastY = e.y }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> glView.queueEvent { renderer.dragEnd() }
        }
        return true
    }

    // ---- tilt control -------------------------------------------------------------------
    private fun setTilt(on: Boolean) {
        renderer.tiltMode = on
        sensors.unregisterListener(this)
        if (on) {
            baseline = null
            gravitySensor?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        } else {
            renderer.deviceGravity = Vec3(0f, -1f, 0f)
        }
    }

    override fun onSensorChanged(ev: SensorEvent) {
        // Device axes equal screen axes (portrait locked). Gravity "down" is minus the sensor vector.
        val down = Vec3(-ev.values[0], -ev.values[1], -ev.values[2]).normalized()
        val base = baseline ?: down.also { baseline = it }
        // Whatever angle the phone is held at when Tilt is enabled counts as "level".
        val q = com.maze3d.core.Quat.fromTo(base, Vec3(0f, -1f, 0f))
        renderer.deviceGravity = q.rotate(down)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onResume() {
        super.onResume()
        glView.onResume()
        if (renderer.tiltMode) setTilt(true)
    }

    override fun onPause() {
        super.onPause()
        sensors.unregisterListener(this)
        glView.onPause()
    }

    // ---- renderer callbacks (GL thread) -------------------------------------------------
    override fun onHud(shape: String, seconds: Int, solved: Boolean) {
        runOnUiThread {
            hud.text = "$shape   %d:%02d".format(seconds / 60, seconds % 60)
            banner.animate().alpha(if (solved) 1f else 0f).setDuration(300).start()
        }
    }

    override fun onImpact(strength: Float) {
        runOnUiThread { glView.performHapticFeedback(if (strength > 0.5f) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK) }
    }
}
