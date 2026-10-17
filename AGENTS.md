# Aturan pengembangan TinySim

## Target perangkat dan prioritas grafis

- Perangkat utama pengguna adalah HP berspesifikasi tinggi. Jadikan perangkat tersebut sebagai target utama pengembangan, bukan HP kelas rendah.
- Prioritaskan kualitas grafis, detail model, pencahayaan, bayangan, efek, animasi, serta interaksi yang kaya. Jangan membatasi peningkatan hanya karena asumsi batas RAM, ukuran APK, atau kemampuan HP murah.
- Jangan menurunkan kualitas grafis secara otomatis atau menunda fitur visual karena kekhawatiran performa yang belum terbukti pada perangkat target.
- Jangan mengulang peringatan spekulatif tentang lag, panas, baterai, atau penggunaan memori. Bahas masalah performa bila ada bukti pengujian pada perangkat target atau pengguna meminta pembahasannya.
- Kelambatan emulator tanpa KVM maupun renderer desktop tidak mewakili performa HP pengguna dan tidak boleh dijadikan alasan menghentikan upgrade grafis. Gunakan build, tes logika, serta pratinjau renderer/shader untuk melanjutkan verifikasi saat pengujian HP belum tersedia.
- Tetap perbaiki bug atau crash yang terbukti dan laporkan hasil pengujian secara faktual; jangan mengklaim uji perangkat yang belum dilakukan.

## Kontrol utama

Prioritaskan sentuhan langsung: ketuk tujuan, seret kendaraan/bucket, pan/pinch pada map, dan minimap. Jangan menjadikan joystick analog atau tombol arah sebagai kontrol utama.

## Persyaratan produk

Pertahankan konsep dan batasan produk dalam README: permainan ramah anak usia 2–6 tahun, kontrol sederhana, offline, tanpa iklan/login/pembelian/analytics, tanpa izin yang tidak diperlukan, serta area orang tua dengan gestur tahan. Hormati lifecycle dan tombol kembali Android.

## Paket konten dan arah jangka panjang

- TinySim dibangun pelan-pelan dalam jalan panjang menuju game **full fitur**. Ukuran sekitar **100 MB ke atas** adalah gambaran hasil akhir ketika fiturnya lengkap, bukan angka yang dikejar di setiap versi.
- Jangan pernah menambah ukuran demi angka: tidak ada berkas tanpa kompresi, padding, duplikasi, atau konten yang hampir tidak terdengar/terlihat, dan tidak ada pengecekan CI untuk ukuran minimum APK.
- Setiap perubahan dinilai dari mutu fitur dan isi game (gameplay, grafis, model, tekstur, animasi, area, suara), dikerjakan dengan rapi dan teruji. Ukuran mengikuti dengan sendirinya.
- Aset disimpan dalam format terkompresi yang wajar (mis. PNG/KTX/ETC2 untuk tekstur, OGG/Opus untuk audio). Soundscape latar disintesis langsung saat bermain (`Soundscape.kt`). Laporkan ukuran APK dari hasil build nyata.

## Kebiasaan kerja

- Komunikasi dengan pemilik dalam Bahasa Indonesia.
- Sebelum push, jalankan `./gradlew testDebugUnitTest lintDebug assembleDebug assembleDebugAndroidTest` (JDK 17, Android SDK 35).
- Untuk memeriksa tampilan 3D tanpa HP/emulator, gunakan `tools/scene-preview` (lihat README di folder itu).
- Pekerjaan berat di thread GL (misalnya membangun ulang mesh) harus bersifat inkremental; ukur waktunya dengan tes JVM bila ragu.
