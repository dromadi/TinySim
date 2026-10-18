# Alpha.17: proyek pipa

Gambar merupakan pratinjau renderer/HUD asli di JVM + WebGL, bukan screenshot Android.

- `pipe-trench.jpg`: 24 unit tanah sudah digali menjadi parit nyata; hasilnya ada di timbunan samping.
- `pipe-lift.jpg`: crane membawa pipa pertama dari rak.
- `pipe-laid.jpg`: tiga pipa terpasang, masih terlihat sebelum ditimbun.
- `pipe-complete.jpg`: tanah dikembalikan oleh bulldozer; tutup inspeksi dan bendera toska terlihat.

`results.json` menyimpan hasil shader/GL keempat scene; `result.json` hasil tes/lint/APK. Laporan XML, log build, metadata APK dan hasil verifikasi sertifikat disimpan apa adanya. Tidak ada perangkat terhubung saat `adb devices`; instrumentation hanya dikompilasi. Model penggalian/pipa/penimbunan, migrasi save dan konservasi material diuji JVM; kontrol, audio dan lifecycle native belum diuji di HP.

Harness direset dengan upload terrain VBO lengkap per fixture karena konteks WebGL terpisah. Android tetap inkremental. Lihat VERIFICATION.md untuk koreksi pratinjau ini.
