# Scene preview

Renders the game's real `SceneRenderer3D` without an Android device. The renderer runs on the JVM against
recording `GLES20` stubs; the recorded call stream is then replayed in Chromium WebGL 1.0 with the exact same GLSL.

```sh
cd tools/scene-preview
gradle run --args="$PWD/frames.json"       # JDK 17+, Gradle 8.x
node shoot.js frames.json out               # needs the `playwright` npm package and Chromium
```

Screenshots land in `out/`. Scenes are defined in `src/Main.kt`. WebGL 1.0 matches OpenGL ES 2.0 closely, but
driver precision, MSAA and performance on phones can differ; test on a real device before release.

Alpha.4 adds orbit/zoom fixtures. Every fixture conserves 36 sand units and the dump fixture uses the real negative bed angle. To exercise the no-highp shader path on the desktop, run `gradle run -PnoHighp --args="$PWD/frames-fallback.json"`, then replay that file. This validates fallback shader compilation and GL calls, not phone performance or native touch input.

`CHROMIUM_PATH` optionally selects an installed Chrome/Chromium executable. `PREVIEW_FORMAT=jpeg` saves JPEG at quality 90 instead of the default PNG. Replay now returns a nonzero process exit code on shader, linking, or GL errors, and writes per-scene draw/triangle counts to `results.json`.

Alpha.5 also captures the original `GameRenderer` Canvas HUD through recording Android graphics stubs. The browser replays it as a 2D canvas above WebGL; this previews the actual minimap/control layout, with browser font metrics rather than native Android font metrics. Additional conserved-inventory fixtures cover the map overview, construction yard, and building site. Screenshots still do not constitute a device or Android input test.

Alpha.6 adds mobile digging, a rotated excavator, a partially built road, and a completed road (15 scenes per shader path). These fixtures execute the same renderer/HUD as the APK; digging/movement/building behavior is separately exercised by the Kotlin tests.

Alpha.7 adds the real deforming terrain mesh, terrain-aware vehicle positions and house foundation/materials/walls/roof stages. `TINYSIM_PREVIEW_SCENES=start-cam1,terrain-pit,terrain-mound,house-materials,house-walls,house-complete,road-project` selects the seven current verification fixtures. Both shader paths render those fixtures; real touch/audio/device lifecycle still require Android device testing.

Alpha.8 renders the ground through `TerrainMesh`: the first frame uploads the full buffer, later scenes upload only changed rows via `glBufferSubData`, which the stubs record and the replay page applies.

Alpha.10 adds `vehicle-life`: 36 warm-up frames let the truck drive (dust, smoke, suspension) and greet the excavator driver, then the last frame is captured with the driver mid-wave.

Alpha.11 loads the real CC0 material textures: the JVM side records `texImageAsset` with each asset path and the replay page decodes those JPGs (`--allow-file-access-from-files`) before replaying. The preview build needs `org.json` from Maven Central.

Alpha.12 adds `bridge-crane` (the crane mid-swing carrying a steel girder over the river) and `bridge-open` (finished bridge with the truck crossing toward the east-bank quarry).

Alpha.13 adds `crane-grab`: the child-controlled crane right after its grab closed on a sand pile, with the truck waiting in reach and the crane card selected.

Alpha.15 adds `block-lift` and `block-tower`: real crane/block tasks lift a coloured prop, then stack all three. Select with `TINYSIM_PREVIEW_SCENES=block-lift,block-tower`. The completed scene waits for the celebration star to clear so the tower stays visible. A Chrome headless-shell executable may be selected with `CHROMIUM_PATH` in environments that prohibit the full browser singleton socket. These are renderer previews, not phone tests.

Alpha.17 adds `pipe-trench,pipe-lift,pipe-laid,pipe-complete`. All fixtures execute real excavation, crane tasks and backfill; they check material conservation. The exact camera also projects the touch pictograms into the HUD. These are JVM/WebGL renderer previews, not Android/phone tests.

Each Alpha.17 fixture now records a full terrain VBO upload before drawing. Replay uses a fresh WebGL context per screenshot; reusing only incremental changes from a previous fixture otherwise left later screenshots with flat initial ground. This reset is JVM-harness-only; Android still uses incremental terrain updates.

Alpha.18 adds `mixer-pour,wreck-impact,yard-finished,keeper-open`. Real model actions pour concrete, swing the wrecking ball, build the bridge and lift the gates; conserved soil, concrete and demolition counts are tested separately. Each fixture uploads its full terrain mesh so the expanded map is correct in a fresh WebGL replay context.

Alpha.20 adds `arena-wind,arena-sand,arena-ice,arena-duo`. They enter real arenas, grow using actual touch targets, freeze/lift training props, and run the offline helper. Original world material is checked before capture. HUD and both shadow/colour passes are recorded; these remain desktop renderer previews, not phone gameplay tests.

Alpha.21 adds `district-storm,district-push,district-tip,district-pour,district-lift,district-restored`. Fixtures run actual tornado, bulldozer/truck jobs, pouring and crane tasks. The sixteen conserved parts move through one main-world district; the enlarged heightfield is uploaded for every fixture. These remain desktop previews, not Android/phone tests.

Alpha.23 adds `village-welcome,village-garden,village-shelter`: real house recovery unlocks residents, actual planting fills gardens, and active weather hides all residents. New household frames own their arrays; these remain JVM/WebGL renderer previews rather than phone tests.

Alpha.24 adds `wash-dirty,wash-spray,wash-clean`, using real travel to the wash bay, shared close-up camera/picking, held spray and per-patch cleanliness. The water stream is recorded from the actual HUD and dirt from the 3D model. These remain desktop renderer previews, not phone input tests.
