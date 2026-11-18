# Instalasi dan impor game

## Membangun APK

GitHub Actions membangun APK debug setiap ada push ke main atau master, dan dapat dijalankan manual melalui tab Actions.

1. Buka repository di GitHub.
2. Pilih Actions.
3. Jalankan workflow Storm Revolution Android APK atau buka run terbaru.
4. Setelah job berhasil, unduh artifact storm-revolution-launcher-debug-apk.
5. Instal APK pada perangkat Android ARM64.

Build menggunakan JDK 17, Gradle 8.14.3, Android SDK Platform 36, dan Build Tools 36.0.0.

## Mengimpor game dari satu ZIP

1. Siapkan arsip ZIP berisi folder instalasi game PC yang lengkap.
2. Pastikan arsip tersebut berisi NSUNSR.exe, bukan hanya installer atau shortcut.
3. Buka Storm Revolution Launcher.
4. Tekan IMPOR GAME DARI FILE ZIP lalu pilih arsip melalui pemilih dokumen Android.
5. Aplikasi memeriksa struktur ZIP, ruang penyimpanan, mencari executable, dan memvalidasi header PE.
6. Jika valid, instalasi dicatat di tab GAME.

File diekstrak ke direktori khusus aplikasi di Android/data/com.stormlauncher.nsunsr/files/StormLauncher/games/ pada penyimpanan perangkat. Jangan menghapus data aplikasi jika ingin mempertahankan game yang tersimpan di lokasi tersebut.

## Pemeriksaan ZIP

Importer menolak path absolut dan path traversal, membatasi jumlah entri dan ukuran ekstraksi, serta memerlukan ruang kosong sebelum mengekstrak. Impor hanya ditandai berhasil bila NSUNSR.exe ditemukan dan header PE dasarnya valid.

## Status runtime

Versi saat ini belum menyertakan runtime Windows ARM64. Karena itu tombol peluncuran game tetap dinonaktifkan. Menemukan executable dan membangun APK bukan bukti bahwa Windows game sudah dapat dijalankan. Runtime, translasi CPU, API Win32, Direct3D 9, audio, dan output display masih perlu dikembangkan dan diuji terpisah.
