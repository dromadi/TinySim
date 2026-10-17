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

## Paket konten

Target pengguna adalah game berukuran 100 MB ke atas. Penuhi dengan konten offline yang benar-benar dipakai (audio, material, dunia, animasi), bukan padding atau duplikasi berkas untuk mengejar angka. Ukuran APK harus dilaporkan dari hasil build nyata.
