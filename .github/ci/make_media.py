#!/usr/bin/env python3
"""Generates placeholder posters / logos / backdrops for the mock server."""
import os, colorsys
from PIL import Image, ImageDraw, ImageFont
MEDIA = os.environ.get("MEDIA_DIR", "/tmp/media")
os.makedirs(MEDIA, exist_ok=True)
font_path = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"
def font(size):
    try:
        return ImageFont.truetype(font_path, size)
    except Exception:
        return ImageFont.load_default()
def grad(w, h, hue):
    im = Image.new("RGB", (w, h))
    d = ImageDraw.Draw(im)
    for y in range(h):
        r, g, b = colorsys.hsv_to_rgb(hue, 0.65, 0.25 + 0.6 * (1 - y / h))
        d.line([(0, y), (w, y)], fill=(int(r * 255), int(g * 255), int(b * 255)))
    return im
for i in range(24):
    im = grad(400, 600, (i * 0.137) % 1)
    d = ImageDraw.Draw(im)
    d.text((30, 470), f"FILM {i + 1}", font=font(44), fill="white")
    im.save(f"{MEDIA}/poster{i}.png")
for i in range(4):
    im = grad(1280, 720, (i * 0.23 + 0.5) % 1)
    ImageDraw.Draw(im).ellipse([700, 100, 1200, 600], fill=(255, 255, 255, 40))
    im.save(f"{MEDIA}/backdrop{i}.png")
for i in range(20):
    im = Image.new("RGBA", (300, 180), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    r, g, b = colorsys.hsv_to_rgb((i * 0.09) % 1, 0.8, 0.95)
    d.rounded_rectangle([20, 30, 280, 150], radius=26, fill=(int(r * 255), int(g * 255), int(b * 255), 255))
    d.text((60, 62), f"TV{i + 1}", font=font(52), fill="white")
    im.save(f"{MEDIA}/logo{i}.png")
