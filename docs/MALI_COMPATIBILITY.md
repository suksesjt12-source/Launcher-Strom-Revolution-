# Target kompatibilitas Mali-G57 MC2

## Sasaran perangkat

Target awal adalah MediaTek Helio G100 dengan GPU Mali-G57 MC2. Driver grafis yang benar-benar aktif pada perangkat harus dideteksi pada runtime; nama GPU atau keberadaan libvulkan.so saja tidak membuktikan semua ekstensi Vulkan tersedia.

## Status saat ini

Versi launcher 0.1.0 belum memuat backend Direct3D 9, Vulkan WSI, DXVK, WineD3D, atau renderer game. Pilihan renderer di halaman pengaturan hanya disimpan sebagai preferensi dan belum memengaruhi aplikasi game.

## Rencana validasi

1. Deteksi GPU, versi OpenGL ES, versi Vulkan, dan daftar ekstensi yang benar-benar tersedia.
2. Buat uji tampilan native Android dan uji konteks Vulkan terpisah dari game.
3. Implementasikan jalur jendela/display untuk aplikasi Windows.
4. Uji Direct3D 9 dengan program uji legal dan sederhana sebelum mencoba game penuh.
5. Uji stabilitas dan temperatur pada perangkat sasaran.

Turnip/Freedreno adalah driver untuk GPU Adreno dan bukan pilihan untuk Mali-G57. Jalur grafis yang dipilih nantinya harus cocok dengan GPU Mali dan driver sistem yang terpasang.

## Batasan

Belum ada pengujian game pada Helio G100. Tidak ada jaminan FPS, kompatibilitas, atau dukungan fitur grafis sebelum backend selesai dan diuji di perangkat fisik.
