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
