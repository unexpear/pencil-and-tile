#!/usr/bin/env python3
"""Assemble Knife Flip stills and leave one GLB per shot for Filament's gltf_viewer.

    blender --background --python tools/knife-flip/assemble_stills.py

Poses are samples from FlipPhysics (practised toss). Cameras match KnifeFlipWorld.
The water mesh is a vertical column with a plane-cut top, same radii and clip as the app.
"""

import json
import math
import os

import bpy
from mathutils import Matrix, Vector

SCRIPT_DIR = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.abspath(os.path.join(SCRIPT_DIR, "..", ".."))
ASSETS = os.path.join(REPO, "app", "src", "main", "assets", "knife-flip")
OUT = os.environ.get("KNIFE_STILLS", "/tmp/knife-stills")

# glTF (x, y, z) = Blender (x, z, -y). Conjugating by this matrix turns a glTF
# node matrix into the Blender matrix the exporter will turn back.
C = Matrix(((1, 0, 0, 0), (0, 0, 1, 0), (0, -1, 0, 0), (0, 0, 0, 1)))

H = 0.204
MB = 0.025
SHOULDER = 0.148
NECK_LIMIT = 0.040
WATER_RADIUS = 0.0292
MENISCUS = 0.0028
# Same lux as KnifeFlipWorld (IBL_LUX, KEY_LUX, FILL_LUX). Picker key is a little hotter.
IBL_LUX = 22000
KEY_LUX = 42000
FILL_LUX = 15000
PICKER_KEY_LUX = 48000
PICKER_FILL_LUX = 18000
KEY_DIR = [-0.28, -1.0, -0.62]
FILL_DIR = [0.45, -0.4, 0.55]
KNIVES = {
    "chef": 0.330,
    "throwing": 0.280,
    "pocket": 0.200,
    "butterfly": 0.250,
    "cleaver": 0.300,
}
# Distance from the tip to the centre of mass. The mesh origin sits there.
COM = {
    "chef": (0.090 * 0.108 + 0.040 * 0.206 + 0.090 * 0.272) / 0.220,
    "throwing": 0.140,
    "pocket": (0.032 * 0.042 + 0.096 * 0.152) / 0.128,
    "butterfly": (0.055 * 0.055 + 0.100 * 0.190) / 0.155,
    "cleaver": (0.330 * 0.086 + 0.075 * 0.242) / 0.405,
}


def gltf_matrix(tx, ty, tz, angle_z):
    c, s = math.cos(angle_z), math.sin(angle_z)
    return Matrix(((c, -s, 0, tx), (s, c, 0, ty), (0, 0, 1, tz), (0, 0, 0, 1)))


def apply_gltf(obj, tx, ty, tz, theta):
    """theta is the physics angle. The app rotates by -theta about Z."""
    obj.matrix_world = C.inverted() @ gltf_matrix(tx, ty, tz, -theta) @ C


def import_roots(path):
    before = set(bpy.data.objects)
    bpy.ops.import_scene.gltf(filepath=path)
    new = [o for o in bpy.data.objects if o not in before]
    roots = [o for o in new if o.parent is None]
    return roots


def fresh():
    bpy.ops.wm.read_factory_settings(use_empty=True)


def water_height(fill, sigma):
    h0 = fill * H
    return h0 + max(0.0, min(1.0, sigma)) * (H - h0)


def com_from_base(fill, sigma):
    mw = fill * 20.0 * MB
    h = water_height(fill, sigma)
    return (MB * H / 2.0 + mw * h / 2.0) / (MB + mw)


def neck_radius(y):
    samples = (0.0, 0.0292, 0.018, 0.0162, 0.034, 0.0112, 0.040, 0.0106)
    yy = max(0.0, min(NECK_LIMIT, y))
    i = 0
    while i + 2 < len(samples) and samples[i + 2] < yy:
        i += 2
    y0, r0, y1, r1 = samples[i:i + 4]
    t = 0.0 if y1 == y0 else (yy - y0) / (y1 - y0)
    return r0 + (r1 - r0) * t


def clipped_tilt(requested_deg, center, radius, floor, ceiling):
    room = min(center - floor, ceiling - center)
    if requested_deg == 0 or room <= 0.001 or radius <= 0.001:
        return 0.0
    max_rad = math.atan(room / radius)
    requested = math.radians(requested_deg)
    return max(-max_rad, min(max_rad, requested))


def requested_tilt(fill, sigma):
    if fill > 0.98:
        return 0.0
    return sigma * (1.0 - fill) * 28.0


def water_material():
    """Opaque light blue. Alpha water is invisible inside Filament glass."""
    mat = bpy.data.materials.new("Water")
    mat.use_nodes = True
    mat.blend_method = "OPAQUE"
    mat.use_backface_culling = False
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (0.243, 0.686, 0.875, 1)
    bsdf.inputs["Roughness"].default_value = 0.16
    bsdf.inputs["Metallic"].default_value = 0.0
    if "Transmission Weight" in bsdf.inputs:
        bsdf.inputs["Transmission Weight"].default_value = 0.0
    if "Alpha" in bsdf.inputs:
        bsdf.inputs["Alpha"].default_value = 1.0
    return mat


def add_mesh(name, verts, faces, mat):
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.data.materials.append(mat)
    return obj


def ring_verts(radius, y, tilt, sides, meniscus=0.0):
    slope = math.tan(tilt)
    pts = []
    for i in range(sides):
        a = 2 * math.pi * i / sides
        x = math.cos(a) * radius
        z = math.sin(a) * radius
        climb = meniscus * (x * x + z * z) / (radius * radius) if radius > 1e-6 and meniscus else 0.0
        yy = y + x * slope + climb
        # glTF local (x, y, z) -> Blender (x, -z, y), child of the bottle.
        pts.append((x, -z, yy))
    return pts


def liquid_mesh(name, profile, tilt, mat, sides=24, meniscus=0.0):
    """profile is (y, radius) up the bottle. The last ring climbs the wall as a meniscus."""
    rings = []
    for index, (y, radius) in enumerate(profile):
        last = index == len(profile) - 1
        rings.append(ring_verts(radius, y, tilt if last else 0.0, sides, meniscus if last else 0.0))
    verts = [p for ring in rings for p in ring]
    # Centre of the free surface, below the rim, so the cap is concave.
    top_y, top_r = profile[-1]
    verts.append((0.0, 0.0, top_y))
    faces = []
    n = sides
    for r in range(len(rings) - 1):
        for i in range(n):
            a = r * n + i
            b = r * n + (i + 1) % n
            c = (r + 1) * n + (i + 1) % n
            d = (r + 1) * n + i
            faces.append((a, b, c, d))
    faces.append(tuple(range(n)))
    last = (len(rings) - 1) * n
    center = len(verts) - 1
    for i in range(n):
        faces.append((center, last + (i + 1) % n, last + i))
    return add_mesh(name, verts, faces, mat)


def attach(child, parent, local=None):
    """Keep [local] (a Blender matrix) relative to parent. Default is identity."""
    child.parent = parent
    child.matrix_parent_inverse = Matrix.Identity(4)
    child.matrix_basis = local if local is not None else Matrix.Identity(4)


def add_water(parent, fill, sigma, mat):
    height = water_height(fill, sigma)
    in_neck = height > SHOULDER + 0.004
    body_top = SHOULDER if in_neck else max(height, 0.008)
    body_tilt = 0.0 if in_neck else clipped_tilt(
        requested_tilt(fill, sigma), body_top, WATER_RADIUS, 0.004, SHOULDER
    )
    body_meniscus = 0.0 if in_neck else max(0.0, min(MENISCUS, SHOULDER - body_top))
    body = liquid_mesh(
        "water_body", [(0.001, WATER_RADIUS), (body_top, WATER_RADIUS)], body_tilt, mat,
        meniscus=body_meniscus,
    )
    attach(body, parent)
    if in_neck:
        neck_h = max(0.004, min(NECK_LIMIT, height - SHOULDER))
        tilt = clipped_tilt(requested_tilt(fill, sigma), neck_h, neck_radius(neck_h), 0.003, NECK_LIMIT)
        steps = 4
        profile = []
        for i in range(steps + 1):
            y = neck_h * i / steps
            profile.append((y, neck_radius(y)))
        neck_meniscus = max(0.0, min(0.0016, NECK_LIMIT - neck_h))
        neck = liquid_mesh("water_neck", profile, tilt, mat, sides=20, meniscus=neck_meniscus)
        # Shoulder is +Y in the bottle's glTF space, which is +Z in Blender.
        attach(neck, parent, Matrix.Translation((0, 0, SHOULDER)))


def backdrop(length, bottle):
    wall = bpy.data.materials.new("Backdrop")
    wall.use_nodes = True
    wall_bsdf = wall.node_tree.nodes.get("Principled BSDF")
    wall_bsdf.inputs["Base Color"].default_value = (0.835, 0.816, 0.784, 1)
    wall_bsdf.inputs["Roughness"].default_value = 0.92
    board = bpy.data.materials.new("Board")
    board.use_nodes = True
    board_bsdf = board.node_tree.nodes.get("Principled BSDF")
    board_bsdf.inputs["Base Color"].default_value = (0.843, 0.690, 0.478, 1)
    board_bsdf.inputs["Roughness"].default_value = 0.58

    def cube(name, gltf_center, gltf_size, mat):
        bpy.ops.mesh.primitive_cube_add(size=1)
        obj = bpy.context.active_object
        obj.name = name
        sx, sy, sz = gltf_size
        obj.scale = (sx, sz, sy)  # blender (x, y, z) = gltf (x, z, y) for a box aligned to axes
        cx, cy, cz = gltf_center
        obj.location = (cx, -cz, cy)
        obj.data.materials.append(mat)

    cube("back", (0, 0.35, -0.55), (1.6, 1.4, 0.04), wall)
    cube("side", (-0.7, 0.35, 0), (0.04, 1.4, 1.4), wall)
    # A small maple board under the model, not a slab that fills the frame.
    span_x = length * (0.72 if bottle else 1.18)
    span_z = length * (0.55 if bottle else 0.42)
    cube("board", (0, -0.007, 0), (span_x, 0.014, span_z), board)


def camera_play(bottle, yaw, pitch, distance):
    look = (0.26, 0.46, 0.0) if bottle else (0.34, 0.18, 0.0)
    dist = (0.62 if bottle else 0.48) * distance
    cp = math.cos(math.radians(pitch))
    sp = math.sin(math.radians(pitch))
    eye = (
        look[0] + dist * cp * math.sin(math.radians(yaw)),
        look[1] + dist * sp,
        look[2] + dist * cp * math.cos(math.radians(yaw)),
    )
    return eye, look, 36.0, 0.04


def camera_preview(length, bottle, com_from_tip=None):
    """Matches KnifeFlipPreview. Knives look at the geometric middle, not the COM."""
    theta = 0.30 if bottle else 1.20
    if bottle:
        look = (0.0, length * 0.46, 0.0)
        dist = length * 2.15
        pitch = math.radians(16)
        yaw = math.radians(36)
    else:
        mid = (com_from_tip if com_from_tip is not None else length * 0.5) - length * 0.5
        look = (mid * math.sin(theta), mid * math.cos(theta), 0.0)
        # 4× length at 32° vertical / ~31° horizontal fills about 80% of a square frame.
        dist = length * 4.0
        pitch = math.radians(22)
        yaw = math.radians(28)
    cp = math.cos(pitch)
    eye = (
        look[0] + dist * cp * math.sin(yaw),
        look[1] + dist * math.sin(pitch),
        look[2] + dist * cp * math.cos(yaw),
    )
    return eye, look, 32.0, 0.02


def export(path):
    bpy.ops.object.select_all(action="SELECT")
    kwargs = dict(filepath=path, export_format="GLB", use_selection=True, export_apply=False, export_yup=True)
    try:
        bpy.ops.export_scene.gltf(**kwargs)
    except Exception as err:
        print("export", err)
        bpy.ops.export_scene.gltf(**kwargs)


def place_knife(name, x, z, theta):
    roots = import_roots(os.path.join(ASSETS, f"{name}.glb"))
    for root in roots:
        apply_gltf(root, x, z, 0.0, theta)
    return roots


def place_bottle(x, z, theta, fill, sigma):
    roots = import_roots(os.path.join(ASSETS, "bottle.glb"))
    h = com_from_base(fill, sigma)
    bx = x - h * math.sin(theta)
    by = z - h * math.cos(theta)
    for root in roots:
        apply_gltf(root, bx, by, 0.0, theta)
    if roots:
        add_water(roots[0], fill, sigma, water_material())
    return roots


def chip_burst(tip, age=0.12):
    for i in range(8):
        ang = i * 0.85
        speed = 0.16 + (i % 4) * 0.045
        y = tip[1] + (0.22 + (i % 3) * 0.06) * age - 1.6 * age * age
        pos = (
            tip[0] + math.cos(ang) * speed * age,
            y,
            tip[2] + math.sin(ang) * 0.07 * age,
        )
        if pos[1] < 0.004:
            continue
        roots = import_roots(os.path.join(ASSETS, f"chip{i % 4}.glb"))
        for root in roots:
            apply_gltf(root, pos[0], pos[1], pos[2], 0.4 * i)
            root.scale = (0.9 + (i % 3) * 0.15, 0.8, 1.0)


def tip_of(name, x, z, theta):
    reach = COM[name]
    return (x + reach * math.sin(theta), z + reach * math.cos(theta), 0.0)


def shot(name, build, camera):
    fresh()
    build()
    eye, look, fov, near = camera
    path = os.path.join(OUT, f"{name}.glb")
    export(path)
    picker = name.startswith("picker")
    spec = {
        "name": name,
        "eye": list(eye),
        "look": list(look),
        "fov": fov,
        "near": near,
        "background": [0.84, 0.82, 0.78] if picker else [0.55, 0.51, 0.45],
        "ibl": IBL_LUX,
        "key": PICKER_KEY_LUX if picker else KEY_LUX,
        "fill": PICKER_FILL_LUX if picker else FILL_LUX,
        "key_dir": KEY_DIR,
        "fill_dir": FILL_DIR,
    }
    with open(os.path.join(OUT, f"{name}.json"), "w", encoding="utf-8") as fh:
        json.dump(spec, fh)
    print("assembled", name)


def main():
    os.makedirs(OUT, exist_ok=True)
    # Practised-toss samples. Printed from FlipPhysics and copied here.
    held = (0.10, 0.24, 1.20, 0.0)
    mid_throw = (0.1169909, 0.25707525, 1.8668044, 0.0)
    stuck_chef = (0.16151622, 0.17572834, 2.7697892, 0.0)
    stuck_cleaver = (0.13598731, 0.10781152, 2.9096184, 0.0)
    mid_bottle = (0.2085606, 0.73912394, -3.3024645, 0.58984977)
    landed = (0.3151777, 0.042869568, 0.011305547, 0.0)
    fills = [("quarter", 0.25), ("third", 1 / 3), ("half", 0.5), ("three-quarter", 0.75), ("full", 1.0)]

    def knife_picker(name):
        def build():
            backdrop(KNIVES[name], False)
            place_knife(name, 0, 0, 1.20)
        shot(f"picker-{name}", build, camera_preview(KNIVES[name], False, COM[name]))

    for name in KNIVES:
        knife_picker(name)

    def bottle_picker(label, fill):
        def build():
            backdrop(H, True)
            place_bottle(0, 0, 0.30, fill, 0.0)
        shot(f"picker-bottle-{label}", build, camera_preview(H, True))

    for label, fill in fills:
        bottle_picker(label, fill)

    def room_knife(asset, pose, chips=False):
        def build():
            import_roots(os.path.join(ASSETS, "room.glb"))
            place_knife(asset, pose[0], pose[1], pose[2])
            if chips:
                chip_burst(tip_of(asset, pose[0], pose[1], pose[2]))
        return build

    shot("side-held", room_knife("throwing", held), camera_play(False, 32, 34, 2.4))
    shot("mid-flip", room_knife("throwing", mid_throw), camera_play(False, 32, 34, 2.4))
    shot("stuck-chef", room_knife("chef", stuck_chef, chips=True), camera_play(False, 32, 34, 2.4))
    shot("stuck-cleaver", room_knife("cleaver", stuck_cleaver, chips=True), camera_play(False, 32, 34, 2.4))
    shot("corner", room_knife("throwing", held), camera_play(False, 58, 32, 2.5))

    def room_bottle(pose, fill=1 / 3):
        def build():
            import_roots(os.path.join(ASSETS, "room.glb"))
            place_bottle(pose[0], pose[1], pose[2], fill, pose[3])
        return build

    shot("bottle-mid", room_bottle(mid_bottle), camera_play(True, 18, 20, 2.05))
    shot("bottle-landed", room_bottle(landed), camera_play(True, 18, 20, 2.05))


if __name__ == "__main__":
    main()
