#!/usr/bin/env python3
"""Headless Blender generator for Knife Flip.

Builds original meshes (no downloaded models) and exports GLB for Filament:

    blender --background --python tools/knife-flip/generate_models.py

Knives are authored in metres with the centre of mass at the origin and the tip
along Blender +Z. The glTF exporter turns that into glTF +Y, which is "tip up"
at theta = 0 in FlipPhysics. The bottle's origin is the centre of its base.
Nothing here changes FlipPhysics; the proportions are the ones that file already
uses (an 8-inch chef's knife, a centre-balanced thrower, an open folder, an open
balisong, an 18 by 9 cm cleaver, Dekker's 204 mm bottle).

The only image files read from disk are the CC0 wood maps in ./textures and they
are embedded in the GLBs. Steel roughness is drawn in this script.
"""

import math
import os
import random
import sys

import bpy
import bmesh

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))
OUT = os.path.join(REPO, "app", "src", "main", "assets", "knife-flip")
TEX = os.path.join(SCRIPT_DIR, "textures")

# Centres of mass, metres from the tip. Same parts as FlipPhysics.knife().
CHEF_COM = (0.090 * 0.108 + 0.040 * 0.206 + 0.090 * 0.272) / 0.220
THROW_COM = 0.140
POCKET_COM = (0.032 * 0.042 + 0.096 * 0.152) / 0.128
FLY_COM = (0.055 * 0.055 + 0.100 * 0.190) / 0.155
CLEAVER_COM = (0.330 * 0.086 + 0.075 * 0.242) / 0.405


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
    mat, bsdf = new_bsdf("Steel")
    socket(bsdf, "Base Color").default_value = (0.78, 0.80, 0.83, 1)
    socket(bsdf, "Metallic").default_value = 1.0
    socket(bsdf, "Roughness").default_value = 0.22
    if "Anisotropic" in bsdf.inputs:
        socket(bsdf, "Anisotropic").default_value = 0.65
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
    rnd = random.Random(7)
    px = [0.0] * (w * h * 4)
    for y in range(h):
        for x in range(w):
            band = 0.62 + 0.38 * math.sin(x * 1.35 + 0.2 * math.sin(y * 0.15))
            grain = rnd.uniform(-0.05, 0.05)
            v = min(1.0, max(0.0, 0.16 + 0.18 * band + grain))
            i = (y * w + x) * 4
            px[i] = px[i + 1] = px[i + 2] = v
            px[i + 3] = 1.0
    img.pixels.foreach_set(px)
    img.colorspace_settings.name = "Non-Color"
    img.pack()
    return img


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

def chef_profile(t):
    # t = 0 at the tip, 1 at the heel. Spine is -X, edge (belly) is +X.
    spine = -0.0012 - 0.0065 * (t ** 0.85)
    belly = math.sin(t * math.pi * 0.92) ** 1.15
    edge = 0.003 * t + 0.046 * belly
    if t > 0.92:
        # Choil: the edge turns back up into the heel.
        k = (t - 0.92) / 0.08
        edge = edge * (1 - k) + (spine + 0.012) * k
    return spine, edge


def chef_thick(t):
    return 0.00055 + 0.0018 * (t ** 0.65)


def throw_profile(t):
    # Spear point at both ends. t = 0 tip, 1 butt. Height 28 mm.
    span = max(0.035, math.sin(t * math.pi) ** 0.72)
    half = 0.014 * span
    return -half, half


def throw_thick(t):
    return 0.0012 + 0.0038 * math.sin(t * math.pi)


def pocket_profile(t):
    spine = -0.001
    # Drop point: edge rises toward the tip, belly modest, 18 mm tall.
    edge = 0.004 + 0.014 * math.sin(t * math.pi * 0.9) ** 0.9
    if t < 0.18:
        edge = spine + (edge - spine) * (t / 0.18)
    return spine, edge


def pocket_thick(t):
    return 0.0004 + 0.0011 * t


def fly_profile(t):
    spine = -0.0015 - 0.001 * t
    edge = 0.002 + 0.016 * math.sin(min(t, 1) * math.pi * 0.85)
    if t < 0.12:
        edge = spine + (0.004) * (t / 0.12)
    return spine, edge


def fly_thick(t):
    return 0.00045 + 0.0013 * t


def cleaver_profile(t):
    # Tall rectangle, slight belly. across = 94 mm, so spine-to-edge is about that.
    spine = -0.006
    edge = 0.086 + 0.004 * math.sin(t * math.pi)
    if t < 0.08:
        # The tip corner is only slightly rounded, still a cleaver.
        edge = spine + (edge - spine) * (0.55 + 0.45 * t / 0.08)
    return spine, edge


def cleaver_thick(t):
    return 0.0022 + 0.0022 * t


def add_rivets(name, com, stations, radius, mat):
    objs = []
    for i, frm in enumerate(stations):
        obj = disc_y(f"{name}_rivet_{i}", 0.0, -0.012, 0.012, z_of(com, frm), radius, segments=12)
        assign(obj, mat)
        objs.append(obj)
    return objs


def build_chef(steel_mat, wood_mat, brass_mat):
    com = CHEF_COM
    objs = []
    blade_obj = blade("chef_blade", com, 0.203, chef_profile, chef_thick)
    assign(blade_obj, steel_mat)
    objs.append(blade_obj)
    # Full tang, peeking between the scales.
    tang = loft("chef_tang", [
        wedge_ring(z_of(com, 0.198), -0.004, 0.010, 0.0022, edge_full=0.0016, grind=0.5, steps=5),
        wedge_ring(z_of(com, 0.330), -0.006, 0.008, 0.0016, edge_full=0.0014, grind=0.5, steps=5),
    ])
    assign(tang, steel_mat)
    objs.append(tang)
    # Two wood scales.
    for sign, tag in ((1, "a"), (-1, "b")):
        rings = []
        for frm, half_w, half_t in (
            (0.214, 0.013, 0.0055),
            (0.250, 0.016, 0.0072),
            (0.300, 0.015, 0.0064),
            (0.330, 0.012, 0.0048),
        ):
            z = z_of(com, frm)
            y = sign * (0.0024 + half_t)
            rings.append([
                (-half_w, y - sign * half_t, z),
                (half_w * 0.15, y - sign * half_t, z),
                (half_w, y - sign * half_t * 0.2, z),
                (half_w * 0.2, y + sign * half_t * 0.15, z),
                (-half_w, y, z),
            ])
        scale = loft(f"chef_scale_{tag}", rings)
        assign(scale, wood_mat)
        uv_smart(scale)
        objs.append(scale)
    bolster = loft("chef_bolster", [
        wedge_ring(z_of(com, 0.192), -0.008, 0.016, 0.006, edge_full=0.004, grind=0.2, steps=6),
        wedge_ring(z_of(com, 0.214), -0.010, 0.014, 0.008, edge_full=0.006, grind=0.2, steps=6),
    ])
    assign(bolster, brass_mat)
    objs.append(bolster)
    objs.extend(add_rivets("chef", com, (0.240, 0.278, 0.312), 0.0032, brass_mat))
    report("chef", objs, 0.330)
    return objs


def build_throwing(steel_mat, cord_mat):
    obj = blade("throw_blade", THROW_COM, 0.280, throw_profile, throw_thick, stations=48)
    assign(obj, steel_mat)
    objs = [obj]
    # Cord turns on the handle half. Each turn is a thin torus approximated by a lofted ring.
    for i, frm in enumerate((0.198, 0.210, 0.222, 0.234, 0.246, 0.258)):
        z = z_of(THROW_COM, frm)
        ring = []
        for k in range(18):
            a = 2 * math.pi * k / 18
            ring.append((math.cos(a) * 0.0072, math.sin(a) * 0.0042, z))
        # A second ring makes a band rather than a collapsed loop.
        ring2 = [(x, y, z + 0.0034) for x, y, _ in ring]
        band = loft(f"cord_{i}", [ring, ring2])
        assign(band, cord_mat)
        objs.append(band)
    report("throwing", objs, 0.280)
    return objs


def build_pocket(steel_mat, g10, liner_mat, pin_mat):
    com = POCKET_COM
    blade_obj = blade("pocket_blade", com, 0.090, pocket_profile, pocket_thick, stations=28)
    assign(blade_obj, steel_mat)
    objs = [blade_obj]
    # Thumb stud on the blade.
    stud = disc_y("pocket_stud", 0.006, 0.003, 0.008, z_of(com, 0.028), 0.0022, segments=10)
    assign(stud, pin_mat)
    objs.append(stud)
    # Liners, then tan scales outside them.
    for sign, tag, mat, y0, thick in (
        (1, "lin_a", liner_mat, 0.0016, 0.0013),
        (-1, "lin_b", liner_mat, -0.0016, 0.0013),
        (1, "sc_a", g10, 0.0046, 0.0034),
        (-1, "sc_b", g10, -0.0046, 0.0034),
    ):
        rings = []
        for frm, half in ((0.098, 0.012), (0.140, 0.0145), (0.175, 0.0135), (0.200, 0.011)):
            z = z_of(com, frm)
            y = y0 + sign * thick * 0.5
            rings.append([
                (-half, y - thick * 0.5, z),
                (half, y - thick * 0.5, z),
                (half, y + thick * 0.5, z),
                (-half, y + thick * 0.5, z),
            ])
        part = loft(f"pocket_{tag}", rings)
        assign(part, mat)
        if mat == g10:
            uv_smart(part)
        objs.append(part)
    pivot = disc_y("pocket_pivot", 0.0, -0.009, 0.009, z_of(com, 0.096), 0.0034, segments=14)
    assign(pivot, pin_mat)
    objs.append(pivot)
    report("pocket", objs, 0.200)
    return objs


def build_butterfly(steel_mat, scale_mat, pin_mat):
    com = FLY_COM
    blade_obj = blade("fly_blade", com, 0.120, fly_profile, fly_thick, stations=30)
    assign(blade_obj, steel_mat)
    objs = [blade_obj]
    # Tang between the handles.
    tang = loft("fly_tang", [
        wedge_ring(z_of(com, 0.112), -0.003, 0.006, 0.0022, edge_full=0.0018, grind=0.4, steps=4),
        wedge_ring(z_of(com, 0.246), -0.003, 0.005, 0.0018, edge_full=0.0016, grind=0.4, steps=4),
    ])
    assign(tang, steel_mat)
    objs.append(tang)
    # Two channel handles. The channel is the gap around the tang: each handle is a
    # slab with its inner face cut by sitting the slabs apart.
    for sign, tag in ((1, "a"), (-1, "b")):
        rings = []
        for frm, half in ((0.128, 0.011), (0.180, 0.0125), (0.230, 0.012), (0.250, 0.010)):
            z = z_of(com, frm)
            inner = 0.0032
            outer = 0.0072
            y_in = sign * inner
            y_out = sign * outer
            rings.append([
                (-half, y_in, z), (half, y_in, z), (half, y_out, z), (-half, y_out, z),
            ])
        handle = loft(f"fly_handle_{tag}", rings)
        assign(handle, scale_mat)
        objs.append(handle)
        # Latch block near the butt, on one side only once.
    latch = box("fly_latch", -0.004, 0.010, -0.003, 0.003, z_of(com, 0.232), z_of(com, 0.250))
    assign(latch, pin_mat)
    objs.append(latch)
    for frm, rad in ((0.132, 0.0026), (0.210, 0.0018), (0.242, 0.0018)):
        pin = disc_y(f"fly_pin_{frm}", 0.0, -0.008, 0.008, z_of(com, frm), rad, segments=12)
        assign(pin, pin_mat)
        objs.append(pin)
    report("butterfly", objs, 0.250)
    return objs


def build_cleaver(steel_mat, wood_mat, brass_mat):
    com = CLEAVER_COM
    blade_obj = blade("cleaver_blade", com, 0.180, cleaver_profile, cleaver_thick, stations=24)
    assign(blade_obj, steel_mat)
    objs = [blade_obj]
    # Hanging hole near the top front. A short cylinder punched visually by a ring sitting
    # in the blade; the opening reads because the ring is brass-dark and the centre is empty
    # only if we boolean. Boolean in background is reliable enough for one cylinder.
    cutter = disc_y("cleaver_hole_cut", -0.001, -0.02, 0.02, z_of(com, 0.028), 0.011, segments=16)
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
    # Handle along the spine, behind the heel.
    bolster = loft("cleaver_bolster", [
        wedge_ring(z_of(com, 0.172), -0.012, 0.018, 0.010, edge_full=0.006, grind=0.15, steps=5),
        wedge_ring(z_of(com, 0.196), -0.014, 0.012, 0.012, edge_full=0.008, grind=0.15, steps=5),
    ])
    assign(bolster, brass_mat)
    objs.append(bolster)
    for sign, tag in ((1, "a"), (-1, "b")):
        rings = []
        for frm, half in ((0.196, 0.014), (0.240, 0.016), (0.285, 0.014), (0.300, 0.012)):
            z = z_of(com, frm)
            y = sign * 0.0065
            rings.append([
                (-0.016, y - sign * 0.004, z),
                (0.010, y - sign * 0.004, z),
                (0.012, y + sign * 0.001, z),
                (-0.014, y + sign * 0.001, z),
            ])
        scale = loft(f"cleaver_scale_{tag}", rings)
        assign(scale, wood_mat)
        uv_smart(scale)
        objs.append(scale)
    objs.extend(add_rivets("cleaver", com, (0.230, 0.275), 0.0034, brass_mat))
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
    # Unit height so the app can scale Y to the fill in metres. Radius sits inside the bottle.
    obj = lathe_solid("water_body", [(0.0, 0.0004), (0.0, 0.0262), (1.0, 0.0262)], segments=28)
    assign(obj, water_mat)
    report("water_body", [obj], 1.0)
    return [obj]


def build_water_neck(water_mat):
    # Interior of the shoulder and neck, base of this mesh at the shoulder (local z = 0).
    # World placement is y = 0.148. Height is 0.056 so scale can stop the water in the neck.
    profile = [
        (0.000, 0.0004),
        (0.000, 0.0255),
        (0.018, 0.0165),
        (0.034, 0.0115),
        (0.056, 0.0105),
    ]
    obj = lathe_solid("water_neck", profile, segments=24)
    assign(obj, water_mat)
    report("water_neck", [obj], 0.056)
    return [obj]


def build_block(wood_mat):
    # Top face on z = 0, centred on x = 0.29 so it covers physics x = 0..0.58.
    # Depth is along Y. After export, Blender -Y becomes glTF +Z (the camera side).
    obj = bevel_box("block", 0.0, 0.58, -0.11, 0.11, -0.10, 0.0, 0.006)
    assign(obj, wood_mat)
    cube_uv(obj, scale=3.2)
    report("block", [obj])
    return [obj]


def build_chip(wood_mat):
    # A splinter about 2.4 cm long, origin at its centre.
    obj = loft("chip", [
        wedge_ring(-0.012, -0.004, 0.004, 0.0030, edge_full=0.0012, grind=0.4, steps=4),
        wedge_ring(0.012, -0.001, 0.001, 0.0008, edge_full=0.0003, grind=0.4, steps=4),
    ])
    assign(obj, wood_mat)
    uv_smart(obj)
    return [obj]


def build_room(wood_mat, wall_mat, glass_mat, plaster_mat):
    objs = []
    # Counter top. Physics block sits on z = -0.10. Front (camera) is -Y.
    top = bevel_box("counter", -0.55, 1.25, -0.34, 0.42, -0.142, -0.100, 0.008)
    assign(top, wood_mat)
    cube_uv(top, scale=1.6)
    objs.append(top)
    # Cabinet face and a side panel. Kept blocky on purpose: the HDRI is the room,
    # this is the counter the block stands on.
    cabinet = box("cabinet", -0.52, 1.22, -0.30, 0.38, -0.90, -0.142)
    assign(cabinet, plaster_mat)
    objs.append(cabinet)
    kick = box("kick", -0.48, 1.18, -0.28, 0.34, -0.90, -0.84)
    assign(kick, wall_mat)
    objs.append(kick)
    # Back wall and a return wall. Window is a hole via boolean.
    wall = box("back_wall", -0.90, 1.55, 0.46, 0.52, -0.90, 1.45)
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
    assign(wall, wall_mat)
    objs.append(wall)
    side = box("side_wall", -0.90, -0.84, -0.40, 0.52, -0.90, 1.45)
    assign(side, wall_mat)
    objs.append(side)
    floor = box("floor", -1.3, 1.8, -0.9, 0.9, -0.94, -0.90)
    assign(floor, plaster_mat)
    objs.append(floor)
    # Window frame and a pane of glass, slightly proud of the wall (smaller Y).
    frame_parts = [
        box("frame_l", 0.55, 0.58, 0.44, 0.50, 0.35, 0.95),
        box("frame_r", 1.12, 1.15, 0.44, 0.50, 0.35, 0.95),
        box("frame_b", 0.55, 1.15, 0.44, 0.50, 0.35, 0.40),
        box("frame_t", 0.55, 1.15, 0.44, 0.50, 0.90, 0.95),
        box("mullion", 0.83, 0.86, 0.44, 0.495, 0.40, 0.90),
    ]
    for part in frame_parts:
        assign(part, wood_mat)
        objs.append(part)
    pane = box("pane", 0.58, 1.12, 0.455, 0.462, 0.40, 0.90)
    assign(pane, glass_mat)
    objs.append(pane)
    # A plain canister on the far-left of the counter, out of the toss.
    jar = lathe_solid("canister", [
        ( -0.10, 0.0004), (-0.10, 0.045), (-0.02, 0.048), (0.06, 0.046), (0.08, 0.028),
    ], segments=20)
    # That profile used absolute z by mistake (negative). Rebuild at the counter.
    bpy.data.objects.remove(jar, do_unlink=True)
    jar = lathe_solid("canister", [
        (0.0, 0.0004), (0.0, 0.045), (0.08, 0.048), (0.11, 0.046), (0.125, 0.030),
    ], segments=20)
    jar.location = (-0.28, 0.16, -0.10)
    assign(jar, plaster_mat)
    bpy.context.view_layer.update()
    objs.append(jar)
    lid = cylinder("canister_lid", 0.0, 0.012, 0.032, segments=16)
    lid.location = (-0.28, 0.16, 0.025)
    assign(lid, wood_mat)
    bpy.context.view_layer.update()
    objs.append(lid)
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
    wood_big = wood(color, normal, rough, "WoodScene")
    cord = paint("Cord", (0.45, 0.16, 0.10), roughness=0.72)
    g10 = paint("G10", (0.72, 0.58, 0.36), roughness=0.48)
    liner = paint("Liner", (0.55, 0.57, 0.60), roughness=0.35, metallic=0.9)
    pin = paint("Pin", (0.62, 0.48, 0.22), roughness=0.3, metallic=1.0)
    fly_scale = paint("Channels", (0.28, 0.30, 0.33), roughness=0.34, metallic=0.85)
    plastic = glass("PET", (0.90, 0.95, 0.96), transmission=0.94, roughness=0.05, ior=1.52)
    cap_mat = paint("Cap", (0.16, 0.38, 0.40), roughness=0.38)
    water_mat = glass("Water", (0.55, 0.78, 0.92), transmission=0.98, roughness=0.02, ior=1.33)
    wall = paint("Wall", (0.86, 0.82, 0.74), roughness=0.85)
    plaster = paint("Plaster", (0.78, 0.74, 0.68), roughness=0.9)
    window_glass = glass("Window", (0.85, 0.92, 0.95), transmission=0.88, roughness=0.04, ior=1.5)

    groups = {
        "chef.glb": build_chef(steel_mat, wood_handle, brass_mat),
        "throwing.glb": build_throwing(steel_mat, cord),
        "pocket.glb": build_pocket(steel_mat, g10, liner, pin),
        "butterfly.glb": build_butterfly(steel_mat, fly_scale, pin),
        "cleaver.glb": build_cleaver(steel_mat, wood_handle, brass_mat),
        "bottle.glb": build_bottle(plastic, cap_mat),
        "water_body.glb": build_water_body(water_mat),
        "water_neck.glb": build_water_neck(water_mat),
        "chip.glb": build_chip(wood_handle),
        # The block shares the room file so the 1K wood maps are stored once.
        "room.glb": build_room(wood_big, wall, window_glass, plaster) + build_block(wood_big),
    }
    # chip loft uses wedge_ring which has a fixed point count; report length separately.
    for filename, objs in groups.items():
        export(objs, filename)
    print("knife-flip models written to", OUT)


if __name__ == "__main__":
    main()
