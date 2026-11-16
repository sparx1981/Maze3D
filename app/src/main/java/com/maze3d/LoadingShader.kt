package com.maze3d

/**
 * AGSL (Android 13+) program for the Marblix loading screen: a ray-marched glass marble rolling along a golden thread
 * through a maze whose walls rise out of the dark. Written in the common subset of AGSL and GLSL so tools/loading can
 * render the very same source in a desktop WebGL preview.
 */
object LoadingShader {
    const val SRC = """
uniform float2 uRes;
uniform float uTime;
uniform float uQ;           // 1 = full quality, 0 = cheaper (no refraction march)

const float S = 1.35;       // maze cell size
const float TH = 0.055;     // wall half thickness

float hash21(float2 p) {
    p = fract(p * float2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float sdBox(float3 p, float3 b) {
    float3 q = abs(p) - b;
    return length(max(q, 0.0)) + min(max(q.x, max(q.y, q.z)), 0.0);
}

float scrollAt() { return uTime * 1.55; }

// height of the wall that stands on a cell edge: rises in a wave out from the marble during the intro
float wallH(float2 cell, float k) {
    float rel = cell.y - floor(scrollAt() / S);
    float g = smoothstep(0.0, 1.0, uTime * 1.25 - 0.22 * abs(rel) - 0.15);
    return (0.5 + 0.7 * hash21(cell + k)) * g;
}

// distance to the walls near p (p.z is scrolled so the maze streams toward the camera)
float walls(float3 p, out float top) {
    float sc = scrollAt();
    float2 q = float2(p.x, p.z + sc) / S;
    float2 c = floor(q);
    float d = 1e3; top = 0.0;
    for (int n = 0; n < 4; n++) {
        float fn = float(n);
        float2 cell = c + float2(mod(fn, 2.0), floor(fn / 2.0));
        // wall along z on the left edge of the cell
        if (hash21(cell) > 0.52 && abs(cell.x * S) > 0.95) {
            float h = wallH(cell, 1.7);
            float db = sdBox(p - float3(cell.x * S, h * 0.5, (cell.y + 0.5) * S - sc), float3(TH, h * 0.5, S * 0.5)) - 0.012;
            if (db < d) { d = db; top = h; }
        }
        // wall along x on the near edge of the cell
        if (hash21(cell + 7.3) > 0.52 && abs((cell.x + 0.5) * S) > 0.95) {
            float h = wallH(cell, 3.1);
            float db = sdBox(p - float3((cell.x + 0.5) * S, h * 0.5, cell.y * S - sc), float3(S * 0.5, h * 0.5, TH)) - 0.012;
            if (db < d) { d = db; top = h; }
        }
    }
    return d;
}

float map(float3 p, out float top) {
    float dw = walls(p, top);
    return min(p.y, dw);
}

float3 mapNormal(float3 p) {
    float t0;
    float2 e = float2(0.004, 0.0);
    return normalize(float3(map(p + e.xyy, t0) - map(p - e.xyy, t0), map(p + e.yxy, t0) - map(p - e.yxy, t0), map(p + e.yyx, t0) - map(p - e.yyx, t0)));
}

float3 studio(float3 r) {
    float up = r.y * 0.5 + 0.5;
    float3 c = mix(float3(0.02, 0.03, 0.06), float3(0.34, 0.44, 0.66), smoothstep(0.1, 1.0, up));
    c += float3(5.0, 4.4, 3.6) * smoothstep(0.86, 0.97, dot(r, normalize(float3(-0.5, 0.8, -0.4))));
    c += float3(1.2, 2.0, 3.6) * smoothstep(0.78, 0.92, dot(r, normalize(float3(0.8, 0.25, 0.6))));
    c += float3(2.4, 1.6, 0.8) * 0.7 * smoothstep(0.8, 0.95, dot(r, normalize(float3(0.1, -0.2, 1.0))));
    return c;
}

float3 sky(float3 rd) {
    float h = clamp(rd.y * 0.5 + 0.5, 0.0, 1.0);
    return mix(float3(0.006, 0.01, 0.022), float3(0.03, 0.06, 0.13), pow(h, 2.0)) + float3(0.04, 0.07, 0.14) * exp(-abs(rd.y) * 9.0) * 0.5;
}

// the golden thread on the floor, running straight toward the horizon with light pulses travelling along it
float3 thread(float2 xz) {
    float w = 0.035;
    float line = smoothstep(w * 2.2, 0.0, abs(xz.x));
    float core = smoothstep(w, 0.0, abs(xz.x));
    float pulse = 0.55 + 0.45 * sin(xz.y * 2.4 + uTime * 5.5 * 0.0 - scrollAt() * 2.4);
    return float3(1.0, 0.72, 0.28) * (core * 2.4 + line * 0.7) * (0.65 + 0.7 * pulse);
}

float3 shade(float3 ro, float3 rd, int steps) {
    float t = 0.0; float top = 0.0; float d = 0.0;
    bool hit = false;
    for (int i = 0; i < 64; i++) {
        if (i >= steps) break;
        float3 p = ro + rd * t;
        d = map(p, top);
        if (d < 0.003) { hit = true; break; }
        t += d * 0.92;
        if (t > 26.0) break;
    }
    float3 col = sky(rd);
    if (hit) {
        float3 p = ro + rd * t;
        float3 n = mapNormal(p);
        float3 L = normalize(float3(-0.5, 0.8, -0.4));
        float3 key = float3(1.0, 0.9, 0.76);
        if (p.y < 0.012) {
            // floor: dark polished slate, faint tile seams, thread, light pool around the marble
            float2 g = float2(p.x, p.z + scrollAt()) / S;
            float2 f = abs(fract(g) - 0.5);
            float seam = smoothstep(0.482, 0.5, max(f.x, f.y));
            float3 base = float3(0.035, 0.05, 0.085) * (0.7 + 0.6 * hash21(floor(g)));
            float nl = max(dot(n, L), 0.0);
            col = base * (0.35 + 1.1 * nl) + float3(0.05, 0.12, 0.22) * seam * 0.6;
            col += thread(p.xz);
            float pool = exp(-dot(p.xz - float2(0.0, 0.0), p.xz) * 0.55);
            col += float3(0.18, 0.32, 0.55) * pool * 0.5;
            // shadow of the marble
            float sh = 1.0 - 0.8 * exp(-dot(p.xz - float2(0.35, 0.2), p.xz - float2(0.35, 0.2)) * 2.2);
            col *= mix(1.0, sh, 0.7);
        } else {
            // wall: deep teal enamel with a hot glowing rim along the top edge
            float3 base = float3(0.03, 0.14, 0.17);
            float nl = max(dot(n, L), 0.0);
            float edge = smoothstep(0.045, 0.0, top - p.y);
            float fres = pow(1.0 - max(dot(n, -rd), 0.0), 3.0);
            col = base * (0.3 + 1.2 * nl) * key + float3(0.05, 0.14, 0.26) * fres * 1.4;
            col += mix(float3(0.2, 0.8, 1.0), float3(1.0, 0.75, 0.3), smoothstep(8.0, 22.0, t)) * edge * 2.1 * (0.6 + 0.4 * uQ);
        }
        // distance haze into the night
        col = mix(col, sky(rd), 1.0 - exp(-t * 0.06));
    }
    return col;
}

float3 aces(float3 x) { return clamp((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0); }

half4 main(float2 fc) {
    float2 uv = (fc - 0.5 * uRes) / uRes.y;
    uv.y = -uv.y;
    float t = uTime;

    // camera: low and close, easing back as the marble settles, with a slow drift
    float ease = 1.0 - exp(-t * 1.1);
    float3 ro = float3(0.55 * sin(t * 0.35) , 1.9 + 0.9 * ease, -5.6 - 1.2 * ease);
    float3 target = float3(0.0, 0.35, 1.3);
    float3 fw = normalize(target - ro);
    float3 rt = normalize(cross(float3(0.0, 1.0, 0.0), fw));
    float3 up = cross(fw, rt);
    float3 rd = normalize(fw * 1.25 + rt * uv.x + up * uv.y);

    // marble: drops in and bounces, then rolls in place while the maze streams past
    float drop = max(0.0, 1.0 - t / 0.85);
    float bounce = abs(sin(t * 7.0)) * 0.55 * drop * drop;
    float R = 0.62;
    float3 mc = float3(0.0, R + 2.8 * drop * drop * drop + bounce * 0.5, 0.0);

    float3 col = shade(ro, rd, 56);

    // ray vs marble
    float3 oc = ro - mc;
    float b = dot(oc, rd);
    float c = dot(oc, oc) - R * R;
    float disc = b * b - c;
    if (disc > 0.0) {
        float th = -b - sqrt(disc);
        if (th > 0.0) {
            float3 p = ro + rd * th;
            float3 n = normalize(p - mc);
            float NoV = max(dot(n, -rd), 0.0);
            float F = 0.04 + 0.96 * pow(1.0 - NoV, 5.0);
            float3 refl = studio(reflect(rd, n));
            // refraction: bend the ray in, flip it through the ball, and look at the maze behind
            float3 rin = refract(rd, n, 1.0 / 1.5);
            float3 inner = float3(0.0);
            float3 exitDir = rin;
            if (uQ > 0.5) {
                float3 pi = p + rin * 0.0;
                float tb = -dot(p - mc, rin) + sqrt(max(dot(p - mc, rin) * dot(p - mc, rin) - (dot(p - mc, p - mc) - R * R), 0.0));
                float3 pe = p + rin * tb;
                float3 ne = normalize(pe - mc);
                float3 rout = refract(rin, -ne, 1.5);
                if (dot(rout, rout) < 0.01) rout = reflect(rin, -ne);
                inner = shade(pe + rout * 0.02, normalize(rout), 40);
            } else {
                inner = shade(p, rin, 24);
            }
            // rolling ribbons inside the glass, in body space so they turn with the ball
            float ang = -scrollAt() / R;
            float cs = cos(ang); float sn = sin(ang);
            float3 bp = float3(n.x, n.y * cs - n.z * sn, n.y * sn + n.z * cs);
            float band = sin(bp.x * 6.0 + sin(bp.y * 4.0 + bp.z * 3.0) * 1.6);
            float ribbon = smoothstep(0.7, 0.85, band) * (1.0 - smoothstep(0.9, 0.97, band));
            float3 ribCol = mix(float3(0.2, 0.75, 1.0), float3(1.0, 0.72, 0.28), 0.5 + 0.5 * sin(bp.z * 2.0 + 1.0));
            float3 glass = inner * float3(0.86, 0.95, 1.0) * mix(1.0, 0.45, pow(1.0 - NoV, 1.6));
            glass = mix(glass, ribCol * 1.2 + glass * 0.3, ribbon * 0.55);
            float3 mcol = glass * (1.0 - F) + refl * (0.15 + 0.85 * F);
            mcol += float3(8.0) * pow(max(dot(reflect(rd, n), normalize(float3(-0.5, 0.8, -0.4))), 0.0), 220.0);
            // caustic kiss of light on the floor under the glass
            col = mcol;
        }
    } else {
        // soft glow halo around the marble and caustic pool on the floor beneath it
        float2 pr = (mc.xy - ro.xy);
        float3 toC = normalize(mc - ro);
        float cosA = dot(rd, toC);
        float halo = pow(max(cosA, 0.0), 220.0);
        col += float3(0.25, 0.5, 0.95) * halo * 0.9;
    }

    // dust motes: soft bokeh discs drifting upward
    for (int i = 0; i < 14; i++) {
        float fi = float(i);
        float2 q = float2(hash21(float2(fi, 1.0)) - 0.5, fract(hash21(float2(fi, 2.0)) + t * (0.02 + 0.03 * hash21(float2(fi, 3.0)))) - 0.5) * float2(uRes.x / uRes.y, 1.0) * 1.3;
        float r = 0.012 + 0.03 * hash21(float2(fi, 4.0));
        float dd = length(uv - q);
        col += float3(0.4, 0.6, 1.0) * smoothstep(r, r * 0.55, dd) * 0.05 * (0.4 + 0.6 * hash21(float2(fi, 5.0)));
    }

    // grade: tone-map, cool shadows, vignette, grain, intro fade from black
    col = aces(col * 1.0);
    col = pow(col, float3(0.92));
    float vig = smoothstep(1.15, 0.25, length(uv * float2(0.9, 0.62)));
    col *= mix(0.45, 1.0, vig);
    col += (hash21(fc + t) - 0.5) / 120.0;
    col *= smoothstep(0.0, 0.7, t);
    return half4(float4(col, 1.0));
}
"""
}
