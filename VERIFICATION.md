# Verifikasi Alpha.18 — armada baru, peta dan penjaga

Tanggal: 18 Oktober 2026 (WIB). `com.dromadi.tinysim`, versionCode 18 / `0.1.0-alpha.18`. Basis `2d288b1` (Alpha.17), tidak ada PR terbuka ketika pekerjaan dimulai.

## Isi permainan yang bertambah

Dua kendaraan baru memakai rute, tabrakan, antrean jembatan dan input sentuh yang sudah ada. Molen mengaduk dua detik kemudian menuang delapan unit beton ke petak, satu unit tiap 0,48 detik. Truk penghancur mengayunkan bola baja tiga kali untuk mengubah sembilan balok bangunan latihan menjadi sembilan puing. Bola disimpan di belakang kabin dan diangkat melewati atas kabin saat boom dibuka. Jalur ayun dan puing deterministik, tanpa impuls fisika bebas. Posisi kerja tidak saling menghalangi.

Peta diperpanjang sepuluh meter ke selatan (86 × 22 → 86 × 32 m, +45,45%). Bertambah bengkel, area cor, bangunan latihan, jalan marka dan area piknik timur. Kegiatan baru langsung bisa dicapai di tepi barat. Palang dua ujung jembatan terbuka setelah penjaga disapa atau kendaraan meminta lewat; kendaraan menunggu hingga palang terangkat. Palang tetap di atas selama dek terisi. Sebelum jembatan selesai, penjaga hanya menyapa, tanpa membuka akses ke air.

Save v13 memuat armada dan progres baru. Payload lama dengan 22 baris dimigrasikan, sedangkan 860 sel baru berisi tanah awal: total tanah/pasir sekarang **11.068 unit**. Beton delapan unit dan sembilan balok latihan adalah properti terpisah seperti `BlockYard`, bukan tanah tambahan. Total beton dan jumlah properti juga diuji. Kunci debug privat lama dipertahankan, tidak dimasukkan ke Git.

## Perintah dan pemeriksaan

JDK 17, Gradle 8.9, compile/target SDK 35. Build-Tools 34 dipilih AGP 8.7.3; inspeksi APK memakai Build-Tools 35. Build dan cache berada di `/tmp` agar tidak terganggu sinkronisasi workspace. Runner Python meneruskan proxy jaringan yang tersedia dan merekam stdout/stderr setelah proses selesai.

```sh
GRADLE_USER_HOME=/tmp/ts-gradle-home /tmp/ts-downloads/gradle-8.9/bin/gradle \
  --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process \
  testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

- **BUILD SUCCESSFUL in 55s**, 74 task (16 dieksekusi, 58 up-to-date). APK debug **67,798,358 byte**; SHA-256 `0b6a5f14b805afb37b26f8736b73556f51a93afa07889f15edf208e18c23621d`. Log final: [build.log](docs/verification/alpha18/build.log); hitungan, ukuran APK dan SHA-256: [result.json](docs/verification/alpha18/result.json).
- **117 tes lulus, 0 gagal/error/skipped**: 103 tes lama dan 14 tes `WorkFleetTest`. Cakupan: pengecoran lengkap/parsial, ketukan tombol dan badan truk berulang, ayunan dan puing, pause/cold restore, tabrakan enam kendaraan, lintasan sungai, penjaga/palang, save lama, save rusak, bay kerja terhalang dan evakuasi molen saat jembatan dibangun. Setiap langkah pekerjaan memeriksa material tanah/pasir serta beton.
- **Lint 0 error, 1 warning** `DiscouragedApi` karena orientasi landscape yang disengaja. Tes lama tetap berjalan; fixture prefix save dan dimensi payload lama disesuaikan agar tetap menguji format lama 22 baris, bukan tanpa sengaja berganti ke peta baru.
- APK permainan dan APK instrumentation dibangun. `apksigner verify --verbose --print-certs`, `aapt dump permissions`, `aapt dump badging`, pemeriksaan CRC ZIP dan tidak adanya WAV dijalankan pada APK nyata. Hasil mentah berada di direktori verifikasi.
- Sertifikat SHA-256 `f6700a05aaf7b1a92347a38c54cd65de29099c2c355c7e8b51217b6ecf7f283e`, sama dengan APK lokal Alpha.8–Alpha.17.
- `adb devices` dijalankan ulang. Tidak ada uji HP/emulator; pengujian lifecycle hanya pada model/save JVM. Runner sentuh diperbarui untuk posisi kartu dan inventaris saat ini, tetapi **hanya dikompilasi**, tidak dijalankan pada perangkat.

Uji pengembangan sempat menemukan tiga kegagalan: setelah molen selesai, posisi parkirnya menghalangi bay penghancur. Bay dipindahkan dari x=28 ke x=28,5, lalu seluruh tes dijalankan kembali. Tes tambahan memeriksa bay yang sengaja dihalangi agar tidak mengulang rute tanpa akhir. Tidak ada tes yang dihapus, dilewati atau dilemahkan agar lulus.

## Pratinjau renderer asli

```sh
cd tools/scene-preview
TINYSIM_PREVIEW_SCENES=mixer-pour,wreck-impact,yard-finished,keeper-open \
  /tmp/ts-downloads/gradle-8.9/bin/gradle --no-daemon --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process run --args=/tmp/ts-frames18-final.json
CHROMIUM_PATH=/tmp/ts-chrome/chrome-headless-shell-linux64/chrome-headless-shell \
  PREVIEW_FORMAT=jpeg node shoot.js /tmp/ts-frames18-final.json /tmp/ts-shots18-final
```

Fixtures menjalankan model pekerjaan nyata, termasuk pembangunan jembatan sebelum membuka palang. Inventaris tanah/pasir diperiksa sebelum merekam setiap frame. VBO terrain lengkap diunggah per fixture untuk konteks replay baru; Android tetap memakai pembaruan inkremental. Shader, model, tekstur dan HUD asli direkam JVM kemudian diputar di WebGL.

**Empat pratinjau PASS, GL error 0**; hasil mentah pada `results.json`.

Alat preview sempat kehabisan heap Node saat mengirim seluruh rekaman empat adegan sekaligus ke browser. `shoot.js` diperbaiki untuk mengirim adegan yang sedang diputar saja, kemudian keempatnya dirender ulang. Ini masalah alat desktop, bukan bukti performa HP. Gambar di [docs/verification/alpha18](docs/verification/alpha18/) **bukan screenshot Android**, uji sentuh/audio native atau pengukuran FPS HP.

---

# Verifikasi Alpha.17 — proyek pipa bawah tanah

Tanggal: 18 Oktober 2026 (WIB). `com.dromadi.tinysim`, versionCode 17 / `0.1.0-alpha.17`.

Basis sumber: `816f84a` (Alpha.15); tidak ada PR terbuka saat pekerjaan dimulai. Sumber Alpha.16 yang belum dipush sebelumnya tidak tersedia, sehingga penggalian terpandu dibuat kembali pada basis tersebut. Alpha.15 (balok), sungai/jembatan, tabrakan kendaraan, mesh tanah inkremental, tekstur serta sintesis audio dipertahankan.

Yang dapat dimainkan: sentuh kartu pipa lalu enam tanda bucket untuk menggali parit 6 × 2 m sedalam 0,44 m. Excavator mengangkat 24 unit tanah dan meletakkannya pada timbunan samping. Tiga pipa jingga diangkat dan dipasang memakai crane; bulldozer kemudian mendekati timbunan, menurunkan blade dan mengembalikan tanah sedikit demi sedikit. Penimbunan merupakan bantuan berbasis sel dengan sumber timbunan samping, bukan simulasi butiran/rigid-body. Dua tutup inspeksi dan empat bendera toska tetap terlihat saat selesai. Tangan penunjuk dapat membawa anak melewati seluruh kegiatan.

## Build, tes dan pemeriksaan

JDK 17, Gradle 8.9, Android compile/target SDK 35. Build-Tools 34.0.0 dipilih otomatis AGP 8.7.3; SDK lokal juga memiliki 35.0.0 untuk inspeksi APK. Proyek dan cache dibangun di `/tmp` agar output build tidak terganggu sinkronisasi workspace.

Perintah final yang benar-benar dijalankan (runner Python hanya meneruskan proxy koneksi yang tersedia dan merekam output lengkap):

```sh
GRADLE_USER_HOME=/tmp/ts-gradle-home /tmp/ts-downloads/gradle-8.9/bin/gradle \
  --no-daemon --max-workers=2 -Pkotlin.compiler.execution.strategy=in-process \
  testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

- **BUILD SUCCESSFUL in 52s**, 74 task (16 dieksekusi, 58 up-to-date). APK debug **67,772,264 byte**; SHA-256 `c3425da410ac702b2a90c241811ae67195c5e8ac1ec910050273095875e5081d`. Hasil final dan ukuran/checksum APK: [result.json](docs/verification/alpha17/result.json), [build.log](docs/verification/alpha17/build.log).
- **103 tes lulus, 0 gagal/error/skipped**: 92 tes sebelumnya + 11 tes `PipeSiteTest`. Laporan XML asli disimpan di direktori yang sama. Tes meliputi seluruh alur melalui `Guide`, kedalaman parit, semua transfer material setiap tick, kontak sebelum menggali, ketukan aksi berulang, pembatalan perjalanan, pause/cold restore dengan bucket/pipa, penimbunan parsial, migrasi payload v10/v11, reset dan save rusak. Fixture migrasi adalah payload sintetis dengan format lama, bukan save yang diambil dari HP.
- **Lint 0 error, 1 warning** `DiscouragedApi` untuk landscape yang disengaja. Tidak ada tes lama dihapus/dilewati/dilemahkan; pemeriksaan prefix save diperbarui, dan fixture korupsi memakai indeks field yang dituju agar penambahan field baru tidak mengubah tujuan tes.
- APK permainan dan APK instrumentation berhasil dibangun. `apksigner verify --verbose --print-certs`, `aapt dump permissions`, `aapt dump badging`, serta pemeriksaan CRC ZIP dijalankan pada hasil nyata. Tidak ada Android permission, WAV atau paket audio pengisi.
- SHA-256 sertifikat debug: `f6700a05aaf7b1a92347a38c54cd65de29099c2c355c7e8b51217b6ecf7f283e`, sama dengan APK lokal Alpha.8–Alpha.16. Keystore tetap privat dan tidak disertakan di repo.
- `adb devices` berhasil dijalankan; tidak ada perangkat terhubung. **Tidak ada uji sentuh/lifecycle/audio di HP atau emulator.** Tes pause/restore hanya memverifikasi model dan save JVM. APK instrumentation dikompilasi, tidak dijalankan.

Build pertama tertahan konfigurasi proxy unduhan dependency; koneksi runner diperbaiki tanpa mengubah repository dependency. Uji awal kemudian menemukan satu kegagalan penanda yang bertumpuk (100/101 lulus). Tata letak penanda diperbaiki, lalu dua tes interaksi/resume ditambahkan. Kompilasi Kotlin awal sempat memakai fallback setelah daemon compiler gagal startup; perintah final memakai compiler in-process dan berhasil. Keberhasilan akhir tidak menghapus riwayat kegagalan tersebut.

## Pratinjau visual

```sh
cd tools/scene-preview
TINYSIM_PREVIEW_SCENES=pipe-trench,pipe-lift,pipe-laid,pipe-complete \
  /tmp/ts-downloads/gradle-8.9/bin/gradle --no-daemon --max-workers=2 \
  -Pkotlin.compiler.execution.strategy=in-process run --args=/tmp/ts-frames17-fixed.json
CHROMIUM_PATH=/tmp/ts-chrome/chrome-headless-shell-linux64/chrome-headless-shell \
  PREVIEW_FORMAT=jpeg node shoot.js /tmp/ts-frames17-fixed.json /tmp/ts-shots17-fixed
```

Renderer dan Canvas HUD asli dijalankan lewat JVM lalu diputar di WebGL dengan shader dan tekstur yang sama. Empat fixture melakukan kegiatan model sungguhan, bukan mengisi flag selesai secara manual. Harness kini merekam upload penuh terrain VBO untuk setiap fixture: masing-masing gambar diputar di konteks WebGL baru, sehingga delta dari gambar sebelumnya tidak cukup. Sebelum koreksi ini, gambar setelah fixture pertama dapat menampilkan tanah awal datar yang menutupi pipa. Koreksi hanya pada harness JVM, bukan renderer incremental Android. Excavator dipindahkan keluar lewat `driveTo` setelah menggali agar tidak menutupi hasilnya. Semua fixture memeriksa konservasi material. Penanda digeser ke depan agar parit/pipa tetap terlihat; lapisan tanah yang baru terbuka diberi warna lebih gelap dan tertutup kembali saat tinggi tanah pulih.

Pemeriksaan Android setelah koreksi harness juga **BUILD SUCCESSFUL in 7s**, 74 task (1 dieksekusi, 73 up-to-date); APK dan SHA-256 identik. Log tambahan: `pre-push-check.log`.

**Empat scene PASS, seluruhnya GL error 0.** Gambar dan hasil shader/GL: [docs/verification/alpha17](docs/verification/alpha17/). **Ini pratinjau renderer desktop, bukan screenshot Android, uji kontrol sentuh, atau bukti FPS HP.**

---

# Verifikasi Alpha.15 — crane menyusun balok

Tanggal: 18 Oktober 2026 (WIB). versionCode 15; `0.1.0-alpha.15`.

Basis: `92489e9` (PR #1 sudah merged). Basis tersebut **sudah** menghapus generator/WAV 172,8 MB dan pemeriksaan ukuran minimum, serta menambahkan `TerrainMesh` inkremental, suara mesin/hidrolik/pasir, crane, sungai dan jembatan. Alpha.15 melanjutkan sistem tersebut, bukan mengimplementasikannya ulang.

Perubahan yang bisa dimainkan: tiga balok berwarna di area barat rumah. Crane mengambil dengan sling, mengangkat, lalu menumpuknya pada sasaran lebar. Anak bisa memakai sentuhan langsung atau tombol aksi bergambar. Balok boleh diletakkan di tanah dan diambil lagi; air bukan sasaran. Kartu progres crane membuka area balok. Tangan penunjuk melanjutkan ke balok setelah jembatan selesai. Ada bunyi benturan sintetis dengan attack lembut, animasi, bayangan dan bintang pemasangan.

Save v10 mempertahankan posisi, tingkat dan muatan balok; v9 dan format lama dimigrasikan. Tiga balok adalah properti terpisah dari inventaris material. Pause/restore membatalkan gerakan, mempertahankan balok dan material.

Perintah yang dijalankan dari root proyek (distribusi Gradle lokal 8.9, JDK 17, SDK 35):

```sh
bash /workspace/scratch/250fb4835a8a/collision-toolchain/gradle-8.9/bin/gradle --no-daemon --max-workers=2 testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

- **BUILD SUCCESSFUL in 1m 1s**. 92 tes, 0 gagal/error/skipped (85 tes sebelumnya + 7 tes `BlockYardTest`). `GuideTest` juga diperluas sampai menara selesai. Tes migrasi lama diperbarui untuk mengambil payload tanpa bergantung pada prefix save terbaru.
- Lint: **0 error, 1 warning** `DiscouragedApi` untuk orientasi landscape yang disengaja.
- APK: **67.756.722 byte**; SHA-256 `003e3da9b7ccca39b5a584e8e671db23935faf085ed724e7c76b0440dc8585ae`.
- ZIP CRC lulus; tidak ada `.wav`, `.ogg`, `.mp3`, atau `.opus` di APK (audio disintesis). Tidak ada izin Android (`aapt dump permissions`). Signature APK diverifikasi dengan `apksigner verify --print-certs`, memakai kunci lokal Alpha.8 yang dipertahankan.
- Build pertama dengan tes baru: 91 lulus, 1 gagal pada skenario pause yang masih memilih excavator ketika memanggil tombol aksi crane. Skenario diperbaiki agar memilih crane seperti alur UI; pengujian ulang penuh menghasilkan 92 lulus. Tidak ada tes dinonaktifkan atau assertion dihapus.
- Setelah posisi sasaran digeser agar terlihat, tes jembatan menangkap regresi prioritas tombol crane: susun balok sempat mengambil alih aksi jembatan. Prioritas diperbaiki di kode: muatan balok diselesaikan dulu, kemudian proyek jembatan yang siap, lalu balok di dekat crane. Tes lama dipertahankan; hasil akhir 92 lulus.
- Pratinjau JVM menjalankan renderer/HUD asli untuk `block-lift` dan `block-tower`, termasuk kondisi balok sungguhan dari `GameState`. `gradle run` berhasil; dua replay WebGL berhasil dengan GL error 0, lalu kedua gambar diperiksa. Ini bukan pengujian HP.

**Batas pengujian:** `adb devices` tidak menampilkan perangkat. APK instrumentasi berhasil dikompilasi, belum dijalankan. Kontrol sentuh Android, audio dari speaker, lifecycle Android nyata, dan kelancaran penggalian di HP pengguna belum diuji. Pengujian pause di atas menguji model dan save, bukan lifecycle perangkat. Tes sinyal audio tidak membuktikan kenyaringan speaker HP.

Bukti: `docs/verification/alpha15/` berisi log build, XML JUnit, lint, metadata/hash APK, log pratinjau, dan gambar.

---

# Verifikasi alpha.14 — penggabungan fitur tabrakan dari `main`

Tanggal: 17 Oktober 2026. versionCode 14; versi `0.1.0-alpha.14`.

- `main` mendapat commit `9b9df77` (tabrakan antar kendaraan) yang dibuat ChatGPT di atas alpha.7. Commit itu digabung ke branch PR #1 dengan merge commit, tanpa menulis ulang riwayat.
- `VehicleNavigation` diperluas:
  - 4 kendaraan; crane mendapat radius 2,2 m;
  - batas peta mengikuti jembatan;
  - titik dan lintasan tidak boleh masuk sungai kecuali di lantai jembatan yang sudah jadi.
- `GameState` memakai rute sungai/jembatan yang sudah ada, ditambah jalur memutar per leg:
  - tunggu hingga 3 detik saat terhalang mesin yang masih bergerak;
  - jembatan satu lajur dengan titik antre 6 m;
  - tempat muat truk dipilih dari tepi sungai yang sama.
- `Guide`: truk baru menuang ke fondasi/gudang bila sel tuangnya benar-benar di sasaran. Sebelum perbaikan ini, `GuideTest` macet karena truk parkir miring (heading 319°) dan menuang di luar fondasi.
- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` **berhasil**: 85 tes, 0 gagal; lint 0 error, 1 warning `DiscouragedApi` (landscape, disengaja). APK debug 67.671.950 byte.
- Tes:
  - 9 tes asli ChatGPT (`VehicleCollisionTest`) lulus tanpa diubah.
  - `RiverCollisionTest` (baru, 3 tes) memeriksa jarak aman 4 kendaraan dan larangan masuk air di setiap frame:
    - truk memutari crane yang parkir di tepi sungai;
    - truk dan bulldozer menyeberang jembatan dari arah berlawanan;
    - sentuhan acak di kedua tepi dengan langkah waktu besar.
  - Empat tes lama ditata ulang karena kendaraan kini tidak boleh bertumpuk. Pengecekannya tidak dilemahkan:
    - truk dipindah dulu sebelum bulldozer meratakan (sama seperti perubahan ChatGPT di `main`);
    - crane dipindah dari pojok peta yang dituju truk;
    - bulldozer ditaruh di lokasi jembatan tanpa menumpuk truk.
  - Smoke test Android diberi cek tumpang-tindih dari versi ChatGPT, tetapi masih hanya dikompilasi.
- Folder bukti ChatGPT `docs/verification/alpha8/` dipindah ke `docs/verification/collision-chatgpt/` karena nama alpha.8 di repo ini dipakai versi lain. Isinya tidak diubah, kecuali catatan di README-nya.
- Pratinjau `bridge-open`, `crane-grab`, `construction-yard`, dan `house-materials` setelah penggabungan: GL error 0 (`docs/verification/alpha14/`).

**Batas:** belum dijalankan di HP. Perilaku menghindar dan antre di jembatan hanya diuji lewat tes JVM.

---

# Verifikasi alpha.13 — crane yang dikendalikan anak

Tanggal: 17 Oktober 2026. versionCode 13; versi `0.1.0-alpha.13`.

- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` **berhasil**: 73 tes, 0 gagal; lint 0 error, 1 warning `DiscouragedApi` (landscape, disengaja). APK debug 67.659.950 byte.
- `CraneTest` (baru, 5 tes):
  - sentuhan dalam jangkauan membuat grab mengambil 2 unit, dan sentuhan pada truk menuangnya ke bak;
  - sentuhan jauh membuat crane berjalan mendekat lalu menyendok dari sumber;
  - muatan masuk ke gudang jembatan dan tidak pernah jatuh ke sungai;
  - tombol aksi membawa crane ke tepi sungai dan memasang ketiga tahap jembatan;
  - save v9 menyimpan keadaan crane, dan save lama mendapat crane bawaan.
  - Total material tetap sama di setiap tes.
- `GuideTest`: simulasi anak yang hanya mengikuti tangan penunjuk kini menekan crane sendiri untuk tiap tahap jembatan, tanpa pembangunan otomatis.
- Pratinjau `crane-grab` (`docs/verification/alpha13/`): GL error 0. Terlihat crane dengan boom terarah ke tumpukan, grab terangkat berisi pasir, kartu crane terpilih, dan ikon aksi "tuang". Ikon crane diberi garis tepi gelap karena sebelumnya hilang di kartu kuning yang terpilih.

**Batas:** belum dijalankan di HP. Sentuhan crane hanya diuji lewat tes logika dan pratinjau.

---

# Verifikasi alpha.12 — sungai dan proyek jembatan

Tanggal: 17 Oktober 2026. versionCode 12; versi `0.1.0-alpha.12`.

- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` **berhasil**: 68 tes, 0 gagal; lint 0 error, 1 warning `DiscouragedApi` (landscape, disengaja). APK debug 67.651.410 byte.
- `BridgeTest` (baru):
  - sungai menghalangi tepi timur sebelum jembatan jadi;
  - 8 unit di gudang material membuat crane membangun 3 tahap;
  - truk menyeberang pulang-pergi lewat lantai jembatan, dan di setiap frame tidak ada kendaraan di air selain di atas jembatan;
  - lokasi dikosongkan sebelum tiap tahap;
  - air tidak pernah menjadi tujuan;
  - excavator mencapai tambang timur lalu menyendok;
  - save v8 menyimpan jembatan dan posisi di timur;
  - save alpha.7 (62 kolom) dimigrasikan tanpa kehilangan material;
  - save yang menaruh kendaraan di timur tanpa jembatan ditolak.
- `GuideTest`: simulasi anak yang hanya mengikuti tangan penunjuk kini berlanjut dari rumah sampai jembatan terbuka, dengan total material tetap di setiap langkah.
- Tes lama yang menganggap pasir dunia 36 unit diperbarui ke `GameState.START_SAND` (36 + 24 tambang) dan `westSand()`. Isi pemeriksaannya sama.
- Pratinjau `bridge-crane` dan `bridge-open` (`docs/verification/alpha12/`): GL error 0. Crane terlihat membawa balok baja di atas sungai, dan truk menyeberang jembatan jadi.

**Batas:** belum dijalankan di HP. Animasi air dan crane hanya dilihat lewat pratinjau.

---

# Verifikasi alpha.11 — material foto CC0

Tanggal: 17 Oktober 2026. versionCode 11; versi `0.1.0-alpha.11`.

- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` **berhasil**: 63 tes, 0 gagal; lint 0 error, 1 warning `DiscouragedApi` (landscape, disengaja).
- APK debug **67.641.738 byte**. Isinya 27 file tekstur JPG asli Poly Haven (9 material × albedo/normal/roughness) yang disimpan tanpa kompresi ulang ("Stored", karena JPG sudah terkompresi), ditambah `index.json`. Total aset tekstur 66.694.041 byte.
- `tools/textures/fetch_textures.py` memeriksa checksum MD5 dari API Poly Haven untuk setiap file. Lisensi semua aset: CC0 1.0.
- Pratinjau (`tools/scene-preview`, gambar di `docs/verification/alpha11/`): renderer memuat 9 material dengan GL error 0. Tekstur tampak pada tanah lokasi, rumput, atap, dinding, pagar, aspal, dan kendaraan.

**Batas:** belum dijalankan di HP. Waktu memuat 27 tekstur saat aplikasi dibuka belum diukur. Bila GPU tidak mendukung atau tekstur gagal dimuat, game otomatis memakai permukaan prosedural sebelumnya.

---

# Verifikasi alpha.10 — kendaraan yang hidup

Tanggal: 17 Oktober 2026. versionCode 10; versi `0.1.0-alpha.10`.

- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` **berhasil**: 63 tes, 0 gagal; lint 0 error, 1 warning `DiscouragedApi` (landscape, disengaja). APK debug 943.872 byte.
- `VehicleLifeTest` (baru):
  - kecepatan mengikuti jarak tempuh;
  - setelah berhenti ada pantulan teredam (bergoyang minimal 2 kali, 1–7 cm, lalu diam);
  - frame pertama dan input tidak valid tidak menimbulkan lonjakan;
  - suara mesin hening sampai ada kejadian, makin keras dengan tenaga, dan mati saat mesin dimatikan;
  - klakson dan desir pasir terdengar lalu berhenti sendiri;
  - hidrolik terdengar saat lengan bergerak;
  - sampel suara selalu terbatas, dan tes `SoundscapeTest` lama tetap lulus.
- Pratinjau `vehicle-life` (`tools/scene-preview`): truk melaju dengan asap knalpot, sopir excavator terlihat melambai di dalam kabin berkaca, dan sopir truk memegang setir. GL error 0.

**Batas:** suara belum didengarkan di HP. Keras-lemah dan karakter suara mesin perlu disetel setelah didengar di perangkat. Bekas roda disimpan hanya di renderer (60 jejak per kendaraan) dan hilang saat aplikasi ditutup.

---

# Verifikasi alpha.9 — kontrol ramah balita

Tanggal: 17 Oktober 2026. versionCode 9; versi `0.1.0-alpha.9`.

## Hasil yang benar-benar dijalankan

- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` **berhasil**: 58 tes, 0 gagal; lint 0 error, 1 warning `DiscouragedApi` (landscape, disengaja). APK debug 934.088 byte.
- `GuideTest` (baru): urutan petunjuk untuk alur pasir; **simulasi anak yang hanya mengikuti tangan penunjuk** (pilih kartu yang ditunjuk, tekan aksi, sentuh titik tanah yang ditunjuk) dari dunia baru sampai rumah tahap 3, dengan total material tetap 5.492 di setiap langkah; aksi bulldozer mendorong menembus tumpukan terdekat, dan tetap mengangkat/menurunkan blade bila tidak ada tumpukan. Simulasi ini menemukan dan membantu memperbaiki dua putaran tanpa akhir sebelum rilis: pasir yang sama terus dimuat ulang, dan tanah galian fondasi ditimbun kembali.
- Pratinjau HUD (`tools/scene-preview`): layar anak hanya menampilkan 3 kartu, tombol aksi bergambar, kartu progres bergambar, dan gembok; tangan penunjuk tampil penuh di atas tombol aksi. GL error 0.
- Runner instrumentasi Android disesuaikan (kontrol lanjutan diaktifkan, bantuan otomatis dimatikan selama skrip, rumah dirakit otomatis) dan berhasil dikompilasi, **tetapi belum dijalankan** di HP/emulator.

## Batas pengujian

Belum dicoba bersama anak dan belum dijalankan di perangkat. Batas waktu 5 detik (tangan) dan 14 detik (bantuan) adalah nilai awal yang perlu disesuaikan setelah uji nyata.

---

# Verifikasi alpha.8 — tanah inkremental & audio sintesis

Tanggal: 17 Oktober 2026. Package `com.dromadi.tinysim`; versionCode 8; versi `0.1.0-alpha.8`.

## Hasil yang benar-benar dijalankan

- `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` **berhasil**: 55 tes, 0 gagal/error/skipped; lint 0 error dan satu warning `DiscouragedApi` (landscape yang disengaja, sama seperti sebelumnya).
- **Mesh tanah.** Diukur di JVM desktop dengan kode asli: alpha.7 membangun ulang seluruh mesh 43.648 segitiga setiap kali tanah/pasir berubah, 46–107 ms per perubahan (satu frame 60 fps = 16,7 ms). Alpha.8 `TerrainMesh`: pembangunan penuh awal sekitar 4 ms setelah pemanasan JIT; saat bulldozer mendorong pasir, tiap perubahan 0,05–0,14 ms dan hanya 12 baris quad yang diperbarui. `TerrainMeshTest` membuktikan hasil update parsial **identik** dengan pembangunan ulang penuh setelah 44 perubahan acak termasuk sudut dan tepi peta, serta normal/winding menghadap ke atas.
- **Pratinjau renderer** (`tools/scene-preview`): adegan awal, lubang galian, timbunan dan rumah lengkap lulus tanpa GL error. Adegan berikutnya memakai jalur `glBufferSubData`; hasil gambar sama dengan bukti alpha.7.
- **Audio.** `SoundscapeTest`: tiga area terdengar (RMS 0,001–0,05), tidak pernah melewati batas ±0,35, deterministik, berbeda antar area, kicau burung muncul di jendela waktunya, fade-in mencapai volume 0,22, crossfade antar area tanpa lonjakan sampel besar, dan fade-out sampai hening.
- APK debug lokal **987.732 byte** (alpha.7: 173.725.502 byte). Tidak ada aset WAV; CI tidak lagi mewajibkan APK >100 MiB.

## Batas pengujian

Alpha.8 belum dijalankan pada HP/emulator. Suara AudioTrack belum didengarkan di perangkat; tes membuktikan sinyal yang dihasilkan, bukan pengalaman mendengar. FPS di HP belum diukur; angka mesh di atas berasal dari JVM desktop.

---

# Verifikasi alpha.7 — terrain & proyek rumah

Tanggal: 17 Oktober 2026. Package `com.dromadi.tinysim`; versionCode 7; versi `0.1.0-alpha.7`.

## Hasil yang benar-benar dijalankan

- Build bersih `clean testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` **berhasil**: 48 tes, 0 gagal/error/skipped; lint 0 error dan satu warning `DiscouragedApi` untuk landscape yang disengaja.
- Tes terrain/rumah mencakup perubahan tinggi dan normal mesh setelah menggali, penggalian → pemuatan → pembongkaran → pengisian kembali lubang oleh dozer, empat titik fondasi → pengiriman delapan unit → tiga tahap rumah, perpindahan kendaraan keluar tapak, ray picking pada heightfield, save/migrasi/corrupt state, lapisan dasar, serta timbunan besar.
- Semua material dihitung: 36 unit sumber awal + 1.364 × 4 unit tanah dasar = **5.492 unit**. Unit yang masuk jalan atau rumah tetap masuk total. Timbunan di atas batas tinggi visual tetap dapat disimpan dan diambil kembali.
- APK lokal **173.725.502 byte = 173,7 MB / 165,68 MiB**. Signature v2 valid, package/versi benar, 0 Android permission. Key lokal sama dengan APK lokal alpha.4/alpha.5/alpha.6.
- Verifikasi ZIP/DEX: CRC seluruh entry lulus, tidak ada kelas duplikat, tidak ada berkas audio tambahan/temporer.
- Audio: tiga WAV orisinal, masing-masing 300 detik, stereo PCM16/48 kHz; total 172.800.132 byte. Header, variasi data, fade akhir loop, hash, batas peak/RMS, streaming ZIP_STORED dan ukuran APK >100 MiB lulus. Verifier yang sama dijalankan oleh GitHub Actions. Binary audio dihasilkan dari kode Java saat build, tanpa unduhan sampel.

- **12 pratinjau renderer/HUD lulus**: enam adegan (awal, jalan, terrain/fondasi, bahan rumah, dinding, rumah lengkap), masing-masing shader normal berbayangan dan fallback mediump; GL error 0. Gambar akhir ditinjau secara visual. Kode game/mesh/renderer/HUD asli dieksekusi pada JVM lalu WebGL, bukan Android.

## Batas pengujian

Alpha.7 belum dijalankan pada HP/emulator Android. Runner MotionEvent asli sudah dikompilasi dan diperluas untuk fondasi, pemuatan, rumah tiga tahap serta pause/resume, tetapi belum dieksekusi. Playback, crossfade, audio focus dan lifecycle Android belum diverifikasi di perangkat; pengecekan file audio tidak membuktikan pengalaman mendengar di HP.

Terrain menggunakan sel dengan mesh grid 0,25 unit, batas lapisan dasar 0,88 unit dan batas tinggi timbunan visual 3,12 unit. Posisi Y kendaraan mengikuti tinggi pusat kendaraan; belum ada simulasi kemiringan sasis atau tabrakan bangunan/dekorasi. Kendaraan yang berada di tapak dipindahkan sebelum perakitan rumah. Save lama mempertahankan material; bekas penggalian alpha.6 yang dahulu hanya gambar tidak diberi pengurangan tanah secara retroaktif.

Bukti tersedia di [`docs/verification/alpha7/`](docs/verification/alpha7/).

---

# Verifikasi alpha.6 — excavator bergerak & proyek jalan

Tanggal: 17 Oktober 2026. Package `com.dromadi.tinysim`; versionCode 6; versi `0.1.0-alpha.6`.

## Yang benar-benar dijalankan

- `testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: berhasil. **41 tes, 0 gagal/error/skipped**; lint **0 error, 1 warning** `DiscouragedApi` untuk landscape yang disengaja.
- Tes baru mencakup excavator mendatangi area jauh, mengambil kembali pasir yang dituang, memuat truk pada lokasi baru; bucket pada lima arah; koordinat bidang lengan berputar; pembatalan rute dan snapshot; migrasi save alpha.5 dan penolakan save rusak.
- Proyek jalan diuji dengan blade terangkat/turun dan tanpa pasir; juga melalui alur sumber → bucket → truk 4 unit → perjalanan → dump → lintasan bulldozer → empat bagian jalan selesai. Inventori tetap **36 unit**, termasuk pasir yang menjadi jalan.
- Bantuan pemuatan diuji pada 16 kombinasi posisi batas map dan arah excavator. Pengenal sentuhan diuji untuk seret badan excavator versus bucket, pan/pinch, pembatalan, tombol, minimap, dan tahan area orang tua.
- APK debug diverifikasi dengan `apksigner`: signature v2 valid, package/versi sesuai, **0 Android permission**. Key lokal sama dengan APK lokal alpha.4/alpha.5; APK CI memakai key lingkungan CI.

- **30 pratinjau renderer/HUD lulus**: 15 adegan shader normal dengan bayangan serta 15 adegan fallback mediump, semuanya GL error 0. Kode renderer dan HUD asli dijalankan pada JVM lalu direplay di WebGL; empat JPG akhir sudah ditinjau secara visual. Ini bukan uji perangkat Android.

## Batas hasil

Belum ada pengujian alpha.6 pada HP/emulator Android. Runner Android yang mengirim MotionEvent asli **berhasil dikompilasi, belum dijalankan**; sekarang mencakup penggalian ulang dengan excavator bergerak serta pause/resume. Tes JVM tidak menggantikan uji lifecycle Android pada perangkat.

Pasir memakai sel deterministik. Bekas pengambilan berupa guratan permukaan, bukan deformasi mesh tanah. Empat bagian jalan yang terbangun tetap terpasang sampai reset; kendaraan/dekorasi belum memakai tabrakan rigid-body.

Bukti build, JUnit XML, lint, identitas/hash/signature APK, dan hasil pratinjau disimpan di [`docs/verification/alpha6/`](docs/verification/alpha6/).

---

# Verifikasi TinySim 3D — 0.1.0-alpha.5

Tanggal: 17 Oktober 2026. Target pengembangan tetap HP pengguna berspesifikasi tinggi sesuai `AGENTS.md`. Perubahan: lahan 62 × 22 unit dunia dengan tiga zona, perjalanan truk/bulldozer di bidang X/Z, pasir dua dimensi, kontrol sentuh langsung, pan/minimap/follow camera, dan migrasi save.

## Hasil aktual

- `gradle --no-daemon --max-workers=2 testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest`: **BUILD SUCCESSFUL**, 40 detik pada build akhir.
- **31 tes unit lulus**, tanpa gagal/error/skipped: 9 GameState, 10 TouchController, 7 WorldCamera, 3 Geometry, 2 ArmRig.
- GameState: rute menuju zona kedua, tiba tanpa overshoot, batas lahan, Stop, penurunan bak sebelum berjalan, pembuangan sesuai arah kendaraan pada empat arah, perataan pasir, bantuan pemanggilan truk, migrasi save dan konservasi 36 unit.
- TouchController: state machine yang sama dipakai adapter Android. Diuji ketuk tanah, seret kendaraan, pan ruang kosong, drag bucket hingga titik akhir, isolasi pinch dari bucket/perjalanan, pembatalan jari ketiga, cancel rute, minimap, gerbang orang tua 3 detik, dan kehilangan pointer/keluar tombol.
- WorldCamera: ground picking di berbagai pan/zoom pada map luas, ditambah tes lengan, proyeksi, snapshot thread GL dan save sebelumnya.
- Lint: **0 error, 1 warning** landscape `DiscouragedApi` yang disengaja.
- APK `com.dromadi.tinysim`, versi `0.1.0-alpha.5`, versionCode 5, min SDK 26, target SDK 35. `apksigner verify --verbose` berhasil; `aapt dump permissions` tidak mencantumkan permission.
- SHA-256 APK lokal: `4f46f02dc8fff7352be44b0bd3d4a0cb9dc28136a8243a84fa7ce8a70131f991`.
- Sertifikat debug lokal alpha.5 sama dengan APK lokal alpha.4: SHA-256 `19b7382005d729a9b2e7887a8801ea95290673c4959e4716f92eb48a0fcb3049`. Signature/package/version mendukung pembaruan tanpa pergantian key; pemasangan native alpha.5 belum dijalankan dalam pemeriksaan ini.
- Capture JVM memakai **renderer dan HUD asli**. Sebelas fixture, masing-masing dengan inventaris 36, mencakup tiga preset, orbit dekat/jauh, overview map, halaman konstruksi, fondasi pembangunan, muatan, bak terangkat dan bulldozer.
- Replay Chromium headless/WebGL + Canvas: **11/11 PASS** dengan shadow map 4096 px, **11/11 PASS** pada fallback tanpa highp, GL error 0, tanpa error kompilasi/link shader. Default scene mempunyai 1508 draw call, atau 1041 pada fallback.
- HUD asli direkam melalui stub Canvas lalu diputar ulang dengan Canvas browser. Ini memeriksa susunan minimap/kontrol; metrik font browser bisa berbeda dari Android. Gambar di folder ini adalah pratinjau desktop, bukan screenshot HP.

Bukti mentah ada di `docs/verification/alpha5/`: log build, XML tes, lint, metadata/signature/hash APK, capture/replay, hasil setiap skenario, dan tiga gambar map/HUD. Status CI harus diperiksa pada run commit yang dipublikasikan.

## Batas pemeriksaan

APK instrumentasi sentuh berhasil dikompilasi dan diperbarui untuk minimap, tujuan antarzona, serta bulldozer di zona kedua, tetapi **belum dijalankan pada Android untuk alpha.5**. Uji unit memeriksa logika gestur/perjalanan dan pratinjau memeriksa rendering; keduanya tidak diklaim sebagai pengujian native sentuhan atau lifecycle HP. Integrasi pause menghentikan rute, menyimpan posisi/pengaturan, serta menghentikan audio dan GL; resume mengatur ulang jam frame.

Truk/bulldozer memakai rute garis lurus di permukaan datar; bangunan ditempatkan di luar jalur utama. Dekorasi tidak memakai rigid-body collision. Excavator tetap di stasiun pemuatan. Pasir menggunakan grid deterministik 62 × 22 sel; partikel adalah respons visual, sedangkan unit pasir yang dipindahkan tetap dihitung oleh simulasi.

---

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
