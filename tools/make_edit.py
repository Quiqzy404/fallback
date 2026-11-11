"""ZERO Client – Base Finder Promo-Edit aus den aufgenommenen Frames bauen (eigene Titel, Effekte und Sounds)."""
import glob
import math
import os
import random
import subprocess
import sys
import wave

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

PROJ = sys.argv[1]
WORK = sys.argv[2]
FFMPEG = sys.argv[3]
SHOTS = os.path.join(PROJ, 'run', 'screenshots')
FONTS = os.path.join(PROJ, 'src', 'main', 'resources', 'assets', 'quiqzy', 'font')
OUT_FRAMES = os.path.join(WORK, 'edit_out')
os.makedirs(OUT_FRAMES, exist_ok=True)
for f in glob.glob(os.path.join(OUT_FRAMES, '*.png')):
    os.remove(f)

W, H, FPS = 1280, 720, 30
ORANGE = (255, 178, 110)
ORANGE2 = (236, 95, 28)
BOLD = os.path.join(FONTS, 'nunito_extrabold.ttf')
LOGO = os.path.join(FONTS, 'playfair_italic.ttf')
random.seed(7)


def shot(name):
    return sorted(glob.glob(os.path.join(SHOTS, f'edit_{name}_*.png')))


A, B, C = shot('a'), shot('b'), shot('c')
cache = {}


def src(path):
    if path not in cache:
        cache.clear() if len(cache) > 40 else None
        cache[path] = Image.open(path).convert('RGB').resize((W, H), Image.LANCZOS)
    return cache[path]


def grade(im):
    """Leicht warmer Look + Vignette."""
    a = np.asarray(im).astype(np.float32)
    a[..., 0] *= 1.06
    a[..., 2] *= 0.94
    yy, xx = np.mgrid[0:H, 0:W]
    d = np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2)
    a *= (1.0 - 0.38 * np.clip(d - 0.55, 0, 1))[..., None]
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


VIG = None


def zoom_blur(im, strength):
    """Radialer Zoom-Blur: mehrere vergrößerte Kopien übereinander."""
    if strength <= 0.01:
        return im
    acc = np.asarray(im).astype(np.float32)
    n = 7
    for i in range(1, n):
        s = 1.0 + strength * 0.22 * i / n
        w2, h2 = int(W * s), int(H * s)
        big = im.resize((w2, h2), Image.BILINEAR).crop(((w2 - W) // 2, (h2 - H) // 2, (w2 - W) // 2 + W, (h2 - H) // 2 + H))
        acc += np.asarray(big).astype(np.float32)
    return Image.fromarray((acc / n).astype(np.uint8))


def gradient_text(text, font, size, spacing=0):
    f = ImageFont.truetype(font, size)
    bbox = f.getbbox(text)
    tw = bbox[2] - bbox[0] + spacing * len(text) + 40
    th = bbox[3] - bbox[1] + 40
    mask = Image.new('L', (tw, th), 0)
    d = ImageDraw.Draw(mask)
    x = 20
    for ch in text:
        d.text((x, 20 - bbox[1]), ch, font=f, fill=255)
        x += f.getlength(ch) + spacing
    grad = Image.new('RGB', (tw, th))
    gd = ImageDraw.Draw(grad)
    for y in range(th):
        t = y / max(1, th - 1)
        c = tuple(int(ORANGE[i] * (1 - t) + ORANGE2[i] * t) for i in range(3))
        gd.line([(0, y), (tw, y)], fill=c)
    return grad, mask


def put_title(im, text, scale=1.0, alpha=1.0, y=None, size=78, font=BOLD, spacing=4, shake=0):
    if alpha <= 0.01:
        return im
    grad, mask = gradient_text(text, font, int(size * scale), spacing)
    tw, th = mask.size
    x0 = (W - tw) // 2 + random.randint(-shake, shake)
    y0 = (int(H * 0.42) if y is None else y) - th // 2 + random.randint(-shake, shake)
    base = im.convert('RGBA')
    # Glow
    glow_m = mask.filter(ImageFilter.GaussianBlur(18)).point(lambda v: int(min(255, v * 1.6) * alpha))
    glow = Image.new('RGBA', mask.size, ORANGE2 + (0,))
    glow.putalpha(glow_m)
    base.alpha_composite(glow, (x0, y0))
    glow_m2 = mask.filter(ImageFilter.GaussianBlur(5)).point(lambda v: int(v * 0.9 * alpha))
    glow2 = Image.new('RGBA', mask.size, ORANGE + (0,))
    glow2.putalpha(glow_m2)
    base.alpha_composite(glow2, (x0, y0))
    txt = grad.convert('RGBA')
    txt.putalpha(mask.point(lambda v: int(v * alpha)))
    base.alpha_composite(txt, (x0, y0))
    return base.convert('RGB')


def put_sub(im, text, y, alpha, size=22):
    if alpha <= 0.01:
        return im
    f = ImageFont.truetype(BOLD, size)
    base = im.convert('RGBA')
    layer = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(layer)
    tw = d.textlength(text, font=f)
    d.text(((W - tw) / 2, y), text, font=f, fill=(244, 238, 232, int(220 * alpha)))
    base.alpha_composite(layer)
    return base.convert('RGB')


frames = []


def emit(im):
    p = os.path.join(OUT_FRAMES, f'{len(frames):05d}.png')
    im.save(p)
    frames.append(p)


def ease_out(t):
    return 1 - (1 - t) ** 3


events = []  # (Zeitpunkt in s, Art) für den Sound

# ---------------------------------------------------------------- 1) Intro
n = int(1.6 * FPS)
for i in range(n):
    t = i / n
    im = Image.new('RGB', (W, H), (8, 6, 5))
    a = min(1, t / 0.25)
    sc = 1.25 - 0.25 * ease_out(min(1, t / 0.4))
    im = put_title(im, 'ZERO', scale=sc, alpha=a, size=150, font=LOGO, spacing=2)
    im = put_sub(im, 'C L I E N T', int(H * 0.42) + 80, min(1, max(0, (t - 0.3) / 0.3)), 24)
    if t > 0.8:
        im = zoom_blur(im, (t - 0.8) / 0.2 * 1.6)
    emit(im)
events.append((0.05, 'boom'))
events.append((n / FPS - 0.2, 'whoosh'))


def play(shot_frames, speed, title, sub=None, title_len=1.4, blur_in=0.3, blur_out=0.25):
    start = len(frames) / FPS
    dur = len(shot_frames) / 20.0 / speed
    count = int(dur * FPS)
    events.append((start, 'boom'))
    for i in range(count):
        t = i / FPS
        k = min(len(shot_frames) - 1, int(t * speed * 20))
        im = grade(src(shot_frames[k]))
        if t < blur_in:
            im = zoom_blur(im, (1 - t / blur_in) * 1.8)
        if t > dur - blur_out:
            im = zoom_blur(im, (t - (dur - blur_out)) / blur_out * 1.8)
        if t < title_len:
            pop = ease_out(min(1, t / 0.18))
            fade = 1 if t < title_len - 0.25 else (title_len - t) / 0.25
            im = put_title(im, title, scale=1.2 - 0.2 * pop, alpha=pop * fade, shake=3 if t < 0.15 else 0)
            if sub:
                im = put_sub(im, sub, int(H * 0.42) + 52, fade * min(1, t / 0.3))
        emit(im)
    events.append((len(frames) / FPS - 0.2, 'whoosh'))


# ---------------------------------------------------------------- 2) Shots
play(A, 1.35, 'BASE FINDER', 'scans every loaded chunk')
play(B, 1.25, 'STORAGE ESP', 'shulkers · chests · beacons')
play(C, 1.0, 'FOUND.', None, title_len=1.6)

# ---------------------------------------------------------------- 3) Outro
start = len(frames) / FPS
n = int(2.4 * FPS)
for i in range(n):
    t = i / n
    im = Image.new('RGB', (W, H), (8, 6, 5))
    a = min(1, t / 0.2) * (1 if t < 0.85 else (1 - t) / 0.15)
    im = put_title(im, 'ZERO', scale=1.0 + 0.04 * math.sin(t * 6), alpha=a, size=150, font=LOGO, spacing=2, y=int(H * 0.40))
    im = put_sub(im, 'C L I E N T', int(H * 0.40) + 80, a, 24)
    im = put_sub(im, 'base finder  ·  storage esp  ·  visuals', int(H * 0.40) + 118, a * 0.8, 18)
    if t < 0.15:
        im = zoom_blur(im, (1 - t / 0.15) * 1.6)
    emit(im)
events.append((start + 0.02, 'boom'))
total = len(frames) / FPS
print('frames', len(frames), 'dauer', round(total, 2), 's')

# ---------------------------------------------------------------- Sound (eigener Synth)
SR = 44100
L = int(total * SR) + SR
mix = np.zeros(L, dtype=np.float32)
rng = np.random.default_rng(3)


def add(at, sig):
    i = int(at * SR)
    j = min(L, i + len(sig))
    if i < L:
        mix[i:j] += sig[: j - i]


def kick():
    n = int(0.35 * SR)
    t = np.arange(n) / SR
    f = 120 * np.exp(-t * 18) + 42
    ph = 2 * np.pi * np.cumsum(f) / SR
    return (np.sin(ph) * np.exp(-t * 7)).astype(np.float32) * 0.9


def hat():
    n = int(0.05 * SR)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n).astype(np.float32)
    noise = np.diff(np.concatenate([[0], noise]))  # hell
    return noise * np.exp(-t * 90) * 0.12


def boom():
    n = int(1.4 * SR)
    t = np.arange(n) / SR
    f = 70 * np.exp(-t * 2.5) + 30
    ph = 2 * np.pi * np.cumsum(f) / SR
    body = np.sin(ph) * np.exp(-t * 2.2)
    noise = rng.standard_normal(n) * np.exp(-t * 14) * 0.25
    return (body + noise).astype(np.float32) * 0.9


def whoosh():
    n = int(0.45 * SR)
    t = np.arange(n) / SR
    noise = rng.standard_normal(n).astype(np.float32)
    env = np.sin(np.pi * np.clip(t / 0.45, 0, 1)) ** 2
    # einfacher wandernder Tiefpass
    out = np.zeros(n, dtype=np.float32)
    y = 0.0
    for i in range(n):
        a = 0.02 + 0.5 * (i / n)
        y += a * (noise[i] - y)
        out[i] = y
    return out * env * 0.7


bpm = 128
beat = 60 / bpm
k = kick()
h = hat()
tt = 0.0
while tt < total - 0.3:
    add(tt, k)
    add(tt + beat / 2, h)
    tt += beat
# Bass-Pad unter allem (eigene Akkordfolge)
t = np.arange(L) / SR
notes = [55.0, 55.0, 49.0, 41.2]
pad = np.zeros(L, dtype=np.float32)
for idx, f0 in enumerate(notes * 8):
    s0 = idx * beat * 4
    if s0 > total:
        break
    seg = (t >= s0) & (t < s0 + beat * 4)
    pad[seg] += (np.sin(2 * np.pi * f0 * t[seg]) * 0.18 + np.sin(2 * np.pi * f0 * 2 * t[seg]) * 0.06).astype(np.float32)
mix += pad * np.clip(t / 1.0, 0, 1).astype(np.float32)
for at, kind in events:
    add(at, boom() if kind == 'boom' else whoosh())
# Ausblenden + Normalisieren
fade = np.clip((total - t) / 1.2, 0, 1).astype(np.float32)
mix *= fade
mix /= max(1e-6, np.max(np.abs(mix))) / 0.85
wav = os.path.join(WORK, 'edit_audio.wav')
with wave.open(wav, 'wb') as wf:
    wf.setnchannels(1)
    wf.setsampwidth(2)
    wf.setframerate(SR)
    wf.writeframes((mix * 32767).astype(np.int16).tobytes())

out = os.path.join(PROJ, 'ZERO_BaseFinder_Edit.mp4')
cmd = [FFMPEG, '-y', '-hide_banner', '-loglevel', 'error', '-framerate', str(FPS), '-i', os.path.join(OUT_FRAMES, '%05d.png'),
       '-i', wav, '-c:v', 'libx264', '-preset', 'medium', '-crf', '18', '-pix_fmt', 'yuv420p', '-c:a', 'aac', '-b:a', '192k',
       '-shortest', out]
subprocess.run(cmd, check=True)
print('fertig:', out)
