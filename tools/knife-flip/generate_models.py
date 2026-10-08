#!/usr/bin/env python3
"""Headless Blender generator for Knife Flip.

Builds original meshes (no downloaded models) and exports GLB for Filament:

    blender --background --python tools/knife-flip/generate_models.py

Knives are authored in metres with the centre of mass at the origin and the tip
along Blender +Z. The glTF exporter turns that into glTF +Y, which is "tip up"
at theta = 0 in FlipPhysics. The bottle's origin is the centre of its base.
Nothing here changes FlipPhysics; the proportions are the ones that file already
uses (an 8-inch chef's knife, a centre-balanced thrower, an open folder,
Dekker's 204 mm bottle). The butterfly knife and the cleaver are still built
here, but a normal run does not write their GLBs. Set KNIFE_SHELVED=1 to export
butterfly.glb and cleaver.glb.

The only image files read from disk are the CC0 wood maps in ./textures and they
are embedded in the GLBs. Steel roughness is drawn in this script.
"""

import math
import os
import random
import re
import sys

import bpy
import bmesh

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))
OUT = os.path.join(REPO, "app", "src", "main", "assets", "knife-flip")
TEX = os.path.join(SCRIPT_DIR, "textures")
if SCRIPT_DIR not in sys.path:
    sys.path.insert(0, SCRIPT_DIR)
import blade_profiles as blades

# Centres of mass, metres from the tip. Same parts as FlipPhysics.knife().
CHEF_COM = (0.085 * 0.108 + 0.040 * 0.206 + 0.095 * 0.270) / 0.220
THROW_COM = 0.140
POCKET_COM = (0.030 * 0.040 + 0.098 * 0.145) / 0.128
FLY_COM = (0.050 * 0.048 + 0.105 * 0.180) / 0.155
CLEAVER_COM = (0.330 * 0.090 + 0.075 * 0.240) / 0.405


def throwing_thickness():
    """The same 5 mm stock FlipPhysics names THROWING_THICKNESS. Not the 28 mm width."""
    path = os.path.join(
        REPO, "sudoku-engine", "src", "main", "kotlin",
        "com", "simplegamegen", "sudoku", "arcade", "FlipPhysics.kt",
    )
    text = open(path, encoding="utf-8").read()
    match = re.search(r"const val THROWING_THICKNESS = ([0-9.]+)f", text)
    if not match:
        raise SystemExit("THROWING_THICKNESS is missing from FlipPhysics.kt")
    return float(match.group(1))


THROWING_THICKNESS = throwing_thickness()


def z_of(com, from_tip):
    """Blender Z of a station measured from the tip. Tip is +Z."""
    return com - from_tip


def fresh_scene():
    bpy.ops.wm.read_factory_settings(use_empty=True)
    scene = bpy.context.scene
    scene.unit_settings.system = "METRIC"
    scene.unit_settings.scale_length = 1.0


def link_mesh(name, verts, faces):
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.validate(verbose=False)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    bm = bmesh.new()
    bm.from_mesh(mesh)
    bmesh.ops.recalc_face_normals(bm, faces=bm.faces)
    for edge in bm.edges:
        if not edge.is_manifold:
            edge.smooth = False
            continue
        try:
            ang = edge.calc_face_angle(0.0)
        except ValueError:
            ang = 0.0
        edge.smooth = ang < math.radians(48)
    bm.to_mesh(mesh)
    bm.free()
    for poly in mesh.polygons:
        poly.use_smooth = True
    mesh.update()
    return obj


def loft(name, rings):
    """Rings are lists of (x, y, z) with a shared count. Closed along the ring, capped."""
    n = len(rings[0])
    verts = [p for ring in rings for p in ring]
    faces = []
    for i in range(len(rings) - 1):
        for j in range(n):
            a = i * n + j
            b = i * n + (j + 1) % n
            c = (i + 1) * n + (j + 1) % n
            d = (i + 1) * n + j
            faces.append((a, b, c, d))
    faces.append(tuple(range(n)))
    last = (len(rings) - 1) * n
    faces.append(tuple(last + i for i in range(n - 1, -1, -1)))
    return link_mesh(name, verts, faces)


def wedge_ring(z, spine_x, edge_x, spine_full, edge_full=0.00018, grind=0.34, steps=7):
    """A blade cross-section. +X is the edge, -X the spine, thickness along Y."""
    pts_y = []
    for k in range(steps):
        u = k / (steps - 1)
        x = spine_x + (edge_x - spine_x) * u
        if u <= grind:
            thick = spine_full
        else:
            w = (u - grind) / (1.0 - grind)
            thick = spine_full * (1.0 - w) ** 1.35 + edge_full * w
        pts_y.append((x, thick * 0.5))
    ring = [(x, y, z) for x, y in pts_y]
    for x, y in reversed(pts_y[1:-1]):
        ring.append((x, -y, z))
    return ring


def flat_grind_ring(z, spine_x, edge_x, spine_full, bevel=0.0015, grind_at=0.12, blunt=False, edge_thick=None):
    """Flat primary grind and a short secondary bevel. Faceted, not a lens.

    +X is the edge. grind_at is how far down from the spine the flat face runs
    before the grind line (0 is a full flat off the spine, 0.40 leaves the top
    40% at full thickness). The last `bevel` metres are the secondary bevel.
    """
    height = max(edge_x - spine_x, 1.2e-4)
    flat = min(height * grind_at, height * 0.55)
    x_grind = spine_x + flat
    x_bevel = edge_x - min(bevel, height * 0.42)
    if x_bevel <= x_grind + 1.5e-4:
        x_bevel = (x_grind + edge_x) * 0.5
    if edge_thick is not None:
        t_edge = min(max(edge_thick, 1.2e-4), spine_full * 0.92)
        t_bevel = min(max(t_edge, spine_full * 0.28), spine_full * 0.96)
    elif blunt:
        t_bevel = spine_full * 0.72
        t_edge = spine_full * 0.55
    else:
        t_bevel = max(0.00032, spine_full * 0.20)
        t_edge = 0.00012
    top = [
        (spine_x, spine_full * 0.5),
        (x_grind, spine_full * 0.5),
        (x_bevel, t_bevel * 0.5),
        (edge_x, t_edge * 0.5),
    ]
    ring = [(x, y, z) for x, y in top]
    for x, y in reversed(top[:-1]):
        ring.append((x, -y, z))
    return ring


def diamond_ring(z, half, thick):
    """Both edges bevelled. Four flat facets, not a rounded lens."""
    h = max(half, 1.5e-4)
    t = thick * 0.5
    return [
        (0.0, t, z),
        (h, 0.0, z),
        (0.0, -t, z),
        (-h, 0.0, z),
    ]


def blade(name, com, heel, profile, thick, stations=42):
    rings = []
    for i in range(stations):
        t = i / (stations - 1)
        # Pull the last samples in so the tip and heel are not a single collapsed face.
        tt = t
        spine_x, edge_x = profile(tt)
        rings.append(wedge_ring(z_of(com, tt * heel), spine_x, edge_x, thick(tt)))
    return loft(name, rings)


def disc_y(name, x, y0, y1, z, radius, segments=14):
    """A short cylinder along Y, so a rivet or pin sits through the handle."""
    rings = []
    for y in (y0, y1):
        ring = []
        for i in range(segments):
            a = 2 * math.pi * i / segments
            ring.append((x + math.cos(a) * radius, y, z + math.sin(a) * radius))
        rings.append(ring)
    return loft(name, rings)


def cylinder(name, z0, z1, radius, segments=24, radius1=None):
    r1 = radius if radius1 is None else radius1
    rings = []
    steps = 2
    for s in range(steps):
        z = z0 + (z1 - z0) * s / (steps - 1)
        r = radius + (r1 - radius) * s / (steps - 1)
        ring = []
        for i in range(segments):
            a = 2 * math.pi * i / segments
            ring.append((math.cos(a) * r, math.sin(a) * r, z))
        rings.append(ring)
    return loft(name, rings)


def tube(name, profile, segments=28, wall=0.0007):
    """profile is (z, outer radius) from base upward. A thin closed wall."""
    outer = []
    inner = []
    for i in range(segments):
        a = 2 * math.pi * i / segments
        ca, sa = math.cos(a), math.sin(a)
        outer.append([(ca * r, sa * r, z) for z, r in profile])
        inner.append([(ca * (r - wall), sa * (r - wall), z) for z, r in profile])
    # Build as one mesh: outer loft, inner loft, lip rings.
    # Simpler path: two lofts joined by stitching the lip and the base.
    n = segments
    m = len(profile)
    verts = []
    for col in outer:
        verts.extend(col)
    for col in inner:
        verts.extend(col)
    # Reorder to rings along Z so loft winding is consistent: we'll make faces directly.
    faces = []

    def idx(side, i, k):
        # side 0 outer, 1 inner. verts were stored column-major (around, then along).
        return side * n * m + i * m + k

    for i in range(n):
        j = (i + 1) % n
        for k in range(m - 1):
            faces.append((idx(0, i, k), idx(0, j, k), idx(0, j, k + 1), idx(0, i, k + 1)))
            faces.append((idx(1, i, k), idx(1, i, k + 1), idx(1, j, k + 1), idx(1, j, k)))
        # lip at the top, base ring at the bottom
        faces.append((idx(0, i, m - 1), idx(0, j, m - 1), idx(1, j, m - 1), idx(1, i, m - 1)))
        faces.append((idx(0, i, 0), idx(1, i, 0), idx(1, j, 0), idx(0, j, 0)))
    return link_mesh(name, verts, faces)


def lathe_solid(name, profile, segments=32):
    """profile (z, radius), including a 0-radius start or end if it should be closed."""
    rings = []
    for z, r in profile:
        ring = []
        rr = max(r, 1e-5)
        for i in range(segments):
            a = 2 * math.pi * i / segments
            ring.append((math.cos(a) * rr, math.sin(a) * rr, z))
        rings.append(ring)
    return loft(name, rings)


def box(name, x0, x1, y0, y1, z0, z1):
    verts = [
        (x0, y0, z0), (x1, y0, z0), (x1, y1, z0), (x0, y1, z0),
        (x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1),
    ]
    faces = [
        (0, 1, 2, 3), (4, 7, 6, 5),
        (0, 4, 5, 1), (1, 5, 6, 2), (2, 6, 7, 3), (3, 7, 4, 0),
    ]
    return link_mesh(name, verts, faces)


def bevel_box(name, x0, x1, y0, y1, z0, z1, chamfer):
    """A box with the vertical edges and the top lip knocked back. Chamfer in metres."""
    c = chamfer
    # Eight-sided footprint.
    def ring(z, inset):
        xa, xb = x0 + inset, x1 - inset
        ya, yb = y0 + inset, y1 - inset
        return [
            (xa + c, ya, z), (xb - c, ya, z), (xb, ya + c, z), (xb, yb - c, z),
            (xb - c, yb, z), (xa + c, yb, z), (xa, yb - c, z), (xa, ya + c, z),
        ]
    rings = [ring(z0, 0.0), ring(z0 + c, 0.0), ring(z1 - c, 0.0), ring(z1, c * 0.35)]
    return loft(name, rings)


def assign(obj, mat):
    obj.data.materials.append(mat)


def uv_smart(obj):
    bpy.ops.object.select_all(action="DESELECT")
    obj.select_set(True)
    bpy.context.view_layer.objects.active = obj
    bpy.ops.object.mode_set(mode="EDIT")
    bpy.ops.mesh.select_all(action="SELECT")
    bpy.ops.uv.smart_project(angle_limit=1.15, island_margin=0.01)
    bpy.ops.object.mode_set(mode="OBJECT")


def cube_uv(obj, scale=4.0):
    mesh = obj.data
    uv = mesh.uv_layers.new(name="UVMap") if not mesh.uv_layers else mesh.uv_layers[0]
    for poly in mesh.polygons:
        for i, loop in enumerate(poly.loop_indices):
            vert = mesh.vertices[mesh.loops[loop].vertex_index].co
            if abs(poly.normal.z) > 0.5:
                uv.data[loop].uv = (vert.x * scale, vert.y * scale)
            elif abs(poly.normal.x) > 0.5:
                uv.data[loop].uv = (vert.y * scale, vert.z * scale)
            else:
                uv.data[loop].uv = (vert.x * scale, vert.z * scale)


def socket(bsdf, name):
    found = bsdf.inputs.get(name)
    if found is None:
        names = [s.name for s in bsdf.inputs]
        raise SystemExit(f"Principled BSDF has no '{name}'. Sockets: {names}")
    return found


def new_bsdf(name):
    mat = bpy.data.materials.new(name)
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    return mat, bsdf


def steel(brush):
    """Light brushed steel. Metallic is not 1, so the grey base shows when the room is dark."""
    mat, bsdf = new_bsdf("Steel")
    socket(bsdf, "Base Color").default_value = (0.84, 0.86, 0.88, 1)
    socket(bsdf, "Metallic").default_value = 0.86
    socket(bsdf, "Roughness").default_value = 0.32
    if "Anisotropic" in bsdf.inputs:
        socket(bsdf, "Anisotropic").default_value = 0.45
    if "Anisotropic Rotation" in bsdf.inputs:
        socket(bsdf, "Anisotropic Rotation").default_value = 0.25
    nt = mat.node_tree
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = brush
    nt.links.new(tex.outputs["Color"], socket(bsdf, "Roughness"))
    return mat


def brass():
    mat, bsdf = new_bsdf("Brass")
    socket(bsdf, "Base Color").default_value = (0.74, 0.56, 0.24, 1)
    socket(bsdf, "Metallic").default_value = 1.0
    socket(bsdf, "Roughness").default_value = 0.32
    return mat


def paint(name, color, roughness=0.42, metallic=0.0):
    mat, bsdf = new_bsdf(name)
    socket(bsdf, "Base Color").default_value = (*color, 1)
    socket(bsdf, "Metallic").default_value = metallic
    socket(bsdf, "Roughness").default_value = roughness
    return mat


def wood(color_img, normal_img, rough_img, name="Wood"):
    mat, bsdf = new_bsdf(name)
    socket(bsdf, "Metallic").default_value = 0.0
    socket(bsdf, "Roughness").default_value = 0.55
    nt = mat.node_tree
    col = nt.nodes.new("ShaderNodeTexImage")
    col.image = color_img
    nt.links.new(col.outputs["Color"], socket(bsdf, "Base Color"))
    rgh = nt.nodes.new("ShaderNodeTexImage")
    rgh.image = rough_img
    nt.links.new(rgh.outputs["Color"], socket(bsdf, "Roughness"))
    ntex = nt.nodes.new("ShaderNodeTexImage")
    ntex.image = normal_img
    nmap = nt.nodes.new("ShaderNodeNormalMap")
    nmap.inputs["Strength"].default_value = 1.0
    nt.links.new(ntex.outputs["Color"], nmap.inputs["Color"])
    nt.links.new(nmap.outputs["Normal"], socket(bsdf, "Normal"))
    return mat


def glass(name, color, transmission=0.92, roughness=0.06, ior=1.5):
    mat, bsdf = new_bsdf(name)
    socket(bsdf, "Base Color").default_value = (*color, 1)
    socket(bsdf, "Metallic").default_value = 0.0
    socket(bsdf, "Roughness").default_value = roughness
    socket(bsdf, "IOR").default_value = ior
    socket(bsdf, "Transmission Weight").default_value = transmission
    mat.use_backface_culling = False
    return mat


def alpha_glass(name, color, alpha=0.16, roughness=0.06):
    """See-through glass without refraction.

    Filament's transmission pass samples the opaque background and skips a transparent
    liquid sitting inside the bottle, so the bottle used to look empty. Alpha blending
    composites over the opaque water instead.
    """
    mat, bsdf = new_bsdf(name)
    socket(bsdf, "Base Color").default_value = (*color, 1)
    socket(bsdf, "Metallic").default_value = 0.0
    socket(bsdf, "Roughness").default_value = roughness
    socket(bsdf, "Transmission Weight").default_value = 0.0
    socket(bsdf, "Alpha").default_value = alpha
    mat.blend_method = "BLEND"
    mat.use_backface_culling = False
    mat.show_transparent_back = True
    return mat


def liquid(name, color):
    """Opaque light-blue water. Transparent water disappears inside the glass."""
    mat, bsdf = new_bsdf(name)
    socket(bsdf, "Base Color").default_value = (*color, 1)
    socket(bsdf, "Metallic").default_value = 0.0
    socket(bsdf, "Roughness").default_value = 0.16
    socket(bsdf, "Transmission Weight").default_value = 0.0
    socket(bsdf, "Alpha").default_value = 1.0
    mat.blend_method = "OPAQUE"
    mat.use_backface_culling = False
    return mat


def image_mat(name, img, roughness=0.62):
    mat, bsdf = new_bsdf(name)
    socket(bsdf, "Metallic").default_value = 0.0
    socket(bsdf, "Roughness").default_value = roughness
    tex = mat.node_tree.nodes.new("ShaderNodeTexImage")
    tex.image = img
    mat.node_tree.links.new(tex.outputs["Color"], socket(bsdf, "Base Color"))
    return mat


def load_image(path, name, noncolor=False):
    img = bpy.data.images.load(path)
    img.name = name
    img.colorspace_settings.name = "Non-Color" if noncolor else "sRGB"
    return img


def scaled_copy(img, size, name):
    copy = img.copy()
    copy.name = name
    copy.scale(size, size)
    return copy


def brush_image():
    w = h = 128
    img = bpy.data.images.new("steel_brush", width=w, height=h, alpha=False)
    # Set the color space before the pixels. Doing it afterwards clears the buffer.
    img.colorspace_settings.name = "Non-Color"
    rnd = random.Random(7)
    px = [0.0] * (w * h * 4)
    for y in range(h):
        for x in range(w):
            band = 0.62 + 0.38 * math.sin(x * 1.35 + 0.2 * math.sin(y * 0.15))
            grain = rnd.uniform(-0.05, 0.05)
            # Brushed range about 0.27–0.36, so the key light reads as pale steel.
            v = min(1.0, max(0.0, 0.26 + 0.08 * band + grain * 0.35))
            i = (y * w + x) * 4
            px[i] = px[i + 1] = px[i + 2] = v
            px[i + 3] = 1.0
    img.pixels.foreach_set(px)
    return bake_image(img)


def _image(name, size, color_at):
    img = bpy.data.images.new(name, width=size, height=size, alpha=False)
    # Set the color space before the pixels. Doing it afterwards clears the buffer.
    img.colorspace_settings.name = "sRGB"
    px = [0.0] * (size * size * 4)
    for y in range(size):
        for x in range(size):
            r, g, b = color_at(x, y, size)
            i = (y * size + x) * 4
            px[i] = r
            px[i + 1] = g
            px[i + 2] = b
            px[i + 3] = 1.0
    img.pixels.foreach_set(px)
    return bake_image(img)


def bake_image(img):
    """Save generated pixels and reload the file.

    The glTF exporter writes a black JPEG for an image that only exists as a
    packed pixel buffer. The block, the counter and the steel roughness all
    went out black until the pixels were saved to a real PNG first.
    """
    img.update()
    folder = os.environ.get("KNIFE_IMAGE_BAKE", "/tmp/knife-flip-images")
    os.makedirs(folder, exist_ok=True)
    path = os.path.join(folder, img.name + ".png")
    img.filepath_raw = path
    img.file_format = "PNG"
    img.save()
    loaded = bpy.data.images.load(path)
    loaded.name = img.name + "_baked"
    loaded.colorspace_settings.name = img.colorspace_settings.name
    return loaded


def endgrain_image(size=512):
    """Maple end-grain: a grid of rectangular staves, each with partial ring arcs.

    A full circle in every cell reads as coins. Real butcher block is square
    sticks glued in a grid, and the rings are arcs of a centre that is not in
    the middle of the stave.
    """
    cols, rows = 6, 8

    def color_at(x, y, n):
        cw, ch = n / cols, n / rows
        ix, iy = int(min(cols - 1, x // cw)), int(min(rows - 1, y // ch))
        fx = (x - ix * cw) / cw
        fy = (y - iy * ch) / ch
        if fx < 0.045 or fy < 0.04 or fx > 0.965 or fy > 0.97:
            return (0.42, 0.30, 0.16)
        tone = ((ix * 13 + iy * 7) % 9) / 8.0
        base_r = 0.74 + 0.14 * tone
        base_g = 0.54 + 0.10 * tone
        base_b = 0.30 + 0.08 * tone
        # Centre sits outside the stave, so the rings are arcs, not discs.
        ox = -0.25 + ((ix * 5 + 2) % 7) / 9.0
        oy = 1.25 - ((iy * 3 + 1) % 5) / 6.0
        radius = math.hypot(fx - ox, (fy - oy) * (ch / cw))
        ring = 0.5 + 0.5 * math.sin(radius * 34.0 + ix * 0.7)
        shade = 0.88 + 0.14 * ring
        return (base_r * shade, base_g * shade, base_b * shade)
    return _image("endgrain", size, color_at)


def plaster_image(size=256):
    """Warm wall paint with a slow tone shift, not a flat grey fill."""
    def color_at(x, y, n):
        wave = 0.035 * math.sin(x * 0.05 + 0.4 * math.sin(y * 0.03))
        wave += 0.02 * math.sin(y * 0.11 + x * 0.02)
        v = 0.90 + wave
        return (0.80 * v, 0.76 * v, 0.70 * v)
    return _image("plaster_wall", size, color_at)


def tile_image(size=256):
    """Off-white rectangular tiles with grout, for the backsplash."""
    def color_at(x, y, n):
        cell_x, cell_y = n / 5.0, n / 8.0
        ix, iy = int(x // cell_x), int(y // cell_y)
        fx, fy = x % cell_x, y % cell_y
        if fx < 2.2 or fy < 2.2:
            return (0.52, 0.49, 0.45)
        tone = ((ix * 3 + iy * 5) % 4) / 3.0
        return (0.84 + 0.05 * tone, 0.81 + 0.04 * tone, 0.75 + 0.03 * tone)
    return _image("backsplash", size, color_at)


def edgegrain_image(size=256):
    """Long grain for the block sides and the chips, lighter than the counter."""
    rnd = random.Random(4)

    def color_at(x, y, n):
        wave = 0.5 + 0.5 * math.sin(y * 0.55 + 2.2 * math.sin(x * 0.04))
        speck = rnd.uniform(-0.03, 0.03)
        shade = 0.90 - 0.12 * wave + speck
        return (0.78 * shade, 0.60 * shade, 0.38 * shade)
    return _image("edgegrain", size, color_at)


def counter_image(size=512):
    """Mid oak planks, darker than the maple block so the two separate."""
    def color_at(x, y, n):
        plank = int(y / (n / 5.0))
        seam = (y % (n / 5.0)) < 2.0
        grain = 0.5 + 0.5 * math.sin(x * 0.09 + plank + 1.4 * math.sin(y * 0.03))
        shade = 0.62 if seam else (0.94 - 0.14 * grain)
        return (0.64 * shade, 0.48 * shade, 0.32 * shade)
    return _image("counter", size, color_at)


def export(objs, filename):
    path = os.path.join(OUT, filename)
    bpy.ops.object.select_all(action="DESELECT")
    for obj in objs:
        obj.select_set(True)
    bpy.context.view_layer.objects.active = objs[0]
    kwargs = dict(
        filepath=path,
        export_format="GLB",
        use_selection=True,
        export_apply=True,
        export_yup=True,
        export_image_format="JPEG",
        export_jpeg_quality=82,
    )
    try:
        bpy.ops.export_scene.gltf(**kwargs, export_draco_mesh_compression_enable=True, export_draco_mesh_compression_level=7)
        kind = "draco"
    except TypeError:
        bpy.ops.export_scene.gltf(**kwargs)
        kind = "plain"
    except RuntimeError as err:
        print("draco export failed, retrying without it:", err)
        bpy.ops.export_scene.gltf(**kwargs)
        kind = "plain"
    size = os.path.getsize(path)
    print(f"wrote {filename} {size/1024:.1f} KiB ({kind})")
    return path


def bounds(obj):
    xs, ys, zs = [], [], []
    for corner in obj.bound_box:
        w = obj.matrix_world @ __import__("mathutils").Vector(corner)
        xs.append(w.x)
        ys.append(w.y)
        zs.append(w.z)
    return (min(xs), max(xs), min(ys), max(ys), min(zs), max(zs))


def report(name, objs, expect_len=None):
    xs, ys, zs = [], [], []
    for obj in objs:
        b = bounds(obj)
        xs += [b[0], b[1]]
        ys += [b[2], b[3]]
        zs += [b[4], b[5]]
    length = max(zs) - min(zs)
    print(
        f"{name}: x {min(xs):.3f}..{max(xs):.3f}  y {min(ys):.3f}..{max(ys):.3f}"
        f"  z {min(zs):.3f}..{max(zs):.3f}  length {length:.3f} m"
    )
    if expect_len is not None and abs(length - expect_len) > 0.012:
        raise SystemExit(f"{name} length {length:.3f} is not {expect_len:.3f}")


# --- knives -----------------------------------------------------------------

def stations(n, length):
    return [length * i / (n - 1) for i in range(n)]


def loft_along(name, com, from_tips, ring_at):
    # ring_at takes metres from the tip and places its own Blender Z.
    return loft(name, [ring_at(s) for s in from_tips])


def rounded_ring(z, cx, half_h, y_mid, half_t, n=12):
    ring = []
    for k in range(n):
        a = 2 * math.pi * k / n
        ring.append((cx + math.cos(a) * half_h, y_mid + math.sin(a) * half_t, z))
    return ring


def smoothstep(u):
    u = 0.0 if u < 0.0 else (1.0 if u > 1.0 else u)
    return u * u * (3.0 - 2.0 * u)


def lerp(a, b, u):
    return a + (b - a) * u


def join_gap(a, b):
    """Gap between two meshes, in metres. Zero when their triangles intersect."""
    from mathutils.bvhtree import BVHTree

    def tree(obj):
        bm = bmesh.new()
        bm.from_mesh(obj.data)
        bm.transform(obj.matrix_world)
        out = BVHTree.FromBMesh(bm)
        bm.free()
        return out

    ta, tb = tree(a), tree(b)
    if ta.overlap(tb):
        return 0.0
    gap = 1.0
    for obj, other in ((a, tb), (b, ta)):
        for vert in obj.data.vertices:
            co = obj.matrix_world @ vert.co
            _loc, _normal, _index, dist = other.find_nearest(co)
            if dist is not None and dist < gap:
                gap = dist
    return gap


def assert_joins(knife, blade_objs, handle_objs, limit=0.0002):
    """Blade and handle have to touch. A 0.2 mm crack is a failed join."""
    worst = 0.0
    for blade in blade_objs:
        for handle in handle_objs:
            gap = join_gap(blade, handle)
            print(f"  join {knife}: {handle.name} <-> {blade.name}  {gap * 1000:.3f} mm")
            worst = max(worst, gap)
    if worst > limit:
        raise SystemExit(
            f"{knife} handle is {worst * 1000:.3f} mm off the blade (limit {limit * 1000:.1f} mm)"
        )
    print(f"{knife} join ok")


def add_rivets(name, com, stations_m, radius, mat, x=0.0, y=0.0066):
    """Flush with the scale face. The cylinder ends on the wood, it does not stand proud."""
    objs = []
    for i, frm in enumerate(stations_m):
        xx = x[i] if isinstance(x, (list, tuple)) else x
        yy = y[i] if isinstance(y, (list, tuple)) else y
        obj = disc_y(f"{name}_rivet_{i}", xx, -yy, yy, z_of(com, frm), radius, segments=12)
        assign(obj, mat)
        objs.append(obj)
    return objs


def slab(z, cx, half_h, y_in, y_out):
    """A handle scale. y_in is the face against the tang, y_out the outer face."""
    return [
        (cx - half_h, y_in, z),
        (cx + half_h, y_in, z),
        (cx + half_h, y_out, z),
        (cx - half_h, y_out, z),
    ]


def d_scale(z, top_x, bot_x, y_in, y_out, n=12):
    """Oval scale with a flat face on the tang. top_x is the spine side."""
    sign = 1.0 if y_out >= 0.0 else -1.0
    inner = abs(y_in) * sign
    outer = abs(y_out) * sign
    cx = (top_x + bot_x) * 0.5
    rx = max(abs(bot_x - top_x) * 0.5, 1.5e-4)
    pts = [(top_x, inner, z)]
    for i in range(1, n - 1):
        a = math.pi * i / (n - 1)
        x = cx - math.cos(a) * rx
        y = inner + (outer - inner) * math.sin(a)
        pts.append((x, y, z))
    pts.append((bot_x, inner, z))
    return pts


def piece_lerp(frm, keys, index):
    if frm <= keys[0][0]:
        return keys[0][index]
    if frm >= keys[-1][0]:
        return keys[-1][index]
    for a, b in zip(keys, keys[1:]):
        if frm <= b[0]:
            u = 0.0 if b[0] == a[0] else (frm - a[0]) / (b[0] - a[0])
            return lerp(a[index], b[index], u)
    return keys[-1][index]


def assert_layout(
    name, handles, axis_x, blade_len, angle_rng, offset_rng, ratio_rng,
    mode="top", z_lo=None, z_hi=None, front_z=None,
):
    """Handle axis, spine offset, and handle/blade length. Ranges are in KNIFE_CONSTRUCTION.md."""
    verts = []
    for obj in handles:
        for vert in obj.data.vertices:
            co = obj.matrix_world @ vert.co
            if z_lo is not None and co.z < z_lo:
                continue
            if z_hi is not None and co.z > z_hi:
                continue
            verts.append(co)
    if len(verts) < 8:
        raise SystemExit(f"{name} layout has no handle vertices")
    z0 = min(v.z for v in verts)
    z1 = max(v.z for v in verts)
    handle_len = z1 - z0
    band = max(0.008, handle_len * 0.12)
    front = [v for v in verts if v.z >= z1 - band]
    rear = [v for v in verts if v.z <= z0 + band]

    def bounds_x(group):
        xs = [v.x for v in group]
        return min(xs), max(xs)

    f0, f1 = bounds_x(front)
    r0, r1 = bounds_x(rear)
    front_mid = (f0 + f1) * 0.5
    rear_mid = (r0 + r1) * 0.5
    # Positive angle: the butt sits toward the edge (+X), a drop off the spine.
    angle = math.degrees(math.atan2(rear_mid - front_mid, max(handle_len, 1e-6)))
    sample = front_mid if mode == "center" else f0
    offset_mm = (sample - axis_x) * 1000.0
    # front_z counts the bolster, which is on the blade mesh, as part of the handle.
    ratio_len = (front_z - z0) if front_z is not None else handle_len
    ratio = ratio_len / blade_len
    print(
        f"  layout {name}: angle {angle:+.2f} deg (range {angle_rng[0]:+.1f}..{angle_rng[1]:+.1f})"
        f"  offset {offset_mm:+.2f} mm (range {offset_rng[0]:+.1f}..{offset_rng[1]:+.1f})"
        f"  handle/blade {ratio:.3f} (range {ratio_rng[0]:.2f}..{ratio_rng[1]:.2f})"
    )
    if not (angle_rng[0] <= angle <= angle_rng[1]):
        raise SystemExit(f"{name} handle axis is {angle:.2f} deg off the spine")
    if not (offset_rng[0] <= offset_mm <= offset_rng[1]):
        raise SystemExit(f"{name} handle is {offset_mm:.2f} mm off its spine line")
    if not (ratio_rng[0] <= ratio <= ratio_rng[1]):
        raise SystemExit(f"{name} handle/blade {ratio:.3f} is outside {ratio_rng}")
    print(f"{name} layout ok")


def build_chef(steel_mat, wood_mat, brass_mat):
    """Spine runs into the handle. One steel piece: blade, bolster, full tang."""
    com = CHEF_COM
    # Heights are metres up from the edge line. X on the mesh is the negative of that.
    # (from the tip, top of the wood, bottom of the wood, outer face).
    # The top starts on the spine and drops 3 mm. Girth swells, then the butt flares a little.
    handle = (
        (0.218, 0.0480, 0.0220, 0.0026),
        (0.248, 0.0474, 0.0174, 0.0088),
        (0.278, 0.0466, 0.0146, 0.0102),
        (0.308, 0.0456, 0.0156, 0.0096),
        (0.324, 0.0452, 0.0162, 0.0086),
        (0.330, 0.0460, 0.0175, 0.0072),
    )

    def top_of(frm):
        return piece_lerp(frm, handle, 1)

    def bot_of(frm):
        return piece_lerp(frm, handle, 2)

    def outer_of(frm):
        return piece_lerp(frm, handle, 3)

    def choil(frm):
        """Quarter-ellipse from the heel up to the handle bottom. Concave, no notch."""
        u = max(0.0, min(1.0, (frm - 0.200) / 0.018))
        return bot_of(0.218) * math.sqrt(max(0.0, 2.0 * u - u * u))

    def ring(frm):
        z = z_of(com, frm)
        if frm <= 0.200:
            spine, edge, thick = blades.chef_station(frm)
            return flat_grind_ring(z, spine, edge, thick, bevel=0.0015, grind_at=0.10)
        heel_t = blades.chef_station(0.200)[2]
        if frm <= 0.218:
            top = -0.048
            bot = -choil(frm)
            u = smoothstep((frm - 0.200) / 0.012)
            thick = lerp(heel_t, 0.0086, min(1.0, u * 1.15))
            return flat_grind_ring(
                z, top, bot, thick, bevel=0.0024, grind_at=0.08,
                edge_thick=lerp(0.00016, thick * 0.55, u),
            )
        # Tang is the handle outline. The wood is inset, so the spine line is steel.
        top = -top_of(frm)
        bot = -bot_of(frm)
        u = smoothstep((frm - 0.218) / 0.014)
        thick = lerp(0.0080, 0.0020, u)
        if frm >= 0.232:
            return flat_grind_ring(z, top, bot, 0.0020, bevel=0.003, grind_at=0.2, blunt=True)
        return flat_grind_ring(
            z, top, bot, thick, bevel=0.0026, grind_at=0.14,
            edge_thick=lerp(0.0044, 0.0011, u),
        )

    frs = [0.200 * i / 48 for i in range(49)]
    frs += [0.203, 0.206, 0.209, 0.212, 0.215, 0.218, 0.222, 0.228, 0.248, 0.278, 0.308, 0.330]
    blade_obj = loft("chef_blade", [ring(frm) for frm in frs])
    assign(blade_obj, steel_mat)
    objs = [blade_obj]
    scales = []
    for sign, tag in ((1, "a"), (-1, "b")):
        rings = [
            d_scale(z_of(com, frm), -top, -bot, sign * 0.0005, sign * outer)
            for frm, top, bot, outer in handle
        ]
        scale = loft(f"chef_scale_{tag}", rings)
        assign(scale, wood_mat)
        uv_smart(scale)
        objs.append(scale)
        scales.append(scale)
    rivet_at = (0.252, 0.286, 0.316)
    objs.extend(add_rivets(
        "chef", com, rivet_at, 0.0032, brass_mat,
        x=[-(top_of(f) + bot_of(f)) * 0.5 for f in rivet_at],
        y=[outer_of(f) for f in rivet_at],
    ))
    assert_joins("chef", [blade_obj], scales)
    assert_layout(
        "chef", scales, axis_x=-0.048, blade_len=0.200,
        angle_rng=(-1.0, 3.5), offset_rng=(-0.6, 4.0), ratio_rng=(0.55, 0.75),
        front_z=com - 0.200,
    )
    report("chef", objs, 0.330)
    return objs


def cord_turn(name, z, half_w, half_t, strand=0.00105, bury=0.00022, around=28, seg=8):
    """One round of cord. The strand straddles the diamond so it touches the steel."""
    h = max(half_w, 1.5e-4)
    t = max(half_t, 1.5e-4)
    corners = [(0.0, t), (h, 0.0), (0.0, -t), (-h, 0.0)]
    per = max(4, around // 4)
    samples = []
    for i in range(4):
        ax, ay = corners[i]
        bx, by = corners[(i + 1) % 4]
        ex, ey = bx - ax, by - ay
        # Clockwise diamond: the outward normal is the counter-clockwise turn of the edge.
        nx, ny = -ey, ex
        nlen = math.hypot(nx, ny) or 1.0
        nx, ny = nx / nlen, ny / nlen
        for k in range(per):
            u = k / per
            samples.append((ax + ex * u - nx * bury, ay + ey * u - ny * bury, nx, ny))
    verts = []
    for cx, cy, nx, ny in samples:
        for s in range(seg):
            a = 2 * math.pi * s / seg
            verts.append((
                cx + math.cos(a) * strand * nx,
                cy + math.cos(a) * strand * ny,
                z + math.sin(a) * strand,
            ))
    n = len(samples)
    faces = []
    for i in range(n):
        for s in range(seg):
            a = i * seg + s
            b = i * seg + (s + 1) % seg
            c = ((i + 1) % n) * seg + (s + 1) % seg
            d = ((i + 1) % n) * seg + s
            faces.append((a, b, c, d))
    return link_mesh(name, verts, faces)


def build_throwing(steel_mat, cord_mat):
    com = THROW_COM

    def ring(s):
        neg, pos, thick = blades.throw_station(s)
        return diamond_ring(z_of(com, s), pos, thick)

    obj = loft_along("throw_blade", com, stations(64, 0.280), ring)
    assign(obj, steel_mat)
    objs = [obj]
    # Each turn is a round strand whose centreline sits on the steel, so the
    # wrap crosses the surface (no gap) and stays inside the 5 mm stock.
    cords = []
    for i, frm in enumerate((0.152, 0.166, 0.180, 0.194, 0.208, 0.222, 0.236, 0.250)):
        neg, pos, thick = blades.throw_station(frm)
        band = cord_turn(f"cord_{i}", z_of(com, frm), max(abs(neg), pos), thick * 0.5)
        assign(band, cord_mat)
        objs.append(band)
        cords.append(band)
    assert_joins("throwing", [obj], cords)
    # The handle is the rear half of the same bar. Its axis is the centreline.
    assert_layout(
        "throwing", [obj], axis_x=0.0, blade_len=0.140,
        angle_rng=(-1.0, 1.0), offset_rng=(-0.6, 0.6), ratio_rng=(0.90, 1.10),
        mode="center", z_lo=-0.141, z_hi=0.004,
    )
    report("throwing", objs, 0.280)
    half = THROWING_THICKNESS * 0.5
    peak = max(abs(co) for o in objs for co in (v.co.y for v in o.data.vertices))
    if peak > half + 1e-4:
        raise SystemExit(f"throwing thickness {peak * 2:.4f} m exceeds {THROWING_THICKNESS:.4f} m")
    return objs


def build_pocket(steel_mat, g10, liner_mat, pin_mat):
    com = POCKET_COM

    def ring(frm):
        z = z_of(com, frm)
        if frm <= 0.085:
            spine, edge, thick, blunt = blades.pocket_station(frm)
            return flat_grind_ring(z, spine, edge, thick, bevel=0.0012, grind_at=0.14, blunt=blunt)
        # Tang stays inside the handle, on the same centre as the spine-aligned scales.
        u = smoothstep((frm - 0.085) / 0.018)
        spine0, edge0, thick0, _blunt = blades.pocket_station(0.085)
        return flat_grind_ring(
            z,
            lerp(spine0, -0.0222, u),
            lerp(edge0, -0.0006, u),
            lerp(thick0, 0.0018, u),
            bevel=0.002,
            grind_at=0.2,
            blunt=True,
        )

    blade_obj = loft("pocket_blade", [ring(frm) for frm in stations(48, 0.085) + [0.092, 0.100, 0.112]])
    assign(blade_obj, steel_mat)
    objs = [blade_obj]
    # Thumb stud on the blade face, near the spine, just ahead of the ricasso.
    stud = disc_y("pocket_stud", -0.016, 0.0014, 0.0046, z_of(com, 0.058), 0.0022, segments=10)
    assign(stud, pin_mat)
    objs.append(stud)
    # Spine side stays on the blade spine. The edge sits inside the handle.
    outline = (
        (0.084, -0.0242, 0.0016),
        (0.112, -0.0244, 0.0020),
        (0.148, -0.0240, 0.0020),
        (0.178, -0.0234, 0.0012),
        (0.200, -0.0228, 0.0006),
    )

    def center_half(frm):
        top = piece_lerp(frm, outline, 1)
        bot = piece_lerp(frm, outline, 2)
        return (top + bot) * 0.5, abs(bot - top) * 0.5
    liners = []
    for sign, tag, mat, y_mid, half_t in (
        # Inner face at ±0.40 mm, so it cuts into the 1.8 mm tang.
        (1, "lin_a", liner_mat, 0.00130, 0.00090),
        (-1, "lin_b", liner_mat, -0.00130, 0.00090),
        (1, "sc_a", g10, 0.0044, 0.0023),
        (-1, "sc_b", g10, -0.0044, 0.0023),
    ):
        rings = [
            rounded_ring(z_of(com, frm), *center_half(frm), y_mid, half_t)
            for frm, _top, _bot in outline
        ]
        part = loft(f"pocket_{tag}", rings)
        assign(part, mat)
        if mat == g10:
            uv_smart(part)
        objs.append(part)
        if tag.startswith("lin"):
            liners.append(part)
    # Pivot pin through the tang and both liners.
    pivot_x, _pivot_half = center_half(0.090)
    pivot = disc_y("pocket_pivot", pivot_x, -0.0080, 0.0080, z_of(com, 0.090), 0.0044, segments=14)
    assign(pivot, pin_mat)
    objs.append(pivot)
    assert_joins("pocket", [blade_obj], liners + [pivot])
    assert_layout(
        "pocket", liners, axis_x=-0.024, blade_len=0.085,
        angle_rng=(-2.0, 2.5), offset_rng=(-1.5, 1.5), ratio_rng=(1.05, 1.50),
    )
    clip = box(
        "pocket_clip",
        -0.020, -0.014,
        0.0068, 0.0082,
        z_of(com, 0.118), z_of(com, 0.188),
    )
    assign(clip, liner_mat)
    objs.append(clip)
    report("pocket", objs, 0.200)
    return objs


def build_butterfly(steel_mat, scale_mat, pin_mat):
    com = FLY_COM
    cx = -0.011

    def ring(frm):
        z = z_of(com, frm)
        if frm <= 0.100:
            spine, edge, thick = blades.fly_station(frm)
            return flat_grind_ring(z, spine, edge, thick, bevel=0.0012, grind_at=0.16)
        # Tang thickens immediately so it fills the channel at the pivot pin.
        u = smoothstep((frm - 0.100) / 0.016)
        thick_u = smoothstep((frm - 0.100) / 0.005)
        spine0, edge0, thick0 = blades.fly_station(0.100)
        return flat_grind_ring(
            z,
            lerp(spine0, -0.020, u),
            lerp(edge0, -0.002, u),
            lerp(thick0, 0.0066, thick_u),
            bevel=0.002,
            grind_at=0.2,
            blunt=True,
        )

    blade_obj = loft("fly_blade", [ring(frm) for frm in stations(40, 0.100) + [0.104, 0.112, 0.122]])
    assign(blade_obj, steel_mat)
    objs = [blade_obj]
    # Kicker on the spine, overlapping the handle fronts. It is the close-stop.
    kicker = box("fly_kicker", -0.027, -0.018, -0.0034, 0.0034, z_of(com, 0.096), z_of(com, 0.112))
    assign(kicker, steel_mat)
    objs.append(kicker)
    handles = []
    for sign, tag in ((1, "a"), (-1, "b")):
        rings = []
        for frm, half in ((0.100, 0.0125), (0.155, 0.0130), (0.205, 0.0126), (0.242, 0.0116)):
            z = z_of(com, frm)
            rings.append([
                (cx - half, sign * 0.0028, z),
                (cx + half, sign * 0.0028, z),
                (cx + half, sign * 0.0066, z),
                (cx - half, sign * 0.0066, z),
            ])
        handle = loft(f"fly_handle_{tag}", rings)
        assign(handle, scale_mat)
        objs.append(handle)
        handles.append(handle)
    latch = box("fly_latch", -0.018, -0.004, -0.0024, 0.0024, z_of(com, 0.228), z_of(com, 0.248))
    assign(latch, pin_mat)
    objs.append(latch)
    pins = []
    for frm, rad in ((0.104, 0.0028), (0.200, 0.0018), (0.236, 0.0018)):
        pin = disc_y(f"fly_pin_{frm}", cx, -0.0074, 0.0074, z_of(com, frm), rad, segments=12)
        assign(pin, pin_mat)
        objs.append(pin)
        pins.append(pin)
    assert_joins("butterfly", [blade_obj], handles)
    assert_joins("butterfly kicker", [kicker], handles)
    assert_joins("butterfly pin", [blade_obj], [pins[0]])
    assert_layout(
        "butterfly", handles, axis_x=-0.011, blade_len=0.100,
        angle_rng=(-2.0, 2.0), offset_rng=(-1.2, 1.2), ratio_rng=(1.15, 1.60),
        mode="center",
    )
    report("butterfly", objs, 0.250)
    return objs


def build_cleaver(steel_mat, wood_mat, brass_mat):
    com = CLEAVER_COM
    # (from the tip, spine-side X, edge-side X, outer face). Top sits on the spine.
    handle = (
        (0.188, -0.0900, -0.0570, 0.0026),
        (0.228, -0.0892, -0.0556, 0.0096),
        (0.268, -0.0884, -0.0554, 0.0092),
        (0.300, -0.0874, -0.0564, 0.0074),
    )

    def ring(frm):
        z = z_of(com, frm)
        spine, edge, thick = blades.cleaver_station(frm)
        # The rectangle stops at the heel. The tang is a separate tongue at the top.
        return flat_grind_ring(z, spine, edge, thick, bevel=0.0020, grind_at=0.40)

    frs = [0.180 * i / 40 for i in range(41)]
    blade_obj = loft("cleaver_blade", [ring(frm) for frm in frs])
    assign(blade_obj, steel_mat)
    objs = [blade_obj]
    # Hole near the top front corner, about 14 mm down from the spine.
    cutter = disc_y("cleaver_hole_cut", -0.076, -0.02, 0.02, z_of(com, 0.028), 0.009, segments=16)
    mod = blade_obj.modifiers.new("hole", "BOOLEAN")
    mod.operation = "DIFFERENCE"
    mod.object = cutter
    mod.solver = "EXACT"
    bpy.context.view_layer.objects.active = blade_obj
    try:
        bpy.ops.object.modifier_apply(modifier=mod.name)
        bpy.data.objects.remove(cutter, do_unlink=True)
    except RuntimeError as err:
        print("cleaver hole boolean failed:", err)
        bpy.data.objects.remove(cutter, do_unlink=True)
    # Tang is a tongue in the top of the heel. It starts inside the blade so the
    # rectangular heel stays flat, then runs the length of the handle.
    def tang_ring(frm):
        z = z_of(com, frm)
        if frm <= 0.182:
            top, bot, thick = -0.0900, -0.0560, 0.0042
        else:
            top = piece_lerp(frm, handle, 1)
            bot = piece_lerp(frm, handle, 2)
            thick = 0.0022
        return flat_grind_ring(z, top, bot, thick, bevel=0.003, grind_at=0.2, blunt=True)

    tang = loft("cleaver_tang", [tang_ring(frm) for frm in (0.168, 0.180, 0.190, 0.230, 0.270, 0.300)])
    assign(tang, steel_mat)
    objs.append(tang)
    # Ferrule caps the top of the heel and the front of the scales. Top is the spine.
    ferrule = loft("cleaver_ferrule", [
        flat_grind_ring(z_of(com, 0.170), -0.0900, -0.0545, 0.0094, bevel=0.003, grind_at=0.15, blunt=True),
        flat_grind_ring(z_of(com, 0.198), -0.0894, -0.0582, 0.0084, bevel=0.003, grind_at=0.15, blunt=True),
    ])
    assign(ferrule, brass_mat)
    objs.append(ferrule)
    scales = []
    for sign, tag in ((1, "a"), (-1, "b")):
        rings = [
            d_scale(z_of(com, frm), top, bot, sign * 0.00055, sign * outer)
            for frm, top, bot, outer in handle
        ]
        scale = loft(f"cleaver_scale_{tag}", rings)
        assign(scale, wood_mat)
        uv_smart(scale)
        objs.append(scale)
        scales.append(scale)
    rivet_at = (0.232, 0.272)
    objs.extend(add_rivets(
        "cleaver", com, rivet_at, 0.0034, brass_mat,
        x=[(piece_lerp(f, handle, 1) + piece_lerp(f, handle, 2)) * 0.5 for f in rivet_at],
        y=[piece_lerp(f, handle, 3) for f in rivet_at],
    ))
    assert_joins("cleaver tang", [blade_obj], [tang])
    assert_joins("cleaver", [tang], scales)
    assert_joins("cleaver ferrule", [blade_obj], [ferrule])
    assert_joins("cleaver ferrule-handle", [ferrule], scales)
    assert_layout(
        "cleaver", scales, axis_x=-0.090, blade_len=0.180,
        angle_rng=(-1.0, 3.5), offset_rng=(-0.8, 4.0), ratio_rng=(0.55, 0.80),
        front_z=com - 0.180,
    )
    report("cleaver", objs, 0.300)
    return objs


# --- bottle, water, block, room --------------------------------------------

BOTTLE_PROFILE = [
    (0.000, 0.0240),
    (0.008, 0.0315),
    (0.148, 0.0315),
    (0.166, 0.0180),
    (0.182, 0.0130),
    (0.196, 0.0122),
    (0.204, 0.0120),
]


def build_bottle(plastic, cap_mat):
    shell = tube("bottle_shell", BOTTLE_PROFILE, segments=32, wall=0.00065)
    assign(shell, plastic)
    # Ribbed cap. Origin of the bottle file is the base (z = 0).
    rings = []
    for i, z in enumerate((0.188, 0.194, 0.198, 0.204)):
        r = 0.0148 + (0.0006 if i % 2 else 0.0)
        ring = []
        for k in range(20):
            a = 2 * math.pi * k / 20
            wobble = 0.00045 * (1 if k % 2 == 0 else -0.15)
            ring.append((math.cos(a) * (r + wobble), math.sin(a) * (r + wobble), z))
        rings.append(ring)
    cap = loft("bottle_cap", rings)
    assign(cap, cap_mat)
    report("bottle", [shell, cap], 0.204)
    return [shell, cap]


def build_water_body(water_mat):
    # Unit height so a loader can scale Y. Radius is inside the bottle wall (inner ~30.9 mm).
    # The app does not scale this mesh: it builds a plane-cut column at the same radius.
    obj = lathe_solid("water_body", [(0.0, 0.0004), (0.0, 0.0292), (1.0, 0.0292)], segments=28)
    assign(obj, water_mat)
    report("water_body", [obj], 1.0)
    return [obj]


def build_water_neck(water_mat):
    # Interior of the shoulder and neck, base of this mesh at the shoulder (local z = 0).
    # Stops at 40 mm, under the cap. The app builds its own plane-cut surface at these radii.
    profile = [
        (0.000, 0.0004),
        (0.000, 0.0292),
        (0.018, 0.0162),
        (0.034, 0.0112),
        (0.040, 0.0106),
    ]
    obj = lathe_solid("water_neck", profile, segments=24)
    assign(obj, water_mat)
    report("water_neck", [obj], 0.040)
    return [obj]


def shadow_disc(name, cx, cy, cz, rx, ry, color, alpha, segments=28):
    """A flat ellipse on the counter. Alpha reads as a contact shadow."""
    verts = [(cx, cy, cz)]
    for i in range(segments):
        a = 2 * math.pi * i / segments
        verts.append((cx + math.cos(a) * rx, cy + math.sin(a) * ry, cz))
    faces = [(0, 1 + i, 1 + (i + 1) % segments) for i in range(segments)]
    obj = link_mesh(name, verts, faces)
    mat, bsdf = new_bsdf(name + "Mat")
    socket(bsdf, "Base Color").default_value = (*color, 1)
    socket(bsdf, "Roughness").default_value = 1.0
    socket(bsdf, "Metallic").default_value = 0.0
    socket(bsdf, "Alpha").default_value = alpha
    mat.blend_method = "BLEND"
    mat.use_backface_culling = False
    mat.show_transparent_back = True
    assign(obj, mat)
    return obj


def build_block(top_mat, side_mat):
    # Top face just under z = 0. The bottom is sunk a few millimetres into the
    # counter so the seam cannot read as a gap. Depth is along Y.
    obj = bevel_box("block", 0.0, 0.58, -0.11, 0.11, -0.104, -0.004, 0.006)
    obj.data.materials.append(top_mat)
    obj.data.materials.append(side_mat)
    for poly in obj.data.polygons:
        poly.material_index = 0 if poly.normal.z > 0.55 else 1
    # About a 4 cm stave on the 58×22 cm top, so the rings read as a grid of sticks.
    cube_uv(obj, scale=3.6)
    # Soft contact shadow on the counter, slightly larger than the footprint.
    # A rim just past the block. A wider disc covers the near counter and reads as a black slab.
    shade = (0.10, 0.07, 0.04)
    outer = shadow_disc("block_shadow", 0.29, 0.0, -0.0994, 0.32, 0.13, shade, 0.32)
    inner = shadow_disc("block_shadow_tight", 0.29, 0.0, -0.0992, 0.30, 0.115, shade, 0.50)
    report("block", [obj])
    return [obj, outer, inner]


def build_chip(wood_mat, seed):
    """One small irregular splinter, a bit over a centimetre, origin at its centre."""
    rng = random.Random(seed)
    length = 0.008 + rng.random() * 0.005
    rings = []
    for i in range(5):
        t = i / 4
        z = -length * 0.5 + length * t
        taper = math.sin(t * math.pi) ** 0.55
        ring = []
        for k in range(6):
            a = 2 * math.pi * k / 6 + rng.random() * 0.25
            rx = (0.00045 + 0.0015 * taper) * (0.65 + 0.7 * rng.random())
            ry = (0.00035 + 0.0011 * taper) * (0.6 + 0.8 * rng.random())
            ring.append((math.cos(a) * rx, math.sin(a) * ry, z))
        rings.append(ring)
    obj = loft(f"chip_{seed}", rings)
    assign(obj, wood_mat)
    uv_smart(obj)
    report(f"chip{seed}", [obj])
    return [obj]


def mug(ceramic):
    """A cup with a handle, on the left of the counter. The old prop was a plain cylinder."""
    body = tube("mug", [
        (0.0, 0.030),
        (0.006, 0.036),
        (0.072, 0.038),
        (0.088, 0.034),
    ], segments=24, wall=0.0022)
    assign(body, ceramic)
    # C-shaped handle in the XY plane, opening toward the cup, on the camera side (-Y).
    rings = []
    steps = 14
    for i in range(steps):
        a = math.radians(210) + math.radians(300) * i / (steps - 1)
        cy = -0.034 + math.cos(a) * 0.018
        cz = 0.046 + math.sin(a) * 0.022
        ring = []
        for k in range(8):
            b = 2 * math.pi * k / 8
            ring.append((
                math.cos(b) * 0.0032,
                cy + math.sin(b) * 0.0032,
                cz,
            ))
        rings.append(ring)
    handle = loft("mug_handle", rings)
    assign(handle, ceramic)
    for obj in (body, handle):
        obj.location = (-0.32, 0.05, -0.10)
    bpy.context.view_layer.update()
    return [body, handle]


def build_room(counter_mat, wall_mat, glass_mat, plaster_mat, frame_mat):
    objs = []
    # Counter top. Physics block sits on z = -0.10. Front (camera) is -Y.
    top = bevel_box("counter", -0.55, 1.25, -0.34, 0.42, -0.142, -0.100, 0.008)
    assign(top, counter_mat)
    # One Wood051 repeat across most of the counter, so the grain is not a tiled smear.
    cube_uv(top, scale=0.48)
    objs.append(top)
    # Cabinet under the counter. Gameplay cameras keep this below the frame.
    cabinet = box("cabinet", -0.52, 1.22, -0.30, 0.38, -0.90, -0.142)
    assign(cabinet, counter_mat)
    cube_uv(cabinet, scale=0.48)
    objs.append(cabinet)
    kick = box("kick", -0.48, 1.18, -0.28, 0.34, -0.90, -0.84)
    assign(kick, wall_mat)
    objs.append(kick)
    # Back wall, tall enough to meet the ceiling. The window is a hole via boolean.
    wall = box("back_wall", -1.20, 2.40, 0.46, 0.52, -0.90, 2.55)
    cutter = box("window_cut", 0.55, 1.15, 0.40, 0.60, 0.35, 0.95)
    mod = wall.modifiers.new("window", "BOOLEAN")
    mod.operation = "DIFFERENCE"
    mod.object = cutter
    mod.solver = "EXACT"
    bpy.context.view_layer.objects.active = wall
    try:
        bpy.ops.object.modifier_apply(modifier=mod.name)
        bpy.data.objects.remove(cutter, do_unlink=True)
    except RuntimeError as err:
        print("window boolean failed:", err)
        bpy.data.objects.remove(cutter, do_unlink=True)
    painted = image_mat("WallPaint", plaster_image(), roughness=0.88)
    assign(wall, painted)
    cube_uv(wall, scale=0.28)
    objs.append(wall)
    # Tile backsplash between the counter and the window, proud of the wall.
    splash = box("backsplash", -0.20, 1.40, 0.418, 0.452, -0.100, 0.32)
    assign(splash, image_mat("Tiles", tile_image(), roughness=0.45))
    cube_uv(splash, scale=2.0)
    objs.append(splash)
    side = box("side_wall", -1.20, -1.14, -2.20, 0.52, -0.90, 2.55)
    assign(side, painted)
    cube_uv(side, scale=0.28)
    objs.append(side)
    # Right wall and a front wall behind every gameplay camera, plus a ceiling.
    # Together with the floor they hide the photograph that used to be the skybox.
    right = box("right_wall", 2.34, 2.40, -2.20, 0.52, -0.90, 2.55)
    assign(right, painted)
    cube_uv(right, scale=0.28)
    objs.append(right)
    front = box("front_wall", -1.20, 2.40, -2.26, -2.20, -0.90, 2.55)
    assign(front, painted)
    cube_uv(front, scale=0.28)
    objs.append(front)
    ceiling = box("ceiling", -1.20, 2.40, -2.26, 0.52, 2.50, 2.58)
    assign(ceiling, plaster_mat)
    objs.append(ceiling)
    floor = box("floor", -1.30, 2.50, -2.30, 0.70, -0.94, -0.90)
    assign(floor, plaster_mat)
    objs.append(floor)
    # Opaque panel just outside the window, so the glass does not open onto the IBL photo.
    outside = box("window_backing", 0.52, 1.18, 0.56, 0.64, 0.32, 0.98)
    assign(outside, paint("Exterior", (0.62, 0.74, 0.82), roughness=0.9))
    objs.append(outside)
    # Window frame and a pane of glass, slightly proud of the wall (smaller Y).
    frame_parts = [
        box("frame_l", 0.55, 0.58, 0.44, 0.50, 0.35, 0.95),
        box("frame_r", 1.12, 1.15, 0.44, 0.50, 0.35, 0.95),
        box("frame_b", 0.55, 1.15, 0.44, 0.50, 0.35, 0.40),
        box("frame_t", 0.55, 1.15, 0.44, 0.50, 0.90, 0.95),
        box("mullion", 0.83, 0.86, 0.44, 0.495, 0.40, 0.90),
    ]
    for part in frame_parts:
        assign(part, frame_mat)
        objs.append(part)
    pane = box("pane", 0.58, 1.12, 0.455, 0.462, 0.40, 0.90)
    assign(pane, glass_mat)
    objs.append(pane)
    # Far left of the counter, out of the toss.
    ceramic = paint("Ceramic", (0.90, 0.86, 0.78), roughness=0.42)
    objs.extend(mug(ceramic))
    report("room", objs)
    return objs


def main():
    os.makedirs(OUT, exist_ok=True)
    fresh_scene()
    color = load_image(os.path.join(TEX, "Wood051_1K_Color.jpg"), "wood_color")
    normal = load_image(os.path.join(TEX, "Wood051_1K_NormalGL.jpg"), "wood_normal", noncolor=True)
    rough = load_image(os.path.join(TEX, "Wood051_1K_Roughness.jpg"), "wood_rough", noncolor=True)
    color_s = scaled_copy(color, 512, "wood_color_512")
    normal_s = scaled_copy(normal, 512, "wood_normal_512")
    rough_s = scaled_copy(rough, 512, "wood_rough_512")
    brush = brush_image()

    steel_mat = steel(brush)
    brass_mat = brass()
    wood_handle = wood(color_s, normal_s, rough_s, "WoodHandle")
    endgrain = image_mat("EndGrain", endgrain_image(), roughness=0.58)
    edgegrain = image_mat("EdgeGrain", edgegrain_image(), roughness=0.62)
    # Full 1K Wood051, not the 512 px handle copy and not the procedural oak.
    counter = wood(color, normal, rough, "CounterWood")
    cord = paint("Cord", (0.45, 0.16, 0.10), roughness=0.72)
    g10 = paint("G10", (0.72, 0.58, 0.36), roughness=0.48)
    liner = paint("Liner", (0.55, 0.57, 0.60), roughness=0.35, metallic=0.9)
    pin = paint("Pin", (0.62, 0.48, 0.22), roughness=0.3, metallic=1.0)
    fly_scale = paint("Channels", (0.28, 0.30, 0.33), roughness=0.34, metallic=0.85)
    # Alpha glass, not transmission: the water has to show through.
    plastic = alpha_glass("PET", (0.93, 0.96, 0.97), alpha=0.28, roughness=0.05)
    cap_mat = paint("Cap", (0.16, 0.38, 0.40), roughness=0.38)
    water_mat = liquid("Water", (0.32, 0.70, 0.90))
    wall = paint("Wall", (0.86, 0.82, 0.74), roughness=0.85)
    plaster = paint("Plaster", (0.78, 0.74, 0.68), roughness=0.9)
    window_glass = alpha_glass("Window", (0.85, 0.92, 0.95), alpha=0.22, roughness=0.04)
    frame = paint("Frame", (0.62, 0.46, 0.30), roughness=0.5)

    groups = {
        "chef.glb": build_chef(steel_mat, wood_handle, brass_mat),
        "throwing.glb": build_throwing(steel_mat, cord),
        "pocket.glb": build_pocket(steel_mat, g10, liner, pin),
        "bottle.glb": build_bottle(plastic, cap_mat),
        "water_body.glb": build_water_body(water_mat),
        "water_neck.glb": build_water_neck(water_mat),
        "chip0.glb": build_chip(edgegrain, 1),
        "chip1.glb": build_chip(edgegrain, 2),
        "chip2.glb": build_chip(edgegrain, 3),
        "chip3.glb": build_chip(edgegrain, 4),
        "room.glb": build_room(counter, wall, window_glass, plaster, frame) + build_block(endgrain, edgegrain),
    }
    if os.environ.get("KNIFE_SHELVED") == "1":
        groups["butterfly.glb"] = build_butterfly(steel_mat, fly_scale, pin)
        groups["cleaver.glb"] = build_cleaver(steel_mat, wood_handle, brass_mat)
    # chip loft uses wedge_ring which has a fixed point count; report length separately.
    for filename, objs in groups.items():
        export(objs, filename)
    print("knife-flip models written to", OUT)


if __name__ == "__main__":
    main()
