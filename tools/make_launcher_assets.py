#!/usr/bin/env python3
"""Renders the Google TV banner and the launcher icons from the bundled fonts.

Usage: python3 tools/make_launcher_assets.py   (needs Pillow)

The motif is the app itself: a small square "cover" lit from behind by warm lamp light,
on a near-black wall, next to the wordmark set in Newsreader.
"""
import math
import os
import random

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES = os.path.join(ROOT, "app", "src", "main", "res")
FONT = os.path.join(RES, "font", "newsreader.ttf")

WALL = (13, 11, 10)
INK = (232, 222, 208)
GLOW = (214, 150, 82)
CARD_TOP = (190, 112, 58)
CARD_BOTTOM = (92, 44, 26)


def newsreader(size, weight=330, opsz=72):
    f = ImageFont.truetype(FONT, size)
    axes = f.get_variation_axes()
    vals = []
    for a in axes:
        name = a["name"] if isinstance(a["name"], str) else a["name"].decode()
        vals.append({"Weight": weight, "Optical size": opsz}.get(name, a["default"]))
    f.set_variation_by_axes(vals)
    return f


def dither(img, amount=1.6, seed=3):
    rnd = random.Random(seed)
    px = img.load()
    w, h = img.size
    for y in range(h):
        for x in range(w):
            r, g, b, *rest = px[x, y]
            n = (rnd.random() + rnd.random() - 1.0) * amount
            px[x, y] = (max(0, min(255, int(r + n))), max(0, min(255, int(g + n))), max(0, min(255, int(b + n))), *rest)
    return img


def lit_card(img, box, scale):
    """A square 'cover' with a warm halo behind it."""
    x0, y0, x1, y1 = box
    glow = Image.new("RGB", img.size, (0, 0, 0))
    gd = ImageDraw.Draw(glow)
    gd.rectangle(box, fill=GLOW)
    glow = glow.filter(ImageFilter.GaussianBlur(radius=14 * scale))
    base = Image.new("RGB", img.size, WALL)
    img.paste(Image.blend(base, Image.composite(glow, base, glow.convert("L").point(lambda v: 255)), 0.55))
    # the card: a quiet gradient like a painted cover
    card = Image.new("RGB", (x1 - x0, y1 - y0))
    cd = ImageDraw.Draw(card)
    for yy in range(y1 - y0):
        t = yy / max(1, (y1 - y0 - 1))
        col = tuple(int(CARD_TOP[i] + (CARD_BOTTOM[i] - CARD_TOP[i]) * t) for i in range(3))
        cd.line([(0, yy), (x1 - x0, yy)], fill=col)
    # a pale disc, like a low sun
    r = (x1 - x0) * 0.22
    cx, cy = (x1 - x0) * 0.58, (y1 - y0) * 0.42
    cd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(236, 206, 160))
    cd.rectangle([0, (y1 - y0) * 0.62, x1 - x0, y1 - y0], fill=(70, 32, 20))
    img.paste(card, (x0, y0))


def banner(path, w=320, h=180):
    s = 4  # supersample
    img = Image.new("RGB", (w * s, h * s), WALL)
    side = int(h * s * 0.46)
    x0 = int(w * s * 0.12)
    y0 = (h * s - side) // 2
    lit_card(img, (x0, y0, x0 + side, y0 + side), s)
    d = ImageDraw.Draw(img)
    f = newsreader(int(40 * s))
    text = "Klangbild"
    tw = d.textlength(text, font=f)
    tx = x0 + side + int(22 * s)
    ascent, descent = f.getmetrics()
    ty = (h * s) // 2 - ascent // 2 - int(6 * s)
    d.text((tx, ty), text, font=f, fill=INK)
    img = img.resize((w, h), Image.LANCZOS)
    dither(img)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


def legacy_icon(path, size):
    s = 4
    img = Image.new("RGB", (size * s, size * s), WALL)
    side = int(size * s * 0.46)
    x0 = (size * s - side) // 2
    lit_card(img, (x0, x0, x0 + side, x0 + side), s * size / 48)
    img = img.resize((size, size), Image.LANCZOS)
    dither(img, 1.0)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)


if __name__ == "__main__":
    banner(os.path.join(RES, "drawable-xhdpi", "banner.png"))
    for density, size in {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}.items():
        legacy_icon(os.path.join(RES, f"mipmap-{density}", "ic_launcher.png"), size)
    print("ok")
