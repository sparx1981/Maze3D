package com.maze3d.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Sound effects through [SoundPool] (low latency, panned and pitch-shifted per event) plus a looping
 * ambient music track. Effects and music can be switched independently; the choice is remembered.
 */
class SoundEngine(private val ctx: Context) : Audio {

    private val prefs = ctx.getSharedPreferences("maze3d_audio", Context.MODE_PRIVATE)
    @Volatile var sfxOn = prefs.getBoolean("sfx", true)
    @Volatile var impactOn = prefs.getBoolean("impact", true)
        private set
    @Volatile var musicOn = prefs.getBoolean("music", true)
        private set

    private val pool: SoundPool = SoundPool.Builder()
        .setMaxStreams(16)
        .setAudioAttributes(
            AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()
        ).build()
    private val ids = HashMap<String, Int>()
    private val loaded = HashSet<Int>()

    // looping streams
    private val rollNames = arrayOf("sfx_roll_hard", "sfx_roll_wood", "sfx_roll_sand", "sfx_roll_mud")
    private val rollStreams = IntArray(4)
    private val rollVols = FloatArray(4)
    private var windStream = 0
    private var windVol = 0f
    private var rainStream = 0
    private var rainVol = 0f
    private var lastSplashAt = 0L
    private var paused = false

    private var music: MediaPlayer? = null
    private var musicVol = 0f
    private val handler = Handler(Looper.getMainLooper())

    private var lastImpactAt = 0L
    private var lastVariant = -1

    init {
        pool.setOnLoadCompleteListener { _, id, status -> if (status == 0) synchronized(loaded) { loaded.add(id) } }
        val names = listOf(
            "sfx_impact_s0", "sfx_impact_s1", "sfx_impact_s2", "sfx_impact_m0", "sfx_impact_m1", "sfx_impact_m2",
            "sfx_impact_h0", "sfx_impact_h1", "sfx_impact_h2", "sfx_spark_0", "sfx_spark_1",
            "sfx_roll_hard", "sfx_roll_wood", "sfx_roll_sand", "sfx_roll_mud", "sfx_wind", "sfx_door_open", "sfx_door_close",
            "sfx_hole_open", "sfx_hole_close", "sfx_hole_fall", "sfx_respawn", "sfx_goal",
            "sfx_level_grow", "sfx_level_sink", "sfx_ui_click", "sfx_ui_on", "sfx_ui_off", "sfx_rain", "sfx_splash_0", "sfx_splash_1"
        )
        for (n in names) {
            val res = ctx.resources.getIdentifier(n, "raw", ctx.packageName)
            if (res != 0) ids[n] = pool.load(ctx, res, 1)
        }
        if (musicOn) startMusic()
    }

    // ---- helpers -------------------------------------------------------------------------
    private fun ready(name: String): Int? {
        val id = ids[name] ?: return null
        return if (synchronized(loaded) { id in loaded }) id else null
    }

    private fun play(name: String, volume: Float, pan: Float = 0f, rate: Float = 1f): Int {
        if (!sfxOn || paused) return 0
        val id = ready(name) ?: return 0
        val a = ((pan.coerceIn(-1f, 1f) + 1f) * PI.toFloat() / 4f)
        val v = volume.coerceIn(0f, 1f)
        return pool.play(id, v * cos(a) * 1.06f, v * sin(a) * 1.06f, 1, 0, rate.coerceIn(0.5f, 2f))
    }

    private fun jitter(amount: Float = 0.05f) = 1f + (Random.nextFloat() - 0.5f) * 2f * amount

    // ---- events --------------------------------------------------------------------------
    override fun impact(strength: Float, pan: Float) {
        if (!impactOn) return
        val now = System.nanoTime()
        if (now - lastImpactAt < 45_000_000L) return
        lastImpactAt = now
        val s = strength.coerceIn(0f, 1f)
        val tier = when { s < 0.3f -> 's'; s < 0.65f -> 'm'; else -> 'h' }
        var v = Random.nextInt(3)
        if (v == lastVariant) v = (v + 1) % 3
        lastVariant = v
        // a hard ball on a hard wall: soft taps are quick and light, hard hits heavier and slightly lower; no metallic ring on top
        val vol = 0.25f + 0.75f * s
        play("sfx_impact_$tier$v", vol, pan, jitter(0.05f) * (1.06f - 0.12f * s))
    }

    override fun roll(speed: Float, kind: Int) {
        if (!sfxOn || paused) { silenceRolls(); return }
        for (k in rollNames.indices) if (rollStreams[k] == 0 && ready(rollNames[k]) != null) rollStreams[k] = loop(rollNames[k])
        val n = speed.coerceIn(0f, 1f)
        // louder and a little brighter with speed, but over a narrow pitch range so it stays a natural roll, never a whine
        val vol = if (n < 0.015f) 0f else (Math.pow(n.toDouble(), 0.7).toFloat() * 0.8f).coerceAtMost(0.8f)
        val rate = 0.78f + 0.5f * n
        val sel = kind.coerceIn(0, rollNames.size - 1)
        for (k in rollNames.indices) {
            rollVols[k] += ((if (k == sel) vol else 0f) - rollVols[k]) * 0.25f
            val s = rollStreams[k]
            if (s != 0) { pool.setVolume(s, rollVols[k] * 0.8f, rollVols[k] * 0.8f); pool.setRate(s, rate) }
        }
    }

    override fun doorOpen(volume: Float, pan: Float) { play("sfx_door_open", volume, pan, jitter()) }
    override fun doorClose(volume: Float, pan: Float) { play("sfx_door_close", volume, pan, jitter()) }
    override fun holeOpen(volume: Float, pan: Float) { play("sfx_hole_open", volume, pan, jitter()) }
    override fun holeClose(volume: Float, pan: Float) { play("sfx_hole_close", volume, pan, jitter()) }
    override fun fall(pan: Float) { play("sfx_hole_fall", 0.95f, pan) }
    override fun respawn(pan: Float) { play("sfx_respawn", 0.7f, pan) }
    override fun goal() { play("sfx_goal", 0.95f) }
    override fun levelGrow() { play("sfx_level_grow", 0.7f) }
    override fun levelSink() { play("sfx_level_sink", 0.7f) }

    override fun wind(amount: Float) {
        if (!sfxOn || paused) { if (windStream != 0) pool.setVolume(windStream, 0f, 0f); windVol = 0f; return }
        if (windStream == 0) { if (amount > 0.02f && ready("sfx_wind") != null) windStream = loop("sfx_wind") else return }
        windVol += (amount.coerceIn(0f, 1f) * 0.55f - windVol) * 0.05f
        pool.setVolume(windStream, windVol, windVol)
    }

    override fun rain(amount: Float) {
        if (!sfxOn || paused) { if (rainStream != 0) pool.setVolume(rainStream, 0f, 0f); rainVol = 0f; return }
        if (rainStream == 0) { if (amount > 0.02f && ready("sfx_rain") != null) rainStream = loop("sfx_rain") else return }
        rainVol += (amount.coerceIn(0f, 1f) * 0.7f - rainVol) * 0.04f
        pool.setVolume(rainStream, rainVol, rainVol)
    }

    override fun splash(volume: Float, pan: Float) {
        val now = System.nanoTime()
        if (now - lastSplashAt < 160_000_000L) return
        lastSplashAt = now
        play("sfx_splash_${Random.nextInt(2)}", volume.coerceIn(0.15f, 0.9f), pan, jitter(0.1f))
    }

    private fun loop(name: String): Int {
        val id = ready(name) ?: return 0
        return pool.play(id, 0f, 0f, 1, -1, 1f)
    }

    private fun silenceRolls() {
        for (k in rollVols.indices) { rollVols[k] = 0f; if (rollStreams[k] != 0) pool.setVolume(rollStreams[k], 0f, 0f) }
    }

    // ---- UI sounds (called on the UI thread) -----------------------------------------------
    fun uiClick() { play("sfx_ui_click", 0.6f) }
    fun uiToggle(on: Boolean) { play(if (on) "sfx_ui_on" else "sfx_ui_off", 0.6f) }

    // ---- switches ------------------------------------------------------------------------
    fun setSfx(on: Boolean) {
        sfxOn = on
        prefs.edit().putBoolean("sfx", on).apply()
        if (!on) { silenceRolls(); pool.autoPause() } else if (!paused) pool.autoResume()
    }

    fun setImpact(on: Boolean) { impactOn = on; prefs.edit().putBoolean("impact", on).apply() }

    fun setMusic(on: Boolean) {
        musicOn = on
        prefs.edit().putBoolean("music", on).apply()
        if (on) startMusic() else fadeMusic(0f) { music?.pause() }
    }

    // ---- music -----------------------------------------------------------------------------
    private fun startMusic() {
        if (paused) return
        handler.post {
            if (music == null) {
                val res = ctx.resources.getIdentifier("music_ambient", "raw", ctx.packageName)
                if (res == 0) return@post
                music = MediaPlayer.create(ctx, res)?.apply {
                    isLooping = true
                    setAudioAttributes(
                        AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
                    )
                    setVolume(0f, 0f)
                }
            }
            music?.let { if (!it.isPlaying) it.start() }
            fadeMusic(MUSIC_LEVEL) {}
        }
    }

    private var fadeRunnable: Runnable? = null
    private fun fadeMusic(target: Float, done: () -> Unit) {
        fadeRunnable?.let { handler.removeCallbacks(it) }
        val r = object : Runnable {
            override fun run() {
                val m = music ?: return
                val d = target - musicVol
                if (kotlin.math.abs(d) < 0.01f) { musicVol = target; m.setVolume(musicVol, musicVol); done(); return }
                musicVol += d * 0.12f
                m.setVolume(musicVol, musicVol)
                handler.postDelayed(this, 40)
            }
        }
        fadeRunnable = r
        handler.post(r)
    }

    // ---- lifecycle -----------------------------------------------------------------------------
    fun onPause() {
        paused = true
        silenceRolls()
        if (windStream != 0) pool.setVolume(windStream, 0f, 0f)
        if (rainStream != 0) pool.setVolume(rainStream, 0f, 0f)
        pool.autoPause()
        handler.post { music?.pause() }
    }

    fun onResume() {
        paused = false
        if (sfxOn) pool.autoResume()
        if (musicOn) startMusic()
    }

    fun release() {
        handler.post { music?.release(); music = null }
        pool.release()
    }

    companion object { const val MUSIC_LEVEL = 0.55f }
}
