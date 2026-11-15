package com.maze3d.gl

import com.maze3d.core.Vec3
import java.util.Random

/** A material "finish": channel floor, wall metal and the studio backdrop. */
class Palette(val floor: Vec3, val wall: Vec3, val bgTop: Vec3, val bgBottom: Vec3, val accent: Vec3) {
    companion object {
        private fun c(r: Int, g: Int, b: Int) = Vec3(r / 255f, g / 255f, b / 255f)
        private val all = listOf(
            // copper walls on deep teal enamel
            Palette(c(26, 70, 80), c(224, 140, 82), c(52, 58, 68), c(10, 12, 18), c(255, 190, 120)),
            // titanium walls on espresso
            Palette(c(70, 48, 40), c(214, 212, 205), c(70, 58, 52), c(14, 11, 11), c(255, 226, 190)),
            // coral on midnight
            Palette(c(30, 38, 84), c(238, 104, 98), c(40, 46, 80), c(8, 9, 20), c(255, 150, 140)),
            // brass on moss
            Palette(c(40, 72, 48), c(226, 196, 104), c(48, 66, 52), c(8, 14, 10), c(255, 230, 140)),
            // jade on graphite
            Palette(c(52, 54, 60), c(108, 214, 178), c(60, 64, 72), c(10, 11, 14), c(170, 255, 220))
        )

        fun random(rnd: Random) = all[rnd.nextInt(all.size)]
    }
}
