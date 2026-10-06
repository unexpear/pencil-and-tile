#!/usr/bin/env python3
"""Turn the CC-BY Battleship STLs into the app's small triangle meshes.

Source: Original Battleship Pieces by MZimb, Thingiverse thing 5190846,
Creative Commons Attribution. https://www.thingiverse.com/thing:5190846

The pack's hulls, measured peg-span to peg-span, are 5, 4, 3, 3 and 2
squares. Those lengths are the classic fleet, so the file named Cruiser
(4 squares) is the game's battleship, the file named Destroyer (3) is the
cruiser, and the minesweeper (2) is the destroyer. The submarine file is
the submarine. Pegs under the hull are dropped; the hull is scaled onto
its squares.

Output is little-endian BSM1: magic, uint16 vertex count, uint16 triangle
count, float32 positions, float32 normals, uint16 indices.
"""

import struct
import sys
from pathlib import Path

# (stl name, asset name, squares, flip so the taller end is the stern)
SHIPS = [
    ("Aircraft_Carrier.stl", "carrier", 5, False),
    ("Cruiser.stl", "battleship", 4, False),
    ("Destroyer.stl", "cruiser", 3, True),
    ("Sub.stl", "submarine", 3, True),
    ("Mine_Sweeper.stl", "destroyer", 2, False),
]


def load_stl(path: Path):
    data = path.read_bytes()
    count = struct.unpack_from("<I", data, 80)[0]
    tris = []
    off = 84
    for _ in range(count):
        vals = struct.unpack_from("<12fH", data, off)
        tris.append((vals[3:6], vals[6:9], vals[9:12]))
        off += 50
    return tris


def cluster(tris, cell: float):
    buckets = {}
    corners = []
    for tri in tris:
        ids = []
        for v in tri:
            key = tuple(int(round(c / cell)) for c in v)
            buckets.setdefault(key, []).append(v)
            ids.append(key)
        if len(set(ids)) == 3:
            corners.append(ids)
    index = {}
    pos = []
    for key, pts in buckets.items():
        if key not in {k for tri in corners for k in tri}:
            continue
        index[key] = len(pos)
        n = len(pts)
        pos.append(tuple(sum(p[i] for p in pts) / n for i in range(3)))
    faces = []
    seen = set()
    for a, b, c in corners:
        tri = (index[a], index[b], index[c])
        if len(set(tri)) < 3 or tri in seen:
            continue
        seen.add(tri)
        faces.append(tri)
    return pos, faces


def normals(pos, faces):
    acc = [[0.0, 0.0, 0.0] for _ in pos]
    for a, b, c in faces:
        ax, ay, az = pos[a]
        bx, by, bz = pos[b]
        cx, cy, cz = pos[c]
        nx = (by - ay) * (cz - az) - (bz - az) * (cy - ay)
        ny = (bz - az) * (cx - ax) - (bx - ax) * (cz - az)
        nz = (bx - ax) * (cy - ay) - (by - ay) * (cx - ax)
        for i in (a, b, c):
            acc[i][0] += nx
            acc[i][1] += ny
            acc[i][2] += nz
    out = []
    for nx, ny, nz in acc:
        length = (nx * nx + ny * ny + nz * nz) ** 0.5 or 1.0
        out.append((nx / length, ny / length, nz / length))
    return out


def decimate(tris, lo=2000, hi=3200):
    span = max(max(v[0] for t in tris for v in t) - min(v[0] for t in tris for v in t), 1.0)
    cell = span / 48.0
    best = None
    for _ in range(14):
        pos, faces = cluster(tris, cell)
        best = (pos, faces, cell)
        if lo <= len(faces) <= hi:
            break
        cell *= 1.18 if len(faces) > hi else 0.82
    return best


def keep_hull(tris, waterline=0.0):
    return [tri for tri in tris if max(v[2] for v in tri) >= waterline]


def scale_ship(pos, length, flip):
    xs = [p[0] for p in pos]
    ys = [p[1] for p in pos]
    zs = [p[2] for p in pos]
    xmin, xmax = min(xs), max(xs)
    ymid = (min(ys) + max(ys)) / 2
    zmin = min(zs)
    span = xmax - xmin or 1.0
    # A hair inside the squares, so ships that touch do not occupy the same edge.
    fit = length * 0.92
    pad = (length - fit) / 2
    scaled = []
    for x, y, z in pos:
        u = (xmax - x) / span if flip else (x - xmin) / span
        scaled.append((pad + u * fit, (y - ymid) / span * length, (z - zmin) / span * length))
    width = max(p[1] for p in scaled) - min(p[1] for p in scaled)
    if width > 0.82:
        shrink = 0.82 / width
        scaled = [(x, y * shrink, z) for x, y, z in scaled]
    return scaled


def pack(pos, nrm, faces, path: Path):
    if len(pos) > 65000 or len(faces) > 65000:
        raise SystemExit(f"{path} is too large for the mesh format")
    buf = bytearray()
    buf += b"BSM1"
    buf += struct.pack("<HH", len(pos), len(faces))
    for x, y, z in pos:
        buf += struct.pack("<fff", x, y, z)
    for x, y, z in nrm:
        buf += struct.pack("<fff", x, y, z)
    for a, b, c in faces:
        buf += struct.pack("<HHH", a, b, c)
    path.write_bytes(buf)


def write_ship(src: Path, dest: Path, length: int, flip: bool):
    hull = keep_hull(load_stl(src))
    pos, faces, cell = decimate(hull, lo=2000, hi=3200)
    pos = scale_ship(pos, length, flip)
    # Mirroring the bow end reverses the winding, so put it back.
    if flip:
        faces = [(a, c, b) for a, b, c in faces]
    nrm = normals(pos, faces)
    pack(pos, nrm, faces, dest)
    xs = [p[0] for p in pos]
    ys = [p[1] for p in pos]
    zs = [p[2] for p in pos]
    print(
        f"{dest.name:16} tris {len(faces):4} cell {cell:.2f} "
        f"x {min(xs):.2f}..{max(xs):.2f} y {min(ys):.2f}..{max(ys):.2f} z {min(zs):.2f}..{max(zs):.2f}"
    )


def split_pins(tris):
    """The file holds two pins, one above the other, that touch in height."""
    parent = {}

    def find(a):
        parent.setdefault(a, a)
        while parent[a] != a:
            parent[a] = parent[parent[a]]
            a = parent[a]
        return a

    def union(a, b):
        ra, rb = find(a), find(b)
        if ra != rb:
            parent[rb] = ra

    def key(v):
        return tuple(round(c, 1) for c in v)

    groups = {}
    for tri in tris:
        keys = [key(v) for v in tri]
        union(keys[0], keys[1])
        union(keys[1], keys[2])
    for tri in tris:
        groups.setdefault(find(key(tri[0])), []).append(tri)
    parts = list(groups.values())
    parts.sort(key=lambda part: max(v[2] for tri in part for v in tri))
    return parts


def scale_pin(tris, height, dest: Path):
    pos, faces, _ = decimate(tris, lo=80, hi=400)
    zs = [p[2] for p in pos]
    xs = [p[0] for p in pos]
    ys = [p[1] for p in pos]
    z0 = min(zs)
    span = max(zs) - z0 or 1.0
    xmid = (min(xs) + max(xs)) / 2
    ymid = (min(ys) + max(ys)) / 2
    s = height / span
    pos = [((x - xmid) * s, (y - ymid) * s, (z - z0) * s) for x, y, z in pos]
    # A pin should stay inside one square.
    radius = max(max(abs(p[0]) for p in pos), max(abs(p[1]) for p in pos))
    if radius > 0.16:
        shrink = 0.16 / radius
        pos = [(x * shrink, y * shrink, z) for x, y, z in pos]
    pack(pos, normals(pos, faces), faces, dest)
    print(f"{dest.name:16} tris {len(faces):4} h {max(p[2] for p in pos):.2f}")


def main():
    src = Path(sys.argv[1])
    dest = Path(sys.argv[2])
    dest.mkdir(parents=True, exist_ok=True)
    for name, asset, length, flip in SHIPS:
        write_ship(src / name, dest / f"{asset}.bmesh", length, flip)
    pins = split_pins(load_stl(src / "Pins.stl"))
    if len(pins) != 2:
        raise SystemExit(f"expected two pins, found {len(pins)}")
    # The shorter pin is the white miss peg; the taller one is the red hit peg.
    scale_pin(pins[0], 0.34, dest / "pin_miss.bmesh")
    scale_pin(pins[1], 0.46, dest / "pin_hit.bmesh")


if __name__ == "__main__":
    main()
