# Bukti alpha.7

Build Android menjalankan unit tests, lint, APK debug dan APK runner Android. XML JUnit merekam hasil uji, lint XML merekam pemeriksaan statis. Identitas, permission, signature, SHA-256 dan verifikasi konten APK dicatat terpisah.

Audio pack berisi tiga soundscape orisinal masing-masing 300 detik stereo PCM16 48 kHz. Verifier memeriksa header nyata, variasi data, fade batas loop, hash, ukuran APK >100 MiB, asset ZIP_STORED, CRC ZIP serta penolakan file audio tak terduga. Binary audio dibuat oleh generator Java saat build, bukan disimpan di Git.

Pratinjau menggunakan kode GameState, Terrain, Geometry, SceneRenderer3D dan GameRenderer asli. Rekaman GL/Canvas pada JVM diputar ulang di WebGL, untuk shader normal dan fallback. Gambar memperlihatkan lubang nyata, timbunan, bahan rumah, dinding serta rumah selesai. Ini bukan screenshot perangkat Android; font browser bisa berbeda.

Runner Android yang mengirim MotionEvent asli dikompilasi, tetapi belum dijalankan pada HP/emulator. Playback/audio focus serta lifecycle Android masih memerlukan pengujian perangkat.
