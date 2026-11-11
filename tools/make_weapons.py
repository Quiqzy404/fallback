"""
Erzeugt die Waffen-Modelle fuer das Modul "Weapon Skins" (eigene Designs im ZERO-Stil).
 - Palette-Textur: assets/quiqzy/textures/item/weapon_palette.png (32x32, Farbfelder 4x4 px)
 - Modelle:        assets/quiqzy/models/item/weapon_<name>.json (Quader, senkrecht gebaut, dann -45 Grad gekippt wie ein Vanilla-Schwert)
 - Item-Defs:      assets/quiqzy/items/weapon_<name>.json
Orange Teile leuchten (light_emission).

Aufruf (aus dem Projektordner):  python tools/make_weapons.py
"""
import json
import os
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "quiqzy")

PALETTE = [
    (222, 228, 238),  # 0 Stahl hell
    (172, 180, 194),  # 1 Stahl mittel
    (118, 124, 138),  # 2 Stahl dunkel
    (242, 192, 84),   # 3 Gold
    (186, 136, 48),   # 4 Gold dunkel
    (24, 22, 27),     # 5 Schwarz
    (54, 51, 60),     # 6 Dunkelgrau
    (240, 122, 44),   # 7 Orange
    (255, 176, 108),  # 8 Orange hell
    (66, 45, 34),     # 9 Holz dunkel
    (98, 68, 48),     # 10 Holz
    (250, 250, 255),  # 11 Kante weiss
    (70, 120, 235),   # 12 Blau
    (40, 70, 160),    # 13 Blau dunkel
    (150, 90, 230),   # 14 Violett
]

# Seitenfarbe (etwas dunkler) je Grundfarbe
SIDE = {0: 1, 1: 2, 2: 2, 3: 4, 4: 4, 5: 5, 6: 5, 7: 7, 8: 7, 9: 9, 10: 9, 11: 0, 12: 13, 13: 13, 14: 13}
GLOW = {7, 8}


def palette_png():
    img = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    for i, c in enumerate(PALETTE):
        cx, cy = (i % 8) * 4, (i // 8) * 4
        for y in range(4):
            for x in range(4):
                img.putpixel((cx + x, cy + y), c + (255,))
    path = os.path.join(ROOT, "textures", "item")
    os.makedirs(path, exist_ok=True)
    img.save(os.path.join(path, "weapon_palette.png"))


def uv(i):
    # 32px Textur = 16 UV-Einheiten -> Feld 4px = 2 Einheiten; Mitte nehmen (keine Farbkanten)
    cx, cy = (i % 8) * 2, (i // 8) * 2
    return [cx + 0.5, cy + 0.5, cx + 1.5, cy + 1.5]


def cube(x1, y1, z1, x2, y2, z2, col):
    side = SIDE[col]
    faces = {}
    for f in ("north", "south"):
        faces[f] = {"uv": uv(col), "texture": "#p"}
    for f in ("east", "west", "up", "down"):
        faces[f] = {"uv": uv(side), "texture": "#p"}
    e = {
        "from": [x1, y1, z1],
        "to": [x2, y2, z2],
        "rotation": {"angle": -45, "axis": "z", "origin": [8, 8, 8]},
        "faces": faces,
    }
    if col in GLOW:
        e["light_emission"] = 12
    return e


def blade(x1, y1, x2, y2, edge=True, z=(7.85, 8.15)):
    out = [cube(x1, y1, z[0], x2, y2, z[1], 0)]
    if edge:
        out.append(cube(x2, y1, z[0] + 0.05, x2 + 0.15, y2, z[1] - 0.05, 11))
    return out


def katana():
    e = [cube(7.2, -6, 7.2, 8.8, -5.2, 8.8, 3)]
    e.append(cube(7.4, -5.2, 7.4, 8.6, 3, 8.6, 5))
    for y in (-4.2, -2.2, -0.2, 1.8):
        e.append(cube(7.3, y, 7.3, 8.7, y + 0.6, 8.7, 7))
    e.append(cube(6, 3, 6.5, 10, 3.6, 9.5, 3))
    e.append(cube(7.45, 3.6, 7.6, 8.55, 4.4, 8.4, 4))
    e += blade(7.5, 4.4, 8.45, 23.5)
    e.append(cube(7.7, 23.5, 7.88, 8.5, 24.6, 8.12, 0))
    e.append(cube(8.0, 24.6, 7.9, 8.45, 25.4, 8.1, 11))
    return e


def greatsword():
    e = [cube(7.1, -6, 7.1, 8.9, -4.6, 8.9, 3)]
    e.append(cube(7.3, -4.6, 7.3, 8.7, 2, 8.7, 9))
    e.append(cube(7.2, -2, 7.2, 8.8, -1.4, 8.8, 4))
    e.append(cube(3.5, 2, 7, 12.5, 3.2, 9, 3))
    e.append(cube(3, 2.1, 7.3, 3.8, 3.1, 8.7, 7))
    e.append(cube(12.2, 2.1, 7.3, 13, 3.1, 8.7, 7))
    e.append(cube(6.6, 3.2, 7.7, 9.4, 24.5, 8.3, 0))
    e.append(cube(7.7, 3.4, 7.6, 8.3, 22, 8.4, 2))
    e.append(cube(7.2, 24.5, 7.75, 8.8, 26, 8.25, 0))
    e.append(cube(7.7, 26, 7.8, 8.3, 27.2, 8.2, 11))
    return e


def scythe():
    e = [cube(7.4, -8, 7.4, 8.6, 27, 8.6, 9)]
    for y in (-3, 9):
        e.append(cube(7.25, y, 7.25, 8.75, y + 0.8, 8.75, 7))
    e.append(cube(7.2, 26, 7.2, 8.8, 28, 8.8, 5))
    # Klinge: nach links, in Stufen nach unten gebogen
    segs = [(2, 25.4, 7.4, 27.6), (-1, 24, 2.4, 26.6), (-3.6, 22, 0.2, 25), (-5.2, 19.5, -2, 22.6), (-6, 17.4, -4, 20)]
    for x1, y1, x2, y2 in segs:
        e.append(cube(x1, y1, 7.8, x2, y2, 8.2, 0))
    # helle Schneide unten
    for x1, y1, x2, _ in segs:
        e.append(cube(x1, y1 - 0.25, 7.85, x2, y1, 8.15, 11))
    e.append(cube(6.6, 24.6, 7.7, 7.4, 27.8, 8.3, 7))
    return e


def karambit():
    e = []
    # Ring
    e.append(cube(6, -3, 7.5, 10, -2.4, 8.5, 3))
    e.append(cube(6, 0.4, 7.5, 10, 1, 8.5, 3))
    e.append(cube(6, -3, 7.5, 6.6, 1, 8.5, 3))
    e.append(cube(9.4, -3, 7.5, 10, 1, 8.5, 3))
    # Griff
    e.append(cube(7.2, 1, 7.3, 8.8, 8, 8.7, 7))
    for y in (2.2, 4.2, 6.2):
        e.append(cube(7.1, y, 7.2, 8.9, y + 0.6, 8.8, 5))
    # gebogene Klinge
    segs = [(7.5, 8, 8.5, 11), (8, 11, 9.2, 13.5), (8.8, 13.5, 10.2, 15.5), (9.8, 15.5, 11, 17), (10.6, 17, 11.4, 18)]
    for x1, y1, x2, y2 in segs:
        e.append(cube(x1, y1, 7.85, x2, y2, 8.15, 0))
        e.append(cube(x1 - 0.15, y1, 7.9, x1, y2, 8.1, 11))
    return e


def butterfly():
    e = []
    for x1, x2 in ((6.7, 7.6), (8.4, 9.3)):
        e.append(cube(x1, -4, 7.5, x2, 7, 8.5, 5))
        for y in (-2.5, -0.5, 1.5, 3.5, 5.5):
            e.append(cube(x1 + 0.25, y, 7.45, x2 - 0.25, y + 0.5, 8.55, 7))
    e.append(cube(7.6, -4.2, 7.7, 8.4, -3.6, 8.3, 3))
    e.append(cube(7.2, 7, 7.6, 8.8, 7.6, 8.4, 3))
    e += blade(7.55, 7.6, 8.45, 17.5)
    e.append(cube(7.8, 17.5, 7.88, 8.45, 18.6, 8.12, 0))
    return e


def grip(e, y1, y2, base, stripe):
    """Griff mit Querstreifen."""
    e.append(cube(7.25, y1, 7.25, 8.75, y2, 8.75, base))
    y = y1 + 0.6
    while y < y2 - 0.4:
        e.append(cube(7.15, y, 7.15, 8.85, y + 0.5, 8.85, stripe))
        y += 1.4


def bayonet():
    # Militär-Messer: Fingerschutz, gerade Klinge mit Rücken-Zähnen, blau-violetter Verlauf
    e = []
    e.append(cube(7.3, -6, 7.3, 8.7, -5.2, 8.7, 2))
    grip(e, -5.2, 2.8, 5, 12)
    e.append(cube(5.6, 2.8, 7.5, 10.4, 3.6, 8.5, 2))
    e.append(cube(9.6, 1.6, 7.6, 10.4, 2.8, 8.4, 2))
    cols = [13, 12, 12, 14]
    for i, y in enumerate((3.6, 7.0, 10.4, 13.8)):
        e.append(cube(7.2, y, 7.85, 8.9, y + 3.4, 8.15, cols[i]))
    e.append(cube(8.9, 3.6, 7.9, 9.05, 17.0, 8.1, 11))
    for y in (8.0, 9.4, 10.8):
        e.append(cube(6.9, y, 7.88, 7.2, y + 0.7, 8.12, 1))
    e.append(cube(7.6, 17.0, 7.88, 8.9, 18.4, 8.12, 14))
    e.append(cube(8.3, 18.4, 7.9, 8.9, 19.2, 8.1, 11))
    return e


def flip():
    # Klappmesser: schlanker Griff, Daumenloch-Platte, kurze breite Klinge
    e = []
    grip(e, -4.5, 2.5, 6, 7)
    e.append(cube(7.3, 2.5, 7.4, 8.7, 3.4, 8.6, 1))
    e.append(cube(6.6, 3.4, 7.85, 9.2, 10.5, 8.15, 0))
    e.append(cube(9.2, 3.4, 7.9, 9.35, 10.5, 8.1, 11))
    e.append(cube(7.2, 10.5, 7.85, 9.2, 12.0, 8.15, 0))
    e.append(cube(8.4, 12.0, 7.88, 9.2, 12.9, 8.12, 11))
    e.append(cube(7.0, 4.5, 7.8, 7.8, 5.3, 8.2, 7))
    return e


def gut():
    # Jagdmesser mit Haken auf dem Rücken
    e = []
    grip(e, -4.5, 2.6, 9, 3)
    e.append(cube(6.8, 2.6, 7.5, 9.2, 3.2, 8.5, 3))
    e.append(cube(7.0, 3.2, 7.85, 9.3, 11.0, 8.15, 0))
    e.append(cube(9.3, 3.2, 7.9, 9.45, 10.0, 8.1, 11))
    e.append(cube(6.2, 8.6, 7.88, 7.0, 10.2, 8.12, 0))
    e.append(cube(6.2, 10.2, 7.88, 6.8, 11.2, 8.12, 1))
    e.append(cube(7.4, 11.0, 7.88, 9.3, 12.3, 8.12, 0))
    e.append(cube(8.5, 12.3, 7.9, 9.3, 13.1, 8.1, 11))
    return e


def bowie():
    # großes Bowie-Messer mit Messing-Parierstange, Clip-Point
    e = []
    e.append(cube(7.2, -6, 7.2, 8.8, -5.2, 8.8, 3))
    grip(e, -5.2, 2.4, 10, 9)
    e.append(cube(5.4, 2.4, 7.4, 10.6, 3.3, 8.6, 3))
    e.append(cube(6.8, 3.3, 7.85, 9.8, 15.0, 8.15, 0))
    e.append(cube(9.8, 3.3, 7.9, 9.95, 15.0, 8.1, 11))
    e.append(cube(6.8, 5.0, 7.86, 7.4, 13.0, 8.14, 7))
    e.append(cube(7.4, 15.0, 7.85, 9.8, 16.6, 8.15, 0))
    e.append(cube(8.4, 16.6, 7.88, 9.8, 17.8, 8.12, 0))
    e.append(cube(9.1, 17.8, 7.9, 9.8, 18.6, 8.1, 11))
    return e


DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "gui": {"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": [0.72, 0.72, 0.72]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": [0.8, 0.8, 0.8]},
}

WEAPONS = {
    "katana": (katana, 0.85),
    "greatsword": (greatsword, 0.8),
    "scythe": (scythe, 0.75),
    "karambit": (karambit, 1.0),
    "butterfly": (butterfly, 1.0),
    "bayonet": (bayonet, 0.95),
    "flip": (flip, 1.0),
    "gut": (gut, 1.0),
    "bowie": (bowie, 0.95),
}


def scaled(display, k):
    d = json.loads(json.dumps(display))
    # Unsere Waffen sind ~2x so lang wie ein Vanilla-Schwert -> deutlich kleiner skalieren
    for key in ("thirdperson_righthand", "thirdperson_lefthand", "firstperson_righthand", "firstperson_lefthand"):
        d[key]["scale"] = [round(v * k * 0.55, 3) for v in d[key]["scale"]]
    for key in ("gui", "ground", "fixed"):
        d[key]["scale"] = [round(v * k * 0.62, 3) for v in d[key]["scale"]]
    return d


def main():
    palette_png()
    mdir = os.path.join(ROOT, "models", "item")
    idir = os.path.join(ROOT, "items")
    os.makedirs(mdir, exist_ok=True)
    os.makedirs(idir, exist_ok=True)
    for name, (fn, k) in WEAPONS.items():
        model = {
            "textures": {"p": "quiqzy:item/weapon_palette", "particle": "quiqzy:item/weapon_palette"},
            "elements": fn(),
            "display": scaled(DISPLAY, k),
        }
        with open(os.path.join(mdir, f"weapon_{name}.json"), "w") as f:
            json.dump(model, f, indent=1)
        with open(os.path.join(idir, f"weapon_{name}.json"), "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": f"quiqzy:item/weapon_{name}"}}, f, indent=1)
        print("ok", name, len(model["elements"]), "elements")


if __name__ == "__main__":
    main()
