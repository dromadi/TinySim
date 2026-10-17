# Publikasi TinySim

Repository: https://github.com/dromadi/TinySim (public, dibuat manual oleh pemilik akun).

## APK melalui GitHub Actions

1. Buka tab Actions dan workflow **Android debug**.
2. Pilih run untuk commit yang ingin diuji dan pastikan semua langkah berhasil.
3. Unduh artifact **TinySim-0.1.0-alpha.12-debug**, lalu ekstrak APK.

Workflow menjalankan `testDebugUnitTest`, `lintDebug`, `assembleDebug`, dan `assembleDebugAndroidTest`. Artifact disimpan 30 hari. Laporan tes dan lint tersedia sebagai artifact **verification-reports**. APK debug CI dapat memakai debug signing key yang berbeda dari APK lokal, sehingga pembaruan lintas build mungkin memerlukan uninstall.

## Pengembangan berikutnya

```sh
git clone https://github.com/dromadi/TinySim.git
cd TinySim
./gradlew testDebugUnitTest lintDebug assembleDebug
```

JDK 17 dan Android SDK 35 diperlukan. Jangan force-push atau menimpa pekerjaan lain yang masuk setelah implementasi awal.
