package com.maze3d.gl

/** Full-screen post-processing passes. All use [Shaders.BG_VS] (a single big triangle, vUv 0..1 over the screen). */
object PostShaders {
    private const val HEAD = "#version 300 es\nprecision highp float;\nin vec2 vUv;\nout vec4 outColor;\n"

    /** 4-tap box downsample. */
    const val DOWN_FS = HEAD + """
uniform sampler2D uTex;
uniform vec2 uTexel;
void main() {
    vec4 c = texture(uTex, vUv + uTexel * vec2(-0.5, -0.5)) + texture(uTex, vUv + uTexel * vec2(0.5, -0.5))
           + texture(uTex, vUv + uTexel * vec2(-0.5, 0.5)) + texture(uTex, vUv + uTexel * vec2(0.5, 0.5));
    outColor = c * 0.25;
}
"""

    /** Separable 9-tap gaussian. uDir = texel step along the blur axis. */
    const val BLUR_FS = HEAD + """
uniform sampler2D uTex;
uniform vec2 uDir;
void main() {
    vec4 c = texture(uTex, vUv) * 0.2270270270;
    c += (texture(uTex, vUv + uDir * 1.3846153846) + texture(uTex, vUv - uDir * 1.3846153846)) * 0.3162162162;
    c += (texture(uTex, vUv + uDir * 3.2307692308) + texture(uTex, vUv - uDir * 3.2307692308)) * 0.0702702703;
    outColor = c;
}
"""

    /** Keeps only the bright parts (soft knee). */
    const val BRIGHT_FS = HEAD + """
uniform sampler2D uTex;
uniform vec2 uTexel;
uniform float uThreshold;
void main() {
    vec3 c = (texture(uTex, vUv + uTexel * vec2(-0.5, -0.5)).rgb + texture(uTex, vUv + uTexel * vec2(0.5, -0.5)).rgb
            + texture(uTex, vUv + uTexel * vec2(-0.5, 0.5)).rgb + texture(uTex, vUv + uTexel * vec2(0.5, 0.5)).rgb) * 0.25;
    float l = max(c.r, max(c.g, c.b));
    float knee = 0.18;
    float w = clamp((l - uThreshold + knee) / (2.0 * knee), 0.0, 1.0);
    w = w * w;
    outColor = vec4(c * w, 1.0);
}
"""

    /** Screen-space ambient occlusion from the resolved depth buffer; output in .r. */
    const val SSAO_FS = HEAD + """
uniform sampler2D uDepth;
uniform vec4 uProj;      // x = near, y = far, z = proj[0][0], w = proj[1][1]
uniform vec2 uTexel;     // 1 / size of the depth texture
uniform float uRadius;
uniform float uStrength;
float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
float linZ(float d) { float z = d * 2.0 - 1.0; return 2.0 * uProj.x * uProj.y / (uProj.y + uProj.x - z * (uProj.y - uProj.x)); }
vec3 vpos(vec2 uv) {
    float z = linZ(texture(uDepth, uv).r);
    return vec3((uv * 2.0 - 1.0) * vec2(1.0 / uProj.z, 1.0 / uProj.w) * z, -z);
}
void main() {
    float d0 = texture(uDepth, vUv).r;
    if (d0 >= 0.99999) { outColor = vec4(1.0); return; }
    vec3 P = vpos(vUv);
    vec3 Pr = vpos(vUv + vec2(uTexel.x, 0.0)), Pl = vpos(vUv - vec2(uTexel.x, 0.0));
    vec3 Pu = vpos(vUv + vec2(0.0, uTexel.y)), Pd = vpos(vUv - vec2(0.0, uTexel.y));
    vec3 dx = abs(Pr.z - P.z) < abs(P.z - Pl.z) ? Pr - P : P - Pl;
    vec3 dy = abs(Pu.z - P.z) < abs(P.z - Pd.z) ? Pu - P : P - Pd;
    vec3 N = normalize(cross(dx, dy));
    if (N.z < 0.0) N = -N;
    float ang = hash(gl_FragCoord.xy) * 6.2831853;
    mat2 R = mat2(cos(ang), sin(ang), -sin(ang), cos(ang));
    float occ = 0.0;
    const int K = 12;
    for (int i = 0; i < K; i++) {
        float fi = float(i);
        float a = fi * 2.399963;                      // golden angle spiral
        float r = sqrt((fi + 0.5) / float(K));
        vec2 o = R * vec2(cos(a), sin(a)) * r;
        vec3 S = P + (vec3(o, 0.0) + N * (0.35 + 0.65 * r)) * uRadius;
        vec2 suv = (S.xy / -S.z) * vec2(uProj.z, uProj.w) * 0.5 + 0.5;
        float sd = linZ(texture(uDepth, suv).r);
        float diff = (-S.z) - sd;
        occ += step(0.004, diff) * smoothstep(0.0, 1.0, uRadius / max(abs(-P.z - sd), 1e-4));
    }
    outColor = vec4(vec3(clamp(1.0 - uStrength * occ / float(K), 0.0, 1.0)), 1.0);
}
"""

    /** Cloud banks rendered at low resolution: premultiplied colour + coverage. Three parallax layers, lit from above-left. */
    const val CLOUD_FS = HEAD + """
uniform float uTime;
uniform float uAspect;
uniform float uAmount;     // fade in/out 0..1
uniform float uDensity;    // 0.5 sparse .. 1.0 overcast
uniform vec2 uWind;
uniform vec3 uTint;        // base cloud colour (bright)
uniform vec3 uShade;       // shadowed cloud colour
// Gradient noise on an integer hash, rotated between octaves: no visible lattice or axis-aligned blocks.
uint hashU(uvec2 p) {
    uvec3 q = uvec3(p, p.x ^ p.y) * uvec3(1597334673u, 3812015801u, 2798796415u);
    uint h = (q.x ^ q.y ^ q.z) * 1664525u + 1013904223u;
    h ^= h >> 15; h *= 2246822519u; h ^= h >> 13;
    return h;
}
vec2 grad(vec2 cell) {
    uint h = hashU(uvec2(ivec2(cell) + ivec2(32768)));
    float a = float(h & 65535u) * (6.2831853 / 65535.0);
    return vec2(cos(a), sin(a));
}
float n(vec2 p) {
    vec2 i = floor(p), f = fract(p);
    vec2 u = f * f * f * (f * (f * 6.0 - 15.0) + 10.0);
    float a = dot(grad(i), f);
    float b = dot(grad(i + vec2(1, 0)), f - vec2(1, 0));
    float c = dot(grad(i + vec2(0, 1)), f - vec2(0, 1));
    float d = dot(grad(i + vec2(1, 1)), f - vec2(1, 1));
    return 0.5 + 0.7 * mix(mix(a, b, u.x), mix(c, d, u.x), u.y);
}
float fbm(vec2 p) {
    float s = 0.0, a = 0.5;
    mat2 R = mat2(0.80, -0.60, 0.60, 0.80);
    for (int i = 0; i < 6; i++) { s += a * n(p); p = R * p * 2.03 + vec2(1.7, 9.2); a *= 0.5; }
    return s;
}
// density of one layer at p; coverage threshold adapts to uDensity so thin days still have clear banks
float layer(vec2 p, vec2 wind, float scale, float t, float cut) {
    vec2 q = p * scale + wind * t;
    float w = fbm(q * 0.6 + 2.3 * vec2(fbm(q * 0.5 + wind * t * 0.4), fbm(q * 0.5 + 7.1 - wind * t * 0.3)));
    return smoothstep(cut, cut + 0.28, w);
}
void main() {
    vec2 p = vec2(vUv.x * uAspect, vUv.y);
    float cut = mix(0.74, 0.36, uDensity);
    vec4 acc = vec4(0.0);
    vec2 L = vec2(-0.03, 0.045);                     // light direction in uv: shade by looking toward the light
    for (int i = 0; i < 3; i++) {
        float fi = float(i);
        float scale = 1.6 + 1.5 * fi;                // far layers big and slow, near layers small and fast
        float speed = 0.45 + 0.9 * fi;
        float t = uTime * 0.30 * speed;
        float d = layer(p + fi * 3.7, uWind, scale, t, cut + 0.02 * fi);
        float dl = layer(p + fi * 3.7 + L * (1.0 + fi), uWind, scale, t, cut + 0.02 * fi);
        float lit = clamp(0.55 + (d - dl) * 3.2, 0.0, 1.0);
        vec3 col = mix(uShade, uTint, lit);
        float a = d * (0.28 + 0.17 * fi) * (0.55 + 0.45 * uDensity);
        // composite this layer over what we have (front layers come last)
        acc.rgb = acc.rgb * (1.0 - a) + col * a;
        acc.a = acc.a + a * (1.0 - acc.a);
    }
    acc *= uAmount;
    outColor = acc;                                  // premultiplied
}
"""

    /** Everything that happens between the resolved scene and the final AA pass. */
    const val COMPOSITE_FS = HEAD + """
uniform sampler2D uScene;
uniform sampler2D uAO;
uniform sampler2D uBlur;
uniform sampler2D uDepth;
uniform sampler2D uBloom;
uniform sampler2D uCloud;
uniform vec4 uProj;
uniform float uFocus;        // view-space distance that is in focus
uniform float uFocusRange;
uniform float uAOAmt;
uniform float uDofAmt;
uniform float uBloomAmt;
uniform float uCloudOn;
uniform vec3 uGrade;         // x contrast, y saturation, z warmth
uniform float uMotion;       // radial speed blur strength (chase view)
uniform float uVignette;
float linZ(float d) { float z = d * 2.0 - 1.0; return 2.0 * uProj.x * uProj.y / (uProj.y + uProj.x - z * (uProj.y - uProj.x)); }
void main() {
    vec3 c = texture(uScene, vUv).rgb;
    if (uMotion > 0.01) {                                     // radial blur toward the edges: a sense of speed
        vec2 dv = vUv - vec2(0.5, 0.55);
        float edge = smoothstep(0.12, 0.7, length(dv));
        vec3 acc = c;
        for (int i = 1; i < 6; i++) acc += texture(uScene, vUv - dv * (uMotion * edge * 0.045 * float(i))).rgb;
        c = acc / 6.0;
    }
    float d = texture(uDepth, vUv).r;
    float ao = texture(uAO, vUv).r;
    c *= mix(1.0, ao, uAOAmt);
    if (uDofAmt > 0.0 && d < 0.99999) {
        float z = linZ(d);
        float coc = smoothstep(0.0, uFocusRange, z - uFocus);
        c = mix(c, texture(uBlur, vUv).rgb, coc * uDofAmt);
    }
    if (uCloudOn > 0.5) {
        vec4 cl = texture(uCloud, vUv);
        c = c * (1.0 - cl.a) + cl.rgb;
    }
    c += texture(uBloom, vUv).rgb * uBloomAmt * vec3(1.0, 0.96, 0.9);
    // colour grade: gentle S-curve, saturation, warmth
    float l = dot(c, vec3(0.299, 0.587, 0.114));
    c = mix(vec3(l), c, uGrade.y);
    c = mix(c, c * c * (3.0 - 2.0 * c), uGrade.x);
    c *= vec3(1.0 + uGrade.z, 1.0, 1.0 - uGrade.z);
    vec2 q = vUv - 0.5;
    c *= 1.0 - uVignette * smoothstep(0.25, 0.95, length(q * vec2(1.0, 1.2)));
    outColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
"""

    /** FXAA (luma edge-directed blur) plus film grain and dither. Reads the composite, writes the screen. */
    const val FXAA_FS = HEAD + """
uniform sampler2D uTex;
uniform vec2 uTexel;
uniform float uTime;
uniform float uGrain;
uniform float uFxaa;
float luma(vec3 c) { return dot(c, vec3(0.299, 0.587, 0.114)); }
float hash(vec2 p) { p = fract(p * vec2(123.34, 456.21)); p += dot(p, p + 45.32); return fract(p.x * p.y); }
void main() {
    vec3 rgbM = texture(uTex, vUv).rgb;
    vec3 col = rgbM;
    if (uFxaa > 0.5) {
        vec3 rgbNW = texture(uTex, vUv + uTexel * vec2(-1.0, -1.0)).rgb;
        vec3 rgbNE = texture(uTex, vUv + uTexel * vec2(1.0, -1.0)).rgb;
        vec3 rgbSW = texture(uTex, vUv + uTexel * vec2(-1.0, 1.0)).rgb;
        vec3 rgbSE = texture(uTex, vUv + uTexel * vec2(1.0, 1.0)).rgb;
        float lM = luma(rgbM), lNW = luma(rgbNW), lNE = luma(rgbNE), lSW = luma(rgbSW), lSE = luma(rgbSE);
        float lMin = min(lM, min(min(lNW, lNE), min(lSW, lSE)));
        float lMax = max(lM, max(max(lNW, lNE), max(lSW, lSE)));
        vec2 dir = vec2(-((lNW + lNE) - (lSW + lSE)), (lNW + lSW) - (lNE + lSE));
        float dirReduce = max((lNW + lNE + lSW + lSE) * 0.25 * 0.125, 1.0 / 128.0);
        float rcp = 1.0 / (min(abs(dir.x), abs(dir.y)) + dirReduce);
        dir = clamp(dir * rcp, vec2(-8.0), vec2(8.0)) * uTexel;
        vec3 a = 0.5 * (texture(uTex, vUv + dir * (1.0 / 3.0 - 0.5)).rgb + texture(uTex, vUv + dir * (2.0 / 3.0 - 0.5)).rgb);
        vec3 b = a * 0.5 + 0.25 * (texture(uTex, vUv + dir * -0.5).rgb + texture(uTex, vUv + dir * 0.5).rgb);
        float lB = luma(b);
        col = (lB < lMin || lB > lMax) ? a : b;
    }
    float g = hash(gl_FragCoord.xy + fract(uTime) * 91.7) - 0.5;
    col += g * uGrain;
    col += (hash(gl_FragCoord.xy) - 0.5) / 255.0;
    outColor = vec4(col, 1.0);
}
"""

    /** Soft round particles that blend normally (dust, puffs, splashes). Uses SPARK_VS. */
    const val SMOKE_FS = """#version 300 es
precision mediump float;
in vec3 vCol;
in float vLife;
out vec4 outColor;
void main() {
    vec2 d = gl_PointCoord - 0.5;
    float r = dot(d, d) * 4.0;
    float a = exp(-r * 2.4) * vLife * 0.42;
    outColor = vec4(vCol, a);
}
"""
}
