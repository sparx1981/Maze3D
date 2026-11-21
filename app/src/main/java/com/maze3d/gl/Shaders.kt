package com.maze3d.gl

import android.opengl.GLES30
import android.util.Log

/** GLSL ES 3.00 sources. Kept free of Android types so the same text can be previewed in WebGL2. */
object Shaders {
    const val SCENE_VS = """#version 300 es
uniform mat4 uVP;
uniform mat4 uModel;
uniform float uGrowT;
uniform float uTime;
uniform float uDoorMode;   // 1 = door mesh: aDelay is the phase of its open/close cycle
uniform float uShell;      // grass shells: how far up the blade this pass is (0 = the ground itself)
uniform float uShellH;
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNrm;
layout(location=2) in vec3 aCol;
layout(location=3) in vec3 aLift;
layout(location=4) in float aDelay;
layout(location=5) in vec3 aTile;
out vec3 vWorld;
out vec3 vNrm;
out vec3 vCol;
out vec3 vLocal;
out vec3 vNL;
out vec3 vTile;
void main() {
    float g = clamp((uGrowT - aDelay) / 0.35, 0.0, 1.0);
    g = g * g * (3.0 - 2.0 * g);
    if (uDoorMode > 0.5) {
        // keep in sync with Hazards.doorG: smoothstep(0.4, 0.8, 0.5 + 0.5 sin(1.15 t + 2 pi phase))
        float s = 0.5 + 0.5 * sin(1.15 * uTime + aDelay * 6.2831853);
        g = smoothstep(0.4, 0.8, s) * smoothstep(0.0, 0.6, uGrowT);
    }
    vec3 p = aPos + aLift * g + aNrm * (uShell * uShellH);
    vec4 w = uModel * vec4(p, 1.0);
    vWorld = w.xyz;
    vNrm = mat3(uModel) * aNrm;
    vNL = aNrm;
    vTile = aTile;
    vLocal = p;
    vCol = aCol;
    gl_Position = uVP * w;
    if (aDelay >= 0.0 && g <= 0.001) gl_Position = vec4(2.0, 2.0, 2.0, 1.0); // fully sunk walls vanish
}
"""

    /** Shared studio environment + PBR helpers. */
    const val COMMON = """
const float PI = 3.14159265;
uniform vec3 uLightDir;

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
float vnoise(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(hash21(i), hash21(i + vec2(1, 0)), f.x),
               mix(hash21(i + vec2(0, 1)), hash21(i + vec2(1, 1)), f.x), f.y);
}
// 3D value noise: looks the same from every direction, so surface patterns never stretch or swirl on any face of a shape
float hash31(vec3 p) { p = fract(p * 0.1031); p += dot(p, p.zyx + 31.32); return fract((p.x + p.y) * p.z); }
float vnoise3(vec3 p) {
    vec3 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(mix(hash31(i), hash31(i + vec3(1, 0, 0)), f.x), mix(hash31(i + vec3(0, 1, 0)), hash31(i + vec3(1, 1, 0)), f.x), f.y),
               mix(mix(hash31(i + vec3(0, 0, 1)), hash31(i + vec3(1, 0, 1)), f.x), mix(hash31(i + vec3(0, 1, 1)), hash31(i + vec3(1, 1, 1)), f.x), f.y), f.z);
}

// Studio environment: sky gradient, a big key softbox, a cool fill strip and a warm kicker.
// Highlights widen and dim with roughness, which stands in for a prefiltered cube map.
vec3 envMap(vec3 r, float rough) {
    float up = r.y * 0.5 + 0.5;
    vec3 sky = mix(vec3(0.03, 0.032, 0.04), vec3(0.50, 0.56, 0.68), smoothstep(0.05, 1.0, up));
    sky = mix(sky, vec3(0.20, 0.21, 0.24), rough * 0.5);
    float w = 0.02 + rough * rough * 0.7;
    float k = 1.0 / (1.0 + rough * rough * 14.0);
    vec3 c = sky;
    c += vec3(5.5, 5.2, 4.8) * k * smoothstep(0.92 - w, 0.965 + w * 0.2, dot(r, uLightDir));
    c += vec3(1.6, 2.2, 3.6) * k * smoothstep(0.80 - w, 0.90, dot(r, normalize(vec3(-0.85, 0.15, 0.5))));
    c += vec3(0.55, 0.42, 0.32) * k * smoothstep(0.88 - w, 0.95, dot(r, normalize(vec3(0.2, -0.6, 0.75))));
    c += vec3(0.8, 0.85, 1.0) * k * smoothstep(0.85 - w, 0.93, dot(r, normalize(vec3(0.0, 0.3, -1.0))));
    return c;
}

vec3 irradiance(vec3 n) {
    float up = n.y * 0.5 + 0.5;
    vec3 c = mix(vec3(0.035, 0.035, 0.045), vec3(0.42, 0.47, 0.58), up);
    c += vec3(0.20, 0.17, 0.14) * max(dot(n, uLightDir), 0.0) * 0.4;
    return c;
}

vec3 envBRDF(vec3 F0, float rough, float NoV) {
    vec4 c0 = vec4(-1.0, -0.0275, -0.572, 0.022);
    vec4 c1 = vec4(1.0, 0.0425, 1.04, -0.04);
    vec4 r = rough * c0 + c1;
    float a004 = min(r.x * r.x, exp2(-9.28 * NoV)) * r.x + r.y;
    vec2 AB = vec2(-1.04, 1.04) * a004 + r.zw;
    return F0 * AB.x + AB.y;
}

float ggx(float NoH, float a) {
    float a2 = a * a;
    float d = NoH * NoH * (a2 - 1.0) + 1.0;
    return a2 / (PI * d * d);
}
float visSmith(float NoV, float NoL, float a) {
    float k = a * 0.5;
    return 0.25 / ((NoV * (1.0 - k) + k) * (NoL * (1.0 - k) + k));
}
vec3 fresnel(vec3 F0, float VoH) { return F0 + (1.0 - F0) * pow(1.0 - VoH, 5.0); }

vec3 aces(vec3 x) {
    return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0);
}
uniform float uExposure;     // per-level exposure (0 = unset = 1)
vec3 finish(vec3 c, vec2 frag) {
    c = aces(c * (uExposure > 0.0 ? uExposure : 1.0));
    c = pow(c, vec3(1.0 / 2.2));
    return c + (hash21(frag) - 0.5) / 255.0; // dither: no banding in gradients
}
"""

    const val SCENE_FS = """#version 300 es
precision highp float;
precision highp sampler2DShadow;
uniform vec3 uCam;
uniform float uTime;
uniform float uEmissive;
uniform int uMat;          // 0 enamel, 1 carbon fibre, 2 brushed metal, 3 wood, 4 flat/emissive
uniform float uRough;
uniform float uMetal;
uniform int uHoleN;
uniform float uTrailR;
uniform vec2 uStud;          // toy studs: x = 1 / pitch, y = stud height
uniform vec2 uLava;          // lava crust: x = 1 / plate size, y = detail (0..1, from the quality setting)
uniform vec4 uHoleC[48];     // xyz = hole centre (local space), w = cut radius; only set while drawing the floor
uniform vec3 uFloor2;       // second floor colour for patterned themes (sRGB)
uniform float uShadowOn;
uniform vec3 uMarbleL;      // marble position (world) for its glow and contact shadow
uniform float uMarbleR;     // marble radius; 0 disables
uniform vec3 uMarbleC;
uniform float uFog;         // distance haze density (chase view)
uniform float uNight;       // 1 = moonlit night: dim blue light, lamps do the lighting
uniform vec3 uLampCol;
uniform float uHedgeSnow;   // 1 = snow dusts the hedges
uniform float uHiero;       // 1 = Egyptian walls carry painted hieroglyphs
uniform vec4 uLamp[12];
uniform vec3 uLampC[12];      // linear colour x strength of each lamp; uLamp holds local-space position (xyz) and reach (w)
uniform float uLampGain;     // day scenes dim the lamps; night scenes rely on them
uniform vec3 uFogCol;
uniform float uWet;        // 0..1 rain wetness
uniform float uSandOn;     // 1 = sand / mud height field is bound
uniform float uShell;
uniform sampler2D uSandTex;
uniform vec4 uSandP;       // x = texels per cell, y = tile size in texels (with gutter), z = tiles per atlas row
uniform vec2 uSandAtlas;
uniform float uAlpha;
uniform float uTexel;
uniform mat4 uLightVP;
uniform sampler2DShadow uShadow;
in vec3 vWorld;
in vec3 vNrm;
in vec3 vCol;
in vec3 vLocal;
in vec3 vNL;
in vec3 vTile;
out vec4 outColor;
""" + COMMON + """
float shadowAt(vec3 wp, vec3 N) {
    if (uShadowOn < 0.5) return 1.0;
    vec4 sc = uLightVP * vec4(wp + N * 0.010, 1.0);
    vec3 p = sc.xyz / sc.w * 0.5 + 0.5;
    if (p.x < 0.0 || p.x > 1.0 || p.y < 0.0 || p.y > 1.0 || p.z > 1.0) return 1.0;
    float z = p.z - 0.0012;
    float s = 0.0;
    const vec2 k[12] = vec2[12](
        vec2(-0.326, -0.406), vec2(-0.840, -0.074), vec2(-0.696, 0.457), vec2(-0.203, 0.621),
        vec2(0.962, -0.195), vec2(0.473, -0.480), vec2(0.519, 0.767), vec2(0.185, -0.893),
        vec2(0.507, 0.064), vec2(0.896, 0.412), vec2(-0.322, -0.933), vec2(-0.792, -0.598));
    float rot = hash21(gl_FragCoord.xy) * 6.2831;
    mat2 R = mat2(cos(rot), sin(rot), -sin(rot), cos(rot));
    for (int i = 0; i < 12; i++) s += texture(uShadow, vec3(p.xy + R * k[i] * uTexel * 2.2, z));
    return s / 12.0;
}

// Triplanar blend weights from the local normal.
vec3 triW(vec3 n) { vec3 w = pow(abs(n), vec3(6.0)); return w / (w.x + w.y + w.z + 1e-5); }


float fade3(vec3 P) { return 1.0 - smoothstep(0.0012, 0.004, length(dFdx(P))); }
vec3 hash33(vec3 p) {
    p = vec3(dot(p, vec3(127.1, 311.7, 74.7)), dot(p, vec3(269.5, 183.3, 246.1)), dot(p, vec3(113.5, 271.9, 124.6)));
    return fract(sin(p) * 43758.5453);
}
// lava colour scale: black crust, dark brown, orange, yellow-white (linear)
vec3 lavaRamp(float t) {
    const vec3 c0 = vec3(0.0050, 0.0010, 0.0005), c1 = vec3(0.039, 0.0090, 0.0040);
    const vec3 c2 = vec3(1.0, 0.101, 0.0037), c3 = vec3(1.0, 0.64, 0.116);
    t = clamp(t, 0.0, 1.0) * 3.0;
    return t < 1.0 ? mix(c0, c1, t) : (t < 2.0 ? mix(c1, c2, t - 1.0) : mix(c2, c3, t - 2.0));
}
float hash11(float n) { return fract(sin(n * 127.1) * 43758.5453); }
vec2 rot2(vec2 v, float a) { float c = cos(a), s = sin(a); return vec2(c * v.x - s * v.y, s * v.x + c * v.y); }

// candy sprinkles on one plane: returns premultiplied colour (rgb) and coverage (a)
vec4 sprinkles(vec2 uv) {
    vec2 g = uv * 48.0, id = floor(g), f = fract(g) - 0.5;
    float h = hash21(id);
    if (h > 0.16) return vec4(0.0);
    vec2 d = rot2(f, h * 40.0);
    float sdf = length(vec2(max(abs(d.x) - 0.2, 0.0), d.y)) - 0.075;
    float m = 1.0 - smoothstep(0.0, 0.05, sdf);
    vec3 col = h < 0.04 ? vec3(0.95, 0.2, 0.35) : h < 0.08 ? vec3(0.2, 0.75, 0.95) : h < 0.12 ? vec3(1.0, 0.85, 0.2) : vec3(0.5, 0.85, 0.4);
    return vec4(pow(col, vec3(2.2)) * m, m);
}

// day-of-the-dead marigolds on one plane
vec4 flowers(vec2 uv) {
    vec2 g = uv * 7.0; g.x += 0.5 * mod(floor(g.y), 2.0);
    vec2 id = floor(g), f = fract(g) - 0.5;
    float r = length(f), a = atan(f.y, f.x);
    float petal = 0.30 + 0.075 * cos(a * 8.0 + hash21(id) * 3.0);
    float inF = 1.0 - smoothstep(petal - 0.03, petal, r);
    float centre = 1.0 - smoothstep(0.08, 0.11, r);
    vec3 orange = mix(vec3(1.0, 0.42, 0.04), vec3(1.0, 0.62, 0.08), 0.5 + 0.5 * cos(a * 8.0));
    vec3 col = mix(orange, vec3(0.45, 0.12, 0.05), centre);
    // papel picado dots between the flowers
    float dots = 1.0 - smoothstep(0.045, 0.06, length(fract(g + 0.5) - 0.5 - vec2(0.5)));
    vec3 dcol = vec3(0.95, 0.9, 0.85);
    float m = max(inF, dots * 0.8);
    vec3 outc = mix(dcol, col, inF);
    return vec4(pow(outc, vec3(2.2)) * m, m);
}

// space hull panels: r = panel brightness, g = lit window mask
vec4 panels(vec2 uv) {
    vec2 g = uv * 5.5, id = floor(g), f = fract(g);
    float edge = min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y));
    float seam = smoothstep(0.0, 0.04, edge);
    float tone = 0.8 + 0.25 * hash21(id);
    vec2 rv = abs(f - 0.5) - 0.42;
    float rivet = 1.0 - smoothstep(0.0, 0.03, length(rv));
    vec2 wf = fract(g * vec2(3.0, 5.0));
    float win = step(0.94, hash21(id + 7.0)) * step(0.2, wf.x) * step(wf.x, 0.8) * step(0.3, wf.y) * step(wf.y, 0.7);
    return vec4(seam * tone * (1.0 - 0.3 * rivet), win, 0.0, 0.0);
}

// laboratory tiles: r = tile brightness (grout darkens it)
vec4 tiles(vec2 uv) {
    vec2 g = uv * 6.5, f = fract(g);
    float edge = min(min(f.x, 1.0 - f.x), min(f.y, 1.0 - f.y));
    float grout = smoothstep(0.0, 0.03, edge);
    float v = 0.9 + 0.1 * hash21(floor(g));
    return vec4(mix(0.35, v, grout), 0.0, 0.0, 0.0);
}

// carved glyph blocks: a = glyph mask
vec4 glyphs(vec2 uv) {
    vec2 g = uv * vec2(8.0, 1.0), id = floor(g), f = fract(g);
    float h = hash21(id);
    float on = step(0.45, h);
    vec2 q = abs(f - 0.5);
    float box = (1.0 - smoothstep(0.30, 0.34, q.x)) * (1.0 - smoothstep(0.16, 0.2, q.y));
    float stem = (1.0 - smoothstep(0.03, 0.06, abs(f.x - 0.5 + (h - 0.5) * 0.3))) * (1.0 - smoothstep(0.16, 0.2, q.y));
    float cross = (1.0 - smoothstep(0.03, 0.06, abs(f.y - 0.5))) * (1.0 - smoothstep(0.30, 0.34, q.x));
    float m = on * clamp(box * 0.0 + stem + cross * step(0.7, h), 0.0, 1.0);
    return vec4(0.0, 0.0, 0.0, m);
}

// Painted Egyptian hieroglyphs: columns of symbols (ankh, eye, bird, water, sun, reed) in blue, red, ochre and green,
// outlined in black and framed by column rules. Returns (colour, coverage).
float sline(vec2 p, vec2 a, vec2 b, float w) { vec2 pa = p - a, ba = b - a; float h = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0); return length(pa - ba * h) - w; }
vec4 hiero(vec2 uv) {
    vec2 id = floor(uv), f = fract(uv) - 0.5;
    float h = hash21(id + 0.37);
    int k = int(h * 6.0);
    float aa = max(fwidth(uv.x), fwidth(uv.y)) * 1.5 + 0.004;
    float d = 1e3;                                         // signed distance of the symbol
    vec3 c = vec3(0.12, 0.3, 0.65);
    if (k == 0) {                                          // ankh
        d = min(abs(length((f - vec2(0.0, 0.18)) * vec2(1.0, 0.8)) - 0.11) - 0.035, sline(f, vec2(0.0, 0.08), vec2(0.0, -0.4), 0.04));
        d = min(d, sline(f, vec2(-0.2, 0.02), vec2(0.2, 0.02), 0.04)); c = vec3(0.8, 0.58, 0.16);
    } else if (k == 1) {                                   // eye of Horus
        float e = length(f * vec2(1.0, 2.3)) - 0.28;
        d = abs(e) - 0.03; d = min(d, length(f - vec2(0.02, 0.0)) - 0.06);
        d = min(d, sline(f, vec2(-0.05, -0.1), vec2(-0.12, -0.36), 0.03)); d = min(d, sline(f, vec2(0.06, -0.1), vec2(0.2, -0.3), 0.025));
        c = vec3(0.12, 0.3, 0.65);
    } else if (k == 2) {                                   // bird (ibis)
        d = length((f - vec2(0.02, -0.02)) * vec2(1.0, 1.6)) - 0.18;
        d = min(d, length(f - vec2(0.18, 0.2)) - 0.07); d = min(d, sline(f, vec2(0.22, 0.2), vec2(0.36, 0.08), 0.02));
        d = min(d, sline(f, vec2(0.0, -0.12), vec2(-0.04, -0.42), 0.018)); d = min(d, sline(f, vec2(0.06, -0.12), vec2(0.08, -0.42), 0.018));
        d = min(d, sline(f, vec2(-0.14, 0.0), vec2(-0.34, -0.12), 0.04)); c = vec3(0.1, 0.45, 0.35);
    } else if (k == 3) {                                   // water: three zigzags
        for (int i = 0; i < 3; i++) { float y = -0.22 + float(i) * 0.22; float zz = abs(fract((f.x + 0.5) * 4.0) - 0.5) * 0.12; d = min(d, abs(f.y - y - zz) - 0.022); }
        d = max(d, abs(f.x) - 0.38); c = vec3(0.12, 0.3, 0.65);
    } else if (k == 4) {                                   // sun disk with a cobra
        d = length(f - vec2(0.0, 0.08)) - 0.2; d = min(d, abs(length(f - vec2(0.0, 0.08)) - 0.26) - 0.02);
        d = min(d, sline(f, vec2(-0.08, -0.2), vec2(0.08, -0.38), 0.03)); c = vec3(0.75, 0.16, 0.1);
    } else {                                               // reed / feather
        d = length((f - vec2(0.0, 0.0)) * vec2(3.2, 1.0)) - 0.4; d = max(d, -sline(f, vec2(0.0, -0.4), vec2(0.0, 0.4), 0.012));
        c = vec3(0.8, 0.58, 0.16);
    }
    float fill = 1.0 - smoothstep(-aa, aa, d);
    float outline = 1.0 - smoothstep(0.0, aa, abs(d) - 0.012);
    vec3 col = mix(c, vec3(0.05, 0.04, 0.03), outline * 0.85);
    float rule = 1.0 - smoothstep(0.0, aa, abs(abs(f.x) - 0.47) - 0.012);   // column rules
    col = mix(col, vec3(0.08, 0.06, 0.04), rule * (1.0 - fill));
    return vec4(col, max(fill, rule * 0.9) * step(0.12, h));
}

// 2/2 twill carbon weave on one plane. Returns (tone, direction) with distance-based fade.
vec2 weave(vec2 uv) {
    vec2 g = uv / 0.011;
    vec2 id = floor(g);
    vec2 f = fract(g);
    float flip = step(2.0, mod(id.x + id.y, 4.0));
    float across = flip < 0.5 ? f.x : f.y;
    float fibre = 0.5 + 0.5 * cos((across - 0.5) * PI * 1.2);
    float fw = max(fwidth(g.x), fwidth(g.y));
    float fade = smoothstep(0.35, 0.8, fw);
    return vec2(mix(0.55 + 0.6 * fibre * (0.7 + 0.3 * flip), 0.85, fade), flip);
}

// Bump from a height field (world units) using screen-space derivatives (surface gradient method).
vec3 bumpN(vec3 N, vec3 p, float h) {
    vec3 dpx = dFdx(p), dpy = dFdy(p);
    float dhx = dFdx(h), dhy = dFdy(h);
    vec3 r1 = cross(dpy, N), r2 = cross(N, dpx);
    float det = dot(dpx, r1);
    vec3 grad = sign(det) * (dhx * r1 + dhy * r2);
    return normalize(abs(det) * N - grad);
}

float sandH(vec2 uv, float cell) {
    vec2 org = vec2(mod(cell, uSandP.z), floor(cell / uSandP.z)) * uSandP.y;
    float hl = 0.5 / uSandP.x;
    return texture(uSandTex, (org + 1.0 + clamp(uv, -hl, 1.0 + hl) * uSandP.x) / uSandAtlas).r;
}

uniform vec4 uCutA;          // follow view see-through: xyz = camera (world), w = 1 when on
uniform vec4 uCutB;          // xyz = ball (world), w = radius of the cut-away tunnel
void main() {
    if (uCutA.w > 0.01) {                                  // only while the shape hides the ball: a soft-edged see-through window toward it
        vec3 ab = uCutB.xyz - uCutA.xyz;
        float L = length(ab);
        vec3 d = ab / max(L, 1e-5);
        float t = dot(vWorld - uCutA.xyz, d);
        float keep = uCutB.w * 0.9;                          // the ball's own neighbourhood stays
        if (t > 0.0 && t < L - keep) {
            float r = length(vWorld - uCutA.xyz - d * t);
            float rad = uCutB.w * uCutA.w;
            float k = smoothstep(rad * 0.55, rad, r);          // 0 inside .. 1 outside, with a dithered fringe
            if (k < hash21(floor(gl_FragCoord.xy * 0.5))) discard;
        }
    }
    for (int i = 0; i < 48; i++) {                         // real holes: the floor is cut away
        if (i >= uHoleN) break;
        if (distance(vLocal, uHoleC[i].xyz) < uHoleC[i].w) discard;
    }
    vec3 N = normalize(vNrm);
    vec3 V = normalize(uCam - vWorld);
    vec3 albedo = pow(vCol, vec3(2.2));
    float hgt = 0.0;
    vec3 emit = vec3(0.0);
    float rough = uRough;
    float metal = uMetal;
    float coat = 0.0;
    vec3 tw = triW(normalize(vNL));
    vec3 P = vLocal;

    if (uMat == 1) {            // carbon fibre under clear coat
        vec2 a = weave(P.yz) , b = weave(P.xz), c = weave(P.xy);
        float tone = a.x * tw.x + b.x * tw.y + c.x * tw.z;
        float dir = a.y * tw.x + b.y * tw.y + c.y * tw.z;
        albedo *= tone * 0.9;
        hgt = (tone - 0.85) * 0.0011;
        rough = 0.42 - 0.12 * dir;
        coat = 1.0;
    } else if (uMat == 2) {     // brushed metal
        float n1 = vnoise(vec2(P.x * 8.0 + P.z * 5.0, P.y * 260.0));
        float n2 = vnoise(vec2(P.y * 8.0 + P.x * 5.0, P.z * 260.0));
        float n3 = vnoise(vec2(P.z * 8.0 + P.y * 5.0, P.x * 260.0));
        float n = n1 * tw.x + n2 * tw.y + n3 * tw.z;
        albedo *= 0.88 + 0.24 * n;
        hgt = (n - 0.5) * 0.00035;
        rough = uRough + 0.10 * (n - 0.5);
    } else if (uMat == 3) {     // wood grain
        float g1 = vnoise(vec2(P.y * 70.0, P.z * 5.0)) + 0.5 * vnoise(vec2(P.y * 230.0, P.z * 14.0));
        float g2 = vnoise(vec2(P.z * 70.0, P.x * 5.0)) + 0.5 * vnoise(vec2(P.z * 230.0, P.x * 14.0));
        float g3 = vnoise(vec2(P.x * 70.0, P.y * 5.0)) + 0.5 * vnoise(vec2(P.x * 230.0, P.y * 14.0));
        float g = (g1 * tw.x + g2 * tw.y + g3 * tw.z) / 1.5;
        float ring = 0.5 + 0.5 * sin(g * 28.0);
        albedo *= 0.70 + 0.55 * mix(g, ring, 0.45);
        hgt = (mix(g, ring, 0.45) - 0.5) * 0.0007;
        rough = 0.40 + 0.12 * g;
        coat = 0.6;
    } else if (uMat == 5) {     // sand: fine grain, wind ripples
        float fade = 1.0 - smoothstep(0.0012, 0.004, length(dFdx(P)));      // grain averages out at a distance
        float grain = vnoise3(P * 416.1 + vec3(7.31, 3.17, 5.71));
        float lump = vnoise3(P * 19.8 + vec3(1.62, 6.34, 2.42));
        vec3 sn = normalize(vNL);                               // wind ripples run across the surface (never face-on, so no swirls)
        vec3 wA = vec3(0.82, 0.23, 0.53) - sn * dot(vec3(0.82, 0.23, 0.53), sn);
        vec3 wB = vec3(-0.27, 0.93, 0.25) - sn * dot(vec3(-0.27, 0.93, 0.25), sn);
        vec3 wd = normalize(mix(wB, wA, smoothstep(0.25, 0.55, length(wA))) + 1e-5);
        float rip = sin(dot(P, wd) * 85.0 + 3.0 * lump - uTime * 0.35);                // ripples creep with the wind
        albedo *= 0.80 + 0.2 * lump + 0.22 * (grain - 0.5) * 2.0 * fade;
        hgt = (rip * 0.0004 + (grain - 0.5) * 0.0004 * fade);
        rough = 0.96;
    } else if (uMat == 6) {     // mud: dark, lumpy, wet sheen
        float lump = vnoise3(P * 35.0 + vec3(8.93, 9.51, 8.13));
        float fine = vnoise3(P * 202.1 + vec3(3.24, 1.68, 4.84));
        albedo *= 0.55 + 0.75 * lump;
        hgt = (lump - 0.5) * 0.0014 + (fine - 0.5) * 0.0003;
        rough = 0.22 + 0.4 * (1.0 - lump);
        coat = 0.85;
    } else if (uMat == 7) {     // candy: marbled pastel base with sprinkles
        float n = vnoise3(P * 6.6 + vec3(10.55, 4.85, 1.55));
        albedo = mix(albedo, pow(uFloor2, vec3(2.2)), smoothstep(0.35, 0.65, n));
        vec4 sp = sprinkles(P.yz) * tw.x + sprinkles(P.xz) * tw.y + sprinkles(P.xy) * tw.z;
        albedo = mix(albedo, sp.rgb / max(sp.a, 1e-3), clamp(sp.a, 0.0, 1.0));
        float tw2 = vnoise3(P * 80.0 + vec3(4.86, 8.02, 7.26) + uTime * 0.8);
        emit = vec3(1.0, 0.9, 0.95) * clamp(sp.a, 0.0, 1.0) * smoothstep(0.78, 0.95, tw2) * 0.9;     // sugar glints
        rough = 0.22; coat = 1.0;
    } else if (uMat == 8) {     // day of the dead: marigold flowers and papel-picado dots on purple
        vec4 f1 = flowers(P.yz), f2 = flowers(P.xz), f3 = flowers(P.xy);
        vec4 fl = f1 * tw.x + f2 * tw.y + f3 * tw.z;
        albedo = mix(albedo, fl.rgb, clamp(fl.a, 0.0, 1.0));
        rough = 0.42; coat = 0.6;
    } else if (uMat == 9) {     // asphalt: aggregate grit and dark rubber smears
        float grit = vnoise3(P * 549.8 + vec3(12.17, 0.19, 3.97));
        float smear = vnoise3(P * 10.7 + vec3(6.48, 3.36, 0.68));
        float spk = step(0.93, vnoise3(P * 941.2 + vec3(0.79, 6.53, 6.39)));
        albedo *= (0.65 + 0.7 * grit) * (0.75 + 0.4 * smear);
        albedo += vec3(0.25) * spk * fade3(P);
        hgt = (grit - 0.5) * 0.0004;
        rough = 0.82;
    } else if (uMat == 10) {    // mown turf: alternating bands and fine blades
        float band = step(0.5, fract(P.y * 4.2 + 0.15 * vnoise(vec2(P.x * 2.0, P.z * 2.0))));
        albedo = mix(albedo, pow(uFloor2, vec3(2.2)), band);
        float blade = vnoise3(P * 1161.0 + vec3(8.10, 9.70, 3.10));
        albedo *= 0.78 + 0.4 * blade * fade3(P) + 0.2 * (1.0 - fade3(P));
        hgt = (blade - 0.5) * 0.0006 * fade3(P);
        rough = 0.92;
        float press = 0.0; vec3 leanW = vec3(0.0);           // (the grass no longer reacts to the ball: too costly per fragment)
        if (uShell > 0.0) {                                  // one layer of the short grass: each blade is a thin tapering column
            vec3 shiftW = leanW * uTrailR * 0.45 * uShell;
            vec2 gq = vec2(dot(P - shiftW, vec3(150.0, 85.0, 120.0)), dot(P - shiftW, vec3(-80.0, 140.0, 95.0)));
            vec2 gc = floor(gq), gf = fract(gq) - 0.5;
            float bh = (0.4 + 0.6 * hash21(gc + 3.7)) * (1.0 - 0.7 * min(press, 1.0));
            vec2 off = (vec2(hash21(gc + 1.3), hash21(gc + 8.1)) - 0.5) * 0.5;
            if (uShell > bh || length(gf - off) > 0.46 * (1.0 - 0.75 * uShell / bh)) discard;
            albedo *= 0.45 + 0.85 * uShell;                   // darker at the root, brighter at the tip
            albedo = mix(albedo, albedo * vec3(1.15, 1.1, 0.8), uShell * 0.5);
            hgt = 0.0; rough = 0.7;
        }
    } else if (uMat == 11) {    // space hull: panel seams, rivets and lit windows
        vec4 s1 = panels(P.yz), s2 = panels(P.xz), s3 = panels(P.xy);
        vec4 pn = s1 * tw.x + s2 * tw.y + s3 * tw.z;
        albedo *= pn.r;
        hgt = (pn.r - 0.7) * 0.0012;
        float blink = 0.55 + 0.45 * step(0.35, fract(sin(dot(floor(P * 9.0), vec3(12.9, 78.2, 37.7))) * 43758.5 + uTime * 0.25));
        emit = vec3(0.3, 0.9, 1.0) * pn.g * 1.6 * blink;
        rough = 0.32; metal = 0.5;
    } else if (uMat == 12) {    // diner tablecloth: red and white checks with woven fabric
        float ck = mod(floor(P.x * 7.0) + floor(P.y * 7.0) + floor(P.z * 7.0), 2.0);
        albedo = mix(albedo, pow(uFloor2, vec3(2.2)), ck);
        float wv = sin(dot(P, vec3(900.0, 300.0, 500.0))) * sin(dot(P, vec3(-300.0, 900.0, 400.0)));
        albedo *= 0.92 + 0.08 * wv * fade3(P);
        hgt = wv * 0.00025 * fade3(P);
        rough = 0.78;
    } else if (uMat == 13) {    // laboratory tiles
        vec4 t1 = tiles(P.yz), t2 = tiles(P.xz), t3 = tiles(P.xy);
        vec4 tl = t1 * tw.x + t2 * tw.y + t3 * tw.z;
        albedo *= tl.r;
        hgt = (tl.r - 0.8) * 0.0006;
        rough = 0.12 + 0.3 * (1.0 - tl.r); coat = 1.0;
    } else if (uMat == 14) {    // ice: frosted surface with deep cracks
        float cr = vnoise3(P * 16.5 + vec3(2.41, 1.87, 8.81));
        float crack = 1.0 - smoothstep(0.0, 0.035, abs(cr - 0.5));
        float frost = vnoise3(P * 313.3 + vec3(9.72, 5.04, 5.52));
        albedo = mix(albedo * (0.8 + 0.3 * frost), vec3(0.45, 0.62, 0.8) * albedo, crack * 0.7);
        hgt = -crack * 0.0008 + (frost - 0.5) * 0.0003;
        rough = 0.09 + 0.12 * frost; coat = 1.0;
        float glint = smoothstep(0.86, 0.97, vnoise3(P * 697.9 + vec3(4.03, 8.21, 2.23) + uTime * 1.3)) * fade3(P);
        emit = vec3(0.35, 0.55, 0.8) * 0.12 * (1.0 - crack) + vec3(0.9, 0.97, 1.0) * glint * 1.4;
    } else if (uMat == 15) {    // sandstone with carved bands and glyph blocks
        float band = fract(P.y * 3.4);
        float line = 1.0 - smoothstep(0.0, 0.03, abs(band - 0.5) - 0.44);
        vec4 g1 = glyphs(P.xz * 1.0 + vec2(0.0, P.y * 3.4)), g2 = glyphs(P.zy * 1.0 + vec2(P.x * 3.4, 0.0));
        float gl = max(g1.a * tw.y, g2.a * (tw.x + tw.z));
        float grain = vnoise3(P * 268.9 + vec3(11.34, 0.38, 7.94));
        albedo *= (0.82 + 0.3 * grain) * (1.0 - 0.45 * line) * (1.0 - 0.38 * gl);
        hgt = -(line * 0.0006 + gl * 0.0008);
        rough = 0.88;
    } else if (uMat == 16) {    // rain-soaked city street: dark asphalt, standing puddles that mirror the neon, drizzle rings
        float grit = vnoise3(P * 520.0 + 3.0);
        float tar = vnoise3(P * 14.0 + 7.0);
        albedo *= (0.62 + 0.5 * grit * fade3(P)) * (0.75 + 0.35 * tar);
        float pud = smoothstep(0.52, 0.62, vnoise3(P * 9.0 + vec3(1.0, 4.0, 2.0)) * 0.75 + vnoise3(P * 31.0) * 0.25);  // puddles
        // drizzle: a ring spreading in each little patch of puddle, each on its own clock
        vec3 rq = P * 70.0; vec3 ri = floor(rq); vec3 rf = fract(rq) - 0.5;
        float rh = hash31(ri + 17.0);
        float rt = fract(uTime * 0.7 + rh);
        float ring = sin((length(rf) - rt * 0.5) * 40.0) * smoothstep(0.5, 0.0, rt) * smoothstep(0.5 * rt + 0.06, 0.5 * rt, length(rf)) * step(0.55, rh);
        hgt = (grit - 0.5) * 0.0005 * (1.0 - pud) + ring * 0.00025 * pud;
        albedo *= mix(1.0, 0.45, pud);
        // neon city mirrored in the wet ground: tall coloured streaks from the signs and screens above
        vec3 Rw = reflect(-V, normalize(vNrm));
        float az = atan(Rw.z, Rw.x) * 9.0;
        float sid = floor(az); float sf = fract(az);
        float hs = hash11(sid * 1.7 + 3.0);
        vec3 sc = hs < 0.35 ? vec3(1.0, 0.12, 0.6) : (hs < 0.7 ? vec3(0.1, 0.85, 1.0) : (hs < 0.85 ? vec3(1.0, 0.75, 0.25) : vec3(0.5, 0.25, 1.0)));
        float streak = smoothstep(0.5, 0.15, abs(sf - 0.5)) * step(0.35, hash11(sid + 9.0)) * (0.6 + 0.4 * sin(uTime * (1.0 + 2.0 * hs) + sid));
        float wetF = 0.08 + 0.92 * pow(1.0 - max(dot(normalize(vNrm), V), 0.0), 4.0);
        emit = (sc * streak * 0.55 + vec3(0.02, 0.16, 0.18) * 0.6) * mix(0.18, 1.0, pud) * wetF;
        albedo *= 0.6;
        rough = mix(0.5, 0.04, pud); coat = mix(0.12, 0.85, pud); metal = 0.0;
    } else if (uMat == 17) {    // jungle moss: layered greens, damp sheen
        float m1 = vnoise3(P * 37.0 + vec3(5.65, 3.55, 4.65));
        float m2 = vnoise3(P * 212.3 + vec3(12.96, 6.72, 1.36));
        albedo *= 0.5 + 0.9 * m1;
        albedo = mix(albedo, albedo * vec3(1.3, 1.25, 0.6), smoothstep(0.55, 0.8, m2));
        hgt = (m2 - 0.5) * 0.0010 + (m1 - 0.5) * 0.0006;
        rough = 0.5 + 0.3 * m2; coat = 0.25 + 0.15 * sin(uTime * 0.7 + m1 * 6.0);
    } else if (uMat == 21) {    // carved stone: weathered blocks
        float n1 = vnoise3(P * 55.9 + vec3(7.27, 9.89, 7.07));
        float n2 = vnoise3(P * 346.9 + vec3(1.58, 2.06, 3.78));
        float lawn = smoothstep(0.08, 0.2, vCol.g - vCol.r);                // castle courtyards: grass
        float plank = smoothstep(0.12, 0.26, vCol.r - vCol.b) * (1.0 - lawn); // turret slides: polished boards
        albedo *= mix(0.62 + 0.55 * n1 + 0.18 * (n2 - 0.5), 1.0, max(lawn, plank));
        hgt = ((n1 - 0.5) * 0.0008 + (n2 - 0.5) * 0.0003) * (1.0 - max(lawn, plank));
        rough = 0.86;
        if (lawn > 0.0) {
            float blade = vnoise3(P * 900.0); float clump = vnoise3(P * 60.0 + 4.0);
            albedo = mix(albedo, albedo * (0.6 + 0.6 * blade) * (0.8 + 0.4 * clump), lawn);
            hgt += (blade - 0.5) * 0.0005 * lawn;
        }
        if (plank > 0.0) {
            vec3 an = abs(normalize(vNL));
            vec2 sq = (an.x >= an.y && an.x >= an.z) ? P.yz : (an.y >= an.z ? P.xz : P.xy);
            float board = fract(sq.x * 90.0); float bid = floor(sq.x * 90.0);
            float grain = vnoise3(vec3(sq.x * 90.0, sq.y * 6.0, bid));
            albedo = mix(albedo, albedo * (0.8 + 0.3 * hash11(bid)) * (0.85 + 0.25 * grain) * (1.0 - 0.45 * smoothstep(0.92, 1.0, board)), plank);
            rough = mix(rough, 0.25, plank); coat = max(coat, plank * 0.7);   // waxed smooth
        }
        if (uHiero > 0.5) {
            vec3 an = abs(normalize(vNL));
            vec2 sq = (an.x >= an.y && an.x >= an.z) ? P.yz : (an.y >= an.z ? P.xz : P.xy);
            vec4 g = hiero(sq * 34.0);
            albedo = mix(albedo, pow(g.rgb, vec3(2.2)) * (0.85 + 0.3 * n1), g.a * 0.92);    // painted, a little worn
            hgt -= g.a * 0.00035;                                                        // and lightly carved
        }
    } else if (uMat == 23) {    // snow-capped hedge: dense leaf clumps with powder on top
        float leaf = vnoise3(P * 246.3 + vec3(8.89, 5.23, 0.49));
        float clump = vnoise3(P * 54.6 + vec3(3.20, 8.40, 6.20));
        float snowK = uHedgeSnow * smoothstep(0.72, 0.86, clump * 0.55 + leaf * 0.25 + vCol.g * 0.6);
        vec3 foliage = albedo * (0.45 + 1.0 * leaf) * (0.65 + 0.7 * clump);
        vec3 powder = vec3(0.62, 0.68, 0.8) * (0.85 + 0.2 * leaf);
        albedo = mix(foliage, powder, snowK);
        hgt = mix((leaf - 0.5) * 0.0010 + (clump - 0.5) * 0.0016, (clump - 0.5) * 0.0010, snowK);
        rough = mix(0.93, 0.8, snowK);
    } else if (uMat == 24) {    // forest-floor leaf litter: overlapping gold, orange and rust
        float la = vnoise3(P * 74.2 + vec3(10.51, 0.57, 2.91));
        float lb = vnoise3(P * 227.9 + vec3(4.82, 3.74, 8.62));
        float lc = vnoise3(P * 567.4 + vec3(12.13, 6.91, 5.33));
        vec3 leafA = vec3(0.62, 0.26, 0.05), leafB = vec3(0.86, 0.55, 0.09), leafC = vec3(0.36, 0.10, 0.04);
        vec3 lcol = mix(mix(leafA, leafB, smoothstep(0.35, 0.65, la)), leafC, smoothstep(0.55, 0.82, lb));
        albedo = mix(albedo * 0.4, lcol * (0.5 + 0.45 * lc), 0.9);
        hgt = (lb - 0.5) * 0.0012 + (lc - 0.5) * 0.0005;
        rough = 0.82;
    } else if (uMat == 25) {    // toy world: a studded baseplate in bright primary patches
        vec3 an = abs(normalize(vNL));
        vec2 sq = (an.x >= an.y && an.x >= an.z) ? P.yz : (an.y >= an.z ? P.xz : P.xy);   // round studs: project along the dominant axis
        vec2 q = sq * uStud.x;
        vec2 fp = fract(q) - 0.5;
        float stud = smoothstep(0.36, 0.30, length(fp));
        float hp = hash21(floor(q * 0.18));
        vec3 pcol = hp < 0.25 ? vec3(0.9, 0.12, 0.1) : hp < 0.5 ? vec3(0.1, 0.35, 0.9) : hp < 0.75 ? vec3(0.98, 0.78, 0.08) : vec3(0.1, 0.65, 0.25);
        albedo = mix(albedo, pcol * 0.8, 0.55);
        albedo *= 0.86 + 0.26 * stud;
        albedo *= 1.0 - 0.25 * smoothstep(0.30, 0.40, length(fp)) * (1.0 - stud);
        hgt = 0.0;
        rough = 0.28; coat = 0.7;
        if (uShell > 0.0) {                                  // the studs are real: each shell layer keeps a slightly smaller disc, lifted off the plate
            float r = length(fp);
            if (r > 0.40 - 0.08 * uShell) discard;
            albedo *= 0.92 + 0.22 * uShell;
            hgt = -r * uStud.y * 1.4;                         // a domed top and rounded flank for the lighting
            rough = 0.22;
        }
    } else if (uMat == 26) {    // craft felt with stitching
        vec2 q = vec2(dot(P, vec3(30.0, 18.0, 24.0)), dot(P, vec3(-16.0, 29.0, 20.0)));
        float fuzz = vnoise(q * 9.0) * 0.6 + vnoise(q * 23.0) * 0.4;
        float weave = 0.5 + 0.5 * sin(q.x * 40.0) * sin(q.y * 40.0);
        albedo *= 0.8 + 0.3 * fuzz;
        float lineX = smoothstep(0.04, 0.0, abs(fract(q.x * 0.35) - 0.5) - 0.45) * step(0.5, fract(q.y * 4.0));
        float lineY = smoothstep(0.04, 0.0, abs(fract(q.y * 0.35) - 0.5) - 0.45) * step(0.5, fract(q.x * 4.0));
        albedo = mix(albedo, vec3(0.92, 0.82, 0.62), max(lineX, lineY) * 0.85);
        hgt = (fuzz - 0.5) * 0.0007 + weave * 0.0002;
        rough = 0.97; coat = 0.0;
    } else if (uMat == 27) {    // prehistoric ground: cracked ochre earth with pebbles
        float a = vnoise3(P * 48.3 + vec3(6.44, 10.08, 2.04));
        float b = vnoise3(P * 158.9 + vec3(0.75, 2.25, 7.75));
        float crack = smoothstep(0.045, 0.0, abs(a - 0.5));
        albedo *= 0.65 + 0.7 * b;
        albedo = mix(albedo, albedo * vec3(0.5, 0.62, 0.35), smoothstep(0.55, 0.8, vnoise3(P * 19.0 + vec3(8.06, 5.42, 4.46))) * 0.6);
        albedo *= 1.0 - 0.6 * crack;
        hgt = (b - 0.5) * 0.0012 - crack * 0.0012;
        rough = 0.9;
    } else if (uMat == 28) {    // diamond-plate steel with oil stains
        vec2 q = vec2(dot(P, vec3(44.0, 26.0, 36.0)), dot(P, vec3(-24.0, 42.0, 28.0)));
        vec2 fp = fract(q) - 0.5;
        float chk = mod(floor(q.x) + floor(q.y), 2.0);
        vec2 sc = chk < 0.5 ? vec2(1.0, 3.2) : vec2(3.2, 1.0);
        float bump = smoothstep(0.34, 0.2, length(fp * sc * 0.75));
        float stain = smoothstep(0.55, 0.8, vnoise3(P * 16.1 + vec3(2.37, 8.59, 1.17)));
        albedo *= 0.8 + 0.3 * bump;
        albedo = mix(albedo, albedo * vec3(0.35, 0.33, 0.4), stain * 0.6);
        hgt = bump * 0.0016;
        metal = 0.85; rough = mix(0.33, 0.12, stain);
    } else if (uMat == 29) {    // cooling lava crust: dark basalt plates split by glowing cracks; the plates drift and the cracks breathe
        vec3 q = P * uLava.x;
        float t = uTime * 0.26;
        vec3 wp = vec3(vnoise3(q * 0.9 + vec3(t, 0.0, 0.0)), vnoise3(q * 0.9 + vec3(5.0, -t, 0.0)), vnoise3(q * 0.9 + vec3(9.0, 3.0, t * 0.7)));
        if (uLava.y > 0.6) wp = wp * 0.67 + 0.33 * vec3(vnoise3(q * 1.9 + vec3(2.0, t * 1.3, 0.0)), vnoise3(q * 1.9 + vec3(7.0, 0.0, -t * 1.3)), vnoise3(q * 1.9 + vec3(t, 11.0, 0.0)));
        q += (wp - 0.5) * 0.55;                                  // slow flow: plates stretch and drift
        vec3 cn = floor(q), cf = fract(q);
        float d1 = 8.0, d2 = 8.0, id = 0.0;
        for (int k = -1; k <= 1; k++) for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
            vec3 g = vec3(float(i), float(j), float(k));
            vec3 h = hash33(cn + g);
            vec3 r = g + h - cf;
            float d = dot(r, r);
            if (d < d1) { d2 = d1; d1 = d; id = h.x; } else if (d < d2) d2 = d;
        }
        float edge = sqrt(d2) - sqrt(d1);                       // distance to the nearest crack
        const float cw = 0.18;
        float aa = fwidth(edge);
        float crack = 1.0 - smoothstep(0.0, cw + aa, edge);
        crack *= crack;
        crack = mix(crack, cw * 0.3, smoothstep(cw * 0.6, cw * 1.6, aa));   // far away: settle to an even glow instead of shimmering
        float pulse = 0.75 + 0.25 * sin(uTime * 2.1 + id * 20.0);           // each plate breathes on its own rhythm
        float mott = vnoise3(q * 4.0 + id * 7.0) * 0.65 + vnoise3(q * 9.0 + 3.0) * 0.35 * mix(0.5, 1.0, fade3(P));
        vec3 crust = mix(vec3(0.0050, 0.0010, 0.0005), vec3(0.039, 0.0090, 0.0040), clamp(mott * 1.2, 0.0, 1.0)) * 1.3;
        albedo = mix(crust, vec3(0.01, 0.003, 0.001), crack);
        vec3 heat = lavaRamp(0.45 + crack * 0.55) * pulse;
        emit = (heat * crack + vec3(1.0, 0.64, 0.116) * crack * crack * 0.55) * 1.25
             + vec3(1.0, 0.101, 0.0037) * exp(-edge * 5.0) * 0.12 * pulse;   // heat glow bleeding onto plate edges
        hgt = (mott - 0.5) * 0.0005 - crack * 0.0016;          // crust sits proud of the cracks
        rough = mix(0.72, 0.3, crack);
    } else if (uMat == 30) {    // jelly: glossy gel in colour blobs with an inner glow, rippling away from the ball
        float ja = vnoise3(P * 35.0 + vec3(9.68, 0.76, 6.88));
        albedo = mix(albedo, pow(uFloor2, vec3(2.2)), smoothstep(0.42, 0.62, ja));
        float jd = uMarbleR > 0.0 ? length(vWorld - uMarbleL) / uMarbleR : 30.0;
        float rip = sin(jd * 2.6 - uTime * 7.0) * exp(-jd * 0.28) * smoothstep(0.0, 1.5, jd);
        float idle = sin(dot(P, vec3(70.0, 50.0, 60.0)) + uTime * 2.0) * 0.15;
        hgt = (rip * 0.35 + idle) * (uMarbleR > 0.0 ? uMarbleR : 0.02) * 0.1;
        emit = albedo * (0.16 + 0.1 * ja);
        rough = 0.08; coat = 1.0;
    } else if (uMat == 31) {    // French fries: golden potato with a fluffy-crisp surface, toasted flecks and salt crystals
        vec3 an = abs(normalize(vNL));
        vec2 sq = (an.x >= an.y && an.x >= an.z) ? P.yz : (an.y >= an.z ? P.xz : P.xy);
        float crisp = vnoise(sq * 140.0) * 0.6 + vnoise(sq * 380.0) * 0.4;
        float toast = smoothstep(0.62, 0.85, vnoise(sq * 55.0 + 3.0));
        albedo *= 0.82 + 0.3 * crisp;
        albedo = mix(albedo, albedo * vec3(0.72, 0.5, 0.28), toast * 0.7);
        float salt = step(0.993, hash21(floor(sq * 2200.0))) * fade3(P);
        albedo = mix(albedo, vec3(0.95), salt * 0.6);
        emit = vec3(1.0) * salt * 0.12;
        hgt = (crisp - 0.5) * 0.0008 - toast * 0.0003;
        rough = 0.62 - 0.25 * salt; coat = 0.25;                 // a light sheen of oil
    } else if (uMat == 32) {    // neon city blocks: dark glass and steel, rows of lit windows, LED strips; the neon tube on top glows from its vertex colour
        vec3 an = abs(normalize(vNL));
        vec2 wv = (an.x >= an.y && an.x >= an.z) ? P.yz : (an.y >= an.z ? P.xz : P.xy);
        float k = uLava.x * 3.2;
        vec2 wq = wv * vec2(k, k * 1.7);
        vec2 wi = floor(wq), wf = fract(wq);
        float hw = hash21(wi + 31.0);
        float pane = step(abs(wf.x - 0.5), 0.32) * step(abs(wf.y - 0.5), 0.26);
        float lit = step(0.58, hw) * pane;
        vec3 wc = hw > 0.9 ? vec3(1.0, 0.25, 0.75) : (hw > 0.78 ? vec3(0.2, 0.9, 1.0) : vec3(1.0, 0.72, 0.36));
        float onWall = 1.0 - smoothstep(0.55, 0.9, max(vCol.r, max(vCol.g, vCol.b)));      // not on the glowing top tube
        float flick = 0.75 + 0.25 * step(0.2, fract(uTime * (0.3 + hw) + hw * 7.0));
        float led = smoothstep(0.06, 0.0, abs(fract(wv.x * k * 0.25 + 0.5) - 0.5)) * step(0.5, hash21(vec2(floor(wv.x * k * 0.25 + 0.5), 3.0)));
        albedo = mix(albedo, albedo * 0.55, pane);
        emit = (wc * lit * flick * 0.9 + vec3(0.1, 0.9, 1.0) * led * 0.8) * onWall;
        hgt = -pane * 0.0006;
        rough = mix(0.35, 0.06, pane); coat = 0.6; metal = 0.3;
    } else if (uMat == 22) {    // theme props: vertex colour is the paint, anything above 1.0 glows
        float vm = max(vCol.r, max(vCol.g, vCol.b));
        vec3 baseC = vm > 1.0 ? vCol / vm : vCol;                // keep the hue of a glowing colour instead of clipping it to white
        albedo = pow(baseC * 0.8, vec3(2.2));
        emit = vm > 1.0 ? pow(baseC, vec3(2.2)) * (vm - 1.0) * 1.9 : vec3(0.0);
        rough = 0.5; coat = 0.45;
    } else if (uMat == 0) {     // lacquer / enamel
        coat = 1.0;
    }

    if (uMat == 4) {            // markers and the thread: glowing, unlit
        vec3 e = vCol * pow(vec3(1.0), vec3(2.2)) * uEmissive * (1.0 + 0.2 * sin(uTime * 3.0));
        vec3 col = albedo * (0.25 + 0.75 * max(dot(N, uLightDir), 0.0)) * 0.6 + pow(vCol, vec3(2.2)) * uEmissive * (1.0 + 0.2 * sin(uTime * 3.0)) * 2.2;
        outColor = vec4(finish(col, gl_FragCoord.xy), uAlpha);
        return;
    }

    if ((uMat >= 1 && uMat <= 3) || uMat >= 5) N = bumpN(N, vWorld, hgt);
    float sandSh = 1.0;
    if (uSandOn > 0.5 && vTile.z >= 0.0 && (uMat == 5 || uMat == 6)) {
        // dug grooves and pushed-up banks from the simulated height field
        float cellId = vTile.z;
        vec2 uv = vTile.xy;
        float e = 1.0 / uSandP.x;
        float h0 = sandH(uv, cellId);
        float hu = (sandH(uv + vec2(e, 0.0), cellId) - sandH(uv - vec2(e, 0.0), cellId)) / (2.0 * e);
        float hv = (sandH(uv + vec2(0.0, e), cellId) - sandH(uv - vec2(0.0, e), cellId)) / (2.0 * e);
        vec3 dpx = dFdx(vWorld), dpy = dFdy(vWorld);
        vec2 ux = dFdx(uv), uy = dFdy(uv);
        float det = ux.x * uy.y - ux.y * uy.x;
        if (abs(det) > 1e-12) {
            vec3 Pu = (dpx * uy.y - dpy * ux.y) / det;
            vec3 Pv = (dpy * ux.x - dpx * uy.x) / det;
            vec3 Tu = normalize(Pu), Tv = normalize(Pv);
            N = normalize(N - (Tu * hu + Tv * hv) * (uMat == 5 ? 3.6 : 3.0));
            // banks throw soft shadows: march toward the light across the height field
            vec3 Lp = uLightDir - N * dot(uLightDir, N);
            float lz = max(dot(uLightDir, N), 0.05);
            float lpl = max(length(Lp), 0.05);
            vec3 Ln = Lp / lpl;
            vec2 dirUV = normalize(vec2(dot(Ln, Tu) / max(length(Pu), 1e-5), dot(Ln, Tv) / max(length(Pv), 1e-5)) + 1e-6);
            float tanE = lz / lpl;
            float occl = 0.0;
            for (int k = 1; k <= 6; k++) {
                float dist = 0.05 * float(k);
                float hs = sandH(uv + dirUV * dist, cellId);
                occl = max(occl, smoothstep(0.0, 0.012, hs - h0 - dist * tanE));
            }
            sandSh = 1.0 - 0.35 * occl;
        }
        float groove = smoothstep(0.0, -0.045, h0);
        float bank = smoothstep(0.004, 0.03, h0);
        float cavity = 1.0 - 0.18 * groove;
        albedo *= (uMat == 5 ? mix(1.0, 0.9, groove) : mix(1.0, 0.65, groove)) * mix(1.0, 1.1, bank) * cavity;
        if (uMat == 5) { float cold = smoothstep(0.55, 0.75, dot(albedo, vec3(0.333))); albedo = mix(albedo, albedo * mix(vec3(1.08, 0.9, 0.7), vec3(0.8, 0.92, 1.08), cold), groove * 0.7); rough = 0.96; coat = 0.0; }
        else { rough = mix(rough, 0.18, groove); coat = max(coat, groove); }
    }
    if (uWet > 0.0 && uMat != 4) {                         // rain: darker, glossier surfaces
        albedo *= mix(1.0, 0.7, uWet);
        rough = mix(rough, rough * 0.45, uWet);
        coat = max(coat, uWet * 0.9);
    }
    {   // specular anti-aliasing: bumpy or sharply curved surfaces get a little rougher so highlights stop shimmering
        vec3 dnx = dFdx(N), dny = dFdy(N);
        float variance = 0.25 * (dot(dnx, dnx) + dot(dny, dny));
        rough = sqrt(rough * rough + min(2.0 * variance, 0.18));
    }
    float NoV = max(dot(N, V), 1e-3);
    float a = max(rough * rough, 0.015);
    vec3 F0 = mix(vec3(0.04), albedo, metal);
    vec3 diffC = albedo * (1.0 - metal);
    float sh = shadowAt(vWorld, N) * sandSh;

    // key light
    vec3 L = uLightDir;
    vec3 H = normalize(L + V);
    float NoL = max(dot(N, L), 0.0);
    float NoH = max(dot(N, H), 0.0);
    float VoH = max(dot(V, H), 0.0);
    vec3 keyC = mix(vec3(1.0, 0.94, 0.86) * 3.4, vec3(0.30, 0.42, 0.9) * 0.3, uNight);
    vec3 col = (diffC / PI + ggx(NoH, a) * visSmith(NoV, NoL, a) * fresnel(F0, VoH)) * keyC * NoL * sh * PI * 0.5;
    // clear coat: thin glossy layer on top
    if (coat > 0.0) col += coat * 0.04 * ggx(NoH, 0.06) * visSmith(NoV, NoL, 0.06) * (0.04 + 0.96 * pow(1.0 - VoH, 5.0)) * keyC * NoL * sh * PI * 0.5;

    // cool fill
    vec3 L2 = normalize(vec3(-0.85, 0.15, 0.5));
    float NoL2 = max(dot(N, L2), 0.0);
    col += diffC / PI * vec3(0.35, 0.5, 0.85) * NoL2 * 1.4;

    // image-based ambient: diffuse + specular
    vec3 R = reflect(-V, N);
    vec3 env = envMap(R, rough);
    vec3 spec = env * envBRDF(F0, rough, NoV);
    if (coat > 0.0) spec += coat * envMap(R, 0.04) * (0.04 + 0.96 * pow(1.0 - NoV, 5.0)) * 0.9;
    float occ = mix(1.0, 0.55 + 0.45 * sh, 0.6);  // shadowed areas also lose some specular sky light
    col += irradiance(N) * diffC * mix(1.0, 0.28, uNight) * mix(vec3(1.0), vec3(0.4, 0.55, 1.1), uNight) + spec * occ * mix(1.0, 0.3, uNight);
    if (uLampGain > 0.0) {                                    // light from lamps and glowing props: coloured pools on the floor, walls and props nearby
        for (int i = 0; i < 12; i++) {
            if (uLamp[i].w <= 0.0) continue;
            vec3 dl = uLamp[i].xyz - vLocal;
            float dd = length(dl);
            float x = dd / uLamp[i].w;
            float att = 1.0 / (1.0 + 9.0 * x * x) * (1.0 - smoothstep(0.6, 1.0, x));
            vec3 ld = dl / max(dd, 1e-5);
            float nl = max(dot(N, ld), 0.0) * 0.8 + 0.2;
            vec3 hl = normalize(ld + V);
            float sp = pow(max(dot(N, hl), 0.0), mix(12.0, 90.0, 1.0 - rough)) * (1.0 - rough) * 0.6;
            col += ((diffC * 2.0 + 0.02) * nl + F0 * sp) * uLampC[i] * att * 0.55 * uLampGain;
        }
    }
    // rim light
    col += pow(1.0 - NoV, 3.5) * vec3(0.10, 0.12, 0.16) * (0.4 + 0.6 * metal);
    col += emit;
    if (uMarbleR > 0.0) {                                     // the glass marble lights its surroundings and sits in its own shadow
        vec3 Lm = uMarbleL - vWorld;
        float dm2 = dot(Lm, Lm);
        float r2 = uMarbleR * uMarbleR;
        float att = 1.0 / (1.0 + dm2 / (r2 * 7.0));
        col += diffC * uMarbleC * att * 0.45 * max(dot(N, normalize(Lm)), 0.0);
        col *= 1.0 - 0.42 * exp(-dm2 / (r2 * 6.0));
    }
    if (uFog > 0.0) {                                         // haze; near lamps and signs it glows in their colour
        vec3 fc = uFogCol;
        if (uLampGain > 0.0) for (int i = 0; i < 12; i++) {
            if (uLamp[i].w <= 0.0) continue;
            float x = length(uLamp[i].xyz - vLocal) / uLamp[i].w;
            fc += uLampC[i] * 0.22 / (1.0 + 14.0 * x * x);
        }
        col = mix(col, fc, 1.0 - exp(-length(uCam - vWorld) * uFog));
    }

    outColor = vec4(finish(col, gl_FragCoord.xy), 1.0);
}
"""

    const val MARBLE_VS = """#version 300 es
uniform mat4 uVP;
uniform mat4 uModel;
uniform vec3 uCenter;
uniform float uRadius;
layout(location=0) in vec3 aPos;
out vec3 vWorld;
out vec3 vNrmW;
out vec3 vNrmL;
void main() {
    vec3 lp = uCenter + aPos * uRadius;
    vec4 w = uModel * vec4(lp, 1.0);
    vWorld = w.xyz;
    vNrmW = mat3(uModel) * aPos;
    vNrmL = aPos;
    gl_Position = uVP * w;
}
"""

    const val MARBLE_FS = """#version 300 es
precision highp float;
uniform vec3 uCam;
uniform vec3 uAccent;
uniform mat3 uBody;
uniform float uGhost;
uniform samplerCube uCube;
uniform float uCubeOn;
uniform float uDirt;
uniform vec3 uDirtCol;
uniform int uSkin;
uniform float uTime;
in vec3 vWorld;
in vec3 vNrmW;
in vec3 vNrmL;
out vec4 outColor;
""" + COMMON + """
// Studio lighting blended with the live reflection of the maze around the marble.
// The balls see a dim sky and the sun only: the studio's big white fill and back softboxes used to paint a pale
// sheen across every ball, which washed out their colours.
vec3 ballEnv(vec3 r, float rough) {
    float up = r.y * 0.5 + 0.5;
    vec3 c = mix(vec3(0.03, 0.032, 0.04), vec3(0.30, 0.33, 0.40), smoothstep(0.05, 1.0, up));
    c = mix(c, vec3(0.15, 0.16, 0.18), rough * 0.5);
    float w = 0.02 + rough * rough * 0.7;
    float k = 1.0 / (1.0 + rough * rough * 14.0);
    return c + vec3(5.5, 5.2, 4.8) * k * smoothstep(0.95 - w, 0.975 + w * 0.2, dot(r, uLightDir));
}
vec3 surroundings(vec3 d, float rough) {
    vec3 st = ballEnv(d, rough);
    if (uCubeOn > 0.5) {
        vec4 c = texture(uCube, d);
        st = mix(st, pow(c.rgb, vec3(2.2)), c.a);
    }
    return st;
}

float n3(vec3 p, float k) { return (vnoise(p.xy * k) + vnoise(p.yz * k + 3.1) + vnoise(p.zx * k + 7.7)) * 0.3333; }
float fb3(vec3 p, float k) { return 0.55 * n3(p, k) + 0.3 * n3(p, k * 2.1) + 0.15 * n3(p, k * 4.3); }

// Opaque, themed ball: pattern lives in body space so it rolls with the ball.
vec3 shadeBall(vec3 alb, float rough, float metal, vec3 emit, float coat, vec3 N, vec3 V) {
    float NoV = max(dot(N, V), 1e-3);
    vec3 F0 = mix(vec3(0.04), alb, metal);
    vec3 diff = alb * (1.0 - metal);
    float NoL = max(dot(N, uLightDir), 0.0);
    vec3 col = diff * vec3(1.0, 0.94, 0.86) * 3.4 * NoL * 0.5 + diff * irradiance(N) * 1.1;
    vec3 R = reflect(-V, N);
    col += surroundings(R, rough) * envBRDF(F0, rough, NoV);
    if (coat > 0.0) col += surroundings(R, 0.04) * (0.04 + 0.96 * pow(1.0 - NoV, 5.0)) * coat;
    vec3 H = normalize(uLightDir + V);
    col += pow(max(dot(N, H), 0.0), mix(60.0, 700.0, 1.0 - rough)) * vec3(2.5) * (0.3 + 0.7 * (1.0 - rough));
    return col + emit;
}

vec3 over(vec3 base, vec4 l) { return mix(base, l.rgb, l.a); }

// a flower seen from above: [n] petals out to radius [r], gradient from [ca] (centre) to [cb] (tips), disc [cc] in the middle
vec4 petalFlower(vec2 q, float n, float r, vec3 ca, vec3 cb, vec3 cc) {
    float d = length(q);
    float a = atan(q.y, q.x);
    float edge = r * (0.64 + 0.36 * pow(abs(cos(a * n * 0.5)), 0.7));
    float m = smoothstep(edge, edge - 0.012, d);
    vec3 c = mix(ca, cb, smoothstep(0.1 * r, r, d));
    float mid = smoothstep(0.07, 0.0, abs(sin(a * n * 0.5))) * smoothstep(0.2 * r, 0.55 * r, d);
    c *= 1.0 - 0.45 * mid;
    c = mix(c, cc, smoothstep(0.2 * r, 0.16 * r, d));
    return vec4(c, m);
}

vec4 leafShape(vec2 q, vec2 ctr, float ang, float len, float wid, vec3 col) {
    vec2 d = q - ctr;
    vec2 r = vec2(d.x * cos(ang) + d.y * sin(ang), -d.x * sin(ang) + d.y * cos(ang));
    float e = length(r / vec2(len, wid));
    float m = smoothstep(1.0, 0.9, e);
    vec3 c = mix(col, col * 0.55, smoothstep(0.05, 0.0, abs(r.y)) * 0.8);
    return vec4(c, m);
}

vec3 sugarSkull(vec3 p) {
    const vec3 NAVY = vec3(0.04, 0.04, 0.14), MAG = vec3(0.88, 0.10, 0.45), ORG = vec3(1.0, 0.45, 0.05), YEL = vec3(1.0, 0.74, 0.10);
    const vec3 CYAN = vec3(0.05, 0.68, 0.82), GRN = vec3(0.20, 0.58, 0.20), PINK = vec3(0.93, 0.16, 0.55);
    vec3 c = vec3(0.97, 0.96, 0.93);
    vec2 q = p.xy;
    if (p.z < 0.0) {                                       // back: marigold rosette ringed with dots
        vec2 b = vec2(-p.x, p.y);
        vec2 g = b * 12.0; vec2 ip = floor(g); vec2 fp = fract(g) - 0.5;
        float h = hash21(ip);
        vec3 dc = h < 0.25 ? CYAN : h < 0.5 ? ORG : h < 0.75 ? PINK : GRN;
        c = mix(c, dc, smoothstep(0.2, 0.12, length(fp)) * step(0.35, hash21(ip + 5.0)));
        c = over(c, petalFlower(b, 16.0, 0.62, ORG, YEL, PINK));
        c = over(c, petalFlower(b, 8.0, 0.3, MAG, PINK, YEL));
        return c;
    }
    // scattered coloured dots over the white skull
    vec2 g = q * 15.0; vec2 ip = floor(g); vec2 fp = fract(g) - 0.5;
    float h = hash21(ip);
    vec3 dc = h < 0.25 ? CYAN : h < 0.5 ? ORG : h < 0.75 ? PINK : GRN;
    float dotM = smoothstep(0.17, 0.1, length(fp - (vec2(hash21(ip + 1.0), hash21(ip + 2.0)) - 0.5) * 0.4)) * step(0.45, hash21(ip + 7.0));
    c = mix(c, dc, dotM);
    // cyan scrolls beside the brow, green leaves, side flowers
    for (int s = 0; s < 2; s++) {
        float sg = s == 0 ? -1.0 : 1.0;
        vec2 qs = vec2(q.x * sg, q.y);
        c = mix(c, CYAN, smoothstep(0.016, 0.0, abs(length(qs - vec2(0.42, 0.42)) - 0.085)) * step(0.0, qs.x - 0.34));
        c = mix(c, CYAN, smoothstep(0.014, 0.0, abs(length(qs - vec2(0.55, 0.22)) - 0.06)) * step(qs.y, 0.26));
        c = over(c, leafShape(qs, vec2(0.30, 0.55), 0.35, 0.13, 0.045, GRN));
        c = over(c, leafShape(qs, vec2(0.62, 0.5), 1.1, 0.11, 0.04, GRN));
        c = over(c, petalFlower(qs - vec2(0.74, 0.28), 7.0, 0.17, PINK, MAG, YEL));
        c = over(c, petalFlower(qs - vec2(0.66, -0.3), 6.0, 0.13, MAG, PINK, YEL));
        c = over(c, leafShape(qs, vec2(0.5, -0.48), -0.9, 0.12, 0.04, GRN));
    }
    // forehead marigold
    c = over(c, petalFlower(q - vec2(0.0, 0.58), 14.0, 0.2, YEL, ORG, PINK));
    // eye sockets: dotted marigold rim, magenta ring, dark centre with a little mandala
    for (int s = 0; s < 2; s++) {
        float sg = s == 0 ? -1.0 : 1.0;
        vec2 e = q - vec2(0.34 * sg, 0.04);
        float d = length(e);
        float a = atan(e.y, e.x);
        float rim = smoothstep(0.34, 0.33, d) * smoothstep(0.255, 0.265, d);
        vec3 rimC = mix(ORG, YEL, step(0.5, fract(a * 11.0 / 6.2832)));
        c = mix(c, rimC, rim * (0.55 + 0.45 * smoothstep(0.0, 0.5, abs(sin(a * 11.0)))));
        c = mix(c, MAG, smoothstep(0.012, 0.0, abs(d - 0.25)));
        c = mix(c, NAVY, smoothstep(0.236, 0.226, d));
        c = over(c, petalFlower(e, 10.0, 0.17, ORG, ORG * 1.1, CYAN) * vec4(1.0, 1.0, 1.0, step(d, 0.2)));
        c = mix(c, CYAN, smoothstep(0.012, 0.0, abs(d - 0.205)) * step(d, 0.226));
        c = over(c, petalFlower(e, 8.0, 0.09, CYAN, vec3(0.3, 0.85, 0.9), YEL) * vec4(1.0, 1.0, 1.0, step(d, 0.2)));
    }
    // nose: a dark upturned heart with a small flower
    {
        vec2 n = q - vec2(0.0, -0.13);
        float hw = 0.12 * (1.0 - (n.y + 0.12) / 0.27) * step(-0.12, n.y) * step(n.y, 0.15) + 0.12 * step(n.y, -0.12) * step(-0.17, n.y) * (1.0 - smoothstep(-0.17, -0.12, n.y) * 0.0);
        float inside = step(abs(n.x), hw) * step(-0.17, n.y) * step(n.y, 0.15);
        float rimN = step(abs(n.x), hw + 0.022) * step(-0.19, n.y) * step(n.y, 0.17);
        c = mix(c, ORG, rimN * (1.0 - inside));
        c = mix(c, NAVY, inside);
        c = over(c, petalFlower(n - vec2(0.0, -0.02), 7.0, 0.065, ORG, YEL, YEL));
    }
    // grin: magenta lips and two rows of teeth
    {
        float sd = max(abs(q.x) - 0.31, abs(q.y + 0.46) - 0.1);
        float lip = smoothstep(0.03, 0.01, abs(sd - 0.0)) ;
        c = mix(c, MAG, smoothstep(0.036, 0.02, abs(sd + 0.004)));
        float teeth = step(sd, -0.016);
        float gap = smoothstep(0.012, 0.0, abs(fract(q.x / 0.052 + 0.5) - 0.5) * 0.052 - 0.0) * 0.0 + smoothstep(0.0075, 0.0, abs(fract(q.x / 0.052) - 0.5) * 0.052 - 0.0245);
        float row = smoothstep(0.008, 0.0, abs(q.y + 0.46));
        c = mix(c, vec3(0.99), teeth);
        c = mix(c, NAVY * 2.0, teeth * max(smoothstep(0.42, 0.48, abs(fract(q.x / 0.052) - 0.5)), row));
    }
    // bottom flower and cheek scrolls
    c = over(c, petalFlower(q - vec2(0.0, -0.78), 9.0, 0.16, ORG, MAG, YEL));
    c = mix(c, CYAN, smoothstep(0.016, 0.0, abs(length(q - vec2(0.2, -0.7)) - 0.07)) * step(0.14, q.x));
    c = mix(c, CYAN, smoothstep(0.016, 0.0, abs(length(q - vec2(-0.2, -0.7)) - 0.07)) * step(q.x, -0.14));
    // thin dark outline near the limb
    c = mix(c, NAVY, smoothstep(0.93, 0.985, length(q)) * 0.9);
    return c;
}

vec3 skinColor(vec3 p, vec3 N, vec3 V) {
    float lat = p.z, lon = atan(p.y, p.x);
    if (uSkin == 1) {            // gobstopper: layered candy shells
        float t = p.z * 3.2 + sin(lon * 3.0 + p.z * 4.0) * 0.5;
        float band = floor(fract(t * 0.5) * 6.0);
        vec3 c = band < 1.0 ? vec3(1.0, 0.2, 0.45) : band < 2.0 ? vec3(1.0, 0.95, 0.9) : band < 3.0 ? vec3(0.2, 0.75, 1.0) : band < 4.0 ? vec3(1.0, 0.85, 0.2) : band < 5.0 ? vec3(0.55, 0.9, 0.35) : vec3(0.75, 0.4, 1.0);
        return shadeBall(pow(c, vec3(2.2)), 0.18, 0.0, vec3(0.0), 1.0, N, V);
    }
    if (uSkin == 2) {            // sugar skull: a painted calavera face on one side, marigold rosette and dots on the other
        return shadeBall(pow(sugarSkull(p), vec3(2.2)), 0.3, 0.0, vec3(0.0), 0.85, N, V);
    }
    if (uSkin == 3) {            // tyre: black rubber, zig-zag tread around the equator, sidewall lettering ring
        float lt = abs(lat);
        float tread = smoothstep(0.75, 0.7, lt);
        float zig = abs(fract((lon / 6.2831853) * 18.0 + lat * 3.0 * sign(lat) + 0.5) - 0.5) * 2.0;
        float groove = smoothstep(0.55, 0.45, zig) * tread;
        float rib = smoothstep(0.04, 0.0, abs(lat)) * tread;
        vec3 c = mix(vec3(0.045), vec3(0.012), groove);
        float wall = smoothstep(0.72, 0.8, lt);
        float letters = step(0.5, fract(lon * 9.0 / 6.2831853 * 3.0)) * smoothstep(0.03, 0.0, abs(lt - 0.86)) * step(0.5, fract(lon * 40.0 / 6.2831853));
        c += vec3(0.8) * letters * wall;
        c += vec3(0.02) * rib;
        c = mix(c, vec3(0.5, 0.5, 0.55), smoothstep(0.96, 0.985, lt));          // wheel-rim hub
        return shadeBall(c, mix(0.75, 0.55, 1.0 - tread), mix(0.0, 0.9, smoothstep(0.96, 0.985, lt)), vec3(0.0), 0.0, N, V);
    }
    if (uSkin == 4) {            // football: truncated icosahedron pattern
        const float PHI = 1.6180340;
        vec3 d[12];
        d[0] = normalize(vec3(0.0, 1.0, PHI)); d[1] = normalize(vec3(0.0, -1.0, PHI)); d[2] = normalize(vec3(0.0, 1.0, -PHI)); d[3] = normalize(vec3(0.0, -1.0, -PHI));
        d[4] = normalize(vec3(1.0, PHI, 0.0)); d[5] = normalize(vec3(-1.0, PHI, 0.0)); d[6] = normalize(vec3(1.0, -PHI, 0.0)); d[7] = normalize(vec3(-1.0, -PHI, 0.0));
        d[8] = normalize(vec3(PHI, 0.0, 1.0)); d[9] = normalize(vec3(-PHI, 0.0, 1.0)); d[10] = normalize(vec3(PHI, 0.0, -1.0)); d[11] = normalize(vec3(-PHI, 0.0, -1.0));
        float m1 = -1.0, m2 = -1.0;
        for (int i = 0; i < 12; i++) { float x = dot(p, d[i]); if (x > m1) { m2 = m1; m1 = x; } else if (x > m2) m2 = x; }
        float pent = smoothstep(0.925, 0.94, m1);
        float seam = smoothstep(0.05, 0.0, m1 - m2) * (1.0 - pent);
        vec3 c = mix(vec3(0.93), vec3(0.03), pent);
        c = mix(c, vec3(0.25), seam * 0.9);
        return shadeBall(pow(c, vec3(2.2)) * 1.1, 0.42, 0.0, vec3(0.0), 0.9, N, V);
    }
    if (uSkin == 5) {            // plasma ball: dark glass globe with live lightning filaments
        vec3 q = p * 2.2 + vec3(0.0, 0.0, uTime * 0.25);
        float f = abs(fb3(q, 1.4) - 0.5);
        float arc = pow(smoothstep(0.045, 0.0, f), 1.5);
        float f2 = abs(fb3(q * 1.7 + 11.0, 1.2 + 0.2 * sin(uTime)) - 0.5);
        arc += 0.6 * pow(smoothstep(0.05, 0.0, f2), 1.5);
        float core = smoothstep(0.55, 0.0, length(p.xy * 0.0 + (N.xy * 0.0)) + 0.0) * 0.0;
        float NoV = max(dot(N, V), 0.0);
        vec3 glow = mix(vec3(0.5, 0.1, 1.0), vec3(0.2, 0.7, 1.0), fb3(p * 1.6 + uTime * 0.1, 1.0));
        vec3 emit = glow * (0.15 + 0.5 * pow(1.0 - NoV, 2.0)) * 0.6 + vec3(0.9, 0.8, 1.0) * arc * 3.2 + glow * arc * 2.0;
        return shadeBall(vec3(0.01, 0.005, 0.03), 0.08, 0.0, emit, 1.0, N, V);
    }
    if (uSkin == 6) {            // burger: sesame bun, lettuce, ketchup, melted cheese, grilled patty, tomato and the bottom bun
        float z = lat;
        float wav = sin(lon * 7.0) * 0.012 + sin(lon * 17.0) * 0.006;
        vec3 c; float rr = 0.55; float cc = 0.3;
        if (z > 0.36) {
            float t = fb3(p, 3.0);
            c = mix(vec3(0.84, 0.50, 0.16), vec3(0.60, 0.28, 0.07), t);
            vec2 g = vec2(lon * 6.0, z * 13.0);
            vec2 ip = floor(g), fp = fract(g) - 0.5;
            vec2 off = vec2(hash21(ip) - 0.5, hash21(ip + 9.0) - 0.5) * 0.45;
            float seed = hash21(ip + 3.0) > 0.4 ? smoothstep(0.30, 0.14, length((fp - off) * vec2(1.0, 1.9))) : 0.0;
            c = mix(c, vec3(0.97, 0.9, 0.7), seed);
        } else if (z > 0.27 + wav) {             // lettuce, ruffled
            c = mix(vec3(0.20, 0.55, 0.10), vec3(0.50, 0.82, 0.22), 0.5 + 0.5 * sin(lon * 16.0 + z * 40.0));
            rr = 0.4; cc = 0.6;
        } else if (z > 0.2 + wav) {              // ketchup
            c = vec3(0.78, 0.05, 0.04); rr = 0.15; cc = 1.0;
        } else if (z > 0.07 + 0.06 * (0.5 + 0.5 * sin(lon * 5.0 + 1.0))) {   // cheese with drips
            c = vec3(1.0, 0.76, 0.10); rr = 0.3; cc = 0.8;
        } else if (z > -0.18) {                  // patty with grill marks
            float t = fb3(p, 8.0);
            c = vec3(0.24, 0.11, 0.05) * (0.6 + 0.8 * t);
            float grill = smoothstep(0.05, 0.0, abs(fract(lon * 4.0 + z * 6.0) - 0.5) - 0.38);
            c = mix(c, vec3(0.06, 0.03, 0.02), grill * 0.7);
            rr = 0.75; cc = 0.1;
        } else if (z > -0.25) {                  // tomato slice
            c = vec3(0.82, 0.18, 0.10); rr = 0.35; cc = 0.7;
        } else {                                 // bottom bun
            float t = fb3(p, 3.0);
            c = mix(vec3(0.86, 0.58, 0.22), vec3(0.70, 0.40, 0.12), t);
        }
        return shadeBall(pow(c, vec3(2.2)), rr, 0.0, vec3(0.0), cc, N, V);
    }
    if (uSkin == 7) {            // science: glowing core inside orbital rings
        float r1 = smoothstep(0.045, 0.0, abs(p.x)), r2 = smoothstep(0.045, 0.0, abs(p.y)), r3 = smoothstep(0.045, 0.0, abs(p.z));
        float rings = max(r1, max(r2, r3));
        float NoV = max(dot(N, V), 0.0);
        vec3 body = mix(vec3(0.02, 0.2, 0.28), vec3(0.1, 0.8, 0.9), pow(1.0 - NoV, 1.5));
        vec3 emit = vec3(0.3, 1.0, 0.95) * rings * 2.6 + body * (0.8 + 0.4 * sin(uTime * 2.0 + p.x * 6.0)) * 0.7;
        return shadeBall(vec3(0.05, 0.18, 0.22), 0.1, 0.0, emit, 1.0, N, V);
    }
    if (uSkin == 8) {            // snowball: packed frost with glints
        float t = fb3(p, 6.0);
        vec3 c = mix(vec3(0.82, 0.9, 0.98), vec3(1.0), t);
        float glint = step(0.985, hash21(floor(p.xy * 90.0) + floor(p.z * 90.0))) * (0.5 + 0.5 * sin(uTime * 4.0 + p.x * 40.0));
        return shadeBall(pow(c, vec3(2.2)), 0.7, 0.0, vec3(0.5, 0.7, 1.0) * glint * 1.5 + vec3(0.02, 0.05, 0.1), 0.2, N, V);
    }
    if (uSkin == 9) {            // golden scarab-ball: polished gold with lapis bands and carved glyph ticks
        float band = smoothstep(0.1, 0.07, abs(abs(lat) - 0.55)) + smoothstep(0.08, 0.05, abs(lat));
        float tick = step(0.55, fract(lon * 12.0 / 6.2831853)) * smoothstep(0.35, 0.3, abs(abs(lat) - 0.3));
        vec3 gold = vec3(1.0, 0.76, 0.28);
        vec3 c = mix(gold, vec3(0.05, 0.25, 0.7), band);
        c *= 1.0 - 0.45 * tick * (1.0 - band);
        float metal = 1.0 - band * 0.8;
        return shadeBall(pow(c, vec3(2.2)), 0.2, metal, vec3(0.0), band * 0.8, N, V);
    }
    if (uSkin == 10) {           // neon wireframe globe
        float la = abs(fract(acos(clamp(lat, -1.0, 1.0)) * 6.0 / PI) - 0.5);
        float lo = abs(fract(lon * 8.0 / 6.2831853) - 0.5);
        float line = max(smoothstep(0.06, 0.0, la), smoothstep(0.06, 0.0, lo) * smoothstep(0.98, 0.9, abs(lat)));
        vec3 neon = mix(vec3(1.0, 0.2, 0.8), vec3(0.2, 1.0, 0.9), 0.5 + 0.5 * sin(lat * 3.0 + uTime));
        return shadeBall(vec3(0.01, 0.005, 0.02), 0.15, 0.0, neon * line * 3.4 + neon * 0.05, 0.8, N, V);
    }
    if (uSkin == 11) {           // mossy stone
        float rock = fb3(p, 4.0), moss = smoothstep(0.45, 0.65, fb3(p + 5.0, 3.0));
        vec3 c = mix(vec3(0.34, 0.34, 0.31) * (0.6 + 0.8 * rock), vec3(0.18, 0.4, 0.1) * (0.6 + 0.7 * rock), moss);
        return shadeBall(pow(c, vec3(2.2)), 0.85, 0.0, vec3(0.0), 0.0, N, V);
    }
    if (uSkin == 12) {           // glow orb: dark spore-skin with living green mycelium and drifting spores
        vec3 q = p * 2.4 + vec3(uTime * 0.05, 0.0, -uTime * 0.07);
        float f = abs(fb3(q, 1.5) - 0.5);
        float vein = pow(smoothstep(0.06, 0.0, f), 1.4);
        vec2 g = vec2(lon * 9.0, lat * 11.0);
        vec2 ip = floor(g), fp = fract(g) - 0.5;
        float spore = step(0.8, hash21(ip)) * smoothstep(0.2, 0.0, length(fp - (vec2(hash21(ip + 3.0), hash21(ip + 9.0)) - 0.5) * 0.5)) * (0.5 + 0.5 * sin(uTime * 2.0 + hash21(ip) * 40.0));
        float breath = 0.75 + 0.25 * sin(uTime * 1.7);
        vec3 emit = vec3(0.55, 1.0, 0.36) * (vein * 2.6 + spore * 3.0) * breath + vec3(0.1, 0.55, 0.35) * 0.18;
        return shadeBall(vec3(0.02, 0.05, 0.04), 0.25, 0.0, emit, 0.8, N, V);
    }
    if (uSkin == 13) {           // autumn leaf: gold to rust patches with a dark vein network
        float t = fb3(p, 3.0);
        vec3 c = mix(vec3(0.95, 0.66, 0.12), vec3(0.78, 0.22, 0.06), smoothstep(0.35, 0.7, t));
        c = mix(c, vec3(0.45, 0.25, 0.08), smoothstep(0.6, 0.85, fb3(p + 4.0, 5.0)) * 0.6);
        float rib = smoothstep(0.045, 0.0, abs(sin(lon * 0.5)) - 0.0) * 0.0 + smoothstep(0.05, 0.0, abs(lon - 0.0) * 0.4);
        float net = smoothstep(0.07, 0.0, abs(fb3(p * 2.6, 2.0) - 0.5));
        c = mix(c, c * 0.42, max(net * 0.85, rib));
        return shadeBall(pow(c, vec3(2.2)), 0.5, 0.0, vec3(0.0), 0.5, N, V);
    }
    if (uSkin == 14) {           // toy ball: glossy rubber in primary panels with white stars
        float seg = floor((lon / 6.2831853 + 0.5) * 6.0);
        vec3 c = seg < 1.0 ? vec3(0.92, 0.12, 0.10) : seg < 2.0 ? vec3(0.98, 0.8, 0.08) : seg < 3.0 ? vec3(0.1, 0.4, 0.92) : seg < 4.0 ? vec3(0.92, 0.12, 0.10) : seg < 5.0 ? vec3(0.98, 0.8, 0.08) : vec3(0.1, 0.4, 0.92);
        vec2 g = vec2(lon * 3.0, lat * 4.0); vec2 fp = fract(g) - 0.5;
        float star = smoothstep(0.2, 0.12, length(fp) * (1.0 + 0.5 * abs(sin(atan(fp.y, fp.x) * 2.5)))) * step(0.5, hash21(floor(g)));
        c = mix(c, vec3(0.97), star);
        return shadeBall(pow(c, vec3(2.2)), 0.2, 0.0, vec3(0.0), 0.9, N, V);
    }
    if (uSkin == 15) {           // yarn ball: fuzzy wound strands
        float w = dot(p, normalize(vec3(1.0, 2.0, 3.0))) * 34.0 + sin(dot(p, vec3(5.0, 3.0, 7.0))) * 2.0;
        float w2 = dot(p, normalize(vec3(3.0, -1.0, 2.0))) * 30.0;
        float st = 0.5 + 0.5 * sin(w);
        vec3 c = mix(vec3(0.92, 0.28, 0.46), vec3(0.2, 0.7, 0.78), smoothstep(0.35, 0.65, 0.5 + 0.5 * sin(w2 * 0.5 + fb3(p, 2.0) * 3.0)));
        c *= 0.7 + 0.4 * st;
        return shadeBall(pow(c, vec3(2.2)), 0.97, 0.0, vec3(0.0), 0.0, N, V);
    }
    if (uSkin == 16) {           // amber: honey glass with bubbles and a little dark inclusion
        float t = fb3(p, 3.0);
        vec3 c = mix(vec3(1.0, 0.55, 0.06), vec3(0.75, 0.3, 0.02), t);
        float bub = step(0.93, hash21(floor(p.xy * 26.0) + floor(p.zx * 26.0))) * 0.5;
        c = mix(c, vec3(1.0, 0.9, 0.5), bub);
        float bug = smoothstep(0.12, 0.0, length((p - normalize(vec3(0.3, 0.2, 0.9))) * vec3(1.0, 2.2, 1.0)));
        c = mix(c, vec3(0.08, 0.05, 0.02), bug * 0.9);
        return shadeBall(pow(c, vec3(2.2)), 0.1, 0.0, vec3(0.55, 0.22, 0.02) * (0.25 + 0.3 * t), 1.0, N, V);
    }
    if (uSkin == 17) {           // ball bearing: polished chrome with a fine race line
        float line = smoothstep(0.012, 0.0, abs(lat) - 0.004);
        vec3 c = mix(vec3(0.82, 0.84, 0.88), vec3(0.3, 0.32, 0.36), line);
        return shadeBall(c, 0.1, 1.0, vec3(0.0), 0.0, N, V);
    }
    if (uSkin == 18) {           // molten core: dark crust over glowing fissures
        float f1 = abs(fb3(p * 2.2 + vec3(0.0, uTime * 0.03, 0.0), 1.6) - 0.5);
        float f2 = abs(fb3(p * 4.5 + 7.0, 1.4) - 0.5);
        float vein = max(smoothstep(0.07, 0.0, f1), smoothstep(0.045, 0.0, f2) * 0.7);
        vec3 emit = vec3(1.0, 0.36, 0.06) * vein * (1.8 + 0.8 * sin(uTime * 2.0 + f1 * 20.0)) + vec3(0.5, 0.1, 0.0) * 0.08;
        return shadeBall(vec3(0.02, 0.015, 0.015), 0.5, 0.0, emit, 0.5, N, V);
    }
    if (uSkin == 19) {           // gummy ball: translucent jelly with a bright core and a soft wobble
        float gw = fb3(p * 3.0 + vec3(0.0, uTime * 0.2, 0.0), 1.5);
        vec3 gc = mix(vec3(1.0, 0.15, 0.4), vec3(1.0, 0.55, 0.1), smoothstep(0.35, 0.65, gw));
        float core = pow(max(dot(N, V), 0.0), 1.6);
        vec3 emit = gc * (0.35 + 0.8 * core);
        return shadeBall(pow(gc, vec3(2.2)) * 0.6, 0.06, 0.0, emit, 1.0, N, V);
    }
    if (uSkin == 20) {           // knight's orb: hammered iron with a red-and-gold heraldic cross and rivets
        float hammer = fb3(p * 9.0, 1.2);
        vec3 c = vec3(0.42, 0.43, 0.46) * (0.8 + 0.35 * hammer);
        float cross = max(smoothstep(0.14, 0.11, abs(p.x)), smoothstep(0.14, 0.11, abs(p.y))) * step(0.0, p.z);
        c = mix(c, vec3(0.7, 0.08, 0.06), cross);
        float trim = smoothstep(0.03, 0.0, abs(abs(p.x) - 0.14)) + smoothstep(0.03, 0.0, abs(abs(p.y) - 0.14));
        c = mix(c, vec3(0.95, 0.75, 0.25), clamp(trim, 0.0, 1.0) * step(0.0, p.z) * 0.9);
        float riv = smoothstep(0.05, 0.03, length(fract(vec2(atan(p.y, p.x) * 2.5, p.z * 4.0)) - 0.5));
        c = mix(c, vec3(0.7, 0.7, 0.72), riv * 0.6);
        return shadeBall(pow(c, vec3(2.2)), 0.35, 0.85 * (1.0 - cross), vec3(0.0), 0.4, N, V);
    }
    // 21: beach ball: six panels, white pole caps
    float seg = floor((lon / 6.2831853 + 0.5) * 6.0);
    vec3 c = seg < 1.0 ? vec3(0.95, 0.15, 0.15) : seg < 2.0 ? vec3(1.0) : seg < 3.0 ? vec3(1.0, 0.85, 0.1) : seg < 4.0 ? vec3(1.0) : seg < 5.0 ? vec3(0.1, 0.45, 0.95) : vec3(1.0);
    float seamL = smoothstep(0.02, 0.0, abs(fract((lon / 6.2831853 + 0.5) * 6.0) - 0.5) - 0.48);
    c = mix(c, vec3(0.97), smoothstep(0.86, 0.9, abs(lat)));
    c = mix(c, c * 0.6, seamL);
    c = mix(c, vec3(0.9), smoothstep(0.985, 0.995, abs(lat)));
    return shadeBall(pow(c, vec3(2.2)), 0.35, 0.0, vec3(0.0), 0.9, N, V);
}

void main() {
    vec3 N = normalize(vNrmW);
    vec3 V = normalize(uCam - vWorld);
    float NoV = max(dot(N, V), 1e-3);
    if (uGhost > 0.5) {
        // see-through silhouette when the labyrinth hides the marble
        float rim = pow(1.0 - NoV, 1.5);
        outColor = vec4(mix(vec3(0.9), uAccent, 0.35) * (0.5 + 0.6 * rim), 0.06 + 0.22 * rim);
        return;
    }
    if (uSkin > 0) {
        vec3 sc = skinColor(normalize(uBody * vNrmL), N, V);
        sc = mix(sc, uDirtCol * (0.25 + 0.75 * max(dot(N, uLightDir), 0.0) * 0.8 + 0.15), uDirt * 0.4);
        outColor = vec4(finish(sc, gl_FragCoord.xy), 1.0);
        return;
    }
    // glass sphere: reflection + refraction of the maze behind it, with ribbons floating inside
    vec3 R = reflect(-V, N);
    vec3 T = refract(-V, N, 1.0 / 1.5);
    float F = 0.04 + 0.96 * pow(1.0 - NoV, 5.0);
    vec3 b = uBody * normalize(vNrmL + T * 0.7);
    float s = sin(b.x * 7.0 + sin(b.y * 5.0 + b.z * 3.0) * 1.6);
    float band = smoothstep(0.55, 0.8, s) * (1.0 - smoothstep(0.88, 0.97, s));
    vec3 acc = pow(uAccent, vec3(2.2));
    vec3 refr = surroundings(T, 0.12) * mix(vec3(0.78, 0.82, 0.9), acc, 0.08) * 0.85;
    refr *= mix(1.0, 0.3, pow(1.0 - NoV, 1.6));                 // thicker glass toward the rim
    refr = mix(refr, acc * 1.6 + refr * 0.25, band * 0.85);
    vec3 refl = surroundings(R, 0.03);
    vec3 col = refr * (1.0 - F) + refl * (0.05 + 0.95 * F);
    col = mix(col, uDirtCol * (0.25 + 0.75 * max(dot(N, uLightDir), 0.0) * 0.8 + 0.15), uDirt * 0.62);   // sand or mud caked on the marble
    vec3 H = normalize(uLightDir + V);
    col += pow(max(dot(N, H), 0.0), 500.0) * vec3(7.0);
    col += irradiance(N) * 0.03;
    outColor = vec4(finish(col, gl_FragCoord.xy), 1.0);
}
"""

    /** Water puddle: uv in aCol.xy, puddle id in aCol.z. Ripples (world-space centres with age) bend the surface normal. */
    const val PUDDLE_FS = """#version 300 es
precision highp float;
uniform vec3 uCam;
uniform float uTime;
uniform float uRain;
uniform float uLevel[24];
uniform vec4 uRip[32];      // xyz = world position, w = age in seconds (< 0 = unused)
uniform float uRipA[32];    // amplitude
in vec3 vWorld;
in vec3 vNrm;
in vec3 vCol;
out vec4 outColor;
""" + COMMON + """
// Drizzle: a lattice of tiny rings that keep appearing and fading across the surface (uv space).
vec2 drizzle(vec2 uv, float seed, float t) {
    vec2 g = uv * 5.0, ip = floor(g), fp = fract(g);
    vec2 tilt = vec2(0.0);
    for (int j = -1; j <= 1; j++) for (int i = -1; i <= 1; i++) {
        vec2 cell = ip + vec2(float(i), float(j));
        float r0 = hash21(cell + seed * 13.7);
        vec2 ctr = vec2(hash21(cell * 1.7 + 3.1 + seed), hash21(cell * 2.3 + 9.7 + seed));
        float ph = fract(t * 0.7 + r0);
        vec2 dv = fp - vec2(float(i), float(j)) - ctr;
        float d = length(dv);
        float x = (d - ph * 0.55) / 0.07;
        float w = exp(-x * x) * (1.0 - ph) * (1.0 - ph) * step(0.35, hash21(cell * 3.9 + seed * 5.0));
        tilt += dv / max(d, 1e-4) * w * cos(6.2831853 * x * 0.5);
    }
    return tilt;
}

void main() {
    int id = int(vCol.z + 0.5);
    float lv = uLevel[id];
    vec2 uv = vCol.xy;
    float r = length(uv);
    // irregular, organic outline: two octaves of noise bend the radius
    float ang = atan(uv.y, uv.x);
    float en = 0.6 * vnoise(vec2(ang * 1.7 + float(id) * 5.3, 1.3)) + 0.4 * vnoise(vec2(ang * 4.1, float(id) * 2.9));
    float R = sqrt(lv) * (0.9 + 0.26 * en);
    float fw = fwidth(r) * 1.5;
    float inside = 1.0 - smoothstep(R - 0.05 - fw, R + fw, r);
    // damp halo around the puddle where the floor is wet but not flooded
    float halo = (1.0 - smoothstep(R, R + 0.45, r)) * (1.0 - inside) * 0.38 * smoothstep(0.02, 0.25, lv);
    if (lv < 0.01 || (inside < 0.01 && halo < 0.01)) discard;

    vec3 N = normalize(vNrm);
    vec3 V = normalize(uCam - vWorld);
    if (inside < 0.01) { outColor = vec4(0.0, 0.0, 0.0, halo); return; }

    // water lies flat: blend the surface normal toward world-up so slopes don't tilt the reflection too much
    N = normalize(mix(N, vec3(0.0, 1.0, 0.0), 0.35));

    // expanding ring ripples from drops and the marble
    vec3 tilt = vec3(0.0);
    float hsum = 0.0;
    const float WL = 0.022;        // wavelength in world units
    const float SPD = 0.15;        // ring speed
    for (int i = 0; i < 32; i++) {
        float age = uRip[i].w;
        if (age < 0.0) continue;
        vec3 dv = vWorld - uRip[i].xyz;
        dv -= N * dot(dv, N);
        float d = length(dv);
        float x = (d - SPD * age) / WL;
        float win = exp(-x * x * 0.14) * exp(-age * 1.15) * uRipA[i];
        float phs = 6.2831853 * x;
        hsum += win * sin(phs);
        tilt += (dv / max(d, 1e-5)) * (win * cos(phs));
    }
    // drizzle micro-ripples, mapped from uv space to the world through screen derivatives
    if (uRain > 0.05) {
        vec3 dpx = dFdx(vWorld), dpy = dFdy(vWorld);
        vec2 ux = dFdx(uv), uy = dFdy(uv);
        float det = ux.x * uy.y - ux.y * uy.x;
        if (abs(det) > 1e-9) {
            vec3 Tu = normalize((dpx * uy.y - dpy * ux.y) / det);
            vec3 Tv = normalize((dpy * ux.x - dpx * uy.x) / det);
            vec2 g = drizzle(uv, float(id), uTime) * uRain;
            tilt += (Tu * g.x + Tv * g.y) * 0.05;
        }
    }
    N = normalize(N - tilt * 0.7);

    float NoV = max(dot(N, V), 1e-3);
    vec3 Rf = reflect(-V, N);
    float F = 0.02 + 0.98 * pow(1.0 - NoV, 5.0);
    float Fv = 0.10 + 0.90 * F;
    // water body is darker toward the middle (depth) and lighter at the shallow rim
    float rim = smoothstep(R - 0.30, R, r);
    vec3 deep = mix(vec3(0.020, 0.034, 0.048), vec3(0.07, 0.10, 0.125), rim);
    vec3 col = deep * (1.0 - Fv) + envMap(Rf, 0.012) * vec3(0.50, 0.56, 0.66) * Fv;
    vec3 H = normalize(uLightDir + V);
    col += pow(max(dot(N, H), 0.0), 1200.0) * vec3(11.0, 10.4, 9.6);
    col += max(hsum, 0.0) * 0.10;                              // ring crests catch light
    col += vec3(0.30, 0.40, 0.50) * (1.0 - smoothstep(0.0, 0.06, abs(r - R + 0.03))) * 0.55;   // meniscus glint
    float alpha = inside * mix(0.94, 0.78, rim);
    outColor = vec4(finish(col, gl_FragCoord.xy), max(alpha, halo));
}
"""

    /** Soft light pools on the floor under glowing mushrooms: additive decals whose colour slot rides in the time channel. */
    const val HALO_VS = """#version 300 es
uniform mat4 uVP;
uniform mat4 uModel;
layout(location=0) in vec4 aPA;     // xyz = floor position, w = colour slot
layout(location=1) in vec4 aNS;     // xyz = normal, w = half size
out vec2 vUv;
out float vSeed;
void main() {
    const vec2 C[6] = vec2[6](vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, 1.0));
    vec2 c = C[gl_VertexID];
    vec3 n = normalize(aNS.xyz);
    vec3 a = abs(n.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 t1 = normalize(cross(a, n));
    vec3 t2 = cross(n, t1);
    vec3 p = aPA.xyz + (t1 * c.x + t2 * c.y) * aNS.w + n * 0.003;
    vUv = c; vSeed = aPA.w;
    gl_Position = uVP * uModel * vec4(p, 1.0);
}
"""

    const val HALO_FS = """#version 300 es
precision highp float;
uniform float uTime;
uniform float uPass;
in vec2 vUv;
in float vSeed;
out vec4 outColor;
void main() {
    if (uPass > 0.5) discard;
    float r = length(vUv);
    if (r > 1.0) discard;
    int k = int(vSeed + 0.5);
    vec3 col = k == 0 ? vec3(0.55, 1.0, 0.36) : k == 1 ? vec3(0.30, 1.0, 0.75) : k == 2 ? vec3(0.85, 1.0, 0.35) : vec3(0.25, 0.88, 1.0);
    float g = pow(1.0 - r, 2.2);
    float breath = 0.82 + 0.18 * sin(uTime * 1.7 + vSeed * 2.3 + vUv.x * 0.0);
    outColor = vec4(col * g * 0.55 * breath, 1.0);
}
"""

    /** Neon trail left by the ball on the Glow level: additive soft discs that fade over a few seconds. */
    const val GLOW_FS = """#version 300 es
precision highp float;
uniform float uPass;
in vec2 vUv;
in float vAge;
in vec3 vNrm;
in vec3 vT1;
in vec3 vT2;
in vec3 vWorld;
out vec4 outColor;
void main() {
    if (uPass > 0.5) discard;
    float r = length(vUv);
    if (r > 1.0) discard;
    float f = 1.0 - smoothstep(0.0, 3.6, vAge);
    f *= f;
    if (f < 0.004) discard;
    float glow = exp(-r * r * 3.4);
    float core = exp(-r * r * 15.0);
    vec3 young = vec3(0.35, 1.0, 0.55), mid = vec3(0.2, 0.85, 1.0), old = vec3(0.9, 0.3, 1.0);
    vec3 col = mix(mix(young, mid, smoothstep(0.0, 1.4, vAge)), old, smoothstep(1.2, 3.2, vAge));
    outColor = vec4(col * (glow * 0.9 + core * 2.2) * f, 1.0);
}
"""

    /** Leaves that drift down and settle: the quad is placed at its landing spot and lifted by its remaining fall. */
    const val LEAF_VS = """#version 300 es
uniform mat4 uVP;
uniform mat4 uModel;
uniform float uTime;
layout(location=0) in vec4 aPA;     // xyz = landing position (local), w = time the leaf left the branch
layout(location=1) in vec4 aNS;     // xyz = local surface normal, w = half size
out vec2 vUv;
out float vAge;
out vec3 vNrm;
out vec3 vWorld;
out float vSeed;
void main() {
    const vec2 C[6] = vec2[6](vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, 1.0));
    vec2 c = C[gl_VertexID];
    vec3 n = normalize(aNS.xyz);
    vec3 a = abs(n.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 t1 = normalize(cross(a, n));
    vec3 t2 = cross(n, t1);
    float seed = fract(sin(aPA.w * 127.1 + aNS.w * 4311.7) * 43758.5453);   // per leaf, so it stays the same leaf while it is pushed around
    float age = uTime - aPA.w;
    float f = clamp(age / 3.4, 0.0, 1.0);                  // 0 = on the branch, 1 = landed
    float air = 1.0 - f;
    float s = aNS.w;
    float ang = seed * 6.2831 + air * sin(age * 2.6 + seed * 5.0) * 1.6;
    vec2 r = vec2(c.x * cos(ang) - c.y * sin(ang), c.x * sin(ang) + c.y * cos(ang));
    vec3 off = (t1 * r.x * 1.25 + t2 * r.y * 0.85) * s;
    off += n * (r.x * sin(age * 3.1 + seed * 7.0) * air * s * 0.9);              // tumbling while airborne
    float h = air * s * 15.0;
    vec3 sway = t1 * sin(age * 1.9 + seed * 6.0) * s * 2.6 * air + t2 * cos(age * 1.5 + seed * 9.0) * s * 2.6 * air;
    vec3 p = aPA.xyz + off + n * (0.0035 + h + 0.0006 * seed) + sway;
    vec4 w = uModel * vec4(p, 1.0);
    vWorld = w.xyz;
    vNrm = mat3(uModel) * n;
    vUv = c;
    vAge = age;
    vSeed = seed;
    gl_Position = uVP * w;
}
"""

    const val LEAF_FS = """#version 300 es
precision highp float;
uniform vec3 uLightDir;
uniform float uPass;
uniform float uLeafKind;     // 0 autumn leaves, 1 jungle leaves and twigs
in vec2 vUv;
in float vAge;
in vec3 vNrm;
in vec3 vWorld;
in float vSeed;
out vec4 outColor;
void main() {
    if (uPass > 0.5) discard;
    float x = vUv.x, y = vUv.y;
    if (uLeafKind > 0.5 && fract(vSeed * 3.17) > 0.72) {          // a fallen twig: tapering crooked stick, a forked side shoot with two leaves
        float sd = fract(vSeed * 13.7);
        float bend = 0.07 * sin(x * 2.4 + sd * 6.0) + 0.03 * sin(x * 7.0 + sd * 3.0);
        float r0 = mix(0.075, 0.03, (x + 1.0) * 0.5);                 // thicker at the snapped end
        float stem = r0 - abs(y - bend);
        vec2 fs = vec2(-0.15 + sd * 0.2, bend);                        // the fork
        vec2 fe = fs + vec2(0.55, 0.42 * (sd > 0.5 ? 1.0 : -1.0));
        vec2 pa = vec2(x, y) - fs, ba = fe - fs;
        float hh = clamp(dot(pa, ba) / dot(ba, ba), 0.0, 1.0);
        float shoot = mix(0.035, 0.014, hh) - length(pa - ba * hh);
        vec2 lq = vec2(x, y) - fe;                                     // two small leaves at the tip of the shoot
        float lf1 = 0.07 - length(vec2(dot(lq, normalize(ba)) - 0.07, dot(lq, vec2(-ba.y, ba.x) / length(ba)) * 2.2));
        float lf2 = 0.06 - length(vec2(dot(lq + ba * 0.25, normalize(ba + vec2(0.0, 0.3))) * 1.0 - 0.0, (dot(lq + ba * 0.25, vec2(-ba.y, ba.x) / length(ba)) + 0.06) * 2.4));
        if (abs(x) > 0.97) stem = -1.0;
        float wood = max(stem, shoot);
        float leaf = max(lf1, lf2);
        if (wood < 0.0 && leaf < 0.0) discard;
        float lit = 0.45 + 0.75 * abs(dot(normalize(vNrm), uLightDir));
        vec3 col;
        if (wood >= 0.0) {
            vec3 bark = mix(vec3(0.30, 0.20, 0.12), vec3(0.46, 0.34, 0.2), sd);
            float grain = 0.82 + 0.18 * sin((y - bend) * 120.0 + x * 6.0);
            float knot = smoothstep(0.03, 0.0, length(vec2(x - (sd - 0.5), y - bend)) - 0.02);
            float edge = smoothstep(0.0, 0.025, wood);
            col = bark * grain * (1.0 - 0.45 * knot) * (0.6 + 0.4 * edge);
            col = mix(col, vec3(0.62, 0.52, 0.36), step(0.94, abs(x)) * step(0.0, stem));    // pale snapped end
        } else col = mix(vec3(0.18, 0.44, 0.12), vec3(0.4, 0.6, 0.18), sd);
        outColor = vec4(col * lit, smoothstep(0.0, 0.35, vAge));
        return;
    }
    // pointed leaf outline with a stalk, midrib and side veins
    float w = 0.64 * pow(max(1.0 - x * x, 0.0), 0.65) * (0.75 + 0.25 * x);
    float body = w - abs(y);
    float stalk = step(0.82, -x) * step(abs(y), 0.05) * step(-1.0, x);
    if (body < 0.0 && stalk < 0.5) discard;
    vec3 gold = vec3(0.96, 0.72, 0.16), orange = vec3(0.92, 0.42, 0.08), red = vec3(0.68, 0.14, 0.06), brown = vec3(0.50, 0.30, 0.10);
    float k = fract(vSeed * 7.31);
    vec3 col = k < 0.35 ? gold : k < 0.65 ? orange : k < 0.85 ? red : brown;
    if (uLeafKind > 0.5) col = k < 0.4 ? vec3(0.16, 0.42, 0.12) : k < 0.7 ? vec3(0.32, 0.58, 0.16) : k < 0.88 ? vec3(0.55, 0.62, 0.2) : vec3(0.42, 0.3, 0.12);
    col = mix(col, col * vec3(1.1, 0.9, 0.7), smoothstep(-0.9, 0.9, x) * 0.5);
    float rib = smoothstep(0.045, 0.0, abs(y));
    float veins = smoothstep(0.05, 0.0, abs(fract((abs(y) * 5.0 - x * 2.6) + 0.5) - 0.5) * 0.4) * step(abs(y), w);
    col = mix(col, col * 0.55, max(rib, veins * 0.6));
    vec3 N = normalize(vNrm);
    float lit = 0.45 + 0.75 * abs(dot(N, uLightDir));
    float edge = smoothstep(0.0, 0.06, body);
    float a = smoothstep(0.0, 0.35, vAge) * (stalk > 0.5 ? 1.0 : edge);
    if (a < 0.02) discard;
    outColor = vec4(pow(col * lit, vec3(1.0)), a);
}
"""

    /** Flood water, poison gas and drifting fog sheets (see MediumMesh). vCol.x = concentration, vCol.y = layer. */
    const val MEDIUM_FS = """#version 300 es
precision highp float;
uniform vec3 uCam;
uniform float uTime;
uniform float uKind;        // 1 water, 2 gas, 3 fog
uniform float uLayers;      // number of fog / gas slices
uniform float uFogT;        // fog clock: only advances while the ball moves, so still air stays still
uniform float uScale;       // 1 / cell size
uniform float uNear;        // sheets fade out close to the camera so the ball stays visible
uniform vec3 uMarbleP;
uniform float uMarbleSp;
uniform float uWaveOn;
uniform sampler2D uWaveTex;
uniform sampler2D uFoamTex;
uniform vec4 uWaveP;        // x = texels per cell, y = tile size with gutter, z = tiles per row
uniform vec2 uWaveAtlas;
in vec3 vWorld;
in vec3 vNrm;
in vec3 vCol;
in vec3 vLocal;
in vec3 vTile;
out vec4 outColor;
""" + COMMON + """
float waveH(vec2 uv, float cell) {
    vec2 org = vec2(mod(cell, uWaveP.z), floor(cell / uWaveP.z)) * uWaveP.y;
    float hl = 0.5 / uWaveP.x;
    return texture(uWaveTex, (org + 1.0 + clamp(uv, -hl, 1.0 + hl) * uWaveP.x) / uWaveAtlas).r;
}
float foamH(vec2 uv, float cell) {
    vec2 org = vec2(mod(cell, uWaveP.z), floor(cell / uWaveP.z)) * uWaveP.y;
    float hl = 0.5 / uWaveP.x;
    return texture(uFoamTex, (org + 1.0 + clamp(uv, -hl, 1.0 + hl) * uWaveP.x) / uWaveAtlas).r;
}
float fbm(vec2 p) { return 0.55 * vnoise(p) + 0.3 * vnoise(p * 2.1 + 7.3) + 0.15 * vnoise(p * 4.3 + 1.9); }
void main() {
    float lv = vCol.x;
    float layer = vCol.y * 3.0;
    if (lv < 0.01 && uKind < 2.5) discard;                     // fog covers the whole level
    if (uKind > 2.5) lv = 1.0;
    vec3 N = normalize(vNrm);
    vec3 V = normalize(uCam - vWorld);
    vec3 t1 = normalize(cross(N, vec3(0.31, 0.93, 0.19)));
    vec3 t2 = cross(N, t1);
    vec2 p = vec2(dot(vLocal, t1), dot(vLocal, t2)) * uScale;
    float near = smoothstep(uNear * 0.4, uNear * 2.4, distance(uCam, vWorld));
    if (uKind < 1.5) {                                          // ---- water
        float t = uTime;
        vec2 a = p * 1.2 + vec2(t * 0.30, t * 0.18);
        vec2 b = p * 2.5 - vec2(t * 0.22, -t * 0.32);
        float e = 0.06;
        float h0 = vnoise(a) + 0.5 * vnoise(b);
        vec2 grad = vec2(vnoise(a + vec2(e, 0.0)) + 0.5 * vnoise(b + vec2(e, 0.0)) - h0,
                         vnoise(a + vec2(0.0, e)) + 0.5 * vnoise(b + vec2(0.0, e)) - h0) / e;
        float d = length(vLocal - uMarbleP) * uScale;
        float ring = 0.0;
        float crest = 0.0;
        N = normalize(N - (t1 * grad.x + t2 * grad.y) * 0.09 * (0.5 + lv));
        if (uWaveOn > 0.5) {
            // simulated surface: gradient of the wave height field, in cell units, mapped to the world
            float cellId = vTile.z; vec2 uv = vTile.xy;
            float e = 1.0 / uWaveP.x;
            float hu = (waveH(uv + vec2(e, 0.0), cellId) - waveH(uv - vec2(e, 0.0), cellId)) / (2.0 * e);
            float hv = (waveH(uv + vec2(0.0, e), cellId) - waveH(uv - vec2(0.0, e), cellId)) / (2.0 * e);
            crest = waveH(uv, cellId);
            vec3 dpx = dFdx(vWorld), dpy = dFdy(vWorld);
            vec2 ux = dFdx(uv), uy = dFdy(uv);
            float det = ux.x * uy.y - ux.y * uy.x;
            if (abs(det) > 1e-12) {
                vec3 Tu = normalize((dpx * uy.y - dpy * ux.y) / det);
                vec3 Tv = normalize((dpy * ux.x - dpx * uy.x) / det);
                vec3 wg = (Tu * hu + Tv * hv) * 2.0;
                float wl = length(wg);
                if (wl > 0.24) wg *= 0.24 / wl;
                N = normalize(N - wg);
            }
        }
        float NoV = max(dot(N, V), 1e-3);
        float F = 0.02 + 0.98 * pow(1.0 - NoV, 5.0);
        // clear pool water: almost colourless where shallow, deepening to teal-blue; you see the floor through it
        float depth = smoothstep(0.0, 0.9, lv);
        vec3 tint = mix(vec3(0.10, 0.75, 0.80), vec3(0.01, 0.28, 0.55), depth);
        // caustics: the bright rippling web of light focused onto the floor below
        vec2 cp = p * 3.2;
        float c1 = vnoise(cp + vec2(uTime * 0.45, uTime * 0.3));
        float c2 = vnoise(cp * 1.27 - vec2(uTime * 0.38, -uTime * 0.27) + 4.1);
        float c3 = vnoise(cp * 0.7 + vec2(-uTime * 0.2, uTime * 0.25) + 9.3);
        float caus = pow(1.0 - abs(c1 - c2), 22.0) * 1.6 + pow(1.0 - abs(c2 - c3), 30.0) * 0.9;
        caus *= (1.0 - 0.6 * depth) * (0.6 + 0.4 * max(dot(vec3(0.0, 1.0, 0.0), uLightDir), 0.0));
        vec3 body = tint * (0.75 + 0.35 * (1.0 - depth)) + vec3(0.8, 1.0, 1.0) * caus * 0.9;
        vec3 refl = envMap(reflect(-V, N), 0.02) * vec3(0.6, 0.8, 0.95) + vec3(0.05, 0.12, 0.18);
        vec3 col = mix(body, refl, F * 0.7);
        col += pow(max(dot(N, normalize(uLightDir + V)), 0.0), 900.0) * vec3(5.0, 4.8, 4.4);       // sun glints
        col += pow(max(dot(N, normalize(uLightDir + V)), 0.0), 60.0) * vec3(0.25, 0.28, 0.3);
        float foam = smoothstep(0.2, 0.03, lv) * 0.35 * smoothstep(0.45, 0.8, vnoise(p * 7.0 + uTime * 0.25));
        float wake = 0.0;
        if (uWaveOn > 0.5) {
            float fv = foamH(vTile.xy, vTile.z);
            float lace = 0.55 + 0.45 * vnoise(p * 9.0 + vec2(uTime * 0.3, -uTime * 0.2));
            wake = smoothstep(0.08, 0.6, fv * lace) * (0.35 + 0.65 * smoothstep(0.1, 0.4, lv));
        }
        col = mix(col, vec3(0.88, 0.94, 0.98), clamp(foam, 0.0, 0.5));
        col = mix(col, vec3(0.97, 0.99, 1.0), wake * 0.9);
        // see-through: low alpha in the shallows, more opaque when deep or seen at a grazing angle (Fresnel)
        float alpha = smoothstep(0.0, 0.08, lv) * mix(0.5, 0.86, depth);
        alpha = max(alpha, F * 0.85 * smoothstep(0.0, 0.08, lv));
        alpha = max(alpha, max(wake * 0.95, foam));
        alpha = clamp(alpha + caus * 0.12, 0.0, 0.92) * (0.7 + 0.3 * near);
        outColor = vec4(finish(col, gl_FragCoord.xy), alpha);
    } else {                                                    // ---- gas and fog: slices through a drifting, billowing 3D density field
        bool gas = uKind < 2.5;
        float h = vCol.y;                                       // 0 at the floor .. 1 at the top of the volume
        float sc = gas ? 1.6 : 0.9;
        float ft = gas ? uTime : uFogT;                          // fog only stirs while the ball moves
        float drift = ft * (gas ? 0.08 : 0.06);
        vec2 q = p * sc + vec2(drift, -drift * 0.7);
        if (!gas) {                                             // the moving ball stirs the fog: a swirl that is strongest beside it
            vec3 dl = vLocal - uMarbleP;
            vec2 rel = vec2(dot(dl, t1), dot(dl, t2)) * uScale;
            float rd = length(rel);
            float sw = uMarbleSp * 2.2 * exp(-rd * 0.9);
            float ca = cos(sw), sa = sin(sw);
            q += (vec2(ca * rel.x - sa * rel.y, sa * rel.x + ca * rel.y) - rel) * sc;
        }
        vec2 warp = 0.9 * vec2(fbm(q * 0.7 + vec2(ft * 0.05, 1.7)), fbm(q * 0.7 - vec2(3.1, ft * 0.04)));
        float hz = h * (gas ? 2.6 : 3.2) - ft * (gas ? 0.06 : 0.03);
        float zf = floor(hz);
        float n0 = fbm(q + warp + vec2(zf * 5.37, zf * 2.11));
        float n1 = fbm(q + warp + vec2((zf + 1.0) * 5.37, (zf + 1.0) * 2.11));
        float n = mix(n0, n1, smoothstep(0.0, 1.0, hz - zf));
        // heavy gas pools low; fog sits in a thick bank that thins out above the walls
        float prof = gas ? (1.0 - smoothstep(0.25, 1.0, h)) * (0.75 + 0.25 * smoothstep(0.0, 0.1, h))
                         : smoothstep(0.0, 0.1, h) * (1.0 - smoothstep(0.45, 1.0, h));
        float dens = gas ? clamp((n - 0.16) * 2.8, 0.0, 1.0) * prof * smoothstep(0.0, 0.22, lv) * (0.8 + 0.2 * lv)
                         : clamp(0.55 + (n - 0.45) * 1.6, 0.25, 1.0) * prof;      // fog is a thick blanket everywhere, lumpy rather than patchy
        // light: lower slices sit in the shade of the ones above; looking toward the sun the edges glow (forward scattering)
        float shade = mix(0.5, 1.08, h) * (0.8 + 0.2 * n);
        float fwd = pow(max(dot(-V, uLightDir), 0.0), 5.0) * 0.55 * (1.0 - dens * 0.5);
        vec3 col;
        if (gas) col = mix(vec3(0.20, 0.46, 0.05), vec3(0.72, 0.95, 0.24), n) * (shade + fwd) + vec3(0.08, 0.26, 0.02) * dens;
        else col = mix(vec3(0.60, 0.66, 0.75), vec3(0.97, 0.98, 1.0), n * 0.6 + h * 0.4) * (shade + fwd);
        float total = clamp(dens * (gas ? 0.88 : 0.97), 0.0, 0.985);
        float alpha = 1.0 - pow(1.0 - total, 1.0 / max(uLayers * 0.55, 1.0));  // the stack of slices adds up to the target density
        alpha *= mix(0.35, 1.0, smoothstep(0.02, 0.3, abs(dot(N, V))));       // slices seen edge-on would show as bands: thin them out
        float nearV = gas ? smoothstep(uNear * 0.12, uNear * 0.6, distance(uCam, vWorld)) : smoothstep(uNear * 0.04, uNear * 0.25, distance(uCam, vWorld));   // only the slices right at the lens fade
        float bubble = gas ? mix(0.3, 1.0, smoothstep(0.5, 1.8, length(vLocal - uMarbleP) * uScale))
                           : mix(0.1, 1.0, smoothstep(0.3 + 0.3 * uMarbleSp, 1.25 + 0.5 * uMarbleSp, length(vLocal - uMarbleP) * uScale));   // a small clear pocket around the ball
        outColor = vec4(finish(col, gl_FragCoord.xy), clamp(alpha, 0.0, 0.95) * nearV * bubble);
    }
}
"""

    /**
     * Falling rain as instanced screen-aligned streaks. Drops are spread through the camera's view frustum and fall
     * away from the camera, down and toward the maze, so they converge in perspective instead of dropping straight down.
     */
    const val RAIN_VS = """#version 300 es
uniform mat4 uVP;
uniform float uTime;
uniform float uIntensity;
uniform vec2 uViewport;     // render target size in pixels
uniform float uWidthPx;
uniform vec3 uCamPos;       // camera position and basis (world space)
uniform vec3 uRight;
uniform vec3 uUp;
uniform vec3 uFwd;
uniform float uDepth0;      // nearest / widest distance drops are spread over
uniform float uSpan;
uniform float uTravel;      // how far a drop falls before it respawns
uniform float uLenScale;    // streak length scale
uniform float uScaleW;      // overall world scale (1 in orbit view, small in chase view)
uniform vec2 uTan;          // tan(half fov) in x and y
layout(location=0) in vec4 aSeed;
out float vA;
out vec2 vC;
void main() {
    const vec2 CORNERS[6] = vec2[6](vec2(0.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0), vec2(0.0, -1.0), vec2(1.0, 1.0), vec2(0.0, 1.0));
    vec2 c = CORNERS[gl_VertexID];
    // fall direction: down the screen, leaning slightly away from the camera, relative to the camera's own up
    vec3 D = normalize(-uUp + 0.3 * uFwd);
    float depth = uDepth0 + aSeed.w * uSpan;
    vec3 base = uCamPos + uFwd * depth
              + uRight * ((aSeed.x * 2.0 - 1.0) * depth * uTan.x * 1.15)
              + uUp * ((aSeed.y * 2.0 - 1.0) * depth * uTan.y * 1.15 + uTravel * 0.47);
    float ph = fract(aSeed.z + uTime * (0.55 + 0.25 * aSeed.w));   // phase increases with time: drops travel along D (down)
    vec3 head = base + D * (ph * uTravel);
    vec3 tail = head - D * ((0.07 + 0.09 * (1.0 - aSeed.w)) * uLenScale);
    vec4 ch = uVP * vec4(head, 1.0);
    vec4 ct = uVP * vec4(tail, 1.0);
    vec2 sh = (ch.xy / ch.w) * uViewport * 0.5;
    vec2 st = (ct.xy / ct.w) * uViewport * 0.5;
    vec2 dir = sh - st;
    float len = length(dir);
    dir = len > 1e-4 ? dir / len : vec2(0.0, -1.0);
    vec2 perp = vec2(-dir.y, dir.x);
    float wpx = uWidthPx * clamp(3.0 / (depth / uScaleW), 0.45, 1.8);
    vec4 pos = mix(ct, ch, c.x);
    pos.xy += perp * c.y * wpx / uViewport * 2.0 * pos.w * 0.5;
    gl_Position = pos;
    float fadeIn = smoothstep(0.0, 0.12, ph) * smoothstep(1.0, 0.8, ph);
    float vis = step(aSeed.w * 0.9, uIntensity);
    vA = (0.18 + 0.42 * (1.0 - aSeed.w)) * uIntensity * vis * fadeIn;
    vC = c;
}
"""

    const val RAIN_FS = """#version 300 es
precision mediump float;
in float vA;
in vec2 vC;
out vec4 outColor;
void main() {
    float across = 1.0 - vC.y * vC.y;
    float along = smoothstep(0.0, 0.35, vC.x) * (0.5 + 0.5 * vC.x);
    outColor = vec4(vec3(0.74, 0.84, 1.0), across * across * along * vA * 1.0);
}
"""

    /**
     * Hole pit (walls, glowing floor, hazard rim). aCol.x = depth 0..1 (wall) / radius 0..1 (bottom) / angle 0..1 (rim),
     * aCol.y = surface type (0 wall, 1 bottom, 2.x rim with radial fraction x), aCol.z = animation phase.
     */
    const val PIT_FS = """#version 300 es
precision highp float;
uniform float uTime;
uniform vec3 uAccent;
in vec3 vWorld;
in vec3 vNrm;
in vec3 vCol;
out vec4 outColor;
""" + COMMON + """
void main() {
    float type = vCol.y;
    float ph = vCol.z;
    float s = sin(0.9 * uTime + ph * 6.2831853);
    float open = smoothstep(0.0, 0.6, s);
    vec3 acc = pow(uAccent, vec3(2.2));
    vec3 N = normalize(vNrm);
    float lit = 0.20 + 0.55 * max(dot(N, uLightDir), 0.0);
    vec3 col;
    if (type < 0.5) {                                       // shaft wall: dark steel, ring seams, light from the bottom
        float d = vCol.x;
        float seam = 1.0 - smoothstep(0.0, 0.07, abs(fract(d * 5.0) - 0.5) - 0.40);
        col = vec3(0.10, 0.11, 0.14) * lit * pow(1.0 - d, 1.6);
        col += acc * (0.02 + 2.2 * pow(d, 4.0)) * (0.75 + 0.25 * sin(uTime * 2.0 + ph * 6.0));
        col += acc * seam * 0.22 * (0.3 + d);
    } else if (type < 1.5) {                                // bottom: a glowing well
        float r = vCol.x;
        col = acc * (1.9 - 1.4 * r) * (0.8 + 0.2 * sin(uTime * 3.0 - r * 9.0));
    } else {                                                // hazard rim: amber/black chevrons
        float ang = vCol.x, rad = fract(type);
        float stripes = step(0.5, fract(ang * 28.0 + rad * 2.2));
        col = mix(vec3(0.015), vec3(0.95, 0.66, 0.05), stripes) * (0.35 + 0.9 * max(dot(N, uLightDir), 0.0));
        // warning light blinks while the lid is moving or about to open
        float moving = step(0.01, open) * (1.0 - step(0.999, open));
        float soon = step(0.0, cos(0.9 * uTime + ph * 6.2831853)) * step(s, 0.0) * step(-0.5, s);
        float blink = max(moving, soon) * (0.5 + 0.5 * sin(uTime * 14.0));
        col += vec3(1.0, 0.45, 0.05) * blink * 2.2 * stripes;
    }
    outColor = vec4(finish(col, gl_FragCoord.xy), 1.0);
}
"""

    /** Sliding lid plates: aLift is the slide vector, aDelay the animation phase. Same open/close curve as the hole logic. */
    const val LID_VS = """#version 300 es
uniform mat4 uVP;
uniform mat4 uModel;
uniform float uTime;
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNrm;
layout(location=2) in vec3 aCol;
layout(location=3) in vec3 aLift;
layout(location=4) in float aDelay;
out vec3 vWorld;
out vec3 vNrm;
out vec3 vCol;
void main() {
    float open = smoothstep(0.0, 0.6, sin(0.9 * uTime + aDelay * 6.2831853));
    vec4 w = uModel * vec4(aPos + aLift * open, 1.0);
    vWorld = w.xyz;
    vNrm = mat3(uModel) * aNrm;
    vCol = aCol;
    gl_Position = uVP * w;
}
"""

    const val LID_FS = """#version 300 es
precision highp float;
uniform vec3 uCam;
uniform vec3 uAccent;
in vec3 vWorld;
in vec3 vNrm;
in vec3 vCol;
out vec4 outColor;
""" + COMMON + """
void main() {
    vec3 N = normalize(vNrm);
    vec3 V = normalize(uCam - vWorld);
    vec2 uv = vCol.xy;                         // -1..1 across the whole (closed) disc
    float r = length(uv);
    vec3 steel = vec3(0.17, 0.19, 0.23);
    float tread = 0.5 + 0.5 * sin(uv.x * 38.0) * sin(uv.y * 38.0);
    steel *= 0.82 + 0.28 * tread;
    float border = smoothstep(0.70, 0.74, r);
    float stripes = step(0.5, fract((atan(uv.y, uv.x) / 6.2831853) * 26.0 + r * 2.0));
    vec3 haz = mix(vec3(0.03), vec3(0.95, 0.66, 0.05), stripes);
    vec3 albedo = mix(steel, haz, border);
    float seam = 1.0 - smoothstep(0.0, 0.035, abs(uv.x));
    albedo *= 1.0 - 0.8 * seam;
    float NoV = max(dot(N, V), 1e-3);
    float NoL = max(dot(N, uLightDir), 0.0);
    vec3 R = reflect(-V, N);
    float F = 0.04 + 0.96 * pow(1.0 - NoV, 5.0);
    vec3 col = albedo * (irradiance(N) * 0.9 + vec3(1.0, 0.94, 0.86) * 1.2 * NoL)
             + envMap(R, 0.30) * (0.10 + 0.45 * F) * (1.0 - border);
    outColor = vec4(finish(col, gl_FragCoord.xy), 1.0);
}
"""

    /** Footprint decals: one instanced quad per recorded marble position, lying on the surface. */
    const val TRAIL_VS = """#version 300 es
uniform mat4 uVP;
uniform mat4 uModel;
uniform float uTime;
layout(location=0) in vec4 aPA;     // xyz = local position, w = time it was laid down
layout(location=1) in vec4 aNS;     // xyz = local surface normal, w = half size
out vec2 vUv;
out float vAge;
out vec3 vNrm;
out vec3 vT1;
out vec3 vT2;
out vec3 vWorld;
void main() {
    const vec2 C[6] = vec2[6](vec2(-1.0, -1.0), vec2(1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, -1.0), vec2(1.0, 1.0), vec2(-1.0, 1.0));
    vec2 c = C[gl_VertexID];
    vec3 n = normalize(aNS.xyz);
    vec3 a = abs(n.y) < 0.9 ? vec3(0.0, 1.0, 0.0) : vec3(1.0, 0.0, 0.0);
    vec3 t1 = normalize(cross(a, n));
    vec3 t2 = cross(n, t1);
    vec3 p = aPA.xyz + (t1 * c.x + t2 * c.y) * aNS.w + n * 0.004;
    vec4 w = uModel * vec4(p, 1.0);
    vWorld = w.xyz;
    mat3 M = mat3(uModel);
    vNrm = M * n; vT1 = M * t1; vT2 = M * t2;
    vUv = c;
    vAge = uTime - aPA.w;
    gl_Position = uVP * w;
}
"""

    /**
     * Lit footprints: the groove/berm height profile is turned into a surface normal, so grooves catch the light
     * like real displaced sand or mud instead of being flat stains. Sand is matte, mud wet and glossy.
     */
    const val TRAIL_FS = """#version 300 es
precision highp float;
uniform float uKind;      // 0 sand, 1 mud
uniform float uPass;      // 0 = raised berms, 1 = grooves
uniform vec3 uCam;
uniform vec3 uLightDir;
in vec2 vUv;
in float vAge;
in vec3 vNrm;
in vec3 vT1;
in vec3 vT2;
in vec3 vWorld;
out vec4 outColor;
float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
void main() {
    float r = length(vUv);
    if (r > 1.0 || vAge < 0.0) discard;
    bool mud = uKind > 0.5;
    float life = mud ? 170.0 : 55.0;                             // sand blows back in, mud stays
    float fade = 1.0 - smoothstep(life * 0.3, life, vAge);
    if (fade <= 0.01) discard;
    vec3 N = normalize(vNrm);
    // height profile: a groove in the middle, berms of pushed-aside material on either side
    float groove = 1.0 - smoothstep(0.20, 0.66, r);
    float berm = smoothstep(0.50, 0.78, r) * (1.0 - smoothstep(0.78, 1.0, r));
    float h = -groove * 1.0 + berm * 0.7;
    float dh = (-(-smoothstep(0.20, 0.66, r) + 0.0) * 0.0);              // placeholder to keep the derivative explicit below
    float eps = 0.02;
    float r2 = r + eps;
    float g2 = 1.0 - smoothstep(0.20, 0.66, r2);
    float b2 = smoothstep(0.50, 0.78, r2) * (1.0 - smoothstep(0.78, 1.0, r2));
    float dhdr = ((-g2 + b2 * 0.7) - h) / eps;
    vec2 dir = r > 1e-4 ? vUv / r : vec2(0.0);
    vec3 Np = normalize(N - (vT1 * dir.x + vT2 * dir.y) * dhdr * (mud ? 0.55 : 0.45));
    float lit = 0.34 + 0.72 * max(dot(Np, uLightDir), 0.0);
    float cavity = 1.0 - 0.18 * groove;                          // ambient occlusion inside the groove
    vec3 col; float a;
    if (uPass < 0.5) {
        col = (mud ? vec3(0.30, 0.18, 0.10) : vec3(0.93, 0.78, 0.53)) * lit;
        a = berm * (mud ? 0.75 : 0.5) * fade;
    } else {
        vec3 base = mud ? vec3(0.085, 0.052, 0.032) : vec3(0.66, 0.51, 0.33);
        col = base * lit * cavity;
        float grain = hash(floor(vWorld.xy * 900.0) + floor(vWorld.zx * 900.0));
        if (!mud) col *= 0.93 + 0.14 * grain;                    // fine sand grain inside the track
        if (mud) {                                               // wet sheen: sharp glints from the tilted groove walls
            vec3 V = normalize(uCam - vWorld);
            vec3 H = normalize(uLightDir + V);
            float spec = pow(max(dot(Np, H), 0.0), 90.0);
            col += vec3(0.75, 0.68, 0.56) * spec * 0.9 * groove;
            col += vec3(0.10, 0.12, 0.14) * pow(1.0 - max(dot(Np, V), 0.0), 3.0) * 0.5 * groove;   // sky sheen at grazing angles
        }
        a = groove * (mud ? 0.92 : 0.7) * fade;
    }
    if (a < 0.01) discard;
    outColor = vec4(col, a);
}
"""

    /** Additive glow sprites for sparks. */
    const val SPARK_VS = """#version 300 es
uniform mat4 uVP;
uniform mat4 uModel;
uniform float uScale;
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aCol;
layout(location=2) in float aLife;
layout(location=3) in float aSize;
out vec3 vCol;
out float vLife;
void main() {
    vec4 clip = uVP * (uModel * vec4(aPos, 1.0));
    gl_Position = clip;
    gl_PointSize = max(aSize * uScale / clip.w, 1.0);
    vCol = aCol;
    vLife = aLife;
}
"""

    const val SPARK_FS = """#version 300 es
precision mediump float;
in vec3 vCol;
in float vLife;
out vec4 outColor;
void main() {
    vec2 d = gl_PointCoord - 0.5;
    float r = dot(d, d) * 4.0;
    float a = exp(-r * 3.5) * vLife;
    outColor = vec4(vCol * (1.0 + vLife) , a);
}
"""

    /** Drifting cloud bank drawn over the scene. */
    const val CLOUD_FS = """#version 300 es
precision highp float;
uniform float uTime;
uniform float uAspect;
uniform float uAmount;
uniform vec3 uTint;
in vec2 vUv;
out vec4 outColor;
float h(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float n(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h(i), h(i + vec2(1, 0)), f.x), mix(h(i + vec2(0, 1)), h(i + vec2(1, 1)), f.x), f.y);
}
float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    for (int i = 0; i < 5; i++) { s += a * n(p); p = p * 2.03 + vec2(1.7, 9.2); a *= 0.5; }
    return s;
}
void main() {
    vec2 p = vec2(vUv.x * uAspect, vUv.y) * 2.2;
    vec2 wind = vec2(0.05, 0.02) * uTime;
    float d = fbm(p + wind + 0.6 * vec2(fbm(p * 0.7 - wind * 1.5), fbm(p * 0.7 + 3.1 + wind)));
    float cover = smoothstep(0.30, 0.56, d);
    float lightSide = fbm(p * 1.3 + wind * 1.2 + 4.0);
    vec3 col = mix(uTint * 0.5 + vec3(0.30, 0.33, 0.40), vec3(0.95, 0.97, 1.0), 0.3 + 0.6 * lightSide);
    float a = cover * 0.62 * uAmount;
    col = pow(col, vec3(1.0 / 2.2));
    outColor = vec4(col, a);
}
"""

    const val SHADOW_FS = """#version 300 es
precision mediump float;
out vec4 o;
void main() { o = vec4(1.0); }
"""

    const val BG_VS = """#version 300 es
out vec2 vUv;
void main() {
    vec2 p = vec2(float((gl_VertexID << 1) & 2), float(gl_VertexID & 2));
    vUv = p;
    gl_Position = vec4(p * 2.0 - 1.0, 0.999, 1.0);
}
"""

    const val BG_FS = """#version 300 es
precision highp float;
uniform vec3 uTop;
uniform vec3 uBottom;
uniform float uAspect;
uniform float uTheme;      // see Backdrop: 0 studio, 1 stars, 2 sky, 3 neon, 4 bokeh, 5 aurora, 6 light shafts
uniform float uTime;
in vec2 vUv;
out vec4 outColor;
float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float vn(vec2 p) {
    vec2 i = floor(p), f = fract(p); f = f * f * (3.0 - 2.0 * f);
    return mix(mix(h21(i), h21(i + vec2(1, 0)), f.x), mix(h21(i + vec2(0, 1)), h21(i + vec2(1, 1)), f.x), f.y);
}
void main() {
    vec3 c = mix(uBottom, uTop * 0.55, smoothstep(0.0, 1.0, vUv.y));
    vec2 q = (vUv - vec2(0.5, 0.52)) * vec2(uAspect, 1.0);
    float v = length(q);
    vec2 p = vec2(vUv.x * uAspect, vUv.y);
    if (uTheme < 0.5) {
        c += uTop * 0.22 * (1.0 - smoothstep(0.0, 0.6, v));      // soft studio glow behind the piece
        vec2 s = (vUv - vec2(0.5, 0.17)) * vec2(1.0, 3.4);
        c *= 1.0 - 0.45 * (1.0 - smoothstep(0.0, 0.34, length(s * vec2(uAspect * 0.65, 1.0))));   // faint floor shadow
    } else if (uTheme < 1.5) {                                  // stars and a nebula
        float neb = vn(p * 3.0 + 4.0) * vn(p * 7.0 - 2.0);
        c += mix(vec3(0.10, 0.03, 0.22), vec3(0.02, 0.12, 0.28), vUv.x) * neb * 2.2;
        for (int i = 0; i < 3; i++) {
            float sc = 38.0 + 46.0 * float(i);
            vec2 g = p * sc, id = floor(g), f = fract(g) - 0.5;
            float h = h21(id + float(i) * 11.0);
            vec2 off = (vec2(h21(id + 3.0), h21(id + 9.0)) - 0.5) * 0.6;
            float d = length(f - off);
            float tw = 0.65 + 0.35 * sin(uTime * (1.5 + 3.0 * h) + h * 40.0);
            c += step(0.972, h) * (1.0 - smoothstep(0.0, 0.07, d)) * tw * mix(vec3(0.8, 0.9, 1.0), vec3(1.0, 0.85, 0.7), h21(id));
        }
    } else if (uTheme < 2.5) {                                  // open sky with a sun and haze near the horizon
        vec2 sp = vec2(0.72 * uAspect, 0.80);
        float sd = length(p - sp);
        c += vec3(1.0, 0.82, 0.55) * (0.55 * exp(-sd * 3.2) + 0.9 * exp(-sd * 14.0));
        c = mix(c, c + vec3(0.25, 0.22, 0.2), exp(-abs(vUv.y - 0.38) * 9.0) * 0.5);
        c += vec3(0.05) * vn(p * vec2(2.0, 6.0) + vec2(uTime * 0.02, 0.0));    // wisps
    } else if (uTheme < 3.5) {                                  // retro sun over a neon grid
        float hor = 0.40;
        if (vUv.y < hor) {
            float z = 1.0 / max(hor - vUv.y, 0.004);
            vec2 gp = vec2((vUv.x - 0.5) * z * 0.55, z * 0.35 + uTime * 0.6);
            vec2 gl = abs(fract(gp) - 0.5);
            float line = (1.0 - smoothstep(0.0, 0.05 + 0.25 / z, min(gl.x, gl.y) * 2.0 - 0.9 + 0.9)) * 0.0;
            float lx = 1.0 - smoothstep(0.0, 0.06, abs(fract(gp.x) - 0.5) - 0.44);
            float ly = 1.0 - smoothstep(0.0, 0.06, abs(fract(gp.y) - 0.5) - 0.44);
            c += vec3(1.0, 0.15, 0.8) * max(lx, ly) * clamp(1.5 / z + 0.15, 0.0, 1.0) * 0.9;
        } else {
            vec2 sp = vec2(0.5 * uAspect, 0.56);
            float sd = length(p - sp);
            float stripe = step(0.5, fract((vUv.y - 0.40) * 22.0 - sd * 0.0));
            float disc = (1.0 - smoothstep(0.17, 0.175, sd)) * mix(1.0, stripe, smoothstep(0.52, 0.40, vUv.y + 0.0));
            c += mix(vec3(1.0, 0.8, 0.1), vec3(1.0, 0.1, 0.6), clamp((vUv.y - 0.40) / 0.32, 0.0, 1.0)) * disc * 1.2;
            c += vec3(1.0, 0.2, 0.7) * 0.25 * exp(-sd * 6.0);
        }
        c += vec3(0.6, 0.1, 0.8) * 0.25 * exp(-abs(vUv.y - hor) * 18.0);
    } else if (uTheme < 4.5) {                                  // drifting bokeh lights
        for (int i = 0; i < 14; i++) {
            float fi = float(i);
            vec2 pc = vec2(h21(vec2(fi, 1.3)) * uAspect, h21(vec2(fi, 7.7)));
            pc += vec2(sin(uTime * 0.15 + fi) * 0.08, cos(uTime * 0.11 + fi * 1.7) * 0.06);
            float rad = 0.05 + 0.08 * h21(vec2(fi, 3.1));
            float d = length(p - pc);
            float disc = (1.0 - smoothstep(rad * 0.85, rad, d)) * 0.5 + (1.0 - smoothstep(0.0, 0.01, abs(d - rad))) * 0.5;
            c += mix(uTop * 1.4, vec3(1.0, 0.9, 0.7), h21(vec2(fi, 9.9))) * disc * 0.16;
        }
    } else if (uTheme < 5.5) {                                  // aurora curtains
        float cur = sin(p.x * 3.0 + uTime * 0.25 + vn(p * 2.0) * 3.0) * 0.5 + 0.5;
        float band = smoothstep(0.52, 0.8, vUv.y) * (1.0 - smoothstep(0.8, 1.0, vUv.y)) * (0.4 + 0.6 * cur);
        c += mix(vec3(0.1, 1.0, 0.6), vec3(0.5, 0.3, 1.0), vUv.y) * band * 0.45;
        c += step(0.985, h21(floor(p * 90.0))) * 0.6 * (1.0 - smoothstep(0.3, 0.9, vUv.y) * 0.0);
    } else if (uTheme < 6.5) {                                  // light shafts through foliage
        float beam = 0.0;
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            float ax = (p.x + p.y * (0.35 + 0.1 * fi)) * (3.0 + fi * 1.7) + fi * 2.0 + uTime * 0.03;
            beam += pow(max(sin(ax), 0.0), 6.0) * (0.5 - 0.1 * fi);
        }
        c += vec3(0.75, 1.0, 0.5) * beam * 0.10 * smoothstep(0.2, 1.0, vUv.y);
        c += uTop * 0.18 * (1.0 - smoothstep(0.0, 0.6, v));
    } else {                                                    // rain-soaked neon megacity in a teal haze
        float y = vUv.y;
        c = mix(vec3(0.006, 0.03, 0.036), vec3(0.0015, 0.004, 0.008), smoothstep(0.2, 0.85, y));
        c += vec3(0.04, 0.38, 0.40) * exp(-abs(y - 0.28) * 5.5) * 0.4;                 // glowing smog at street level
        c += vec3(0.5, 0.05, 0.35) * exp(-abs(y - 0.22) * 9.0) * 0.05;                 // a magenta under-glow from the streets
        // searchlights sweeping up into the haze
        for (int i = 0; i < 4; i++) {
            float fi = float(i);
            vec2 o = vec2((0.15 + 0.24 * fi) * uAspect, 0.12);
            float ang = 1.57 + 0.38 * sin(uTime * (0.13 + 0.05 * fi) + fi * 2.1);
            vec2 dir = vec2(cos(ang), sin(ang));
            vec2 d = p - o; float along = dot(d, dir);
            float dist = abs(d.x * dir.y - d.y * dir.x);
            float beam = exp(-dist * (60.0 - 40.0 * clamp(along, 0.0, 1.0))) * step(0.0, along) * exp(-along * 1.2);
            c += mix(vec3(0.3, 0.95, 1.0), vec3(1.0, 0.85, 0.6), step(2.5, fi)) * beam * 0.22;
        }
        // four layers of towers, far ones lost in the haze
        for (int i = 0; i < 4; i++) {
            float fl = float(3 - i);                                // 3 = farthest
            float sc = 3.0 + 3.2 * fl;
            float x = p.x * sc + fl * 7.3;
            float id = floor(x), fx = fract(x);
            float wdt = 0.55 + 0.4 * h21(vec2(id, fl + 8.0));
            float top = 0.3 + 0.42 * h21(vec2(id, fl + 3.0)) * (1.0 - 0.16 * fl) + 0.05 * fl;
            float inside = step(y, top) * step(abs(fx - 0.5), wdt * 0.5);
            if (inside < 0.5) continue;
            float tx = (fx - 0.5) / wdt + 0.5;                      // 0..1 across this tower
            vec3 col = vec3(0.006, 0.012, 0.02) + vec3(0.0, 0.02, 0.025) * (1.0 - tx);
            vec2 w = vec2(tx * (10.0 + 4.0 * fl), y * (80.0 + 20.0 * fl));
            vec2 wi = floor(w), wf = fract(w);
            float hh = h21(wi + id * 7.0 + fl * 3.0);
            float win = step(0.62, hh) * step(abs(wf.x - 0.5), 0.3) * step(abs(wf.y - 0.5), 0.28);
            col += (hh > 0.93 ? vec3(1.0, 0.3, 0.8) : (hh > 0.85 ? vec3(0.3, 0.95, 1.0) : vec3(1.0, 0.75, 0.4))) * win * 0.55;
            float hs = h21(vec2(id, fl + 11.0));
            vec3 ne = hs < 0.5 ? vec3(0.1, 0.95, 1.0) : vec3(1.0, 0.15, 0.7);
            col += ne * (smoothstep(0.03, 0.0, abs(tx - 0.03)) + smoothstep(0.03, 0.0, abs(tx - 0.97))) * step(0.55, hs) * 0.9;   // neon edge strips
            col += ne * smoothstep(0.006, 0.0, abs(y - top)) * 1.4;                                                                  // roof line
            // giant animated screen on some towers
            float sh = h21(vec2(id, fl + 21.0));
            float sy0 = top - 0.06 - 0.25 * sh, sy1 = sy0 + 0.11 + 0.1 * sh;
            if (sh > 0.45 && y > sy0 && y < sy1 && tx > 0.14 && tx < 0.86) {
                vec2 su = vec2((tx - 0.14) / 0.72, (y - sy0) / (sy1 - sy0));
                float t = uTime * 0.35 + sh * 10.0;
                vec3 a1 = 0.5 + 0.5 * cos(6.2831 * (vec3(0.0, 0.33, 0.67) + t * 0.2 + su.y * 0.6 + sh));
                float blob = smoothstep(0.35, 0.0, length((su - vec2(0.5, 0.55 + 0.1 * sin(t))) * vec2(1.0, 0.7)));
                vec3 scr = mix(a1 * 0.5, vec3(1.0, 0.85, 0.3), blob * 0.8);
                scr *= 0.75 + 0.25 * step(0.5, fract(su.y * 90.0));                                  // scan lines
                scr *= 0.6 + 0.4 * step(0.08, abs(fract(su.y - t * 0.4) - 0.5));                     // rolling bar
                col = mix(col, scr * 1.1, 1.0);
            }
            // a vertical neon sign of glyph blocks down one side
            if (hs > 0.7 && tx > 0.78 && tx < 0.93 && y < top - 0.05 && y > top - 0.32) {
                vec2 g = vec2((tx - 0.78) / 0.15, (top - 0.05 - y) * 28.0);
                vec2 gi = floor(g * vec2(3.0, 1.0)), gf = fract(g * vec2(3.0, 1.0));
                float stroke = step(0.5, h21(gi + id + fl)) * step(abs(gf.y - 0.5), 0.32) * step(abs(gf.x - 0.5), 0.36);
                col += ne.yzx * stroke * (0.8 + 0.2 * sin(uTime * 3.0 + id));
            }
            float haze = 0.1 + 0.2 * fl;                                      // atmospheric depth
            col = mix(col, vec3(0.012, 0.1, 0.11) * (0.5 + 0.8 * exp(-abs(y - 0.28) * 5.0)), haze);
            c = col;
        }
        // flying traffic: streams of head and tail lights crossing the sky
        for (int i = 0; i < 3; i++) {
            float fi = float(i);
            float ly = 0.55 + 0.12 * fi;
            float lane = exp(-abs(y - ly - 0.03 * sin(p.x * 2.0 + fi)) * 900.0);
            float xs = p.x * (9.0 + 4.0 * fi) - uTime * (0.3 + 0.2 * fi) * (mod(fi, 2.0) < 0.5 ? 1.0 : -1.0);
            float dots = step(0.9, fract(xs)) * step(0.55, h21(vec2(floor(xs), fi)));
            c += (mod(fi, 2.0) < 0.5 ? vec3(1.0, 0.18, 0.15) : vec3(1.0, 0.95, 0.85)) * lane * dots * 0.9;
        }
        c += vec3(0.6, 0.9, 1.0) * 0.06 * step(0.985, fract((p.x * 1.0 + y * 0.25) * 60.0 + h21(floor(vec2(p.x * 60.0, 0.0)))) ) * step(0.7, fract(y * 3.0 + uTime * 2.6 + h21(vec2(floor(p.x * 60.0), 1.0))));   // rain streaks
    }
    c *= 1.0 - 0.6 * smoothstep(0.35, 1.05, v);                 // vignette
    c = pow(max(c, 0.0), vec3(1.0 / 2.2));
    outColor = vec4(c + (h21(gl_FragCoord.xy) - 0.5) / 255.0, 1.0);
}
"""

    fun program(vs: String, fs: String): Int {
        val v = compile(GLES30.GL_VERTEX_SHADER, vs)
        val f = compile(GLES30.GL_FRAGMENT_SHADER, fs)
        val p = GLES30.glCreateProgram()
        GLES30.glAttachShader(p, v)
        GLES30.glAttachShader(p, f)
        GLES30.glLinkProgram(p)
        val ok = IntArray(1)
        GLES30.glGetProgramiv(p, GLES30.GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetProgramInfoLog(p)
            Log.e("Maze3D", "link failed: $log")
            throw RuntimeException("Program link failed: $log")
        }
        return p
    }

    private fun compile(type: Int, src: String): Int {
        val s = GLES30.glCreateShader(type)
        GLES30.glShaderSource(s, src)
        GLES30.glCompileShader(s)
        val ok = IntArray(1)
        GLES30.glGetShaderiv(s, GLES30.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(s)
            Log.e("Maze3D", "shader failed: $log")
            throw RuntimeException("Shader compile failed: $log")
        }
        return s
    }
}
