# Publikasi Alpha.24

`0.1.0-alpha.24` / versionCode 24, save v18. Excavator berlumpur sesudah menggali, perjalanan ke petak cuci dan semprotan satu jari yang membersihkan bagian terkena air. Artifact: `TinySim-0.1.0-alpha.24-debug`. [Build Android](https://github.com/dromadi/TinySim/actions/runs/39261169242) sukses: 168 tes, lint, APK debug dan APK instrumentation. [Unduh ZIP APK alpha.24](https://github.com/dromadi/TinySim/actions/runs/39261169242/artifacts/12190497237). Ukuran APK 67.779.682 byte. Ketiga pratinjau renderer lulus dan gambarnya tersedia di docs/verification/alpha24. Belum diuji di HP/emulator. APK CI dapat berbeda penanda tangan dari APK lokal pengguna; tidak dibuat key lokal pengganti.

# Publikasi Alpha.23

`0.1.0-alpha.23` / versionCode 23, save v17. Penduduk setelah pemulihan rumah, taman satu sentuhan dan shelter saat tornado. Artifact Actions: `TinySim-0.1.0-alpha.23-debug`. [Build Android](https://github.com/dromadi/TinySim/actions/runs/39257796925) sukses: 161 tes lulus, lint, APK debug dan APK instrumentation. [Unduh ZIP APK alpha.23](https://github.com/dromadi/TinySim/actions/runs/39257796925/artifacts/12188296869). Ketiga pratinjau renderer lulus; gambar tersedia di docs/verification/alpha23. Belum diuji di HP/emulator; instrumentation hanya dibangun. APK CI dapat berbeda penanda tangan dari APK lokal pengguna. Keystore privat lama belum tersedia di workspace, sehingga tidak dibuat APK lokal dengan key pengganti.

# Publikasi Alpha.22

`0.1.0-alpha.22` / versionCode 22. Tombol pemulihan kota satu langkah, pemilihan kendaraan otomatis, lima ikon tahap dan pergantian tornado ke kendaraan. Format save tetap v16.

Artifact Actions: `TinySim-0.1.0-alpha.22-debug`. [Build CI](https://github.com/dromadi/TinySim/actions/runs/39252253869) lulus: 156 tes, lint, APK debug dan APK instrumentation. [Unduh ZIP APK alpha.22](https://github.com/dromadi/TinySim/actions/runs/39252253869/artifacts/12184904975). Tiga pratinjau renderer lulus dan gambarnya ada di docs/verification/alpha22. Belum diuji di HP/emulator. Kunci debug privat versi sebelumnya tidak tersedia di workspace ini; tidak dibuat APK lokal dengan key pengganti. APK CI dapat memiliki penanda tangan berbeda dari APK lokal sebelumnya.

# Publikasi Alpha.21

`0.1.0-alpha.21` / versionCode 21. Kawasan pulih-bangun di perluasan peta utama 86 × 48 m: empat bangunan latihan/16 bagian diterjang tornado, didorong bulldozer ke tempat muat, diangkut truk ke gudang, lalu dibangun kembali dengan molen dan crane. Siklus bisa diulang tanpa reset atau isi ulang material. Save v16 memigrasikan v15 serta format lama. Kontrol sentuhan/ikon, offline, tanpa izin/iklan/login/IAP/analytics tetap dipertahankan.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Artifact Actions: `TinySim-0.1.0-alpha.21-debug`. APK lokal 67.868.716 byte; SHA-256 `2e45b92a244fafef0aa318a2d4b53e98764af0ccff1f52ae3333bfdc8c5e392f`. Kunci debug privat lokal tetap sama dengan versi sebelumnya, tidak diunggah ke Git. APK CI dapat memakai key berbeda. Hasil 154 tes, lint, pratinjau dan batas pengujian ada di [VERIFICATION.md](VERIFICATION.md). Belum diuji di HP/emulator; instrumentation hanya dibangun. Tidak ada target ukuran minimum APK atau aset pengisi.

## Publikasi Alpha.20

`0.1.0-alpha.20` / versionCode 20. Arena tornado: empat tahap pertumbuhan, tiga jenis dengan perilaku berbeda, tiga peta/144 properti, penyerapan dan pelepasan benda, bangunan latihan yang terurai, serta solo/duo pendamping AI offline. Ronde bisa diulang tanpa reset dunia. Save v15 memigrasikan v14 dan format lama; tidak ada jaringan, iklan atau pembelian.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Artifact Actions: `TinySim-0.1.0-alpha.20-debug`. Pertahankan kunci debug lokal privat yang sama dengan versi sebelumnya; jangan unggah ke Git. APK CI dapat memakai key berbeda. Hasil build serta batas pengujian ada di VERIFICATION.

## Publikasi Alpha.19

`0.1.0-alpha.19` / versionCode 19: Taman Angin, puting beliung yang disentuh/diseret, pasir serta balok yang berpindah, lingkungan dan penjaga yang bereaksi. Save v14 memigrasikan v13 dan versi lama, tanpa menghidupkan cuaca kembali saat restore. Lihat README serta VERIFICATION untuk perilaku dan bukti.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Artifact Actions: `TinySim-0.1.0-alpha.19-debug`. Kunci debug lokal privat tetap sama dengan APK lokal Alpha.8–Alpha.18; jangan unggah ke Git. APK CI dapat memakai key berbeda. Tidak ada target ukuran minimum atau aset pengisi.

## Publikasi Alpha.18

`0.1.0-alpha.18` / versionCode 18. Dua truk baru (molen dan bola penghancur), peta 86 × 32 m, penjaga dan palang jembatan. Proyek baru ada di selatan dan langsung bisa dicapai. Lihat README dan VERIFICATION untuk kontrol, migrasi dan bukti.

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

Artifact Actions: `TinySim-0.1.0-alpha.18-debug`. Keystore lokal privat tetap sama dengan APK lokal Alpha.8–Alpha.17; jangan unggah ke Git. APK CI dapat memakai key berbeda. Reset permainan hanya di area orang tua.

## Publikasi Alpha.17

Versi `0.1.0-alpha.17` / code 17. Proyek pipa: gali enam bagian, angkat/pasang tiga pipa dengan crane, timbun dengan bulldozer. Lihat README dan VERIFICATION untuk kontrol dan bukti.

Build dengan JDK 17, SDK 35:

```sh
./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest
```

GitHub Actions mengunggah `TinySim-0.1.0-alpha.17-debug`. Keystore lokal privat tetap sama dengan APK lokal Alpha.8–Alpha.16; jangan unggah ke Git. Keystore CI dapat berbeda. Pembaruan APK harus menggunakan penanda tangan yang sama agar save tetap ada.

## Publikasi TinySim

Repository: https://github.com/dromadi/TinySim (public, dibuat manual oleh pemilik akun).

## APK melalui GitHub Actions

1. Buka tab Actions dan workflow **Android debug**.
2. Pilih run untuk commit yang ingin diuji dan pastikan semua langkah berhasil.
3. Unduh artifact **TinySim-0.1.0-alpha.15-debug**, lalu ekstrak APK.

Workflow menjalankan `testDebugUnitTest`, `lintDebug`, `assembleDebug`, dan `assembleDebugAndroidTest`. Artifact disimpan 30 hari. Laporan tes dan lint tersedia sebagai artifact **verification-reports**. APK debug CI dapat memakai debug signing key yang berbeda dari APK lokal, sehingga pembaruan lintas build mungkin memerlukan uninstall.

## Pengembangan berikutnya

```sh
git clone https://github.com/dromadi/TinySim.git
cd TinySim
./gradlew testDebugUnitTest lintDebug assembleDebug
```

JDK 17 dan Android SDK 35 diperlukan. Jangan force-push atau menimpa pekerjaan lain yang masuk setelah implementasi awal.
