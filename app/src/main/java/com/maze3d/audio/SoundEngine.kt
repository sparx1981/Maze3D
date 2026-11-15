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
    private var rollHard = 0
    private var rollWood = 0
    private var windStream = 0
    private var rollVolHard = 0f
    private var rollVolWood = 0f
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
            "sfx_roll_hard", "sfx_roll_wood", "sfx_wind", "sfx_door_open", "sfx_door_close",
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
        val now = System.nanoTime()
        if (now - lastImpactAt < 45_000_000L) return
        lastImpactAt = now
        val s = strength.coerceIn(0f, 1f)
        val tier = when { s < 0.3f -> 's'; s < 0.65f -> 'm'; else -> 'h' }
        var v = Random.nextInt(3)
        if (v == lastVariant) v = (v + 1) % 3
        lastVariant = v
        val vol = 0.3f + 0.7f * s
        play("sfx_impact_$tier$v", vol, pan, jitter(0.06f) * (1.08f - 0.16f * s))
        if (s > 0.4f) play("sfx_spark_${Random.nextInt(2)}", 0.25f + 0.4f * s, pan, jitter(0.1f))
    }

    override fun roll(speed: Float, wood: Boolean) {
        if (!sfxOn || paused) { silenceRolls(); return }
        if (rollHard == 0 && ready("sfx_roll_hard") != null) rollHard = loop("sfx_roll_hard")
        if (rollWood == 0 && ready("sfx_roll_wood") != null) rollWood = loop("sfx_roll_wood")
        val n = speed.coerceIn(0f, 1f)
        val vol = if (n < 0.02f) 0f else Math.pow(n.toDouble(), 0.6).toFloat() * 0.85f
        val rate = 0.55f + 1.15f * n
        // smooth to avoid zipper noise
        rollVolHard += ((if (wood) 0f else vol) - rollVolHard) * 0.25f
        rollVolWood += ((if (wood) vol else 0f) - rollVolWood) * 0.25f
        if (rollHard != 0) { pool.setVolume(rollHard, rollVolHard * 0.8f, rollVolHard * 0.8f); pool.setRate(rollHard, rate) }
        if (rollWood != 0) { pool.setVolume(rollWood, rollVolWood * 0.8f, rollVolWood * 0.8f); pool.setRate(rollWood, rate) }
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
        rollVolHard = 0f; rollVolWood = 0f
        if (rollHard != 0) pool.setVolume(rollHard, 0f, 0f)
        if (rollWood != 0) pool.setVolume(rollWood, 0f, 0f)
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
