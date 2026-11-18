# Profil konfigurasi game

## Nilai awal yang disarankan sebagai preferensi

Target pengembangan saat ini adalah Infinix Hot 50 4G dengan MediaTek Helio G100, Mali-G57 MC2, dan RAM 8 GB.

| Opsi | Preferensi awal |
| --- | --- |
| Resolusi | 960 × 540 |
| Batas FPS | 30 |
| Mode CPU | BALANCED |
| Renderer | AUTO |
| Audio | PULSEAUDIO |

Pilihan tambahan tersedia di layar PENGATURAN.

## Status implementasi

Nilai di layar pengaturan saat ini disimpan di SharedPreferences sebagai profil launcher. Nilai tersebut belum mengubah proses game karena runtime Windows dan backend grafis/audio belum tersedia. Tidak ada klaim bahwa 30 FPS atau 60 FPS sudah dapat dicapai pada perangkat sasaran.

## Tahap integrasi berikutnya

Saat runtime tersedia, profil harus diterjemahkan menjadi opsi yang benar-benar digunakan oleh proses game. Setiap opsi perlu divalidasi pada perangkat fisik; pengaturan yang tidak didukung GPU Mali atau backend runtime harus disembunyikan atau ditandai tidak tersedia.
