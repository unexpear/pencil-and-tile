"""Blade outlines in millimetres, independent of Blender.

Each knife is an explicit spine curve and an explicit edge curve. s = 0 is the
heel (or the throwing knife's tip, documented on that function) and the height
is millimetres up from the heel's edge line toward the spine. The mesh maps
that height onto Blender X with the edge at +X and the spine at -X, so a picker
rotation of +π/2 lays the edge on the shelf.

Run this file directly to draw the silhouette sheet and to fail if a curve
drifts off the measurements.
"""

import math
import os

# Millimetres. Chef: western 8 inch, blade 200, heel 48, tip one third of that
# up from the edge line.
CHEF_LENGTH = 200.0
CHEF_HEEL = 48.0
CHEF_TIP = CHEF_HEEL / 3.0  # 16 mm, not mid-height
CHEF_SPINE_FLAT = 0.65 * CHEF_LENGTH  # 130 mm
CHEF_EDGE_STRAIGHT = 0.40 * CHEF_LENGTH  # 80 mm

POCKET_LENGTH = 85.0
POCKET_HEIGHT = 24.0
POCKET_TIP = 14.0  # centreline is 12; the drop point sits just above it
POCKET_SPINE_FLAT = 0.65 * POCKET_LENGTH

FLY_LENGTH = 100.0
FLY_HEIGHT = 22.0
FLY_TIP = 4.0
FLY_CLIP = 0.68 * FLY_LENGTH  # straight spine, then a straight clip

CLEAVER_LENGTH = 180.0
CLEAVER_HEIGHT = 90.0
CLEAVER_BELLY = 5.0  # the edge lifts only at the forward corner

THROW_TOTAL = 280.0
THROW_BLADE = 140.0  # ~130 mm; the physics length stays 280 mm and centred
THROW_WIDTH = 28.0


def hermite(s, s0, s1, h0, h1, m0, m1):
    """Cubic Hermite. m0 and m1 are dh/ds in millimetres per millimetre."""
    span = s1 - s0
    u = 0.0 if span == 0 else (s - s0) / span
    u2 = u * u
    u3 = u2 * u
    h00 = 2 * u3 - 3 * u2 + 1
    h10 = u3 - 2 * u2 + u
    h01 = -2 * u3 + 3 * u2
    h11 = u3 - u2
    return h00 * h0 + h10 * (m0 * span) + h01 * h1 + h11 * (m1 * span)


def chef_heights(s):
    """Spine and edge heights, s millimetres from the heel toward the tip."""
    s = max(0.0, min(CHEF_LENGTH, s))
    if s <= CHEF_SPINE_FLAT:
        spine = CHEF_HEEL
    else:
        # Horizontal leave from the straight spine, then a steep arrival at the
        # tip, so the drop stays above its chord (a convex spine).
        spine = hermite(s, CHEF_SPINE_FLAT, CHEF_LENGTH, CHEF_HEEL, CHEF_TIP, 0.0, -0.85)
    # Straight edge to the heel. The finger guard is the bolster curve behind the
    # heel, not a notch cut into the edge.
    if s <= CHEF_EDGE_STRAIGHT:
        edge = 0.0
    else:
        edge = hermite(s, CHEF_EDGE_STRAIGHT, CHEF_LENGTH, 0.0, CHEF_TIP, 0.0, 0.42)
    return spine, edge


def pocket_heights(s):
    """Drop-point folder. s from the pivot heel toward the tip."""
    s = max(0.0, min(POCKET_LENGTH, s))
    if s <= POCKET_SPINE_FLAT:
        spine = POCKET_HEIGHT
    else:
        spine = hermite(s, POCKET_SPINE_FLAT, POCKET_LENGTH, POCKET_HEIGHT, POCKET_TIP, 0.0, -0.55)
    # Ricasso, a small choil, a straight edge, then the belly up to the tip.
    if s <= 8.0:
        edge = 1.2
    elif s <= 14.0:
        u = (s - 8.0) / 6.0
        edge = 1.2 + 3.6 * math.sin(math.pi * u)
    elif s <= 42.0:
        edge = 0.4
    else:
        edge = hermite(s, 42.0, POCKET_LENGTH, 0.4, POCKET_TIP, 0.0, 0.48)
    return spine, edge


def fly_heights(s):
    """Balisong clip point. The edge is the low side only; the clip is a straight cut."""
    s = max(0.0, min(FLY_LENGTH, s))
    if s <= FLY_CLIP:
        spine = FLY_HEIGHT
    else:
        u = (s - FLY_CLIP) / (FLY_LENGTH - FLY_CLIP)
        spine = FLY_HEIGHT + (FLY_TIP - FLY_HEIGHT) * u
    if s <= 62.0:
        edge = 0.0
    else:
        u = (s - 62.0) / (FLY_LENGTH - 62.0)
        edge = FLY_TIP * (u * u * (3 - 2 * u))
    return spine, edge


def cleaver_heights(s):
    """Rectangle with a straight spine and a slight belly only at the forward corner."""
    s = max(0.0, min(CLEAVER_LENGTH, s))
    spine = CLEAVER_HEIGHT
    if s <= 150.0:
        edge = 0.0
    else:
        u = (s - 150.0) / (CLEAVER_LENGTH - 150.0)
        edge = CLEAVER_BELLY * u * u
    return spine, edge


def throw_half_width(from_tip):
    """Half-width of a straight spear. from_tip is millimetres from the point.

    The edges are straight, so this is not a lens. The handle is the same bar,
    narrower, with a rounded butt.
    """
    s = max(0.0, min(THROW_TOTAL, from_tip))
    if s <= THROW_BLADE:
        return 0.35 + (THROW_WIDTH * 0.5 - 0.35) * (s / THROW_BLADE)
    if s <= THROW_BLADE + 16.0:
        u = (s - THROW_BLADE) / 16.0
        return THROW_WIDTH * 0.5 + (10.2 - THROW_WIDTH * 0.5) * u
    if s <= THROW_TOTAL - 16.0:
        return 10.2 + 0.6 * math.sin((s - THROW_BLADE) / 40.0)
    u = (s - (THROW_TOTAL - 16.0)) / 16.0
    return 10.2 + (5.0 - 10.2) * u


def _xs(spine_h, edge_h):
    """Mesh X in metres. +X is the edge, -X is the spine, edge line at 0."""
    return -spine_h / 1000.0, -edge_h / 1000.0


def chef_station(from_tip_m):
    s = CHEF_LENGTH - from_tip_m * 1000.0
    spine, edge = chef_heights(s)
    # from_tip 0 is the tip. Heel is about 2.7 mm, tip about 1.0 mm.
    u = min(1.0, max(0.0, from_tip_m * 1000.0 / CHEF_LENGTH))
    thick = (1.0 + 1.7 * u) / 1000.0
    return (*_xs(spine, edge), thick)


def pocket_station(from_tip_m):
    s = POCKET_LENGTH - from_tip_m * 1000.0
    spine, edge = pocket_heights(s)
    # Ricasso, the first 8 mm at the heel, stays blunt.
    from_heel = s
    thick = (2.4 - 1.3 * (from_heel / POCKET_LENGTH)) / 1000.0
    blunt = from_heel <= 8.0
    return (*_xs(spine, edge), thick, blunt)


def fly_station(from_tip_m):
    s = FLY_LENGTH - from_tip_m * 1000.0
    spine, edge = fly_heights(s)
    thick = (2.6 - 1.2 * (s / FLY_LENGTH)) / 1000.0
    return (*_xs(spine, edge), thick)


def cleaver_station(from_tip_m):
    s = CLEAVER_LENGTH - from_tip_m * 1000.0
    spine, edge = cleaver_heights(s)
    # 5 mm at the heel, easing to about 3.6 mm at the tip.
    u = min(1.0, max(0.0, from_tip_m * 1000.0 / CLEAVER_LENGTH))
    thick = (3.6 + 1.4 * u) / 1000.0
    return (*_xs(spine, edge), thick)


def throw_station(from_tip_m):
    half = throw_half_width(from_tip_m * 1000.0) / 1000.0
    # Full 5 mm on the blade. Under the cord the steel steps in so the wrap is proud
    # of the bar and still inside the 5 mm stock.
    if from_tip_m >= (THROW_BLADE + 8.0) / 1000.0 and from_tip_m <= (THROW_TOTAL - 12.0) / 1000.0:
        thick = 0.0032
    else:
        # Taper the point and the butt. The middle of the blade is the full stock.
        s = from_tip_m * 1000.0
        if s < 18.0:
            thick = 0.005 * (0.35 + 0.65 * s / 18.0)
        elif s > THROW_TOTAL - 14.0:
            u = (THROW_TOTAL - s) / 14.0
            thick = 0.005 * (0.45 + 0.55 * u)
        else:
            thick = 0.005
    return -half, half, thick


def samples(height_fn, length, n=240):
    out = []
    for i in range(n):
        s = length * i / (n - 1)
        spine, edge = height_fn(s)
        out.append((s, spine, edge))
    return out


def check():
    """Raise SystemExit if a silhouette leaves the measurements."""
    errors = []

    def expect(ok, msg):
        if not ok:
            errors.append(msg)

    chef = samples(chef_heights, CHEF_LENGTH, 400)
    for s, spine, edge in chef:
        if s <= CHEF_SPINE_FLAT:
            expect(abs(spine - CHEF_HEEL) < 0.05, f"chef spine not straight at {s:.0f}: {spine:.2f}")
        if 12 <= s <= CHEF_EDGE_STRAIGHT:
            expect(abs(edge) < 0.45, f"chef edge not straight at {s:.0f}: {edge:.2f}")
        expect(edge >= -0.05, f"chef edge dipped below the line at {s:.0f}")
        expect(spine + 0.05 >= edge, f"chef spine crossed the edge at {s:.0f}")
    expect(abs(chef[-1][1] - CHEF_TIP) < 0.05 and abs(chef[-1][2] - CHEF_TIP) < 0.05, "chef tip")
    # Convex spine: above the chord from the end of the flat to the tip.
    for s, spine, _ in chef:
        if CHEF_SPINE_FLAT < s < CHEF_LENGTH:
            u = (s - CHEF_SPINE_FLAT) / (CHEF_LENGTH - CHEF_SPINE_FLAT)
            chord = CHEF_HEEL + (CHEF_TIP - CHEF_HEEL) * u
            expect(spine >= chord - 0.4, f"chef spine not convex at {s:.0f}: {spine:.2f} < {chord:.2f}")
    # Belly: the edge stays on or below the chord from the straight section to the tip.
    for s, _, edge in chef:
        if CHEF_EDGE_STRAIGHT < s < CHEF_LENGTH:
            u = (s - CHEF_EDGE_STRAIGHT) / (CHEF_LENGTH - CHEF_EDGE_STRAIGHT)
            chord = CHEF_TIP * u
            expect(edge <= chord + 0.4, f"chef belly rose above its chord at {s:.0f}")

    throw_w = [throw_half_width(THROW_BLADE * i / 40) for i in range(41)]
    for i in range(1, len(throw_w)):
        expect(throw_w[i] + 1e-6 >= throw_w[i - 1], "throwing spear narrowed before the shoulder")
    expect(abs(throw_half_width(THROW_BLADE) * 2 - THROW_WIDTH) < 0.2, "throwing width")
    expect(throw_half_width(0.0) < 0.6, "throwing tip")

    pocket = samples(pocket_heights, POCKET_LENGTH, 300)
    for s, spine, edge in pocket:
        if s <= POCKET_SPINE_FLAT - 0.5:
            expect(abs(spine - POCKET_HEIGHT) < 0.05, f"pocket spine at {s:.0f}")
        expect(spine + 0.05 >= edge, f"pocket crossed at {s:.0f}")
    tip_s, tip_h = pocket[-1][1], pocket[-1][2]
    expect(abs(tip_s - POCKET_TIP) < 0.05 and abs(tip_h - POCKET_TIP) < 0.05, "pocket tip")
    expect(POCKET_TIP > POCKET_HEIGHT * 0.5, "pocket tip should be above the centreline")

    fly = samples(fly_heights, FLY_LENGTH, 200)
    for s, spine, edge in fly:
        if s <= FLY_CLIP:
            expect(abs(spine - FLY_HEIGHT) < 0.05, f"fly spine at {s:.0f}")
        if s <= 60:
            expect(abs(edge) < 0.05, f"fly edge at {s:.0f}")
        expect(spine > edge + 0.4 or s > FLY_LENGTH - 4, f"fly not single-edged at {s:.0f}: {spine:.2f}/{edge:.2f}")
    expect(abs(fly[-1][1] - FLY_TIP) < 0.05, "fly tip")

    cleaver = samples(cleaver_heights, CLEAVER_LENGTH, 200)
    for s, spine, edge in cleaver:
        expect(abs(spine - CLEAVER_HEIGHT) < 0.05, f"cleaver spine at {s:.0f}")
        expect(0.0 <= edge <= CLEAVER_BELLY + 0.05, f"cleaver edge at {s:.0f}: {edge:.2f}")
    expect(cleaver[-1][2] > 3.0, "cleaver forward belly")

    if errors:
        raise SystemExit("blade profile check failed:\n" + "\n".join(errors))
    print("blade profile check ok")
    print(f"  chef tip {CHEF_TIP:.1f} mm above the edge ({CHEF_TIP / CHEF_HEEL:.2f} of the heel)")
    print(f"  pocket tip {POCKET_TIP:.1f} mm, centreline {POCKET_HEIGHT / 2:.1f} mm")


def _poly(draw, pts, fill=None, outline=None, width=2):
    if len(pts) < 2:
        return
    if fill is not None:
        draw.polygon(pts, fill=fill)
    if outline is not None:
        draw.line(pts + [pts[0]], fill=outline, width=width)


def render_sheet(path):
    from PIL import Image, ImageDraw

    check()
    scale = 2.7
    pad = 28
    rows = [
        ("Chef  200 x 48 mm   spine flat 130 mm, tip at 16 mm", chef_heights, CHEF_LENGTH, CHEF_HEEL,
         [(0, CHEF_HEEL), (CHEF_SPINE_FLAT, CHEF_HEEL), (CHEF_LENGTH, CHEF_TIP),
          (CHEF_EDGE_STRAIGHT, 0), (0, 0)]),
        ("Throwing  spear 140 mm of 280 mm, 28 mm wide, straight edges", None, THROW_TOTAL, 16,
         None),
        ("Pocket  85 x 24 mm   drop point, tip at 14 mm (centre 12)", pocket_heights, POCKET_LENGTH, POCKET_HEIGHT,
         [(0, POCKET_HEIGHT), (POCKET_SPINE_FLAT, POCKET_HEIGHT), (POCKET_LENGTH, POCKET_TIP),
          (42, 0.4), (14, 0.4), (0, 1.2)]),
        ("Butterfly  100 x 22 mm   clip point, edge on one side", fly_heights, FLY_LENGTH, FLY_HEIGHT,
         [(0, FLY_HEIGHT), (FLY_CLIP, FLY_HEIGHT), (FLY_LENGTH, FLY_TIP), (62, 0), (0, 0)]),
        ("Cleaver  180 x 90 mm   straight spine, slight forward belly", cleaver_heights, CLEAVER_LENGTH, CLEAVER_HEIGHT,
         [(0, CLEAVER_HEIGHT), (CLEAVER_LENGTH, CLEAVER_HEIGHT), (CLEAVER_LENGTH, CLEAVER_BELLY),
          (150, 0), (0, 0)]),
    ]
    row_h = int(100 * scale + pad * 2)
    width = int((THROW_TOTAL + 40) * scale + pad * 3 + 520)
    img = Image.new("RGB", (width, row_h * len(rows) + 16), (255, 255, 255))
    draw = ImageDraw.Draw(img)
    for i, (label, fn, length, height, ref) in enumerate(rows):
        top = 12 + i * row_h
        draw.text((pad, top), label + "    black = blade    red = reference outline", fill=(20, 20, 20))
        origin_x = pad
        origin_y = top + int((height + 18) * scale) + 18

        def pt(s, h, ox):
            return (ox + s * scale, origin_y - h * scale)

        if fn is None:
            # Throwing knife: symmetric spear about the centreline.
            actual = []
            for k in range(181):
                s = THROW_TOTAL * k / 180
                half = throw_half_width(s)
                actual.append((THROW_TOTAL - s, 16 + half))
            for k in range(180, -1, -1):
                s = THROW_TOTAL * k / 180
                half = throw_half_width(s)
                actual.append((THROW_TOTAL - s, 16 - half))
            _poly(draw, [pt(s, h, origin_x) for s, h in actual], fill=(0, 0, 0))
            ref_pts = [
                (THROW_TOTAL, 16),
                (THROW_TOTAL - THROW_BLADE, 16 + THROW_WIDTH / 2),
                (THROW_TOTAL - THROW_BLADE - 16, 16 + 10.2),
                (16, 16 + 10.2),
                (0, 16 + 5),
                (0, 16 - 5),
                (16, 16 - 10.2),
                (THROW_TOTAL - THROW_BLADE - 16, 16 - 10.2),
                (THROW_TOTAL - THROW_BLADE, 16 - THROW_WIDTH / 2),
            ]
            draw.line([pt(s, h, origin_x + int(THROW_TOTAL * scale) + 80) for s, h in ref_pts] + [pt(*ref_pts[0], origin_x + int(THROW_TOTAL * scale) + 80)], fill=(180, 30, 30), width=2)
        else:
            curve = samples(fn, length, 200)
            poly = [(s, spine) for s, spine, _ in curve] + [(s, edge) for s, _, edge in reversed(curve)]
            _poly(draw, [pt(s, h, origin_x) for s, h in poly], fill=(0, 0, 0))
            ox = origin_x + int((length + 36) * scale)
            # Reference is the dimension polyline, closed heel-to-tip along spine then back along the edge.
            if label.startswith("Chef"):
                ref_line = [(0, CHEF_HEEL), (CHEF_SPINE_FLAT, CHEF_HEEL), (CHEF_LENGTH, CHEF_TIP),
                            (CHEF_EDGE_STRAIGHT, 0), (9, 0), (0, 0)]
            elif label.startswith("Pocket"):
                ref_line = [(0, POCKET_HEIGHT), (POCKET_SPINE_FLAT, POCKET_HEIGHT), (POCKET_LENGTH, POCKET_TIP),
                            (42, 0.4), (14, 0.4), (0, 1.2)]
            elif label.startswith("Butterfly"):
                ref_line = [(0, FLY_HEIGHT), (FLY_CLIP, FLY_HEIGHT), (FLY_LENGTH, FLY_TIP), (62, 0), (0, 0)]
            else:
                ref_line = [(0, CLEAVER_HEIGHT), (CLEAVER_LENGTH, CLEAVER_HEIGHT),
                            (CLEAVER_LENGTH, CLEAVER_BELLY), (150, 0), (0, 0)]
            draw.line([pt(s, h, ox) for s, h in ref_line] + [pt(*ref_line[0], ox)], fill=(180, 30, 30), width=3)
        draw.text((origin_x, origin_y + 8), "heel", fill=(80, 80, 80))
        draw.text((origin_x + int(length * scale) - 20, origin_y + 8), "tip", fill=(80, 80, 80))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    img.save(path)
    print("wrote", path, img.size)


if __name__ == "__main__":
    out = os.environ.get(
        "BLADE_PROFILE_PNG",
        "/opt/cursor/artifacts/knife-flip-filament-v4/blade-profiles.png",
    )
    render_sheet(out)
