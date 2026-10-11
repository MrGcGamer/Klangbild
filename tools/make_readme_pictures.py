#!/usr/bin/env python3
"""Sets the README's screens in a room and saves its pictures, and draws the download button.

Usage: python3 tools/make_readme_pictures.py   (needs Pillow and numpy)

Render the screens first:
    ./gradlew :app:testDebugUnitTest --tests '*ReadmeShotsTest*' -Proborazzi.test.record=true
They land in app/build/screenshots/readme/, each with the colors the lights behind the TV would
show (a JSON file beside it). The pictures go to docs/screenshots/.

The room is lit in linear light: a painted wall, a little light from the room, and the lights
behind the TV, each a soft lamp hidden behind the screen's edge, shining on the wall in the color
the app sends it. The screen is the app's own render, set behind glass in a thin frame.
"""
import json
import math
import os
import sys

import numpy as np
from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RENDERS = os.path.join(ROOT, "app", "build", "screenshots", "readme")
OUT = os.path.join(ROOT, "docs", "screenshots")
FONTS = os.path.join(ROOT, "app", "src", "main", "res", "font")

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from make_launcher_assets import NAVY, logo  # noqa: E402  (the same mark as on the TV's home screen)


# Color ---------------------------------------------------------------------------------------

def to_linear(srgb):
    c = np.asarray(srgb, dtype=np.float64) / 255.0
    return np.where(c <= 0.04045, c / 12.92, ((c + 0.055) / 1.055) ** 2.4)


def to_srgb(lin):
    x = np.clip(lin, 0.0, 1.0)
    return np.where(x <= 0.0031308, 12.92 * x, 1.055 * x ** (1 / 2.4) - 0.055) * 255.0


def tonemap(x):
    """A filmic curve: soft highlights, so a lamp's core runs toward its color's white."""
    x = np.maximum(x, 0.0)
    return np.clip((x * (2.51 * x + 0.03)) / (x * (2.43 * x + 0.59) + 0.14), 0.0, 1.0)


def xy_to_rgb(x, y):
    """The color of light with chromaticity xy, as linear sRGB scaled to a peak of 1."""
    X, Y, Z = x / y, 1.0, (1 - x - y) / y
    r = 3.2406 * X - 1.5372 * Y - 0.4986 * Z
    g = -0.9689 * X + 1.8758 * Y + 0.0415 * Z
    b = 0.0557 * X - 0.2040 * Y + 1.0570 * Z
    rgb = np.maximum(np.array([r, g, b]), 0.0)
    return rgb / rgb.max()


def light_colors(name, vivid=1.0):
    """The lights' colors for a render: left, middle, right (linear sRGB) and how bright each may
    be. [vivid] above 1 takes each a little further from white along its own hue."""
    with open(os.path.join(RENDERS, name + ".json")) as f:
        data = json.load(f)["lights"]
    out = []
    for l in data:
        rgb = xy_to_rgb(*l["xy"])
        grey = rgb.mean()
        rgb = np.maximum(grey + (rgb - grey) * vivid, 0.0)
        out.append((rgb / rgb.max(), l["dim"]))
    return out


# Images --------------------------------------------------------------------------------------

def blur(a, sigma):
    """A Gaussian blur through the FFT, the edges mirrored."""
    if sigma <= 0:
        return a
    pad = int(3 * sigma) + 2
    widths = ((pad, pad), (pad, pad)) + ((0, 0),) * (a.ndim - 2)
    p = np.pad(a, widths, mode="reflect")
    h, w = p.shape[:2]
    fy = np.fft.fftfreq(h)[:, None]
    fx = np.fft.rfftfreq(w)[None, :]
    g = np.exp(-2 * (math.pi * sigma) ** 2 * (fx ** 2 + fy ** 2))
    if a.ndim == 3:
        g = g[..., None]
    out = np.fft.irfft2(np.fft.rfft2(p, axes=(0, 1)) * g, s=(h, w), axes=(0, 1))
    return out[pad:-pad, pad:-pad]


def noise(h, w, sigma, seed):
    """Smooth noise of unit spread at the scale [sigma] (px)."""
    n = blur(np.random.default_rng(seed).standard_normal((h, w)), sigma)
    return n / (n.std() + 1e-9)


def rounded_box(h, w, box, radius):
    """Coverage (0..1) of a rounded rectangle, antialiased."""
    x0, y0, x1, y1 = box
    y, x = np.mgrid[0:h, 0:w] + 0.5
    hx, hy = (x1 - x0) / 2 - radius, (y1 - y0) / 2 - radius
    qx = np.abs(x - (x0 + x1) / 2) - hx
    qy = np.abs(y - (y0 + y1) / 2) - hy
    d = np.hypot(np.maximum(qx, 0), np.maximum(qy, 0)) + np.minimum(np.maximum(qx, qy), 0) - radius
    return np.clip(0.5 - d, 0.0, 1.0)


def load(path, size):
    return np.asarray(Image.open(path).convert("RGB").resize(size, Image.LANCZOS), dtype=np.float64)


def font(name, size, weight, optical):
    """A bundled face at [size] px and [weight]; DM Sans also takes its optical size, as in the app."""
    f = ImageFont.truetype(os.path.join(FONTS, name), size)
    vals = []
    for a in f.get_variation_axes():
        axis = a["name"] if isinstance(a["name"], str) else a["name"].decode()
        vals.append({"Weight": weight, "Optical size": min(max(optical, a["minimum"]), a["maximum"])}.get(axis, a["default"]))
    f.set_variation_by_axes(vals)
    return f


def text_mask(text, face, size, weight, tracking=0.0, ss=4):
    """The coverage of [text] set in [face] at [size] px, its cap height's top at the mask's top."""
    f = font(face, size * ss, weight, optical=size)
    ascent, descent = f.getmetrics()
    advance = sum(f.getlength(ch) for ch in text) + tracking * size * ss * (len(text) - 1)
    img = Image.new("L", (int(advance + size * ss), ascent + descent), 0)
    d = ImageDraw.Draw(img)
    x = 0.0
    for ch in text:
        d.text((x, 0), ch, font=f, fill=255)
        x += f.getlength(ch) + tracking * size * ss
    cap_top = ascent - int(0.70 * size * ss)
    img = img.crop((0, cap_top, int(x), ascent + descent))
    img = img.resize((max(1, round(img.width / ss)), max(1, round(img.height / ss))), Image.LANCZOS)
    return np.asarray(img, dtype=np.float64) / 255.0


def paste(canvas, mask, x, y, color, alpha=1.0):
    """Lays [color] over [canvas] (sRGB floats) through [mask] at (x, y)."""
    h, w = mask.shape
    region = canvas[y:y + h, x:x + w]
    a = mask[: region.shape[0], : region.shape[1], None] * alpha
    region[:] = region * (1 - a) + np.asarray(color, dtype=np.float64) * a


def save(img, name, quality=92):
    os.makedirs(OUT, exist_ok=True)
    rng = np.random.default_rng(11)
    # Fine grain, as in a photograph, which also keeps the soft gradients from banding.
    grain = blur(rng.standard_normal(img.shape[:2]), 0.6)
    grain = grain / grain.std() * 1.3
    out = np.clip(img + grain[..., None], 0, 255).astype(np.uint8)
    path = os.path.join(OUT, name)
    Image.fromarray(out).save(path, quality=quality, optimize=True, progressive=True, subsampling=0)
    print(path, os.path.getsize(path) // 1024, "KB")


# The room ------------------------------------------------------------------------------------

class Tv:
    """A wall-hung TV: the screen's box (px), its frame and its corner."""

    def __init__(self, cx, cy, width, bezel):
        self.w = width
        self.h = width * 9 / 16
        self.x0, self.y0 = cx - self.w / 2, cy - self.h / 2
        self.x1, self.y1 = cx + self.w / 2, cy + self.h / 2
        self.bezel = bezel
        self.cx, self.cy = cx, cy

    @property
    def outer(self):
        b = self.bezel
        return self.x0 - b, self.y0 - b, self.x1 + b, self.y1 + b


def lamps(tv, colors, strength):
    """The lights behind the TV: left, above the middle, right, each a soft lamp hidden near the
    screen's edge and aimed outward a little."""
    (left, dim_l), (top, dim_t), (right, dim_r) = colors
    w, h = tv.w, tv.h
    return [
        ((tv.x0 + 0.10 * w, tv.cy - 0.04 * h), left * dim_l * strength, 0.22 * h, (-0.9, -0.45)),
        ((tv.cx, tv.y0 + 0.08 * h), top * dim_t * strength * 0.85, 0.24 * h, (0.0, -1.0)),
        ((tv.x1 - 0.10 * w, tv.cy - 0.04 * h), right * dim_r * strength, 0.22 * h, (0.9, -0.45)),
    ]


def wall(h, w, albedo, ambient, lights, seed=5):
    """The wall in linear light: matte paint ([albedo]) with a soft unevenness, lit by the room's
    [ambient] light (an array or a color) and by [lights] (position, color, spread, aim): each
    lamp's light falls off as a tight pool and a wide, faint spill."""
    y, x = np.mgrid[0:h, 0:w] + 0.5
    mottle = noise(h, w, 90, seed) * 0.010 + noise(h, w, 22, seed + 1) * 0.004
    irradiance = np.broadcast_to(np.asarray(ambient, dtype=np.float64), (h, w, 3)).copy()
    for (px, py), color, spread, (ax, ay) in lights:
        dx, dy = x - px, y - py
        d = np.hypot(dx, dy) + 1e-6
        pool = (1 + (d / spread) ** 2) ** -1.5
        spill = (1 + (d / (spread * 2.6)) ** 2) ** -1.5
        falloff = 0.72 * pool + 0.28 * spill
        aim = np.clip(1 + 0.5 * (dx * ax + dy * ay) / d, 0.3, None)
        irradiance += (falloff * aim)[..., None] * color
    return np.asarray(albedo, dtype=np.float64) * (1 + mottle)[..., None] * irradiance


def tv_frame(img, tv, screen, glow_lin, sheen=0.05, window=0.0):
    """Sets the TV into [img] (sRGB floats): a slim graphite frame whose outer rim catches the
    light around it ([glow_lin], the wall's light in linear terms), and the screen behind glass
    with a faint sheen and, by day, the soft reflection of a window ([window], its strength)."""
    h, w = img.shape[:2]
    outer = rounded_box(h, w, tv.outer, 5.0)
    inner = rounded_box(h, w, (tv.x0, tv.y0, tv.x1, tv.y1), 1.5)
    # The frame: graphite, a hair lighter toward its outer rim, the rim lit by the wall's light.
    rim = np.clip(outer - rounded_box(h, w, np.add(tv.outer, (2.2, 2.2, -2.2, -2.2)), 3.8), 0, 1)
    frame = np.full((h, w, 3), 17.0)
    lit = to_srgb(tonemap(glow_lin))
    frame = frame + rim[..., None] * np.clip(lit * 0.55 - 6.0, 0, None)
    img[:] = img * (1 - outer[..., None]) + frame * outer[..., None]
    # The screen.
    sw, sh = int(round(tv.x1 - tv.x0)), int(round(tv.y1 - tv.y0))
    shot = load(screen, (sw, sh))
    ys, xs = int(round(tv.y0)), int(round(tv.x0))
    region = img[ys:ys + sh, xs:xs + sw]
    m = inner[ys:ys + sh, xs:xs + sw, None]
    # Glass: a broad sheen from the upper left, fading across the screen.
    v, u = np.mgrid[0:sh, 0:sw]
    s = np.clip(1.0 - (u / sw * 0.85 + v / sh * 0.75), 0, 1) ** 2.0 * sheen * 255
    if window > 0:
        # A window far to the left: a tall, slanted pane of light, its bars dark, all out of focus.
        a = u / sw - 0.10 + (v / sh) * 0.22
        pane = ((a > 0.0) & (a < 0.24)).astype(np.float64) * (v / sh < 0.92)
        bars = (np.abs(a - 0.12) < 0.006) | (np.abs(v / sh - 0.40) < 0.006)
        pane = blur(pane * (1 - bars), 0.012 * sw) * np.clip(1.15 - v / sh, 0, 1)
        s = s + pane * window * 255
    region[:] = region * (1 - m) + np.clip(shot + s[..., None] * (1 - shot / 255.0), 0, 255) * m


# The pictures --------------------------------------------------------------------------------

def dark_room(W, H, tv, colors, strength, albedo=(118, 116, 114), ambient=0.006, cool=(0.92, 0.96, 1.0)):
    """A wall at night: the lamps behind the TV and a breath of light from the room, darker toward
    the picture's edges. Returns the image (sRGB floats) and the wall's light (linear)."""
    lights = lamps(tv, colors, strength)
    y, x = np.mgrid[0:H, 0:W] + 0.5
    r2 = ((x - tv.cx) / (W * 0.6)) ** 2 + ((y - tv.cy) / (H * 0.7)) ** 2
    room = (ambient * np.exp(-r2 * 1.3))[..., None] * np.array(cool)
    paint = to_linear(list(albedo))
    lin = wall(H, W, paint, room, lights)
    return to_srgb(tonemap(lin)), lin / paint


def wordmark(img, cx, top, cap, color=(236, 239, 244)):
    """The logo and "Klangbild" side by side, centered on [cx], the name's cap height from [top]."""
    word = text_mask("Klangbild", "jost.ttf", cap / 0.70, 400, tracking=-0.005)
    side = int(round(cap * 1.5))
    mark = np.asarray(logo(side, background=None), dtype=np.float64)
    gap = int(round(cap * 0.5))
    x0 = int(round(cx - (side + gap + word.shape[1]) / 2))
    y0 = int(round(top + cap / 2 - side / 2))
    a = mark[..., 3:4] / 255.0
    region = img[y0:y0 + side, x0:x0 + side]
    region[:] = region * (1 - a) + mark[..., :3] * a
    paste(img, word, x0 + side + gap, int(top), color, 0.96)


def now_playing():
    """The first picture: the song at night, the wall lit in its colors, the name above."""
    W, H = 2400, 1500
    tv = Tv(W / 2, 872, 1480, 9)
    img, light = dark_room(W, H, tv, light_colors("now-playing", vivid=1.5), 1.8)
    tv_frame(img, tv, os.path.join(RENDERS, "now-playing.png"), light * 0.16)
    wordmark(img, W / 2, 196, 54)
    save(img, "mockup-now-playing.jpg")


def window_view():
    """The window view at dusk: the room in the blue hour from a window far to the left, the
    lights in the view's own colors, dim as at night (its sky above, its fire on the right)."""
    W, H = 2400, 1350
    tv = Tv(W / 2, H / 2, 1600, 10)
    colors = light_colors("window-view")
    # The meadow's green on the left stays low beside the blue of the hour.
    colors[0] = (colors[0][0], colors[0][1] * 0.35)
    lights = lamps(tv, colors, 0.75)
    y, x = np.mgrid[0:H, 0:W] + 0.5
    dusk = 0.26 * (0.25 + 0.75 * np.clip(1.1 - x / W, 0, 1) ** 1.6) * (1.05 - 0.35 * y / H)
    paint = to_linear([130, 132, 140])
    lin = wall(H, W, paint, dusk[..., None] * np.array([0.42, 0.56, 1.0]), lights)
    img = to_srgb(tonemap(lin))
    tv_frame(img, tv, os.path.join(RENDERS, "window-view.png"), lin / paint * 0.16)
    save(img, "mockup-window-view.jpg")


def daylight_room(W, H, tv, seed=21):
    """A light wall by day: daylight from a window to the left, a soft band of sun across the wall
    with the shade of the window's bars in it, and the TV's own soft shadow. Returns the image
    (sRGB floats) and the light on the TV's rim (linear)."""
    y, x = np.mgrid[0:H, 0:W] + 0.5
    # Daylight: brighter toward the window, a little brighter toward the ceiling.
    sky = 0.70 + 0.26 * np.clip(1 - x / W, 0, 1) ** 1.4 + 0.08 * np.clip(1 - y / H, 0, 1)
    # The sun falls in a slanted band from the upper left, the window's bars across it.
    a = (x * 0.80 + y * 0.60) / W
    band = blur(((a > 0.18) & (a < 0.62)).astype(np.float64), 34)
    bars = blur(((np.abs(a - 0.40) < 0.010) | (np.abs((y * 0.80 - x * 0.60) / W - 0.05) < 0.010)).astype(np.float64), 10)
    sun = 0.55 * band * (1 - 0.65 * bars) * np.clip(1.35 - x / W, 0, 1)
    light = sky[..., None] * np.array([1.0, 0.985, 0.960]) + sun[..., None] * np.array([1.0, 0.93, 0.80])
    # The TV keeps some light off the wall below and right of it.
    shadow = blur(rounded_box(H, W, np.add(tv.outer, (20, 26, 20, 26)), 8.0), 22)
    light = light * (1 - 0.30 * shadow)[..., None]
    mottle = noise(H, W, 90, seed) * 0.008 + noise(H, W, 22, seed + 1) * 0.003
    paint = to_linear([240, 234, 224])
    lin = paint * (1 + mottle)[..., None] * light
    rim = (0.25 + 0.75 * np.clip(1.2 - x / W - 0.5 * y / H, 0, 1))[..., None] * np.ones(3) * 0.8
    return to_srgb(tonemap(lin * 1.25)), rim


def day_and_night():
    """One song twice: on the light wall by day, on the dark wall at night, side by side."""
    W, H = 1200, 1000
    tv = Tv(W / 2, H / 2 + 8, 1010, 5)
    day, rim = daylight_room(W, H, tv)
    tv_frame(day, tv, os.path.join(RENDERS, "day.png"), rim, sheen=0.03, window=0.10)
    night, light = dark_room(W, H, tv, light_colors("night"), 1.15)
    tv_frame(night, tv, os.path.join(RENDERS, "night.png"), light * 0.16)
    save(np.concatenate([day, night], axis=1), "day-and-night.jpg")


def download_button(scale=3):
    """The README's download button: the logo, then "Download for" over "Android TV", on night
    blue with a hairline edge that holds on light and dark pages alike. Shown 236 px wide."""
    w, h, r = 236 * scale, 68 * scale, 14 * scale
    ss = 4
    shape = Image.new("L", (w * ss, h * ss), 0)
    ImageDraw.Draw(shape).rounded_rectangle([0, 0, w * ss - 1, h * ss - 1], radius=r * ss, fill=255)
    edge = Image.new("L", (w * ss, h * ss), 0)
    ImageDraw.Draw(edge).rounded_rectangle([0, 0, w * ss - 1, h * ss - 1], radius=r * ss, outline=255, width=scale * ss)
    shape = np.asarray(shape.resize((w, h), Image.LANCZOS), dtype=np.float64) / 255.0
    edge = np.asarray(edge.resize((w, h), Image.LANCZOS), dtype=np.float64) / 255.0
    y, x = np.mgrid[0:h, 0:w] + 0.5
    # The logo and the words, centered on the button as a group.
    side = int(42 * scale)
    small = text_mask("Download for", "dmsans.ttf", 11.5 * scale, 460)
    large = text_mask("Android TV", "jost.ttf", 23 * scale, 430, tracking=-0.005)
    space = int(12 * scale)
    pad = int(round((w - (side + space + max(small.shape[1], large.shape[1]))) / 2))
    lx, ly = pad + side / 2, h / 2
    # Night blue, a breath lighter toward the top, and the logo's light behind it.
    base = np.array(NAVY, dtype=np.float64)
    top = np.array([13, 28, 62], dtype=np.float64)
    img = base + (top - base) * np.clip(1 - y / h, 0, 1)[..., None] ** 1.5
    glow = np.exp(-(((x - lx) / (side * 0.9)) ** 2 + ((y - ly) / (side * 0.9)) ** 2))
    img = img + glow[..., None] * np.array([18, 34, 70]) * 0.6
    img = img * (1 - edge[..., None] * 0.16) + np.array([140, 170, 220]) * edge[..., None] * 0.16
    mark = np.asarray(logo(side, background=None), dtype=np.float64)
    a = mark[..., 3:4] / 255.0
    my = int(round(ly - side / 2))
    img[my:my + side, pad:pad + side] = img[my:my + side, pad:pad + side] * (1 - a) + mark[..., :3] * a
    # The words as one block centered on the button: the small line's cap height to the large line's baseline.
    cap_small, cap_large = 0.70 * 11.5 * scale, 0.70 * 23 * scale
    gap = 7.5 * scale
    y0 = h / 2 - (cap_small + gap + cap_large) / 2
    tx = pad + side + space
    paste(img, small, tx, int(round(y0)), (168, 194, 232))
    paste(img, large, tx, int(round(y0 + cap_small + gap)), (236, 244, 255))
    rgba = np.dstack([np.clip(img, 0, 255), shape * 255]).astype(np.uint8)
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, "download.png")
    Image.fromarray(rgba, "RGBA").save(path, optimize=True)
    print(path, os.path.getsize(path) // 1024, "KB")


if __name__ == "__main__":
    now_playing()
    window_view()
    day_and_night()
    download_button()
