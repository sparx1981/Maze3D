#!/usr/bin/env python3
"""Synthesises every Maze3D sound effect and the ambient music loop. All sounds are original,
generated from maths only (no samples), so there are no licensing constraints.

usage: python3 generate.py <res/raw dir> [--report report.png]
"""
import sys, os, subprocess, wave
import numpy as np
from scipy import signal

SR = 44100
rng = np.random.default_rng(20261115)


# ---------------------------------------------------------------- helpers
def T(d): return np.arange(int(d * SR)) / SR
def noise(d): return rng.standard_normal(int(d * SR))

def _sos(kind, f, order=4):
    return signal.butter(order, f, btype=kind, fs=SR, output='sos')
def lp(x, f, order=4): return signal.sosfilt(_sos('low', min(f, SR * 0.45), order), x)
def hp(x, f, order=4): return signal.sosfilt(_sos('high', f, order), x)
def bp(x, lo, hi, order=2): return signal.sosfilt(_sos('band', [lo, min(hi, SR * 0.45)], order), x)

def sweep_lp(x, f0, f1, blocks=64):
    """Low-pass with a cutoff that glides exponentially from f0 to f1 (block-wise, state carried)."""
    n = len(x); out = np.zeros(n); edges = np.linspace(0, n, blocks + 1).astype(int)
    zi = None
    for i in range(blocks):
        f = f0 * (f1 / f0) ** ((i + 0.5) / blocks)
        sos = _sos('low', min(f, SR * 0.45), 2)
        if zi is None: zi = np.zeros((sos.shape[0], 2))
        seg, zi = signal.sosfilt(sos, x[edges[i]:edges[i + 1]], zi=zi)
        out[edges[i]:edges[i + 1]] = seg
    return out

def sweep_bp(x, f0, f1, q=1.2, blocks=64):
    n = len(x); out = np.zeros(n); edges = np.linspace(0, n, blocks + 1).astype(int)
    zi = None
    for i in range(blocks):
        f = f0 * (f1 / f0) ** ((i + 0.5) / blocks)
        sos = signal.butter(2, [f / (1 + 0.5 / q), min(f * (1 + 0.5 / q), SR * 0.45)], btype='band', fs=SR, output='sos')
        if zi is None: zi = np.zeros((sos.shape[0], 2))
        seg, zi = signal.sosfilt(sos, x[edges[i]:edges[i + 1]], zi=zi)
        out[edges[i]:edges[i + 1]] = seg
    return out

def edecay(n, tau): return np.exp(-np.arange(n) / SR / tau)
def adsr_swell(n, attack, tau):
    t = np.arange(n) / SR
    return (1 - np.exp(-t / max(attack, 1e-4))) * np.exp(-t / tau)

def glide(f0, f1, d, curve='exp'):
    t = T(d)
    if curve == 'exp': f = f0 * (f1 / f0) ** (t / d)
    else: f = f0 + (f1 - f0) * t / d
    return np.sin(2 * np.pi * np.cumsum(f) / SR)

def modal(freqs, amps, taus, d, phases=None):
    t = T(d); y = np.zeros_like(t)
    for i, (f, a, tau) in enumerate(zip(freqs, amps, taus)):
        ph = 0 if phases is None else phases[i]
        y += a * np.sin(2 * np.pi * f * t + ph) * np.exp(-t / tau)
    return y

def place(dst, src, at, gain=1.0):
    i = int(at * SR); j = min(len(dst), i + len(src))
    if i < len(dst): dst[i:j] += src[:j - i] * gain
    return dst

def fade(x, a_ms=2, b_ms=10):
    x = x.copy(); a = int(a_ms * SR / 1000); b = int(b_ms * SR / 1000)
    if a: x[:a] *= np.linspace(0, 1, a)
    if b: x[-b:] *= np.linspace(1, 0, b)
    return x

def make_ir(rt, damp=6000, d=None, pre=0.004, seed=0, stereo=False):
    r = np.random.default_rng(seed)
    d = d or rt * 1.3
    n = int(d * SR)
    t = np.arange(n) / SR
    chans = []
    for _ in range(2 if stereo else 1):
        ir = r.standard_normal(n) * np.exp(-6.91 * t / rt)
        ir = lp(ir, damp, 2)
        ir[:int(pre * SR)] = 0
        # frequency dependent decay: darker tail
        tail = lp(ir, 1800, 2)
        mix = np.clip(t / rt, 0, 1)
        ir = ir * (1 - 0.7 * mix) + tail * 0.7 * mix
        chans.append(ir / np.sqrt(np.sum(ir ** 2)))
    return chans if stereo else chans[0]

def reverb(x, rt=0.5, wet=0.2, damp=6000, seed=1):
    ir = make_ir(rt, damp, seed=seed)
    w = signal.fftconvolve(x, ir)[:len(x) + int(rt * SR)]
    out = np.zeros(len(w)); out[:len(x)] += x * (1 - wet * 0.5)
    out += w * wet * 3.0
    return out

def trim(x, thresh=1e-3):
    idx = np.where(np.abs(x) > thresh * max(np.max(np.abs(x)), 1e-9))[0]
    return x[:idx[-1] + 1] if len(idx) else x

def soft(x, drive=1.0): return np.tanh(x * drive) / np.tanh(drive)

def peak_norm(x, peak=0.89):
    m = np.max(np.abs(x)); return x * (peak / m) if m > 0 else x

def loopify(x, xf):
    """Make x seamlessly loopable by cross-fading its tail into its head. Returns len(x)-xf samples."""
    n = len(x) - xf
    w = np.linspace(0, 1, xf)
    out = x[:n].copy()
    out[:xf] = x[:xf] * w + x[n:] * (1 - w)
    return out


# ---------------------------------------------------------------- effects
OUT = {}  # name -> (samples float mono, loop?)

def add(name, x, peak=0.89, loop=False):
    OUT[name] = (peak_norm(x, peak), loop)


def impact(tier, i):
    r = np.random.default_rng(100 + tier * 10 + i)
    d = [0.5, 1.0, 2.0][tier]
    f0 = r.uniform(950, 1750) * [1.15, 1.0, 0.85][tier]
    ratios = np.array([1, 2.32, 3.97, 5.82, 8.11, 11.2])
    amps = np.array([1, 0.62, 0.42, 0.26, 0.16, 0.08]) * [0.7, 1, 1][tier]
    taus = np.array([1, 0.62, 0.45, 0.33, 0.22, 0.14]) * [0.05, 0.14, 0.34][tier]
    x = modal(f0 * ratios, amps, taus, d, phases=r.uniform(0, 6.28, 6))
    # glassy marble "tick" transient
    n = int(0.006 * SR)
    click = hp(noise(0.02), 2500)[:int(0.02 * SR)] * edecay(int(0.02 * SR), 0.0016)
    x[:len(click)] += click * [0.9, 1.1, 1.4][tier]
    # body thud of the wall
    f_body = r.uniform(140, 260) * [1.2, 1, 0.8][tier]
    body = np.sin(2 * np.pi * f_body * T(d)) * edecay(int(d * SR), [0.012, 0.03, 0.07][tier])
    x += body * [0.35, 0.6, 0.9][tier]
    if tier == 2:
        sub = glide(110, 55, d) * edecay(int(d * SR), 0.09)
        x += sub * 0.7
    x = reverb(x, rt=[0.18, 0.28, 0.45][tier], wet=[0.1, 0.14, 0.2][tier], seed=tier * 10 + i)
    add(f"sfx_impact_{'smh'[tier]}{i}", fade(trim(x), 0.5, 12), peak=[0.45, 0.65, 0.88][tier])

for tier in range(3):
    for i in range(3): impact(tier, i)

def zing(i):
    r = np.random.default_rng(300 + i)
    d = 0.32; y = np.zeros(int(d * SR))
    for k in range(r.integers(7, 12)):
        at = r.uniform(0, 0.2); dur = r.uniform(0.004, 0.02)
        n = int(dur * SR)
        s = hp(noise(dur), r.uniform(3500, 6500))[:n] * edecay(n, dur / 3)
        place(y, s, at, r.uniform(0.3, 1))
    f = r.uniform(4200, 7000)
    y += np.sin(2 * np.pi * f * T(d) * (1 + 0.04 * np.sin(2 * np.pi * 30 * T(d)))) * edecay(len(y), 0.05) * 0.18
    add(f"sfx_spark_{i}", fade(hp(y, 2500, 2), 0.5, 20), peak=0.5)
for i in range(2): zing(i)

# --- rolling loops (pitch/volume are driven live by marble speed)
def roll_hard():
    d = 2.4; n = int(d * SR) + int(0.2 * SR)
    base = bp(noise(n / SR), 350, 4200, 2)
    res = sum(bp(noise(n / SR), f * 0.93, f * 1.07, 2) * a for f, a in [(880, 1.4), (1760, 1.0), (2650, 0.7), (3900, 0.4)])
    t = np.arange(n) / SR
    am = 0.72 + 0.28 * np.sin(2 * np.pi * 6.5 * t + 1.5 * lp(noise(n / SR), 3, 2) * 6)
    ticks = np.zeros(n)
    for k in np.arange(0.05, d + 0.2, 1 / 6.5):
        place(ticks, hp(noise(0.004), 2000)[:int(0.004 * SR)] * edecay(int(0.004 * SR), 0.001), k, 0.5)
    x = (base * 0.9 + res * 0.5) * am + ticks
    x += lp(noise(n / SR), 160, 2) * 0.5
    x = loopify(x, int(0.2 * SR))
    add("sfx_roll_hard", x, peak=0.6, loop=True)
def roll_wood():
    d = 2.4; n = int(d * SR) + int(0.2 * SR)
    t = np.arange(n) / SR
    base = bp(noise(n / SR), 90, 1300, 2)
    grain = 0.65 + 0.35 * np.clip(lp(noise(n / SR), 40, 2) * 8, -1, 1)
    am = (0.7 + 0.3 * np.sin(2 * np.pi * 5.2 * t + lp(noise(n / SR), 3, 2) * 5)) * grain
    x = base * am + lp(noise(n / SR), 110, 2) * 0.9
    x += bp(noise(n / SR), 1500, 3500, 2) * 0.12 * am
    x = loopify(x, int(0.2 * SR))
    add("sfx_roll_wood", x, peak=0.6, loop=True)
roll_hard(); roll_wood()

def wind():
    d = 8.0; xf = int(1.0 * SR); n = int(d * SR) + xf
    t = np.arange(n) / SR
    x = noise(n / SR)
    a = sweep_bp(x, 260, 760, q=0.8)
    b = sweep_bp(noise(n / SR), 700, 320, q=0.9)
    lfo = 0.5 + 0.5 * np.sin(2 * np.pi * 0.13 * t)
    lfo2 = 0.5 + 0.5 * np.sin(2 * np.pi * 0.09 * t + 1.7)
    y = a * lfo + b * lfo2 + 0.3 * lp(noise(n / SR), 120, 2)
    y *= 0.55 + 0.45 * np.sin(2 * np.pi * 0.07 * t + 0.4)
    add("sfx_wind", loopify(y, xf), peak=0.5, loop=True)
wind()

# --- doors
def door_open():
    d = 0.7; n = int(d * SR); t = T(d)
    f = 150 * (380 / 150) ** (t / d)
    ph = np.cumsum(f) / SR * 2 * np.pi
    servo = (np.sin(ph) + 0.45 * np.sin(2 * ph) + 0.25 * np.sin(3 * ph)) * (1 + 0.06 * np.sin(2 * np.pi * 38 * t))
    env = np.minimum(t / 0.05, 1) * np.exp(-np.maximum(t - 0.45, 0) / 0.05)
    slide = sweep_bp(noise(d), 500, 2600, q=1.4) * 0.9
    x = (servo * 0.55 + slide) * env
    latch = modal([2100, 3300, 4700], [1, 0.5, 0.3], [0.03, 0.02, 0.015], 0.15) + hp(noise(0.15), 2500)[:int(0.15 * SR)] * edecay(int(0.15 * SR), 0.003)
    place(x, latch, 0.56, 0.5)
    x = reverb(x, 0.3, 0.12)
    add("sfx_door_open", fade(trim(x), 4, 20), peak=0.7)
def door_close():
    d = 0.9; n = int(d * SR); t = T(d)
    f = 360 * (140 / 360) ** (np.minimum(t, 0.32) / 0.32)
    servo = np.sin(np.cumsum(f) / SR * 2 * np.pi) + 0.4 * np.sin(2 * np.cumsum(f) / SR * 2 * np.pi)
    env = np.minimum(t / 0.04, 1) * (t < 0.32)
    slide = sweep_bp(noise(d), 2400, 500, q=1.4) * (t < 0.32) * np.minimum(t / 0.04, 1)
    x = servo * env * 0.45 + slide * 0.8
    k = int(0.32 * SR)
    thud = glide(85, 42, 0.5) * edecay(int(0.5 * SR), 0.11)
    clank = modal(np.array([1150, 2670, 4630, 6900]), [1, 0.7, 0.4, 0.2], [0.18, 0.1, 0.07, 0.05], 0.6)
    burst = lp(noise(0.1), 3200, 2) * edecay(int(0.1 * SR), 0.018)
    place(x, thud, 0.32, 1.5); place(x, clank, 0.32, 0.55); place(x, burst, 0.32, 0.9)
    x = reverb(x, 0.4, 0.15)
    add("sfx_door_close", fade(trim(x), 4, 25), peak=0.9)
door_open(); door_close()

# --- holes
def hole_open():
    d = 0.8; n = int(d * SR); t = T(d)
    air = sweep_lp(noise(d), 1600, 260) * adsr_swell(n, 0.12, 0.3)
    tone = glide(240, 95, d) * adsr_swell(n, 0.1, 0.28)
    x = air * 0.9 + tone * 0.55
    x = reverb(x, 0.35, 0.15)
    add("sfx_hole_open", fade(trim(x), 8, 30), peak=0.55)
def hole_close():
    d = 0.5; n = int(d * SR); t = T(d)
    air = sweep_lp(noise(d), 250, 1500) * np.minimum(t / 0.25, 1) ** 2 * (t < 0.28)
    thump = glide(120, 55, 0.3) * edecay(int(0.3 * SR), 0.07)
    x = air * 0.7; place(x, thump, 0.27, 0.9)
    x = reverb(x, 0.3, 0.12)
    add("sfx_hole_close", fade(trim(x), 6, 25), peak=0.55)
def hole_fall():
    d = 1.4; n = int(d * SR); t = T(d)
    f = 1500 * (170 / 1500) ** (np.minimum(t, 0.6) / 0.6)
    vib = 1 + 0.015 * np.sin(2 * np.pi * 7 * t)
    ph = np.cumsum(f * vib) / SR * 2 * np.pi
    whistle = (np.sin(ph) + 0.25 * np.sin(2 * ph)) * np.exp(-np.maximum(t - 0.1, 0) / 0.32) * np.minimum(t / 0.01, 1)
    whistle *= (t < 0.62) * np.minimum((0.62 - t) / 0.08, 1)
    air = hp(noise(d), 700) * (t < 0.6) * 0.12 * np.exp(-t / 0.4)
    x = whistle * 0.7 + air
    thud = glide(90, 38, 0.8) * edecay(int(0.8 * SR), 0.18)
    dust = lp(noise(0.5), 700, 2) * edecay(int(0.5 * SR), 0.14)
    place(x, thud, 0.6, 1.4); place(x, dust, 0.6, 0.7)
    x = reverb(x, 0.8, 0.28, damp=3500)
    add("sfx_hole_fall", fade(trim(x), 2, 40), peak=0.85)
def respawn():
    d = 1.0
    x = np.zeros(int(d * SR))
    pop = glide(520, 1250, 0.05) * edecay(int(0.05 * SR), 0.02)
    place(x, pop, 0.0, 0.8)
    for i, f in enumerate([1318.5, 1568, 1975.5, 2637]):
        n = int(0.7 * SR)
        bell = (np.sin(2 * np.pi * f * T(0.7) + 2.0 * np.sin(2 * np.pi * f * 2.0 * T(0.7)) * edecay(n, 0.05)) * edecay(n, 0.22))
        place(x, bell, 0.06 + i * 0.07, 0.4 - 0.05 * i)
    x = reverb(x, 0.6, 0.25)
    add("sfx_respawn", fade(trim(x), 1, 40), peak=0.65)
hole_open(); hole_close(); hole_fall(); respawn()

# --- level events
def goal_chime():
    d = 3.6; x = np.zeros(int(d * SR))
    notes = [523.25, 659.25, 783.99, 987.77, 1174.66, 1567.98]  # C5 E5 G5 B5 D6 G6
    for i, f in enumerate(notes):
        n = int(2.2 * SR); t = T(2.2)
        idx = 2.4 * np.exp(-t / 0.35)
        bell = np.sin(2 * np.pi * f * t + idx * np.sin(2 * np.pi * f * 3.5 * t)) * np.exp(-t / (1.1 - 0.08 * i))
        bell += 0.35 * np.sin(2 * np.pi * f * 2.0 * t) * np.exp(-t / 0.4)
        place(x, bell, i * 0.11, 0.5)
    shim = hp(noise(1.6), 6500) * adsr_swell(int(1.6 * SR), 0.15, 0.5)
    place(x, shim, 0.1, 0.06)
    x = reverb(x, 1.6, 0.32, damp=7000)
    add("sfx_goal", fade(trim(x, 2e-3), 2, 200), peak=0.85)
def level_grow():
    d = 1.5; n = int(d * SR); t = T(d)
    rum = sweep_lp(noise(d), 120, 2400) * (t / d) ** 1.4 * 0.9
    sub = glide(45, 90, d) * np.sin(np.pi * np.minimum(t / d, 1)) ** 0.6 * 0.5
    x = rum + sub
    k = 0.05; i = 0
    while k < d - 0.05:  # accelerating wall "clicks" as they lock into place
        f = 700 + 90 * (i % 5)
        tick = modal([f, f * 2.4], [1, 0.5], [0.02, 0.012], 0.06) + hp(noise(0.01), 3000)[:int(0.01 * SR)].mean()
        place(x, tick, k, 0.25 + 0.2 * (k / d))
        k += max(0.16 * (1 - 0.8 * k / d), 0.03); i += 1
    x *= np.minimum(t / 0.08, 1)
    x = reverb(x, 0.5, 0.16)
    add("sfx_level_grow", fade(trim(x), 2, 80), peak=0.7)
def level_sink():
    d = 1.0; n = int(d * SR); t = T(d)
    rum = sweep_lp(noise(d), 2200, 110) * np.exp(-t / 0.45) * 0.9
    sub = glide(95, 38, d) * edecay(n, 0.4) * 0.5
    x = (rum + sub) * np.minimum(t / 0.03, 1)
    x = reverb(x, 0.5, 0.15)
    add("sfx_level_sink", fade(trim(x), 2, 80), peak=0.65)
goal_chime(); level_grow(); level_sink()

# --- UI
def ui_click():
    d = 0.06; t = T(d)
    x = np.sin(2 * np.pi * 1900 * t) * edecay(len(t), 0.008) + hp(noise(d), 3000) * edecay(len(t), 0.002) * 0.6
    add("sfx_ui_click", fade(x, 0.2, 5), peak=0.5)
def ui_toggle(up):
    d = 0.16; x = np.zeros(int(d * SR))
    f1, f2 = (880, 1320) if up else (1320, 880)
    for f, at in [(f1, 0.0), (f2, 0.06)]:
        n = int(0.09 * SR); tt = T(0.09)
        place(x, (np.sin(2 * np.pi * f * tt) + 0.3 * np.sin(2 * np.pi * 2 * f * tt)) * edecay(n, 0.03), at, 0.6)
    add("sfx_ui_on" if up else "sfx_ui_off", fade(x, 0.3, 10), peak=0.5)
ui_click(); ui_toggle(True); ui_toggle(False)


# ---------------------------------------------------------------- music
def midi(m): return 440.0 * 2 ** ((m - 69) / 12)

def pad_voice(f, d, detune_cents, seed):
    r = np.random.default_rng(seed)
    t = T(d); y = np.zeros_like(t)
    for c in (-detune_cents, 0, detune_cents):
        ff = f * 2 ** (c / 1200)
        ph = r.uniform(0, 1)
        y += signal.sawtooth(2 * np.pi * (ff * t + ph) + 0.8 * np.sin(2 * np.pi * 0.17 * t + r.uniform(0, 6)), 0.5)
    y += 0.8 * np.sin(2 * np.pi * f * 0.5 * t) + 0.3 * np.sin(2 * np.pi * f * 2 * t)
    return y

def music(duration=64.0):
    tail = 6.0
    n = int((duration + tail) * SR)
    L = np.zeros(n); R = np.zeros(n)
    chords = [  # Am9, Fmaj9, Cmaj7add9, Gsus2add6 -- slow, warm, unresolved
        [45, 52, 59, 60, 64, 67],
        [41, 48, 57, 60, 64, 67],
        [48, 55, 59, 62, 64, 67],
        [43, 50, 57, 62, 66, 69],
    ]
    seg = duration / len(chords)
    for ci, ch in enumerate(chords):
        start = ci * seg
        for vi, m in enumerate(ch):
            d = seg + 7.0
            y = pad_voice(midi(m), d, 6 + vi, seed=ci * 10 + vi)
            # darkness depends on register; slow filter bloom
            t = T(d)
            bright = lp(y, 1100 if m < 55 else 2400, 2)
            dark = lp(y, 500 if m < 55 else 900, 2)
            mixlfo = 0.5 + 0.5 * np.sin(2 * np.pi * (0.04 + 0.01 * vi) * t + vi)
            y = dark * (1 - mixlfo) + bright * mixlfo
            env = np.minimum(t / 5.0, 1) ** 1.5 * np.minimum((d - t) / 6.0, 1) ** 1.5
            y *= env * (0.6 if m < 50 else 0.32)
            pan = np.interp(vi, [0, len(ch) - 1], [-0.55, 0.55])
            gl, gr = np.cos((pan + 1) * np.pi / 4), np.sin((pan + 1) * np.pi / 4)
            i = int(start * SR); j = min(n, i + len(y))
            L[i:j] += y[:j - i] * gl * 1.2; R[i:j] += y[:j - i] * gr * 1.2
        # soft sub
        d = seg + 4.0; t = T(d)
        sub = np.sin(2 * np.pi * midi(ch[0] - 12) * t) * np.minimum(t / 3, 1) * np.minimum((d - t) / 3, 1) * 0.35
        i = int(start * SR); j = min(n, i + len(sub))
        L[i:j] += sub[:j - i]; R[i:j] += sub[:j - i]
    # sparse bell plucks (pentatonic of the current chord), with ping-pong echo
    r = np.random.default_rng(77)
    scale = [69, 72, 74, 76, 79, 81, 84]
    t_ = 2.0
    while t_ < duration - 2:
        f = midi(r.choice(scale))
        nn = int(2.2 * SR); tt = T(2.2)
        idx = 1.1 * np.exp(-tt / 0.2)
        bell = np.sin(2 * np.pi * f * tt + idx * np.sin(2 * np.pi * f * 2.0 * tt)) * np.exp(-tt / 0.7)
        bell *= 0.07
        pan = r.uniform(-0.6, 0.6)
        for k, (dl, g) in enumerate([(0, 1.0), (0.42, 0.5), (0.84, 0.25), (1.26, 0.12)]):
            p = pan * (-1) ** k
            gl, gr = np.cos((p + 1) * np.pi / 4), np.sin((p + 1) * np.pi / 4)
            i = int((t_ + dl) * SR); j = min(n, i + len(bell))
            L[i:j] += bell[:j - i] * g * gl; R[i:j] += bell[:j - i] * g * gr
        t_ += r.uniform(4.5, 9.0)
    # shared lush reverb
    irL, irR = make_ir(3.6, 5200, d=4.5, pre=0.02, seed=5, stereo=True)
    wl = signal.fftconvolve(L, irL)[:n]; wr = signal.fftconvolve(R, irR)[:n]
    L = L * 0.55 + wl * 2.6; R = R * 0.55 + wr * 2.6
    # gentle air and warmth shaping
    L = lp(hp(L, 35, 2), 9000, 2); R = lp(hp(R, 35, 2), 9000, 2)
    # wrap the tail onto the start so the loop is seamless
    m = int(duration * SR)
    Lo = L[:m].copy(); Ro = R[:m].copy()
    tl = L[m:m + int(tail * SR)]; tr = R[m:m + int(tail * SR)]
    Lo[:len(tl)] += tl; Ro[:len(tr)] += tr
    st = np.stack([Lo, Ro], axis=1)
    st = soft(st / (np.max(np.abs(st)) + 1e-9) * 1.2, 1.0)
    rms = np.sqrt(np.mean(st ** 2))
    st *= 10 ** (-21 / 20) / rms        # about -21 dBFS RMS: sits well under the effects
    st = np.clip(st, -0.89, 0.89)
    return st


def write_wav(path, x, stereo=False):
    x = np.clip(x, -1, 1)
    pcm = (x * 32767).astype('<i2')
    with wave.open(path, 'wb') as w:
        w.setnchannels(2 if stereo else 1); w.setsampwidth(2); w.setframerate(SR)
        w.writeframes(pcm.tobytes())


if __name__ == '__main__':
    out = sys.argv[1]; os.makedirs(out, exist_ok=True)
    report = None
    if '--report' in sys.argv: report = sys.argv[sys.argv.index('--report') + 1]
    total = 0
    for name, (x, loop) in OUT.items():
        p = os.path.join(out, name + '.wav'); write_wav(p, x); total += os.path.getsize(p)
    print(f"{len(OUT)} effects, {total / 1e6:.2f} MB")
    st = music()
    tmp = os.path.join(out, '_music.wav'); write_wav(tmp, st, stereo=True)
    subprocess.run(['ffmpeg', '-y', '-loglevel', 'error', '-i', tmp, '-c:a', 'libvorbis', '-q:a', '4', os.path.join(out, 'music_ambient.ogg')], check=True)
    os.remove(tmp)
    print('music', os.path.getsize(os.path.join(out, 'music_ambient.ogg')) / 1e6, 'MB')
    if report:
        import matplotlib; matplotlib.use('Agg')
        import matplotlib.pyplot as plt
        names = sorted(OUT.keys())
        cols = 6; rows = (len(names) + 1 + cols - 1) // cols
        fig, axes = plt.subplots(rows, cols, figsize=(cols * 3.2, rows * 2.2))
        axes = axes.flatten()
        for ax, nme in zip(axes, names):
            x = OUT[nme][0]
            f, t, S = signal.spectrogram(x, SR, nperseg=1024, noverlap=768)
            ax.pcolormesh(t, f / 1000, 10 * np.log10(S + 1e-12), vmin=-110, vmax=-30, shading='auto', cmap='magma')
            ax.set_ylim(0, 12); ax.set_title(f"{nme.replace('sfx_', '')}  pk={np.max(np.abs(x)):.2f}", fontsize=7); ax.tick_params(labelsize=6)
        f, t, S = signal.spectrogram(st[:, 0], SR, nperseg=4096, noverlap=3072)
        axes[len(names)].pcolormesh(t, f / 1000, 10 * np.log10(S + 1e-12), vmin=-120, vmax=-40, shading='auto', cmap='magma')
        axes[len(names)].set_ylim(0, 8); axes[len(names)].set_title('music_ambient (L)', fontsize=7)
        for ax in axes[len(names) + 1:]: ax.axis('off')
        plt.tight_layout(); plt.savefig(report, dpi=80)
