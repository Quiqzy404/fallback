"""
Glas-Skins für das Avatar-Modul: halbdurchsichtiger Körper, leuchtende Outline an jeder Kante, leuchtende Augen.
Ausgabe: src/main/resources/assets/quiqzy/textures/avatar/glass_<farbe>_<stufe>.png (64x64, Alpha)
Aufruf (aus dem Projektordner): python tools/make_glass.py
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "quiqzy", "textures", "avatar")

COLORS = {
    "clear": ((235, 245, 255), (255, 255, 255)),
    "ice": ((150, 220, 255), (210, 245, 255)),
    "ember": ((255, 150, 80), (255, 210, 160)),
    "violet": ((180, 130, 255), (225, 200, 255)),
    "mint": ((120, 240, 190), (200, 255, 230)),
}
# Füllung (Glas) / Kante (Outline) Deckkraft
LEVELS = {"light": (40, 230), "medium": (95, 245)}


def face(img, x, y, w, h, fill, edge, inner_edge):
    for py in range(h):
        for px in range(w):
            border = px == 0 or py == 0 or px == w - 1 or py == h - 1
            img.putpixel((x + px, y + py), edge if border else fill)
    # feine zweite Linie innen, damit große Flächen nicht leer wirken
    if w >= 8 and h >= 8:
        for px in range(1, w - 1):
            img.putpixel((x + px, y + 1), inner_edge)


def box(img, u, v, w, h, d, fill, edge, inner_edge):
    faces = [
        (u + d, v, w, d), (u + d + w, v, w, d),               # oben, unten
        (u, v + d, d, h), (u + d, v + d, w, h),               # rechts, vorne
        (u + d + w, v + d, d, h), (u + d + w + d, v + d, w, h)  # links, hinten
    ]
    for (x, y, fw, fh) in faces:
        face(img, x, y, fw, fh, fill, edge, inner_edge)


def make(name, colors, level):
    base, glow = colors
    fa, ea = LEVELS[level]
    fill = base + (fa,)
    edge = glow + (ea,)
    inner = base + (min(255, fa + 60),)
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    box(img, 0, 0, 8, 8, 8, fill, edge, inner)      # Kopf
    box(img, 16, 16, 8, 12, 4, fill, edge, inner)   # Körper
    box(img, 40, 16, 4, 12, 4, fill, edge, inner)   # rechter Arm
    box(img, 32, 48, 4, 12, 4, fill, edge, inner)   # linker Arm
    box(img, 0, 16, 4, 12, 4, fill, edge, inner)    # rechtes Bein
    box(img, 16, 48, 4, 12, 4, fill, edge, inner)   # linkes Bein
    # Leuchtende Augen auf der Kopf-Vorderseite (8x8 bei 8,8)
    for ex in (10, 13):
        img.putpixel((ex, 12), (255, 255, 255, 255))
        img.putpixel((ex, 11), glow + (255,))
    os.makedirs(OUT, exist_ok=True)
    img.save(os.path.join(OUT, f"glass_{name}_{level}.png"))


if __name__ == "__main__":
    for n, c in COLORS.items():
        for lv in LEVELS:
            make(n, c, lv)
    print("ok", len(COLORS) * len(LEVELS), "Skins")
