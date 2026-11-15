package com.maze3d.gl

import com.maze3d.core.Vec3
import java.util.Random

/** Material ids understood by the scene shader. */
object Mat { const val ENAMEL = 0; const val CARBON = 1; const val BRUSHED = 2; const val WOOD = 3; const val FLAT = 4 }

/** A "finish": floor + wall materials and the studio backdrop. Colours are sRGB. */
class Palette(
    val name: String,
    val floor: Vec3, val floorMat: Int, val floorRough: Float,
    val wall: Vec3, val wallMat: Int, val wallRough: Float,
    val bgTop: Vec3, val bgBottom: Vec3, val accent: Vec3
) {
    companion object {
        private fun c(r: Int, g: Int, b: Int) = Vec3(r / 255f, g / 255f, b / 255f)
        val all = listOf(
            Palette("Carbon & titanium", c(40, 42, 48), Mat.CARBON, 0.4f, c(196, 198, 204), Mat.BRUSHED, 0.30f,
                c(70, 76, 90), c(8, 9, 13), c(120, 200, 255)),
            Palette("Walnut & brass", c(150, 92, 52), Mat.WOOD, 0.45f, c(232, 190, 96), Mat.BRUSHED, 0.16f,
                c(96, 74, 60), c(14, 10, 9), c(255, 214, 140)),
            Palette("Teal enamel & copper", c(24, 92, 104), Mat.ENAMEL, 0.22f, c(232, 142, 90), Mat.BRUSHED, 0.22f,
                c(52, 78, 88), c(7, 11, 14), c(255, 190, 130)),
            Palette("Midnight & coral", c(30, 40, 104), Mat.ENAMEL, 0.18f, c(244, 108, 98), Mat.BRUSHED, 0.26f,
                c(52, 60, 110), c(6, 7, 18), c(255, 150, 140)),
            Palette("Graphite & jade", c(60, 64, 72), Mat.CARBON, 0.4f, c(110, 224, 186), Mat.BRUSHED, 0.2f,
                c(60, 76, 78), c(8, 11, 12), c(170, 255, 220))
        )

        fun random(rnd: Random, not: Palette? = null): Palette {
            val options = all.filter { it !== not }
            return options[rnd.nextInt(options.size)]
        }
    }
}
