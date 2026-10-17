# Verifikasi TinySim 3D — 0.1.0-alpha.4

Tanggal: 17 Oktober 2026. Integrasi melanjutkan commit alpha.3 `bbaa04f6529f77d495c0b73f9235d91b99f75262` dari branch `ccr-8d3a4508-l85vrd`. Grafik/material/model alpha.3 dipertahankan; alpha.4 menambah orbit/pinch/zoom, save kamera, lengan dua ruas, jangkauan fisik, serta fallback shader yang lebih aman.

## Hasil aktual

- `gradle --no-daemon --max-workers=2 --stacktrace clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: **BUILD SUCCESSFUL**, 2 menit 41 detik, 75 task (74 dijalankan).
- **16 tes unit lulus**, 0 gagal/error/skipped: 5 GameState, 6 WorldCamera/snapshot, 3 Geometry, 2 ArmRig. Tes kamera mencakup round-trip ray-plane pada semua kombinasi batas orbit/elevasi/zoom; tes lengan memeriksa panjang kedua ruas dan jangkauan. GameState tetap menguji siklus ambil → muat → jalan → tuang → ratakan dan konservasi 36 unit pasir.
- Lint: **0 error, 1 warning** landscape `DiscouragedApi` yang sama dengan versi sebelumnya.
- APK package `com.dromadi.tinysim`, versionName `0.1.0-alpha.4`, versionCode 4, min SDK 26, target SDK 35. `apksigner verify --verbose` berhasil (signature v2). `aapt dump permissions` tidak mencantumkan permission.
- SHA-256 APK lokal: `d2dd429aa8b357c55a9623d90b775af911c0602b64a0518732542bd3cda796e7`.
- Capture JVM menjalankan renderer asli pada 8 fixture: tiga preset, orbit dekat/jauh, muatan, bak terangkat, dan bulldozer. Setiap fixture diperiksa memiliki 36 unit pasir. Replay Chromium headless/WebGL dengan shader asli: **8/8 PASS**, GL error 0, tanpa error kompilasi/link shader. Default scene: **931 draw call**, termasuk depth pass.
- Capture kedua memaksa hasil query highp menjadi tidak didukung. Renderer tidak mengompilasi depth shader; shader warna dan varying memakai jalur mediump. Replay: **8/8 PASS**, GL error 0. Default scene fallback: **669 draw call**. Ini simulasi fallback pada GPU desktop; presisi GPU HP lama belum diuji.
- Bukti mentah: `docs/verification/alpha4/` (build, XML tes, lint, metadata/signature/hash APK, capture/replay, hasil tiap skenario). Tiga gambar JPEG adalah capture canvas renderer: kamera awal, orbit dekat, dan fallback. Tidak ada HUD Android dalam pratinjau ini.

## Batas yang belum terverifikasi

**Tidak ada klaim uji sentuh/emulator/HP untuk APK alpha.4.** Emulator tanpa KVM pada percobaan sebelumnya sangat lambat; pengujian native tidak diulang untuk integrasi ini. APK instrumentasi `SmokeInstrumentation` berhasil dikompilasi, tetapi belum dijalankan. Runner tersebut menguji isolasi pinch/orbit, tombol zoom, tombol alur konstruksi, konservasi pasir, dan gerbang orang tua melalui event Android.

Pratinjau JVM/WebGL hanya membuktikan kode renderer dan shader pada desktop; tidak membuktikan lifecycle, sentuhan, audio, FPS, suhu, atau baterai Android. Tes unit memeriksa logika permainan, bukan keseluruhan pengalaman di HP. Lifecycle pause/resume dan Back tetap memakai integrasi alpha.2; perubahan kamera disimpan saat release/cancel/pause. Perlu uji nyata, khususnya Android 16, sebelum rilis stabil.

Kualitas grafik tinggi tetap aktif secara default. Kamera orbit dibatasi ke depan bidang kerja. Pasir tetap simulasi 24 sel deterministik, bukan granular physics 3D bebas. Debug key lokal/CI/versi sebelumnya dapat berbeda; install -r bisa ditolak karena signature. Uninstall menghapus save, jadi jangan menganggap save dapat dipertahankan lintas key.

Percobaan build pertama gagal saat Gradle menyiapkan cache dependency (`Cannot snapshot output property outputDirectory`); build bersih berikutnya berhasil. Ini dicatat sebagai kegagalan persiapan lokal, bukan hasil tes yang lulus. Status CI harus dibaca pada run untuk commit yang dipublikasikan.

---

# Verifikasi TinySim 3D — 0.1.0-alpha.3 (grafik)

Tanggal: 17 Oktober 2026. Perubahan alpha.3 hanya pada rendering; logika permainan, input, save, dan HUD tidak berubah.

- `gradle --no-daemon clean testDebugUnitTest lintDebug assembleDebug`: **BUILD SUCCESSFUL**.
- **12 tes lulus**, 0 gagal: 5 GameState, 4 WorldCamera/snapshot, 3 Geometry baru (winding dan normal menghadap ke luar untuk cube/bevel/sphere/cylinder, batas bevel cube, gundukan pasir menghadap ke atas).
- Lint: **0 error, 1 warning** (`DiscouragedApi` landscape yang disengaja, sama seperti alpha.2).
- Screenshot `docs/verification/graphics/*.png` dihasilkan oleh `tools/scene-preview`: kode `SceneRenderer3D` asli dijalankan di JVM dengan stub GLES20 yang merekam panggilan GL, lalu diputar ulang di Chromium WebGL 1.0 (SwiftShader) dengan shader GLSL yang sama. Keenam skenario (3 kamera, muatan, menuang, bulldozer) berjalan tanpa error GL atau error kompilasi shader.
- Satu frame berisi sekitar 930 draw call (depth pass + pass warna) dan sekitar 172 ribu segitiga.
- Pada emulator Android 11 x86_64 tanpa KVM, APK alpha.3 berhasil membuat konteks EGL (`shadows=true 2048px`, MSAA fallback ke 0x di SwiftShader) dan merender dunia. Emulator kemudian terlalu lambat (System UI ANR) untuk pengujian interaktif, sehingga alur sentuh alpha.3 belum diulang di emulator.

**Batas:** FPS, panas, dan baterai pada HP nyata belum diukur; dua pass rendering lebih berat daripada alpha.2. Perbedaan presisi driver GPU dapat memengaruhi bayangan. Uji di perangkat nyata sebelum rilis.

---

# Verifikasi TinySim 3D — 0.1.0-alpha.2

Tanggal: 17 Oktober 2026. Kotlin 2.0.21, AGP 8.7.3, Gradle 8.9, JDK 17. Compile/target SDK 35; min SDK 26; OpenGL ES 2.0.

## Build dan tes lokal

Perintah aktual: `gradle --no-daemon --max-workers=2 clean testDebugUnitTest lintDebug assembleDebug`, menggunakan distribusi Gradle yang sama dengan wrapper.

- **BUILD SUCCESSFUL**, 38 detik; 50 task dieksekusi.
- **9 tes lulus**, 0 gagal, 0 error, 0 dilewati: 5 tes GameState dan 4 tes WorldCamera/snapshot.
- Lint: **0 error, 1 warning** `DiscouragedApi` untuk landscape yang disengaja.
- APK debug terverifikasi melalui `apksigner`, signature v2, satu signer.
- `aapt dump permissions` tidak mencantumkan izin apa pun, termasuk INTERNET.
- Versi aplikasi `0.1.0-alpha.2`, versionCode 2, package `com.dromadi.tinysim`.

Tes mencakup siklus ambil→muat→gerakkan truk→tuang→ratakan dan konservasi 36 unit pasir; kapasitas truk; transfer tidak valid; batas gerak dan delta waktu; round-trip save dan save rusak; progres misi; migrasi save alpha.1; state bak/blade/arah yang disimpan; kepemilikan salinan array untuk thread GL; serta round-trip proyeksi dan ray-plane picking pada ketiga kamera.

Bukti mentah alpha.2 tersimpan dalam `docs/verification/3d/`. Bukti alpha.1 tetap berada pada folder induk untuk riwayat; gambar alpha.1 tidak digunakan sebagai bukti rendering 3D.

## Rendering dan interaksi emulator

Emulator Android 11/API 30, x86_64, layar 1200×675, Google SwiftShader, tanpa KVM. Rendering menggunakan mesh/VBO, perspektif, depth test, normal permukaan, shader pencahayaan, dan GL thread yang sesungguhnya. Screenshot adalah hasil aplikasi yang berjalan, bukan mockup.

- Upgrade APK di atas alpha.1 berhasil. Save lama berisi sumber 32 dan tanah 4 tetap terbaca, total 36; format save baru ditulis dengan state tambahan.
- Ketiga tombol kamera berhasil mengganti sudut pandang. Screenshot tersedia untuk perspektif kanan, depan, dan kiri.
- Pada kamera 3, event sentuh Android menyeret bucket ke pasir dan ke bak dua kali. Muatan menjadi **4**, sumber **32**, ember/tanah **0**, total **36**. Misi bergambar maju ke tahap menuang.
- Home → buka lagi menghasilkan `LaunchState: HOT`; dunia GL kembali terlihat dengan muatan tersimpan.
- Pilih truk → tahan kanan → angkat bak: posisi berubah dari 620 ke 749,6002; muatan menjadi **0**, delivered **4**, tanah **4**. Misi maju ke tahap meratakan.
- Setelah Home dan force-stop, cold launch berhasil. Kamera 3, posisi kendaraan, pasir, tipping, serta sudut bak sekitar −42° dipulihkan. Log menunjukkan mesh dibuat ulang pada konteks EGL baru.

- Bulldozer berhasil menyebarkan satu tumpukan 4 unit menjadi empat sel masing-masing 1 unit. Misi meratakan selesai dan kembali ke misi mengisi.
- Tap singkat pada gembok tidak membuka area orang tua; tahan 3,8 detik membukanya. Back menutup panel. Dialog reset dapat dibatalkan tanpa mengubah pasir; konfirmasi reset mengembalikan sumber 36, ember/muatan/tanah 0 dan posisi awal. Pengaturan suara nonaktif tersimpan.
- Wi-Fi dan data emulator dimatikan saat pemeriksaan; APK tidak memiliki izin INTERNET.
- Finishing visual memisahkan jalur truk dan bulldozer, memperbaiki bayangan kontak, memberi latar judul, dan menerapkan posisi jari pada akhir drag. APK hasil finishing dibangun bersih lagi, lalu diuji ulang melalui sentuhan Android.

Pada APK final, rangkaian sentuhan ulang menghasilkan muatan 4/sumber 32, kemudian tanah 4/muatan 0. Setelah mode bebas dipilih dan bulldozer dijalankan, `flattened = 4` dan empat sel tanah masing-masing berisi 1 unit. Total selalu 36. Ketiga hasil akhir tersedia sebagai `emulator-final-*.json`; log final tidak menunjukkan `OpenGL error` atau `FATAL EXCEPTION`. Screenshot menuang dan cold restore identik, sementara log mencatat pembuatan resource EGL pada proses baru.

Pada boot awal emulator, **System UI** sempat ANR dan satu pemanggilan launcher timeout. Setelah dialog sistem ditutup dan emulator selesai startup, peluncuran berikutnya mengembalikan `Status: ok`. Catatan ini tidak diperlakukan sebagai hasil performa HP nyata.

## Batas verifikasi

Belum diuji pada Samsung/Android 16 atau bersama anak usia 2–6 tahun. Emulator berjalan tanpa output audio; kualitas bunyi pada perangkat nyata belum didengarkan. FPS, penggunaan baterai, dan temperatur perangkat nyata belum diukur. Multi-jari dan TalkBack perlu uji perangkat; kontrol Canvas belum menyediakan virtual accessibility nodes per tombol.

Dunia ditampilkan dalam 3D, sedangkan lintasan kendaraan dan bidang gerak lengan tetap dibatasi untuk kemudahan kontrol anak. Pasir menggunakan sel deterministik; tidak ada simulasi rigid-body/granular 3D bebas. Bayangan kontak memakai geometri transparan, bukan shadow mapping.

## GitHub dan CI

Repository: https://github.com/dromadi/TinySim. Source, model prosedural, shader, dan workflow tersedia di repository. Workflow **Android debug** menjalankan tes, lint, dan build, lalu menyimpan artifact **TinySim-0.1.0-alpha.2-debug**. Status remote harus diperiksa untuk commit terkait; hasil lokal di atas berdiri sendiri.
