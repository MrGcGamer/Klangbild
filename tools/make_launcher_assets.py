#!/usr/bin/env python3
"""Renders the banner for the TV's home screen and the launcher icons from the logo and the font.

Usage: python3 tools/make_launcher_assets.py   (needs Pillow)

The logo: seven rounded squares, one inside the other, rounding into a circle toward the middle,
from a mid blue outside to the palest blue around a light dot, on night blue. A sound spreading
through a picture. It is drawn here at any size, never scaled from a bitmap.
"""
import os
import random

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
FONT = os.path.join(RES, "font", "jost.ttf")

NAVY = (6, 15, 39)
INK = (232, 242, 255)
# The rings from the outside in: color, and in a 1024 px square their outer half-size and corner radius.
RINGS = [
    ((79, 127, 213), 336, 73),
    ((84, 145, 218), 294, 110),
    ((102, 159, 229), 252, 134),
    ((117, 176, 236), 210, 141),
    ((136, 192, 239), 168, 135),
    ((146, 211, 248), 126, 114),
    ((170, 227, 255), 84, 84),
]
STROKE = 13.5
DOT = ((220, 243, 255), 40)
# The original's square, and a square that holds just the rings.
FULL = 1024
TIGHT = 2 * 336 + 4


def logo(size, background=NAVY, extent=TIGHT):
    """The logo on a square of [size] px showing [extent] units of the 1024 unit original around
    its middle; without a background, on transparency."""
    ss = 4
    s = size * ss
    img = Image.new("RGBA", (s, s), (background + (255,)) if background else (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    k = s / extent
    c = s / 2
    for color, half, radius in RINGS:
        d.rounded_rectangle([c - half * k, c - half * k, c + half * k, c + half * k],
                            radius=radius * k, outline=color + (255,), width=max(1, round(STROKE * k)))
    color, r = DOT
    d.ellipse([c - r * k, c - r * k, c + r * k, c + r * k], fill=color + (255,))
    # Scaled down with the color premultiplied, so the edges keep their color on transparency.
    return img.convert("RGBa").resize((size, size), Image.LANCZOS).convert("RGBA")


def jost(size, weight=400):
    f = ImageFont.truetype(FONT, size)
    vals = []
    for a in f.get_variation_axes():
        name = a["name"] if isinstance(a["name"], str) else a["name"].decode()
        vals.append({"Weight": weight}.get(name, a["default"]))
    f.set_variation_by_axes(vals)
    return f


def dither(img, amount=1.2, seed=3):
    rnd = random.Random(seed)
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, *rest = px[x, y]
            n = (rnd.random() + rnd.random() - 1.0) * amount
            px[x, y] = (max(0, min(255, int(r + n))), max(0, min(255, int(g + n))), max(0, min(255, int(b + n))), *rest)
    return img


def banner(path, w=320, h=180):
    """The logo beside the name, both centered on the banner as a group."""
    s = 4
    img = Image.new("RGB", (w * s, h * s), NAVY)
    side = int(h * s * 0.50)
    mark = logo(side, background=None)
    f = jost(int(37 * s))
    d = ImageDraw.Draw(img)
    text = "Klangbild"
    tw = d.textlength(text, font=f)
    gap = int(17 * s)
    x0 = int((w * s - (side + gap + tw)) / 2)
    y0 = (h * s - side) // 2
    img.paste(mark, (x0, y0), mark)
    ascent, _ = f.getmetrics()
    # The cap height (0.70 em) centered on the logo.
    cap = 0.70 * 37 * s
    d.text((x0 + side + gap, int(h * s / 2 + cap / 2 - ascent)), text, font=f, fill=INK)
    img = img.resize((w, h), Image.LANCZOS)
    dither(img)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def legacy_icon(path, size):
    """The logo filling most of a night blue square."""
    img = logo(size, extent=int(TIGHT / 0.80)).convert("RGB")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


if __name__ == "__main__":
    banner(os.path.join(RES, "drawable-xhdpi", "banner.png"))
    for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        legacy_icon(os.path.join(RES, f"mipmap-{density}", "ic_launcher.png"), size)
    print("ok")
