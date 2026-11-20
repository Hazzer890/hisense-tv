"""Turn wallpaper-src/*.jpg into 1920x1080 backgrounds in assets/.

The top of each stays sharp (the "shelf"); the lower part fades into a blurred,
darkened copy so app tiles sit on frosted glass. Done here so the TV does no blurring.
usage: python wallpaper.py
"""
from pathlib import Path
from PIL import Image, ImageFilter, ImageEnhance

W, H = 1920, 1080
FADE_START, FADE_END = 0.36, 0.60   # where sharp turns into frosted, as a fraction of height
# Vertical crop position per photo: 0 = keep the top, 1 = keep the bottom.
ANCHOR = {"1.jpg": 0.35, "2.jpg": 0.82}

here = Path(__file__).parent
(here / "assets").mkdir(exist_ok=True)

mask = Image.new("L", (1, H))
for y in range(H):
    t = (y / H - FADE_START) / (FADE_END - FADE_START)
    mask.putpixel((0, y), int(255 * min(1, max(0, t))))
mask = mask.resize((W, H))

for i, src in enumerate(sorted((here / "wallpaper-src").glob("*.jpg"))):
    im = Image.open(src).convert("RGB")
    scale = max(W / im.width, H / im.height)
    im = im.resize((round(im.width * scale), round(im.height * scale)), Image.LANCZOS)
    top = round((im.height - H) * ANCHOR.get(src.name, 0.5))
    left = (im.width - W) // 2
    im = im.crop((left, top, left + W, top + H))

    frosted = ImageEnhance.Brightness(im.filter(ImageFilter.GaussianBlur(36))).enhance(0.45)
    out = Image.composite(frosted, ImageEnhance.Brightness(im).enhance(0.92), mask)
    dest = here / "assets" / f"bg{i}.jpg"
    out.save(dest, quality=88)
    print(dest.name, out.size, dest.stat().st_size // 1024, "KB")

    # Screensaver backdrop: the whole photo blurred. Saved small; blur hides the upscale.
    saver = ImageEnhance.Brightness(im.filter(ImageFilter.GaussianBlur(32))).enhance(0.6)
    saver.resize((W // 4, H // 4), Image.LANCZOS).save(here / "assets" / f"blur{i}.jpg", quality=90)
