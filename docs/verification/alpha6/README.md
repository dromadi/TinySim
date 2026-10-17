# Bukti alpha.6

- `build.log`: build final dengan tes JVM, lint, APK debug, serta APK runner Android.
- `TEST-*.xml`: 41 tes JVM (logika permainan, sentuhan, kamera, geometri, lengan).
- `lint-results-debug.xml`: 0 error; satu warning landscape yang disengaja.
- `apk-*`: identitas, permission, signature, dan SHA-256 APK lokal yang dibagikan.
- `renderer-results.json`, `fallback-results.json`: status replay GL dan jumlah draw/triangle per adegan.
- `capture.log`, `fallback-capture.log`, `replay.log`, `fallback-replay.log`: eksekusi kode renderer/HUD asli pada JVM lalu WebGL.
- Empat JPG: excavator di area jauh, excavator berputar, proyek jalan sebagian, dan jalan selesai.

JPG dibuat oleh harness renderer/HUD, bukan screenshot perangkat Android. Browser dapat memiliki metrik font berbeda. Runner Android dikompilasi tetapi belum dijalankan pada HP/emulator.
