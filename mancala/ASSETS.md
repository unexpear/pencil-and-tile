# Mancala assets

The board and the marble are original meshes. `tools/mancala/generate_board.py` builds them in Blender and writes GLB into `app/src/main/assets/mancala/`. Regenerate with:

```
blender --background --python tools/mancala/generate_board.py
```

Pit centres and radii are read from `MancalaBoard` in `MancalaScreen.kt`, and the bowl is the same cosine the stones sit on. The board is a rounded wooden slab with six pits a side and a store at each end. Each cup is a cosine bowl with a low lip, using the depths in `MancalaBoard`. The marble is a unit sphere; the app scales it to `MancalaBoard.stoneRadius` and tints each one as coloured glass.

No model was downloaded. The only image files read from disk are the CC0 Wood051 maps already kept for Knife Flip (`tools/knife-flip/textures/`: Colour, NormalGL, Roughness). They are embedded in `board.glb`. See `knife-flip/ASSETS.md` for the Wood051 and Kiara Interior attribution. Mancala reuses the Kiara HDRI at runtime for image-based light and does not draw it as a background.

Blender's glTF exporter on this machine has no Draco shared library, so the GLBs are uncompressed.
