package com.maze3d.gl

import android.opengl.GLES30
import android.util.Log

object Shaders {
    const val SCENE_VS = """#version 300 es
uniform mat4 uVP;
uniform mat4 uModel;
uniform float uGrowT;
layout(location=0) in vec3 aPos;
layout(location=1) in vec3 aNrm;
layout(location=2) in vec3 aCol;
layout(location=3) in vec3 aLift;
layout(location=4) in float aDelay;
out vec3 vWorld;
out vec3 vNrm;
out vec3 vCol;
out float vH;
void main() {
    float g = clamp((uGrowT - aDelay) / 0.35, 0.0, 1.0);
    g = g * g * (3.0 - 2.0 * g);
    vec3 p = aPos + aLift * g;
    vec4 w = uModel * vec4(p, 1.0);
    vWorld = w.xyz;
    vNrm = mat3(uModel) * aNrm;
    vCol = aCol;
    vH = g;
    gl_Position = uVP * w;
}
"""

    const val SCENE_FS = """#version 300 es
precision highp float;
uniform vec3 uCam;
uniform float uSpec;
uniform float uShine;
uniform float uEmissive;
uniform float uTime;
in vec3 vWorld;
in vec3 vNrm;
in vec3 vCol;
in float vH;
out vec4 outColor;

vec3 env(vec3 r) {
    float up = r.y * 0.5 + 0.5;
    vec3 c = mix(vec3(0.03, 0.03, 0.04), vec3(0.55, 0.60, 0.70), smoothstep(0.1, 0.95, up));
    // soft boxes
    c += vec3(1.6, 1.5, 1.35) * smoothstep(0.86, 0.93, dot(r, normalize(vec3(0.5, 0.8, 0.6))));
    c += vec3(0.5, 0.7, 1.0) * 0.6 * smoothstep(0.82, 0.92, dot(r, normalize(vec3(-0.8, 0.2, 0.5))));
    return c;
}

void main() {
    vec3 N = normalize(vNrm);
    vec3 V = normalize(uCam - vWorld);
    if (dot(N, V) < -0.2 && uEmissive < 0.01) { /* back-facing helps nothing; keep two-sided lit */ }
    vec3 L1 = normalize(vec3(0.5, 0.8, 0.6));
    vec3 L2 = normalize(vec3(-0.8, 0.2, 0.5));
    vec3 amb = mix(vec3(0.10, 0.10, 0.12), vec3(0.34, 0.37, 0.45), N.y * 0.5 + 0.5);
    float d1 = max(dot(N, L1), 0.0);
    float d2 = max(dot(N, L2), 0.0);
    // wrap lighting so surfaces turned away from the key light never go pitch black
    float d3 = max(dot(N, V), 0.0) * 0.25;
    vec3 lit = vCol * (amb + vec3(1.05, 0.98, 0.9) * d1 + vec3(0.30, 0.38, 0.55) * d2 + d3);
    vec3 H1 = normalize(L1 + V);
    float sp = pow(max(dot(N, H1), 0.0), uShine) * uSpec;
    vec3 refl = env(reflect(-V, N));
    float fres = pow(1.0 - max(dot(N, V), 0.0), 4.0);
    lit += refl * (uSpec * 0.32 + fres * 0.14) * mix(vec3(1.0), vCol, 0.55);
    lit += vec3(1.0, 0.95, 0.85) * sp;
    lit += vCol * uEmissive * (0.85 + 0.15 * sin(uTime * 3.0));
    // filmic-ish tone map + gamma
    lit = lit / (1.0 + lit * 0.35);
    lit = pow(lit, vec3(1.0 / 2.2));
    outColor = vec4(lit, 1.0);
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

vec3 env(vec3 r) {
    float up = r.y * 0.5 + 0.5;
    vec3 c = mix(vec3(0.02, 0.02, 0.03), vec3(0.75, 0.80, 0.92), smoothstep(0.15, 0.95, up));
    c += vec3(2.2, 2.0, 1.8) * smoothstep(0.84, 0.93, dot(r, normalize(vec3(0.5, 0.8, 0.6))));
    c += vec3(0.6, 0.8, 1.3) * 0.7 * smoothstep(0.80, 0.92, dot(r, normalize(vec3(-0.8, 0.2, 0.5))));
    c += vec3(0.25) * smoothstep(0.7, 1.0, -r.y);
    return c;
}

void main() {
    vec3 N = normalize(vNrmW);
    vec3 V = normalize(uCam - vWorld);
    float ndv = max(dot(N, V), 0.0);
    if (uGhost > 0.5) {
        // see-through silhouette when the labyrinth hides the marble
        float rim = pow(1.0 - ndv, 1.5);
        outColor = vec4(uAccent * (0.5 + 0.8 * rim), 0.28 + 0.4 * rim);
        return;
    }
    vec3 b = uBody * normalize(vNrmL);
    // glass-marble ribbons that make the rolling visible
    float s = sin(b.x * 7.0 + sin(b.y * 5.0 + b.z * 3.0) * 1.6);
    float band = smoothstep(0.55, 0.8, s) * (1.0 - smoothstep(0.88, 0.97, s));
    vec3 base = mix(vec3(0.86, 0.9, 0.98), uAccent, band * 0.9);
    vec3 r = reflect(-V, N);
    float fres = 0.12 + 0.88 * pow(1.0 - ndv, 4.0);
    vec3 col = base * env(r) * (0.65 + 0.35 * fres) + base * 0.05;
    col += vec3(1.0) * pow(max(dot(r, normalize(vec3(0.5, 0.8, 0.6))), 0.0), 120.0) * 2.0;
    col = col / (1.0 + col * 0.35);
    col = pow(col, vec3(1.0 / 2.2));
    outColor = vec4(col, 1.0);
}
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
in vec2 vUv;
out vec4 outColor;
void main() {
    vec3 c = mix(uBottom, uTop, smoothstep(0.0, 1.0, vUv.y));
    float v = length(vUv - vec2(0.5, 0.55));
    c += uTop * 0.35 * (1.0 - smoothstep(0.0, 0.55, v));
    c *= 1.0 - 0.6 * smoothstep(0.3, 0.9, v);
    c = pow(c, vec3(1.0 / 2.2));
    outColor = vec4(c, 1.0);
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
