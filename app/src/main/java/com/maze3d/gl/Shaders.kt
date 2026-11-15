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

void main() {
    vec3 N = normalize(vNrm);
    vec3 V = normalize(uCam - vWorld);
    vec3 albedo = pow(vCol, vec3(2.2));
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
        rough = 0.42 - 0.12 * dir;
        coat = 1.0;
    } else if (uMat == 2) {     // brushed metal
        float n1 = vnoise(vec2(P.x * 8.0 + P.z * 5.0, P.y * 260.0));
        float n2 = vnoise(vec2(P.y * 8.0 + P.x * 5.0, P.z * 260.0));
        float n3 = vnoise(vec2(P.z * 8.0 + P.y * 5.0, P.x * 260.0));
        float n = n1 * tw.x + n2 * tw.y + n3 * tw.z;
        albedo *= 0.88 + 0.24 * n;
        rough = uRough + 0.10 * (n - 0.5);
    } else if (uMat == 3) {     // wood grain
        float g1 = vnoise(vec2(P.y * 70.0, P.z * 5.0)) + 0.5 * vnoise(vec2(P.y * 230.0, P.z * 14.0));
        float g2 = vnoise(vec2(P.z * 70.0, P.x * 5.0)) + 0.5 * vnoise(vec2(P.z * 230.0, P.x * 14.0));
        float g3 = vnoise(vec2(P.x * 70.0, P.y * 5.0)) + 0.5 * vnoise(vec2(P.x * 230.0, P.y * 14.0));
        float g = (g1 * tw.x + g2 * tw.y + g3 * tw.z) / 1.5;
        float ring = 0.5 + 0.5 * sin(g * 28.0);
        albedo *= 0.70 + 0.55 * mix(g, ring, 0.45);
        rough = 0.40 + 0.12 * g;
        coat = 0.6;
    } else if (uMat == 0) {     // lacquer / enamel
        coat = 1.0;
    }

    if (uMat == 4) {            // markers and the thread: glowing, unlit
        vec3 e = vCol * pow(vec3(1.0), vec3(2.2)) * uEmissive * (1.0 + 0.2 * sin(uTime * 3.0));
        vec3 col = albedo * (0.25 + 0.75 * max(dot(N, uLightDir), 0.0)) * 0.6 + pow(vCol, vec3(2.2)) * uEmissive * (1.0 + 0.2 * sin(uTime * 3.0)) * 2.2;
        outColor = vec4(finish(col, gl_FragCoord.xy), 1.0);
        return;
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
in vec3 vWorld;
in vec3 vNrmW;
in vec3 vNrmL;
out vec4 outColor;
""" + COMMON + """
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
    vec3 b = uBody * normalize(vNrmL);
    // ribbons through the glass make the roll visible
    float s = sin(b.x * 7.0 + sin(b.y * 5.0 + b.z * 3.0) * 1.6);
    float band = smoothstep(0.55, 0.8, s) * (1.0 - smoothstep(0.88, 0.97, s));
    vec3 albedo = mix(vec3(0.93, 0.95, 0.98), pow(uAccent, vec3(2.2)), band * 0.9);
    float rough = 0.05;
    vec3 F0 = albedo;
    vec3 R = reflect(-V, N);
    vec3 col = envMap(R, rough) * envBRDF(F0, rough, NoV);
    col += irradiance(N) * albedo * 0.05;
    vec3 H = normalize(uLightDir + V);
    col += pow(max(dot(N, H), 0.0), 400.0) * vec3(6.0);
    outColor = vec4(finish(col, gl_FragCoord.xy), 1.0);
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
