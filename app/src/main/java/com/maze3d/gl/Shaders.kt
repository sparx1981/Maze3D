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
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNrm;
layout(location=2) in vec3 aCol;
layout(location=3) in vec3 aLift;
layout(location=4) in float aDelay;
out vec3 vWorld;
out vec3 vNrm;
out vec3 vCol;
out vec3 vLocal;
out vec3 vNL;
void main() {
    float g = clamp((uGrowT - aDelay) / 0.35, 0.0, 1.0);
    g = g * g * (3.0 - 2.0 * g);
    if (uDoorMode > 0.5) {
        // keep in sync with Hazards.doorG: smoothstep(0.4, 0.8, 0.5 + 0.5 sin(1.15 t + 2 pi phase))
        float s = 0.5 + 0.5 * sin(1.15 * uTime + aDelay * 6.2831853);
        g = smoothstep(0.4, 0.8, s) * smoothstep(0.0, 0.6, uGrowT);
    }
    vec3 p = aPos + aLift * g;
    vec4 w = uModel * vec4(p, 1.0);
    vWorld = w.xyz;
    vNrm = mat3(uModel) * aNrm;
    vNL = aNrm;
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
vec3 finish(vec3 c, vec2 frag) {
    c = aces(c);
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
uniform float uShadowOn;
uniform float uWet;        // 0..1 rain wetness
uniform float uAlpha;
uniform float uTexel;
uniform mat4 uLightVP;
uniform sampler2DShadow uShadow;
in vec3 vWorld;
in vec3 vNrm;
in vec3 vCol;
in vec3 vLocal;
in vec3 vNL;
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

void main() {
    vec3 N = normalize(vNrm);
    vec3 V = normalize(uCam - vWorld);
    vec3 albedo = pow(vCol, vec3(2.2));
    float hgt = 0.0;
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
        float grain = vnoise(vec2(dot(P, vec3(310.0, 170.0, 250.0)), dot(P, vec3(-190.0, 330.0, 120.0))));
        float lump = vnoise(vec2(dot(P, vec3(14.0, 9.0, 11.0)), dot(P, vec3(-8.0, 15.0, 10.0))));
        float rip = sin(dot(P, vec3(70.0, 20.0, 45.0)) + 3.0 * lump);
        albedo *= 0.80 + 0.2 * lump + 0.22 * (grain - 0.5) * 2.0 * fade;
        hgt = (rip * 0.0004 + (grain - 0.5) * 0.0004 * fade);
        rough = 0.96;
    } else if (uMat == 6) {     // mud: dark, lumpy, wet sheen
        float lump = vnoise(vec2(dot(P, vec3(26.0, 14.0, 20.0)), dot(P, vec3(-12.0, 28.0, 16.0))));
        float fine = vnoise(vec2(dot(P, vec3(150.0, 90.0, 120.0)), dot(P, vec3(-80.0, 160.0, 70.0))));
        albedo *= 0.55 + 0.75 * lump;
        hgt = (lump - 0.5) * 0.0014 + (fine - 0.5) * 0.0003;
        rough = 0.22 + 0.4 * (1.0 - lump);
        coat = 0.85;
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
    if (uWet > 0.0 && uMat != 4) {                         // rain: darker, glossier surfaces
        albedo *= mix(1.0, 0.7, uWet);
        rough = mix(rough, rough * 0.45, uWet);
        coat = max(coat, uWet * 0.9);
    }
    float NoV = max(dot(N, V), 1e-3);
    float a = max(rough * rough, 0.015);
    vec3 F0 = mix(vec3(0.04), albedo, metal);
    vec3 diffC = albedo * (1.0 - metal);
    float sh = shadowAt(vWorld, N);

    // key light
    vec3 L = uLightDir;
    vec3 H = normalize(L + V);
    float NoL = max(dot(N, L), 0.0);
    float NoH = max(dot(N, H), 0.0);
    float VoH = max(dot(V, H), 0.0);
    vec3 keyC = vec3(1.0, 0.94, 0.86) * 3.4;
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
    col += irradiance(N) * diffC * 1.0 + spec * occ;
    // rim light
    col += pow(1.0 - NoV, 3.5) * vec3(0.10, 0.12, 0.16) * (0.4 + 0.6 * metal);

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
in vec3 vWorld;
in vec3 vNrmW;
in vec3 vNrmL;
out vec4 outColor;
""" + COMMON + """
// Studio lighting blended with the live reflection of the maze around the marble.
vec3 surroundings(vec3 d, float rough) {
    vec3 st = envMap(d, rough);
    if (uCubeOn > 0.5) {
        vec4 c = texture(uCube, d);
        st = mix(st, pow(c.rgb, vec3(2.2)), c.a);
    }
    return st;
}

void main() {
    vec3 N = normalize(vNrmW);
    vec3 V = normalize(uCam - vWorld);
    float NoV = max(dot(N, V), 1e-3);
    if (uGhost > 0.5) {
        // see-through silhouette when the labyrinth hides the marble
        float rim = pow(1.0 - NoV, 1.5);
        outColor = vec4(uAccent * (0.5 + 0.8 * rim), 0.28 + 0.4 * rim);
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
    vec3 refr = surroundings(T, 0.12) * mix(vec3(0.5, 0.62, 0.86), acc, 0.12) * 0.85;
    refr *= mix(1.0, 0.3, pow(1.0 - NoV, 1.6));                 // thicker glass toward the rim
    refr = mix(refr, acc * 1.6 + refr * 0.25, band * 0.85);
    vec3 refl = surroundings(R, 0.03);
    vec3 col = refr * (1.0 - F) + refl * (0.18 + 0.82 * F);
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
    vNrm = mat3(uModel) * n;
    vUv = c;
    vAge = uTime - aPA.w;
    gl_Position = uVP * w;
}
"""

    const val TRAIL_FS = """#version 300 es
precision highp float;
uniform float uKind;      // 0 sand, 1 mud
uniform float uPass;      // 0 = raised ridges, 1 = grooves
uniform vec3 uCam;
uniform vec3 uLightDir;
in vec2 vUv;
in float vAge;
in vec3 vNrm;
in vec3 vWorld;
out vec4 outColor;
void main() {
    float r = length(vUv);
    if (r > 1.0 || vAge < 0.0) discard;
    bool mud = uKind > 0.5;
    float life = mud ? 150.0 : 50.0;                            // sand blows back in, mud stays
    float fade = 1.0 - smoothstep(life * 0.3, life, vAge);
    if (fade <= 0.01) discard;
    vec3 N = normalize(vNrm);
    float lit = 0.40 + 0.60 * max(dot(N, uLightDir), 0.0);
    vec3 col; float a;
    if (uPass < 0.5) {
        float ridge = smoothstep(0.50, 0.82, r) * (1.0 - smoothstep(0.82, 1.0, r));
        col = (mud ? vec3(0.22, 0.13, 0.08) : vec3(0.97, 0.83, 0.58)) * lit * (mud ? 0.9 : 1.0);
        a = ridge * (mud ? 0.60 : 0.34) * fade;
    } else {
        float groove = 1.0 - smoothstep(0.30, 0.80, r);
        col = (mud ? vec3(0.045, 0.028, 0.018) : vec3(0.60, 0.46, 0.29)) * lit;
        if (mud) {                                               // wet sheen along the groove
            vec3 V = normalize(uCam - vWorld);
            vec3 H = normalize(uLightDir + V);
            col += vec3(0.55, 0.5, 0.42) * pow(max(dot(N, H), 0.0), 60.0) * groove * 0.5;
        }
        a = groove * (mud ? 0.88 : 0.60) * fade;
    }
    if (a < 0.01) discard;
    outColor = vec4(col, a);
}
"""

    /** Floor hole: uv in aCol.xy, phase in aCol.z (see Hazards.holeOpen: smoothstep(0, 0.6, sin(0.9 t + 2 pi phase))). */
    const val HOLE_FS = """#version 300 es
precision highp float;
uniform float uTime;
uniform float uAppear;
uniform vec3 uAccent;
in vec3 vCol;
in vec3 vNrm;
out vec4 outColor;
""" + COMMON + """
void main() {
    vec2 uv = vCol.xy;
    float r = length(uv);
    float ph = 0.9 * uTime + vCol.z * 6.2831853;
    float s = sin(ph);
    float a = smoothstep(0.0, 0.6, s) * uAppear;
    float R = a;                        // opening radius in uv units (1.0 = full hole)
    float fw = fwidth(r) * 1.5;
    float lip = 1.0 - smoothstep(0.0, 0.12 + fw, abs(r - R - 0.06));   // glowing rim just outside
    float inside = 1.0 - smoothstep(R - fw, R + fw, r);
    float rimGlow = smoothstep(R - 0.4, R, r) * inside;
    vec3 acc = pow(uAccent, vec3(2.2));
    vec3 col = mix(vec3(0.004, 0.004, 0.008), acc * 1.4, rimGlow * 0.55);
    float alpha = max(inside, lip * 0.9 * step(0.02, a));
    col = mix(col, acc * 2.2, lip * (1.0 - inside) * 0.9);

    // A shut hole is never invisible: a faint ring marks the spot and pulses just before it opens.
    float ring = 1.0 - smoothstep(0.0, 0.06 + fw, abs(r - 1.0));
    float soon = step(0.0, cos(ph)) * smoothstep(-0.6, -0.05, min(s, 0.0)) * step(s, 0.0);
    float warn = (0.30 + 0.70 * soon * (0.65 + 0.35 * sin(uTime * 16.0))) * (1.0 - smoothstep(0.0, 0.25, a)) * uAppear;
    float ringA = ring * warn;
    col = mix(col, acc * 2.4, ringA * step(alpha, ringA));
    alpha = max(alpha, ringA);

    if (alpha < 0.01) discard;
    outColor = vec4(finish(col, gl_FragCoord.xy), alpha);
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
in vec2 vUv;
out vec4 outColor;
float h21(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
void main() {
    vec3 c = mix(uBottom, uTop * 0.55, smoothstep(0.0, 1.0, vUv.y));
    vec2 q = (vUv - vec2(0.5, 0.52)) * vec2(uAspect, 1.0);
    float v = length(q);
    c += uTop * 0.22 * (1.0 - smoothstep(0.0, 0.6, v));      // soft studio glow behind the piece
    // faint floor shadow under the floating piece
    vec2 s = (vUv - vec2(0.5, 0.17)) * vec2(1.0, 3.4);
    c *= 1.0 - 0.45 * (1.0 - smoothstep(0.0, 0.34, length(s * vec2(uAspect * 0.65, 1.0)))) ;
    c *= 1.0 - 0.7 * smoothstep(0.35, 1.05, v);                 // vignette
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
