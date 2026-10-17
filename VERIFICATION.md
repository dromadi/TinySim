# Verifikasi TinySim 0.1.0-alpha.1

Tanggal: 17 Oktober 2026. Build lokal Linux, JDK 17, Gradle 8.9, AGP 8.7.3, Kotlin 2.0.21, compile/target SDK 35, min SDK 26.

## Hasil final build bersih

Perintah aktual: `gradle --no-daemon --max-workers=2 clean testDebugUnitTest lintDebug assembleDebug` (menggunakan distribusi Gradle 8.9 yang sama dengan wrapper).

- **BUILD SUCCESSFUL**, 34 detik, 50 task dieksekusi.
- **5 tes unit lulus**, 0 gagal, 0 error, 0 dilewati.
- **Lint: 0 error, 1 warning** `DiscouragedApi` karena `sensorLandscape` yang disengaja untuk desain game landscape.
- APK terverifikasi menggunakan APK Signature Scheme v2, 1 signer debug.
- Badging: `com.dromadi.tinysim`, `0.1.0-alpha.1`, versionCode 1; launchable activity `com.dromadi.tinysim.MainActivity`.
- `aapt dump permissions` tidak mencantumkan `uses-permission` apa pun, termasuk INTERNET.
- Bukti mentah tersedia dalam `docs/verification/`: hasil Gradle, JUnit XML, lint, signature, manifest badging, izin dan SHA-256 APK.

Tes unit menjalankan alur ambil→muat→gerakkan truk→tuang→ratakan serta konservasi 36 unit pasir; kapasitas truk dan transfer di lokasi tidak valid; pembatasan gerak/delta waktu; round-trip snapshot dan input snapshot rusak; serta progres/perayaan misi.

## Pengujian emulator Android 11 (API 30)

Emulator x86_64 1200×675, tanpa akselerasi KVM. Boot awal sangat lambat dan System UI sempat ANR; setelah setup emulator selesai, TinySim berhasil diinstal dan diluncurkan (`Status: ok`, `LaunchState: COLD`). Wi-Fi dan data emulator dimatikan sebelum pengujian aplikasi. Tidak ada crash TinySim pada log AndroidRuntime yang diperiksa.

Interaksi menggunakan event sentuh Android (`input tap` / `input swipe`), bukan perubahan langsung ke GameState:

- Empat tap tombol bantuan excavator menjalankan dua siklus ambil/isi. Snapshot setelah Home: sumber 32, ember 0, muatan truk 4, tanah 0, total 36.
- Home → buka lagi menghasilkan `LaunchState: HOT`, dengan screenshot identik dan muatan tetap 4.
- Pilih truk → tahan panah kanan → angkat bak. Posisi truk berubah dari 620 ke 749,6002; muatan menjadi 0, delivered 4, dan satu sel tanah berisi 4. Total tetap 36.
- Setelah Home dan force-stop, buka lagi menghasilkan `LaunchState: COLD` dan dunia kembali dengan posisi serta pasir yang tersimpan.

- Tap singkat pada gembok tidak membuka panel (screenshot identik dengan dunia). Tahan 3,8 detik membuka area orang tua. Back menutupnya; tap speaker mengubah pengaturan suara menjadi `false` dan tersimpan di SharedPreferences.

- Pilih bulldozer → tahan kiri lalu kanan: `flattened = 4`; tumpukan 4 unit menjadi empat sel dengan masing-masing 1 unit. Sumber tetap 32, muatan/ember 0, total pasir 36. Log AndroidRuntime setelah seluruh alur tidak menunjukkan crash aplikasi.

## Catatan pengujian

Kelayakan untuk anak usia 2–6 tahun dan kualitas suara pada volume perangkat nyata belum divalidasi bersama pengguna. Emulator dijalankan tanpa output audio; tombol pengaturan dapat diuji, tetapi kualitas bunyi belum didengarkan. Emulator perangkat lunak tidak cukup untuk mengukur FPS atau kelancaran pada HP nyata. Pengujian Android 16/Samsung, cutout, multi-jari dan TalkBack masih perlu dilakukan. Kontrol Canvas belum menyediakan virtual accessibility nodes per tombol.

## GitHub

Repository `https://github.com/dromadi/TinySim` dibuat manual oleh pengguna pada 17 Oktober 2026. Pemeriksaan awal memastikan repository kosong, akun koneksi `dromadi` memiliki akses push, dan visibility repository adalah public. Tidak ada kode remote sebelumnya yang ditimpa.

Workflow `Android debug` disertakan untuk menjalankan tes, lint, dan build APK pada push/pull request atau workflow_dispatch. Hasil build lokal di atas berdiri sendiri; status CI remote harus diperiksa pada halaman Actions untuk commit terkait.
