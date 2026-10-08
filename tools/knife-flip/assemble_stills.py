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


# glTF AABBs (POSITION accessor). Y is length, X is edge-to-spine, Z is thickness.
MESH = {
    "chef": ((-0.016, -0.1371, -0.012), (0.0476, 0.1929, 0.012)),
    "throwing": ((-0.014, -0.14, -0.0025), (0.014, 0.14, 0.0025)),
    "pocket": ((-0.014, -0.0755, -0.0084), (0.0158, 0.1245, 0.0082)),
    "butterfly": ((-0.0125, -0.1079, -0.008), (0.018, 0.1421, 0.008)),
    "cleaver": ((-0.016, -0.1851, -0.012), (0.09, 0.1149, 0.012)),
    "bottle": ((-0.0315, 0.0, -0.0315), (0.0315, 0.204, 0.0315)),
}
# Knife pickers are a wide banner (1280×560), the same shape as the in-app strip.
# Bottle pickers stay on the viewer's 614×640 window. The app derives the vertical
# field from the live aspect and keeps this horizontal field.
PICKER_KNIFE_ASPECT = 1280.0 / 560.0
PICKER_BOTTLE_ASPECT = 614.0 / 640.0
PICKER_HFOV = math.radians(28.0)
# Nearly face-on, so the length runs across the frame instead of a steep diagonal.
PICKER_YAW = math.radians(14.0)
PICKER_PITCH = math.radians(8.0)
BOTTLE_YAW = math.radians(28.0)
BOTTLE_PITCH = math.radians(12.0)
KNIFE_THETA = -math.pi / 2
BOTTLE_THETA = 0.22
# Play cameras are a 9:19.5 phone (720×1560) at 40° vertical. Distance is metres.
# Fitted so the block, the counter and the toss stay inside, with the subject high
# in the frame so the wall is a strip. Top views stay centred on the block.
PLAY_FOV = 40.0
PLAY = {
    "knife": {
        "side": (52.0, 46.0, 2.0769, (0.4645, -0.1107, 0.1959)),
        "corner": (68.0, 50.0, 1.7615, (0.4222, -0.0179, 0.1043)),
        "top": (24.0, 68.0, 2.1822, (0.3000, 0.0724, 0.1092)),
    },
    "bottle": {
        "side": (36.0, 24.0, 2.1500, (0.3313, 0.2039, 0.1341)),
        "corner": (54.0, 28.0, 1.9520, (0.3422, 0.2697, 0.0937)),
        "top": (18.0, 62.0, 1.9171, (0.2811, 0.3438, 0.0758)),
    },
}


def _corners(box):
    (x0, y0, z0), (x1, y1, z1) = box
    return [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]


def _xform(point, theta, origin):
    x, y, z = point
    ox, oy, oz = origin
    return (
        math.cos(theta) * x + math.sin(theta) * y + ox,
        -math.sin(theta) * x + math.cos(theta) * y + oy,
        z + oz,
    )


def _sub(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def _add(a, b):
    return (a[0] + b[0], a[1] + b[1], a[2] + b[2])


def _mul(a, s):
    return (a[0] * s, a[1] * s, a[2] * s)


def _dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def _cross(a, b):
    return (
        a[1] * b[2] - a[2] * b[1],
        a[2] * b[0] - a[0] * b[2],
        a[0] * b[1] - a[1] * b[0],
    )


def _norm(v):
    length = math.sqrt(_dot(v, v))
    return (v[0] / length, v[1] / length, v[2] / length)


def _eye(look, yaw, pitch, dist):
    cp, sp = math.cos(pitch), math.sin(pitch)
    return _add(look, (dist * cp * math.sin(yaw), dist * sp, dist * cp * math.cos(yaw)))


def _basis(eye, look):
    view = _norm(_sub(look, eye))
    right = _norm(_cross(view, (0.0, 1.0, 0.0)))
    up = _cross(right, view)
    return right, up, view


def _span(points, eye, look, tan_h, tan_v):
    right, up, view = _basis(eye, look)
    xs, ys = [], []
    for point in points:
        rel = _sub(point, eye)
        depth = _dot(rel, view)
        if depth <= 0.02:
            return None
        xs.append((_dot(rel, right) / depth) / tan_h)
        ys.append((_dot(rel, up) / depth) / tan_v)
    return min(xs), max(xs), min(ys), max(ys)


def pose_lift(box, theta, clearance):
    """Raise the mesh so its lowest point sits [clearance] metres above the board."""
    (x0, y0, _), (x1, y1, _) = box
    lowest = min(
        -math.sin(theta) * x + math.cos(theta) * y
        for x in (x0, x1) for y in (y0, y1)
    )
    return clearance - lowest


def preview_pose(name, bottle):
    """Edge-up knife, or a slightly tipped bottle, clear of the board."""
    theta = BOTTLE_THETA if bottle else KNIFE_THETA
    clearance = 0.004 if bottle else 0.012
    lift = pose_lift(MESH[name], theta, clearance)
    points = [_xform(c, theta, (0.0, lift, 0.0)) for c in _corners(MESH[name])]
    return theta, lift, points


def preview_camera(points, bottle):
    """Distance from the posed bounds and the field, aimed at the bounds centre.

    Knives fill 80% of the banner width. Bottles fill 70% of the frame height
    so the water line is readable. A few recentres keep the bounds centred.
    """
    aspect = PICKER_BOTTLE_ASPECT if bottle else PICKER_KNIFE_ASPECT
    vfov = 2.0 * math.atan(math.tan(PICKER_HFOV / 2.0) / aspect)
    tan_v = math.tan(vfov / 2.0)
    tan_h = math.tan(PICKER_HFOV / 2.0)
    yaw = BOTTLE_YAW if bottle else PICKER_YAW
    pitch = BOTTLE_PITCH if bottle else PICKER_PITCH
    # Knife target is the shelf, which is a little wider than the blade, so the
    # blade itself lands near 80% of the width with the board still inside.
    target_w = 0.55 if bottle else 0.84
    target_h = 0.70 if bottle else 0.92
    xs, ys, zs = zip(*points)
    look = [(min(xs) + max(xs)) / 2, (min(ys) + max(ys)) / 2, (min(zs) + max(zs)) / 2]
    dist = 0.6
    for _ in range(5):
        lo, hi = 0.08, 4.0
        for _step in range(22):
            dist = (lo + hi) / 2
            eye = _eye(look, yaw, pitch, dist)
            span = _span(points, eye, look, tan_h, tan_v)
            wide = (span[1] - span[0]) / 2
            tall = (span[3] - span[2]) / 2
            if span is None or wide > target_w or tall > target_h:
                lo = dist
            else:
                hi = dist
        dist = hi
        eye = _eye(look, yaw, pitch, dist)
        span = _span(points, eye, look, tan_h, tan_v)
        cx = (span[0] + span[1]) / 2
        cy = (span[2] + span[3]) / 2
        right, up, view = _basis(eye, look)
        depth = sum(_dot(_sub(p, eye), view) for p in points) / len(points)
        look = list(_add(look, _add(_mul(right, cx * tan_h * depth), _mul(up, cy * tan_v * depth))))
    eye = _eye(look, yaw, pitch, dist)
    return eye, tuple(look), math.degrees(vfov), 0.02


def shelf_points(points):
    """Corners of the board under the model. The camera fit has to include them."""
    xs, _, zs = zip(*points)
    span_x = (max(xs) - min(xs)) + 0.018
    span_z = 0.040
    cx = (min(xs) + max(xs)) / 2
    cz = (min(zs) + max(zs)) / 2
    return [
        (x, y, z)
        for x in (cx - span_x / 2, cx + span_x / 2)
        for y in (0.0, -0.016)
        for z in (cz - span_z / 2, cz + span_z / 2)
    ]


def studio(points):
    """A board just under the model and a neutral panel just behind it."""
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
        obj.scale = (sx, sz, sy)
        cx, cy, cz = gltf_center
        obj.location = (cx, -cz, cy)
        obj.data.materials.append(mat)

    xs, ys, zs = zip(*points)
    # A shallow shelf under the model. A deep board becomes the floor at this range.
    span_x = (max(xs) - min(xs)) + 0.018
    span_z = 0.040
    cx = (min(xs) + max(xs)) / 2
    cz = (min(zs) + max(zs)) / 2
    cube("board", (cx, -0.008, cz), (span_x, 0.016, span_z), board)


def camera_play(kind, name):
    yaw, pitch, dist, look = PLAY[kind][name]
    cp = math.cos(math.radians(pitch))
    sp = math.sin(math.radians(pitch))
    eye = (
        look[0] + dist * cp * math.sin(math.radians(yaw)),
        look[1] + dist * sp,
        look[2] + dist * cp * math.cos(math.radians(yaw)),
    )
    return eye, look, PLAY_FOV, 0.04


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
        "portrait": not picker,
        "background": [0.27, 0.26, 0.25] if picker else [0.55, 0.51, 0.45],
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
    only = [part for part in os.environ.get("KNIFE_SHOTS", "").split(",") if part]

    def want(name):
        return not only or any(name.startswith(part) for part in only)
    # Practised-toss samples. Printed from FlipPhysics and copied here.
    held = (0.10, 0.24, 1.20, 0.0)
    mid_throw = (0.1169909, 0.25707525, 1.8668044, 0.0)
    stuck_chef = (0.16151622, 0.17572834, 2.7697892, 0.0)
    stuck_cleaver = (0.13598731, 0.10781152, 2.9096184, 0.0)
    mid_bottle = (0.2085606, 0.73912394, -3.3024645, 0.58984977)
    landed = (0.3151777, 0.042869568, 0.011305547, 0.0)
    fills = [("quarter", 0.25), ("third", 1 / 3), ("half", 0.5), ("three-quarter", 0.75), ("full", 1.0)]

    def knife_picker(name):
        theta, lift, points = preview_pose(name, False)

        def build():
            studio(points)
            place_knife(name, 0, lift, theta)
        if want(f"picker-{name}"):
            shot(f"picker-{name}", build, preview_camera(points + shelf_points(points), False))

    for name in KNIVES:
        knife_picker(name)

    def bottle_picker(label, fill):
        theta, lift, points = preview_pose("bottle", True)

        def build():
            studio(points)
            # place_bottle takes the centre of mass. The fit puts the base at (0, lift).
            h = com_from_base(fill, 0.0)
            place_bottle(h * math.sin(theta), lift + h * math.cos(theta), theta, fill, 0.0)
        if want(f"picker-bottle-{label}"):
            shot(f"picker-bottle-{label}", build, preview_camera(points, True))

    for label, fill in fills:
        bottle_picker(label, fill)

    def room_knife(asset, pose, chips=False):
        def build():
            import_roots(os.path.join(ASSETS, "room.glb"))
            place_knife(asset, pose[0], pose[1], pose[2])
            if chips:
                chip_burst(tip_of(asset, pose[0], pose[1], pose[2]))
        return build

    if want("side-held"):
        shot("side-held", room_knife("throwing", held), camera_play("knife", "side"))
    if want("mid-flip"):
        shot("mid-flip", room_knife("throwing", mid_throw), camera_play("knife", "side"))
    if want("stuck-chef"):
        shot("stuck-chef", room_knife("chef", stuck_chef, chips=True), camera_play("knife", "side"))
    if want("stuck-cleaver"):
        shot("stuck-cleaver", room_knife("cleaver", stuck_cleaver, chips=True), camera_play("knife", "side"))
    if want("corner"):
        shot("corner", room_knife("throwing", held), camera_play("knife", "corner"))
    if want("block-top"):
        shot("block-top", room_knife("chef", stuck_chef), camera_play("knife", "top"))

    def room_bottle(pose, fill=1 / 3):
        def build():
            import_roots(os.path.join(ASSETS, "room.glb"))
            place_bottle(pose[0], pose[1], pose[2], fill, pose[3])
        return build

    if want("bottle-mid"):
        shot("bottle-mid", room_bottle(mid_bottle), camera_play("bottle", "side"))
    if want("bottle-landed"):
        shot("bottle-landed", room_bottle(landed), camera_play("bottle", "side"))


if __name__ == "__main__":
    main()
