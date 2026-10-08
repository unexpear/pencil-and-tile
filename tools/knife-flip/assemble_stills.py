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
KNIVES = {
    "chef": 0.330,
    "throwing": 0.280,
    "pocket": 0.200,
    "butterfly": 0.250,
    "cleaver": 0.300,
}
COM = {
    "chef": (0.090 * 0.108 + 0.040 * 0.206 + 0.090 * 0.272) / 0.220,
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
    mat = bpy.data.materials.new("Water")
    mat.use_nodes = True
    mat.blend_method = "BLEND"
    mat.use_backface_culling = False
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (0.29, 0.64, 0.78, 1)
    bsdf.inputs["Roughness"].default_value = 0.08
    bsdf.inputs["Metallic"].default_value = 0.02
    if "Alpha" in bsdf.inputs:
        bsdf.inputs["Alpha"].default_value = 0.72
    return mat


def add_mesh(name, verts, faces, mat):
    mesh = bpy.data.meshes.new(name)
    mesh.from_pydata(verts, [], faces)
    mesh.update()
    obj = bpy.data.objects.new(name, mesh)
    bpy.context.collection.objects.link(obj)
    obj.data.materials.append(mat)
    return obj


def ring_verts(radius, y, tilt, sides, gltf_to_blender=True):
    slope = math.tan(tilt)
    pts = []
    for i in range(sides):
        a = 2 * math.pi * i / sides
        x = math.cos(a) * radius
        z = math.sin(a) * radius
        yy = y + x * slope
        # glTF local (x, y, z) -> Blender (x, -z, y), child of the bottle.
        pts.append((x, -z, yy) if gltf_to_blender else (x, yy, z))
    return pts


def liquid_mesh(name, profile, tilt, mat, sides=24):
    """profile is (y, radius) up the bottle. The last ring is the plane-cut surface."""
    rings = []
    for index, (y, radius) in enumerate(profile):
        rings.append(ring_verts(radius, y, tilt if index == len(profile) - 1 else 0.0, sides))
    verts = [p for ring in rings for p in ring]
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
    faces.append(tuple(last + i for i in range(n - 1, -1, -1)))
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
    body = liquid_mesh("water_body", [(0.001, WATER_RADIUS), (body_top, WATER_RADIUS)], body_tilt, mat)
    attach(body, parent)
    if in_neck:
        neck_h = max(0.004, min(NECK_LIMIT, height - SHOULDER))
        tilt = clipped_tilt(requested_tilt(fill, sigma), neck_h, neck_radius(neck_h), 0.003, NECK_LIMIT)
        steps = 4
        profile = []
        for i in range(steps + 1):
            y = neck_h * i / steps
            profile.append((y, neck_radius(y)))
        neck = liquid_mesh("water_neck", profile, tilt, mat, sides=20)
        # Shoulder is +Y in the bottle's glTF space, which is +Z in Blender.
        attach(neck, parent, Matrix.Translation((0, 0, SHOULDER)))


def backdrop():
    mat = bpy.data.materials.new("Backdrop")
    mat.use_nodes = True
    bsdf = mat.node_tree.nodes.get("Principled BSDF")
    bsdf.inputs["Base Color"].default_value = (0.79, 0.73, 0.66, 1)
    bsdf.inputs["Roughness"].default_value = 0.85

    def cube(name, gltf_center, gltf_size):
        bpy.ops.mesh.primitive_cube_add(size=1)
        obj = bpy.context.active_object
        obj.name = name
        sx, sy, sz = gltf_size
        obj.scale = (sx, sz, sy)  # blender (x, y, z) = gltf (x, z, y) for a box aligned to axes
        cx, cy, cz = gltf_center
        obj.location = (cx, -cz, cy)
        assign = obj.data.materials
        assign.append(mat)

    cube("back", (0, 0.35, -0.55), (1.6, 1.4, 0.04))
    cube("side", (-0.7, 0.35, 0), (0.04, 1.4, 1.4))
    cube("plinth", (0, -0.02, 0), (0.42, 0.02, 0.22))


def camera_play(bottle, yaw, pitch, distance):
    look = (0.30, 0.36, 0.0) if bottle else (0.34, 0.18, 0.0)
    dist = (0.72 if bottle else 0.48) * distance
    cp = math.cos(math.radians(pitch))
    sp = math.sin(math.radians(pitch))
    eye = (
        look[0] + dist * cp * math.sin(math.radians(yaw)),
        look[1] + dist * sp,
        look[2] + dist * cp * math.cos(math.radians(yaw)),
    )
    return eye, look, 36.0, 0.04


def camera_preview(length, bottle):
    dist = length * (2.1 if bottle else 2.55)
    pitch = math.radians(16 if bottle else 20)
    yaw = math.radians(54)
    cp = math.cos(pitch)
    eye = (
        dist * cp * math.sin(yaw),
        length * 0.15 + dist * math.sin(pitch),
        dist * cp * math.cos(yaw),
    )
    look = (0.0, length * (0.45 if bottle else 0.02), 0.0)
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
    spec = {
        "name": name,
        "eye": list(eye),
        "look": list(look),
        "fov": fov,
        "near": near,
        "background": [0.79, 0.73, 0.66] if name.startswith("picker") else [0.55, 0.51, 0.45],
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
            backdrop()
            place_knife(name, 0, 0, 1.15)
        shot(f"picker-{name}", build, camera_preview(KNIVES[name], False))

    for name in KNIVES:
        knife_picker(name)

    def bottle_picker(label, fill):
        def build():
            backdrop()
            place_bottle(0, 0, 0.22, fill, 0.0)
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

    shot("bottle-mid", room_bottle(mid_bottle), camera_play(True, 30, 26, 2.6))
    shot("bottle-landed", room_bottle(landed), camera_play(True, 30, 26, 2.6))


if __name__ == "__main__":
    main()
