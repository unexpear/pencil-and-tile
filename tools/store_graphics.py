"""Draws the Play Store icon (512×512) and feature graphic (1024×500) from the launcher icon's geometry
(app/src/main/res/drawable/ic_sudoku.xml, 108-unit viewport). Run from the repository root."""
import math
from PIL import Image, ImageDraw, ImageFont

NAVY, GOLD, WHITE = (0x17, 0x3B, 0x55), (0xE9, 0xAA, 0x45), (255, 255, 255)


def draw_icon(d, ox, oy, scale):
    P = lambda x, y: (ox + x * scale, oy + y * scale)
    d.rectangle([P(30, 30), P(78, 78)], fill=WHITE)
    for a, b, c, e in [(32, 32, 44, 44), (64, 48, 76, 60), (48, 64, 60, 76)]:
        d.rectangle([P(a, b), P(c, e)], fill=GOLD)

    def segment(p, q, width, round_ends):
        (x1, y1), (x2, y2) = P(*p), P(*q)
        h = width * scale / 2
        dx, dy = x2 - x1, y2 - y1
        n = math.hypot(dx, dy) or 1
        nx, ny = -dy / n * h, dx / n * h
        d.polygon([(x1 + nx, y1 + ny), (x2 + nx, y2 + ny), (x2 - nx, y2 - ny), (x1 - nx, y1 - ny)], fill=NAVY)
        if round_ends:
            for x, y in ((x1, y1), (x2, y2)): d.ellipse([x - h, y - h, x + h, y + h], fill=NAVY)

    for k in (46, 62):
        segment((k, 30), (k, 78), 2, False)
        segment((30, k), (78, k), 2, False)
    for pts in ([(51, 36), (54, 34), (54, 42)], [(35, 54), (41, 54)], [(70, 67), (70, 73)], [(67, 70), (73, 70)]):
        for p, q in zip(pts, pts[1:]): segment(p, q, 3, True)


def icon(path):
    big = Image.new("RGB", (2048, 2048), NAVY)
    s = 2048 / 68  # viewport 20..88 fills the square
    draw_icon(ImageDraw.Draw(big), -20 * s, -20 * s, s)
    big.resize((512, 512), Image.LANCZOS).save(path)


def feature(path):
    W, H = 2048, 1000
    img = Image.new("RGB", (W, H), NAVY)
    d = ImageDraw.Draw(img)
    s = 700 / 48
    draw_icon(d, 140 - 30 * s, (H - 700) / 2 - 30 * s, s)
    title = ImageFont.truetype("C:/Windows/Fonts/segoeuib.ttf", 150)
    sub = ImageFont.truetype("C:/Windows/Fonts/segoeui.ttf", 76)
    d.text((960, 320), "Pencil & Tile", font=title, fill=WHITE)
    d.text((966, 540), "32 offline puzzles", font=sub, fill=(0xF4, 0xD5, 0x8D))
    d.text((966, 640), "No ads · no account", font=sub, fill=(0xC9, 0xD6, 0xE0))
    assert d.textbbox((960, 320), "Pencil & Tile", font=title)[2] < W - 60, "title too wide"
    img.resize((1024, 500), Image.LANCZOS).save(path)


if __name__ == "__main__":
    icon("docs/store/graphics/icon-512.png")
    feature("docs/store/graphics/feature-1024x500.png")
    print("ok")
