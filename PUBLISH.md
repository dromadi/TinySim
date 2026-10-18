# Publikasi Alpha.19

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
