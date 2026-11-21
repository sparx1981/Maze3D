package com.maze3d.gl

import com.maze3d.core.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural, theme-specific models. Each is built about 1 unit tall, standing on z = 0, with a footprint
 * radius of at most 0.5, so the placer can scale them to fit a wall joint, a dead end or a landmark slot.
 */
object PropLibrary {
    private fun c(r: Int, g: Int, b: Int) = PM.rgb(r, g, b)
    private fun glow(v: Vec3, k: Float) = v * k

    /** How a placed prop (or a part of it) moves. Parts are drawn at each offset in [at], in the prop's own units. */
    object Anim { const val SPIN = 0; const val BOB = 1; const val SWAY = 2; const val FLICKER = 3; const val WOBBLE = 4; const val NOD = 5; const val SCREW = 6; const val CROWD = 7; const val FLAG = 8 }
    class AnimSpec(val part: PropMesh, val type: Int, val speed: Float = 1f, val amp: Float = 1f, val whole: Boolean = false,
                   val at: List<Vec3> = listOf(Vec3.ZERO), val pivotZ: Float = 0f,
                   /** Flag cloth (type [Anim.FLAG]): wave height and waves per unit length, and the cloth length (negative = hangs from a bar). */
                   val flagAmp: Float = 0f, val flagWaves: Float = 0f, val flagLen: Float = 0f)
    /** A small flock circling above the maze: body plus two flapping wings (left wing along +y, right along -y). */
    class Bird(val body: PropMesh, val wingL: PropMesh, val wingR: PropMesh, val count: Int, val size: Float, val height: Float, val flap: Float, val glide: Boolean = false)
    /** Extra flocks beyond the first (e.g. toucans as well as parrots). */

    class ThemeProps(val junction: PropMesh, val deadA: PropMesh, val deadB: PropMesh, val landmark: PropMesh,
                     val deadC: PropMesh? = null, val junction2: PropMesh? = null,
                     val anims: Map<PropMesh, AnimSpec> = emptyMap(), val bird: Bird? = null, val more: List<PropMesh> = emptyList(),
                     val junctionMore: List<PropMesh> = emptyList(), val floor: List<PropMesh> = emptyList(), val birds2: Bird? = null, val junctionShare: Float = 0.2f, val birds3: Bird? = null, val ring: PropMesh? = null, val ringCount: Int = 0)

    private fun whole(m: PropMesh, type: Int, speed: Float = 1f, amp: Float = 1f, pivotZ: Float = 0f) = m to AnimSpec(m, type, speed, amp, true, pivotZ = pivotZ)
    private fun part(host: PropMesh, part: PropMesh, type: Int, at: List<Vec3>, speed: Float = 1f, amp: Float = 1f) = host to AnimSpec(part, type, speed, amp, false, at)
    /** Cloth [cloth] flying from the poles of [host] at [at]: rippling in a gusty wind (see SCENE_VS uFlag). */
    private fun flagPart(host: PropMesh, cloth: PropMesh, len: Float, at: List<Vec3>, waveH: Float = 0.09f, waves: Float = 1.6f, speed: Float = 1f) =
        host to flagSpec(cloth, len, at, waveH, waves, speed)
    /** Preview only: the cloth of a flag spec bent into one frame of its ripple (mirrors SCENE_VS uFlag). */
    fun flagPose(sp: AnimSpec, phase: Float): PropMesh {
        val m = sp.part; val pos = m.pos.copyOf(); val L = kotlin.math.abs(sp.flagLen); val hang = sp.flagLen < 0f
        val k = sp.flagWaves * 6.2831853f
        for (i in 0 until m.vertexCount) {
            val x = pos[i * 3]; val z = pos[i * 3 + 2]
            val d = if (hang) maxOf(-z, 0f) else maxOf(x, 0f)
            val sN = (d / L).coerceIn(0f, 1f); val env = sN * (0.35f + 0.65f * sN)
            val wave = sin(k * d - phase) + 0.35f * sin(2.3f * k * d - 1.7f * phase + 1.3f) + 0.25f * sin(0.6f * k * d - 0.55f * phase + 0.4f)
            val off = sp.flagAmp * env * wave
            pos[i * 3 + 1] += off
            if (!hang) { pos[i * 3 + 2] -= sp.flagAmp * 0.25f * sN * sN * (1f - 0.5f * sin(phase * 0.37f)); pos[i * 3] -= 0.12f * kotlin.math.abs(off) * sN }
        }
        return PropMesh(pos, m.nrm, m.col, m.idx)
    }
    fun flagSpec(cloth: PropMesh, len: Float, at: List<Vec3>, waveH: Float = 0.09f, waves: Float = 1.6f, speed: Float = 1f) =
        AnimSpec(cloth, Anim.FLAG, speed, 1f, false, at, 0f, waveH * kotlin.math.abs(len), waves / kotlin.math.abs(len), len)


    private val cache = HashMap<Int, ThemeProps>()
    fun forTheme(id: Int): ThemeProps? = if (id < 0) null else cache.getOrPut(id) { build(id) }

    private fun build(id: Int): ThemeProps = when (id) {
        0 -> { val lp = lollipop(); val cc = candyCane()
            val fp = fruitPastilles(); val ws = wrappedSweet(); val lw = liquoriceWheel(); val cn = candyCorn()
            ThemeProps(gumdrop(), cupcake(), lp, cc, allsortsPile(), allsortLayered(0.5f),
                mapOf(whole(lp, Anim.SPIN, 0.6f), whole(cc, Anim.SPIN, 0.25f), whole(lw, Anim.SPIN, 0.5f), whole(ws, Anim.WOBBLE, 1f, 0.6f)), butterfly(c(255, 120, 200), c(120, 220, 255)),
                listOf(fp, ws, lw, cn), junctionMore = listOf(fp, cn, ws)) }
        1 -> { val mg = marigold(); val cv = candleVotive(); val cb = candelabra()
            ThemeProps(mg, sugarSkull(), cv, cb, null, null,
                mapOf(whole(mg, Anim.SWAY, 1f, 1.2f), part(cv, flame(0.05f, 0.22f), Anim.FLICKER, listOf(Vec3(0f, 0f, 0.55f))),
                    part(cb, flame(0.032f, 0.13f), Anim.FLICKER, listOf(Vec3(-0.3f, 0f, 0.94f), Vec3(0f, 0f, 0.94f), Vec3(0.3f, 0f, 0.94f)))),
                butterfly(c(255, 130, 20), c(40, 20, 10))) }
        2 -> { val tr = trophy(); val fp = flagPole()
            val gs = grandstand(c(220, 30, 30), c(250, 250, 250), c(30, 30, 34))
            ThemeProps(trafficCone(), tyreStack(), tr, fp, raceCar(), null, mapOf(whole(tr, Anim.SPIN, 0.5f), flagPart(fp, chequerCloth, flagLen, listOf(Vec3(0.02f, 0f, 0.62f))), crowdSpec(gs)), null, listOf(gs, gs, gs), ring = gs, ringCount = 18) }
        3 -> { val cf = cornerFlag(); val fb = football(); val tr = trophy()
            val gs = grandstand(c(40, 90, 220), c(250, 214, 40), c(40, 140, 60))
            ThemeProps(cf, fb, tr, floodlight(), goalNet(), null, mapOf(flagPart(cf, cornerCloth, 0.3f, listOf(Vec3(0.018f, 0f, 0.68f)), 0.08f, 1.4f, 1.1f), whole(fb, Anim.BOB, 1f, 1f), whole(tr, Anim.SPIN, 0.5f), crowdSpec(gs)),
                bird(c(250, 250, 250), c(200, 200, 205), 5, 0.5f, 3.2f, 7f), listOf(gs, gs)) }
        4 -> { val ds = dish(); val rk = rocket()
            val mr = moonRover()
            ThemeProps(beacon(), ds, canister(), rk, mr, null,
                mapOf(whole(ds, Anim.SPIN, 0.35f), whole(mr, Anim.NOD, 0.4f, 0.5f), part(rk, flame(0.07f, 0.22f, c(120, 200, 255), c(255, 255, 255)), Anim.FLICKER, listOf(Vec3(0f, 0f, 0.02f)), 1.5f, -1f))) }
        5 -> { val bg = burger()
            ThemeProps(ketchup(), bg, sodaCup(), friesBox(), null, null, mapOf(whole(bg, Anim.WOBBLE, 1f, 0.7f)),
                bird(c(250, 250, 250), c(220, 220, 220), 4, 0.55f, 3f, 6f)) }
        6 -> { val at = atom(); val bk = beaker()
            val tc = teslaCoil()
            ThemeProps(flask(), bk, testTubes(), at, microscope(), tc, mapOf(whole(at, Anim.SPIN, 1.2f), part(bk, bubbles(), Anim.BOB, listOf(Vec3(0f, 0f, 0.15f)), 2f, 1f),
                part(tc, teslaSparks(), Anim.SPIN, listOf(Vec3.ZERO), 4f))) }
        7 -> { val sp = snowPine(); val sm = snowman()
            val pg = penguin()
            ThemeProps(lampPost(), sm, sp, sp, iceCrystals(), null, mapOf(whole(sp, Anim.SWAY, 0.7f, 0.8f), whole(sm, Anim.NOD, 0.6f, 0.6f), whole(pg, Anim.BOB, 1.4f, 0.8f)),
                bird(c(200, 40, 30), c(110, 70, 50), 4, 0.35f, 2.2f, 9f), listOf(pg)) }
        8 -> { val sx = sphinx()
            val tq = torch(); val pt = palmTree()
            ThemeProps(obelisk(), canopicJar(), sx, pyramidProp(), pt, tq, mapOf(whole(pt, Anim.SWAY, 0.8f, 1f), part(tq, flame(0.06f, 0.2f), Anim.FLICKER, listOf(Vec3(0f, 0f, 0.78f)))), falcon(), listOf(mummy())) }
        9 -> { val hg = hologram(); val sA = holoSign(1, nMag); val sB = holoSign(2, nCyan); val mast = roofMast(); val bl = adBlimp(); val pl = palmPlanter()
            ThemeProps(sA, vendingMachine(), noodleStall(), bl, neonTriangle(), sB,
                mapOf(whole(hg, Anim.SPIN, 1.1f), whole(bl, Anim.BOB, 0.4f, 0.6f), whole(pl, Anim.SWAY, 0.6f, 0.5f)), hoverCar(),
                listOf(chevronBarrier(), hg, neonSign(), terminal()), junctionMore = listOf(mast, holoSign(3, nYel)), floor = listOf(streetLamp(), pl, streetLamp(), chevronBarrier()),
                birds2 = drone(), junctionShare = 0.4f) }
        10 -> { val fn = fern(); val jt = bigJungleTree(); val mk = monkey(); val bn = bananaPlant(); val br = bromeliad(); val bb = bamboo(); val ms = monstera(); val hl = heliconia()
            val sn = snake(); val jt2 = jungleTree()
            ThemeProps(fn, idol(), jungleStump(), jt, mk, br,
                mapOf(whole(fn, Anim.SWAY, 1.1f, 1.4f), whole(mk, Anim.BOB, 1.2f, 1f), whole(bn, Anim.SWAY, 0.9f, 1.3f), whole(bb, Anim.SWAY, 0.7f, 1.2f),
                    whole(ms, Anim.SWAY, 1f, 1.2f), whole(hl, Anim.SWAY, 1.1f, 1.3f), whole(br, Anim.SWAY, 1.2f, 0.8f), whole(jt2, Anim.SWAY, 0.7f, 0.8f),
                    part(jt, sn, Anim.SWAY, listOf(Vec3(0.26f * cos(0.4f), 0.26f * sin(0.4f), 0.76f)), 0.8f, 2.5f)),
                parrot(), listOf(bn, bb, ms, hl, jt2), junctionMore = listOf(bn, ms, hl, bb), floor = listOf(fn, br, ms, hl, bb, jt2, fn, br), birds2 = toucan(), junctionShare = 0.85f, birds3 = butterfly(c(60, 140, 255), c(20, 20, 40))) }
        11 -> { val gb = glowBell(); val gc = glowCluster(); val gp = glowParasol(); val gg = glowGiant(); val bn = bonnets(); val ik = inkcap()
            ThemeProps(gb, gc, gp, gg, bn, ik, mapOf(whole(gb, Anim.SWAY, 0.5f, 0.6f), whole(gc, Anim.SWAY, 0.6f, 0.5f), whole(gp, Anim.SWAY, 0.45f, 0.7f),
                whole(bn, Anim.SWAY, 0.7f, 0.6f), whole(ik, Anim.SWAY, 0.55f, 0.6f))) }
        12 -> { val at = autumnTree(); val pk = pumpkin()
            val sc = scarecrow()
            ThemeProps(pk, leafPile(), hayBale(), at, sc, pk, mapOf(whole(at, Anim.SWAY, 0.8f, 0.9f), whole(sc, Anim.SWAY, 1.1f, 1.3f)), crow()) }
        13 -> { val dk = duck(); val tr = toyRocket(); val sb = starBlock()
            val jb = jackInBox(); val st = spinningTop()
            ThemeProps(legoBrick(), dk, toyCar(), tr, blockStack(), sb, mapOf(whole(dk, Anim.BOB, 1f, 1f), whole(tr, Anim.BOB, 0.5f, 0.8f), whole(sb, Anim.SPIN, 0.6f),
                whole(jb, Anim.BOB, 1.6f, 1.4f), whole(st, Anim.SPIN, 6f)), paperPlane(), listOf(jb, st)) }
        14 -> { val sp = spool(); val pp = pomPom(); val ft = feltTree(); val yb = yarnBall()
            ThemeProps(buttonProp(), sp, pp, ft, heartPatch(), yb, mapOf(whole(sp, Anim.SPIN, 0.7f), whole(pp, Anim.WOBBLE, 1.2f, 1f), whole(ft, Anim.SWAY, 0.8f, 1f), whole(yb, Anim.SPIN, 0.4f)),
                butterfly(c(255, 150, 190), c(250, 230, 200))) }
        15 -> { val en = eggNest(); val fn = fern(); val jt = jungleTree(); val dn = dino(); val rp = raptor(); val sk = dinoSkeleton(); val eg = bigEggs()
            val bn = bananaPlant(); val bb = bamboo(); val ms = monstera(); val br = bromeliad()
            ThemeProps(boneProp(), en, fn, volcanoProp(), dn, jt,
                mapOf(whole(en, Anim.WOBBLE, 0.8f, 0.6f), whole(fn, Anim.SWAY, 1f, 1.3f), whole(jt, Anim.SWAY, 0.6f, 0.8f), whole(dn, Anim.NOD, 0.9f, 1.2f),
                    whole(rp, Anim.NOD, 1.3f, 1.4f), whole(eg, Anim.WOBBLE, 0.7f, 0.5f), whole(bn, Anim.SWAY, 0.9f, 1.3f), whole(ms, Anim.SWAY, 1f, 1.2f)),
                pterodactyl(), listOf(rp, sk, eg, dn, sk), junctionMore = listOf(bn, ms, bb, fn), floor = listOf(fn, ms, br, bb), junctionShare = 0.55f,
                birds2 = dragonfly(), birds3 = mosquito()) }
        16 -> { val dr = drill(); val gp = gearProp(); val b1 = boltProp(); val b2 = boltProp(); val nut = hexNut()
            ThemeProps(b1, toolbox(), wrench(), hammerProp(), gp, b2, mapOf(whole(gp, Anim.SPIN, 0.9f),
                part(b1, nut, Anim.SCREW, listOf(Vec3(0f, 0f, 0.16f)), 0.8f, 0.34f), part(b2, nut, Anim.SCREW, listOf(Vec3(0f, 0f, 0.16f)), 0.6f, 0.34f),
                dr to AnimSpec(drillBit(), Anim.SPIN, 9f, 1f, false, listOf(Vec3(0.06f, 0f, 0.84f)))), null, listOf(dr)) }
        17 -> { val bz = brazier()
            val lp = lavaPool()
            ThemeProps(emberSpire(), lavaRocks(), obsidianCrystal(), bz, volcanoProp(), emberSpire(),
                mapOf(part(bz, flame(0.16f, 0.42f, c(255, 90, 20), c(255, 210, 60)), Anim.FLICKER, listOf(Vec3(0f, 0f, 0.48f)), 1.2f), whole(lp, Anim.WOBBLE, 0.7f, 0.5f)), null, listOf(lp)) }
        18 -> { val gb = gummyBear(); val pd = pudding(); val ch = cherryProp()
            ThemeProps(gb, pd, jellyBean(), jellyDrop(), ch, gb, mapOf(whole(gb, Anim.WOBBLE, 1.1f, 1f), whole(pd, Anim.WOBBLE, 0.9f, 1.3f), whole(ch, Anim.SWAY, 1.2f, 1.2f)),
                butterfly(c(120, 255, 160), c(255, 240, 120))) }
        else -> medieval()
    }

    private fun medieval(): ThemeProps {
        val cf = campfire(); val bn = banner(); val ca = cage(); val sw = swordInStone(); val sh = shieldStand(); val cs = castle()
        val gd = groundDragon()
        return ThemeProps(bn, sw, sh, cs, ca, bn,
            mapOf(part(cf, flame(0.12f, 0.36f), Anim.FLICKER, listOf(Vec3(0f, 0f, 0.08f)), 1.1f), flagPart(bn, gonfalonCloth, -0.52f, listOf(Vec3(0.02f, 0f, 0.865f)), 0.07f, 0.9f, 0.6f), whole(ca, Anim.NOD, 0.7f, 0.5f),
                flagPart(cs, castlePennantA, 0.16f, listOf(Vec3(0.306f, 0.3f, 1.03f), Vec3(-0.294f, 0.3f, 1.03f), Vec3(0.306f, -0.3f, 1.03f), Vec3(-0.294f, -0.3f, 1.03f)), 0.12f, 1.4f, 1.4f),
                whole(gd, Anim.NOD, 0.35f, 0.35f)),
            dragon(), listOf(cf, cf, ca, sw, gd, gd), birds2 = bird(c(40, 40, 46), c(30, 30, 36), 4, 0.45f, 2.6f, 6f))
    }

    // ---------------------------------------------------------------- shared animated parts
    /** A flame standing on z = 0: a hot core inside a softer outer tongue (colours above 1 glow and light the scene). */
    fun flame(r: Float, h: Float, outer: Vec3 = c(255, 120, 30), inner: Vec3 = c(255, 220, 90)) = PM().apply {
        col = outer * 2.2f; lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(r, h * 0.18f), floatArrayOf(r * 0.8f, h * 0.45f), floatArrayOf(r * 0.3f, h * 0.8f), floatArrayOf(0f, h)), 12)
        col = inner * 2.8f; lathe(listOf(floatArrayOf(0f, h * 0.02f), floatArrayOf(r * 0.55f, h * 0.18f), floatArrayOf(r * 0.4f, h * 0.45f), floatArrayOf(0f, h * 0.7f)), 10)
    }.build()

    private fun bubbles() = PM().apply {
        col = c(170, 255, 200) * 1.3f
        for (i in 0 until 6) { val a = i * 2.3f; at(cos(a) * 0.08f, sin(a) * 0.08f, i * 0.07f) { sphere(0.025f + 0.008f * (i % 3), 8, 6) } }
    }.build()

    // ---------------------------------------------------------------- flying creatures (body along +x, wings along ±y)
    private fun wingPair(span: Float, chord: Float, cA: Vec3, cB: Vec3, tip: Float = 0.5f): Pair<PropMesh, PropMesh> {
        fun wing(sgn: Float) = PM().apply {
            val n = 5
            for (k in 0 until n) {
                val y0 = sgn * span * k / n; val y1 = sgn * span * (k + 1) / n
                val f0 = 1f - (1f - tip) * k / n; val f1 = 1f - (1f - tip) * (k + 1) / n
                col = if (k >= n - 2) cB else cA
                push(); translate(-chord * 0.15f, (y0 + y1) * 0.5f, 0f); box(chord * (f0 + f1) * 0.5f, kotlin.math.abs(y1 - y0) * 1.02f, 0.012f); pop()
            }
        }.build()
        return wing(1f) to wing(-1f)
    }

    private fun bird(cBody: Vec3, cWing: Vec3, count: Int, size: Float, height: Float, flap: Float): Bird {
        val body = PM().apply {
            col = cBody; at { scale(1f, 0.6f, 0.6f); sphere(0.12f, 12, 8) }
            at(0.13f, 0f, 0.03f) { sphere(0.06f, 10, 8) }
            col = c(250, 180, 40); at(0.19f, 0f, 0.03f) { rotY((PI / 2).toFloat()); cone(0.02f, 0.06f, 6) }
            col = cWing; at(-0.16f, 0f, 0f) { scale(1f, 1f, 0.2f); cone(0.06f, 0.12f, 6) }
        }.build()
        val (l, r) = wingPair(0.3f, 0.14f, cWing, cWing * 0.8f)
        return Bird(body, l, r, count, size, height, flap)
    }

    private fun parrot(): Bird {
        val body = PM().apply {
            col = c(230, 40, 40); at { scale(1f, 0.6f, 0.6f); sphere(0.13f, 14, 10) }
            at(0.14f, 0f, 0.04f) { sphere(0.065f, 12, 8) }
            col = c(255, 230, 120); at(0.2f, 0f, 0.02f) { rotY(2.0f); cone(0.025f, 0.07f, 8) }
            col = c(250, 250, 250); for (s in intArrayOf(-1, 1)) at(0.165f, s * 0.04f, 0.06f) { sphere(0.015f, 6, 4) }
            col = c(40, 100, 230); push(); translate(-0.13f, 0f, 0f); rotY((PI / 2).toFloat() + 0.2f); scale(0.4f, 1f, 1f); cylinder(0.06f, 0.26f, 8, 0.01f); pop()
        }.build()
        val (l, r) = wingPair(0.34f, 0.16f, c(230, 40, 40), c(40, 110, 230), 0.45f)
        return Bird(body, l, r, 5, 0.6f, 2.6f, 8f)
    }

    private fun crow(): Bird {
        val b = bird(c(30, 30, 36), c(24, 24, 30), 5, 0.55f, 2.8f, 6f)
        return Bird(b.body, b.wingL, b.wingR, 6, 0.55f, 2.8f, 6f, glide = true)
    }

    private fun falcon(): Bird {
        val b = bird(c(150, 110, 70), c(110, 80, 50), 3, 0.75f, 3.6f, 4f)
        return Bird(b.body, b.wingL, b.wingR, 3, 0.75f, 3.6f, 4f, glide = true)
    }

    private fun pterodactyl(): Bird {
        val body = PM().apply {
            col = c(150, 110, 80); at { scale(1f, 0.5f, 0.5f); sphere(0.12f, 12, 8) }
            at(0.12f, 0f, 0.05f) { sphere(0.05f, 10, 8) }
            push(); translate(0.15f, 0f, 0.06f); rotY((PI / 2).toFloat() + 0.25f); cone(0.03f, 0.2f, 8); pop()          // long beak
            col = c(200, 80, 50); push(); translate(0.08f, 0f, 0.09f); rotY(-0.9f); cone(0.025f, 0.14f, 6); pop()       // crest
        }.build()
        val (l, r) = wingPair(0.48f, 0.2f, c(170, 120, 90), c(120, 80, 60), 0.2f)
        return Bird(body, l, r, 3, 0.9f, 3.6f, 3f, glide = true)
    }

    private fun butterfly(cA: Vec3, cB: Vec3): Bird {
        val body = PM().apply { col = c(40, 30, 30); at { scale(1f, 0.3f, 0.3f); sphere(0.08f, 8, 6) } }.build()
        fun wing(sgn: Float) = PM().apply {
            col = cA * 1.1f; push(); translate(0.02f, sgn * 0.11f, 0f); scale(1f, 1f, 0.06f); sphere(0.1f, 12, 8); pop()
            col = cB; push(); translate(-0.07f, sgn * 0.08f, 0f); scale(1f, 1f, 0.06f); sphere(0.065f, 10, 6); pop()
        }.build()
        return Bird(body, wing(1f), wing(-1f), 6, 0.35f, 0.9f, 14f)
    }

    private fun paperPlane(): Bird {
        val body = PM().apply { col = c(250, 250, 250); box(0.3f, 0.02f, 0.05f) }.build()
        fun wing(sgn: Float) = PM().apply { col = c(240, 240, 245); prism(listOf(floatArrayOf(0.15f, 0f), floatArrayOf(-0.15f, sgn * 0.0f), floatArrayOf(-0.15f, sgn * 0.16f)).let { if (sgn < 0) it.reversed() else it }, 0.008f) }.build()
        return Bird(body, wing(1f), wing(-1f), 3, 0.6f, 2.2f, 0f, glide = true)
    }

    private fun drone(): Bird {
        val body = PM().apply {
            col = c(40, 44, 54); box(0.22f, 0.1f, 0.05f)
            col = c(255, 40, 160) * 2f; at(0.11f, 0f, 0f) { sphere(0.02f, 6, 4) }
            col = c(40, 220, 255) * 2f; at(-0.11f, 0f, 0f) { sphere(0.02f, 6, 4) }
        }.build()
        fun arm(sgn: Float) = PM().apply {
            col = c(60, 64, 74); at(0f, sgn * 0.1f, 0f) { box(0.03f, 0.12f, 0.02f) }
            col = c(160, 170, 190); for (x in floatArrayOf(-0.09f, 0.09f)) at(x, sgn * 0.16f, 0.015f) { cylinder(0.06f, 0.004f, 14) }
        }.build()
        return Bird(body, arm(1f), arm(-1f), 4, 0.6f, 2.4f, 0f, glide = true)
    }

    /** Preview harness only: the props and flocks being worked on, shown side by side. */
    /** Preview harness only: the props being worked on, shown side by side. */
    internal fun previewSet(): Pair<List<PropMesh>, List<Bird>> = listOf(groundDragon(), monkey(), dino(), idol(), trafficCone()) to listOf(dragon(), dragonfly(), mosquito())
    internal fun previewSetFlags(): Pair<List<PropMesh>, List<Bird>> {
        fun withFlag(host: PropMesh, sp: AnimSpec, ph: Float): PropMesh {
            val parts = listOf(host) + sp.at.map { o -> flagPose(sp, ph + o.x * 5f).let { m -> PropMesh(FloatArray(m.pos.size) { i -> m.pos[i] + when (i % 3) { 0 -> o.x; 1 -> o.y; else -> o.z } }, m.nrm, m.col, m.idx) } }
            val pos = parts.flatMap { it.pos.toList() }.toFloatArray(); val nrm = parts.flatMap { it.nrm.toList() }.toFloatArray(); val col = parts.flatMap { it.col.toList() }.toFloatArray()
            var base = 0; val idx = ArrayList<Int>(); for (pm in parts) { for (i in pm.idx) idx.add(i + base); base += pm.vertexCount }
            return PropMesh(pos, nrm, col, idx.toIntArray())
        }
        val fp = flagPole(); val cf = cornerFlag(); val bn = banner(); val cs = castle()
        return listOf(raceCar(), withFlag(fp, flagSpec(chequerCloth, flagLen, listOf(Vec3(0.02f, 0f, 0.62f))), 1.3f),
            withFlag(cf, flagSpec(cornerCloth, 0.3f, listOf(Vec3(0.018f, 0f, 0.68f)), 0.08f, 1.4f), 2.1f),
            withFlag(bn, flagSpec(gonfalonCloth, -0.52f, listOf(Vec3(0.02f, 0f, 0.865f)), 0.07f, 0.9f), 0.7f),
            withFlag(cs, flagSpec(castlePennantA, 0.16f, listOf(Vec3(0.306f, 0.3f, 1.03f), Vec3(-0.294f, 0.3f, 1.03f), Vec3(0.306f, -0.3f, 1.03f), Vec3(-0.294f, -0.3f, 1.03f)), 0.12f, 1.4f), 1.9f)) to emptyList()
    }

    /** A big dragonfly: ten-segment abdomen in electric blue with black dorsal marks and tail appendages, a striped thorax,
     *  huge wrap-round compound eyes, spiny legs folded under, and four clear veined wings with amber bases and dark spots. */
    private fun dragonfly(): Bird {
        val body = PM().apply {
            for (sgm in 0 until 10) {                                                                       // abdomen segments
                val x0 = -0.03f - sgm * 0.044f; val r = 0.021f * (1f - sgm * 0.035f)
                col = if (sgm < 2) c(40, 130, 170) else c(40, 150, 200)
                at(x0 - 0.022f, 0f, 0f) { scale(1f, 1f, 0.95f); push(); rotY((PI / 2).toFloat()); translate(0f, 0f, -0.022f); cylinder(r, 0.042f, 12, r * 0.93f); pop() }
                col = c(14, 18, 24); at(x0 - 0.003f, 0f, 0f) { push(); rotY((PI / 2).toFloat()); torus(r * 0.98f, 0.003f, 12, 3); pop() }   // segment joint
                col = c(18, 22, 30); at(x0 - 0.022f, 0f, r * 0.75f) { scale(1.4f, 0.55f, 0.35f); sphere(r * 0.55f, 8, 4) }              // black dorsal mark
            }
            col = c(20, 24, 30); for (sg in intArrayOf(-1, 1)) tube(listOf(Vec3(-0.47f, sg * 0.004f, 0f), Vec3(-0.5f, sg * 0.014f, 0.004f)), 0.003f, 4)   // claspers
            col = c(60, 140, 80); at(0.03f, 0f, 0.005f) { scale(1.35f, 0.95f, 1.05f); sphere(0.042f, 16, 12) }                        // thorax
            col = c(16, 20, 20); for (k in 0 until 3) at(0.01f + k * 0.02f, 0f, 0.01f) { push(); rotX((PI / 2).toFloat()); scale(1f, 1f, 1f); torus(0.04f, 0.004f, 14, 3); pop() }   // thorax stripes
            col = c(60, 200, 190) * 1.05f; for (sg in intArrayOf(-1, 1)) at(0.092f, sg * 0.024f, 0.012f) { scale(1f, 1f, 1.05f); sphere(0.031f, 14, 10) }   // compound eyes
            col = c(20, 50, 60); for (sg in intArrayOf(-1, 1)) at(0.094f, sg * 0.024f, 0.03f) { scale(1f, 1f, 0.45f); sphere(0.027f, 12, 6) }   // darker eye tops
            col = c(220, 240, 250) * 1.2f; for (sg in intArrayOf(-1, 1)) at(0.11f, sg * 0.03f, 0.024f) { sphere(0.005f, 4, 3) }
            col = c(150, 180, 120); at(0.114f, 0f, -0.004f) { scale(0.5f, 1f, 0.8f); sphere(0.02f, 10, 8) }                          // face
            col = c(30, 30, 30); for (sg in intArrayOf(-1, 1)) for (k in 0 until 3) {                                                // spiny legs folded under
                val x = 0.05f - k * 0.02f
                tube(listOf(Vec3(x, sg * 0.015f, -0.03f), Vec3(x + 0.02f, sg * 0.035f, -0.055f), Vec3(x + 0.045f, sg * 0.03f, -0.06f)), 0.0035f, 4)
                for (j in 0 until 2) at(x + 0.01f + j * 0.012f, sg * 0.03f, -0.05f) { rotY(2.6f); cone(0.0015f, 0.008f, 3) }
            }
        }.build()
        fun wing(sg: Float) = PM().apply {
            for ((x, span, chord, hind) in listOf(listOf(0.05f, 0.34f, 0.055f, 0f), listOf(-0.005f, 0.31f, 0.072f, 1f))) {
                // outline: a long narrow wing, the hindwing broader at its base
                val n = 14
                val out = (0..n).map { k -> val t = k / n.toFloat(); val w = chord * (0.55f + 0.45f * kotlin.math.sin(t * PI.toFloat() * 0.92f + 0.12f)) * (1f + hind * 0.6f * (1f - t) * (1f - t))
                    Vec3(x - w * 0.5f, sg * span * t, 0.02f) to Vec3(x + w * 0.5f - hind * 0.01f, sg * span * t, 0.02f) }
                for (k in 0 until n) {
                    val (a0, b0) = out[k]; val (a1, b1) = out[k + 1]
                    val t = k / n.toFloat()
                    col = if (t < 0.12f) c(210, 170, 90) else c(205, 222, 232) * (0.95f + 0.05f * (k % 2))                         // amber base, clear glassy membrane
                    flat(a0, b0, b1, a1)
                }
                col = c(50, 46, 40)                                                                           // veins: leading edge, a mid vein, cross veins, nodus
                tube(out.map { it.second + Vec3(0f, 0f, 0.0015f) }, 0.0022f, 3)
                tube(out.map { (it.first + it.second) * 0.5f + Vec3(0f, 0f, 0.0015f) }, 0.0012f, 3)
                for (k in 1 until n step 1) tube(listOf(out[k].first + Vec3(0f, 0f, 0.0015f), out[k].second + Vec3(0f, 0f, 0.0015f)), 0.0007f, 3)
                col = c(40, 26, 20); val p = out[n - 2]; at((p.first.x + p.second.x) * 0.5f + 0.012f, p.first.y, 0.0225f) { scale(0.012f, 0.025f, 0.002f); sphere(1f, 6, 4) }   // pterostigma
            }
        }.build()
        return Bird(body, wing(1f), wing(-1f), 3, 0.95f, 1.4f, 22f)
    }

    /** A giant mosquito: humped thorax with a pale lyre pattern, a ringed black-and-white abdomen, dark compound eyes, palps and
     *  a long needle proboscis, bushy antennae, six long jointed legs banded in white, and narrow veined wings with fringed edges. */
    private fun mosquito(): Bird {
        val body = PM().apply {
            col = c(46, 40, 36); at(0.02f, 0f, 0.012f) { scale(1.25f, 0.85f, 1.15f); sphere(0.04f, 16, 12) }                       // thorax
            col = c(235, 232, 220); for (sg in intArrayOf(-1, 1)) tube((0..6).map { k -> val v = k / 6f; Vec3(0.05f - 0.06f * v, sg * (0.008f + 0.012f * kotlin.math.sin(v * 3f)), 0.05f - 0.005f * v) }, 0.0028f, 4)   // lyre
            tube(listOf(Vec3(0.045f, 0f, 0.052f), Vec3(-0.01f, 0f, 0.05f)), 0.0022f, 4)
            for (sgm in 0 until 8) {                                                                       // ringed abdomen, drooping slightly
                val t = sgm / 8f; val x = -0.02f - 0.032f * sgm; val z = -0.012f - 0.055f * t * t; val r = 0.022f * (1f - 0.35f * t)
                col = c(40, 36, 34); at(x - 0.016f, 0f, z) { scale(1.1f, 1f, 0.9f); sphere(r, 12, 8) }
                col = c(230, 226, 214); at(x - 0.002f, 0f, z) { push(); rotY((PI / 2).toFloat()); torus(r * 0.92f, 0.003f, 12, 3); pop() }
            }
            col = c(30, 26, 24); at(0.072f, 0f, 0f) { sphere(0.024f, 12, 10) }                                                       // head
            col = c(70, 20, 20); for (sg in intArrayOf(-1, 1)) at(0.08f, sg * 0.014f, 0.006f) { scale(0.9f, 0.8f, 1f); sphere(0.016f, 10, 8) }   // compound eyes
            col = c(24, 20, 20); tube(listOf(Vec3(0.094f, 0f, -0.004f), Vec3(0.18f, 0f, -0.032f), Vec3(0.27f, 0f, -0.07f)), 0.004f, 5, taper = { 1f - 0.7f * it })   // proboscis
            for (sg in intArrayOf(-1, 1)) tube(listOf(Vec3(0.094f, sg * 0.006f, -0.002f), Vec3(0.15f, sg * 0.012f, -0.02f)), 0.0025f, 4)   // palps
            for (sg in intArrayOf(-1, 1)) {                                                                // bushy antennae
                val ant = (0..6).map { k -> val v = k / 6f; Vec3(0.09f + 0.06f * v, sg * (0.008f + 0.03f * v), 0.012f + 0.045f * v) }
                col = c(40, 34, 30); tube(ant, 0.0022f, 3)
                col = c(90, 80, 70); for (k in 1 until 6) { val q = ant[k]; for (w in 0 until 4) { val a = w * 1.57f
                    tube(listOf(q, q + Vec3(0f, kotlin.math.cos(a) * 0.01f, kotlin.math.sin(a) * 0.01f)), 0.0008f, 3) } }
            }
            for (sg in intArrayOf(-1, 1)) for (k in 0 until 3) {                                          // long jointed legs, white bands at the joints
                val x0 = 0.03f - k * 0.022f
                val hip = Vec3(x0, sg * 0.02f, -0.02f); val knee = Vec3(x0 + 0.04f - k * 0.05f, sg * 0.11f, 0.04f)
                val ankle = Vec3(x0 + 0.06f - k * 0.1f, sg * 0.16f, -0.12f); val foot = Vec3(x0 + 0.07f - k * 0.13f, sg * 0.18f, -0.2f)
                col = c(46, 40, 36); tube(listOf(hip, knee), 0.0045f, 4); tube(listOf(knee, ankle), 0.0035f, 4); tube(listOf(ankle, foot), 0.0025f, 4)
                col = c(235, 232, 220); for (q in listOf(knee, ankle)) at(q.x, q.y, q.z) { sphere(0.005f, 5, 4) }
                at(foot.x, foot.y, foot.z) { sphere(0.003f, 4, 3) }
            }
            col = c(180, 170, 150); for (sg in intArrayOf(-1, 1)) at(-0.01f, sg * 0.03f, 0.02f) { sphere(0.006f, 5, 4) }          // halteres
        }.build()
        fun wing(sg: Float) = PM().apply {
            val n = 10
            val out = (0..n).map { k -> val t = k / n.toFloat(); val w = 0.042f * kotlin.math.sin(t * PI.toFloat() * 0.95f + 0.1f)
                val y = sg * 0.24f * t; val x = -0.01f - 0.07f * t
                Vec3(x - w * 0.5f, y, 0.03f) to Vec3(x + w * 0.5f, y, 0.03f) }
            for (k in 0 until n) { val (a0, b0) = out[k]; val (a1, b1) = out[k + 1]; col = c(200, 206, 210) * (0.94f + 0.06f * (k % 2)); flat(a0, b0, b1, a1) }
            col = c(60, 56, 50)
            tube(out.map { it.second + Vec3(0f, 0f, 0.001f) }, 0.0015f, 3); tube(out.map { (it.first + it.second) * 0.5f + Vec3(0f, 0f, 0.001f) }, 0.001f, 3)
            col = c(120, 112, 100); for (k in 1 until n) tube(listOf(out[k].first, out[k].first + Vec3(-0.006f, 0f, -0.002f)), 0.0008f, 3)   // fringe of scales on the trailing edge
        }.build()
        return Bird(body, wing(1f), wing(-1f), 4, 0.75f, 1.1f, 30f)
    }

    // ---------------------------------------------------------------- neon megacity
    private val nMag = c(255, 40, 170); private val nCyan = c(30, 235, 255); private val nYel = c(255, 214, 60); private val nRed = c(255, 50, 60)
    private val steel = c(34, 38, 48); private val darkSteel = c(18, 20, 26)

    /** Made-up glyphs: each one a few strokes on a 3x4 grid (never a real word), lit in [glow] on a dark panel. */
    private fun PM.glyphs(n: Int, seed: Int, x: Float, w: Float, z0: Float, h: Float, glow: Vec3, vertical: Boolean = true) {
        val cell = h / n
        for (g in 0 until n) {
            val gz = if (vertical) z0 + h - (g + 0.5f) * cell else z0 + h * 0.5f
            val gy = if (vertical) 0f else (g - (n - 1) / 2f) * cell
            col = glow * 2.2f
            for (k in 0 until 4) {
                val hsh = ((seed * 131 + g * 37 + k * 11) * 2654435761L).toInt().let { (it ushr 8) % 1000 } / 1000f
                val horiz = hsh < 0.5f
                val oy = ((((seed + g * 7 + k * 13) * 97) % 3) - 1) * cell * 0.22f
                val oz = ((((seed + g * 5 + k * 17) * 89) % 4) - 1.5f) * cell * 0.18f
                at(x, gy + oy, gz + oz) { if (horiz) box(w, cell * 0.5f, cell * 0.08f) else box(w, cell * 0.08f, cell * 0.42f) }
            }
        }
    }

    /** Rooftop sign: a tall dark blade on two struts with a column of glowing glyphs and a neon outline. */
    private fun holoSign(seed: Int, glow: Vec3) = PM().apply {
        col = darkSteel; for (sy in floatArrayOf(-0.08f, 0.08f)) at(0f, sy, 0f) { cylinder(0.018f, 0.25f, 6) }
        col = c(10, 10, 16); at(0f, 0f, 0.62f) { box(0.05f, 0.26f, 0.78f) }
        col = glow * 2.0f; for (sy in floatArrayOf(-0.135f, 0.135f)) at(0f, sy, 0.62f) { box(0.06f, 0.016f, 0.8f) }
        for (sz in floatArrayOf(0.22f, 1.02f)) at(0f, 0f, sz) { box(0.06f, 0.29f, 0.016f) }
        glyphs(5, seed, 0.03f, 0.012f, 0.26f, 0.72f, if (seed % 2 == 0) nYel else nCyan)
        glyphs(5, seed + 3, -0.03f, 0.012f, 0.26f, 0.72f, if (seed % 2 == 0) nCyan else nYel)
        col = nRed * 2.4f; at(0f, 0f, 1.06f) { sphere(0.022f, 8, 6) }           // aircraft warning light
    }.build()

    /** Rooftop water tank and antenna mast with blinking lights (a common city skyline silhouette). */
    private fun roofMast() = PM().apply {
        col = steel; for (sx in floatArrayOf(-0.1f, 0.1f)) for (sy in floatArrayOf(-0.1f, 0.1f)) at(sx, sy, 0f) { cylinder(0.012f, 0.2f, 5) }
        col = c(60, 54, 50); at(z = 0.2f) { cylinder(0.16f, 0.26f, 14) }; at(z = 0.46f) { cone(0.17f, 0.1f, 14) }
        col = darkSteel; at(0.12f, 0f, 0f) { cylinder(0.012f, 1.0f, 5) }
        for (k in 0 until 3) { col = (if (k % 2 == 0) nRed else nCyan) * 2.4f; at(0.12f, 0f, 0.4f + k * 0.3f) { sphere(0.02f, 6, 4) } }
    }.build()

    /** Street lamp: slim post, swan-neck arm and a long cyan-white light bar. */
    private fun streetLamp() = PM().apply {
        col = darkSteel; cylinder(0.05f, 0.05f, 10, 0.045f)
        col = steel; cylinder(0.022f, 0.9f, 8, 0.016f)
        tube(listOf(Vec3(0f, 0f, 0.88f), Vec3(0.04f, 0f, 0.97f), Vec3(0.14f, 0f, 1.0f), Vec3(0.26f, 0f, 0.98f)), 0.014f, 6)
        col = darkSteel; at(0.24f, 0f, 0.965f) { box(0.18f, 0.06f, 0.03f) }
        col = c(200, 245, 255) * 2.6f; at(0.24f, 0f, 0.948f) { box(0.16f, 0.04f, 0.012f) }
        col = nMag * 1.8f; at(0.0f, 0f, 0.5f) { torus(0.03f, 0.007f, 12, 4) }       // neon collar
    }.build()

    /** Vending machine: dark cabinet, glowing product window in a grid of colours, a lit header and coin slot. */
    private fun vendingMachine() = PM().apply {
        col = c(26, 28, 36); at(z = 0.42f) { box(0.36f, 0.5f, 0.84f) }
        col = c(10, 12, 18); at(0.181f, 0f, 0.5f) { box(0.01f, 0.4f, 0.56f) }
        val cols = listOf(nMag, nCyan, nYel, c(120, 255, 140), c(255, 120, 40))
        for (r in 0 until 5) for (k in 0 until 4) { col = cols[(r * 3 + k) % cols.size] * 1.6f; at(0.188f, -0.14f + k * 0.093f, 0.29f + r * 0.1f) { box(0.008f, 0.06f, 0.06f) } }
        col = nCyan * 2.2f; at(0.186f, 0f, 0.8f) { box(0.012f, 0.44f, 0.07f) }
        glyphs(4, 77, 0.193f, 0.006f, 0.77f, 0.06f, nMag, vertical = false)
        col = c(70, 74, 86); at(0.185f, 0.17f, 0.12f) { box(0.012f, 0.1f, 0.12f) }
        col = nYel * 2f; at(0.19f, 0.17f, 0.15f) { box(0.006f, 0.05f, 0.012f) }
    }.build()

    /** Street-food stall: counter, striped awning, a row of red paper lanterns, stools and a hanging glyph sign. */
    private fun noodleStall() = PM().apply {
        col = c(60, 32, 26); at(z = 0.22f) { box(0.36f, 0.86f, 0.44f) }
        col = c(110, 70, 48); at(0.02f, 0f, 0.45f) { box(0.44f, 0.9f, 0.03f) }
        col = steel; for (sy in floatArrayOf(-0.42f, 0.42f)) at(-0.16f, sy, 0f) { cylinder(0.014f, 0.86f, 6) }
        for (k in 0 until 8) { col = if (k % 2 == 0) c(200, 30, 40) else c(240, 230, 220)
            at(0.06f, -0.42f + k * 0.12f + 0.06f, 0.84f) { rotY(-0.35f); box(0.5f, 0.12f, 0.02f) } }
        col = nRed * 2.3f; for (k in 0 until 5) at(0.26f, -0.36f + k * 0.18f, 0.7f) { scale(1f, 1f, 1.25f); sphere(0.045f, 10, 8) }
        col = c(40, 20, 16); for (k in 0 until 5) at(0.26f, -0.36f + k * 0.18f, 0.765f) { cylinder(0.006f, 0.04f, 4) }
        col = c(30, 30, 36); for (k in 0 until 3) { at(0.36f, -0.26f + k * 0.26f, 0f) { cylinder(0.012f, 0.24f, 5) }; at(0.36f, -0.26f + k * 0.26f, 0.24f) { cylinder(0.06f, 0.025f, 10) } }
        col = c(12, 10, 14); at(0.2f, 0f, 0.98f) { box(0.03f, 0.5f, 0.12f) }
        glyphs(4, 21, 0.218f, 0.008f, 0.96f, 0.04f, nYel, vertical = false)
        col = c(255, 236, 200) * 1.5f; at(0.0f, 0f, 0.47f) { cylinder(0.05f, 0.04f, 10, 0.06f) }   // steaming bowl lit by the counter lamp
    }.build()

    /** An inverted triangle of neon tube on two dark posts, buzzing red-pink. */
    private fun neonTriangle() = PM().apply {
        col = darkSteel; for (sy in floatArrayOf(-0.4f, 0.4f)) at(0f, sy, 0f) { cylinder(0.03f, 0.95f, 6) }
        val pts = listOf(Vec3(0f, -0.38f, 0.95f), Vec3(0f, 0.38f, 0.95f), Vec3(0f, 0f, 0.3f))
        col = c(255, 60, 90) * 2.4f; tube(pts + listOf(pts[0]), 0.022f, 8)
        col = c(255, 160, 180) * 2.8f; tube(listOf(Vec3(0.01f, -0.2f, 0.84f), Vec3(0.01f, 0.2f, 0.84f), Vec3(0.01f, 0f, 0.5f), Vec3(0.01f, -0.2f, 0.84f)), 0.01f, 6)
    }.build()

    /** Road barrier with glowing orange chevrons. */
    private fun chevronBarrier() = PM().apply {
        col = darkSteel; for (sy in floatArrayOf(-0.3f, 0.3f)) at(0f, sy, 0f) { cylinder(0.02f, 0.3f, 6) }
        col = c(14, 14, 18); at(z = 0.32f) { box(0.04f, 0.74f, 0.14f) }
        col = c(255, 130, 20) * 2.3f
        for (k in 0 until 6) { val y = -0.28f + k * 0.112f
            tube(listOf(Vec3(0.022f, y - 0.025f, 0.37f), Vec3(0.022f, y + 0.02f, 0.32f), Vec3(0.022f, y - 0.025f, 0.27f)), 0.008f, 4) }
        col = nRed * 2.4f; for (sy in floatArrayOf(-0.37f, 0.37f)) at(0f, sy, 0.41f) { sphere(0.02f, 6, 4) }
    }.build()

    /** Palm in a concrete planter with a cyan neon edge. */
    private fun palmPlanter() = PM().apply {
        col = c(70, 72, 78); at(z = 0.09f) { box(0.36f, 0.36f, 0.18f) }
        col = nCyan * 2.0f; at(z = 0.185f) { box(0.37f, 0.37f, 0.01f) }
        col = c(30, 26, 20); at(z = 0.18f) { box(0.32f, 0.32f, 0.01f) }
        val trunk = (0..10).map { k -> val v = k / 10f; Vec3(0.12f * v * v, 0.04f * v, 0.18f + 0.95f * v) }
        col = c(92, 72, 52); tube(trunk, 0.035f, 8, colorFn = { v -> if (((v * 18f).toInt() % 2) == 0) c(92, 72, 52) else c(70, 54, 40) }, taper = { 1f - 0.35f * it })
        val top = trunk.last()
        for (f in 0 until 9) {
            val a = f * 6.2832f / 9f; val ca = cos(a); val sa = sin(a)
            val fr = (0..6).map { k -> val v = k / 6f; Vec3(top.x + ca * 0.42f * v, top.y + sa * 0.42f * v, top.z + 0.08f * v - 0.3f * v * v) }
            for (k in 0 until fr.size - 1) {
                val d = fr[k + 1] - fr[k]; val side = Vec3(-sa, ca, 0f) * (0.07f * (1f - k / 6f) + 0.015f)
                col = if (k % 2 == 0) c(30, 90, 60) else c(24, 72, 50)
                flat(fr[k] - side, fr[k] + side, fr[k + 1] + side * 0.85f, fr[k + 1] - side * 0.85f)
            }
        }
    }.build()

    /** Megatower for the skyline ring: stepped dark slab, rows of lit windows, neon edges, a giant screen and a spire. */
    private val towerCache = HashMap<Int, PropMesh>()
    /** Skyline megatowers in a few varieties (cached). */
    fun megaTowerFor(i: Int): PropMesh = towerCache.getOrPut(i % 4) { megaTower(i % 4) }
    private fun megaTower(seed: Int) = PM().apply {
        val h = 2.6f + 0.8f * ((seed * 37) % 5) / 4f
        col = c(12, 16, 24); at(z = h * 0.5f) { box(0.7f, 0.7f, h) }
        col = c(16, 20, 30); at(z = h + 0.25f) { box(0.45f, 0.45f, 0.5f) }
        val edge = if (seed % 2 == 0) nCyan else nMag
        col = edge * 2.2f; for (sx in floatArrayOf(-0.355f, 0.355f)) for (sy in floatArrayOf(-0.355f, 0.355f)) at(sx, sy, h * 0.5f) { box(0.014f, 0.014f, h) }
        at(z = h) { box(0.72f, 0.72f, 0.015f) }
        for (r in 0 until 22) for (k in 0 until 6) {                                     // lit windows on two faces
            val hh = ((seed * 977 + r * 131 + k * 71) * 2654435761L).toInt().let { (it ushr 9) % 1000 } / 1000f
            if (hh < 0.55f) continue
            col = (if (hh > 0.93f) nMag else if (hh > 0.85f) nCyan else c(255, 190, 110)) * 1.4f
            val z = 0.15f + r * (h - 0.4f) / 22f; val y = -0.26f + k * 0.104f
            at(y, 0.352f, z) { box(0.06f, 0.006f, 0.05f) }; at(0.352f, y, z) { box(0.006f, 0.06f, 0.05f) }; at(-0.352f, y, z) { box(0.006f, 0.06f, 0.05f) }
        }
        val s0 = h * (0.45f + 0.2f * ((seed * 13) % 3) / 2f)                               // giant screen: bands of colour like a frozen frame
        for (b in 0 until 10) { val v = b / 9f
            col = Vec3.lerp(if (seed % 3 == 0) nMag else nYel, if (seed % 3 == 2) c(120, 255, 160) else nCyan, v) * 1.9f
            at(0f, 0.356f, s0 + b * 0.05f) { box(0.6f, 0.008f, 0.045f) } }
        col = darkSteel; at(z = h + 0.5f) { cylinder(0.02f, 0.6f, 6) }
        col = nRed * 2.6f; at(z = h + 1.1f) { sphere(0.035f, 8, 6) }
    }.build()

    /** Advertising blimp drifting over the city: grey envelope, fins, a gondola and a glowing screen band down its side. */
    private fun adBlimp() = PM().apply {
        col = c(70, 76, 88); at(z = 0.5f) { scale(2.4f, 1f, 1f); sphere(0.22f, 20, 12) }
        for ((ry, rz) in listOf(0f to 1f, 1f to 0f, 0f to -1f, -1f to 0f)) { col = c(50, 54, 64)
            push(); translate(-0.46f, ry * 0.1f, 0.5f + rz * 0.1f); box(0.16f, 0.02f + kotlin.math.abs(ry) * 0.16f, 0.02f + kotlin.math.abs(rz) * 0.16f); pop() }
        col = c(30, 32, 40); at(0.05f, 0f, 0.27f) { box(0.18f, 0.08f, 0.06f) }
        for (b in 0 until 8) { col = Vec3.lerp(nCyan, nMag, b / 7f) * 2.0f; for (sy in floatArrayOf(-1f, 1f)) at(-0.25f + b * 0.07f, sy * 0.215f, 0.5f) { box(0.06f, 0.012f, 0.16f) } }
        col = nRed * 2.4f; at(-0.53f, 0f, 0.62f) { sphere(0.02f, 6, 4) }
    }.build()

    /** Sky bridge over a street: a glass-walled deck on two pylons standing on the walls, neon strips under and along it. */
    fun skyBridge(seed: Int) = PM().apply {
        val glow = if (seed % 2 == 0) nCyan else nMag
        col = steel; for (sy in floatArrayOf(-0.56f, 0.56f)) { at(0f, sy, 0f) { box(0.16f, 0.12f, 1.4f) } }
        col = c(22, 26, 34); at(z = 1.0f) { box(0.3f, 1.3f, 0.1f) }
        col = c(40, 90, 110); at(z = 1.12f) { box(0.28f, 1.24f, 0.14f) }                   // glazing
        col = c(16, 20, 28); at(z = 1.2f) { box(0.32f, 1.3f, 0.03f) }
        col = glow * 2.4f; for (sx in floatArrayOf(-0.15f, 0.15f)) at(sx, 0f, 0.945f) { box(0.016f, 1.3f, 0.016f) }
        at(z = 1.22f) { box(0.012f, 1.3f, 0.012f) }
        for (k in 0 until 6) { col = c(255, 210, 150) * 1.5f; at(0.142f, -0.5f + k * 0.2f, 1.12f) { box(0.004f, 0.12f, 0.08f) } }  // lit windows of the walkway
        col = c(12, 12, 18); at(0.17f, 0f, 1.32f) { box(0.03f, 0.56f, 0.14f) }                 // sign on its side
        glyphs(5, seed + 40, 0.188f, 0.006f, 1.27f, 0.1f, if (seed % 2 == 0) nYel else nCyan, vertical = false)
    }.build()

    /** Hover car: sleek wedge body with a glass canopy, white headlights and a wide red tail-light bar; short fins as 'wings'. */
    private fun hoverCar(): Bird {
        val body = PM().apply {
            col = c(28, 30, 38); at { scale(2.4f, 1f, 0.42f); sphere(0.11f, 16, 10) }
            col = c(60, 140, 170); at(0.03f, 0f, 0.04f) { scale(1.3f, 0.75f, 0.45f); sphere(0.08f, 12, 8) }
            col = c(255, 40, 50) * 2.6f; at(-0.26f, 0f, 0.0f) { box(0.012f, 0.16f, 0.018f) }
            col = c(230, 245, 255) * 2.6f; for (sy in floatArrayOf(-0.06f, 0.06f)) at(0.26f, sy, -0.005f) { box(0.012f, 0.035f, 0.014f) }
            col = nCyan * 2.0f; for (sy in floatArrayOf(-1f, 1f)) at(0f, sy * 0.095f, -0.03f) { box(0.36f, 0.006f, 0.006f) }   // underglow
        }.build()
        fun fin(sgn: Float) = PM().apply { col = c(22, 24, 30); at(-0.12f, sgn * 0.12f, 0f) { box(0.1f, 0.06f, 0.012f) } }.build()
        return Bird(body, fin(1f), fin(-1f), 5, 0.75f, 2.4f, 0f, glide = true)
    }

    // ---------------------------------------------------------------- flags (cloth that ripples in the wind; see SCENE_VS uFlag)
    /** Flag cloth in the standard frame: the hoist (fixed edge) runs up the pole at x = 0 and the cloth flies out along +x in
     *  the y = 0 plane, double-sided and finely gridded so it can ripple. [top]/[bot] give the upper/lower edge height at
     *  u (0 at the pole .. 1 at the fly end); [paint] colours the cloth at (u, v), v running bottom to top. */
    private fun PM.cloth(len: Float, nu: Int, nv: Int, top: (Float) -> Float, bot: (Float) -> Float, paint: (Float, Float) -> Vec3) {
        for (i in 0 until nu) for (j in 0 until nv) {
            val u0 = i / nu.toFloat(); val u1 = (i + 1) / nu.toFloat(); val v0 = j / nv.toFloat(); val v1 = (j + 1) / nv.toFloat()
            fun at(u: Float, v: Float) = Vec3(0.004f + len * u, 0f, bot(u) + (top(u) - bot(u)) * v)
            val cm = paint((u0 + u1) * 0.5f, (v0 + v1) * 0.5f)
            flat(at(u0, v0), at(u1, v0), at(u1, v1), at(u0, v1), cm, cm, cm, cm)
        }
    }
    /** A banner hanging from a bar at z = 0, across x in [0, width], down to [drop] (swallowtail at the bottom). */
    private fun PM.hangingCloth(width: Float, drop: Float, nu: Int, nv: Int, tail: Float, paint: (Float, Float) -> Vec3) {
        for (i in 0 until nu) for (j in 0 until nv) {
            val u0 = i / nu.toFloat(); val u1 = (i + 1) / nu.toFloat(); val v0 = j / nv.toFloat(); val v1 = (j + 1) / nv.toFloat()
            fun bottom(u: Float) = -drop + tail * (1f - kotlin.math.abs(2f * u - 1f))        // swallowtail: the notch rises in the middle
            fun at(u: Float, v: Float) = Vec3(width * u, 0f, -0.004f + (bottom(u) + 0.004f) * v)
            val cm = paint((u0 + u1) * 0.5f, (v0 + v1) * 0.5f)
            flat(at(u0, v0), at(u1, v0), at(u1, v1), at(u0, v1), cm, cm, cm, cm)
        }
    }

    private val flagLen = 0.46f
    /** The chequered flag: 8 x 5 squares. */
    private val chequerCloth by lazy { PM().apply {
        cloth(flagLen, 24, 10, { 0.3f }, { 0f }) { u, v -> if (((u * 8f).toInt() + (v * 5f).toInt()) % 2 == 0) c(24, 24, 26) else c(246, 246, 246) }
    }.build() }
    /** Football corner flag: red and yellow quarters. */
    private val cornerCloth by lazy { PM().apply {
        cloth(0.3f, 16, 8, { 0.2f }, { 0f }) { u, v -> if ((u < 0.5f) == (v < 0.5f)) c(250, 214, 40) else c(220, 40, 40) }
    }.build() }
    /** Long tapering pennant (castle towers, turret tops) in two colours split along its length. */
    fun pennantCloth(a: Vec3, b: Vec3, len: Float = 0.22f, h: Float = 0.07f) = PM().apply {
        cloth(len, 18, 4, { u -> h * (1f - 0.5f * u) }, { u -> h * 0.5f * u }) { _, v -> if (v > 0.5f) a else b }
    }.build()
    private val castlePennantA by lazy { pennantCloth(c(220, 40, 40), c(250, 214, 40), 0.16f, 0.05f) }
    /** Medieval gonfalon: crimson with a gold border and cross, swallowtailed, hanging from its crossbar. */
    private val gonfalonCloth by lazy { PM().apply {
        hangingCloth(0.29f, 0.52f, 8, 14, 0.1f) { u, v ->
            val border = u < 0.08f || u > 0.92f || v > 0.93f
            val cross = kotlin.math.abs(u - 0.5f) < 0.07f || kotlin.math.abs(v - 0.62f) < 0.05f
            if (border) c(214, 168, 70) else if (cross) c(250, 214, 40) else c(150, 26, 30)
        }
    }.build() }
    /** Checkpoint pennants: orange until reached, then green; slightly glowing. */
    val checkpointClothOn by lazy { pennantCloth(c(90, 255, 130) * 1.25f, c(60, 220, 110) * 1.25f, 0.36f, 0.3f) }
    val checkpointClothOff by lazy { pennantCloth(c(255, 190, 60) * 1.25f, c(255, 150, 40) * 1.25f, 0.36f, 0.3f) }

    // ---------------------------------------------------------------- castle grounds
    /** A broad oak for the courtyard lawns: gnarled trunk, spreading boughs and clumped leafy crown. */
    fun oakTree() = PM().apply {
        val bark = c(92, 70, 50); val leaf = c(70, 118, 48); val leaf2 = c(96, 140, 60)
        col = bark
        tube(listOf(Vec3(0f, 0f, 0f), Vec3(0.02f, 0f, 0.25f), Vec3(-0.01f, 0.02f, 0.45f)), 0.07f, 10, taper = { 1f - 0.35f * it })
        for (r in 0 until 4) { val a = r * 1.57f + 0.4f
            tube(listOf(Vec3(0f, 0f, 0.02f), Vec3(cos(a) * 0.09f, sin(a) * 0.09f, 0.0f)), 0.025f, 6) }
        for (b in 0 until 4) { val a = b * 1.6f + 0.3f
            tube(listOf(Vec3(0f, 0f, 0.4f), Vec3(cos(a) * 0.12f, sin(a) * 0.12f, 0.52f), Vec3(cos(a) * 0.22f, sin(a) * 0.22f, 0.6f)), 0.03f, 6, taper = { 1f - 0.6f * it }) }
        for (k in 0 until 9) { val a = k * 2.4f; val r = if (k == 0) 0f else 0.16f + 0.06f * (k % 3)
            col = if (k % 2 == 0) leaf else leaf2
            at(cos(a) * r, sin(a) * r, 0.66f + 0.08f * (k % 3) + (if (k == 0) 0.12f else 0f)) { scale(1f, 1f, 0.8f); sphere(0.15f + 0.03f * (k % 2), 12, 8) } }
    }.build()

    /** A clump of long grass. */
    fun grassTuft() = PM().apply {
        for (k in 0 until 9) { val a = k * 0.7f; val l = 0.16f + 0.08f * ((k * 7) % 3)
            col = if (k % 2 == 0) c(80, 130, 50) else c(110, 150, 60)
            val b = Vec3(cos(a) * 0.02f, sin(a) * 0.02f, 0f); val tip = Vec3(cos(a) * 0.08f, sin(a) * 0.08f, l)
            val side = Vec3(-sin(a), cos(a), 0f) * 0.012f
            flat(b - side, b + side, tip, tip) }
    }.build()

    /** A few wild flowers on short stems. */
    fun flowerPatch() = PM().apply {
        val heads = listOf(c(240, 230, 90), c(240, 240, 250), c(200, 90, 200), c(230, 80, 70))
        for (k in 0 until 5) { val a = k * 1.3f; val r = 0.05f + 0.03f * (k % 2); val h = 0.1f + 0.04f * (k % 3)
            col = c(70, 120, 50); tube(listOf(Vec3(cos(a) * r, sin(a) * r, 0f), Vec3(cos(a) * r, sin(a) * r, h)), 0.006f, 4)
            col = heads[k % heads.size]; at(cos(a) * r, sin(a) * r, h) { scale(1f, 1f, 0.5f); sphere(0.025f, 8, 4) } }
        grassTuftInto(this)
    }.build()
    private fun grassTuftInto(pm: PM) = pm.apply { for (k in 0 until 4) { val a = k * 1.6f; col = c(90, 140, 55)
        val b = Vec3(cos(a) * 0.03f, sin(a) * 0.03f, 0f); val tip = Vec3(cos(a) * 0.07f, sin(a) * 0.07f, 0.12f); val side = Vec3(-sin(a), cos(a), 0f) * 0.01f
        flat(b - side, b + side, tip, tip) } }

    /** Stone well with a little tiled roof and a bucket. */
    fun well() = PM().apply {
        col = c(150, 144, 132); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.3f, 0f), floatArrayOf(0.3f, 0.32f), floatArrayOf(0.24f, 0.32f), floatArrayOf(0.24f, 0.05f), floatArrayOf(0f, 0.05f)), 18, false)
        col = c(30, 50, 70); at(z = 0.2f) { cylinder(0.24f, 0.01f, 18) }
        col = c(110, 80, 50); for (sy in floatArrayOf(-0.26f, 0.26f)) at(0f, sy, 0.3f) { box(0.05f, 0.05f, 0.5f) }
        at(z = 0.6f) { rotX((PI / 2).toFloat()); cylinder(0.025f, 0.56f, 8) }
        col = c(140, 60, 40); for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.12f, 0f, 0.78f); rotY(s * 0.75f); box(0.32f, 0.66f, 0.03f); pop() }
        col = c(100, 70, 40); at(0.05f, 0f, 0.42f) { cylinder(0.05f, 0.08f, 10, 0.06f) }
    }.build()

    /** Crown of a turret: a round parapet of merlons with gaps where the path comes in and leaves ([gaps] are angles in
     *  the prop's xy plane), a flagpole with a pennant and two torches. About one cell across. */
    /** Where on the turret crown ring its flagpole stands: the first spot clear of the path gaps. */
    fun turretFlagAngle(gaps: List<Float>): Float {
        fun inGap(a: Float) = gaps.any { g -> val d = kotlin.math.abs(((a - g + 9.42478f) % 6.28318f) - 3.14159f); d < 0.62f }
        var fa = 0f; while (inGap(fa)) fa += 0.7f
        return fa
    }
    val turretPennant by lazy { pennantCloth(c(200, 30, 40), c(250, 214, 40), 0.3f, 0.1f) }

    fun turretCrown(gaps: List<Float>) = PM().apply {
        val stone = c(160, 152, 138); val dark = c(120, 114, 104)
        fun inGap(a: Float) = gaps.any { g -> val d = kotlin.math.abs(((a - g + 9.42478f) % 6.28318f) - 3.14159f); d < 0.62f }
        val nM = 14
        for (k in 0 until nM) {
            val a = k * 6.28318f / nM
            if (inGap(a)) continue
            val r = 0.6f
            col = if (k % 2 == 0) stone else dark
            push(); translate(cos(a) * r, sin(a) * r, 0f); rotZ(a); box(0.12f, 0.2f, 0.34f); pop()                // parapet
            if (k % 2 == 0) { col = stone; push(); translate(cos(a) * r, sin(a) * r, 0.24f); rotZ(a); box(0.13f, 0.18f, 0.14f); pop() }   // merlon
        }
        val fa = turretFlagAngle(gaps)
        col = c(80, 60, 40); at(cos(fa) * 0.6f, sin(fa) * 0.6f, 0.3f) { cylinder(0.012f, 0.62f, 6) }      // flagpole (its pennant flies as cloth)
        col = c(214, 168, 70); at(cos(fa) * 0.6f, sin(fa) * 0.6f, 0.93f) { sphere(0.016f, 6, 4) }
        var ta = fa + 3.14f; while (inGap(ta)) ta += 0.7f
        for (dt in floatArrayOf(-0.45f, 0.45f)) { val a = ta + dt; if (inGap(a)) continue
            col = c(60, 44, 30); at(cos(a) * 0.52f, sin(a) * 0.52f, 0.3f) { cylinder(0.012f, 0.12f, 5) }
            col = c(255, 140, 40) * 2.4f; at(cos(a) * 0.52f, sin(a) * 0.52f, 0.43f) { scale(1f, 1f, 1.6f); sphere(0.025f, 8, 6) } }
    }.build()

    // ---------------------------------------------------------------- ground animals
    private fun monkey() = PM().apply {
        // a sitting plush chimp: soft black pile with a slight sheen variation, a fluffy fringe round a heart-shaped tan face,
        // brow ridge, big glossy eyes under tan lids, a full muzzle with nostrils, philtrum and a smile, cupped ears, long arms
        // bent at the elbow ending in fingered hands, and big padded feet with toes stuck out in front
        fun pile(seed: Int, base: Vec3): (Float, Float) -> Vec3 = { u, v ->
            val k = (((u * 37f).toInt() * 73 + (v * 23f).toInt() * 151 + seed) * 2654435761L ushr 9) % 1000 / 1000f
            base * (0.86f + 0.26f * k) }
        val fur = c(34, 28, 28); val tan = c(232, 172, 116); val tanDk = c(204, 140, 90); val tanLt = c(244, 196, 146)
        at(0f, 0f, 0.3f) { scale(0.85f, 1f, 1.05f); sphere(0.25f, 24, 18, pile(1, fur)) }                                  // body
        at(0.12f, 0f, 0.3f) { scale(0.5f, 0.8f, 0.85f); sphere(0.2f, 18, 12, pile(2, c(52, 44, 42))) }                    // chest
        at(0.01f, 0f, 0.69f) { scale(0.96f, 1.06f, 1f); sphere(0.205f, 24, 18, pile(3, fur)) }                             // head
        for (k in 0 until 7) { val a = 0.35f + k * 0.4f                                                                    // soft fur cheeks framing the face
            at(0.08f + 0.05f * sin(a), 0.17f * cos(a), 0.66f + 0.12f * sin(a)) { scale(0.8f, 1f, 1f); sphere(0.07f, 12, 8, pile(10 + k, fur)) } }
        for (k in 0 until 5) at(-0.04f + 0.03f * k, (k - 2) * 0.03f, 0.88f) { sphere(0.03f, 8, 6, pile(30 + k, fur)) }    // a tuft on top
        col = tan
        at(0.13f, 0f, 0.72f) { scale(0.5f, 1.06f, 0.62f); sphere(0.14f, 20, 14, pile(4, tan)) }                            // upper face
        at(0.17f, 0f, 0.6f) { scale(0.74f, 0.94f, 0.74f); sphere(0.12f, 20, 14, pile(5, tan)) }                            // muzzle
        at(0.205f, 0f, 0.53f) { scale(0.6f, 0.7f, 0.4f); sphere(0.07f, 12, 8, pile(6, tanLt)) }                            // chin
        col = tanDk; at(0.17f, 0f, 0.78f) { scale(0.5f, 1.15f, 0.22f); sphere(0.12f, 16, 8) }                             // brow ridge
        col = c(150, 90, 60); at(0.25f, 0f, 0.6f) { box(0.006f, 0.006f, 0.04f) }                                          // philtrum
        col = tanDk; for (s in intArrayOf(-1, 1)) at(0.245f, s * 0.017f, 0.635f) { scale(1f, 1.2f, 0.8f); sphere(0.012f, 8, 6) }   // nostrils
        col = c(110, 60, 40); tube((0..8).map { k -> val v = k / 8f - 0.5f; Vec3(0.236f - 0.025f * v * v * 4f, v * 0.13f, 0.57f + 0.035f * v * v * 4f) }, 0.006f, 6)   // smile
        for (s in intArrayOf(-1, 1)) {                                                                                     // big glossy eyes under tan lids
            col = c(160, 70, 24); at(0.19f, s * 0.058f, 0.715f) { sphere(0.037f, 16, 12) }
            col = c(60, 30, 14); at(0.2f, s * 0.058f, 0.715f) { sphere(0.031f, 16, 12) }
            col = c(14, 10, 8); at(0.212f, s * 0.058f, 0.715f) { sphere(0.022f, 14, 10) }
            col = c(255, 255, 255) * 1.4f; at(0.227f, s * 0.048f, 0.73f) { sphere(0.008f, 8, 6) }; at(0.226f, s * 0.066f, 0.703f) { sphere(0.004f, 6, 4) }
            col = tan; at(0.188f, s * 0.058f, 0.742f) { scale(1f, 1.15f, 0.42f); sphere(0.04f, 12, 8) }                    // lids
        }
        for (s in intArrayOf(-1, 1)) {                                                                                     // cupped ears
            col = tan; at(-0.01f, s * 0.208f, 0.7f) { scale(0.38f, 0.55f, 0.85f); sphere(0.1f, 16, 12) }
            col = tanDk; at(0.015f, s * 0.214f, 0.7f) { scale(0.2f, 0.4f, 0.62f); sphere(0.1f, 12, 8) }
            col = tanLt; at(0.0f, s * 0.24f, 0.7f) { rotX((PI / 2).toFloat()); scale(1f, 1.5f, 1f); torus(0.04f, 0.012f, 14, 6) }
        }
        for (s in intArrayOf(-1, 1)) {
            val sh = Vec3(0.0f, s * 0.2f, 0.5f); val el = Vec3(0.06f, s * 0.31f, 0.3f); val wr = Vec3(0.13f, s * 0.34f, 0.1f)
            // the whole arm as one soft curve from shoulder through a gently bent elbow to the wrist
            val arm = (0..12).map { k -> val t = k / 12f; val a = sh * ((1 - t) * (1 - t)) + el * (2 * (1 - t) * t * 1.0f) + wr * (t * t)
                a + (el - (sh + wr) * 0.5f) * (2 * (1 - t) * t) }
            col = fur; tube(arm, 0.074f, 16, taper = { 1f - 0.18f * it })
            col = tan; at(0.16f, s * 0.35f, 0.05f) { scale(1.4f, 1f, 0.55f); sphere(0.075f, 14, 10, pile(50 + s, tan)) }      // palm
            for (f in 0 until 4) { col = tan; tube(listOf(Vec3(0.2f, s * (0.32f + f * 0.02f), 0.05f), Vec3(0.245f, s * (0.32f + f * 0.022f), 0.03f)), 0.013f, 6) }   // fingers
            tube(listOf(Vec3(0.15f, s * 0.305f, 0.06f), Vec3(0.18f, s * 0.29f, 0.07f)), 0.013f, 6)                           // thumb
            col = fur                                                                                                          // legs out in front
            tube(listOf(Vec3(0.02f, s * 0.12f, 0.16f), Vec3(0.18f, s * 0.15f, 0.11f), Vec3(0.3f, s * 0.16f, 0.1f)), 0.09f, 16, taper = { 1f - 0.12f * it })
            col = tan; at(0.36f, s * 0.16f, 0.11f) { scale(0.55f, 0.85f, 1.25f); sphere(0.095f, 18, 12, pile(60 + s, tan)) }   // sole
            col = tanDk; at(0.375f, s * 0.16f, 0.09f) { scale(0.3f, 0.6f, 0.6f); sphere(0.07f, 10, 8) }                        // heel pad
            col = tan; for (f in 0 until 4) at(0.392f, s * 0.16f + (f - 1.5f) * 0.032f, 0.205f) { scale(0.8f, 1f, 1.1f); sphere(0.019f, 8, 6) }   // toes
            at(0.39f, s * 0.215f, 0.13f) { sphere(0.022f, 8, 6) }                                                              // big toe
        }
    }.build()

    private fun dino() = PM().apply {
        // Brachiosaurus: tall shoulders and a long neck rising steeply to a small head with the raised nasal arch, a back
        // sloping to a long tail; muscular thighs and upper arms on columnar legs with elephantine feet and toenails; olive
        // skin mottled darker on top, a paler belly and throat, wrinkle bands on the neck and rows of small scales
        val skin = c(128, 140, 80); val dark = c(92, 106, 58); val pale = c(200, 196, 150)
        val ctrl = listOf(Vec3(-0.8f, 0f, 0.1f), Vec3(-0.56f, 0f, 0.27f), Vec3(-0.3f, 0f, 0.47f), Vec3(-0.08f, 0f, 0.56f), Vec3(0.12f, 0f, 0.63f),
            Vec3(0.23f, 0f, 0.8f), Vec3(0.31f, 0f, 1.0f), Vec3(0.38f, 0f, 1.14f))
        val rad = floatArrayOf(0.012f, 0.07f, 0.17f, 0.21f, 0.19f, 0.1f, 0.075f, 0.06f)
        fun cr(a: Vec3, b: Vec3, c2: Vec3, d: Vec3, t: Float): Vec3 { val t2 = t * t; val t3 = t2 * t
            return (b * 2f + (c2 - a) * t + (a * 2f - b * 5f + c2 * 4f - d) * t2 + (b * 3f - a - c2 * 3f + d) * t3) * 0.5f }
        val path = ArrayList<Vec3>(); val rr = ArrayList<Float>()
        for (i in 0 until ctrl.size - 1) for (k in 0 until 10) {
            val t = k / 10f
            path.add(cr(ctrl[maxOf(i - 1, 0)], ctrl[i], ctrl[i + 1], ctrl[minOf(i + 2, ctrl.size - 1)], t)); rr.add(rad[i] + (rad[i + 1] - rad[i]) * (t * t * (3f - 2f * t)))
        }
        path.add(ctrl.last()); rr.add(rad.last())
        val nP = path.size
        col = skin
        tube(path, 1f, 24, colorFn = { v ->
            val ring = (v * (nP - 1)).toInt()
            val wrinkle = v > 0.6f && ring % 3 == 0                                                       // neck folds
            val mott = ((ring * 37) % 7) / 7f
            if (wrinkle) skin * 0.88f else skin * (0.98f + 0.04f * mott) }, taper = { v -> rr[minOf((v * (nP - 1)).toInt(), nP - 1)] })
        col = pale; at(-0.1f, 0f, 0.43f) { scale(1.75f, 0.84f, 0.55f); sphere(0.17f, 22, 12) }                                 // belly
        at(0.17f, 0f, 0.6f) { rotY(-0.9f); scale(0.9f, 0.55f, 0.45f); sphere(0.13f, 16, 10) }                                  // throat
        for (k in 0 until 24) { val i = 30 + k * 2; if (i >= nP) break; val q = path[i]
            col = pale * 0.95f; at(q.x + 0.01f, 0f, q.z - rr[i] * 0.86f) { scale(1f, 1.4f, 0.4f); sphere(0.03f, 8, 4) } }          // throat-to-chest pale stripe
        col = dark; for (k in 0 until 22) { val q = path[8 + k * 2]; if (8 + k * 2 >= nP) break; at(q.x, 0f, q.z + rr[8 + k * 2] * 0.93f) { scale(1.2f, 1f, 0.6f); sphere(0.022f, 8, 6) } }   // ridge
        for (k in 0 until 220) {                                                                                                  // scales and darker mottles over the back
            val i = 6 + (k * 7) % (nP - 8); val q = path[i]; val a = -1.25f + 2.5f * ((k * 13) % 17) / 16f
            col = if (k % 5 == 0) dark * 0.85f else if (k % 3 == 0) skin * 1.08f else dark * 1.05f
            push(); translate(q.x, sin(a) * rr[i] * 0.985f, q.z + cos(a) * rr[i] * 0.985f); rotX(-a); scale(1f, 1f, 0.3f); sphere(0.01f + 0.005f * (k % 3), 6, 4); pop()
        }
        val h = ctrl.last()
        col = skin; at(h.x + 0.07f, 0f, h.z - 0.005f) { scale(1.9f, 0.78f, 0.62f); sphere(0.066f, 18, 12) }                     // snout
        at(h.x + 0.02f, 0f, h.z + 0.02f) { scale(1.15f, 0.9f, 0.85f); sphere(0.058f, 16, 12) }                                  // skull
        at(h.x + 0.06f, 0f, h.z + 0.065f) { scale(1.3f, 0.75f, 0.85f); sphere(0.04f, 14, 10) }                                  // nasal arch
        col = c(40, 34, 28); for (sg in intArrayOf(-1, 1)) at(h.x + 0.08f, sg * 0.018f, h.z + 0.09f) { scale(1.4f, 1f, 0.6f); sphere(0.01f, 6, 4) }   // nostrils on top
        col = c(60, 44, 36); tube((0..6).map { k -> val v = k / 6f; Vec3(h.x - 0.01f + 0.21f * v, 0f, h.z - 0.03f + 0.005f * kotlin.math.sin(v * 3f)) }, 0.004f, 4)   // mouth line
        col = c(70, 56, 44); for (sg in intArrayOf(-1, 1)) tube((0..6).map { k -> val v = k / 6f; Vec3(h.x - 0.01f + 0.2f * v, sg * 0.05f * (1f - 0.4f * v), h.z - 0.03f) }, 0.003f, 4)
        for (sg in intArrayOf(-1, 1)) {
            col = skin * 0.9f; at(h.x + 0.045f, sg * 0.044f, h.z + 0.045f) { scale(1.4f, 0.6f, 0.5f); sphere(0.02f, 8, 6) }        // brow ridge
            col = c(220, 200, 150); at(h.x + 0.05f, sg * 0.045f, h.z + 0.03f) { sphere(0.013f, 10, 8) }
            col = c(20, 16, 12); at(h.x + 0.057f, sg * 0.051f, h.z + 0.031f) { sphere(0.008f, 8, 6) }
            col = c(255, 255, 255) * 1.3f; at(h.x + 0.06f, sg * 0.054f, h.z + 0.035f) { sphere(0.003f, 4, 3) }
        }
        for ((lx, top, len) in listOf(Triple(0.1f, 0.56f, 1f), Triple(-0.3f, 0.44f, 0.82f))) for (sg in intArrayOf(-1, 1)) {
            val front = len > 0.9f
            col = skin
            at(lx + (if (front) 0.02f else -0.02f), sg * 0.135f, top - 0.06f) { scale(if (front) 0.85f else 1.05f, 0.6f, 1.2f); sphere(if (front) 0.1f else 0.12f, 16, 12) }   // shoulder / thigh
            // one continuous leg: hip/shoulder, a slight knee (forward on the hind leg, back on the front), down to the foot
            val bend = if (front) -0.02f else 0.035f
            val leg = (0..12).map { k -> val t = k / 12f; Vec3(lx + bend * kotlin.math.sin(t * PI.toFloat()), sg * (0.14f + 0.01f * t), (top - 0.08f) * (1f - t) + 0.05f * t) }
            tube(leg, 0.078f * (0.85f + 0.15f * len), 18, taper = { t -> 1f - 0.22f * t + 0.18f * (t * t * t) })
            at(lx, sg * 0.15f, 0f) { cylinder(0.074f, 0.07f, 18, 0.062f) }                                                       // columnar foot
            col = c(226, 218, 190); for (t in -1..2) { val a = t * 0.45f - 0.2f
                at(lx + cos(a) * 0.07f, sg * 0.15f + sin(a) * 0.07f, 0.014f) { scale(1f, 0.85f, 0.7f); sphere(0.02f, 8, 6) } }       // toenails
            if (!front) { col = c(200, 190, 160); at(lx + 0.075f, sg * 0.13f, 0.03f) { rotY(1.2f); cone(0.012f, 0.045f, 6) } }      // the big thumb-claw of the hind foot
        }
    }.build()

    // ---------------------------------------------------------------- candy
    private fun gumdrop() = PM().apply {
        val cols = c(255, 70, 110)
        col = cols
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.5f, 0f), floatArrayOf(0.5f, 0.1f), floatArrayOf(0.44f, 0.4f),
            floatArrayOf(0.3f, 0.75f), floatArrayOf(0.12f, 0.96f), floatArrayOf(0f, 1f)), 20) { u, v -> if ((u * 24f).toInt() % 2 == 0 && v > 0.15f) c(255, 235, 240) * (0.85f + 0.15f * v) else cols }
    }.build()

    private fun cupcake() = PM().apply {
        col = c(240, 150, 190)
        // paper case with pleats
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.30f, 0f), floatArrayOf(0.44f, 0.38f), floatArrayOf(0f, 0.38f)), 24, false) { u, _ ->
            if ((u * 16f).toInt() % 2 == 0) c(250, 200, 220) else c(236, 120, 170) }
        // sponge dome
        col = c(204, 148, 92); at(z = 0.36f) { sphere(0.44f, 20, 8) }
        // frosting swirl: three stacked tapering rings
        col = c(255, 236, 244)
        for (i in 0 until 3) at(z = 0.50f + i * 0.14f) { torus(0.26f - i * 0.07f, 0.15f - i * 0.025f, 20, 8) }
        // cherry and sprinkles
        col = c(220, 20, 50); at(z = 0.95f) { sphere(0.11f, 12, 8) }
        for (i in 0 until 14) {
            val a = i * 2.4f; val r = 0.1f + 0.2f * ((i * 7) % 5) / 5f
            col = listOf(c(80, 200, 255), c(255, 220, 60), c(120, 220, 120), c(255, 90, 90))[i % 4]
            push(); translate(cos(a) * r, sin(a) * r, 0.58f + (0.3f - r) * 1.2f); rotZ(a); rotY(1.1f); scale(0.12f, 0.025f, 0.025f); sphere(0.5f, 6, 4); pop()
        }
    }.build()

    private fun lollipop() = PM().apply {
        col = c(250, 250, 250); cylinder(0.025f, 0.62f, 8)
        // swirl disc: concentric coloured rings give the spiral read
        push(); translate(0f, 0f, 0.8f); rotX((PI / 2).toFloat())
        val ringCols = listOf(c(255, 60, 130), c(255, 240, 240), c(60, 190, 255), c(255, 240, 240), c(255, 200, 40), c(255, 240, 240))
        for (i in 0 until 6) { col = ringCols[i]; push(); translate(0f, 0f, 0.0f); scale(1f, 1f, 0.35f); torus(0.04f + i * 0.06f, 0.035f, 20, 6); pop() }
        pop()
        col = c(255, 60, 130); at(z = 0.8f) { push(); rotX((PI / 2).toFloat()); scale(1f, 1f, 0.35f); sphere(0.12f, 10, 6); pop() }
    }.build()

    private fun candyCane() = PM().apply {
        val path = ArrayList<Vec3>()
        for (i in 0..8) path.add(Vec3(0f, 0f, i * 0.09f))
        for (i in 1..14) { val a = PI.toFloat() * i / 14f; path.add(Vec3(0.2f - 0.2f * cos(a), 0f, 0.72f + 0.2f * sin(a))) }
        tube(path, 0.065f, 10, false, { t -> if ((t * 22f).toInt() % 2 == 0) c(235, 40, 60) else c(255, 250, 250) })
    }.build()

    // ---------------------------------------------------------------- liquorice allsorts
    private val LIQ = c(22, 16, 20)
    private fun PM.layerBox(w: Float, d: Float, layers: List<Vec3>, lh: Float, z0: Float = 0f) {
        for ((i, lc) in layers.withIndex()) { col = lc; push(); translate(0f, 0f, z0 + lh * (i + 0.5f)); scale(1f - 0.012f * (i % 2), 1f - 0.012f * (i % 2), 1f); box(w, d, lh * 0.97f); pop() }
    }
    private fun allsortLayered(scale: Float) = PM().apply {
        // the classic sandwich: liquorice and coloured coconut/aniseed layers
        scale(1f / 0.9f * scale * 1.6f)
        layerBox(0.5f, 0.3f, listOf(LIQ, c(250, 120, 170), LIQ, c(245, 245, 238), LIQ, c(250, 120, 170), LIQ), 0.12f)
    }.build()

    private fun allsortsPile() = PM().apply {
        // layered cuboid
        push(); translate(-0.14f, 0.06f, 0f); rotZ(0.35f); layerBox(0.34f, 0.22f, listOf(LIQ, c(255, 220, 70), LIQ, c(245, 245, 238), LIQ, c(250, 120, 170), LIQ), 0.1f); pop()
        // Catherine wheel: spiral of liquorice and sugar, standing on edge
        push(); translate(0.2f, -0.12f, 0.2f); rotX((PI / 2).toFloat()); rotZ(0.4f)
        val spiral = listOf(LIQ, c(255, 140, 190), LIQ, c(245, 245, 238), LIQ, c(255, 140, 190), LIQ)
        for ((i, sc) in spiral.withIndex()) { col = sc; push(); translate(0f, 0f, 0f); scale(1f, 1f, 1f); torus(0.025f + i * 0.026f, 0.015f, 20, 6); pop() }
        col = LIQ; cylinder(0.205f, 0.09f, 28); pop()
        // bobble: liquorice core rolled in blue sugar beads
        push(); translate(-0.02f, -0.24f, 0f)
        col = LIQ; cylinder(0.11f, 0.26f, 14)
        for (k in 0 until 40) { val a = k * 2.399f; val z = 0.02f + 0.22f * ((k * 7) % 11) / 11f
            col = if (k % 3 == 0) c(250, 120, 170) else c(90, 170, 255); push(); translate(cos(a) * 0.115f, sin(a) * 0.115f, z); sphere(0.026f, 6, 4); pop() }
        pop()
        // pink coconut roll with liquorice centre
        push(); translate(0.12f, 0.2f, 0.11f); rotY((PI / 2).toFloat()); rotZ(0.5f)
        col = c(250, 125, 175); cylinder(0.11f, 0.3f, 18); col = LIQ; cylinder(0.05f, 0.302f, 12); pop()
        // a loose blue-and-pink jelly bead or two
        col = c(90, 170, 255); push(); translate(-0.26f, -0.18f, 0.04f); sphere(0.045f, 8, 6); pop()
        col = c(255, 120, 180); push(); translate(0.3f, 0.04f, 0.04f); sphere(0.04f, 8, 6); pop()
    }.build()

    // ---------------------------------------------------------------- day of the dead
    private fun marigold() = PM().apply {
        col = c(60, 130, 50); cylinder(0.04f, 0.35f, 8)
        for (layer in 0 until 3) for (i in 0 until 12) {
            val a = (i + layer * 0.5f) * (2 * PI / 12).toFloat()
            col = Vec3.lerp(c(255, 170, 30), c(255, 110, 10), layer / 2f)
            push(); translate(0f, 0f, 0.42f + layer * 0.05f); rotZ(a); rotY(0.5f - layer * 0.2f); translate(0.18f - layer * 0.04f, 0f, 0f); scale(0.3f - layer * 0.05f, 0.1f, 0.03f); sphere(0.5f, 8, 4); pop()
        }
        col = c(150, 60, 10); at(z = 0.5f) { sphere(0.08f, 8, 6) }
    }.build()

    private fun sugarSkull() = PM().apply {
        // calavera: rounded cranium with temples, cheekbones and a jaw, deep eye sockets ringed with petals,
        // an upside-down heart nose, stitched smile and teeth, painted forehead flower and dotted scrollwork
        val bone = c(250, 246, 236)
        col = bone
        at(z = 0.58f) { scale(0.86f, 0.82f, 0.92f); sphere(0.4f, 28, 18) }                                  // cranium
        for (s in intArrayOf(-1, 1)) at(s * 0.2f, -0.2f, 0.38f) { scale(0.9f, 0.8f, 0.7f); sphere(0.14f, 14, 10) }   // cheekbones
        at(0f, -0.08f, 0.24f) { scale(0.78f, 0.7f, 0.55f); sphere(0.3f, 22, 12) }                         // jaw
        at(0f, -0.24f, 0.18f) { scale(1.2f, 0.6f, 0.5f); sphere(0.12f, 14, 8) }                            // chin
        for (s in intArrayOf(-1, 1)) {
            col = c(30, 14, 46); at(s * 0.14f, -0.3f, 0.52f) { scale(1f, 0.45f, 1.05f); sphere(0.105f, 16, 10) }        // socket
            col = c(255, 255, 255) * 1.3f; at(s * 0.12f, -0.345f, 0.55f) { sphere(0.018f, 6, 4) }                       // glint
            for (i in 0 until 10) {                                                                                        // petals around the eye
                val a = i * 0.628f
                col = if (i % 2 == 0) c(255, 130, 30) else c(255, 196, 40)
                push(); translate(s * 0.14f + cos(a) * 0.135f, -0.325f, 0.52f + sin(a) * 0.14f); rotY(a); scale(1.6f, 0.4f, 0.9f); sphere(0.028f, 8, 5); pop()
            }
            col = c(40, 200, 190); for (k in 0 until 5) at(s * (0.3f + 0.02f * k), -0.2f + 0.04f * k, 0.32f + 0.06f * k) { sphere(0.016f, 6, 4) }   // cheek dots
            col = c(240, 70, 120); at(s * 0.24f, -0.27f, 0.35f) { scale(1f, 0.4f, 1f); sphere(0.04f, 10, 6) }                                   // cheek rose
        }
        col = c(40, 16, 50); push(); translate(0f, -0.345f, 0.4f); rotY(PI.toFloat()); scale(1f, 0.4f, 1f)                 // heart-shaped nose
        for (s in intArrayOf(-1, 1)) at(s * 0.025f, 0f, -0.01f) { sphere(0.03f, 8, 6) }; at(z = 0.02f) { cone(0.045f, 0.06f, 8) }
        pop()
        col = c(40, 16, 50); tube((0..10).map { k -> val u = k / 10f - 0.5f; Vec3(u * 0.34f, -0.33f + 0.02f * (u * u * 4f), 0.29f + 0.03f * (u * u * 4f)) }, 0.008f, 4)    // smile
        col = bone * 1.02f; for (i in -4..4) at(i * 0.036f, -0.335f, 0.28f + 0.012f * (i * i) / 4f) { box(0.026f, 0.02f, 0.05f) }
        col = c(40, 16, 50); for (i in -4..4) at(i * 0.036f + 0.018f, -0.345f, 0.28f + 0.012f * (i * i) / 4f) { box(0.004f, 0.006f, 0.05f) }
        // forehead flower and scrollwork
        for (i in 0 until 6) { val a = i * 1.047f; col = if (i % 2 == 0) c(240, 70, 120) else c(150, 80, 220)
            push(); translate(cos(a) * 0.055f, -0.36f + 0.02f, 0.78f + sin(a) * 0.055f); scale(1f, 0.35f, 1f); sphere(0.04f, 10, 6); pop() }
        col = c(255, 214, 40); at(0f, -0.37f, 0.78f) { scale(1f, 0.4f, 1f); sphere(0.028f, 8, 6) }
        col = c(40, 200, 190); for (s in intArrayOf(-1, 1)) tube((0..8).map { k -> val v = k / 8f; Vec3(s * (0.1f + 0.14f * v), -0.34f + 0.1f * v * v, 0.7f + 0.12f * kotlin.math.sin(v * 3.2f)) }, 0.008f, 4)
        col = c(255, 214, 40); for (k in 0 until 7) at(-0.15f + k * 0.05f, -0.3f, 0.92f - 0.01f * (k - 3) * (k - 3)) { sphere(0.014f, 6, 4) }
    }.build()

    private fun candleVotive() = PM().apply {
        col = c(255, 214, 120); cylinder(0.2f, 0.55f, 20, 0.2f)
        col = c(240, 90, 60); at(z = 0.2f) { torus(0.2f, 0.012f, 20, 4) }
        col = c(60, 30, 20); cylinder(0.24f, 0.06f, 20)
    }.build()

    private fun candelabra() = PM().apply {
        col = c(214, 168, 70); cylinder(0.3f, 0.06f, 16, 0.2f); cylinder(0.04f, 0.8f, 10)
        at(z = 0.4f) { torus(0.06f, 0.03f, 10, 6) }
        for (s in -1..1) {
            val x = s * 0.3f
            val path = listOf(Vec3(0f, 0f, 0.6f), Vec3(x * 0.4f, 0f, 0.58f), Vec3(x * 0.9f, 0f, 0.65f), Vec3(x, 0f, 0.78f))
            col = c(214, 168, 70); tube(path, 0.025f, 8)
            at(x, 0f, 0.78f) { col = c(214, 168, 70); cylinder(0.06f, 0.03f, 10); col = c(250, 240, 220); cylinder(0.035f, 0.16f, 10)
                col = c(60, 40, 30); at(z = 0.16f) { cylinder(0.006f, 0.02f, 4) } }
        }
    }.build()

    // ---------------------------------------------------------------- motorsport
    private fun trafficCone() = PM().apply {
        // a real traffic cone: square black rubber base with chamfered corners and a moulded collar, a slim tapering body in
        // fluorescent orange with two crisp retroreflective white bands, a rolled lip round the open top, and road grime low down
        col = c(28, 28, 30); at(z = 0.02f) { box(0.56f, 0.56f, 0.04f) }
        col = c(36, 36, 38); at(z = 0.045f) { box(0.5f, 0.5f, 0.012f) }
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) { col = c(20, 20, 22); at(sx * 0.26f, sy * 0.26f, 0.02f) { rotZ(0.785f); box(0.06f, 0.02f, 0.041f) } }
        val orange = c(255, 92, 12); val white = c(250, 250, 246) * 1.05f
        // profile: radius vs height, with doubled rings at the band edges so the bands stay crisp
        val zs = listOf(0f, 0.03f, 0.06f, 0.2f, 0.34f, 0.3401f, 0.47f, 0.4701f, 0.56f, 0.5601f, 0.66f, 0.6601f, 0.8f, 0.9f, 0.93f)
        fun r(z: Float) = 0.2f - 0.165f * (z / 0.93f) - 0.012f * kotlin.math.sin(z / 0.93f * PI.toFloat())   // a slight inward curve
        val prof = ArrayList<FloatArray>()
        prof.add(floatArrayOf(0f, 0f)); prof.add(floatArrayOf(0.215f, 0f)); prof.add(floatArrayOf(0.215f, 0.03f))      // moulded collar
        for (z in zs.drop(2)) prof.add(floatArrayOf(r(z), z))
        prof.add(floatArrayOf(0.045f, 0.945f)); prof.add(floatArrayOf(0.03f, 0.945f)); prof.add(floatArrayOf(0.028f, 0.9f))   // rolled lip, open top
        val zAt = prof.map { it[1] }
        push(); translate(0f, 0f, 0.05f)
        lathe(prof, 32, false) { _, v ->
            val i = Math.round(v * (prof.size - 1)).coerceIn(0, prof.size - 1); val z = zAt[i]
            when {
                i <= 2 -> c(40, 40, 42)
                (z > 0.3401f && z < 0.4701f) || (z > 0.5601f && z < 0.6601f) -> white
                z == 0.3401f || z == 0.5601f -> white
                z < 0.2f -> orange * (0.8f + z)                                                   // grime near the ground
                else -> orange
            }
        }
        col = c(18, 18, 20); at(z = 0.98f) { cylinder(0.028f, 0.004f, 14) }                                                  // the dark hollow inside
        pop()
    }.build()

    private fun tyreStack() = PM().apply {
        // three racing tyres with tread grooves and rims, strapped with a sponsor band
        for (k in 0 until 3) {
            val z = 0.1f + k * 0.2f; val tw = k * 0.07f
            push(); translate(0.02f * sin(k * 2f), 0.02f * cos(k * 2f), z); rotZ(tw)
            col = c(26, 26, 30); torus(0.26f, 0.1f, 28, 12)
            col = c(44, 44, 50); for (g in 0 until 24) { push(); rotZ(g * 0.2618f); translate(0.355f, 0f, 0f); box(0.012f, 0.02f, 0.16f); pop() }
            col = c(250, 214, 40); torus(0.2f, 0.012f, 24, 4)
            pop()
        }
        col = c(230, 40, 40); push(); translate(0f, -0.365f, 0.3f); box(0.3f, 0.01f, 0.5f); pop()
        col = c(250, 250, 250); push(); translate(0f, -0.37f, 0.36f); box(0.22f, 0.006f, 0.08f); pop()
    }.build()

    private fun trophy() = PM().apply {
        col = c(255, 206, 70)
        lathe(listOf(floatArrayOf(0f, 0.0f), floatArrayOf(0.3f, 0f), floatArrayOf(0.3f, 0.08f), floatArrayOf(0.12f, 0.12f), floatArrayOf(0.05f, 0.2f),
            floatArrayOf(0.05f, 0.45f), floatArrayOf(0.12f, 0.5f), floatArrayOf(0.2f, 0.65f), floatArrayOf(0.34f, 0.92f), floatArrayOf(0.3f, 0.98f), floatArrayOf(0.28f, 0.93f)), 24)
        for (s in intArrayOf(-1, 1)) {
            val p = ArrayList<Vec3>(); for (i in 0..10) { val a = PI.toFloat() * i / 10f; p.add(Vec3(s * (0.3f + 0.12f * sin(a)), 0f, 0.8f - 0.25f * (1f - cos(a)) * 0.5f - 0.1f * i / 10f)) }
            tube(p, 0.025f, 8)
        }
        col = c(50, 40, 30); cylinder(0.32f, 0.04f, 4)
    }.build()

    private fun flagPole() = PM().apply {
        col = c(60, 62, 70); cylinder(0.07f, 0.05f, 12, 0.05f)                                         // base
        col = c(220, 222, 228); cylinder(0.02f, 1f, 10, 0.014f)
        col = c(255, 214, 80); at(z = 1f) { sphere(0.032f, 10, 8) }                                    // gilt finial
        col = c(240, 240, 236); tube(listOf(Vec3(0.022f, 0f, 0.97f), Vec3(0.03f, 0.01f, 0.5f), Vec3(0.024f, 0.012f, 0.32f)), 0.004f, 3)   // halyard
        col = c(120, 120, 128); at(0.024f, 0f, 0.3f) { box(0.012f, 0.03f, 0.04f) }                    // cleat
    }.build()

    // ---------------------------------------------------------------- football
    private fun cornerFlag() = PM().apply {
        col = c(250, 250, 250); cylinder(0.018f, 0.9f, 8, 0.014f)
        col = c(220, 40, 40); for (k in 0 until 4) at(z = 0.1f + k * 0.2f) { cylinder(0.0185f, 0.1f, 8) }   // red bands on the pole
        col = c(30, 30, 30); cylinder(0.1f, 0.025f, 14, 0.06f)
    }.build()

    private fun football() = PM().apply {
        // white ball with dark patches placed on the vertices of an icosahedron (pentagon centres)
        val phi = ((1f + 5f.let { kotlin.math.sqrt(it) }) / 2f)
        val dirs = ArrayList<Vec3>()
        for (a in intArrayOf(-1, 1)) for (b in intArrayOf(-1, 1)) { dirs.add(Vec3(0f, a.toFloat(), b * phi).normalized()); dirs.add(Vec3(a.toFloat(), b * phi, 0f).normalized()); dirs.add(Vec3(b * phi, 0f, a.toFloat()).normalized()) }
        at(z = 0.5f) { sphere(0.5f, 28, 18) { u, v ->
            val th = u * 2f * PI.toFloat(); val ph = v * PI.toFloat()
            val p = Vec3(sin(ph) * cos(th), sin(ph) * sin(th), -cos(ph))
            if (dirs.any { (it dot p) > 0.86f }) c(24, 24, 26) else c(246, 246, 246) } }
    }.build()

    private fun floodlight() = PM().apply {
        // lattice mast with a cage and a bank of lamps that light the pitch
        val steel = c(150, 156, 166)
        col = c(70, 74, 80); cylinder(0.16f, 0.04f, 12)
        col = steel
        for (s in 0 until 3) { val a = s * 2.094f; tube(listOf(Vec3(cos(a) * 0.1f, sin(a) * 0.1f, 0.04f), Vec3(cos(a) * 0.04f, sin(a) * 0.04f, 0.92f)), 0.012f, 5) }
        for (k in 0 until 8) { val z0 = 0.06f + k * 0.11f; val r0 = 0.1f - 0.06f * z0; val a = k * 2.094f
            tube(listOf(Vec3(cos(a) * r0, sin(a) * r0, z0), Vec3(cos(a + 2.094f) * r0, sin(a + 2.094f) * r0, z0 + 0.11f)), 0.006f, 3) }
        at(0f, 0f, 0.98f) {
            col = c(60, 64, 72); box(0.54f, 0.1f, 0.32f)
            for (i in -2..2) for (j in -1..0) { col = c(255, 250, 225) * 2.2f; push(); translate(i * 0.1f, -0.055f, j * 0.13f + 0.065f); rotX((PI / 2).toFloat()); cylinder(0.04f, 0.01f, 10); pop() }
            col = c(60, 64, 72); for (i in -2..2) { push(); translate(i * 0.1f, -0.07f, 0.13f); box(0.08f, 0.04f, 0.012f); pop() }
        }
    }.build()

    // ---------------------------------------------------------------- space
    private fun beacon() = PM().apply {
        // antenna mast: tripod, lattice and a blinking warning light on top (lights its surroundings red)
        val steel = c(170, 178, 192)
        col = c(120, 128, 142); cylinder(0.26f, 0.06f, 14, 0.2f)
        col = steel
        for (s in 0 until 3) { val a = s * 2.094f; tube(listOf(Vec3(cos(a) * 0.18f, sin(a) * 0.18f, 0.05f), Vec3(cos(a) * 0.03f, sin(a) * 0.03f, 0.82f)), 0.012f, 5) }
        for (k in 0 until 6) at(z = 0.15f + k * 0.12f) { torus(0.16f - 0.18f * (0.15f + k * 0.12f), 0.006f, 12, 3) }
        col = c(210, 214, 224); at(z = 0.6f) { push(); rotX(1.2f); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.1f, 0.03f), floatArrayOf(0.12f, 0.05f)), 14, true); pop() }
        col = c(255, 60, 60) * 2.4f; at(z = 0.86f) { sphere(0.06f, 12, 8) }
        col = c(60, 64, 76); at(z = 0.82f) { cylinder(0.05f, 0.02f, 10) }
    }.build()

    private fun dish() = PM().apply {
        col = c(170, 176, 190); cylinder(0.3f, 0.08f, 16, 0.2f); cylinder(0.04f, 0.35f, 8)
        push(); translate(0f, 0f, 0.45f); rotX(-0.9f)
        col = c(230, 232, 240); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.2f, 0.03f), floatArrayOf(0.4f, 0.12f), floatArrayOf(0.45f, 0.16f)), 26, true)
        col = c(120, 128, 142); cylinder(0.012f, 0.3f, 6); at(z = 0.3f) { col = c(255, 90, 160) * 1.8f; sphere(0.03f, 8, 6) }
        pop()
    }.build()

    private fun canister() = PM().apply {
        // ribbed fuel tank with hazard band, valve wheel and carry handle
        val steel = c(206, 212, 222)
        col = steel; lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.26f, 0f), floatArrayOf(0.28f, 0.03f), floatArrayOf(0.28f, 0.62f), floatArrayOf(0.22f, 0.7f), floatArrayOf(0.08f, 0.74f), floatArrayOf(0f, 0.74f)), 24)
        col = c(170, 176, 188); for (k in 0 until 4) at(z = 0.1f + k * 0.15f) { torus(0.283f, 0.012f, 24, 4) }
        for (i in 0 until 8) { col = if (i % 2 == 0) c(250, 190, 30) else c(30, 30, 34); push(); rotZ(i * 0.785f); at(0f, 0f, 0.36f) { lathe(listOf(floatArrayOf(0.286f, 0f), floatArrayOf(0.286f, 0.08f)), 6, false) }; pop() }
        col = c(84, 224, 255) * 1.6f; at(0f, -0.28f, 0.52f) { box(0.12f, 0.01f, 0.05f) }
        col = c(90, 96, 108); at(z = 0.74f) { cylinder(0.03f, 0.1f, 10) }
        col = c(220, 40, 40); at(z = 0.84f) { torus(0.07f, 0.012f, 14, 5) }
        col = c(60, 64, 74); tube(listOf(Vec3(-0.16f, 0f, 0.68f), Vec3(-0.12f, 0f, 0.82f), Vec3(0.12f, 0f, 0.82f), Vec3(0.16f, 0f, 0.68f)), 0.015f, 6)
    }.build()

    private fun rocket() = PM().apply {
        col = c(240, 242, 248)
        lathe(listOf(floatArrayOf(0f, 0.1f), floatArrayOf(0.14f, 0.1f), floatArrayOf(0.15f, 0.6f), floatArrayOf(0.1f, 0.85f), floatArrayOf(0f, 1f)), 24, true) { _, v -> if (v > 0.78f) c(230, 50, 50) else c(240, 242, 248) }
        col = c(60, 160, 255); at(0f, -0.145f, 0.55f) { sphere(0.06f, 10, 8) }
        col = c(230, 50, 50)
        for (i in 0 until 3) { push(); rotZ(i * 2.094f); rotX((PI / 2).toFloat()); translate(0f, 0f, -0.0125f)
            prism(listOf(floatArrayOf(0.12f, 0.08f), floatArrayOf(0.34f, 0.04f), floatArrayOf(0.12f, 0.46f)), 0.025f); pop() }
        col = c(60, 66, 78); at(z = 0.02f) { cylinder(0.08f, 0.1f, 14, 0.12f) }
    }.build()

    // ---------------------------------------------------------------- fast food
    private fun ketchup() = PM().apply {
        // squeeze bottle: rounded body, white label, flip cap with a nozzle and a drip of sauce
        val red = c(205, 22, 24)
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.26f, 0f), floatArrayOf(0.3f, 0.04f), floatArrayOf(0.31f, 0.3f), floatArrayOf(0.3f, 0.52f),
            floatArrayOf(0.24f, 0.64f), floatArrayOf(0.14f, 0.7f), floatArrayOf(0.13f, 0.72f)), 26) { _, v -> if (v in 0.22f..0.6f) c(248, 244, 232) else red }
        col = c(40, 140, 50); for (k in 0 until 3) at(0f, 0f, 0.3f + k * 0.09f) { push(); translate(0f, -0.31f, 0f); box(0.22f - k * 0.05f, 0.012f, 0.03f); pop() }
        col = red * 0.8f; at(z = 0.42f) { push(); translate(0f, -0.312f, 0f); scale(1f, 0.1f, 1f); sphere(0.06f, 10, 6); pop() }      // tomato
        col = c(250, 250, 250); at(z = 0.72f) { cylinder(0.14f, 0.1f, 20, 0.12f) }
        at(z = 0.82f) { cylinder(0.05f, 0.14f, 12, 0.012f) }
        col = red; at(0.0f, 0.0f, 0.97f) { sphere(0.022f, 8, 6) }
        tube(listOf(Vec3(0.12f, 0f, 0.74f), Vec3(0.2f, 0f, 0.7f), Vec3(0.27f, 0f, 0.62f), Vec3(0.31f, 0f, 0.5f)), 0.025f, 8, taper = { 1f - 0.5f * it })
    }.build()

    private fun burger() = PM().apply {
        col = c(224, 160, 80)
        at(z = 0.0f) { lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.46f, 0f), floatArrayOf(0.5f, 0.06f), floatArrayOf(0.42f, 0.14f), floatArrayOf(0f, 0.14f)), 28) }
        col = c(250, 190, 40); at(z = 0.15f) { push(); rotZ(0.4f); box(0.9f, 0.9f, 0.025f); pop() }               // cheese
        col = c(96, 52, 30); at(z = 0.16f) { cylinder(0.46f, 0.12f, 28, 0.44f) }                             // patty
        col = c(70, 170, 50); at(z = 0.27f) { torus(0.42f, 0.04f, 22, 6) }                                    // lettuce
        col = c(220, 50, 40); at(z = 0.31f) { cylinder(0.4f, 0.04f, 20) }                                     // tomato
        col = c(228, 164, 84)                                                                                   // top bun
        at(z = 0.34f) { lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.5f, 0f), floatArrayOf(0.52f, 0.12f), floatArrayOf(0.42f, 0.34f), floatArrayOf(0.2f, 0.45f), floatArrayOf(0f, 0.47f)), 28) }
        col = c(255, 244, 220)
        for (i in 0 until 14) { val a = i * 2.4f; val r = 0.1f + 0.3f * ((i * 5) % 7) / 7f
            push(); translate(cos(a) * r, sin(a) * r, 0.34f + 0.43f - r * 0.62f); rotZ(a); scale(0.07f, 0.035f, 0.02f); sphere(0.5f, 6, 4); pop() }
    }.build()

    private fun sodaCup() = PM().apply {
        col = c(250, 250, 250)
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.22f, 0f), floatArrayOf(0.3f, 0.8f), floatArrayOf(0.32f, 0.8f)), 24, false) { _, v -> if (v in 0.3f..0.65f) c(220, 30, 40) else c(250, 250, 250) }
        col = c(60, 60, 64); at(z = 0.8f) { cylinder(0.34f, 0.05f, 24, 0.33f) }
        col = c(250, 250, 250); at(z = 0.85f) { lathe(listOf(floatArrayOf(0.33f, 0f), floatArrayOf(0.2f, 0.07f), floatArrayOf(0f, 0.09f)), 24) }
        col = c(230, 40, 50); tube(listOf(Vec3(0.03f, 0f, 0.8f), Vec3(0.06f, 0f, 1.0f), Vec3(0.14f, 0f, 1.12f)), 0.022f, 8)
    }.build()

    private fun friesBox() = PM().apply {
        // red carton with a curved front, a golden arch and a fan of fries
        val red = c(212, 30, 30)
        col = red
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.2f, 0f), floatArrayOf(0.3f, 0.5f), floatArrayOf(0.31f, 0.52f)), 4, false)
        col = c(255, 200, 40); at(0f, -0.23f, 0.25f) { rotX((PI / 2).toFloat()); torus(0.08f, 0.018f, 16, 5) }
        for (i in 0 until 16) {
            val a = i * 2.4f; val r = 0.04f + 0.17f * ((i * 7) % 9) / 9f
            col = if (i % 3 == 0) c(255, 210, 90) else c(244, 190, 70)
            push(); translate(cos(a) * r, sin(a) * r * 0.7f, 0.42f); rotX(sin(a) * 0.25f); rotY(cos(a) * 0.25f)
            translate(0f, 0f, 0.14f + 0.06f * (i % 4)); box(0.045f, 0.045f, 0.36f + 0.06f * (i % 3)); pop()
        }
    }.build()

    // ---------------------------------------------------------------- science
    private fun flask() = PM().apply {
        // conical lab flask with glowing liquid, a cork, a little clamp stand and vapour
        col = c(80, 255, 170) * 1.3f; lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.3f, 0.01f), floatArrayOf(0.22f, 0.22f), floatArrayOf(0f, 0.24f)), 22)
        col = c(214, 236, 248); lathe(listOf(floatArrayOf(0.22f, 0.22f), floatArrayOf(0.08f, 0.56f), floatArrayOf(0.07f, 0.72f), floatArrayOf(0.085f, 0.74f)), 22, false)
        at(z = 0f) { torus(0.29f, 0.015f, 22, 4) }
        col = c(170, 120, 70); at(z = 0.7f) { cylinder(0.065f, 0.1f, 12, 0.075f) }
        col = c(60, 64, 76); push(); translate(-0.36f, 0f, 0f); cylinder(0.12f, 0.03f, 12); cylinder(0.015f, 0.75f, 6); pop()
        tube(listOf(Vec3(-0.36f, 0f, 0.52f), Vec3(-0.08f, 0f, 0.52f)), 0.012f, 5)
        col = c(200, 255, 230) * 1.2f; for (k in 0 until 3) at(0.02f * k, 0f, 0.86f + k * 0.07f) { sphere(0.035f + 0.012f * k, 8, 6) }
    }.build()

    private fun beaker() = PM().apply {
        // glass beaker with a pouring lip, graduation marks and a glowing liquid
        val glass = c(200, 228, 240)
        col = c(90, 255, 160) * 1.25f; at(z = 0.02f) { cylinder(0.27f, 0.42f, 24) }
        col = c(150, 255, 200) * 1.4f; at(z = 0.44f) { cylinder(0.27f, 0.015f, 24) }
        col = glass
        lathe(listOf(floatArrayOf(0.3f, 0.46f), floatArrayOf(0.3f, 0.78f), floatArrayOf(0.33f, 0.8f)), 24, false)     // glass shows above the liquid only
        at(z = 0.0f) { torus(0.29f, 0.015f, 24, 5) }
        at(0f, -0.3f, 0.78f) { rotX(-0.6f); cone(0.06f, 0.08f, 8) }
        col = c(250, 250, 255)
        for (i in 1..5) at(0.3f, 0f, 0.12f * i) { box(0.012f, if (i % 2 == 0) 0.14f else 0.08f, 0.012f) }
    }.build()

    private fun testTubes() = PM().apply {
        // a wooden rack holding four tubes of coloured, glowing liquids
        col = c(170, 120, 70); push(); translate(0f, 0f, 0.03f); box(0.7f, 0.2f, 0.06f); pop()
        push(); translate(0f, 0f, 0.4f); box(0.7f, 0.2f, 0.04f); pop()
        for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.33f, 0f, 0.22f); box(0.04f, 0.2f, 0.4f); pop() }
        val liq = listOf(c(255, 80, 120), c(80, 220, 255), c(140, 255, 120), c(255, 200, 60))
        for (i in 0 until 4) {
            val x = -0.22f + i * 0.145f
            col = c(214, 236, 248); push(); translate(x, 0f, 0.07f); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.045f, 0.03f), floatArrayOf(0.045f, 0.62f), floatArrayOf(0.055f, 0.64f)), 12, false); pop()
            col = liq[i] * 1.3f; push(); translate(x, 0f, 0.075f); cylinder(0.04f, 0.2f + 0.08f * (i % 3), 12); pop()
        }
    }.build()

    private fun atom() = PM().apply {
        col = c(255, 100, 90); at(z = 0.5f) { sphere(0.1f, 12, 8) }
        for (k in 0 until 3) { push(); translate(0f, 0f, 0.5f); rotZ(k * 1.047f); rotX(1.1f); col = c(150, 220, 255) * 1.3f; torus(0.38f, 0.012f, 36, 6)
            col = c(255, 240, 120) * 1.6f; translate(0.38f, 0f, 0f); sphere(0.05f, 8, 6); pop() }
        col = c(120, 128, 140); cylinder(0.04f, 0.4f, 8); cylinder(0.18f, 0.05f, 14)
    }.build()

    // ---------------------------------------------------------------- ice
    private fun iceCrystals() = PM().apply {
        // a cluster of faceted ice shards, glowing faintly from inside
        col = c(180, 214, 240); at(z = 0.01f) { scale(1f, 1f, 0.25f); sphere(0.3f, 10, 6) }
        for (i in 0 until 7) {
            val a = i * 0.9f; val r = if (i == 0) 0f else 0.14f + 0.04f * (i % 2); val h = if (i == 0) 0.95f else 0.4f + 0.35f * ((i * 3) % 5) / 4f
            push(); translate(cos(a) * r, sin(a) * r, 0.02f); rotZ(a); rotX(if (i == 0) 0f else 0.35f * (if (i % 2 == 0) 1f else -0.7f))
            col = c(210, 240, 255) * (if (i % 2 == 0) 1.12f else 1.3f); cylinder(0.07f + 0.03f * (i == 0).compareTo(false), h * 0.72f, 6, 0.06f)
            col = c(235, 250, 255) * 1.4f; at(z = h * 0.72f) { cone(0.065f + 0.03f * (i == 0).compareTo(false), h * 0.3f, 6) }
            pop()
        }
    }.build()

    private fun snowman() = PM().apply {
        // three packed snowballs, coal buttons and eyes, carrot nose, stick arms, knitted scarf and a top hat
        val snow = c(246, 249, 255)
        col = snow; at(z = 0.2f) { scale(1f, 1f, 0.9f); sphere(0.24f, 20, 14) }; at(z = 0.5f) { sphere(0.17f, 18, 12) }; at(z = 0.74f) { sphere(0.12f, 16, 12) }
        col = c(20, 20, 24)
        for (k in 0 until 3) at(0.165f, 0f, 0.42f + k * 0.07f) { sphere(0.02f, 6, 4) }
        for (s in intArrayOf(-1, 1)) at(0.105f, s * 0.04f, 0.78f) { sphere(0.016f, 6, 4) }
        for (k in 0 until 5) { val a = -0.5f + k * 0.25f; at(0.11f * cos(a), 0.11f * sin(a), 0.7f - 0.012f * (2 - kotlin.math.abs(k - 2))) { sphere(0.01f, 5, 4) } }
        col = c(255, 130, 30); at(0.115f, 0f, 0.75f) { rotY((PI / 2).toFloat()); cone(0.025f, 0.13f, 8) }
        col = c(110, 70, 40)
        for (s in intArrayOf(-1, 1)) { tube(listOf(Vec3(0f, s * 0.15f, 0.55f), Vec3(0.02f, s * 0.28f, 0.66f), Vec3(0.0f, s * 0.36f, 0.74f)), 0.012f, 5)
            tube(listOf(Vec3(0.0f, s * 0.3f, 0.69f), Vec3(0.03f, s * 0.34f, 0.62f)), 0.008f, 4) }
        col = c(210, 40, 40); at(z = 0.64f) { torus(0.12f, 0.035f, 18, 6) }
        tube(listOf(Vec3(0.06f, 0.08f, 0.63f), Vec3(0.1f, 0.12f, 0.5f), Vec3(0.11f, 0.13f, 0.42f)), 0.03f, 6)
        col = c(250, 250, 250); for (k in 0 until 3) at(0.12f, 0.135f, 0.44f + k * 0.03f) { box(0.01f, 0.04f, 0.006f) }
        col = c(30, 30, 36); at(z = 0.84f) { cylinder(0.14f, 0.015f, 18) }; at(z = 0.855f) { cylinder(0.09f, 0.16f, 16) }
        col = c(200, 40, 40); at(z = 0.87f) { cylinder(0.092f, 0.025f, 16) }
    }.build()

    private fun lampPost() = PM().apply {
        // Victorian street lamp: fluted post, scroll arms, glowing glass lantern with a snowy cap
        val iron = c(26, 34, 46)
        col = iron; cylinder(0.14f, 0.06f, 12, 0.11f); at(z = 0.06f) { cylinder(0.08f, 0.1f, 10, 0.05f) }
        at(z = 0.16f) { cylinder(0.045f, 0.62f, 10, 0.035f) }
        at(z = 0.4f) { torus(0.045f, 0.012f, 10, 4) }
        for (s in intArrayOf(-1, 1)) tube(listOf(Vec3(0f, 0f, 0.7f), Vec3(s * 0.09f, 0f, 0.74f), Vec3(s * 0.07f, 0f, 0.8f), Vec3(s * 0.02f, 0f, 0.78f)), 0.01f, 5)
        at(z = 0.8f) { cylinder(0.1f, 0.04f, 8, 0.12f) }
        col = c(255, 214, 140) * 2.6f; at(z = 0.84f) { cylinder(0.09f, 0.18f, 8, 0.11f) }
        col = iron; for (k in 0 until 4) { val a = k * 1.571f + 0.39f; tube(listOf(Vec3(cos(a) * 0.1f, sin(a) * 0.1f, 0.84f), Vec3(cos(a) * 0.12f, sin(a) * 0.12f, 1.02f)), 0.008f, 4) }
        at(z = 1.02f) { cone(0.16f, 0.1f, 8) }
        col = c(240, 248, 255); at(z = 1.06f) { push(); scale(1f, 1f, 0.45f); sphere(0.13f, 12, 6); pop() }
    }.build()

    private fun snowPine() = PM().apply {
        // layered fir with drooping boughs, snow lying on each tier, a trunk flare and a star of frost at the top
        col = c(96, 64, 40); cylinder(0.05f, 0.25f, 10, 0.04f)
        for (i in 0 until 5) {
            val z = 0.15f + i * 0.15f; val r = 0.4f - i * 0.07f
            col = Vec3.lerp(c(30, 90, 56), c(46, 120, 70), i / 4f)
            at(z = z) { lathe(listOf(floatArrayOf(0f, 0.22f), floatArrayOf(r * 0.4f, 0.12f), floatArrayOf(r, -0.02f), floatArrayOf(r * 0.94f, -0.05f), floatArrayOf(r * 0.3f, 0.0f)), 14) { u, _ -> if ((u * 14f).toInt() % 2 == 0) c(30, 96, 60) else c(40, 110, 66) } }
            col = c(244, 248, 255); at(z = z + 0.04f) { lathe(listOf(floatArrayOf(0f, 0.17f), floatArrayOf(r * 0.38f, 0.095f), floatArrayOf(r * 0.85f, 0.01f), floatArrayOf(r * 0.7f, 0f)), 14) }
        }
        col = c(230, 245, 255) * 1.4f; at(z = 0.98f) { sphere(0.035f, 8, 6) }
    }.build()

    // ---------------------------------------------------------------- egypt
    private fun obelisk() = PM().apply {
        // a tapering granite needle on a stepped base, carved with glyph rows and capped in gold
        col = c(190, 152, 96); push(); translate(0f, 0f, 0.03f); box(0.44f, 0.44f, 0.06f); pop(); push(); translate(0f, 0f, 0.08f); box(0.34f, 0.34f, 0.04f); pop()
        push(); translate(0f, 0f, 0.1f); rotZ((PI / 4).toFloat())
        lathe(listOf(floatArrayOf(0.19f, 0f), floatArrayOf(0.12f, 0.78f)), 4, false) { _, v -> if (((v * 24f).toInt() % 3) == 0) c(176, 138, 84) else c(214, 176, 112) }
        col = c(255, 206, 70) * 1.25f; at(z = 0.78f) { cone(0.12f, 0.16f, 4) }
        pop()
    }.build()

    private fun canopicJar() = PM().apply {
        // painted jar with a jackal-head lid, hieroglyph bands and gold rims
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.2f, 0f), floatArrayOf(0.27f, 0.2f), floatArrayOf(0.26f, 0.45f), floatArrayOf(0.18f, 0.58f), floatArrayOf(0.16f, 0.6f)), 24) { u, v ->
            when { v in 0.3f..0.36f -> c(40, 100, 180); v in 0.38f..0.44f -> if ((u * 16f).toInt() % 2 == 0) c(200, 50, 40) else c(240, 220, 170); else -> c(232, 214, 176) } }
        col = c(255, 206, 70); at(z = 0.0f) { torus(0.2f, 0.015f, 20, 4) }; at(z = 0.6f) { torus(0.165f, 0.015f, 20, 4) }
        col = c(30, 30, 36)
        at(z = 0.6f) { cylinder(0.15f, 0.06f, 18, 0.13f) }
        at(z = 0.72f) { scale(1f, 0.85f, 1f); sphere(0.12f, 14, 10) }
        at(0.13f, 0f, 0.7f) { rotY(1.4f); cone(0.06f, 0.14f, 10) }
        for (s in intArrayOf(-1, 1)) at(-0.02f, s * 0.06f, 0.82f) { cone(0.035f, 0.14f, 8) }
        col = c(255, 206, 70) * 1.2f; for (s in intArrayOf(-1, 1)) at(0.09f, s * 0.045f, 0.76f) { sphere(0.012f, 6, 4) }
    }.build()

    private fun sphinx() = PM().apply {
        // a recumbent lion with a human head in a striped nemes headdress
        val stone = c(214, 176, 116); val shade = c(186, 148, 96)
        col = stone
        at(0f, 0.12f, 0.2f) { scale(0.32f, 0.62f, 0.2f); sphere(1f, 20, 12) }                     // body
        at(0f, 0.5f, 0.24f) { scale(0.3f, 0.22f, 0.22f); sphere(1f, 16, 10) }                      // haunches
        for (s in intArrayOf(-1, 1)) {
            at(s * 0.16f, -0.32f, 0.07f) { scale(0.09f, 0.28f, 0.07f); sphere(1f, 12, 8) }        // fore legs
            col = shade; at(s * 0.16f, -0.58f, 0.06f) { sphere(0.07f, 10, 8) }; col = stone      // paws
        }
        at(0f, -0.2f, 0.42f) { scale(0.2f, 0.18f, 0.2f); sphere(1f, 14, 10) }                       // chest
        tube(listOf(Vec3(0f, 0.7f, 0.12f), Vec3(0.18f, 0.66f, 0.04f), Vec3(0.26f, 0.38f, 0.03f)), 0.03f, 6)   // tail
        // head and headdress
        at(0f, -0.26f, 0.64f) { scale(0.13f, 0.14f, 0.16f); sphere(1f, 16, 12) }
        col = c(40, 110, 190)
        for (i in 0 until 6) {
            col = if (i % 2 == 0) c(40, 110, 190) else c(236, 196, 90)
            for (s in intArrayOf(-1, 1)) at(s * (0.15f + i * 0.012f), -0.22f, 0.7f - i * 0.06f) { box(0.05f, 0.14f, 0.06f) }
        }
        col = c(236, 196, 90); at(0f, -0.25f, 0.78f) { scale(1.15f, 1f, 0.45f); sphere(0.15f, 14, 8) }
        col = c(255, 206, 70) * 1.2f; at(0f, -0.38f, 0.8f) { sphere(0.03f, 8, 6) }               // uraeus
        col = shade; for (s in intArrayOf(-1, 1)) at(s * 0.05f, -0.385f, 0.66f) { box(0.04f, 0.01f, 0.012f) }
        at(0f, -0.4f, 0.62f) { box(0.025f, 0.03f, 0.05f) }
        col = c(120, 90, 60); at(0f, -0.36f, 0.5f) { box(0.03f, 0.03f, 0.1f) }                    // beard
    }.build()

    private fun pyramidProp() = PM().apply {
        col = c(224, 188, 122); push(); rotZ((PI / 4).toFloat()); cone(0.52f, 0.85f, 4); pop()
        col = c(255, 206, 70); at(z = 0.82f) { push(); rotZ((PI / 4).toFloat()); cone(0.05f, 0.1f, 4); pop() }
        col = c(210, 170, 110)
        for (i in 0 until 5) { val r = 0.5f * (1f - i * 0.18f); at(z = i * 0.16f) { push(); rotZ((PI / 4).toFloat()); cylinder(r * 1.03f, 0.012f, 4); pop() } }
    }.build()

    // ---------------------------------------------------------------- neon
    private fun neonStar() = PM().apply {
        val pts = ArrayList<Vec3>()
        for (i in 0..10) { val r = if (i % 2 == 0) 0.4f else 0.17f; val a = (PI.toFloat() * i / 5f) + PI.toFloat() / 2f; pts.add(Vec3(cos(a) * r, 0f, 0.5f + sin(a) * r)) }
        pts.add(pts[0])
        col = c(255, 230, 80) * 2.4f; tube(pts, 0.025f, 8)
        col = c(40, 40, 60); cylinder(0.02f, 0.12f, 8)
    }.build()

    private fun arcadeStick() = PM().apply {
        col = c(30, 20, 60); box(0.8f, 0.5f, 0.4f).also { }
        push(); translate(0f, 0f, 0.2f); col = c(30, 20, 60); box(0.8f, 0.5f, 0.4f); pop()
        col = c(255, 60, 200) * 1.6f; push(); translate(0f, -0.26f, 0.24f); box(0.78f, 0.02f, 0.03f); pop()
        col = c(200, 200, 220); at(-0.15f, 0f, 0.4f) { cylinder(0.015f, 0.2f, 8) }
        col = c(255, 60, 60); at(-0.15f, 0f, 0.6f) { sphere(0.07f, 12, 8) }
        for (i in 0 until 3) { col = listOf(c(60, 255, 222), c(255, 230, 60), c(255, 60, 200))[i] * 1.5f; at(0.1f + i * 0.12f, 0f, 0.4f) { cylinder(0.04f, 0.03f, 12) } }
    }.build()

    private fun neonRing() = PM().apply {
        push(); translate(0f, 0f, 0.5f); rotX((PI / 2).toFloat()); col = c(60, 255, 222) * 2.4f; torus(0.32f, 0.025f, 36, 8)
        col = c(255, 60, 200) * 2.4f; torus(0.2f, 0.02f, 32, 8); pop()
        col = c(40, 40, 60); cylinder(0.12f, 0.06f, 14)
    }.build()

    private fun neonPalm() = PM().apply {
        col = c(255, 140, 40) * 2.0f; tube(listOf(Vec3(0f, 0f, 0f), Vec3(0.05f, 0f, 0.4f), Vec3(0.02f, 0f, 0.75f)), 0.03f, 8)
        for (i in 0 until 6) { val a = i * 1.047f; val p = ArrayList<Vec3>(); for (k in 0..6) { val t = k / 6f; p.add(Vec3(0.02f + cos(a) * 0.38f * t, sin(a) * 0.38f * t, 0.75f + 0.18f * sin(t * 2.2f) - 0.2f * t * t)) }
            col = c(60, 255, 150) * 2.2f; tube(p, 0.02f, 6, false, null) { 1f - it * 0.5f } }
    }.build()

    // ---------------------------------------------------------------- neon city (cyberpunk)
    private val PINK = c(255, 28, 170)
    private val NCYAN = c(0, 238, 255)
    private val NYEL = c(252, 238, 10)

    private fun neonSign() = PM().apply {
        col = c(20, 22, 30); cylinder(0.035f, 0.5f, 8)
        col = c(14, 14, 22); push(); translate(0f, 0f, 0.78f); box(0.46f, 0.05f, 0.62f); pop()
        col = PINK * 3.2f; push(); translate(0f, -0.03f, 0.95f); box(0.4f, 0.02f, 0.05f); pop()
        col = NCYAN * 3.2f; push(); translate(0f, -0.03f, 0.8f); box(0.3f, 0.02f, 0.05f); pop()
        col = NYEL * 3.0f; push(); translate(0f, -0.03f, 0.65f); box(0.36f, 0.02f, 0.05f); pop()
        col = NCYAN * 3.2f; push(); translate(-0.19f, -0.03f, 0.8f); box(0.04f, 0.02f, 0.5f); pop()
        col = PINK * 3.2f; push(); translate(0.19f, -0.03f, 0.78f); box(0.04f, 0.02f, 0.4f); pop()
    }.build()

    private fun terminal() = PM().apply {
        col = c(24, 26, 38); push(); translate(0f, 0f, 0.42f); box(0.5f, 0.34f, 0.84f); pop()
        col = NCYAN * 2.4f; push(); translate(0f, -0.175f, 0.62f); box(0.36f, 0.012f, 0.22f); pop()
        col = PINK * 2.8f; push(); translate(-0.16f, -0.175f, 0.36f); box(0.05f, 0.012f, 0.18f); pop()
        col = NYEL * 2.8f; push(); translate(0f, -0.175f, 0.36f); box(0.18f, 0.012f, 0.04f); pop()
        col = c(0, 255, 160) * 2.6f; push(); translate(0.16f, -0.175f, 0.36f); box(0.05f, 0.012f, 0.18f); pop()
        col = NCYAN * 3f; push(); translate(0f, -0.175f, 0.82f); box(0.5f, 0.02f, 0.02f); pop()
        col = PINK * 3f; push(); translate(0f, -0.175f, 0.04f); box(0.5f, 0.02f, 0.02f); pop()
    }.build()

    private fun hologram() = PM().apply {
        col = c(22, 24, 36); cylinder(0.3f, 0.07f, 20, 0.26f); at(z = 0.07f) { cylinder(0.12f, 0.05f, 16) }
        val apex = Vec3(0f, 0f, 0.8f); val b = listOf(Vec3(0.2f, 0.2f, 0.2f), Vec3(-0.2f, 0.2f, 0.2f), Vec3(-0.2f, -0.2f, 0.2f), Vec3(0.2f, -0.2f, 0.2f))
        col = NCYAN * 3.2f
        for (i in 0 until 4) { tube(listOf(b[i], apex), 0.012f, 5); tube(listOf(b[i], b[(i + 1) % 4]), 0.012f, 5) }
        col = PINK * 2.6f; at(z = 0.46f) { torus(0.1f, 0.012f, 20, 5) }
        col = NYEL * 3f; at(z = 0.45f) { sphere(0.035f, 8, 6) }
    }.build()

    private fun cityTower() = PM().apply {
        col = c(16, 16, 26)
        val tiers = listOf(floatArrayOf(0.42f, 0.9f), floatArrayOf(0.32f, 0.7f), floatArrayOf(0.22f, 0.5f))
        var z = 0f
        for ((ti, t) in tiers.withIndex()) {
            val w = t[0]; val h = t[1]
            col = c(16, 16, 26); push(); translate(0f, 0f, z + h * 0.5f * 0.5f); box(w, w, h * 0.5f); pop()
            val rows = 6; val cols = 4
            for (r in 0 until rows) for (k in 0 until cols) for (face in 0 until 2) {
                val lit = ((r * 7 + k * 3 + ti * 5 + face) % 5) != 0
                if (!lit) continue
                col = (when ((r + k + ti) % 3) { 0 -> NYEL; 1 -> NCYAN; else -> PINK }) * 2.4f
                val wx = -w / 2 + (k + 0.5f) * w / cols; val wz = z + (r + 0.5f) * h * 0.5f / rows
                push(); translate(wx, if (face == 0) -w / 2 - 0.004f else w / 2 + 0.004f, wz); box(w / cols * 0.5f, 0.012f, h * 0.5f / rows * 0.5f); pop()
            }
            col = (if (ti % 2 == 0) NCYAN else PINK) * 3f; push(); translate(0f, 0f, z + h * 0.5f); box(w + 0.02f, w + 0.02f, 0.02f); pop()
            z += h * 0.5f
        }
        col = c(60, 64, 80); cylinder(0.012f, 0.35f, 6)
        push(); translate(0f, 0f, z); cylinder(0.012f, 0.35f, 6); pop()
        col = c(255, 40, 60) * 3.4f; at(z = z + 0.36f) { sphere(0.025f, 8, 6) }
    }.build()

    // ---------------------------------------------------------------- jungle
    private fun fern() = PM().apply {
        for (i in 0 until 7) { val a = i * 0.9f; val p = ArrayList<Vec3>(); for (k in 0..8) { val t = k / 8f; p.add(Vec3(cos(a) * 0.4f * t, sin(a) * 0.4f * t, 0.1f + 0.5f * sin(t * 1.6f) - 0.12f * t)) }
            col = Vec3.lerp(c(52, 140, 50), c(150, 220, 80), (i % 3) / 2f); tube(p, 0.016f, 5)
            for (k in 1..7) { val q = p[k]; val d = (p[minOf(k + 1, 8)] - p[k - 1]); val sd = Vec3(-d.y, d.x, 0f).normalized() * (0.07f * (1f - k / 9f))
                flat(q, q + sd + d * 0.05f, q + d * 0.18f, q - sd + d * 0.05f)
                flat(q, q - sd + d * 0.05f, q + d * 0.18f, q + sd + d * 0.05f) } }
    }.build()

    private fun idol() = PM().apply {
        // an Olmec-style colossal head on a stepped, carved plinth: weathered stone with patchy tones; a helmet with a band of
        // carved glyphs, side flaps and a ridged crest; a heavy brow over deep-set glowing eyes, high cheekbones, a broad nose
        // with flared nostrils and a philtrum, full downturned lips and a strong chin; ear spools; cracks, moss and leafy vines
        fun weather(seed: Int, base: Vec3): (Float, Float) -> Vec3 = { u, v ->
            val k1 = (((u * 14f).toInt() * 73 + (v * 9f).toInt() * 151 + seed) * 2654435761L ushr 9) % 1000 / 1000f
            val k2 = (((u * 41f).toInt() * 31 + (v * 27f).toInt() * 97 + seed) * 2654435761L ushr 9) % 1000 / 1000f
            base * (0.82f + 0.18f * k1 + 0.1f * k2) * (if (v < 0.25f) 0.86f else 1f) }
        val stone = c(132, 128, 112); val shade = c(96, 94, 82); val moss = c(74, 128, 52); val mossD = c(46, 96, 40)
        // stepped plinth with carved glyph panels
        for ((k, w) in listOf(0.82f, 0.72f, 0.64f).withIndex()) { col = if (k % 2 == 0) shade else stone; at(z = 0.03f + k * 0.045f) { box(w, w * 0.88f, 0.045f) } }
        col = shade * 0.8f; for (g in 0 until 5) { val y = -0.2f + g * 0.1f
            at(y, -0.283f, 0.075f) { box(0.06f, 0.008f, 0.03f) }; at(y + 0.02f, -0.284f, 0.075f) { box(0.012f, 0.009f, 0.03f) } }
        // head and jaw
        at(0f, 0f, 0.52f) { scale(0.86f, 0.72f, 1.0f); sphere(0.36f, 32, 24, weather(1, stone)) }
        at(0f, -0.06f, 0.27f) { scale(0.95f, 0.75f, 0.6f); sphere(0.3f, 26, 16, weather(2, stone)) }
        at(0f, -0.2f, 0.17f) { scale(1.3f, 0.7f, 0.7f); sphere(0.08f, 14, 10, weather(3, stone)) }                                   // chin
        // helmet: band with carved glyph blocks, side flaps over the ears, ridged crest
        col = shade; at(0f, 0f, 0.68f) { scale(0.9f, 0.76f, 0.5f); torus(0.32f, 0.05f, 36, 10) }
        for (k in 0 until 11) { val a = -1.35f + k * 0.27f
            col = if (k % 2 == 0) stone * 1.05f else shade * 1.1f
            at(sin(a) * 0.3f, -cos(a) * 0.268f, 0.7f) { rotZ(-a); box(0.05f, 0.03f, 0.05f) }
            col = shade * 0.7f; at(sin(a) * 0.302f, -cos(a) * 0.272f, 0.7f) { rotZ(-a); box(0.018f, 0.031f, 0.025f) } }               // carved glyph
        for (s in intArrayOf(-1, 1)) { col = shade; at(s * 0.31f, 0.0f, 0.52f) { scale(0.35f, 0.9f, 1.3f); sphere(0.12f, 14, 10, weather(20 + s, shade)) } }   // side flaps
        for (k in 0 until 4) { col = if (k % 2 == 0) shade else stone; at(0f, 0.1f + k * 0.04f, 0.83f - k * 0.03f) { scale(0.45f - k * 0.05f, 0.5f, 0.32f); sphere(0.2f, 16, 8) } }   // crest
        // face
        col = stone; at(0f, -0.235f, 0.585f) { scale(2.4f, 0.6f, 0.5f); sphere(0.07f, 18, 10, weather(4, stone)) }                     // heavy brow
        for (s in intArrayOf(-1, 1)) {
            col = c(40, 38, 32); at(s * 0.12f, -0.24f, 0.505f) { scale(1.3f, 0.5f, 0.7f); sphere(0.065f, 14, 10) }                     // socket
            col = stone; at(s * 0.12f, -0.258f, 0.548f) { scale(1.5f, 0.6f, 0.45f); sphere(0.06f, 14, 10, weather(5 + s, stone)) }     // upper lid
            at(s * 0.12f, -0.255f, 0.462f) { scale(1.4f, 0.55f, 0.35f); sphere(0.055f, 12, 8, weather(7 + s, stone)) }                // lower lid
            col = c(255, 190, 70) * 1.8f; at(s * 0.12f, -0.272f, 0.505f) { scale(1f, 0.5f, 0.8f); sphere(0.025f, 10, 8) }
            col = c(255, 240, 180) * 2.2f; at(s * 0.12f, -0.283f, 0.505f) { sphere(0.008f, 6, 4) }
            col = stone; at(s * 0.2f, -0.2f, 0.4f) { scale(0.8f, 0.6f, 0.6f); sphere(0.09f, 14, 10, weather(9 + s, stone)) }           // cheekbones
            at(s * 0.31f, -0.02f, 0.45f) { scale(0.4f, 1f, 1.3f); sphere(0.07f, 12, 8) }                                                 // ears
            col = shade; at(s * 0.345f, -0.02f, 0.42f) { rotY((PI / 2).toFloat()); torus(0.035f, 0.014f, 14, 6) }                      // ear spools
            col = c(40, 38, 32); at(s * 0.36f, -0.02f, 0.42f) { sphere(0.016f, 6, 4) }
        }
        col = stone; at(0f, -0.26f, 0.5f) { scale(0.55f, 0.6f, 1.6f); sphere(0.045f, 12, 8, weather(11, stone)) }                      // nose bridge
        at(0f, -0.29f, 0.41f) { scale(1.45f, 0.8f, 1f); sphere(0.07f, 16, 10, weather(12, stone)) }                                     // nose
        for (s in intArrayOf(-1, 1)) at(s * 0.06f, -0.28f, 0.4f) { sphere(0.035f, 10, 8, weather(13 + s, stone)) }                       // nostril wings
        col = c(40, 38, 32); for (s in intArrayOf(-1, 1)) at(s * 0.045f, -0.33f, 0.385f) { scale(1.2f, 1f, 0.8f); sphere(0.016f, 8, 6) }   // nostrils
        col = shade; at(0f, -0.315f, 0.345f) { box(0.03f, 0.01f, 0.03f) }                                                                // philtrum
        col = stone; at(0f, -0.295f, 0.3f) { scale(1.75f, 0.7f, 0.48f); sphere(0.07f, 16, 10, weather(15, stone)) }                     // upper lip
        at(0f, -0.285f, 0.248f) { scale(1.55f, 0.7f, 0.48f); sphere(0.065f, 16, 10, weather(16, stone)) }                                 // lower lip
        col = c(40, 38, 32); tube((0..6).map { k -> val v = k / 6f - 0.5f; Vec3(v * 0.2f, -0.33f, 0.272f - 0.02f * v * v * 4f) }, 0.006f, 5)   // mouth, corners turned down
        // weathering: cracks, moss on the crown and down one side, vines with leaves
        col = c(60, 58, 50); tube(listOf(Vec3(0.14f, -0.2f, 0.8f), Vec3(0.2f, -0.24f, 0.64f), Vec3(0.24f, -0.22f, 0.5f), Vec3(0.27f, -0.16f, 0.36f)), 0.006f, 3)
        tube(listOf(Vec3(-0.18f, -0.24f, 0.34f), Vec3(-0.15f, -0.27f, 0.24f)), 0.005f, 3)
        for (k in 0 until 20) { val a = k * 0.45f; col = if (k % 3 == 0) mossD else moss
            at(cos(a) * 0.18f * (0.4f + 0.6f * (k % 4) / 3f), sin(a) * 0.15f + 0.04f, 0.83f + 0.02f * (k % 3)) { scale(1f, 1f, 0.35f); sphere(0.07f, 10, 6) } }
        for (k in 0 until 6) { val x = -0.27f + k * 0.03f
            col = moss; val vine = listOf(Vec3(x, -0.05f, 0.8f), Vec3(x - 0.04f, -0.15f, 0.62f), Vec3(x - 0.05f, -0.13f, 0.42f), Vec3(x - 0.03f, -0.12f, 0.25f - 0.03f * k))
            tube(vine, 0.007f, 4)
            col = c(80, 160, 60); for (j in 1 until 4) { val p = vine[j]; push(); translate(p.x - 0.01f, p.y - 0.01f, p.z); rotZ(k * 1.3f + j); rotY(0.6f); scale(1f, 0.5f, 0.12f); sphere(0.03f, 8, 4); pop() } }
        col = moss; for (k in 0 until 8) { val a = k * 0.8f; at(cos(a) * 0.32f, sin(a) * 0.28f, 0.13f) { scale(1f, 1f, 0.4f); sphere(0.05f, 8, 6) } }   // moss round the plinth
    }.build()

    private fun jungleStump() = PM().apply {
        col = c(104, 76, 48); cylinder(0.3f, 0.45f, 16, 0.24f)
        col = c(70, 130, 50); at(z = 0.44f) { push(); scale(1f, 1f, 0.3f); sphere(0.26f, 14, 6); pop() }
        col = c(250, 70, 140); for (i in 0 until 5) { val a = i * 1.256f; at(cos(a) * 0.14f, sin(a) * 0.14f, 0.5f) { sphere(0.035f, 6, 4) } }
        col = c(60, 150, 50); for (i in 0 until 4) { push(); translate(cos(i * 1.6f) * 0.22f, sin(i * 1.6f) * 0.22f, 0.4f); rotZ(i * 1.6f); rotY(0.7f); scale(0.3f, 0.12f, 0.02f); sphere(0.5f, 8, 4); pop() }
    }.build()

    private fun jungleTree() = PM().apply {
        col = c(94, 70, 44); tube(listOf(Vec3(0f, 0f, 0f), Vec3(0.03f, 0f, 0.3f), Vec3(-0.02f, 0f, 0.6f), Vec3(0f, 0f, 0.85f)), 0.07f, 10, false, null) { 1f - 0.4f * it }
        for (i in 0 until 4) { col = Vec3.lerp(c(40, 120, 46), c(110, 190, 70), i / 3f)
            at(cos(i * 1.7f) * 0.18f, sin(i * 1.7f) * 0.18f, 0.8f + 0.06f * i) { push(); scale(1f, 1f, 0.6f); sphere(0.3f - 0.03f * i, 14, 8); pop() } }
        col = c(60, 150, 50); for (i in 0 until 3) tube(listOf(Vec3(0.2f * cos(i * 2.1f), 0.2f * sin(i * 2.1f), 0.82f), Vec3(0.26f * cos(i * 2.1f), 0.26f * sin(i * 2.1f), 0.55f), Vec3(0.24f * cos(i * 2.1f), 0.24f * sin(i * 2.1f), 0.3f)), 0.012f, 5)
    }.build()

    // ---------------------------------------------------------------- glow (bioluminescent forest)
    private val FOX = c(140, 255, 92)
    private val TEAL = c(77, 255, 192)
    private val CYAN = c(63, 224, 255)
    private val LIME = c(216, 255, 90)

    /** A mushroom after the spore demo: pale stem, a cap that is only faintly lit on top, brightly glowing gills underneath, and dew beads. */
    private fun PM.shroom(stem: Float, capR: Float, glowCol: Vec3, k: Float = 2.4f, x: Float = 0f, y: Float = 0f) {
        push(); translate(x, y, 0f)
        col = c(184, 196, 176); cylinder(0.022f + capR * 0.07f, stem, 10, 0.02f + capR * 0.045f)
        val capTop = c(200, 255, 154)
        val prof = listOf(floatArrayOf(0.02f, stem * 0.9f), floatArrayOf(capR * 0.6f, stem * 0.88f), floatArrayOf(capR, stem * 1.0f), floatArrayOf(capR * 0.92f, stem * 1.22f), floatArrayOf(capR * 0.5f, stem * 1.42f), floatArrayOf(0.01f, stem * 1.5f))
        lathe(prof, 18, true) { _, v -> Vec3.lerp(capTop * 0.85f, glowCol * 1.1f, 1f - v) }
        col = glowCol * (k * 1.5f); at(z = stem * 0.9f) { cylinder(capR * 0.97f, 0.012f, 20) }           // the glowing gills under the cap
        col = glowCol * (k * 1.2f); at(z = stem * 0.9f) { torus(capR * 0.86f, 0.016f, 22, 6) }
        for (i in 0 until 7) { col = c(235, 255, 235); val a = i * 0.9f; val r = capR * (0.25f + 0.5f * ((i * 5) % 3) / 3f)
            push(); translate(cos(a) * r, sin(a) * r, stem * (1.28f - r / capR * 0.25f)); sphere(0.016f, 6, 4); pop() }   // dew
        pop()
    }

    private fun inkcap() = PM().apply {
        col = c(214, 222, 208); cylinder(0.035f, 0.78f, 10, 0.03f)
        col = Vec3.lerp(c(220, 235, 210), c(77, 255, 192), 0.4f) * 1.4f
        lathe(listOf(floatArrayOf(0.04f, 0.7f), floatArrayOf(0.14f, 0.68f), floatArrayOf(0.15f, 0.82f), floatArrayOf(0.1f, 1.0f), floatArrayOf(0.02f, 1.08f)), 16, true) { _, v -> c(77, 255, 192) * (0.5f + 2.0f * (1f - v)) }
    }.build()

    private fun bonnets() = PM().apply {
        for (i in 0 until 9) { val a = i * 2.4f; val r = 0.04f + 0.24f * ((i * 7) % 9) / 9f; shroom(0.12f + 0.1f * ((i * 3) % 4) / 4f, 0.05f + 0.025f * (i % 3), if (i % 2 == 0) c(63, 224, 255) else c(216, 255, 90), 2.8f, cos(a) * r, sin(a) * r) }
    }.build()

    private fun glowBell() = PM().apply { shroom(0.38f, 0.3f, FOX, 1.8f) }.build()

    private fun glowCluster() = PM().apply {
        shroom(0.5f, 0.2f, TEAL, 2.4f, -0.22f, 0.08f)
        shroom(0.34f, 0.16f, FOX, 2.6f, 0.16f, 0.16f)
        shroom(0.62f, 0.24f, CYAN, 2.4f, 0.04f, -0.2f)
        shroom(0.22f, 0.11f, LIME, 2.8f, 0.3f, -0.12f)
        shroom(0.28f, 0.12f, TEAL, 2.6f, -0.28f, -0.2f)
    }.build()

    private fun glowParasol() = PM().apply { shroom(0.8f, 0.42f, c(192, 112, 255), 2.2f) }.build()

    private fun glowGiant() = PM().apply {
        shroom(0.62f, 0.46f, FOX, 2.0f, 0f, 0f)
        shroom(0.36f, 0.2f, TEAL, 2.4f, 0.34f, 0.1f)
        shroom(0.28f, 0.16f, CYAN, 2.4f, -0.3f, 0.22f)
    }.build()

    private fun sporePod() = PM().apply {
        col = c(60, 90, 70); cylinder(0.04f, 0.4f, 8, 0.02f)
        col = FOX * 2.6f; at(z = 0.46f) { sphere(0.12f, 12, 8) }
        for (i in 0 until 8) { val a = i * 0.785f; col = LIME * 3f; at(cos(a) * 0.2f, sin(a) * 0.2f, 0.5f + 0.1f * ((i * 5) % 3)) { sphere(0.03f, 6, 4) } }
    }.build()

    // ---------------------------------------------------------------- fall (autumn)
    private fun pumpkin() = PM().apply {
        // ribbed pumpkin with a curly stem and a leaf, plus a little gourd beside it
        col = c(236, 120, 30)
        for (k in 0 until 10) { val a = k * 0.628f; at(cos(a) * 0.13f, sin(a) * 0.13f, 0.2f) { rotZ(a); scale(1.1f, 0.8f, 0.85f); sphere(0.2f, 14, 10) } }
        col = c(200, 90, 20); at(z = 0.2f) { scale(1f, 1f, 0.9f); sphere(0.24f, 16, 10) }
        col = c(90, 110, 40); tube(listOf(Vec3(0f, 0f, 0.36f), Vec3(0.02f, 0f, 0.46f), Vec3(0.07f, 0.02f, 0.5f)), 0.03f, 8, taper = { 1f - 0.4f * it })
        tube(listOf(Vec3(0.02f, 0f, 0.44f), Vec3(-0.05f, 0.06f, 0.46f), Vec3(-0.08f, 0.02f, 0.5f), Vec3(-0.05f, -0.02f, 0.48f)), 0.006f, 4)
        col = c(70, 130, 40); push(); translate(-0.08f, -0.08f, 0.38f); rotZ(-0.8f); rotY(0.4f); scale(1f, 0.6f, 0.08f); sphere(0.09f, 10, 6); pop()
    }.build()

    private fun leafPile() = PM().apply {
        val cols = listOf(c(240, 180, 40), c(232, 100, 24), c(190, 52, 24), c(210, 140, 40))
        for (i in 0 until 28) {
            val a = i * 2.4f; val r = 0.34f * ((i * 7) % 11) / 11f
            col = cols[i % 4]
            push(); translate(cos(a) * r, sin(a) * r, 0.04f + 0.17f * (1f - r / 0.34f) + 0.01f * (i % 3)); rotZ(a * 1.7f); rotX(0.25f * ((i % 5) - 2)); scale(0.2f, 0.12f, 0.02f); sphere(0.5f, 8, 4); pop()
        }
        col = c(238, 120, 28); push(); translate(0.1f, 0.06f, 0.3f); scale(0.5f, 0.5f, 0.42f); sphere(0.3f, 14, 10); pop()
        col = c(92, 112, 40); push(); translate(0.1f, 0.06f, 0.5f); cylinder(0.025f, 0.08f, 6); pop()
    }.build()

    private fun hayBale() = PM().apply {
        // two rectangular bales stacked crosswise, with twine and loose straw
        val straw = c(214, 170, 72)
        for (b in 0..1) {
            push(); translate(0f, 0f, 0.17f + b * 0.32f); rotZ(b * 1.4f)
            col = straw; box(0.62f, 0.36f, 0.3f)
            col = c(232, 196, 104); for (k in 0 until 9) at(-0.28f + k * 0.07f, -0.181f, 0f) { box(0.012f, 0.006f, 0.28f) }
            col = c(150, 96, 40); for (s in intArrayOf(-1, 1)) at(s * 0.15f, 0f, 0f) { box(0.02f, 0.372f, 0.312f) }
            pop()
        }
        col = c(232, 196, 104)
        for (k in 0 until 10) { val a = k * 0.63f; tube(listOf(Vec3(cos(a) * 0.33f, sin(a) * 0.24f, 0.02f), Vec3(cos(a) * 0.42f, sin(a) * 0.3f, 0.06f + 0.03f * (k % 3))), 0.006f, 4) }
    }.build()

    private fun autumnTree() = PM().apply {
        // gnarled trunk splitting into boughs, clouds of red-gold foliage and a few bare twigs
        val bark = c(88, 60, 40)
        col = bark
        tube(listOf(Vec3(0f, 0f, 0f), Vec3(0.03f, 0f, 0.2f), Vec3(-0.01f, 0.02f, 0.42f)), 0.07f, 10, taper = { 1f - 0.3f * it })
        for (i in 0 until 4) { val a = i * 1.57f + 0.3f
            tube(listOf(Vec3(-0.01f, 0.02f, 0.4f), Vec3(cos(a) * 0.12f, sin(a) * 0.12f, 0.55f), Vec3(cos(a) * 0.22f, sin(a) * 0.22f, 0.66f)), 0.035f, 7, taper = { 1f - 0.6f * it }) }
        for (s in intArrayOf(-1, 1)) tube(listOf(Vec3(0f, 0f, 0.03f), Vec3(s * 0.12f, 0.04f, 0f)), 0.025f, 6)
        val cols = listOf(c(214, 70, 24), c(236, 140, 30), c(190, 40, 30), c(240, 180, 50), c(160, 70, 30))
        for (i in 0 until 11) {
            val a = i * 0.57f; val r = 0.08f + 0.22f * ((i * 3) % 7) / 6f; val z = 0.62f + 0.12f * ((i * 5) % 4) / 3f
            col = cols[i % cols.size]
            at(cos(a) * r, sin(a) * r, z) { scale(1f, 1f, 0.75f); sphere(0.14f + 0.03f * (i % 3), 12, 8) }
        }
        col = bark * 0.8f; for (k in 0 until 3) { val a = k * 2.1f; tube(listOf(Vec3(cos(a) * 0.22f, sin(a) * 0.22f, 0.66f), Vec3(cos(a) * 0.38f, sin(a) * 0.38f, 0.78f)), 0.008f, 4) }
    }.build()

    // ---------------------------------------------------------------- toy world
    private fun legoBrick() = PM().apply {
        // a 2x4 brick with rounded studs, embossed rims and the hollow tubes visible underneath
        val red = c(220, 30, 30)
        col = red; push(); translate(0f, 0f, 0.2f); box(0.8f, 0.4f, 0.4f); pop()
        for (i in 0 until 4) for (j in 0 until 2) at(-0.3f + i * 0.2f, -0.1f + j * 0.2f, 0.4f) {
            col = red; cylinder(0.07f, 0.07f, 16)
            col = c(240, 60, 60); at(z = 0.07f) { torus(0.06f, 0.01f, 16, 4) }
        }
        col = c(170, 20, 20); for (i in 0 until 3) at(-0.2f + i * 0.2f, 0f, 0f) { cylinder(0.06f, 0.006f, 14) }
        col = c(250, 250, 250); push(); translate(0f, -0.201f, 0.2f); box(0.2f, 0.003f, 0.06f); pop()
    }.build()

    private fun starBlock() = PM().apply {
        col = c(250, 200, 30); push(); translate(0f, 0f, 0.2f); box(0.5f, 0.5f, 0.4f); pop()
        col = c(255, 235, 120); at(z = 0.4f) { cylinder(0.12f, 0.08f, 5) }
    }.build()

    private fun duck() = PM().apply {
        // rubber duck: plump body with a raised tail, head, orange bill with a lip line, glossy eyes and wing bumps
        val y = c(255, 214, 30)
        col = y; at(z = 0.24f) { scale(1.25f, 0.95f, 0.75f); sphere(0.26f, 22, 14) }
        push(); translate(-0.3f, 0f, 0.36f); rotY(-0.8f); scale(0.6f, 0.5f, 1f); cone(0.1f, 0.16f, 12); pop()
        at(0.18f, 0f, 0.58f) { sphere(0.17f, 20, 14) }
        for (s in intArrayOf(-1, 1)) at(-0.04f, s * 0.22f, 0.3f) { scale(1.4f, 0.4f, 0.8f); sphere(0.11f, 12, 8) }
        col = c(255, 120, 20); at(0.34f, 0f, 0.55f) { scale(1.2f, 1f, 0.45f); sphere(0.08f, 12, 8) }
        col = c(220, 90, 10); at(0.36f, 0f, 0.54f) { box(0.1f, 0.12f, 0.006f) }
        col = c(20, 20, 24); for (s in intArrayOf(-1, 1)) at(0.29f, s * 0.08f, 0.64f) { sphere(0.025f, 8, 6) }
        col = c(255, 255, 255) * 1.1f; for (s in intArrayOf(-1, 1)) at(0.305f, s * 0.085f, 0.655f) { sphere(0.008f, 5, 4) }
    }.build()

    private fun toyCar() = PM().apply {
        // chunky wind-up toy car: rounded body, windows, big wheels with hubs, key on the back
        val paint = c(40, 130, 230)
        col = paint; push(); translate(0f, 0f, 0.22f); scale(0.28f, 0.44f, 0.12f); sphere(1f, 18, 10); pop()
        push(); translate(0f, 0.05f, 0.34f); scale(0.2f, 0.22f, 0.12f); sphere(1f, 14, 8); pop()
        col = c(200, 230, 255); for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.17f, 0.05f, 0.36f); box(0.01f, 0.2f, 0.07f); pop() }
        push(); translate(0f, -0.13f, 0.37f); rotX(0.5f); box(0.26f, 0.01f, 0.08f); pop()
        col = c(255, 214, 40); for (s in intArrayOf(-1, 1)) at(s * 0.12f, -0.43f, 0.24f) { sphere(0.035f, 8, 6) }
        col = c(230, 50, 50); at(0f, 0.45f, 0.22f) { box(0.3f, 0.02f, 0.06f) }
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            col = c(24, 24, 28); push(); translate(sx * 0.24f, sy * 0.27f, 0.12f); rotY((PI / 2).toFloat()); translate(0f, 0f, -0.05f); cylinder(0.12f, 0.1f, 16); pop()
            col = c(250, 214, 40); push(); translate(sx * 0.295f, sy * 0.27f, 0.12f); rotY((PI / 2).toFloat()); cylinder(0.05f, 0.01f, 12); pop()
        }
        col = c(200, 200, 210); tube(listOf(Vec3(0f, 0.44f, 0.3f), Vec3(0f, 0.54f, 0.3f)), 0.015f, 6)
        push(); translate(0f, 0.56f, 0.3f); rotX((PI / 2).toFloat()); scale(1f, 0.25f, 1f); torus(0.05f, 0.015f, 12, 4); pop()
    }.build()

    private fun toyRocket() = PM().apply {
        col = c(235, 60, 50)
        lathe(listOf(floatArrayOf(0.02f, 0.2f), floatArrayOf(0.2f, 0.22f), floatArrayOf(0.2f, 0.7f), floatArrayOf(0.1f, 0.92f), floatArrayOf(0f, 1f)), 20, true) { _, v -> if (v > 0.75f) c(250, 200, 30) else c(235, 60, 50) }
        col = c(40, 130, 230); for (k in 0 until 3) { push(); rotZ(k * 2.094f); translate(0.2f, 0f, 0.28f); box(0.16f, 0.03f, 0.3f); pop() }
        col = c(210, 235, 255); at(0f, -0.195f, 0.55f) { sphere(0.06f, 10, 8) }
    }.build()

    private fun blockStack() = PM().apply {
        val cols = listOf(c(226, 48, 40), c(250, 200, 30), c(34, 120, 230))
        for (i in 0 until 3) { col = cols[i]; push(); translate(0.04f * (i % 2), 0f, 0.15f + i * 0.3f); rotZ(0.3f * i); box(0.34f, 0.34f, 0.3f); pop() }
        col = c(255, 255, 255); at(z = 0.92f) { sphere(0.12f, 10, 8) }
    }.build()

    // ---------------------------------------------------------------- craft
    private fun buttonProp() = PM().apply {
        col = c(236, 90, 120); cylinder(0.3f, 0.08f, 26)
        col = c(250, 130, 150); at(z = 0.06f) { torus(0.26f, 0.025f, 26, 6) }
        col = c(60, 40, 40); for (i in 0..1) for (j in 0..1) { at(-0.07f + i * 0.14f, -0.07f + j * 0.14f, 0.075f) { cylinder(0.03f, 0.02f, 8) } }
        col = c(250, 240, 220); tube(listOf(Vec3(-0.07f, -0.07f, 0.1f), Vec3(0.07f, 0.07f, 0.1f)), 0.012f, 5); tube(listOf(Vec3(0.07f, -0.07f, 0.1f), Vec3(-0.07f, 0.07f, 0.1f)), 0.012f, 5)
    }.build()

    private fun spool() = PM().apply {
        col = c(200, 150, 90); cylinder(0.3f, 0.07f, 24); at(z = 0.73f) { cylinder(0.3f, 0.07f, 24) }
        col = c(224, 60, 90); at(z = 0.07f) { cylinder(0.2f, 0.66f, 22) }
        col = c(240, 100, 130); for (k in 0 until 8) { at(z = 0.1f + k * 0.075f) { torus(0.2f, 0.012f, 22, 5) } }
        col = c(250, 240, 220); tube(listOf(Vec3(0.2f, 0f, 0.3f), Vec3(0.34f, 0.1f, 0.15f), Vec3(0.3f, 0.3f, 0.03f)), 0.012f, 5)
    }.build()

    private fun pomPom() = PM().apply {
        val cols = listOf(c(250, 140, 170), c(250, 200, 80), c(110, 200, 220))
        for (i in 0 until 40) { val a = i * 2.4f; val z = 0.1f + 0.5f * ((i * 7) % 13) / 13f; val r = 0.28f * kotlin.math.sqrt(1f - ((z - 0.35f) / 0.4f).let { it * it }.coerceAtMost(1f))
            col = cols[i % 3]; push(); translate(cos(a) * r, sin(a) * r, z); sphere(0.07f, 6, 4); pop() }
        col = c(250, 140, 170); at(z = 0.36f) { sphere(0.26f, 12, 8) }
    }.build()

    private fun heartPatch() = PM().apply {
        // a stuffed felt heart on a stand, edged with running stitches
        val felt = c(224, 60, 90)
        fun lobe(sgn: Float) = listOf(floatArrayOf(0f, -0.42f)) + (0..10).map { k ->
            val a = (PI * (1.15f - 1.15f * k / 10f)).toFloat()
            floatArrayOf(sgn * (0.2f + 0.2f * cos(a)) , 0.12f + 0.2f * sin(a)) }.let { if (sgn < 0) it else it.reversed() } + listOf(floatArrayOf(0f, 0.1f))
        push(); translate(0f, 0.05f, 0.5f); rotX((PI / 2).toFloat())
        col = felt; for (s in floatArrayOf(-1f, 1f)) { val pts = lobe(s); prism(if (s > 0) pts.reversed() else pts, 0.1f) }
        col = c(250, 240, 220)
        for (s in floatArrayOf(-1f, 1f)) for (k in 0 until 9) {
            val a0 = (PI * (1.1f - 1.1f * k / 9f)).toFloat(); val a1 = a0 - 0.08f
            tube(listOf(Vec3(s * (0.2f + 0.19f * cos(a0)), 0.12f + 0.19f * sin(a0), 0.105f), Vec3(s * (0.2f + 0.19f * cos(a1)), 0.12f + 0.19f * sin(a1), 0.105f)), 0.008f, 4)
        }
        pop()
        col = c(180, 140, 100); cylinder(0.025f, 0.12f, 8); cylinder(0.14f, 0.03f, 16)
    }.build()

    private fun yarnBall() = PM().apply {
        for (k in 0 until 14) { col = if (k % 2 == 0) c(100, 190, 210) else c(236, 100, 130); push(); translate(0f, 0f, 0.3f); rotZ(k * 0.5f); rotX(0.4f * k); torus(0.28f, 0.03f, 26, 6); pop() }
        col = c(236, 100, 130); at(0f, 0f, 0.3f) { sphere(0.27f, 14, 10) }
    }.build()

    private fun feltTree() = PM().apply {
        col = c(130, 90, 60); cylinder(0.06f, 0.25f, 8)
        for (i in 0 until 3) { col = if (i % 2 == 0) c(70, 150, 90) else c(100, 180, 100); at(z = 0.2f + i * 0.22f) { cone(0.34f - i * 0.08f, 0.4f, 14) } }
        col = c(250, 200, 60); at(z = 0.92f) { sphere(0.05f, 8, 6) }
    }.build()

    // ---------------------------------------------------------------- prehistoric
    private fun boneProp() = PM().apply {
        // a big dinosaur bone lying on a rock, knobbly at both ends
        col = c(120, 110, 96); at(z = 0.05f) { scale(1.3f, 1f, 0.4f); sphere(0.2f, 10, 6) }
        col = c(240, 232, 210)
        push(); translate(0f, 0f, 0.16f); rotY((PI / 2).toFloat()); translate(0f, 0f, -0.3f); cylinder(0.055f, 0.6f, 12, 0.06f); pop()
        for (s in intArrayOf(-1, 1)) { at(s * 0.32f, 0.05f, 0.16f) { sphere(0.085f, 12, 8) }; at(s * 0.32f, -0.05f, 0.16f) { sphere(0.08f, 12, 8) } }
        col = c(210, 200, 176); for (k in 0 until 3) at(-0.1f + 0.1f * k, 0f, 0.215f) { box(0.01f, 0.06f, 0.006f) }
    }.build()

    private fun eggNest() = PM().apply {
        // a twiggy nest holding three speckled eggs
        col = c(120, 86, 50)
        for (k in 0 until 18) { val a = k * 0.35f; val r = 0.3f
            tube(listOf(Vec3(cos(a) * r, sin(a) * r, 0.04f + 0.03f * (k % 3)), Vec3(cos(a + 0.9f) * (r + 0.03f), sin(a + 0.9f) * (r + 0.03f), 0.1f - 0.02f * (k % 2))), 0.018f, 4) }
        col = c(140, 100, 60); at(z = 0.02f) { scale(1f, 1f, 0.3f); sphere(0.3f, 16, 8) }
        for (i in 0 until 3) { val a = i * 2.1f
            push(); translate(cos(a) * 0.1f, sin(a) * 0.1f, 0.16f); rotY(0.3f * (i - 1)); scale(0.8f, 0.8f, 1.1f)
            sphere(0.12f, 16, 12) { u, v -> if (((u * 37f + v * 23f) * 7f % 1f) > 0.9f) c(150, 120, 90) else c(244, 236, 214) }
            pop() }
    }.build()

    private fun volcanoProp() = PM().apply {
        // rugged cone, glowing crater, lava rivulets down the flanks and a plume of smoke
        lathe(listOf(floatArrayOf(0.02f, 0f), floatArrayOf(0.48f, 0f), floatArrayOf(0.4f, 0.12f), floatArrayOf(0.26f, 0.48f), floatArrayOf(0.16f, 0.76f), floatArrayOf(0.13f, 0.8f), floatArrayOf(0.08f, 0.74f), floatArrayOf(0.02f, 0.72f)), 22, false) { u, v ->
            val n = sin(u * 37f) * sin(v * 23f + u * 11f)
            if (v > 0.9f) c(255, 120, 30) * 2f else c(84, 64, 54) * (0.8f + 0.25f * n) }
        col = c(255, 110, 20) * 2.4f; at(z = 0.74f) { cylinder(0.1f, 0.02f, 14) }
        for (i in 0 until 4) {
            val a = i * 1.57f + 0.4f
            col = c(255, 100, 20) * 2f
            tube((0..5).map { k -> val v = k / 5f; val r = 0.14f + 0.33f * v; Vec3(cos(a + 0.2f * sin(v * 6f)) * r, sin(a + 0.2f * sin(v * 6f)) * r, 0.78f - 0.74f * v) }, 0.016f, 5, taper = { 1f - 0.6f * it })
        }
        col = c(90, 86, 90)
        for (k in 0 until 5) at(0.04f * sin(k * 2f), 0.04f * cos(k * 1.3f), 0.86f + k * 0.07f) { sphere(0.07f + 0.025f * k, 10, 8) }
    }.build()

    private fun ammonite() = PM().apply {
        col = c(176, 150, 110)
        val path = ArrayList<Vec3>(); for (i in 0..30) { val a = i * 0.28f; val r = 0.04f + 0.012f * i; path.add(Vec3(cos(a) * r, 0f, 0.2f + sin(a) * r)) }
        tube(path, 0.05f, 8, false, null) { 0.5f + 0.9f * it }
        col = c(120, 100, 70); push(); translate(0f, 0f, 0.0f); cylinder(0.2f, 0.06f, 14); pop()
    }.build()

    // ---------------------------------------------------------------- tool shop
    private fun boltProp() = PM().apply {
        col = c(168, 172, 182); cylinder(0.3f, 0.12f, 6); at(z = 0.12f) { cylinder(0.22f, 0.04f, 6, 0.2f) }
        col = c(130, 134, 144); at(z = 0.1f) { cylinder(0.09f, 0.5f, 12) }
        col = c(100, 104, 114); for (k in 0 until 7) at(z = 0.14f + k * 0.06f) { torus(0.09f, 0.012f, 12, 4) }
    }.build()

    /** A hex nut centred on the bolt axis, sitting on z = 0 (it winds up and down the thread). */
    private fun hexNut() = PM().apply {
        col = c(200, 170, 70); cylinder(0.17f, 0.09f, 6)
        col = c(225, 196, 96); at(z = 0.09f) { cylinder(0.15f, 0.012f, 6, 0.13f) }
        col = c(60, 60, 66); at(z = 0.1f) { cylinder(0.092f, 0.004f, 14) }
    }.build()

    private fun toolbox() = PM().apply {
        // steel toolbox with a cantilever tray, latches, handle and a few tools poking out
        val red = c(214, 40, 36)
        col = red; push(); translate(0f, 0f, 0.18f); box(0.78f, 0.4f, 0.36f); pop()
        col = c(180, 30, 28); push(); translate(0f, 0f, 0.38f); box(0.8f, 0.42f, 0.04f); pop()
        col = c(150, 24, 22); for (k in 0 until 3) { push(); translate(0f, -0.201f, 0.08f + k * 0.1f); box(0.7f, 0.004f, 0.012f); pop() }
        col = c(200, 204, 214); for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.28f, -0.21f, 0.33f); box(0.07f, 0.02f, 0.08f); pop() }
        tube(listOf(Vec3(-0.2f, 0f, 0.4f), Vec3(-0.18f, 0f, 0.56f), Vec3(0.18f, 0f, 0.56f), Vec3(0.2f, 0f, 0.4f)), 0.022f, 8)
        col = c(30, 30, 34); at(0f, 0f, 0.565f) { push(); rotY((PI / 2).toFloat()); translate(0f, 0f, -0.12f); cylinder(0.03f, 0.24f, 10); pop() }
        col = c(186, 190, 200); push(); translate(0.2f, 0.1f, 0.48f); rotY(0.3f); box(0.04f, 0.02f, 0.22f); pop()
        col = c(250, 214, 40); push(); translate(-0.22f, 0.1f, 0.46f); rotY(-0.4f); box(0.05f, 0.04f, 0.18f); pop()
        col = c(250, 214, 40); push(); translate(0f, -0.203f, 0.22f); box(0.16f, 0.004f, 0.06f); pop()
    }.build()

    private fun wrench() = PM().apply {
        // combination spanner standing on its ring end: tapered shaft, open jaw at the top
        val steel = c(186, 190, 200)
        col = steel
        push(); translate(0f, 0f, 0.48f); box(0.09f, 0.035f, 0.62f); pop()
        at(0f, 0f, 0.12f) { rotX((PI / 2).toFloat()); translate(0f, 0f, -0.02f); torus(0.09f, 0.035f, 18, 6) }
        for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.1f, 0f, 0.9f); rotY(-s * 0.25f); box(0.06f, 0.04f, 0.16f); pop() }
        push(); translate(0f, 0f, 0.8f); box(0.24f, 0.04f, 0.06f); pop()
        col = c(230, 40, 36); push(); translate(0f, 0f, 0.42f); box(0.1f, 0.045f, 0.3f); pop()
        col = c(60, 60, 66); for (k in 0 until 4) at(0f, -0.024f, 0.32f + k * 0.06f) { box(0.08f, 0.004f, 0.012f) }
    }.build()

    private fun hammerProp() = PM().apply {
        // claw hammer standing on its head: wooden handle with grip wraps, forged head with claw
        col = c(70, 74, 86)
        push(); translate(0f, 0f, 0.09f); box(0.14f, 0.16f, 0.18f); pop()
        push(); translate(0.13f, 0f, 0.09f); rotY((PI / 2).toFloat()); cylinder(0.075f, 0.06f, 14); pop()
        for (s in intArrayOf(-1, 1)) { push(); translate(-0.12f, s * 0.035f, 0.12f); rotY(-0.5f); box(0.16f, 0.03f, 0.06f); pop() }
        col = c(176, 120, 64); at(z = 0.18f) { cylinder(0.04f, 0.72f, 12, 0.032f) }
        col = c(30, 30, 34); for (k in 0 until 6) at(z = 0.62f + k * 0.04f) { torus(0.038f, 0.008f, 12, 4) }
    }.build()

    private fun gearProp() = PM().apply {
        // a thick cog with chamfered teeth, spokes and a hub, standing on its edge
        push(); translate(0f, 0f, 0.45f); rotX((PI / 2).toFloat()); translate(0f, 0f, -0.06f)
        col = c(176, 182, 194)
        lathe(listOf(floatArrayOf(0.3f, 0f), floatArrayOf(0.36f, 0f), floatArrayOf(0.36f, 0.12f), floatArrayOf(0.3f, 0.12f)), 32, false)
        lathe(listOf(floatArrayOf(0.3f, 0f), floatArrayOf(0.3f, 0.12f)), 32, false)
        for (k in 0 until 14) { push(); rotZ(k * 0.4488f); translate(0.4f, 0f, 0.06f); box(0.1f, 0.09f, 0.12f); pop() }
        for (k in 0 until 5) { push(); rotZ(k * 1.2566f); translate(0.18f, 0f, 0.06f); box(0.24f, 0.05f, 0.08f); pop() }
        col = c(120, 126, 140); cylinder(0.09f, 0.14f, 16)
        col = c(40, 44, 54); cylinder(0.04f, 0.15f, 12)
        pop()
        col = c(60, 64, 74); push(); translate(0f, 0f, 0.03f); box(0.3f, 0.2f, 0.06f); pop(); cylinder(0.03f, 0.45f, 8)
    }.build()

    // ---------------------------------------------------------------- lava
    private fun emberSpire() = PM().apply {
        // a cluster of basalt columns with molten seams
        val rock = c(36, 28, 32)
        val hs = floatArrayOf(0.95f, 0.7f, 0.55f, 0.8f, 0.45f)
        for (i in 0 until 5) {
            val a = i * 1.26f; val r = if (i == 0) 0f else 0.17f
            col = rock; push(); translate(cos(a) * r, sin(a) * r, 0f); rotZ(a); cylinder(0.11f, hs[i], 6, 0.1f)
            col = c(255, 100, 20) * 2.2f; at(z = hs[i]) { cylinder(0.075f, 0.012f, 6) }
            col = c(255, 120, 30) * 1.8f; at(0.1f, 0f, hs[i] * 0.4f) { box(0.012f, 0.02f, hs[i] * 0.8f) }
            pop()
        }
    }.build()

    private fun lavaRocks() = PM().apply {
        // rough boulders split by glowing cracks, with a small pool of lava between them
        col = c(255, 90, 20) * 2.1f; at(z = 0.005f) { cylinder(0.3f, 0.02f, 18) }
        for (i in 0 until 5) {
            val a = i * 1.3f; val r = 0.12f + 0.12f * (i % 2)
            col = c(42, 32, 34)
            push(); translate(cos(a) * r, sin(a) * r, 0.1f + 0.04f * (i % 3)); rotZ(a); rotX(0.3f * (i % 2)); scale(1.2f, 0.9f, 0.8f + 0.2f * (i % 3)); sphere(0.14f, 7, 5); pop()
            col = c(255, 110, 30) * 1.9f
            push(); translate(cos(a) * r, sin(a) * r, 0.12f); rotZ(a + 0.5f); box(0.2f, 0.012f, 0.012f); pop()
        }
    }.build()

    private fun obsidianCrystal() = PM().apply {
        col = c(24, 18, 28); cylinder(0.14f, 0.6f, 6, 0.1f); at(z = 0.6f) { cone(0.1f, 0.3f, 6) }
        col = c(255, 90, 30) * 1.7f; push(); translate(0.1f, 0f, 0.0f); box(0.02f, 0.02f, 0.6f); pop()
        col = c(40, 26, 34); push(); translate(0.22f, 0.1f, 0f); rotZ(0.6f); cylinder(0.08f, 0.38f, 6, 0.05f); pop()
    }.build()

    private fun brazier() = PM().apply {
        col = c(46, 38, 40); cylinder(0.04f, 0.4f, 8); at(z = 0.4f) { cylinder(0.24f, 0.1f, 14, 0.16f) }
        col = c(255, 110, 30) * 1.8f; at(z = 0.48f) { cylinder(0.18f, 0.03f, 14) }          // glowing coals
    }.build()

    // ---------------------------------------------------------------- jelly
    private fun gummyBear() = PM().apply {
        col = c(255, 60, 90) * 1.25f
        at(z = 0.3f) { scale(1f, 0.85f, 1.2f); sphere(0.3f, 20, 12) }
        at(z = 0.66f) { sphere(0.22f, 18, 12) }
        for (s in intArrayOf(-1, 1)) { at(s * 0.16f, 0f, 0.84f) { sphere(0.08f, 12, 8) }; at(s * 0.3f, 0f, 0.42f) { sphere(0.1f, 12, 8) }; at(s * 0.14f, 0f, 0.06f) { sphere(0.1f, 12, 8) } }
        col = c(255, 235, 240); for (s in intArrayOf(-1, 1)) at(s * 0.08f, 0.19f, 0.7f) { sphere(0.035f, 8, 6) }
    }.build()

    private fun pudding() = PM().apply {
        col = c(255, 200, 70) * 1.15f; cylinder(0.32f, 0.5f, 26, 0.2f)
        col = c(120, 56, 30); at(z = 0.5f) { cylinder(0.2f, 0.04f, 24) }
        col = c(235, 40, 70) * 1.3f; at(z = 0.62f) { sphere(0.1f, 14, 10) }
        col = c(250, 250, 250); at(0f, 0f, 0.0f) { cylinder(0.36f, 0.04f, 26) }
    }.build()

    private fun jellyBean() = PM().apply {
        col = c(70, 220, 90) * 1.2f; at(z = 0.22f) { scale(0.7f, 1.2f, 0.7f); sphere(0.26f, 18, 12) }
        col = c(255, 255, 255); at(0.1f, 0.14f, 0.34f) { scale(1f, 1.4f, 0.5f); sphere(0.05f, 8, 6) }
    }.build()

    private fun jellyDrop() = PM().apply {
        col = c(80, 200, 255) * 1.2f; cone(0.28f, 0.55f, 22)
        col = c(255, 255, 255); for (k in 0 until 10) { val a = k * 0.628f; at(0.22f * kotlin.math.cos(a) * (1f - k * 0.05f), 0.22f * kotlin.math.sin(a) * (1f - k * 0.05f), 0.04f + k * 0.045f) { sphere(0.03f, 6, 5) } }
    }.build()

    private fun cherryProp() = PM().apply {
        col = c(220, 30, 60) * 1.25f; at(0f, 0f, 0.22f) { sphere(0.22f, 18, 12) }
        col = c(60, 140, 50); tube(listOf(Vec3(0f, 0f, 0.42f), Vec3(0.06f, 0.02f, 0.62f), Vec3(0.2f, 0f, 0.78f)), 0.018f, 6)
        col = c(255, 230, 235); at(-0.08f, 0.14f, 0.3f) { sphere(0.04f, 8, 6) }
    }.build()

    // ---------------------------------------------------------------- extra props (second quality pass)
    private fun raceCar() = PM().apply {
        // a modern single-seater, nose along +x: tapered nose, three-element front wing, survival cell with halo and driver,
        // sculpted sidepods with intakes, engine cover with airbox and shark fin, two-element rear wing, floor and diffuser,
        // pushrod-style wishbones, and shaped tyres with coloured sidewall stripes on spoked rims
        val paint = c(200, 20, 28); val white = c(245, 245, 245); val carbon = c(26, 26, 30); val tyre = c(22, 22, 24)
        fun loft(pts: List<Vec3>, r: Float, rr: (Float) -> Float, sy: Float = 1f, sz: Float = 1f, cf: ((Float) -> Vec3)? = null) {
            push(); scale(1f, sy, sz); tube(pts.map { Vec3(it.x, it.y / sy, it.z / sz) }, r, 14, colorFn = cf, taper = rr); pop()
        }
        // nose cone and survival cell
        loft((0..8).map { k -> val v = k / 8f; Vec3(0.5f - 0.38f * v, 0f, 0.085f + 0.06f * v) }, 0.06f, { v -> 0.22f + 0.78f * v }, 1.25f, 0.75f,
            { v -> if (v < 0.25f) white else paint })
        loft((0..8).map { k -> val v = k / 8f; Vec3(0.12f - 0.3f * v, 0f, 0.145f + 0.01f * kotlin.math.sin(v * 3f)) }, 0.065f, { 1f - 0.08f * it }, 1.15f, 0.9f)
        col = c(12, 12, 14); at(-0.03f, 0f, 0.19f) { scale(1.7f, 0.85f, 0.25f); sphere(0.042f, 14, 8) }                  // cockpit opening
        col = white; at(0.2f, 0f, 0.177f) { box(0.26f, 0.016f, 0.004f) }                                                  // nose stripe
        // driver: helmet with a dark visor
        col = c(250, 214, 40); at(-0.03f, 0f, 0.215f) { sphere(0.034f, 14, 10) }
        col = c(20, 24, 34); at(-0.005f, 0f, 0.218f) { scale(0.7f, 1.05f, 0.42f); sphere(0.03f, 12, 6) }
        // halo
        col = c(50, 52, 58)
        tube(listOf(Vec3(0.07f, 0f, 0.16f), Vec3(0.06f, 0f, 0.235f)), 0.009f, 6)
        tube((0..10).map { k -> val a = PI.toFloat() * (k / 10f - 0.5f); Vec3(0.06f - 0.09f * (1f - cos(a)) * 1.1f, 0.055f * sin(a) * 1.15f, 0.235f - 0.03f * (1f - cos(a))) }, 0.008f, 6)
        // sidepods with intakes, and mirrors
        for (s in intArrayOf(-1, 1)) {
            loft((0..8).map { k -> val v = k / 8f; Vec3(0.06f - 0.36f * v, s * (0.115f - 0.03f * v * v), 0.12f - 0.025f * v) }, 0.055f, { v -> 1f - 0.6f * v * v }, 1.1f, 0.85f,
                { v -> if (v in 0.35f..0.55f) white else paint })
            col = c(10, 10, 12); at(0.065f, s * 0.115f, 0.125f) { scale(0.3f, 1f, 0.8f); sphere(0.045f, 10, 6) }      // intake
            col = carbon; tube(listOf(Vec3(0.05f, s * 0.07f, 0.17f), Vec3(0.06f, s * 0.14f, 0.2f)), 0.004f, 4)
            col = paint; at(0.06f, s * 0.15f, 0.2f) { box(0.012f, 0.04f, 0.022f) }
        }
        // engine cover, airbox and shark fin
        loft((0..8).map { k -> val v = k / 8f; Vec3(-0.06f - 0.36f * v, 0f, 0.235f - 0.1f * v) }, 0.042f, { v -> 1f - 0.65f * v }, 1.3f, 1.1f)
        col = c(12, 12, 14); at(-0.055f, 0f, 0.255f) { scale(0.4f, 1f, 1f); sphere(0.022f, 10, 6) }                   // airbox mouth
        col = c(250, 214, 40); at(-0.05f, 0f, 0.285f) { box(0.03f, 0.014f, 0.012f) }                                     // camera pod
        col = paint; push(); translate(-0.4f, 0f, 0.15f); rotX((PI / 2).toFloat())
        prism(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.28f, 0.08f), floatArrayOf(0.3f, 0.12f), floatArrayOf(0f, 0.06f)), 0.004f); pop()
        col = white; at(-0.26f, 0f, 0.2f) { box(0.06f, 0.088f, 0.006f) }                                                // sponsor patch
        // front wing: three elements with endplates
        for (e in 0 until 3) { col = if (e == 2) paint else carbon
            at(0.48f - e * 0.035f, 0f, 0.035f + e * 0.018f) { rotY(-0.15f - e * 0.12f); box(0.05f, 0.46f, 0.008f) } }
        for (s in intArrayOf(-1, 1)) { col = carbon; at(0.45f, s * 0.235f, 0.06f) { box(0.12f, 0.008f, 0.06f) }
            col = paint; at(0.45f, s * 0.236f, 0.08f) { box(0.1f, 0.009f, 0.015f) } }
        // rear wing: main plane and flap between tall endplates, on a centre pillar
        col = carbon; at(-0.47f, 0f, 0.3f) { rotY(0.12f); box(0.07f, 0.27f, 0.01f) }
        col = paint; at(-0.5f, 0f, 0.33f) { rotY(0.45f); box(0.05f, 0.27f, 0.008f) }
        for (s in intArrayOf(-1, 1)) { col = carbon; at(-0.48f, s * 0.14f, 0.27f) { box(0.12f, 0.008f, 0.14f) }
            col = white; at(-0.48f, s * 0.141f, 0.3f) { box(0.08f, 0.009f, 0.03f) } }
        col = carbon; at(-0.45f, 0f, 0.2f) { box(0.02f, 0.012f, 0.1f) }
        // floor and diffuser
        col = carbon; at(-0.05f, 0f, 0.035f) { box(0.62f, 0.34f, 0.01f) }
        at(-0.43f, 0f, 0.06f) { rotY(-0.35f); box(0.12f, 0.24f, 0.008f) }
        for (k in 0 until 4) at(-0.44f, -0.09f + k * 0.06f, 0.06f) { box(0.1f, 0.004f, 0.05f) }
        // wheels and suspension
        for ((x, w) in listOf(0.3f to 0.085f, -0.31f to 0.115f)) for (s in intArrayOf(-1, 1)) {
            val y = s * (0.21f + w * 0.5f)
            col = carbon
            tube(listOf(Vec3(x + 0.05f, s * 0.05f, 0.15f), Vec3(x, y - s * w * 0.4f, 0.12f)), 0.005f, 4)          // upper wishbone
            tube(listOf(Vec3(x - 0.06f, s * 0.05f, 0.15f), Vec3(x, y - s * w * 0.4f, 0.12f)), 0.005f, 4)
            tube(listOf(Vec3(x + 0.05f, s * 0.06f, 0.07f), Vec3(x, y - s * w * 0.4f, 0.06f)), 0.005f, 4)          // lower wishbone
            tube(listOf(Vec3(x - 0.06f, s * 0.06f, 0.07f), Vec3(x, y - s * w * 0.4f, 0.06f)), 0.005f, 4)
            push(); translate(x, y, 0.095f); rotX((PI / 2).toFloat())
            val r = 0.095f; val hw = w * 0.5f
            lathe(listOf(floatArrayOf(0.055f, -hw), floatArrayOf(r * 0.94f, -hw), floatArrayOf(r, -hw * 0.7f), floatArrayOf(r, hw * 0.7f), floatArrayOf(r * 0.94f, hw), floatArrayOf(0.055f, hw)), 28, true) { _, v ->
                val side = kotlin.math.abs(v - 0.5f) > 0.38f
                if (side && (v < 0.06f || v > 0.94f)) c(250, 214, 40) else tyre }                           // yellow sidewall stripe
            col = c(40, 40, 46); cylinder(0.055f, 0.004f, 20)                                                            // rim
            col = c(170, 172, 180); for (k in 0 until 10) { val a = k * 0.628f; at(cos(a) * 0.03f, sin(a) * 0.03f, -s * hw * 0.2f) { rotZ(a); box(0.05f, 0.007f, 0.006f) } }
            col = c(250, 214, 40); at(z = -s * hw * 0.3f) { cylinder(0.012f, 0.006f, 10) }                              // wheel nut (outer face)
            pop()
        }
    }.build()

    private fun goalNet() = PM().apply {
        // goal mouth: white posts and crossbar with a net stretched behind
        val post = c(250, 250, 250)
        col = post
        for (s in intArrayOf(-1, 1)) at(0f, s * 0.42f, 0f) { cylinder(0.025f, 0.62f, 8) }
        push(); translate(0f, -0.42f, 0.62f); rotX(-(PI / 2).toFloat()); cylinder(0.025f, 0.84f, 8); pop()
        col = c(220, 224, 230)
        for (k in 0..8) { val y = -0.42f + k * 0.105f; tube(listOf(Vec3(0f, y, 0.62f), Vec3(-0.18f, y, 0.55f), Vec3(-0.3f, y, 0f)), 0.005f, 3) }
        for (k in 0..5) { val z = k * 0.12f; val x = -0.3f + 0.3f * (z / 0.62f).coerceAtMost(1f) * 0.6f; tube(listOf(Vec3(x, -0.42f, z), Vec3(x, 0.42f, z)), 0.005f, 3) }
    }.build()

    private fun microscope() = PM().apply {
        val body = c(236, 238, 244); val dark = c(40, 44, 54)
        col = dark; push(); translate(0f, 0f, 0.03f); box(0.44f, 0.32f, 0.06f); pop()
        col = body; tube(listOf(Vec3(-0.14f, 0f, 0.05f), Vec3(-0.16f, 0f, 0.35f), Vec3(-0.05f, 0f, 0.62f)), 0.05f, 10)       // arm
        col = dark; push(); translate(0.02f, 0f, 0.3f); box(0.24f, 0.22f, 0.025f); pop()                                   // stage
        col = c(120, 200, 255) * 1.3f; push(); translate(0.04f, 0f, 0.318f); box(0.12f, 0.06f, 0.006f); pop()          // slide
        col = body; push(); translate(0.02f, 0f, 0.5f); rotY(-0.45f); cylinder(0.05f, 0.4f, 14); pop()                  // tube
        col = dark; push(); translate(0.02f, 0f, 0.5f); rotY(-0.45f); translate(0f, 0f, 0.38f); cylinder(0.035f, 0.1f, 12); pop()
        col = c(200, 180, 90); for (k in 0 until 3) { push(); translate(0.04f + 0.03f * (k - 1), 0f, 0.42f); rotY(0.4f * (k - 1)); cylinder(0.018f, 0.08f, 8); pop() }
        col = dark; for (s in intArrayOf(-1, 1)) at(-0.15f, s * 0.07f, 0.25f) { rotX((PI / 2).toFloat()); cylinder(0.04f, 0.03f, 12) }
    }.build()

    private fun teslaCoil() = PM().apply {
        col = c(40, 44, 54); cylinder(0.28f, 0.08f, 18)
        col = c(196, 120, 50); at(z = 0.08f) { cylinder(0.08f, 0.6f, 16) }
        col = c(230, 150, 70); for (k in 0 until 18) at(z = 0.1f + k * 0.032f) { torus(0.082f, 0.006f, 16, 3) }
        col = c(210, 214, 226); at(z = 0.76f) { scale(1f, 1f, 0.35f); torus(0.18f, 0.08f, 24, 10) }
    }.build()
    private fun teslaSparks() = PM().apply {
        col = c(180, 140, 255) * 2.4f
        for (k in 0 until 5) { val a = k * 1.26f; tube(listOf(Vec3(cos(a) * 0.2f, sin(a) * 0.2f, 0.78f), Vec3(cos(a + 0.4f) * 0.32f, sin(a + 0.4f) * 0.32f, 0.86f), Vec3(cos(a + 0.2f) * 0.45f, sin(a + 0.2f) * 0.45f, 0.8f)), 0.007f, 3) }
    }.build()

    private fun penguin() = PM().apply {
        val black = c(26, 28, 36); val white = c(244, 246, 250)
        col = black; at(z = 0.34f) { scale(0.8f, 0.7f, 1.1f); sphere(0.3f, 18, 12) }
        at(z = 0.72f) { sphere(0.17f, 16, 12) }
        col = white; at(0.1f, 0f, 0.32f) { scale(0.55f, 0.6f, 1f); sphere(0.28f, 16, 12) }
        at(0.12f, 0f, 0.72f) { scale(0.6f, 0.9f, 0.8f); sphere(0.12f, 12, 8) }
        col = black; for (s in intArrayOf(-1, 1)) at(0.2f, s * 0.05f, 0.76f) { sphere(0.022f, 6, 4) }
        col = c(255, 170, 30); at(0.2f, 0f, 0.7f) { rotY((PI / 2).toFloat()); cone(0.04f, 0.1f, 8) }
        for (s in intArrayOf(-1, 1)) at(0.06f, s * 0.1f, 0.02f) { scale(1.6f, 1f, 0.4f); sphere(0.06f, 8, 6) }
        col = black; for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * 0.24f, 0.4f); rotX(s * 0.3f); scale(0.35f, 0.12f, 1f); sphere(0.2f, 10, 8); pop() }
        col = c(220, 40, 40); at(z = 0.6f) { torus(0.16f, 0.03f, 18, 6) }                                    // scarf
        tube(listOf(Vec3(-0.12f, 0.1f, 0.6f), Vec3(-0.2f, 0.12f, 0.45f)), 0.03f, 6)
    }.build()

    private fun palmTree() = PM().apply {
        col = c(150, 110, 70)
        tube((0..8).map { k -> val v = k / 8f; Vec3(0.12f * sin(v * 2.5f), 0f, v * 0.85f) }, 0.05f, 10, colorFn = { v -> if ((v * 14f).toInt() % 2 == 0) c(150, 110, 70) else c(120, 86, 54) }, taper = { 1f - 0.35f * it })
        col = c(60, 150, 50)
        for (k in 0 until 7) {
            val a = k * 0.9f
            tube((0..5).map { j -> val v = j / 5f; Vec3(0.12f * sin(2.5f) + cos(a) * v * 0.42f, sin(a) * v * 0.42f, 0.85f + 0.12f * v - 0.3f * v * v) }, 0.035f, 5, taper = { 1f - 0.8f * it })
        }
        col = c(120, 80, 40); for (k in 0 until 3) at(0.12f * sin(2.5f) + 0.05f * cos(k * 2.1f), 0.05f * sin(k * 2.1f), 0.8f) { sphere(0.04f, 8, 6) }
    }.build()

    private fun torch() = PM().apply {
        col = c(110, 80, 40); cylinder(0.035f, 0.7f, 10, 0.05f)
        col = c(214, 168, 70); at(z = 0.66f) { cylinder(0.09f, 0.1f, 12, 0.11f) }
        col = c(60, 40, 30); at(z = 0.76f) { cylinder(0.08f, 0.02f, 12) }
        col = c(214, 168, 70); cylinder(0.12f, 0.04f, 12)
    }.build()

    private fun scarecrow() = PM().apply {
        col = c(130, 96, 60); cylinder(0.03f, 0.9f, 8)
        push(); translate(0f, -0.35f, 0.62f); rotX(-(PI / 2).toFloat()); cylinder(0.025f, 0.7f, 8); pop()
        col = c(70, 110, 160); at(z = 0.5f) { scale(0.6f, 1f, 1.1f); sphere(0.16f, 12, 8) }                        // shirt
        col = c(170, 70, 40); for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * 0.22f, 0.62f); rotX(s * (PI / 2).toFloat()); cylinder(0.05f, 0.14f, 8); pop() }
        col = c(230, 200, 140); at(z = 0.8f) { sphere(0.11f, 12, 8) }
        col = c(20, 20, 20); for (s in intArrayOf(-1, 1)) at(0.1f, s * 0.04f, 0.83f) { sphere(0.014f, 6, 4) }
        col = c(150, 100, 50); at(z = 0.88f) { cylinder(0.2f, 0.015f, 18) }; at(z = 0.89f) { cylinder(0.1f, 0.1f, 14, 0.08f) }
        col = c(230, 190, 80); for (k in 0 until 8) { val a = k * 0.8f; tube(listOf(Vec3(0f, 0f, 0.36f), Vec3(cos(a) * 0.08f, sin(a) * 0.08f, 0.28f)), 0.008f, 3) }
    }.build()

    private fun jackInBox() = PM().apply {
        col = c(60, 120, 230); push(); translate(0f, 0f, 0.2f); box(0.4f, 0.4f, 0.4f); pop()
        col = c(250, 214, 40); for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * 0.201f, 0.2f); box(0.3f, 0.005f, 0.3f); pop() }
        col = c(230, 50, 50); push(); translate(0f, 0.22f, 0.42f); rotX(-1.2f); translate(0f, -0.2f, 0f); box(0.42f, 0.4f, 0.02f); pop()   // open lid
        col = c(200, 200, 210); for (k in 0 until 6) at(z = 0.42f + k * 0.05f) { torus(0.06f, 0.012f, 12, 4) }
        col = c(250, 220, 190); at(z = 0.78f) { sphere(0.12f, 14, 10) }
        col = c(230, 40, 40); at(0.11f, 0f, 0.78f) { sphere(0.03f, 8, 6) }
        col = c(120, 60, 200); at(z = 0.88f) { cone(0.1f, 0.18f, 12) }; col = c(250, 214, 40); at(z = 1.06f) { sphere(0.03f, 8, 6) }
    }.build()

    private fun spinningTop() = PM().apply {
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.1f, 0.08f), floatArrayOf(0.34f, 0.3f), floatArrayOf(0.32f, 0.38f), floatArrayOf(0.12f, 0.46f), floatArrayOf(0.04f, 0.5f), floatArrayOf(0.04f, 0.7f), floatArrayOf(0f, 0.72f)), 24) { u, v ->
            if (v > 0.6f) c(250, 214, 40) else if ((u * 8f).toInt() % 2 == 0) c(230, 50, 50) else c(250, 250, 250) }
    }.build()

    private fun drill() = PM().apply {
        // cordless drill standing on its battery
        col = c(30, 30, 34); push(); translate(0f, 0f, 0.07f); box(0.34f, 0.22f, 0.14f); pop()
        col = c(255, 160, 20); push(); translate(-0.02f, 0f, 0.36f); rotY(0.25f); box(0.12f, 0.12f, 0.46f); pop()     // grip
        col = c(255, 160, 20); push(); translate(0.06f, 0f, 0.66f); scale(1.6f, 0.9f, 0.9f); sphere(0.12f, 14, 10); pop()
        col = c(30, 30, 34); at(0.06f, 0f, 0.74f) { cylinder(0.055f, 0.1f, 14, 0.04f) }                                // chuck, pointing up
        col = c(200, 40, 40); push(); translate(0.04f, 0f, 0.48f); box(0.03f, 0.05f, 0.06f); pop()                        // trigger
    }.build()
    private fun drillBit() = PM().apply {
        col = c(190, 194, 204)
        cylinder(0.022f, 0.24f, 8, 0.008f)
        col = c(150, 154, 166); for (k in 0 until 6) at(z = 0.03f + k * 0.032f) { rotX(0.6f); torus(0.024f, 0.006f, 8, 3) }
    }.build()

    private fun lavaPool() = PM().apply {
        col = c(40, 30, 32); lathe(listOf(floatArrayOf(0.2f, 0f), floatArrayOf(0.44f, 0f), floatArrayOf(0.42f, 0.08f), floatArrayOf(0.32f, 0.1f), floatArrayOf(0.3f, 0.06f)), 18, false)
        col = c(255, 100, 20) * 2.3f; at(z = 0.05f) { cylinder(0.31f, 0.01f, 22) }
        col = c(255, 180, 50) * 2.6f; for (k in 0 until 4) at(cos(k * 1.7f) * 0.15f, sin(k * 1.7f) * 0.15f, 0.06f) { scale(1f, 1f, 0.5f); sphere(0.05f, 8, 6) }
    }.build()

    private fun moonRover() = PM().apply {
        col = c(220, 224, 232); push(); translate(0f, 0f, 0.22f); box(0.5f, 0.32f, 0.1f); pop()
        col = c(230, 180, 60); push(); translate(0f, 0f, 0.29f); box(0.46f, 0.28f, 0.03f); pop()
        col = c(60, 64, 76); for (x in floatArrayOf(-0.2f, 0f, 0.2f)) for (s in intArrayOf(-1, 1)) { push(); translate(x, s * 0.2f, 0.09f); rotX((PI / 2).toFloat()); translate(0f, 0f, -0.03f); cylinder(0.08f, 0.06f, 14); pop() }
        col = c(200, 204, 214); tube(listOf(Vec3(0.15f, 0f, 0.3f), Vec3(0.18f, 0f, 0.55f)), 0.012f, 6)
        push(); translate(0.18f, 0f, 0.58f); rotY(-0.6f); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.12f, 0.04f), floatArrayOf(0.14f, 0.06f)), 18, true); pop()
        col = c(80, 255, 140) * 1.8f; at(-0.15f, 0.1f, 0.33f) { sphere(0.025f, 6, 4) }
        col = c(40, 80, 160); push(); translate(-0.1f, 0f, 0.35f); rotY(0.2f); box(0.2f, 0.36f, 0.01f); pop()
    }.build()

    private fun mummy() = PM().apply {
        // a sarcophagus standing upright with a gold mask
        col = c(200, 160, 70)
        lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.18f, 0f), floatArrayOf(0.22f, 0.4f), floatArrayOf(0.2f, 0.7f), floatArrayOf(0.15f, 0.9f), floatArrayOf(0f, 0.95f)), 18) { _, v ->
            if (v in 0.3f..0.65f && ((v * 30f).toInt() % 2 == 0)) c(40, 100, 180) else c(200, 160, 70) }
        col = c(255, 214, 90) * 1.1f; at(0f, -0.15f, 0.78f) { scale(1f, 0.6f, 1.1f); sphere(0.1f, 12, 8) }
        col = c(20, 20, 30); for (s in intArrayOf(-1, 1)) at(s * 0.035f, -0.21f, 0.8f) { sphere(0.014f, 6, 4) }
        col = c(40, 100, 180); for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.09f, -0.13f, 0.6f); rotY(s * 0.3f); box(0.06f, 0.04f, 0.26f); pop() }
    }.build()

    // ---------------------------------------------------------------- jungle (rich pass)
    /** Adds a long drooping leaf from (0,0,z0) heading out at angle [a]: a ribbon with a midrib, curving down at the tip. */
    private fun PM.leaf(len: Float, wid: Float, cA: Vec3, cB: Vec3, droop: Float, a: Float, z0: Float, rise: Float = 0.5f) {
        val n = 7
        val ca = cos(a); val sa = sin(a)
        val pts = (0..n).map { k -> val v = k / n.toFloat(); Vec3(ca * len * v, sa * len * v, z0 + rise * len * v - droop * len * v * v) }
        for (k in 0 until n) {
            val v0 = k / n.toFloat(); val v1 = (k + 1) / n.toFloat()
            val w0 = wid * sin(PI.toFloat() * (0.08f + 0.9f * v0)); val w1 = wid * sin(PI.toFloat() * (0.08f + 0.9f * v1))
            val side = Vec3(-sa, ca, 0f)
            val c0 = Vec3.lerp(cA, cB, v0); val c1 = Vec3.lerp(cA, cB, v1)
            flat(pts[k] - side * w0, pts[k + 1] - side * w1, pts[k + 1] + side * w1, pts[k] + side * w0, c0, c1, c1, c0)
        }
        col = cA * 0.7f; tube(pts, wid * 0.08f + 0.004f, 3)
    }

    private fun bananaPlant() = PM().apply {
        col = c(120, 150, 70); tube(listOf(Vec3(0f, 0f, 0f), Vec3(0.01f, 0f, 0.35f), Vec3(0f, 0.01f, 0.55f)), 0.06f, 10, taper = { 1f - 0.4f * it })
        for (i in 0 until 7) leaf(0.55f + 0.1f * (i % 3), 0.11f, c(70, 150, 50), c(130, 200, 70), 1.1f, i * 0.9f, 0.5f + 0.03f * i, 0.6f)
        col = c(250, 214, 60); for (k in 0 until 6) at(0.08f, 0.02f * k - 0.05f, 0.4f - 0.03f * k) { push(); rotY(0.6f); scale(0.35f, 0.35f, 1f); cylinder(0.05f, 0.14f, 8, 0.03f); pop() }
        col = c(140, 40, 80); at(0.1f, 0f, 0.26f) { scale(0.8f, 0.8f, 1.2f); sphere(0.05f, 8, 6) }
    }.build()

    private fun bromeliad() = PM().apply {
        for (i in 0 until 12) leaf(0.32f + 0.05f * (i % 2), 0.05f, c(60, 130, 60), c(200, 60, 80), 0.6f, i * 0.524f, 0.02f, 1.1f)
        col = c(255, 70, 60) * 1.15f; at(z = 0.04f) { cone(0.07f, 0.28f, 10) }
        col = c(255, 200, 40); for (k in 0 until 5) at(cos(k * 1.26f) * 0.04f, sin(k * 1.26f) * 0.04f, 0.18f + 0.02f * k) { sphere(0.015f, 6, 4) }
    }.build()

    private fun bamboo() = PM().apply {
        for (s in 0 until 5) {
            val a = s * 1.26f; val r = if (s == 0) 0f else 0.12f; val h = 0.75f + 0.3f * ((s * 7) % 4) / 3f
            val x = cos(a) * r; val y = sin(a) * r
            col = c(140, 180, 70)
            val segs = 6
            for (k in 0 until segs) {
                val z0 = h * k / segs; val z1 = h * (k + 1) / segs
                col = if (k % 2 == 0) c(140, 180, 70) else c(150, 190, 80)
                at(x + 0.02f * k * cos(a), y + 0.02f * k * sin(a), z0) { cylinder(0.035f, z1 - z0, 10) }
                col = c(110, 140, 50); at(x + 0.02f * (k + 1) * cos(a), y + 0.02f * (k + 1) * sin(a), z1) { torus(0.037f, 0.008f, 10, 3) }
            }
            for (k in 0 until 3) leaf(0.2f, 0.035f, c(80, 160, 60), c(150, 210, 90), 0.6f, a + k * 2.1f, h * (0.55f + 0.15f * k), 0.2f)
        }
    }.build()

    private fun monstera() = PM().apply {
        // big split heart-shaped leaves on long stalks
        for (i in 0 until 5) {
            val a = i * 1.26f + 0.3f; val hgt = 0.35f + 0.1f * (i % 3)
            col = c(70, 130, 50); tube(listOf(Vec3(0f, 0f, 0f), Vec3(cos(a) * 0.1f, sin(a) * 0.1f, hgt * 0.7f), Vec3(cos(a) * 0.2f, sin(a) * 0.2f, hgt)), 0.012f, 5)
            push(); translate(cos(a) * 0.2f, sin(a) * 0.2f, hgt); rotZ(a); rotY(0.5f)
            for (k in 0 until 5) {                           // the leaf, cut into lobes by the gaps between them
                val b = -1.1f + k * 0.55f
                col = Vec3.lerp(c(30, 110, 40), c(70, 160, 60), k / 4f)
                push(); rotZ(b * 0.5f); translate(0.12f, 0f, 0f); scale(1f, 0.32f, 0.04f); sphere(0.13f, 10, 6); pop()
            }
            pop()
        }
    }.build()

    private fun heliconia() = PM().apply {
        col = c(80, 140, 60); cylinder(0.02f, 0.7f, 8)
        for (k in 0 until 7) {
            val z = 0.35f + k * 0.055f; val s = if (k % 2 == 0) 1f else -1f
            col = if (k % 2 == 0) c(240, 50, 40) * 1.1f else c(250, 190, 40) * 1.1f
            push(); translate(s * 0.04f, 0f, z); rotY(s * 1.0f); scale(0.3f, 0.6f, 1f); cone(0.06f, 0.16f, 8); pop()
        }
        for (i in 0 until 3) leaf(0.4f, 0.08f, c(50, 130, 50), c(110, 190, 70), 0.9f, i * 2.1f + 0.4f, 0.15f + 0.08f * i, 0.8f)
    }.build()

    /** A green tree python hanging from a branch: built downward from the origin so it can sway like a pendulum. */
    private fun snake() = PM().apply {
        val body = (0..16).map { k -> val v = k / 16f
            if (v < 0.35f) { val a = v / 0.35f * 4.4f; Vec3(cos(a) * 0.06f, sin(a) * 0.06f, -0.02f - v * 0.1f) }     // coiled round the branch
            else Vec3(0.05f * sin(v * 9f), 0.04f * cos(v * 7f), -0.06f - (v - 0.35f) * 0.75f) }
        tube(body, 0.026f, 8, colorFn = { v -> if (((v * 22f).toInt()) % 3 == 0) c(250, 230, 70) else c(60, 170, 60) }, taper = { if (it < 0.85f) 1f else 1f - (it - 0.85f) * 3f })
        val head = body.last()
        col = c(70, 180, 60); at(head.x, head.y, head.z - 0.03f) { scale(1.4f, 1f, 0.8f); sphere(0.035f, 10, 8) }
        col = c(20, 20, 20); for (s in intArrayOf(-1, 1)) at(head.x + 0.03f, head.y + s * 0.02f, head.z - 0.02f) { sphere(0.008f, 5, 4) }
        col = c(220, 40, 40); tube(listOf(Vec3(head.x + 0.045f, head.y, head.z - 0.04f), Vec3(head.x + 0.085f, head.y, head.z - 0.05f)), 0.003f, 3)
    }.build()

    private fun bigJungleTree() = PM().apply {
        // buttress roots, a leaning banded trunk, branches with layered leafy canopy and hanging vines
        val bark = c(96, 72, 48)
        for (i in 0 until 5) { val a = i * 1.26f; col = bark * 0.9f; tube(listOf(Vec3(cos(a) * 0.22f, sin(a) * 0.22f, 0f), Vec3(cos(a) * 0.1f, sin(a) * 0.1f, 0.12f), Vec3(cos(a) * 0.04f, sin(a) * 0.04f, 0.26f)), 0.03f, 6, taper = { 1f - 0.5f * it }) }
        tube(listOf(Vec3(0f, 0f, 0f), Vec3(0.04f, 0f, 0.25f), Vec3(-0.02f, 0.02f, 0.5f), Vec3(0.02f, 0f, 0.72f)), 0.075f, 12,
            colorFn = { v -> if (((v * 30f).toInt()) % 4 == 0) bark * 0.75f else bark }, taper = { 1f - 0.45f * it })
        for (b in 0 until 3) { val a = b * 2.1f + 0.4f; col = bark; tube(listOf(Vec3(0f, 0f, 0.62f), Vec3(cos(a) * 0.14f, sin(a) * 0.14f, 0.72f), Vec3(cos(a) * 0.26f, sin(a) * 0.26f, 0.76f)), 0.025f, 6, taper = { 1f - 0.5f * it }) }
        for (i in 0 until 9) {
            val a = i * 0.7f; val r = 0.08f + 0.2f * ((i * 5) % 7) / 6f; val z = 0.78f + 0.05f * (i % 4)
            col = Vec3.lerp(c(30, 100, 40), c(100, 180, 60), ((i * 3) % 5) / 4f)
            at(cos(a) * r, sin(a) * r, z) { push(); scale(1f, 1f, 0.45f); sphere(0.17f, 12, 8); pop() }
        }
        col = c(50, 130, 50)
        for (i in 0 until 6) { val a = i * 1.05f + 0.2f; val r = 0.24f
            tube((0..5).map { k -> val v = k / 5f; Vec3(cos(a) * r + 0.02f * sin(v * 9f), sin(a) * r, 0.78f - 0.5f * v) }, 0.007f, 3)
            col = c(80, 170, 60); for (k in 1..4) at(cos(a) * r, sin(a) * r, 0.78f - 0.12f * k) { scale(1f, 0.5f, 0.2f); sphere(0.022f, 6, 4) }
            col = c(50, 130, 50) }
        col = c(250, 70, 140); for (k in 0 until 4) at(cos(k * 1.6f) * 0.12f, sin(k * 1.6f) * 0.12f, 0.98f) { sphere(0.02f, 6, 4) }
    }.build()

    private fun toucan(): Bird {
        val body = PM().apply {
            col = c(24, 24, 28); at { scale(1f, 0.6f, 0.6f); sphere(0.12f, 12, 8) }
            col = c(250, 240, 200); at(0.11f, 0f, 0.0f) { scale(0.6f, 0.7f, 0.7f); sphere(0.07f, 10, 8) }
            col = c(24, 24, 28); at(0.13f, 0f, 0.04f) { sphere(0.055f, 10, 8) }
            col = c(255, 150, 20); push(); translate(0.17f, 0f, 0.04f); rotY((PI / 2).toFloat() + 0.15f); scale(0.7f, 0.9f, 1f); cone(0.045f, 0.2f, 10); pop()
            col = c(230, 40, 30); at(0.35f, 0f, 0.02f) { sphere(0.012f, 6, 4) }
            col = c(24, 24, 28); push(); translate(-0.15f, 0f, 0f); rotY((PI / 2).toFloat() + 0.25f); scale(0.5f, 1f, 1f); cylinder(0.05f, 0.16f, 8, 0.02f); pop()
        }.build()
        val (l, r) = wingPair(0.28f, 0.15f, c(24, 24, 28), c(30, 30, 36), 0.5f)
        return Bird(body, l, r, 4, 0.6f, 2.0f, 7f, glide = true)
    }

    // ---------------------------------------------------------------- stadium
    /** Concrete stepped grandstand facing +y, with a roof on posts and advertising boards. The crowd is separate (animated rows). */
    private fun grandstand(seatA: Vec3, seatB: Vec3, board: Vec3) = PM().apply {
        val conc = c(170, 172, 180)
        for (r in 0 until 4) {                           // four tiers, each a step up and back
            val top = 0.1f + 0.16f * r
            col = conc; push(); translate(0f, 0.18f - r * 0.13f, top * 0.5f); box(1.0f, 0.13f, top); pop()
            col = if (r % 2 == 0) seatA else seatB
            for (k in 0 until 9) { push(); translate(-0.44f + k * 0.11f, 0.18f - r * 0.13f + 0.03f, top + 0.015f); box(0.09f, 0.05f, 0.03f); pop() }
        }
        col = conc * 0.8f; push(); translate(0f, -0.25f, 0.3f); box(1.0f, 0.06f, 0.6f); pop()                    // back wall
        col = c(80, 84, 94); for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.49f, -0.25f, 0.42f); box(0.03f, 0.03f, 0.84f); pop() }
        col = c(230, 232, 238); push(); translate(0f, -0.06f, 0.86f); rotX(-0.18f); box(1.08f, 0.5f, 0.025f); pop()        // roof
        col = board; push(); translate(0f, 0.33f, 0.05f); box(1.0f, 0.02f, 0.1f); pop()
        col = c(250, 250, 250); for (k in 0 until 4) { push(); translate(-0.36f + k * 0.24f, 0.341f, 0.05f); box(0.12f, 0.004f, 0.04f); pop() }
    }.build()

    /** One row of spectators, to sit on a tier and be animated to cheer and do the wave. Each fan has shoulders and a shirt,
     *  a head with hair, a cap or a beanie, two arms (some waving, some clapping, some holding a flag or a team scarf
     *  overhead) and a slightly different size, so the rows never look cloned. */
    private fun crowdRow(seed: Int = 0) = PM().apply {
        val shirts = listOf(c(220, 40, 40), c(40, 90, 220), c(250, 214, 40), c(245, 245, 245), c(40, 160, 70), c(240, 120, 30), c(30, 32, 40), c(150, 60, 170))
        val skins = listOf(c(244, 206, 170), c(222, 176, 136), c(190, 140, 100), c(150, 100, 70), c(108, 70, 48))
        val hairs = listOf(c(30, 24, 20), c(90, 60, 30), c(200, 160, 90), c(150, 70, 30), c(120, 120, 124))
        val team = listOf(c(220, 30, 40), c(250, 250, 250))
        fun h(k: Int, salt: Int) = ((((seed * 7919 + k * 131 + salt * 977) * 2654435761L) ushr 9) % 1000).toInt() / 1000f
        for (k in 0 until 9) {
            val x = -0.44f + k * 0.11f + (h(k, 1) - 0.5f) * 0.02f
            val sc = 0.88f + 0.22f * h(k, 2)                                     // kids and grown-ups
            val shirt = shirts[(h(k, 3) * shirts.size).toInt().coerceAtMost(shirts.size - 1)]
            val skin = skins[(h(k, 4) * skins.size).toInt().coerceAtMost(skins.size - 1)]
            push(); translate(x, 0f, 0f); scale(sc)
            col = shirt; at(z = 0.045f) { scale(1.15f, 0.7f, 1.0f); sphere(0.036f, 10, 8) }                 // chest
            at(z = 0.075f) { scale(1.5f, 0.75f, 0.5f); sphere(0.028f, 10, 6) }                              // shoulders
            col = skin; at(z = 0.096f) { cylinder(0.011f, 0.014f, 6) }                                    // neck
            at(z = 0.122f) { scale(0.92f, 0.9f, 1.05f); sphere(0.024f, 10, 8) }                             // head
            at(0.0f, 0.021f, 0.122f) { sphere(0.006f, 5, 4) }                                            // nose
            val hat = h(k, 5)
            when {
                hat < 0.25f -> { col = team[k % 2]; at(z = 0.132f) { scale(1f, 1f, 0.75f); sphere(0.026f, 10, 6) }        // beanie
                    col = team[(k + 1) % 2]; at(z = 0.16f) { sphere(0.009f, 6, 4) } }
                hat < 0.45f -> { col = shirts[(k * 3) % shirts.size]; at(z = 0.135f) { scale(1f, 1f, 0.6f); sphere(0.025f, 10, 6) }  // cap
                    at(0f, 0.025f, 0.128f) { box(0.034f, 0.024f, 0.005f) } }
                else -> { col = hairs[(h(k, 6) * hairs.size).toInt().coerceAtMost(hairs.size - 1)]
                    at(0f, -0.004f, 0.13f) { scale(1f, 1f, 0.82f); sphere(0.026f, 10, 6) } }                         // hair
            }
            if (h(k, 7) < 0.3f) { col = team[0]; at(z = 0.09f) { torus(0.022f, 0.008f, 12, 5) } }                    // team scarf
            // arms: pose by fan
            val pose = h(k, 8)
            col = shirt
            for (sd in intArrayOf(-1, 1)) {
                val sh = Vec3(sd * 0.04f, 0f, 0.075f)
                val hand: Vec3 = when {
                    pose < 0.28f -> Vec3(sd * 0.06f, 0.01f, 0.2f)                         // both arms up, cheering
                    pose < 0.45f && sd == 1 -> Vec3(0.07f, 0.01f, 0.19f)                   // one arm waving
                    pose < 0.62f -> Vec3(sd * 0.008f, 0.04f, 0.095f)                       // clapping in front
                    else -> Vec3(sd * 0.045f, 0.03f, 0.02f)                                // arms down, resting
                }
                val elbow = (sh + hand) * 0.5f + Vec3(sd * 0.018f, 0.005f, 0f)
                tube(listOf(sh, elbow, hand), 0.01f, 5)
                col = skin; at(hand.x, hand.y, hand.z) { sphere(0.011f, 6, 4) }; col = shirt
            }
            if (pose < 0.28f && h(k, 9) < 0.45f) {                                       // a scarf held up between both hands
                col = team[0]; at(0f, 0.012f, 0.205f) { box(0.12f, 0.004f, 0.022f) }
                col = team[1]; at(0f, 0.014f, 0.205f) { box(0.03f, 0.004f, 0.022f) }
            }
            if (pose in 0.28f..0.45f && h(k, 10) < 0.6f) {                               // a flag on a pole
                col = c(200, 200, 205); tube(listOf(Vec3(0.07f, 0.01f, 0.17f), Vec3(0.07f, 0.01f, 0.32f)), 0.003f, 4)
                for (q in 0 until 6) {                                                   // a waving flag: curved and drooping a little towards the fly
                    val u0 = q / 6f; val u1 = (q + 1) / 6f
                    fun fy(u: Float) = 0.01f + 0.012f * u * sin(u * 7f + k)
                    fun fz(u: Float) = -0.012f * u * u
                    val x0 = 0.072f + 0.08f * u0; val x1 = 0.072f + 0.08f * u1
                    col = if (q in 2..3) team[1] else team[0]
                    flat(Vec3(x0, fy(u0), 0.26f + fz(u0)), Vec3(x1, fy(u1), 0.26f + fz(u1)), Vec3(x1, fy(u1), 0.32f + fz(u1)), Vec3(x0, fy(u0), 0.32f + fz(u0)))
                }
            }
            pop()
        }
    }.build()
    private val crowdRowMesh by lazy { crowdRow(1) }
    // alternate tiers sit half a seat apart so the same row of fans never lines up in columns
    private fun crowdSpec(host: PropMesh) = part(host, crowdRowMesh, Anim.CROWD, (0 until 4).map { r -> Vec3(if (r % 2 == 1) 0.055f else 0f, 0.18f - r * 0.13f - 0.01f, 0.13f + 0.16f * r) }, 1f, 1f)

    // ---------------------------------------------------------------- sweets
    private fun fruitPastilles() = PM().apply {
        // a little stack of sugar-dusted fruit gums in different flavours
        val cols = listOf(c(230, 40, 50), c(250, 160, 30), c(130, 200, 50), c(250, 220, 60), c(150, 60, 170))
        for (i in 0 until 6) {
            val a = i * 1.1f; val r = if (i < 3) 0.12f else 0.06f; val z = if (i < 3) 0f else 0.11f + 0.11f * (i - 3) * 0.5f
            col = cols[i % cols.size] * 1.1f
            push(); translate(cos(a) * r, sin(a) * r, z); rotX(0.15f * (i % 2)); lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.13f, 0.005f), floatArrayOf(0.15f, 0.05f), floatArrayOf(0.13f, 0.1f), floatArrayOf(0f, 0.105f)), 18) { u, v -> cols[i % cols.size] * (if (((u * 41f + v * 17f) * 13f % 1f) > 0.85f) 1.5f else 1.1f) }
            pop()
        }
    }.build()

    private fun wrappedSweet() = PM().apply {
        // a boiled sweet in a twisted foil wrapper
        col = c(250, 60, 120); push(); translate(0f, 0f, 0.16f); rotY((PI / 2).toFloat()); scale(1f, 1f, 1.4f); sphere(0.15f, 16, 10); pop()
        col = c(255, 255, 255); for (s in intArrayOf(-1, 1)) { push(); translate(s * 0.2f, 0f, 0.16f); rotY(s * (PI / 2).toFloat()); cone(0.04f, 0.04f, 10); pop()
            push(); translate(s * 0.24f, 0f, 0.16f); rotY(-s * (PI / 2).toFloat()); lathe(listOf(floatArrayOf(0.01f, 0f), floatArrayOf(0.14f, 0.12f), floatArrayOf(0.12f, 0.13f)), 10, false) { u, _ -> if ((u * 10f).toInt() % 2 == 0) c(255, 220, 240) else c(250, 60, 120) }; pop() }
        col = c(255, 230, 245); for (k in 0 until 4) at(0f, -0.13f, 0.12f + k * 0.03f) { box(0.18f, 0.006f, 0.008f) }
    }.build()

    private fun liquoriceWheel() = PM().apply {
        // a liquorice wheel with a pink sweet in the middle, standing on its edge
        push(); translate(0f, 0f, 0.3f); rotX((PI / 2).toFloat()); translate(0f, 0f, -0.06f)
        col = c(20, 18, 22); for (k in 0 until 8) torus(0.05f + k * 0.03f, 0.016f, 24, 5)
        col = c(255, 120, 180) * 1.1f; cylinder(0.05f, 0.12f, 14)
        pop()
        col = c(20, 18, 22); push(); translate(0f, 0f, 0.02f); box(0.2f, 0.14f, 0.04f); pop()
    }.build()

    private fun candyCorn() = PM().apply {
        for (i in 0 until 3) { val a = i * 2.1f
            push(); translate(cos(a) * 0.12f, sin(a) * 0.12f, 0f); rotZ(a)
            lathe(listOf(floatArrayOf(0f, 0f), floatArrayOf(0.12f, 0.0f), floatArrayOf(0.1f, 0.15f), floatArrayOf(0.05f, 0.32f), floatArrayOf(0f, 0.36f)), 10) { _, v -> if (v < 0.35f) c(255, 220, 60) else if (v < 0.7f) c(255, 140, 30) else c(255, 255, 250) }
            pop() }
    }.build()

    // ---------------------------------------------------------------- motorsport sponsor boards and more dinosaurs
    /** An advertising board standing at the foot of a wall (facing +x), in sponsor colours with a block-letter logo. */
    fun sponsorBoard(k: Int) = PM().apply {
        val bgs = listOf(c(220, 30, 30), c(30, 70, 200), c(250, 210, 30), c(20, 20, 24), c(30, 150, 70))
        val fgs = listOf(c(250, 250, 250), c(250, 250, 250), c(20, 20, 24), c(250, 200, 30), c(250, 250, 250))
        val i = k % bgs.size
        col = c(60, 64, 72); for (s in intArrayOf(-1, 1)) at(-0.02f, s * 0.4f, 0f) { box(0.03f, 0.03f, 0.3f) }
        col = bgs[i]; at(0f, 0f, 0.17f) { box(0.03f, 0.9f, 0.24f) }
        col = fgs[i]
        val pattern = (k * 7919 + 13) % 997
        for (j in 0 until 6) {
            val tall = ((pattern shr j) and 1) == 1
            at(0.017f, -0.3f + j * 0.12f, 0.17f) { box(0.004f, 0.08f, if (tall) 0.14f else 0.08f) }
        }
        col = fgs[i] * 0.9f; at(0.017f, 0f, 0.07f) { box(0.004f, 0.8f, 0.015f) }
    }.build()

    private fun raptor() = PM().apply {
        // Velociraptor: green back, teal flanks, yellow belly and legs; big head with open toothy jaws, S-shaped neck,
        // long stiff tail held out level, clawed arms with a fringe of teal feathers, strong legs with raised sickle claws
        val green = c(80, 158, 52); val teal = c(40, 112, 108); val yel = c(214, 204, 72); val claw = c(110, 116, 120)
        col = green; at(0f, 0f, 0.46f) { rotY(-0.2f); scale(1.3f, 0.62f, 0.72f); sphere(0.17f, 16, 12) }        // body
        col = yel; at(0.03f, 0f, 0.4f) { rotY(-0.2f); scale(1.1f, 0.5f, 0.45f); sphere(0.16f, 14, 10) }          // belly
        col = teal; for (sgn in intArrayOf(-1, 1)) at(-0.02f, sgn * 0.045f, 0.45f) { rotY(-0.2f); scale(1.3f, 0.5f, 0.7f); sphere(0.11f, 12, 8) }   // flanks
        col = green; tube(listOf(Vec3(0.17f, 0f, 0.5f), Vec3(0.25f, 0f, 0.6f), Vec3(0.26f, 0f, 0.7f), Vec3(0.3f, 0f, 0.77f)), 0.058f, 12,
            colorFn = { v -> if (v > 0.5f) teal else green }, taper = { 1f - 0.2f * it })                          // neck
        col = teal; at(0.39f, 0f, 0.8f) { rotY(0.08f); scale(2.1f, 0.78f, 0.82f); sphere(0.07f, 16, 12) }      // skull
        col = green; at(0.34f, 0f, 0.84f) { scale(1.5f, 0.75f, 0.6f); sphere(0.06f, 12, 8) }                   // brow
        col = yel; push(); translate(0.37f, 0f, 0.75f); rotY(0.35f); scale(2.0f, 0.65f, 0.4f); sphere(0.06f, 14, 8); pop()   // lower jaw, open
        col = c(150, 40, 46); at(0.42f, 0f, 0.76f) { scale(2f, 0.5f, 0.35f); sphere(0.045f, 10, 6) }           // mouth
        col = c(250, 246, 232)
        for (k in 0 until 8) for (sgn in intArrayOf(-1, 1)) {
            at(0.36f + k * 0.022f, sgn * 0.035f, 0.775f) { rotX(PI.toFloat()); cone(0.006f, 0.022f, 4) }       // upper teeth
            if (k < 6) at(0.36f + k * 0.022f, sgn * 0.03f, 0.745f - k * 0.008f) { cone(0.005f, 0.016f, 4) }  // lower teeth
        }
        for (sgn in intArrayOf(-1, 1)) {
            col = c(220, 120, 40); at(0.36f, sgn * 0.05f, 0.83f) { sphere(0.012f, 8, 6) }
            col = c(20, 16, 14); at(0.368f, sgn * 0.056f, 0.832f) { sphere(0.006f, 6, 4) }
        }
        col = green; tube((0..10).map { k -> val v = k / 10f; Vec3(-0.18f - 0.62f * v, 0f, 0.48f + 0.08f * v + 0.04f * kotlin.math.sin(v * 3f)) }, 0.07f, 12,
            colorFn = { v -> if (v > 0.6f) yel * 0.95f else green }, taper = { 1f - 0.92f * it })                 // tail
        for (sgn in intArrayOf(-1, 1)) {
            col = yel; tube(listOf(Vec3(0.15f, sgn * 0.08f, 0.47f), Vec3(0.24f, sgn * 0.11f, 0.42f), Vec3(0.3f, sgn * 0.11f, 0.36f)), 0.022f, 8)   // arm
            col = teal; for (f in 0 until 4) { val t = f / 3f
                flat(Vec3(0.16f + 0.13f * t, sgn * (0.085f + 0.025f * t), 0.46f - 0.08f * t), Vec3(0.18f + 0.13f * t, sgn * (0.09f + 0.025f * t), 0.45f - 0.08f * t),
                     Vec3(0.15f + 0.13f * t, sgn * (0.1f + 0.025f * t), 0.38f - 0.08f * t), Vec3(0.13f + 0.13f * t, sgn * (0.095f + 0.025f * t), 0.39f - 0.08f * t)) }   // feathers
            col = claw; for (f in 0 until 3) at(0.31f, sgn * (0.095f + f * 0.016f), 0.35f) { rotY(2.4f); cone(0.007f, 0.05f, 4) }
            col = green; at(-0.01f, sgn * 0.08f, 0.36f) { rotY(0.3f); scale(1f, 0.55f, 1.2f); sphere(0.075f, 12, 10) }   // thigh
            col = yel
            tube(listOf(Vec3(0.0f, sgn * 0.09f, 0.3f), Vec3(-0.07f, sgn * 0.095f, 0.15f), Vec3(-0.02f, sgn * 0.095f, 0.04f), Vec3(0.04f, sgn * 0.095f, 0.015f)), 0.032f, 10, taper = { 1f - 0.4f * it })
            for (t in -1..1) tube(listOf(Vec3(0.04f, sgn * 0.095f, 0.015f), Vec3(0.1f, sgn * (0.095f + t * 0.025f), 0.01f)), 0.013f, 6)   // toes
            col = claw; for (t in -1..1) at(0.11f, sgn * (0.095f + t * 0.025f), 0.01f) { rotY(1.9f); cone(0.008f, 0.03f, 4) }
            at(0.05f, sgn * 0.1f, 0.05f) { rotY(-0.6f); cone(0.011f, 0.06f, 5) }                                // raised sickle claw
        }
    }.build()

    private fun dinoSkeleton() = PM().apply {
        // a museum-style T. rex skeleton on a plinth: skull with jaw, teeth and eye holes; vertebrae shrinking down the tail;
        // curved ribs, pelvis, three-part legs with clawed feet, tiny arms and support rods
        val bone = c(236, 226, 200); val old = c(206, 190, 160)
        col = c(70, 66, 62); at(z = 0.02f) { box(1.05f, 0.42f, 0.04f) }
        col = c(150, 140, 120); at(z = 0.045f) { box(1.0f, 0.37f, 0.01f) }
        val spine = (0..22).map { k -> val v = k / 22f
            Vec3(-0.5f + 0.92f * v, 0f, 0.38f + 0.26f * kotlin.math.sin(v * 2.6f + 0.3f) - (if (v < 0.4f) (0.4f - v) * 0.5f else 0f)) }
        for ((k, p) in spine.withIndex()) { val r = 0.016f + 0.028f * (k / 22f).let { if (it < 0.75f) it else 0.75f }
            col = if (k % 2 == 0) bone else old; at(p.x, p.y, p.z) { scale(0.8f, 1f, 1.2f); sphere(r, 8, 6) }
            if (k in 8..18) { col = bone; at(p.x, 0f, p.z + r * 2f) { box(0.012f, 0.01f, r * 1.8f) } } }
        for (k in 9..15) { val p = spine[k]; val drop = 0.16f + 0.05f * (k - 9) * (15 - k) / 9f
            for (s in intArrayOf(-1, 1)) { col = bone; tube((0..5).map { j -> val a = j / 5f * 2.6f; Vec3(p.x - 0.01f * j, s * 0.13f * kotlin.math.sin(a), p.z - drop * (1f - kotlin.math.cos(a)) * 0.6f) }, 0.009f, 4) } }
        val pel = spine[7]
        col = bone; at(pel.x, 0f, pel.z - 0.04f) { scale(1.4f, 1f, 0.8f); sphere(0.06f, 10, 8) }
        for (s in intArrayOf(-1, 1)) {
            tube(listOf(Vec3(pel.x, s * 0.07f, pel.z - 0.04f), Vec3(pel.x + 0.06f, s * 0.08f, 0.32f), Vec3(pel.x - 0.02f, s * 0.08f, 0.17f), Vec3(pel.x + 0.03f, s * 0.08f, 0.07f)), 0.022f, 6, taper = { 1f - 0.35f * it })
            at(pel.x + 0.06f, s * 0.08f, 0.32f) { sphere(0.026f, 8, 6) }; at(pel.x - 0.02f, s * 0.08f, 0.17f) { sphere(0.02f, 8, 6) }
            for (t in -1..1) tube(listOf(Vec3(pel.x + 0.03f, s * 0.08f, 0.07f), Vec3(pel.x + 0.1f, s * 0.08f + t * 0.025f, 0.055f)), 0.007f, 3)
            val ch = spine[17]
            tube(listOf(Vec3(ch.x, s * 0.08f, ch.z - 0.1f), Vec3(ch.x + 0.05f, s * 0.09f, ch.z - 0.17f), Vec3(ch.x + 0.08f, s * 0.08f, ch.z - 0.15f)), 0.008f, 3)
        }
        col = c(60, 60, 66); at(pel.x, 0f, 0.05f) { cylinder(0.008f, pel.z - 0.1f, 6) }; at(spine[16].x, 0f, 0.05f) { cylinder(0.008f, spine[16].z - 0.1f, 6) }
        // skull
        val hd = spine.last()
        col = bone
        push(); translate(hd.x + 0.1f, 0f, hd.z + 0.01f)
        at(0f, 0f, 0.02f) { scale(1.7f, 0.75f, 0.85f); sphere(0.075f, 14, 10) }
        at(0.05f, 0f, -0.05f) { scale(1.8f, 0.6f, 0.35f); sphere(0.06f, 12, 8) }
        col = c(40, 34, 28); for (s in intArrayOf(-1, 1)) { at(-0.03f, s * 0.055f, 0.04f) { sphere(0.022f, 8, 6) }; at(0.06f, s * 0.04f, 0.02f) { scale(1.4f, 0.6f, 1f); sphere(0.018f, 6, 4) } }
        col = c(250, 246, 230); for (k in 0 until 7) { at(0.0f + k * 0.022f, -0.04f, -0.02f) { rotY(PI.toFloat()); cone(0.006f, 0.028f, 4) }; at(0.0f + k * 0.022f, 0.04f, -0.02f) { rotY(PI.toFloat()); cone(0.006f, 0.028f, 4) } }
        pop()
    }.build()

    private fun bigEggs() = PM().apply {
        col = c(130, 110, 80); at(z = 0.02f) { scale(1f, 1f, 0.25f); sphere(0.3f, 14, 8) }
        for (i in 0 until 3) { val a = i * 2.1f
            push(); translate(cos(a) * 0.13f, sin(a) * 0.13f, 0.18f); rotY(0.25f * (i - 1)); scale(0.75f, 0.75f, 1.15f)
            sphere(0.16f, 16, 12) { u, v -> if ((((u * 47f + v * 31f) * 9f) % 1f) > 0.88f) c(120, 140, 90) else c(214, 226, 190) }
            pop() }
        col = c(214, 226, 190); for (k in 0 until 4) at(0.25f + 0.04f * k, -0.1f, 0.02f) { rotZ(k * 0.9f); scale(1f, 0.6f, 0.3f); sphere(0.04f, 6, 4) }    // a hatched shell
    }.build()

    // ---------------------------------------------------------------- medieval
    private fun castle() = PM().apply {
        // a keep with four round corner towers, conical roofs, crenellations, a gatehouse and pennants
        val stone = c(160, 154, 142); val roof = c(60, 70, 120)
        col = stone; push(); translate(0f, 0f, 0.25f); box(0.56f, 0.56f, 0.5f); pop()
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            col = stone; at(sx * 0.3f, sy * 0.3f, 0f) { cylinder(0.11f, 0.68f, 16) }
            for (m in 0 until 6) { val a = m * 1.047f; at(sx * 0.3f + cos(a) * 0.1f, sy * 0.3f + sin(a) * 0.1f, 0.71f) { box(0.05f, 0.05f, 0.06f) } }
            col = roof; at(sx * 0.3f, sy * 0.3f, 0.74f) { cone(0.13f, 0.24f, 16) }
            col = c(70, 60, 50); tube(listOf(Vec3(sx * 0.3f, sy * 0.3f, 0.96f), Vec3(sx * 0.3f, sy * 0.3f, 1.08f)), 0.006f, 4)
        }
        col = stone; for (k in 0 until 7) for (s in intArrayOf(-1, 1)) { at(-0.24f + k * 0.08f, s * 0.28f, 0.53f) { box(0.045f, 0.03f, 0.06f) }; at(s * 0.28f, -0.24f + k * 0.08f, 0.53f) { box(0.03f, 0.045f, 0.06f) } }
        col = c(40, 30, 24); push(); translate(0f, -0.285f, 0.12f); box(0.14f, 0.02f, 0.22f); pop()                  // gate
        col = c(80, 80, 86); for (k in 0 until 4) { push(); translate(-0.05f + k * 0.033f, -0.29f, 0.12f); box(0.008f, 0.01f, 0.22f); pop() }   // portcullis
        col = c(255, 200, 100) * 1.4f; for (sx in intArrayOf(-1, 1)) at(sx * 0.15f, -0.285f, 0.36f) { box(0.03f, 0.01f, 0.06f) }               // lit windows
        col = c(110, 106, 98); push(); translate(0f, 0f, 0.5f); box(0.3f, 0.3f, 0.3f); pop()
        col = roof; at(z = 0.65f) { cone(0.22f, 0.3f, 4) }
    }.build()

    private fun swordInStone() = PM().apply {
        col = c(120, 116, 108); at(z = 0.1f) { scale(1.2f, 1f, 0.6f); sphere(0.26f, 12, 8) }
        col = c(200, 206, 216); push(); translate(0f, 0f, 0.2f); box(0.05f, 0.012f, 0.5f); pop()                       // blade
        col = c(160, 166, 176); push(); translate(0f, 0f, 0.2f); box(0.01f, 0.016f, 0.5f); pop()                      // fuller
        col = c(214, 168, 70); push(); translate(0f, 0f, 0.47f); box(0.24f, 0.03f, 0.03f); pop()                      // crossguard
        col = c(90, 50, 30); at(z = 0.48f) { cylinder(0.018f, 0.14f, 8) }
        col = c(214, 168, 70); at(z = 0.64f) { sphere(0.032f, 10, 8) }
        col = c(255, 230, 140) * 1.3f; at(z = 0.64f) { sphere(0.012f, 6, 4) }                                          // a jewel glinting in the pommel
    }.build()

    private fun shieldStand() = PM().apply {
        // a heater shield with a heraldic lion-rampant-style device, on a wooden stand, and crossed spears behind
        col = c(110, 74, 44); tube(listOf(Vec3(0f, 0.05f, 0f), Vec3(0f, 0.08f, 0.55f)), 0.02f, 6)
        for (s in intArrayOf(-1, 1)) tube(listOf(Vec3(s * 0.25f, 0.1f, 0f), Vec3(-s * 0.22f, 0.1f, 0.9f)), 0.012f, 5)
        col = c(200, 204, 214); for (s in intArrayOf(-1, 1)) at(-s * 0.22f, 0.1f, 0.9f) { rotY(s * 0.24f); cone(0.025f, 0.08f, 6) }
        push(); translate(0f, 0f, 0.48f); rotX((PI / 2).toFloat())
        val outline = listOf(floatArrayOf(-0.22f, 0.22f), floatArrayOf(0.22f, 0.22f), floatArrayOf(0.22f, -0.02f), floatArrayOf(0.12f, -0.2f), floatArrayOf(0f, -0.28f), floatArrayOf(-0.12f, -0.2f), floatArrayOf(-0.22f, -0.02f))
        col = c(214, 168, 70); prism(outline.map { floatArrayOf(it[0] * 1.06f, it[1] * 1.06f + 0.005f) }, 0.03f)
        col = c(30, 60, 160); push(); translate(0f, 0f, 0.0f); prism(outline.map { floatArrayOf(minOf(it[0], 0f), it[1]) }, 0.04f); pop()
        col = c(170, 30, 30); push(); prism(outline.map { floatArrayOf(maxOf(it[0], 0f), it[1]) }, 0.04f); pop()
        col = c(250, 214, 40)
        at(0f, 0.02f, 0.045f) { scale(1f, 1.3f, 0.3f); sphere(0.07f, 10, 8) }
        at(0.02f, 0.11f, 0.045f) { scale(1f, 1f, 0.3f); sphere(0.045f, 8, 6) }
        for (s in intArrayOf(-1, 1)) tube(listOf(Vec3(s * 0.03f, -0.05f, 0.045f), Vec3(s * 0.06f, -0.14f, 0.045f)), 0.014f, 4)
        pop()
    }.build()

    private fun cage() = PM().apply {
        // an iron gibbet cage on a post, with a skull inside
        col = c(80, 64, 44); cylinder(0.035f, 0.95f, 8)
        tube(listOf(Vec3(0f, 0f, 0.92f), Vec3(0.24f, 0f, 0.95f), Vec3(0.28f, 0f, 0.9f)), 0.02f, 6)
        col = c(70, 72, 78)
        tube(listOf(Vec3(0.28f, 0f, 0.9f), Vec3(0.28f, 0f, 0.78f)), 0.006f, 3)
        push(); translate(0.28f, 0f, 0.38f)
        for (k in 0 until 10) { val a = k * 0.628f; tube(listOf(Vec3(cos(a) * 0.09f, sin(a) * 0.09f, 0.38f), Vec3(cos(a) * 0.15f, sin(a) * 0.15f, 0.2f), Vec3(cos(a) * 0.12f, sin(a) * 0.12f, 0f)), 0.006f, 3) }
        at(z = 0f) { torus(0.12f, 0.008f, 16, 3) }; at(z = 0.2f) { torus(0.15f, 0.008f, 16, 3) }; at(z = 0.38f) { torus(0.09f, 0.008f, 16, 3) }
        col = c(236, 228, 210); at(z = 0.06f) { sphere(0.05f, 10, 8) }
        col = c(30, 26, 22); for (s in intArrayOf(-1, 1)) at(0.04f, s * 0.018f, 0.07f) { sphere(0.012f, 5, 4) }
        pop()
    }.build()

    private fun campfire() = PM().apply {
        // a ring of stones, crossed logs and glowing embers (the flames are a flickering part with real light)
        for (k in 0 until 10) { val a = k * 0.628f; col = c(110, 106, 100) * (0.85f + 0.2f * (k % 3) / 2f); at(cos(a) * 0.3f, sin(a) * 0.3f, 0.04f) { scale(1.2f, 1f, 0.8f); sphere(0.07f, 8, 6) } }
        col = c(100, 66, 40)
        for (k in 0 until 4) { val a = k * 1.571f + 0.4f; tube(listOf(Vec3(cos(a) * 0.24f, sin(a) * 0.24f, 0.03f), Vec3(cos(a) * 0.02f, sin(a) * 0.02f, 0.2f)), 0.035f, 7) }
        col = c(255, 110, 30) * 1.9f; at(z = 0.03f) { scale(1f, 1f, 0.3f); sphere(0.16f, 12, 8) }
        col = c(60, 50, 44); at(-0.42f, 0.1f, 0.04f) { rotZ(0.4f); scale(1f, 0.5f, 0.5f); sphere(0.1f, 8, 6) }        // a log seat
    }.build()

    private fun banner() = PM().apply {
        col = c(80, 64, 44); cylinder(0.02f, 0.95f, 8)
        col = c(214, 168, 70); at(z = 0.95f) { cone(0.03f, 0.08f, 8) }; at(z = 0.95f) { sphere(0.026f, 8, 6) }
        col = c(70, 54, 36); push(); translate(0f, 0f, 0.87f); rotY((PI / 2).toFloat()); cylinder(0.009f, 0.33f, 6); pop()      // crossbar
        col = c(214, 168, 70); at(0.33f, 0f, 0.87f) { sphere(0.014f, 6, 4) }
    }.build()

    // ---------------------------------------------------------------- dragons (shared pieces)
    private val dRed = c(168, 26, 22); private val dDark = c(110, 14, 14); private val dGold = c(226, 176, 92); private val dBone = c(236, 220, 186)
    private fun dHash(i: Int) = (((i * 2654435761L) ushr 9) % 1000).toInt() / 1000f
    private fun crSpline(ctrl: List<Vec3>, rad: FloatArray, per: Int): Pair<List<Vec3>, List<Float>> {
        fun cr(a: Vec3, b: Vec3, c2: Vec3, d: Vec3, t: Float): Vec3 { val t2 = t * t; val t3 = t2 * t
            return (b * 2f + (c2 - a) * t + (a * 2f - b * 5f + c2 * 4f - d) * t2 + (b * 3f - a - c2 * 3f + d) * t3) * 0.5f }
        val path = ArrayList<Vec3>(); val rr = ArrayList<Float>()
        for (i in 0 until ctrl.size - 1) for (k in 0 until per) {
            val t = k / per.toFloat()
            path.add(cr(ctrl[maxOf(i - 1, 0)], ctrl[i], ctrl[i + 1], ctrl[minOf(i + 2, ctrl.size - 1)], t)); rr.add(rad[i] + (rad[i + 1] - rad[i]) * (t * t * (3f - 2f * t)))
        }
        path.add(ctrl.last()); rr.add(rad.last())
        return path to rr
    }
    /** Red dragon hide along a spine: banded tone, a row of dorsal spikes, gold belly plates underneath, and many scales. */
    private fun PM.dragonHide(path: List<Vec3>, rr: List<Float>, spikesFrom: Int, spikesTo: Int, scales: Int, seed: Int, spikeK: Float = 1f) {
        val nP = path.size
        col = dRed
        tube(path, 1f, 22, colorFn = { v -> val ring = (v * (nP - 1)).toInt(); dRed * (0.9f + 0.14f * dHash(ring * 7 + seed)) },
            taper = { v -> rr[minOf((v * (nP - 1)).toInt(), nP - 1)] })
        for (i in 2 until nP - 2 step 2) {                                                                 // belly plates
            val q = path[i]; val tng = (path[i + 1] - path[i - 1]).normalized()
            col = if ((i / 2) % 2 == 0) dGold else dGold * 0.86f
            push(); translate(q.x, q.y, q.z - rr[i] * 0.9f); rotY(-kotlin.math.atan2(tng.z, tng.x)); scale(1.5f, 1.6f, 0.45f); sphere(rr[i] * 0.42f, 10, 6); pop()
        }
        for (i in spikesFrom until minOf(spikesTo, nP - 1) step 2) {                                       // dorsal spikes, shrinking to the tail
            val q = path[i]; val tng = (path[i + 1] - path[i - 1]).normalized()
            col = if (i % 4 == 0) dBone else dBone * 0.85f
            push(); translate(q.x, q.y, q.z + rr[i] * 0.85f); rotY(-0.55f - kotlin.math.atan2(tng.z, tng.x) * 0.5f); cone((rr[i] * 0.28f + 0.006f) * spikeK, (rr[i] * 0.75f + 0.02f) * spikeK, 5); pop()
        }
        for (k in 0 until scales) {                                                                        // scales: darker and glinting
            val i = 3 + (dHash(k * 3 + seed * 977) * (nP - 6)).toInt().coerceAtMost(nP - 7); val q = path[i]; val a = -1.4f + 2.8f * dHash(k * 5 + seed * 131 + 1)   // scattered, not in rows
            col = if (k % 4 == 0) dDark * 0.8f else if (k % 7 == 0) c(140, 20, 18) else dDark * 1.1f
            push(); translate(q.x, q.y + sin(a) * rr[i] * 0.98f, q.z + cos(a) * rr[i] * 0.98f); rotX(-a); scale(1.2f, 1f, 0.32f); sphere(rr[i] * 0.09f + 0.006f, 6, 4); pop()
        }
    }
    /** Horned dragon head facing +x at [h]: long snout, open jaws with teeth and an ember glow, slit-pupil eyes, swept-back horns. */
    private fun PM.dragonHead(h: Vec3, sc: Float, open: Float) {
        push(); translate(h.x, h.y, h.z); scale(sc)
        col = dRed
        at(0f, 0f, 0f) { scale(1.2f, 0.95f, 0.9f); sphere(0.07f, 18, 12) }                                                  // skull
        at(0.1f, 0f, 0.0f) { scale(1.9f, 0.7f, 0.55f); sphere(0.055f, 18, 10) }                                            // upper snout
        col = dGold; push(); translate(0.05f, 0f, -0.035f - open * 0.03f); rotY(open * 0.6f); scale(1.9f, 0.62f, 0.35f); sphere(0.05f, 16, 8); pop()   // lower jaw
        col = c(90, 10, 10); at(0.09f, 0f, -0.03f - open * 0.012f) { scale(1.8f, 0.5f, 0.3f); sphere(0.04f, 12, 6) }           // mouth
        col = c(255, 110, 20) * 2.2f; at(0.06f, 0f, -0.03f - open * 0.01f) { scale(1.2f, 0.4f, 0.25f); sphere(0.03f, 8, 6) }  // ember glow in the throat
        col = dBone; for (k in 0 until 6) for (sg in intArrayOf(-1, 1)) {
            at(0.04f + k * 0.022f, sg * 0.028f * (1f - k * 0.06f), -0.022f) { rotX(PI.toFloat()); cone(0.006f, 0.022f, 4) }
            at(0.05f + k * 0.02f, sg * 0.026f * (1f - k * 0.06f), -0.045f - open * 0.03f + k * open * 0.006f) { cone(0.005f, 0.018f, 4) } }
        col = c(40, 8, 8); for (sg in intArrayOf(-1, 1)) at(0.19f, sg * 0.016f, 0.012f) { scale(1.3f, 1f, 0.7f); sphere(0.008f, 6, 4) }   // nostrils
        for (sg in intArrayOf(-1, 1)) {
            col = dDark; at(0.035f, sg * 0.05f, 0.04f) { scale(1.6f, 0.6f, 0.5f); sphere(0.025f, 10, 6) }                       // brow ridge
            col = c(255, 180, 30) * 1.7f; at(0.045f, sg * 0.058f, 0.025f) { scale(1.3f, 0.6f, 0.8f); sphere(0.015f, 10, 8) }    // eye
            col = c(20, 10, 4); at(0.05f, sg * 0.067f, 0.025f) { scale(0.3f, 0.3f, 1.2f); sphere(0.01f, 6, 6) }                 // slit pupil
            col = dBone                                                                                                          // swept-back horns
            tube((0..6).map { k -> val v = k / 6f; Vec3(-0.02f - 0.13f * v, sg * (0.04f + 0.03f * v), 0.05f + 0.06f * v - 0.05f * v * v) }, 0.014f, 6, taper = { 1f - 0.92f * it })
            tube((0..4).map { k -> val v = k / 4f; Vec3(-0.03f - 0.06f * v, sg * (0.06f + 0.03f * v), -0.01f - 0.01f * v) }, 0.008f, 5, taper = { 1f - 0.9f * it })   // cheek horns
            for (k in 0 until 3) at(0.02f - k * 0.03f, sg * 0.06f, -0.04f) { rotX(sg * 2.4f); cone(0.007f, 0.02f, 4) }          // jaw spikes
        }
        pop()
    }
    /** A bat wing on the right (+y, mirrored by [sg]): arm bone and elbow, four finger bones, and a membrane with a scalloped trailing edge. */
    private fun PM.dragonWing(root: Vec3, sg: Float, span: Float, lift: Float, sweep: Float) {
        val elbow = root + Vec3(-0.08f * span, sg * 0.35f * span, 0.18f * lift * span)
        val wrist = elbow + Vec3(0.12f * span, sg * 0.3f * span, 0.12f * lift * span)
        val tips = (0 until 4).map { f -> val a = 0.25f + f * 0.36f + sweep
            wrist + Vec3(-kotlin.math.sin(a) * 0.62f * span * (1f - f * 0.12f), sg * kotlin.math.cos(a) * 0.5f * span * (1f - f * 0.1f), -0.06f * f * lift * span) }
        col = dDark
        tube(listOf(root, elbow), 0.022f * span, 8, taper = { 1f - 0.3f * it }); tube(listOf(elbow, wrist), 0.017f * span, 8)
        at(wrist.x, wrist.y, wrist.z) { sphere(0.02f * span, 8, 6) }
        col = dBone; at(wrist.x + 0.02f * span, wrist.y, wrist.z + 0.02f * span) { rotY(-0.5f); cone(0.01f * span, 0.05f * span, 5) }   // thumb claw
        col = dDark; for (t in tips) tube(listOf(wrist, (wrist + t) * 0.5f + Vec3(0f, 0f, 0.02f * span), t), 0.009f * span, 5, taper = { 1f - 0.75f * it })
        // membrane: between successive fingers, sagging a little, and from the last finger back to the body
        val anchors = listOf(elbow) + tips + listOf(root + Vec3(-0.32f * span, sg * 0.05f * span, -0.02f * span))
        for (f in 0 until anchors.size - 1) {
            val a = anchors[f]; val b = anchors[f + 1]
            val sag = (a + b) * 0.5f + (wrist - (a + b) * 0.5f) * 0.28f - Vec3(0f, 0f, 0.03f * span)              // the scalloped edge pulls in towards the wrist
            col = if (f % 2 == 0) c(150, 36, 28) else c(132, 28, 22)
            flat(wrist, a, sag, sag); flat(wrist, sag, b, b)
            col = c(190, 70, 40); flat(sag, a, a + (sag - a) * 0.15f, sag)                                          // lighter veins near the edge
        }
    }

    /** A red dragon at rest on the castle grounds, built like the Brachiosaurus (spine curve and leg columns) but crouched
     *  and muscular: S-curved neck and a horned head with open jaws and an ember glow, half-raised bat wings, a spiked back,
     *  a spade-tipped tail, clawed feet, gold belly plates and scaly red hide. Facing +x, about one unit long. */
    fun groundDragon() = PM().apply {
        val (path, rr) = crSpline(listOf(Vec3(-0.92f, 0.1f, 0.05f), Vec3(-0.68f, 0.06f, 0.1f), Vec3(-0.42f, 0f, 0.24f), Vec3(-0.18f, 0f, 0.34f), Vec3(0.08f, 0f, 0.36f),
            Vec3(0.22f, 0f, 0.46f), Vec3(0.27f, 0f, 0.62f), Vec3(0.34f, 0f, 0.74f), Vec3(0.44f, 0f, 0.78f)),
            floatArrayOf(0.012f, 0.04f, 0.12f, 0.16f, 0.15f, 0.09f, 0.07f, 0.065f, 0.06f), 8)
        dragonHide(path, rr, 6, path.size - 6, 260, 3)
        // spade at the tail tip
        col = dDark; push(); translate(-0.95f, 0.1f, 0.05f); rotX((PI / 2).toFloat())
        prism(listOf(floatArrayOf(0.04f, 0f), floatArrayOf(-0.03f, 0.05f), floatArrayOf(-0.08f, 0f), floatArrayOf(-0.03f, -0.05f)), 0.012f); pop()
        dragonHead(Vec3(0.5f, 0f, 0.8f), 1.15f, 0.7f)
        // legs: muscular thighs/shoulders, bent knees, clawed feet
        for ((lx, top, front) in listOf(Triple(0.06f, 0.36f, true), Triple(-0.36f, 0.27f, false))) for (sg in intArrayOf(-1, 1)) {
            col = dRed
            at(lx, sg * 0.13f, top - 0.04f) { scale(if (front) 0.8f else 1.1f, 0.6f, 1.15f); sphere(if (front) 0.085f else 0.11f, 16, 12) }
            val knee = Vec3(lx + (if (front) -0.05f else 0.09f), sg * 0.17f, top * 0.45f)
            val ankle = Vec3(lx + (if (front) 0.02f else -0.02f), sg * 0.17f, 0.06f)
            tube((0..10).map { k -> val t = k / 10f; val a = Vec3(lx, sg * 0.14f, top - 0.08f)
                if (t < 0.5f) Vec3.lerp(a, knee, t * 2f) else Vec3.lerp(knee, ankle, (t - 0.5f) * 2f) }, 0.055f, 14, taper = { t -> 1f - 0.35f * t })
            col = dRed * 0.9f; at(ankle.x + 0.03f, ankle.y, 0.03f) { scale(1.5f, 1f, 0.55f); sphere(0.05f, 12, 8) }                    // foot
            col = c(40, 30, 28); for (t in -1..1) at(ankle.x + 0.09f, ankle.y + t * 0.03f, 0.02f) { rotY(1.6f); cone(0.012f, 0.05f, 5) }   // claws
        }
        dragonWing(Vec3(0.02f, 0.1f, 0.48f), 1f, 1f, 1.3f, 0.2f); dragonWing(Vec3(0.02f, -0.1f, 0.48f), -1f, 1f, 1.3f, 0.2f)
    }.build()

    private fun dragon(): Bird {
        // the flying dragon: streamlined scaly body with gold belly plates and a spiked back, outstretched neck and horned
        // head, legs tucked up, a spade-tipped tail, and articulated bat wings (arm, elbow, four fingers, scalloped membrane)
        val body = PM().apply {
            val (path, rr) = crSpline(listOf(Vec3(-0.75f, 0f, -0.02f), Vec3(-0.5f, 0f, 0.0f), Vec3(-0.22f, 0f, 0.02f), Vec3(0.0f, 0f, 0.02f), Vec3(0.18f, 0f, 0.03f),
                Vec3(0.32f, 0f, 0.07f), Vec3(0.44f, 0f, 0.1f)), floatArrayOf(0.01f, 0.03f, 0.07f, 0.085f, 0.075f, 0.045f, 0.04f), 7)
            dragonHide(path, rr, 4, path.size - 4, 160, 9, 0.5f)
            col = dDark; push(); translate(-0.78f, 0f, -0.02f); prism(listOf(floatArrayOf(0.04f, 0f), floatArrayOf(-0.02f, 0.04f), floatArrayOf(-0.06f, 0f), floatArrayOf(-0.02f, -0.04f)), 0.008f); pop()
            dragonHead(Vec3(0.48f, 0f, 0.11f), 0.75f, 0.35f)
            for ((lx, sg) in listOf(0.08f to 1, 0.08f to -1, -0.24f to 1, -0.24f to -1)) {                                       // tucked legs
                col = dRed; tube(listOf(Vec3(lx, sg * 0.05f, -0.04f), Vec3(lx - 0.06f, sg * 0.06f, -0.09f), Vec3(lx - 0.13f, sg * 0.05f, -0.08f)), 0.02f, 8, taper = { 1f - 0.4f * it })
                col = c(40, 30, 28); at(lx - 0.15f, sg * 0.05f, -0.08f) { rotY(PI.toFloat() / 2f); cone(0.008f, 0.03f, 4) }
            }
        }.build()
        fun wing(sg: Float) = PM().apply { dragonWing(Vec3(0.06f, sg * 0.04f, 0.04f), sg, 0.85f, 0.25f, 0.05f) }.build()
        return Bird(body, wing(1f), wing(-1f), 2, 1.5f, 4.5f, 2.6f, glide = true)
    }

    // ---------------------------------------------------------------- racing bridges over the track (span along y, ±0.56 from the centre line)
    /** A tyre-maker's arch: a black tyre-tread arch with a white brand, on yellow footings. */
    fun tyreArch() = PM().apply {
        col = c(250, 200, 30); for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * 0.56f, 0.06f); box(0.22f, 0.14f, 0.12f); pop() }
        push(); rotY((PI / 2).toFloat())                         // the ring stands up across the track; its lower half is under the floor
        torus(0.56f, 0.075f, 40, 10) { u, v ->
            val a = u * 2f * PI.toFloat()
            val onTop = cos(a) < 0f
            val face = cos(v * 2f * PI.toFloat())
            if (!onTop) c(20, 20, 22)
            else if (face < -0.2f || face > 0.95f) c(24, 24, 26) * (if (((u * 160f).toInt() % 2) == 0) 0.8f else 1.1f)
            else if (((u * 38f).toInt() % 3) != 0 && u in 0.36f..0.64f) c(245, 245, 245) else c(24, 24, 26)
        }
        pop()
        col = c(250, 200, 30); for (s in floatArrayOf(-1f, 1f)) { push(); translate(s * 0.08f, 0f, 0f); rotY((PI / 2).toFloat()); torus(0.56f, 0.025f, 40, 5); pop() }
    }.build()

    /** An energy-drink style arch: navy legs and curved deck, red-and-gold emblem in the middle, brand panels on the legs. */
    fun brandArch() = PM().apply {
        val navy = c(18, 26, 70)
        col = c(30, 30, 34); for (s in intArrayOf(-1, 1)) { push(); translate(0f, s * 0.56f, 0.03f); box(0.24f, 0.16f, 0.06f); pop() }
        for (s in intArrayOf(-1, 1)) {
            col = navy; tube(listOf(Vec3(0f, s * 0.56f, 0.05f), Vec3(0f, s * 0.56f, 0.45f), Vec3(0f, s * 0.48f, 0.72f), Vec3(0f, s * 0.3f, 0.86f)), 0.05f, 6)
            col = c(220, 30, 40); for (k in 0 until 4) { push(); translate(-0.051f, s * 0.56f, 0.18f + k * 0.07f); box(0.006f, 0.07f, 0.04f); pop() }
        }
        col = navy; push(); translate(0f, 0f, 0.88f); box(0.1f, 0.66f, 0.2f); pop()
        col = c(255, 214, 40) * 1.1f; push(); translate(-0.052f, 0f, 0.88f); rotY((PI / 2).toFloat()); cylinder(0.075f, 0.004f, 20); pop()
        col = c(220, 30, 40); for (s in intArrayOf(-1, 1)) { push(); translate(-0.056f, s * 0.12f, 0.87f); scale(0.2f, 1.6f, 0.9f); sphere(0.05f, 10, 6); pop()
            push(); translate(-0.056f, s * 0.05f, 0.92f); rotX(s * 0.8f); cone(0.012f, 0.06f, 5); pop() }
        col = c(250, 250, 250); for (s in intArrayOf(-1, 1)) for (k in 0 until 3) { push(); translate(-0.052f, s * (0.22f + k * 0.035f), 0.88f); box(0.004f, 0.025f, 0.05f); pop() }
    }.build()

    /** Start gantry: a lattice tower, an overhead beam with a black board and a grid of start lights (they glow and light the track). */
    fun startGantry() = PM().apply {
        val steel = c(70, 74, 82)
        for (s in intArrayOf(-1, 1)) {
            col = steel
            for (cx in intArrayOf(-1, 1)) for (cy in intArrayOf(-1, 1)) { push(); translate(cx * 0.05f, s * 0.56f + cy * 0.05f, 0.5f); box(0.02f, 0.02f, 1f); pop() }
            for (k in 0 until 8) { val z = 0.06f + k * 0.12f; tube(listOf(Vec3(-0.05f, s * 0.56f - 0.05f, z), Vec3(0.05f, s * 0.56f + 0.05f, z + 0.12f)), 0.006f, 3); tube(listOf(Vec3(0.05f, s * 0.56f - 0.05f, z), Vec3(-0.05f, s * 0.56f + 0.05f, z + 0.12f)), 0.006f, 3) }
        }
        col = steel; push(); translate(0f, 0f, 0.98f); box(0.06f, 1.24f, 0.05f); pop()
        col = c(16, 16, 18); push(); translate(-0.04f, -0.24f, 0.86f); box(0.03f, 0.5f, 0.2f); pop()
        col = c(245, 245, 245); for (k in 0 until 5) { push(); translate(-0.057f, -0.42f + k * 0.07f, 0.88f); box(0.004f, 0.045f, if (k % 2 == 0) 0.1f else 0.07f); pop() }
        col = c(16, 16, 18); push(); translate(-0.04f, 0.28f, 0.84f); box(0.03f, 0.5f, 0.26f); pop()
        for (cx in 0 until 5) for (cy in 0 until 3) {
            col = if (cy == 0) c(255, 150, 30) * 2f else c(255, 40, 30) * 2.2f
            push(); translate(-0.058f, 0.08f + cx * 0.1f, 0.76f + cy * 0.07f); rotY((PI / 2).toFloat()); cylinder(0.026f, 0.006f, 12); pop()
        }
    }.build()
}
