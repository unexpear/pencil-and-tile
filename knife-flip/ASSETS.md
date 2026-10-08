# Knife Flip assets

The meshes are original. `tools/knife-flip/generate_models.py` builds them in Blender and writes GLB into `app/src/main/assets/knife-flip/`. Regenerate with:

```
blender --background --python tools/knife-flip/generate_models.py
```

Proportions follow `FlipPhysics` (an 8-inch chef's knife, a centre-balanced throwing knife, an open folder, an open balisong, an 18×9 cm cleaver, and Dekker's 204 mm bottle). They are not traced from a branded product. No knife, bottle, or room model was downloaded.

Blender's glTF exporter on this machine has no Draco shared library, so the GLBs are uncompressed. The meshes are small. Wood maps are embedded JPEGs.

## Downloaded files (CC0 only)

| Asset | Use | Source | Author | License | Modifications |
| --- | --- | --- | --- | --- | --- |
| Kiara Interior 1K HDR | Image-based light and reflections only. It is not drawn as the background. | https://polyhaven.com/a/kiara_interior | Greg Zaal | CC0 1.0 | Downloaded the 1K `.hdr` only (1.7 MB). No other edit. |
| Wood051 1K | Counter, butcher block, knife handles | https://ambientcg.com/view?id=Wood051 | ambientCG / Lennart Demes | CC0 1.0 | Kept Colour, NormalGL and Roughness. Did not ship the displacement map, preview, or the included `.blend`. Handles use a copy scaled to 512 px inside the generator. |

The source JPEGs live in `tools/knife-flip/textures/` so the script can be re-run. The HDRI is the runtime file `app/src/main/assets/knife-flip/kiara_interior_1k.hdr`.

## Made for this app

chef, throwing, pocket, butterfly, cleaver, bottle shell and cap, water body, water neck, cutting block, counter, cabinet, walls, window, floor, ceiling, canister, and four wood splinters. Steel roughness is a brush map drawn by the script.

The throwing knife's steel and cord stay inside `FlipModels.THROWING_THICKNESS` (5 mm). The pocket knife is an open drop-point folder: 18 mm blade, 28 mm handle, about 14 mm thick. Chips are small irregular splinters with the wood material. The room is a closed box, so the HDRI lights the scene and does not show up as a photograph behind it. Water in the app is a vertical column with a tilted top, clipped so the surface stays inside the glass. The exported water GLBs are the untilted volumes at the same radii.

## Renderer

Knife Flip uses `io.github.sceneview:sceneview` 4.18.0 (Apache 2.0), which brings Filament 1.71.5 (Apache 2.0). 4.52.0 is newer but it is built with Kotlin 2.4, and no KSP release supports Kotlin 2.4 yet. This app processes Room with KSP, and KSP 2.3.12 also requires Android Gradle Plugin 8.12. SceneView 4.18.0 is the newest release that still compiles here: Kotlin 2.3.20, KSP 2.3.11, AGP 8.10.1, minSdk 26 (the library's minSdk is 24).

SceneView 4.18 imports Compose BOM 2026.05.01. The app module uses `enforcedPlatform` on Compose BOM 2024.09.00 so the rest of the games keep their existing Material3 calls. SceneView's own Compose usage is `AndroidView`, `remember`, and `BasicText` in a debug overlay.
