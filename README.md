# Storm Revolution Launcher

APK Android khusus untuk mengelola salinan game PC milik pengguna NARUTO SHIPPUDEN: Ultimate Ninja STORM Revolution melalui satu file arsip ZIP.

> Status versi 0.1.0: fondasi launcher Android dan impor ZIP sudah ditambahkan. Aplikasi belum dapat menjalankan game Windows karena runtime Windows ARM64 dan backend grafis/audio belum selesai diintegrasikan. Build APK yang berhasil tidak berarti game sudah bisa dimainkan.

## Target perangkat

- Infinix Hot 50 4G
- MediaTek Helio G100
- GPU Mali-G57 MC2
- RAM 8 GB
- Android 14 / ARM64

## Yang sudah ada pada source saat ini

- Antarmuka Android Kotlin + Jetpack Compose dengan tema Storm bernuansa gelap, jingga, dan emas.
- Pemilih satu arsip ZIP melalui Android Storage Access Framework.
- Ekstraksi ke direktori privat aplikasi.
- Pemeriksaan keamanan ZIP: path traversal/absolute path ditolak, jumlah entri dibatasi, ukuran arsip dan hasil ekstraksi dibatasi, dan ruang penyimpanan diperiksa sebelum ekstraksi.
- Pencarian NSUNSR.exe secara case-insensitive.
- Validasi dasar header Windows PE (MZ dan PE\0\0).
- Penyimpanan lokasi game, hitungan file CPK, dan deteksi folder data_win32.
- Profil preferensi resolusi, FPS, mode CPU, renderer, dan audio. Nilai ini masih berupa preferensi launcher; belum diterapkan ke proses game.
- Unit test untuk path ZIP dan pemeriksaan header PE.
- GitHub Actions untuk menguji source dan membangun APK debug.

## Yang belum selesai

- Runtime Windows ARM64 mandiri yang dapat memuat dan menjalankan NSUNSR.exe.
- Translasi instruksi x86 32-bit ke ARM64 dan implementasi API Win32/WoW64 yang dibutuhkan game.
- Backend tampilan game, Direct3D 9, jalur grafis Mali, audio, dan input gamepad virtual.
- Pengujian peluncuran game di perangkat Helio G100.
- Paket Full Offline. Saat ini APK hanya berisi launcher; runtime belum dikemas.

Menu MAINkan GAME sengaja dinonaktifkan sampai runtime nyata terintegrasi. Jangan menganggap deteksi EXE sebagai bukti kompatibilitas game.

## Build APK lewat GitHub

1. Buka tab Actions di repository.
2. Pilih workflow Storm Revolution Android APK.
3. Tunggu sampai job build selesai.
4. Unduh artifact storm-revolution-launcher-debug-apk dari run yang berhasil.
5. Instal APK debug pada perangkat Android ARM64.

Build lokal membutuhkan JDK 17, Gradle 8.14.3, Android SDK Platform 36, dan Build Tools 36.0.0.

## Impor game

Pilih satu ZIP yang berisi folder instalasi game lengkap, bukan ZIP yang hanya berisi shortcut atau installer web. ZIP harus menyertakan NSUNSR.exe yang valid dan data game terkait. Arsip akan diekstrak ke direktori privat aplikasi; pastikan ruang kosong mencukupi.

## Legal dan aset

Repository tidak menyertakan executable game, file CPK, firmware proprietary, atau artwork berhak cipta. Gunakan hanya salinan game yang pengguna berhak miliki. Identitas dan merek Naruto tetap milik pemegang hak terkait; aset visual khusus proyek harus dibuat sendiri atau digunakan dengan izin yang sesuai.
