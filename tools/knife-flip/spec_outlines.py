"""Dimensioned side outlines for the five knives.

These are line drawings of the proportions in KNIFE_CONSTRUCTION.md, not renders
of the meshes. Run this file to write one PNG per knife.
"""

import math
import os

import blade_profiles as blades
from PIL import Image, ImageDraw, ImageFont

FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf"


def font(size):
    return ImageFont.truetype(FONT, size)


def chef_outline():
    """Points in millimetres. +x toward the tip, +y toward the spine. Heel at x=0."""
    top = []
    bot = []
    # Butt to the heel, along the spine side, then the blade spine out to the tip.
    for i in range(40):
        u = i / 39
        x = -130 + 130 * u
        # 3 mm drop at the butt, back to the spine at the heel. Small flare at the end.
        drop = 3.0 * (1 - u) * (1 - u)
        if u > 0.92:
            drop -= 0.8 * (u - 0.92) / 0.08
        top.append((x, 48 - drop))
    for i in range(80):
        s = blades.CHEF_LENGTH * i / 79
        spine, _edge = blades.chef_heights(s)
        top.append((s, spine))
    # Tip back along the edge to the heel, then the choil up to the handle.
    for i in range(80, -1, -1):
        s = blades.CHEF_LENGTH * i / 80
        _spine, edge = blades.chef_heights(s)
        bot.append((s, edge))
    # Quarter-ellipse choil, 18 mm, up to 22 mm, then the handle bottom.
    for i in range(1, 24):
        u = i / 23
        x = -18 * u
        y = 22 * math.sqrt(max(0.0, 2 * u - u * u))
        bot.append((x, y))
    girth = [(-18, 26), (-52, 30), (-82, 32), (-112, 30), (-124, 29), (-130, 28.5)]
    for i in range(len(girth) - 1):
        (x0, g0), (x1, g1) = girth[i], girth[i + 1]
        for k in range(1, 9):
            u = k / 8
            x = x0 + (x1 - x0) * u
            g = g0 + (g1 - g0) * u
            # Top at this x is already in `top`; the bottom is top minus girth.
            drop = 3.0 * ((-x) / 130) ** 2
            bot.append((x, 48 - drop - g))
    return top + bot


def throw_outline():
    pts = []
    for i in range(120):
        s = blades.THROW_TOTAL * i / 119
        half = blades.throw_half_width(s)
        pts.append((blades.THROW_TOTAL - s, 14 + half))
    for i in range(119, -1, -1):
        s = blades.THROW_TOTAL * i / 119
        half = blades.throw_half_width(s)
        pts.append((blades.THROW_TOTAL - s, 14 - half))
    return pts


def pocket_outline():
    top, bot = [], []
    for i in range(24):
        u = i / 23
        x = -116 + 116 * u
        top.append((x, 24.0 + 0.4 * (1 - u) ** 2))
    for i in range(60):
        s = blades.POCKET_LENGTH * i / 59
        spine, _edge = blades.pocket_heights(s)
        top.append((s, spine))
    for i in range(59, -1, -1):
        s = blades.POCKET_LENGTH * i / 59
        _spine, edge = blades.pocket_heights(s)
        bot.append((s, edge))
    for i in range(23, -1, -1):
        u = i / 23
        x = -116 + 116 * u
        bot.append((x, top[i][1] - (26.0 - 3.0 * (1 - u))))
    return top + bot


def fly_outline():
    top, bot = [], []
    for i in range(28):
        u = i / 27
        x = -142 + 142 * u
        # Meets the spine at the pivot. The extra height is toward the butt.
        top.append((x, 22.0 + 1.5 * (1 - u) ** 2))
    for i in range(50):
        s = blades.FLY_LENGTH * i / 49
        spine, _edge = blades.fly_heights(s)
        top.append((s, spine))
    for i in range(49, -1, -1):
        s = blades.FLY_LENGTH * i / 49
        _spine, edge = blades.fly_heights(s)
        bot.append((s, edge))
    for i in range(27, -1, -1):
        u = i / 27
        x = -142 + 142 * u
        bot.append((x, -1.5))
    return top + bot


def cleaver_outline():
    top, bot = [], []
    for i in range(24):
        u = i / 23
        x = -120 + 120 * u
        top.append((x, 90 - 2.6 * (1 - u) ** 2))
    for i in range(50):
        s = blades.CLEAVER_LENGTH * i / 49
        spine, edge = blades.cleaver_heights(s)
        top.append((s, spine))
    for i in range(49, -1, -1):
        s = blades.CLEAVER_LENGTH * i / 49
        _spine, edge = blades.cleaver_heights(s)
        bot.append((s, edge))
    # Handle bottom, about 33 mm under the spine, at the top of the heel.
    for i in range(23, -1, -1):
        u = i / 23
        x = -120 + 120 * u
        bot.append((x, top[i][1] - 33))
    return top + bot


def draw_knife(name, pts, dims, note, out_path, width=980, height=640):
    img = Image.new("RGB", (width, height), (250, 249, 246))
    draw = ImageDraw.Draw(img)
    title = font(22)
    label = font(15)
    small = font(13)
    xs = [p[0] for p in pts]
    ys = [p[1] for p in pts]
    min_x, max_x = min(xs), max(xs)
    min_y, max_y = min(ys), max(ys)
    pad_l, pad_r, pad_t, pad_b = 70, 40, 78, 150
    span_x = max_x - min_x
    span_y = max(max_y - min_y, 1)
    scale = min((width - pad_l - pad_r) / span_x, (height - pad_t - pad_b) / span_y)

    def xy(p):
        return (pad_l + (p[0] - min_x) * scale, height - pad_b - (p[1] - min_y) * scale)

    # Faint centre / edge reference, then the outline.
    edge_y = xy((0, 0))[1]
    draw.line([(pad_l, edge_y), (width - pad_r, edge_y)], fill=(220, 216, 208), width=1)
    outline = [xy(p) for p in pts]
    draw.line(outline + [outline[0]], fill=(28, 26, 24), width=3, joint="curve")

    ink = (70, 78, 92)
    for dim in dims:
        kind = dim[0]
        if kind == "h":
            _, x0, x1, y, text = dim
            ypx = xy((0, y))[1]
            a, b = xy((x0, y))[0], xy((x1, y))[0]
            draw.line([(a, ypx), (b, ypx)], fill=ink, width=1)
            for x in (a, b):
                draw.line([(x, ypx - 5), (x, ypx + 5)], fill=ink, width=1)
            draw.line([(a, ypx), (a + 8, ypx - 3), (a + 8, ypx + 3)], fill=ink)
            draw.line([(b, ypx), (b - 8, ypx - 3), (b - 8, ypx + 3)], fill=ink)
            tw = draw.textlength(text, font=label)
            draw.text(((a + b) / 2 - tw / 2, ypx - 22), text, fill=ink, font=label)
        elif kind == "v":
            _, x, y0, y1, text = dim
            xpx = xy((x, 0))[0]
            a, b = xy((x, y0))[1], xy((x, y1))[1]
            top, bot = min(a, b), max(a, b)
            draw.line([(xpx, top), (xpx, bot)], fill=ink, width=1)
            for y in (top, bot):
                draw.line([(xpx - 5, y), (xpx + 5, y)], fill=ink, width=1)
            tw = draw.textlength(text, font=label)
            draw.text((xpx + 8, (top + bot) / 2 - 8), text, fill=ink, font=label)

    draw.text((28, 22), name, fill=(24, 22, 20), font=title)
    draw.text((28, 52), note, fill=(90, 86, 80), font=small)
    img.save(out_path)
    print("wrote", out_path)


def main():
    out = os.environ.get("OUTLINE_DIR", "/opt/cursor/artifacts/knife-flip-filament-v6")
    os.makedirs(out, exist_ok=True)
    draw_knife(
        "Chef's knife",
        chef_outline(),
        [
            ("h", -130, 200, -18, "330 mm overall"),
            ("h", 0, 200, -32, "blade 200 mm"),
            ("h", -130, 0, 58, "handle 130 mm"),
            ("v", 210, 0, 48, "48 mm"),
            ("v", -70, 16, 48, "32 mm"),
        ],
        "Spine into the handle. Bolster 8.6 mm, spine 2.7 mm at the heel to 1.0 mm at the tip.",
        os.path.join(out, "outline-chef.png"),
    )
    draw_knife(
        "Throwing knife",
        throw_outline(),
        [
            ("h", 0, 280, -8, "280 mm, balance at the middle"),
            ("h", 140, 280, 32, "blade 140 mm"),
            ("v", -12, 4, 24, "28 mm"),
        ],
        "One bar. Handle on the centreline, symmetric. Stock 5 mm.",
        os.path.join(out, "outline-throwing.png"),
    )
    draw_knife(
        "Pocket knife",
        pocket_outline(),
        [
            ("h", -116, 85, -10, "handle 116 mm  ·  blade 85 mm"),
            ("v", 96, 0, 24, "24 mm"),
        ],
        "Handle back on the blade spine. The edge sits inside the handle.",
        os.path.join(out, "outline-pocket.png"),
    )
    draw_knife(
        "Butterfly knife",
        fly_outline(),
        [
            ("h", -142, 100, -12, "handles 142 mm  ·  blade 100 mm"),
            ("v", 112, 0, 22, "22 mm"),
        ],
        "Blade centred between the two handles. Kicker on the spine at the pivot.",
        os.path.join(out, "outline-butterfly.png"),
    )
    draw_knife(
        "Cleaver",
        cleaver_outline(),
        [
            ("h", -120, 180, -14, "300 mm overall"),
            ("h", 0, 180, -28, "blade 180 mm"),
            ("v", 192, 0, 90, "90 mm"),
            ("v", -40, 57, 90, "33 mm"),
        ],
        "Handle at the top of the heel, top flush with the spine. Eye at the top front.",
        os.path.join(out, "outline-cleaver.png"),
    )


if __name__ == "__main__":
    main()
