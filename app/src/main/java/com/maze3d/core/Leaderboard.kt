package com.maze3d.core

import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/** Tiny client for the daily leaderboard (see /server). Only an arcade-style name and a time are ever sent. */
object Leaderboard {
    /** Set to the deployed server, e.g. "https://marblix-scores.vercel.app". Empty = leaderboard disabled. */
    const val BASE_URL = ""

    class Row(val name: String, val seconds: Float)

    val enabled get() = BASE_URL.isNotEmpty()

    fun top(day: String, done: (List<Row>?) -> Unit) {
        if (!enabled) { done(null); return }
        Thread {
            val rows = try {
                val c = URL("$BASE_URL/api/scores?day=$day").openConnection() as HttpURLConnection
                c.connectTimeout = 6000; c.readTimeout = 6000
                val arr = JSONObject(c.inputStream.bufferedReader().readText()).getJSONArray("scores")
                (0 until arr.length()).map { val o = arr.getJSONObject(it); Row(o.getString("name"), o.getInt("ms") / 1000f) }
            } catch (e: Exception) { null }
            done(rows)
        }.start()
    }

    fun submit(day: String, name: String, seconds: Float, done: (Boolean) -> Unit) {
        if (!enabled) { done(false); return }
        Thread {
            val ok = try {
                val c = URL("$BASE_URL/api/scores").openConnection() as HttpURLConnection
                c.requestMethod = "POST"; c.doOutput = true; c.connectTimeout = 6000; c.readTimeout = 6000
                c.setRequestProperty("Content-Type", "application/json")
                val body = JSONObject().put("day", day).put("name", name).put("ms", (seconds * 1000f).toInt()).toString()
                c.outputStream.use { it.write(body.toByteArray()) }
                c.responseCode in 200..299
            } catch (e: Exception) { false }
            done(ok)
        }.start()
    }
}
