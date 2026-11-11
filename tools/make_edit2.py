"""
ZERO Client – Base-Edit (Beat-synchron, OHNE Musik).
Schnitte liegen auf dem Raster des Referenz-Songs: Tempo ~92,3 BPM (Schlag 0,65 s), Drop bei 11,61 s, Outro ab 30,37 s.
Den Song legst du selbst in CapCut ab 0:00 darunter – dann sitzt jeder Schnitt auf dem Beat.

Aufruf: python make_edit2.py <Projektordner> <Arbeitsordner> <ffmpeg.exe>
"""
import glob
import math
import os
import random
import subprocess
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFilter, ImageFont

PROJ, WORK, FFMPEG = sys.argv[1], sys.argv[2], sys.argv[3]
SHOTS = os.path.join(PROJ, 'run', 'screenshots')
FONTS = os.path.join(PROJ, 'src', 'main', 'resources', 'assets', 'quiqzy', 'font')
OUT = os.path.join(WORK, 'edit2_out')
os.makedirs(OUT, exist_ok=True)
for f in glob.glob(os.path.join(OUT, '*.png')):
    os.remove(f)

W, H, FPS = 1280, 720, 30
TOTAL = 33.37
BEAT = 0.650
DROP = 11.61
OUTRO = 30.37
ORANGE, ORANGE2, WHITE = (255, 178, 110), (236, 95, 28), (255, 244, 236)
BOLD = os.path.join(FONTS, 'nunito_extrabold.ttf')
LOGO = os.path.join(FONTS, 'playfair_italic.ttf')
random.seed(11)

shots = {}
for path in sorted(glob.glob(os.path.join(SHOTS, 'e2_*.png'))):
    name = os.path.basename(path).split('_')[2]
    shots.setdefault(name, []).append(path)
print({k: len(v) for k, v in shots.items()})
pointer = {k: 0 for k in shots}
_cache = {}


def frame(path):
    if path not in _cache:
        if len(_cache) > 30:
            _cache.clear()
        _cache[path] = Image.open(path).convert('RGB').resize((W, H), Image.LANCZOS)
    return _cache[path]


yy, xx = np.mgrid[0:H, 0:W]
VIG = (1.0 - 0.42 * np.clip(np.sqrt(((xx - W / 2) / (W / 2)) ** 2 + ((yy - H / 2) / (H / 2)) ** 2) - 0.5, 0, 1))[..., None]


def grade(im, dark=1.0):
    a = np.asarray(im).astype(np.float32)
    a[..., 0] *= 1.05
    a[..., 2] *= 0.95
    a = (a - 128) * 1.08 + 128  # etwas mehr Kontrast
    a *= VIG * dark
    return Image.fromarray(np.clip(a, 0, 255).astype(np.uint8))


def zoom(im, s):
    if abs(s - 1) < 1e-3:
        return im
    w2, h2 = int(W * s), int(H * s)
    big = im.resize((w2, h2), Image.BILINEAR)
    return big.crop(((w2 - W) // 2, (h2 - H) // 2, (w2 - W) // 2 + W, (h2 - H) // 2 + H))


def zoom_blur(im, k):
    if k <= 0.01:
        return im
    acc = np.asarray(im).astype(np.float32)
    n = 8
    for i in range(1, n):
        acc += np.asarray(zoom(im, 1 + k * 0.25 * i / n)).astype(np.float32)
    return Image.fromarray((acc / n).astype(np.uint8))


def text_layer(text, font, size, color, spacing=0, shear=0.0):
    f = ImageFont.truetype(font, size)
    bb = f.getbbox(text)
    tw = int(bb[2] - bb[0] + spacing * len(text) + 80 + abs(shear) * size)
    th = bb[3] - bb[1] + 60
    m = Image.new('L', (tw, th), 0)
    d = ImageDraw.Draw(m)
    x = 40
    for ch in text:
        d.text((x, 30 - bb[1]), ch, font=f, fill=255)
        x += f.getlength(ch) + spacing
    if shear:
        m = m.transform(m.size, Image.AFFINE, (1, shear, -shear * th / 2, 0, 1, 0), Image.BICUBIC)
    return m


def composite(base, mask, x0, y0, fill, glow, alpha=1.0, glow_size=16):
    base = base.convert('RGBA')
    for blur, col, strength in ((glow_size, glow, 1.7), (5, fill, 0.9)):
        gm = mask.filter(ImageFilter.GaussianBlur(blur)).point(lambda v: int(min(255, v * strength) * alpha))
        g = Image.new('RGBA', mask.size, col + (0,))
        g.putalpha(gm)
        base.alpha_composite(g, (int(x0), int(y0)))
    t = Image.new('RGBA', mask.size, fill + (0,))
    t.putalpha(mask.point(lambda v: int(v * alpha)))
    base.alpha_composite(t, (int(x0), int(y0)))
    return base.convert('RGB')


def pop_title(im, text, t, size=84, y=None, glow=ORANGE2):
    """Buchstaben ploppen einzeln rein (wachsen von groß auf normal)."""
    f = ImageFont.truetype(BOLD, size)
    total_w = sum(f.getlength(c) for c in text) + 6 * len(text)
    x = (W - total_w) / 2
    cy = H * 0.44 if y is None else y
    for i, ch in enumerate(text):
        lt = t - i * 0.045
        if lt <= 0:
            x += f.getlength(ch) + 6
            continue
        k = min(1.0, lt / 0.16)
        sc = 1.6 - 0.6 * (1 - (1 - k) ** 3)
        if ch.strip():
            m = text_layer(ch, BOLD, int(size * sc), WHITE)
            cw = f.getlength(ch) * sc
            im = composite(im, m, x + f.getlength(ch) / 2 - cw / 2 - 40, cy - m.size[1] / 2, WHITE, glow, alpha=k)
        x += f.getlength(ch) + 6
    return im


def logo(im, a, scale=1.0, y=None):
    """ZERO-Logo: schräg, orange Verlauf, kräftiger Glow, darunter 'CLIENT'."""
    m = text_layer('ZERO', BOLD, int(150 * scale), ORANGE, spacing=6, shear=-0.22)
    cy = H * 0.42 if y is None else y
    x0, y0 = (W - m.size[0]) / 2, cy - m.size[1] / 2
    im = composite(im, m, x0, y0, ORANGE, ORANGE2, alpha=a, glow_size=22)
    sub = text_layer('C L I E N T', BOLD, 26, WHITE)
    im = composite(im, sub, (W - sub.size[0]) / 2, y0 + m.size[1] - 34, WHITE, ORANGE2, alpha=a, glow_size=8)
    return im


def take(name, speed=1.0):
    """Nächstes Bild eines Schusses (läuft weiter, wenn der Schuss später wieder benutzt wird)."""
    lst = shots[name]
    i = min(len(lst) - 1, int(pointer[name]))
    pointer[name] = (pointer[name] + speed * 20 / FPS) % len(lst)
    return frame(lst[i])


# ---------------------------------------------------------------- Zeitplan
plan = []  # (start, ende, schuss, effekt, titel)
plan += [(0.00, 2.60, 'sky', 'intro', 'ZERO CLIENT')]
plan += [(2.60, 5.20, 'shaft', 'dive', None)]
plan += [(5.20, 7.80, 'ugorbit', 'logo', None)]
plan += [(7.80, 10.40, 'ugfly', 'title', 'BASE FINDER')]
plan += [(10.40, DROP, 'approach', 'build', None)]
cycle = ['orbit', 'hall', 'ugorbit', 'top', 'ugfly', 'approach', 'hall', 'orbit', 'shaft', 'top', 'ugfly', 'orbit', 'hall', 'ugorbit', 'top']
words = {0: 'MEGA BASES', 2: 'UNDERGROUND', 4: 'STORAGE ESP', 7: 'FOUND.', 10: 'EVERY CHUNK', 12: 'ZERO'}
t = DROP
i = 0
while t + 0.01 < OUTRO:
    end = min(OUTRO, t + 2 * BEAT)
    plan.append((t, end, cycle[i % len(cycle)], 'cut', words.get(i)))
    t = end
    i += 1
plan += [(OUTRO, TOTAL, 'orbit', 'outro', None)]

frames = 0
cuts = []
for (s, e, name, fx, title) in plan:
    cuts.append(s)
    n0, n1 = int(round(s * FPS)), int(round(e * FPS))
    for fi in range(n0, n1):
        lt = fi / FPS - s
        dur = e - s
        if fx == 'outro':
            im = Image.new('RGB', (W, H), (8, 6, 5))
            a = min(1, lt / 0.3) * (1 if lt < dur - 0.4 else max(0, (dur - lt) / 0.4))
            im = logo(im, a, scale=1.0 + 0.03 * math.sin(lt * 5))
            if lt < 0.2:
                im = zoom_blur(im, (1 - lt / 0.2) * 1.5)
        else:
            speed = {'dive': 1.15, 'build': 1.6, 'cut': 1.25}.get(fx, 1.0)
            im = grade(take(name, speed), dark=0.55 if fx == 'logo' else 1.0)
            if fx == 'cut':
                # Punch-Zoom auf jedem Beat + kurzer Zoom-Blur beim Schnitt
                beat_t = (lt % BEAT) / BEAT
                im = zoom(im, 1.0 + 0.06 * (1 - beat_t) ** 3)
                if lt < 0.12:
                    im = zoom_blur(im, (1 - lt / 0.12) * 1.3)
            elif fx == 'build':
                im = zoom_blur(zoom(im, 1 + 0.15 * lt / dur), 0.3 + 1.6 * (lt / dur) ** 2)
            elif fx in ('intro', 'dive', 'title', 'logo'):
                if lt < 0.2:
                    im = zoom_blur(im, (1 - lt / 0.2) * 1.6)
                if lt > dur - 0.18:
                    im = zoom_blur(im, (lt - (dur - 0.18)) / 0.18 * 1.6)
            if fx == 'logo':
                a = min(1, lt / 0.25) * (1 if lt < dur - 0.3 else max(0, (dur - lt) / 0.3))
                im = logo(im, a, scale=1.08 - 0.08 * min(1, lt / 0.4))
            if title:
                fade = 1 if lt < dur - 0.2 else max(0, (dur - lt) / 0.2)
                if fade > 0.02:
                    size = 92 if fx in ('intro', 'title') else 70
                    im = pop_title(im, title, lt, size=size)
        im.save(os.path.join(OUT, f'{fi:05d}.png'))
        frames += 1
print('frames', frames, 'cuts', len(cuts))

out = os.path.join(PROJ, 'ZERO_Bases_Edit_NoMusic.mp4')
subprocess.run([FFMPEG, '-y', '-hide_banner', '-loglevel', 'error', '-framerate', str(FPS), '-i', os.path.join(OUT, '%05d.png'),
                '-c:v', 'libx264', '-preset', 'medium', '-crf', '17', '-pix_fmt', 'yuv420p', out], check=True)
with open(os.path.join(PROJ, 'ZERO_Bases_Edit_Schnitte.txt'), 'w', encoding='utf-8') as f:
    f.write('Schnitte (Sekunden) – Song in CapCut ab 0:00 anlegen:\n')
    for c in cuts:
        f.write(f'{c:6.2f}\n')
print('fertig:', out)
