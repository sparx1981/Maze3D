package com.maze3d.gl

import com.maze3d.core.Vec3
import java.util.Random

/** Material ids understood by the scene shader. */
object Mat {
    const val ENAMEL = 0; const val CARBON = 1; const val BRUSHED = 2; const val WOOD = 3; const val FLAT = 4
    const val SAND = 5; const val MUD = 6
    const val CANDY = 7; const val DEAD = 8; const val ASPHALT = 9; const val TURF = 10; const val SPACE = 11
    const val TABLE = 12; const val LAB = 13; const val ICE = 14; const val SANDSTONE = 15; const val NEON = 16; const val MOSS = 17
    const val STONE = 21; const val PROP = 22; const val HEDGE = 23; const val LEAF = 24
}

/** Wall cross-sections. */
object Profile { const val ROUND = 0; const val SQUARE = 1; const val KERB = 2; const val TAPER = 3; const val TUBE = 4; const val BLOCK = 5; const val FLAT = 6 }

/** Backdrop styles drawn behind the piece. */
object Backdrop { const val STUDIO = 0; const val STARS = 1; const val SKY = 2; const val NEON = 3; const val BOKEH = 4; const val AURORA = 5; const val SHAFTS = 6; const val CITY = 7 }

/**
 * A "finish" or surface theme: floor and wall materials, wall shape and decoration, and the backdrop.
 * Colours are sRGB; wall colours above 1.0 glow.
 */
class Palette(
    val name: String,
    val floor: Vec3, val floorMat: Int, val floorRough: Float,
    val wall: Vec3, val wallMat: Int, val wallRough: Float,
    val bgTop: Vec3, val bgBottom: Vec3, val accent: Vec3,
    val door: Vec3,
    val floor2: Vec3 = floor,
    val wall2: Vec3 = wall,
    val wallTop: Vec3? = null,
    val profile: Int = Profile.ROUND,
    val stripes: Boolean = false,
    val knobs: Boolean = false,
    val backdrop: Int = Backdrop.STUDIO,
    val wallMetal: Float = 1f,
    val glow: Float = 1f,
    val themeId: Int = -1
) {
    /** Same finish, but with a different floor (sand or mud). */
    fun withFloor(color: Vec3, mat: Int, rough: Float) =
        Palette(name, color, mat, rough, wall, wallMat, wallRough, bgTop, bgBottom, accent, door,
            floor2, wall2, wallTop, profile, stripes, knobs, backdrop, wallMetal, glow, themeId)

    companion object {
        private fun c(r: Int, g: Int, b: Int) = Vec3(r / 255f, g / 255f, b / 255f)

        val classic = listOf(
            Palette("Carbon & titanium", c(40, 42, 48), Mat.CARBON, 0.4f, c(196, 198, 204), Mat.BRUSHED, 0.30f,
                c(70, 76, 90), c(8, 9, 13), c(120, 200, 255), c(235, 70, 60)),
            Palette("Walnut & brass", c(150, 92, 52), Mat.WOOD, 0.45f, c(232, 190, 96), Mat.BRUSHED, 0.16f,
                c(96, 74, 60), c(14, 10, 9), c(255, 214, 140), c(40, 150, 190)),
            Palette("Teal enamel & copper", c(24, 92, 104), Mat.ENAMEL, 0.22f, c(232, 142, 90), Mat.BRUSHED, 0.22f,
                c(52, 78, 88), c(7, 11, 14), c(255, 190, 130), c(235, 235, 245)),
            Palette("Midnight & coral", c(30, 40, 104), Mat.ENAMEL, 0.18f, c(244, 108, 98), Mat.BRUSHED, 0.26f,
                c(52, 60, 110), c(6, 7, 18), c(255, 150, 140), c(250, 215, 70)),
            Palette("Graphite & jade", c(60, 64, 72), Mat.CARBON, 0.4f, c(110, 224, 186), Mat.BRUSHED, 0.2f,
                c(60, 76, 78), c(8, 11, 12), c(170, 255, 220), c(250, 90, 140))
        )

        /** Index in the menu's Surface list is 4 + position here. */
        val themes = listOf(
            // 0 Candy: pastel sprinkle floor, red-and-white candy-cane walls
            Palette("Candy", c(250, 188, 214), Mat.CANDY, 0.22f, c(255, 252, 250), Mat.ENAMEL, 0.12f,
                c(255, 168, 214), c(110, 54, 120), c(255, 92, 150), c(60, 200, 255),
                floor2 = c(168, 224, 255), wall2 = c(232, 42, 70), profile = Profile.TUBE, stripes = true,
                backdrop = Backdrop.BOKEH, wallMetal = 0f, themeId = 0),
            // 1 Day of the Dead: marigold flowers on purple, bone walls with knuckle ends
            Palette("Day of the Dead", c(52, 20, 74), Mat.DEAD, 0.4f, c(246, 238, 218), Mat.ENAMEL, 0.45f,
                c(70, 28, 86), c(12, 5, 18), c(255, 150, 24), c(30, 220, 200),
                floor2 = c(255, 140, 20), wall2 = c(232, 222, 198), profile = Profile.TUBE, knobs = true,
                backdrop = Backdrop.BOKEH, wallMetal = 0f, themeId = 1),
            // 2 Motorsport: asphalt, red-and-white kerbs
            Palette("Motorsport", c(54, 56, 60), Mat.ASPHALT, 0.8f, c(242, 242, 242), Mat.ENAMEL, 0.35f,
                c(120, 160, 215), c(26, 30, 40), c(255, 62, 48), c(255, 220, 0),
                wall2 = c(220, 30, 34), profile = Profile.KERB, stripes = true,
                backdrop = Backdrop.SKY, wallMetal = 0f, themeId = 2),
            // 3 Football: mown grass, white pitch lines
            Palette("Football", c(46, 150, 56), Mat.TURF, 0.9f, c(250, 250, 250), Mat.ENAMEL, 0.5f,
                c(110, 176, 240), c(14, 44, 24), c(255, 232, 80), c(255, 80, 60),
                floor2 = c(40, 126, 48), profile = Profile.FLAT, backdrop = Backdrop.SKY, wallMetal = 0f, themeId = 3),
            // 4 Space: panelled hull, metal block walls with a glowing seam, stars
            Palette("Space", c(40, 46, 62), Mat.SPACE, 0.35f, c(208, 214, 226), Mat.BRUSHED, 0.28f,
                c(18, 20, 48), c(2, 3, 10), c(84, 224, 255), c(255, 92, 182),
                wallTop = c(60, 230, 255), profile = Profile.BLOCK, backdrop = Backdrop.STARS, glow = 1f, themeId = 4),
            // 5 Fast food: red/white tablecloth, French-fry walls
            Palette("Fast food", c(222, 40, 40), Mat.TABLE, 0.75f, c(255, 205, 64), Mat.ENAMEL, 0.5f,
                c(255, 192, 96), c(126, 44, 24), c(255, 72, 44), c(60, 200, 90),
                floor2 = c(252, 246, 238), wall2 = c(238, 172, 44), profile = Profile.SQUARE,
                backdrop = Backdrop.BOKEH, wallMetal = 0f, themeId = 5),
            // 6 Science: white lab tiles, glass tubes of coloured liquid with bead joints
            Palette("Science", c(236, 241, 246), Mat.LAB, 0.14f, c(120, 232, 255), Mat.ENAMEL, 0.04f,
                c(206, 228, 248), c(64, 84, 112), c(60, 200, 255), c(255, 90, 90),
                wall2 = c(150, 255, 160), profile = Profile.TUBE, stripes = true, knobs = true,
                backdrop = Backdrop.STUDIO, wallMetal = 0f, glow = 1.1f, themeId = 6),
            // 7 Winter night: powder snow, snow-capped hedges, blue fog and lamps (after the mrdoob labyrinth look)
            Palette("Winter night", c(196, 214, 238), Mat.ICE, 0.08f, c(34, 70, 52), Mat.HEDGE, 0.9f,
                c(18, 34, 72), c(3, 6, 18), c(130, 190, 255), c(255, 190, 120),
                wall2 = c(30, 62, 48), wallTop = c(62, 112, 80), profile = Profile.TUBE, backdrop = Backdrop.STARS, wallMetal = 0f, themeId = 7),
            // 8 Egyptian temple: carved sandstone, gold-capped blocks
            Palette("Egyptian temple", c(216, 178, 120), Mat.SANDSTONE, 0.85f, c(206, 166, 106), Mat.STONE, 0.8f,
                c(244, 156, 76), c(58, 28, 40), c(255, 206, 84), c(40, 170, 204),
                wallTop = c(255, 204, 64), profile = Profile.BLOCK, backdrop = Backdrop.SKY, wallMetal = 0f, themeId = 8),
            // 9 Neon city: cyberpunk night, wet black streets, hot magenta and cyan tubes, yellow accents
            Palette("Neon city", c(10, 8, 20), Mat.NEON, 0.12f, c(66, 10, 52), Mat.ENAMEL, 0.1f,
                c(24, 6, 48), c(2, 2, 10), c(252, 238, 10), c(0, 238, 255),
                wall2 = c(8, 56, 66), wallTop = c(255, 40, 180), profile = Profile.BLOCK, stripes = true, backdrop = Backdrop.CITY, wallMetal = 0f, glow = 2.2f, themeId = 9),
            // 10 Jungle ruins: mossy floor, vine walls
            Palette("Jungle ruins", c(48, 94, 42), Mat.MOSS, 0.55f, c(74, 134, 52), Mat.ENAMEL, 0.55f,
                c(50, 110, 60), c(6, 26, 12), c(196, 255, 126), c(255, 160, 40),
                wall2 = c(112, 162, 62), profile = Profile.TUBE, stripes = true, backdrop = Backdrop.SHAFTS, wallMetal = 0f, themeId = 10),
            // 11 Glow: a forest floor at night; the only light comes from the mushrooms (after the spore demo's Foxfire look)
            Palette("Glow", c(8, 13, 11), Mat.MOSS, 0.8f, c(12, 18, 16), Mat.ENAMEL, 0.55f,
                c(3, 7, 11), c(13, 28, 26), c(140, 255, 92), c(77, 255, 192),
                wall2 = c(14, 22, 20), wallTop = c(30, 110, 80), profile = Profile.TUBE, knobs = false,
                backdrop = Backdrop.STARS, wallMetal = 0f, glow = 0.55f, themeId = 11),
            // 12 Fall: golden leaf litter, amber hedges, a low warm sun
            Palette("Fall", c(110, 56, 20), Mat.LEAF, 0.85f, c(150, 64, 16), Mat.HEDGE, 0.9f,
                c(255, 176, 84), c(96, 44, 30), c(255, 150, 40), c(255, 214, 80),
                wall2 = c(130, 56, 18), wallTop = c(196, 104, 26), profile = Profile.TUBE,
                backdrop = Backdrop.SKY, wallMetal = 0f, themeId = 12)
        )

        val all = classic + themes

        fun random(rnd: Random, not: Palette? = null): Palette {
            val options = classic.filter { it !== not }
            return options[rnd.nextInt(options.size)]
        }

        /** Level start: a classic finish, or (about half the time) one of the themes. */
        fun randomAny(rnd: Random, not: Palette? = null): Palette {
            val options = all.filter { it !== not }
            return if (rnd.nextFloat() < 0.5f) themes.filter { it !== not }.let { it[rnd.nextInt(it.size)] } else random(rnd, not)
        }
    }
}
