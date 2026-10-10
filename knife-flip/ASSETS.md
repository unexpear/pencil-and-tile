# Knife Flip assets

The meshes are original. `tools/knife-flip/generate_models.py` builds them in Blender and writes GLB into `app/src/main/assets/knife-flip/`. Regenerate with:

```
blender --background --python tools/knife-flip/generate_models.py
```

Proportions follow `FlipPhysics` (an 8-inch chef's knife, a centre-balanced throwing knife, an open folder, and Dekker's 204 mm bottle). They are not traced from a branded product. No knife, bottle, or room model was downloaded. The butterfly knife and the cleaver stay in `generate_models.py` and export only when `KNIFE_SHELVED=1`. Their GLBs are not in the app assets.

Blender's glTF exporter on this machine has no Draco shared library, so the GLBs are uncompressed. The meshes are small. Wood maps are embedded JPEGs.

## Downloaded files (CC0 only)

| Asset | Use | Source | Author | License | Modifications |
| --- | --- | --- | --- | --- | --- |
| Kiara Interior 1K HDR | Image-based light and reflections only. It is not drawn as the background. | https://polyhaven.com/a/kiara_interior | Greg Zaal | CC0 1.0 | Downloaded the 1K `.hdr` only (1.7 MB). No other edit. |
| Wood051 1K | Knife handles | https://ambientcg.com/view?id=Wood051 | ambientCG / Lennart Demes | CC0 1.0 | Kept Colour, NormalGL and Roughness. Did not ship the displacement map, preview, or the included `.blend`. Handles use a copy scaled to 512 px inside the generator. |

The source JPEGs live in `tools/knife-flip/textures/` so the script can be re-run. The HDRI is the runtime file `app/src/main/assets/knife-flip/kiara_interior_1k.hdr`.

## Made for this app

chef, throwing, pocket, bottle shell and cap, water body, water neck, cutting block, counter, cabinet, walls, window, floor, ceiling, mug, and four wood splinters. Steel roughness is a brush map drawn by the script. The block top is a generated maple end-grain, and the block sides and chips are a lighter long grain. The counter uses the Wood051 maps at a scale of about one repeat across the top.

The throwing knife's steel and cord stay inside `FlipModels.THROWING_THICKNESS` (5 mm). Outlines are spine and edge splines in `blade_profiles.py`: an 8-inch chef (200×48 mm, spine flat then a convex drop, belly on the edge, tip a third of the way up), a straight spear-point thrower, and an 85×24 mm drop-point folder. The pocket knife handle is about 110 mm with liners, scales and a clip. The butterfly and cleaver outlines stay in that script for the shelved generator path. Chips are small irregular splinters. The room is a closed box, so the HDRI lights the scene and does not show up as a photograph behind it. The walls are a warm plaster with a slow tone shift, and a tile backsplash sits between the counter and the window. The block top is a grid of rectangular end-grain staves, each with partial growth-ring arcs and a slightly different maple tone, not a field of round discs. Water is an opaque light-blue column with a meniscus, clipped so the surface stays inside the glass. The bottle is alpha-blended glass (alpha 0.28) rather than a refraction material, because Filament's transmission pass does not show a liquid inside the bottle. Generated images are written to PNG before export: a packed pixel buffer goes out as a black JPEG, which made the block, counter and steel roughness render black. The exported water GLBs are the untilted volumes at the same radii.

## Renderer

Knife Flip uses `io.github.sceneview:sceneview` 4.18.0 (Apache 2.0), which brings Filament 1.71.5 (Apache 2.0). 4.52.0 is newer but it is built with Kotlin 2.4, and no KSP release supports Kotlin 2.4 yet. This app processes Room with KSP, and KSP 2.3.12 also requires Android Gradle Plugin 8.12. SceneView 4.18.0 is the newest release that still compiles here: Kotlin 2.3.20, KSP 2.3.11, AGP 8.10.1, minSdk 26 (the library's minSdk is 24).

SceneView 4.18 is compiled against Compose BOM 2026.05.01. The app uses that same BOM. Pinning an older one (2024.09.00) removes `Composer.shouldExecute` and `getCurrentCompositeKeyHashCode`, and Knife Flip crashes on the first composition, before a frame.
