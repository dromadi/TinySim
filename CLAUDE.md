# Aturan proyek TinySim

## Target perangkat dan prioritas grafik
- Perangkat target pemilik adalah HP Android **spesifikasi tinggi**. Utamakan kualitas grafik dan realisme.
- Jangan menurunkan kualitas grafik, mengurangi detail, atau menambah mode "grafik rendah" demi HP murah kecuali pemilik memintanya.
- Jangan mengangkat lag, FPS, draw call, panas, atau baterai sebagai masalah, peringatan, atau alasan menahan fitur. Fallback keamanan yang sudah ada (misalnya MSAA/bayangan nonaktif bila GPU tidak mendukung) boleh tetap dipertahankan agar aplikasi tidak crash.

## Kebiasaan kerja
- Komunikasi dengan pemilik dalam Bahasa Indonesia.
- Sebelum push, jalankan `./gradlew testDebugUnitTest lintDebug assembleDebug` (JDK 17, Android SDK 35).
- Untuk memeriksa tampilan 3D tanpa HP/emulator, gunakan `tools/scene-preview` (lihat README di folder itu).
