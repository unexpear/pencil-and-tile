#!/usr/bin/env python3
"""Carved Kalah board and a glass marble for Mancala.

    blender --background --python tools/mancala/generate_board.py

The board is an original mesh. Pit centres, radii and the top plane are read from
MancalaBoard.kt so the bowls line up with the tap ovals. Wood is the CC0 Wood051
maps already used for Knife Flip handles (Colour, NormalGL, Roughness only).
The marble is a unit sphere; the app scales it to MancalaBoard.stoneRadius.

Blender is Z-up. The glTF exporter turns that into Y-up as (x, z, -y). Vertices
are stored as (board X, -board Y, board Z) so the exported asset is
(board X, board Z up, board Y depth), which is the Filament placement.
"""

import math
import os
import re
import sys

import bmesh
import bpy

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))
OUT = os.path.join(REPO, "app", "src", "main", "assets", "mancala")
TEX = os.path.join(REPO, "tools", "knife-flip", "textures")
BOARD_KT = os.path.join(
    REPO, "app", "src", "main", "java", "com", "simplegamegen", "sudoku",
    "ui", "screens", "MancalaScreen.kt",
)

STEP = 0.012
CORNER = 0.48
# Round-over on the top edge, board units. A wide radius catches the light so the edge reads as carved.
RIM = 0.16


def const(text, name):
    match = re.search(rf"const val {name} = ([0-9.]+)f", text)
    if not match:
        raise SystemExit(f"{name} is missing from MancalaScreen.kt")
    return float(match.group(1))


def load_board():
    text = open(BOARD_KT, encoding="utf-8").read()
    width = const(text, "width")
    depth = const(text, "depth")
    top = const(text, "top")
    pit_depth = const(text, "pitDepth")
    store_depth = const(text, "storeDepth")
    block = text.split("val holes", 1)[1].split("fun hole", 1)[0]
    step = float(re.search(r"val step = ([0-9.]+)f", block).group(1))
    x0 = float(re.search(r"val x0 = ([0-9.]+)f", block).group(1))
    near_y = float(re.search(r"Hole\(i, x, ([0-9.]+)f,", block).group(1))
    far_y = float(re.search(r"Hole\(12 - i, x, ([0-9.]+)f,", block).group(1))
    pit_rx, pit_ry = [float(v) for v in re.search(
        r"Hole\(i, x, [0-9.]+f, ([0-9.]+)f, ([0-9.]+)f\)", block).groups()]
    you = [float(v) for v in re.search(
        r"Hole\(Mancala\.YOU, ([0-9.]+)f, ([0-9.]+)f, ([0-9.]+)f, ([0-9.]+)f\)", block).groups()]
    cpu = [float(v) for v in re.search(
        r"Hole\(Mancala\.CPU, ([0-9.]+)f, ([0-9.]+)f, ([0-9.]+)f, ([0-9.]+)f\)", block).groups()]
    holes = []
    for i in range(6):
        x = x0 + i * step
        holes.append((x, near_y, pit_rx, pit_ry, pit_depth))
        holes.append((x, far_y, pit_rx, pit_ry, pit_depth))
    holes.append((you[0], you[1], you[2], you[3], store_depth))
    holes.append((cpu[0], cpu[1], cpu[2], cpu[3], store_depth))
    return width, depth, top, holes


def fresh_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.unit_settings.system = "METRIC"
    scene.unit_settings.scale_length = 1.0


def socket(bsdf, name):
    found = bsdf.inputs.get(name)
    if found is None:
        names = [s.name for s in bsdf.inputs]
        raise SystemExit(f"Principled BSDF has no '{name}'. Sockets: {names}")
    return found


def no_metal(nt, bsdf, size):
    """Blender 4.3's glTF exporter packs metallic = 1 into the blue channel of the
    metallic-roughness map when Metallic is not linked, and Filament then renders the wood
    as dark metal. Linking a black map keeps that channel at 0."""
    folder = os.environ.get("KNIFE_IMAGE_BAKE", "/tmp/knife-flip-images")
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, f"no_metal_{size}.png")
    img = bpy.data.images.new(f"no_metal_{size}", width=size, height=size, alpha=False)
    img.colorspace_settings.name = "Non-Color"
    img.pixels.foreach_set([0.0, 0.0, 0.0, 1.0] * (size * size))
    img.filepath_raw = path
    img.file_format = "PNG"
    img.save()
    loaded = bpy.data.images.load(path, check_existing=True)
    loaded.colorspace_settings.name = "Non-Color"
    node = nt.nodes.new("ShaderNodeTexImage")
    node.image = loaded
    nt.links.new(node.outputs["Color"], socket(bsdf, "Metallic"))


def new_bsdf(name):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    return mat, mat.node_tree.nodes.get("Principled BSDF")


def load_image(path, name, noncolor=False):
    img = bpy.data.images.load(path)
    img.name = name
    img.colorspace_settings.name = "Non-Color" if noncolor else "sRGB"
    return img


def brightened(img, gain, name):
    copy = img.copy()
    copy.name = name
    px = list(copy.pixels)
    for i in range(0, len(px), 4):
        px[i] = min(1.0, px[i] * gain[0])
        px[i + 1] = min(1.0, px[i + 1] * gain[1])
        px[i + 2] = min(1.0, px[i + 2] * gain[2])
    copy.pixels.foreach_set(px)
    folder = os.environ.get("KNIFE_IMAGE_BAKE", "/tmp/knife-flip-images")
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, name + ".png")
    copy.filepath_raw = path
    copy.file_format = "PNG"
    copy.save()
    loaded = bpy.data.images.load(path)
    loaded.name = name + "_baked"
    return loaded


def wood_material():
    color = load_image(os.path.join(TEX, "Wood051_1K_Color.jpg"), "Wood051Color")
    normal = load_image(os.path.join(TEX, "Wood051_1K_NormalGL.jpg"), "Wood051Normal", noncolor=True)
    rough = load_image(os.path.join(TEX, "Wood051_1K_Roughness.jpg"), "Wood051Rough", noncolor=True)
    mat, bsdf = new_bsdf("KalahWood")
    socket(bsdf, "Metallic").default_value = 0.0
    socket(bsdf, "Roughness").default_value = 0.55
    nt = mat.node_tree
    col = nt.nodes.new("ShaderNodeTexImage")
    # The glTF exporter drops the multiply below, so the shipped colour is the map itself.
    # Bake a lighter, warm copy instead: same grain, a little lighter than before.
    col.image = brightened(color, (1.55, 1.30, 1.10), "Wood051Light")
    nt.links.new(col.outputs["Color"], socket(bsdf, "Base Color"))
    rgh = nt.nodes.new("ShaderNodeTexImage")
    rgh.image = rough
    nt.links.new(rgh.outputs["Color"], socket(bsdf, "Roughness"))
    no_metal(nt, bsdf, rough.size[0])
    ntex = nt.nodes.new("ShaderNodeTexImage")
    ntex.image = normal
    nmap = nt.nodes.new("ShaderNodeNormalMap")
    nmap.inputs["Strength"].default_value = 1.15
    nt.links.new(ntex.outputs["Color"], nmap.inputs["Color"])
    nt.links.new(nmap.outputs["Normal"], socket(bsdf, "Normal"))
    return mat


def core_material():
    mat, bsdf = new_bsdf("MarbleCore")
    socket(bsdf, "Base Color").default_value = (0.8, 0.3, 0.1, 1)
    socket(bsdf, "Roughness").default_value = 0.3
    return mat


def glass_material():
    """Clear polished glass for the shell. The app swaps in its own transmissive glass
    (no clearcoat, which Filament's ubershaders cannot combine with transmission)."""
    mat, bsdf = new_bsdf("Marble")
    socket(bsdf, "Base Color").default_value = (0.92, 0.94, 0.95, 1)
    socket(bsdf, "Metallic").default_value = 0.0
    socket(bsdf, "Roughness").default_value = 0.04
    socket(bsdf, "IOR").default_value = 1.5
    socket(bsdf, "Specular IOR Level").default_value = 0.55
    socket(bsdf, "Transmission Weight").default_value = 1.0
    mat.use_backface_culling = False
    return mat


def bowl_drop(x, y, holes):
    drop = 0.0
    lift = 0.0
    for hx, hy, rx, ry, depth in holes:
        dx = (x - hx) / rx
        dy = (y - hy) / ry
        t2 = dx * dx + dy * dy
        if t2 >= 1.16 * 1.16:
            continue
        t = math.sqrt(t2)
        if t < 1.0:
            # Cosine bowl: flat join at the rim, deepest in the middle.
            profile = 0.5 * (1.0 + math.cos(math.pi * t))
            drop = max(drop, depth * profile)
        # A low lip just outside the rim, so each cup reads as carved.
        if 0.90 < t < 1.14:
            u = (t - 0.90) / 0.24
            lift = max(lift, 0.055 * math.sin(math.pi * min(1.0, u)) ** 2)
    return drop - lift


def rim_drop(x, y, width, depth):
    d = min(x, y, width - x, depth - y)
    if d >= RIM:
        return 0.0
    # Quarter circle of radius RIM: flat on the top, rolling over to the side.
    u = 1.0 - d / RIM
    return RIM * (1.0 - math.sqrt(max(0.0, 1.0 - u * u))) * 0.55


def clamp_corner(x, y, width, depth):
    corners = (
        (CORNER, CORNER),
        (width - CORNER, CORNER),
        (CORNER, depth - CORNER),
        (width - CORNER, depth - CORNER),
    )
    for cx, cy in corners:
        if (x - cx) * (cx - CORNER if cx < width / 2 else cx - (width - CORNER)) <= 0 and False:
            pass
        in_x = (x <= CORNER and cx == CORNER) or (x >= width - CORNER and cx == width - CORNER)
        in_y = (y <= CORNER and cy == CORNER) or (y >= depth - CORNER and cy == depth - CORNER)
        if not (in_x and in_y):
            continue
        dx, dy = x - cx, y - cy
        dist = math.hypot(dx, dy)
        if dist > CORNER and dist > 1e-6:
            x = cx + dx / dist * CORNER
            y = cy + dy / dist * CORNER
    return x, y


def build_board(width, depth, top, holes, material):
    nx = int(round(width / STEP))
    ny = int(round(depth / STEP))
    bm = bmesh.new()
    grid = []
    for j in range(ny + 1):
        row = []
        v = j / ny
        for i in range(nx + 1):
            u = i / nx
            x, y = clamp_corner(u * width, v * depth, width, depth)
            z = top - bowl_drop(x, y, holes) - rim_drop(x, y, width, depth)
            # Blender Y is -board Y so glTF Z comes out as board Y.
            row.append(bm.verts.new((x, -y, z)))
        grid.append(row)
    bm.verts.ensure_lookup_table()
    top_faces = []
    for j in range(ny):
        for i in range(nx):
            a, b = grid[j][i], grid[j][i + 1]
            c, d = grid[j + 1][i + 1], grid[j + 1][i]
            if len({a.co.to_tuple(4), b.co.to_tuple(4), c.co.to_tuple(4), d.co.to_tuple(4)}) < 3:
                continue
            try:
                top_faces.append(bm.faces.new((a, b, c, d)))
            except ValueError:
                pass
    # Skirt around the boundary, then a bottom cap.
    edge_z = 0.0
    bottom = []
    for j in range(ny + 1):
        row = []
        for i in range(nx + 1):
            if i not in (0, nx) and j not in (0, ny):
                row.append(None)
                continue
            top_v = grid[j][i]
            row.append(bm.verts.new((top_v.co.x, top_v.co.y, edge_z)))
        bottom.append(row)

    def side(a, b, c, d):
        try:
            bm.faces.new((a, b, c, d))
        except ValueError:
            pass

    for i in range(nx):
        side(grid[0][i], bottom[0][i], bottom[0][i + 1], grid[0][i + 1])
        side(grid[ny][i + 1], bottom[ny][i + 1], bottom[ny][i], grid[ny][i])
    for j in range(ny):
        side(grid[j + 1][0], bottom[j + 1][0], bottom[j][0], grid[j][0])
        side(grid[j][nx], bottom[j][nx], bottom[j + 1][nx], grid[j + 1][nx])
    # Bottom: one quad is enough, but the boundary is a rounded loop. Fan from a centre.
    centre = bm.verts.new((width / 2, -depth / 2, edge_z))
    loop = []
    for i in range(nx + 1):
        loop.append(bottom[0][i])
    for j in range(1, ny + 1):
        loop.append(bottom[j][nx])
    for i in range(nx - 1, -1, -1):
        loop.append(bottom[ny][i])
    for j in range(ny - 1, 0, -1):
        loop.append(bottom[j][0])
    for a, b in zip(loop, loop[1:] + loop[:1]):
        if a == b:
            continue
        try:
            bm.faces.new((centre, b, a))
        except ValueError:
            pass

    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    uv = bm.loops.layers.uv.new("UVMap")
    for face in bm.faces:
        for loop in face.loops:
            co = loop.vert.co
            # Board X across, board Y (which is -blender Y) along the grain.
            loop[uv].uv = (co.x / width * 2.0, (-co.y) / depth * 1.4)
    mesh = bpy.data.meshes.new("KalahBoard")
    bm.to_mesh(mesh)
    bm.free()
    # Smooth only the carved top. The skirt and the base stay flat, so the edge where the
    # round-over meets the side keeps its own normals instead of averaging into a smear.
    for poly in mesh.polygons:
        poly.use_smooth = poly.normal.z > 0.2
    obj = bpy.data.objects.new("KalahBoard", mesh)
    bpy.context.collection.objects.link(obj)
    obj.data.materials.append(material)
    print(f"board verts {len(mesh.vertices)} faces {len(mesh.polygons)}")
    return obj


def build_marble(material, core_material):
    """A clear glass shell around a cat's-eye core of three twisted vanes.

    The app names the parts: MarbleShell gets the clear glass, MarbleCore the colour.
    """
    bpy.ops.mesh.primitive_uv_sphere_add(segments=64, ring_count=32, radius=1.0, location=(0, 0, 0))
    shell = bpy.context.active_object
    shell.name = "MarbleShell"
    shell.data.name = "MarbleShell"
    for poly in shell.data.polygons:
        poly.use_smooth = True
    shell.data.materials.append(material)
    vanes = []
    for k in range(3):
        bpy.ops.mesh.primitive_uv_sphere_add(segments=32, ring_count=16, radius=1.0, location=(0, 0, 0))
        vane = bpy.context.active_object
        vane.scale = (0.84, 0.075, 0.80)
        vane.rotation_euler = (0.0, 0.0, math.pi * k / 3.0)
        bpy.ops.object.transform_apply(location=False, rotation=True, scale=True)
        # A gentle twist along the axis, so the vanes curl like a real cat's eye.
        for v in vane.data.vertices:
            a = 1.35 * v.co.z
            x, y = v.co.x, v.co.y
            v.co.x = x * math.cos(a) - y * math.sin(a)
            v.co.y = x * math.sin(a) + y * math.cos(a)
        for poly in vane.data.polygons:
            poly.use_smooth = True
        vanes.append(vane)
    bpy.ops.object.select_all(action="DESELECT")
    for vane in vanes:
        vane.select_set(True)
    bpy.context.view_layer.objects.active = vanes[0]
    bpy.ops.object.join()
    core = bpy.context.active_object
    core.name = "MarbleCore"
    core.data.name = "MarbleCore"
    core.data.materials.append(core_material)
    return [shell, core]


def export(objs, filename):
    os.makedirs(OUT, exist_ok=True)
    path = os.path.join(OUT, filename)
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objs:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    bpy.ops.export_scene.gltf(
        filepath=path,
        export_format="GLB",
        use_selection=True,
        export_apply=True,
        export_yup=True,
        export_image_format="JPEG",
        export_jpeg_quality=90,
    )
    print(f"wrote {filename} {os.path.getsize(path) / 1024:.1f} KiB")


def main():
    width, depth, top, holes = load_board()
    print(f"board {width} x {depth} top {top} holes {len(holes)}")
    fresh_scene()
    board = build_board(width, depth, top, holes, wood_material())
    export([board], "board.glb")
    fresh_scene()
    marble = build_marble(glass_material(), core_material())
    export(marble, "marble.glb")


if __name__ == "__main__":
    main()
