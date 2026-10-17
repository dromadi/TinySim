# Material textures

TinySim's photo materials come from [Poly Haven](https://polyhaven.com) and are **CC0 1.0** (public domain, no
attribution required; credited here anyway). `fetch_textures.py` downloads the exact files, verifies Poly Haven's MD5
checksums and writes `app/src/main/assets/textures/index.json` with sources, checksums and the mean albedo colour.

| Slot | Poly Haven asset | Used for |
|---|---|---|
| sand | coast_sand_01 | sand piles, bucket and truck loads |
| soil | dry_ground_rocks | the deformable work-site ground |
| grass | leafy_grass | meadow and hills |
| asphalt | asphalt_02 | finished road sections |
| concrete | brushed_concrete | kerbs, foundation slab, steps |
| paint | blue_metal_plate | vehicle paint wear (recoloured to each vehicle's colour) |
| wood | weathered_planks | fence posts |
| plaster | clay_plaster | house walls |
| roof | clay_roof_tiles_02 | house roof |

Albedo and normal maps are 2K, roughness 1K, all original Poly Haven JPGs (no re-encoding). The renderer projects them
triplanarly (models have no UVs), in world space for the site and in each vehicle's own space for vehicles.
