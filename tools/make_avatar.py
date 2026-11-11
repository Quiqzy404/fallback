"""
Erzeugt die Avatar-Texturen fuer das Modul "Avatar" (Figur "Rook", eigenes Design).
Standard-Minecraft-Skinformat 64x64 (breite Arme). Ausgabe:
  src/main/resources/assets/quiqzy/textures/avatar/rook_<outfit>_<tone>.png

Aufruf (aus dem Projektordner):  python tools/make_avatar.py
"""
import os
from PIL import Image

OUT = os.path.join(os.path.dirname(__file__), "..", "src", "main", "resources", "assets", "quiqzy", "textures", "avatar")

TONES = {
    "light": ((240, 200, 170), (222, 178, 146), (205, 160, 128)),
    "medium": ((198, 142, 100), (178, 124, 86), (156, 106, 72)),
    "dark": ((122, 84, 60), (104, 70, 50), (88, 58, 41)),
}

OUTFITS = {
    # hoodie hell, hoodie mittel, hoodie dunkel, akzent, hose, hose dunkel, schuh, sohle
    "orange": ((255, 155, 79), (240, 122, 44), (200, 90, 28), (255, 240, 225), (38, 35, 42), (28, 26, 31), (236, 236, 236), (240, 122, 44)),
    "midnight": ((60, 58, 66), (44, 42, 50), (30, 29, 35), (240, 122, 44), (24, 23, 28), (16, 15, 19), (34, 33, 38), (240, 122, 44)),
    "ice": ((242, 246, 250), (220, 228, 236), (190, 200, 212), (60, 200, 210), (52, 62, 78), (40, 48, 62), (250, 250, 250), (60, 200, 210)),
}

CAP = (22, 22, 26)
CAP2 = (36, 36, 42)
HAIR = (34, 26, 22)
SHADE = (14, 14, 18)
SHADE_HI = (255, 150, 70)
GOLD = (236, 192, 74)
GOLD2 = (190, 146, 44)
MOUTH = (110, 60, 50)


def face(img, x, y, w, h, fn):
    """fn(px, py) -> rgb oder None (fuer jede Zelle einer Flaeche)."""
    for py in range(h):
        for px in range(w):
            c = fn(px, py)
            if c is not None:
                img.putpixel((x + px, y + py), c + (255,))


def box(img, u, v, w, h, d, fns):
    """Box-UV wie Minecraft: u,v = Ecke, w/h/d = Breite/Hoehe/Tiefe. fns: dict top,bottom,right,front,left,back."""
    faces = {
        "top": (u + d, v, w, d),
        "bottom": (u + d + w, v, w, d),
        "right": (u, v + d, d, h),
        "front": (u + d, v + d, w, h),
        "left": (u + d + w, v + d, d, h),
        "back": (u + d + w + d, v + d, w, h),
    }
    for name, (x, y, fw, fh) in faces.items():
        fn = fns.get(name) or fns.get("*")
        if fn:
            face(img, x, y, fw, fh, fn)


def make(outfit, tone):
    s1, s2, s3 = TONES[tone]
    h1, h2, h3, acc, p1, p2, shoe, sole = OUTFITS[outfit]
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))

    # ---------------- Kopf (8x8x8 bei 0,0)
    def head_front(x, y):
        if y <= 1:
            return HAIR
        if y in (3, 4) and 1 <= x <= 6:  # Sonnenbrille
            if y == 3 and x in (2, 5):
                return SHADE_HI
            return SHADE if x not in (3, 4) or y == 3 else s2
        if y == 6 and 3 <= x <= 4:
            return MOUTH
        if y == 7:
            return s2
        return s1

    def head_side(x, y):
        if y <= 2:
            return HAIR
        if y == 3 and (x == 0 or x == 7):
            return SHADE
        return s2 if x in (0, 7) else s1

    box(img, 0, 0, 8, 8, 8, {
        "top": lambda x, y: HAIR,
        "bottom": lambda x, y: s3,
        "front": head_front,
        "back": lambda x, y: HAIR if y <= 4 else s2,
        "right": head_side,
        "left": head_side,
    })

    # Cap (Hut-Ebene bei 32,0) – rueckwaerts getragen
    def cap_front(x, y):
        if y <= 1:
            return CAP if not (y == 1 and 3 <= x <= 4) else None  # Verschluss-Oeffnung
        return None

    def cap_back(x, y):
        if y <= 1:
            return CAP2
        if y == 2:
            return acc if 2 <= x <= 5 else CAP  # Schirm-Kante mit Akzent
        return None

    def cap_side(x, y):
        if y <= 1:
            return CAP
        if y == 2 and x >= 4:
            return CAP
        return None

    box(img, 32, 0, 8, 8, 8, {
        "top": lambda x, y: acc if (x in (3, 4) and y in (3, 4)) else (CAP2 if y >= 5 else CAP),
        "front": cap_front,
        "back": cap_back,
        "right": lambda x, y: cap_side(7 - x, y),
        "left": cap_side,
    })

    # ---------------- Koerper: Hoodie (8x12x4 bei 16,16)
    def body_front(x, y):
        if y == 0 and 2 <= x <= 5:
            return s1  # Hals
        if y in (1, 2) and (x == 1 or x == 6) and y == 1:
            pass
        # Kette (V-Form)
        if (y == 1 and x in (2, 5)) or (y == 2 and x in (3, 4)):
            return GOLD
        if y == 3 and x in (3, 4):
            return GOLD2 if x == 3 else GOLD
        # Kordeln
        if 1 <= y <= 4 and x in (1, 6):
            return acc
        # Bauchtasche
        if 7 <= y <= 9 and 1 <= x <= 6:
            return h3 if y == 7 or x in (1, 6) else h2
        if y == 11:
            return h3  # Bund
        return h1 if x in (2, 3, 4, 5) and y < 7 else h2

    box(img, 16, 16, 8, 12, 4, {
        "front": body_front,
        "back": lambda x, y: h3 if y == 11 else h2,
        "right": lambda x, y: h3 if y == 11 else h2,
        "left": lambda x, y: h3 if y == 11 else h2,
        "top": lambda x, y: h2,
        "bottom": lambda x, y: h3,
    })
    # Kapuze hinten als 3D-Ebene (Jacket-Layer bei 16,32)
    box(img, 16, 32, 8, 12, 4, {
        "back": lambda x, y: (h3 if y == 3 else h2) if y <= 3 else None,
        "top": lambda x, y: h2 if y >= 2 else None,
    })

    # ---------------- Arme (4x12x4): rechts bei 40,16, links bei 32,48
    def sleeve(x, y):
        if y == 11:
            return s2  # Hand
        if y == 10:
            return s1
        if y == 9:
            return h3  # Buendchen
        if y == 4 and outfit != "orange":
            return acc  # Streifen
        return h1 if x in (1, 2) else h2

    arm = {
        "front": sleeve, "back": sleeve, "right": sleeve, "left": sleeve,
        "top": lambda x, y: h2, "bottom": lambda x, y: s2,
    }
    box(img, 40, 16, 4, 12, 4, arm)
    box(img, 32, 48, 4, 12, 4, arm)

    # ---------------- Beine (4x12x4): rechts bei 0,16, links bei 16,48
    def leg(x, y, side_pocket=False):
        if y >= 10:
            return sole if y == 11 else shoe
        if y == 9:
            return shoe if x in (1, 2) else (acc if outfit == "ice" else shoe)
        if side_pocket and 4 <= y <= 6 and 1 <= x <= 2:
            return p2
        if y == 6 and x in (0, 3):
            return p2
        return p1

    def leg_front(x, y):
        if y == 10 and x in (1, 2):
            return sole  # Schnuersenkel-Akzent
        return leg(x, y)

    legs = {
        "front": leg_front,
        "back": lambda x, y: leg(x, y),
        "right": lambda x, y: leg(x, y, True),
        "left": lambda x, y: leg(x, y, True),
        "top": lambda x, y: p1,
        "bottom": lambda x, y: sole,
    }
    box(img, 0, 16, 4, 12, 4, legs)
    box(img, 16, 48, 4, 12, 4, legs)
    # Cargo-Taschen als 3D-Ebene (Hosen-Layer bei 0,32 und 0,48)
    pocket = lambda x, y: (p2 if y == 4 else p1) if 4 <= y <= 7 and 1 <= x <= 2 else None
    box(img, 0, 32, 4, 12, 4, {"right": pocket})
    box(img, 0, 48, 4, 12, 4, {"left": pocket})

    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, f"rook_{outfit}_{tone}.png")
    img.save(path)
    return path


if __name__ == "__main__":
    for o in OUTFITS:
        for t in TONES:
            print(make(o, t))
