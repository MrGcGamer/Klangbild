#!/usr/bin/env python3
"""Turns the README renderings from the screenshot tests into docs/screens/.

    ./gradlew :app:testDebugUnitTest --tests '*Readme*' -Proborazzi.test.record=true
    python3 tools/make_readme_images.py

Stills become high-quality JPEGs (4:4:4, dark gradients survive), the track change an
animated GIF (dithered, 10 fps).
"""
import glob
import os

from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "app", "build", "screenshots")
DST = os.path.join(ROOT, "docs", "screens")


def stills():
    os.makedirs(DST, exist_ok=True)
    for path in sorted(glob.glob(os.path.join(SRC, "readme", "*.png"))):
        name = os.path.splitext(os.path.basename(path))[0]
        img = Image.open(path).convert("RGB")
        img.save(os.path.join(DST, name + ".jpg"), quality=92, subsampling=0, optimize=True)
        print("still", name)


def motion():
    frames = sorted(glob.glob(os.path.join(SRC, "readme-motion", "frame_*.png")))
    if not frames:
        return
    images = [Image.open(f).convert("RGB").resize((800, 450), Image.LANCZOS) for f in frames]
    # one shared palette from a few representative frames keeps colours steady
    sample = Image.new("RGB", (800, 450 * 3))
    for i, idx in enumerate([0, len(images) // 2, len(images) - 1]):
        sample.paste(images[idx], (0, 450 * i))
    palette = sample.quantize(colors=255, method=Image.Quantize.MEDIANCUT)
    quantized = [im.quantize(palette=palette, dither=Image.Dither.FLOYDSTEINBERG) for im in images]
    durations = [100] * len(quantized)
    durations[0] = 1200
    durations[-1] = 2500
    quantized[0].save(
        os.path.join(DST, "titelwechsel.gif"),
        save_all=True,
        append_images=quantized[1:],
        duration=durations,
        loop=0,
        optimize=False,
    )
    print("gif", len(quantized), "frames")


if __name__ == "__main__":
    stills()
    motion()
