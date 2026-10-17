# Bukti fitur tabrakan (commit `9b9df77` di `main`)

Dibuat ChatGPT di atas alpha.7 dan diberi nama "alpha.8" di sana. Saat digabung dengan PR #1, fitur ini masuk sebagai bagian alpha.14; nama alpha.8 di repo ini merujuk ke perbaikan tanah dan audio. Isi di bawah adalah catatan asli build tersebut (57 tes, peta lama 62 kolom).


`build.log` merekam build bersih. `TEST-*.xml` merekam 57 unit test; VehicleCollisionTest memeriksa pemisahan bodi dan massa di setiap langkah. Lint, identitas APK, signature publik, permission, SHA-256, serta verifikasi paket audio dicatat terpisah.

Pengujian Android MotionEvent/lifecycle baru dikompilasi, belum dijalankan pada HP/emulator. Keystore/private key tidak boleh disimpan di direktori ini atau di Git. Tidak ada screenshot perangkat yang diklaim.
