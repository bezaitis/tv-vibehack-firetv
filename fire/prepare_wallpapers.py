#!/usr/bin/env python3
"""Turn a folder of photos into TV wallpapers: wallpaper-1.jpg, wallpaper-2.jpg, ... in file-name order.

Each photo is turned upright, cropped to 16:9 (keeping more of the top, where faces usually are), resized to
1920x1080, dimmed and darkened toward the bottom so the clock, app names and dock stay readable, and saved
under 300 KB. iPhone HEIC photos work when pillow-heif is installed (fire/theme.sh installs it).

Usage: prepare_wallpapers.py <photos folder> <output folder> [brightness 0.2-1.0, default 0.62]
"""
import os
import sys

from PIL import Image, ImageEnhance, ImageOps

try:
    from pillow_heif import register_heif_opener
    register_heif_opener()
except ImportError:
    pass

EXTS = {".jpg", ".jpeg", ".png", ".heic", ".heif", ".webp"}
MAX = 30   # the launcher shows at most 30
W, H = 1920, 1080


def bottom_shade():
    """Alpha mask: clear in the top 40%, then up to 55% black at the bottom edge."""
    col = Image.new("L", (1, H))
    for y in range(H):
        t = max(0.0, (y - 430) / 650)
        col.putpixel((0, y), int(255 * min(0.55, 0.55 * t ** 1.2)))
    return col.resize((W, H))


def prepare(path, out, brightness, shade):
    im = ImageOps.exif_transpose(Image.open(path)).convert("RGB")
    w, h = im.size
    if w / h > W / H:                       # wider than 16:9: trim the sides evenly
        cw = round(h * W / H)
        left = (w - cw) // 2
        im = im.crop((left, 0, left + cw, h))
    else:                                   # taller: keep the upper part, where faces usually are
        ch = round(w * H / W)
        top = round((h - ch) * 0.3)
        im = im.crop((0, top, w, top + ch))
    im = im.resize((W, H), Image.LANCZOS)
    im = ImageEnhance.Brightness(im).enhance(brightness)
    im = Image.composite(Image.new("RGB", (W, H)), im, shade)
    for q in (82, 76, 70, 64, 58):
        im.save(out, quality=q, optimize=True, progressive=True)
        if os.path.getsize(out) < 300_000:
            break


def main():
    src, dst = sys.argv[1], sys.argv[2]
    brightness = float(sys.argv[3]) if len(sys.argv) > 3 else 0.62
    brightness = min(1.0, max(0.2, brightness))
    photos = sorted(f for f in os.listdir(src)
                    if not f.startswith(".") and os.path.splitext(f)[1].lower() in EXTS)
    if not photos:
        sys.exit(f"No photos in {src} (jpg, png, heic, webp)")
    if len(photos) > MAX:
        print(f"{len(photos)} photos; the launcher shows the first {MAX}.")
        photos = photos[:MAX]
    os.makedirs(dst, exist_ok=True)
    shade = bottom_shade()
    n = 0
    for name in photos:
        try:
            n += 1
            prepare(os.path.join(src, name), os.path.join(dst, f"wallpaper-{n}.jpg"), brightness, shade)
            print(f"  {name} -> wallpaper-{n}.jpg")
        except Exception as e:   # one unreadable photo should not stop the rest
            n -= 1
            print(f"  {name}: skipped ({e})")
    if n == 0:
        sys.exit("None of the photos could be read.")


if __name__ == "__main__":
    main()
