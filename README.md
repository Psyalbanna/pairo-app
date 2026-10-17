# P.A.I.R.O Voice — aplikasi Android

Pembungkus tipis untuk halaman suara P.A.I.R.O yang sudah berjalan di
`https://stb.taild257cc.ts.net/`. Tampilan dan otaknya tetap di server;
aplikasi ini menambahkan tiga hal yang tidak bisa diberikan halaman web:

- **pengenalan suara native** (WebView Android tidak punya Web Speech API)
- **notifikasi persisten** dengan tombol *Berhenti*
- kehadiran sebagai aplikasi sungguhan di laci aplikasi

## Cara membuat APK (lewat GitHub Actions, tanpa memasang apa pun)

1. Buat repositori baru di GitHub — privat juga boleh, GitHub Student memberi menit Actions.
2. Unggah seluruh isi folder ini (termasuk folder tersembunyi `.github`).
   Cara termudah: dari halaman repo → *Add file* → *Upload files* → seret semua berkas.
   **Penting:** folder `.github/workflows/build.yml` harus ikut terunggah, karena itulah resep build-nya.
3. Buka tab *Actions* → pilih *build-apk* → *Run workflow*.
4. Tunggu sekitar 3–5 menit. Hasilnya ada di bagian *Artifacts*: **pairo-voice-apk**.
5. Unduh, buka di HP, izinkan "instal dari sumber tidak dikenal", pasang.

## Saat pertama dibuka

Aplikasi meminta kunci akses sekali: `pairo-sir-2026`
(kunci disimpan di perangkat, tidak perlu diulang).

## Yang perlu diketahui

- Mikrofon memakai pengenal suara Android, jadi butuh aplikasi Google di HP —
  sama seperti Web Speech di Chrome.
- Notifikasi berjalan selama aplikasi mendengarkan; menutup aplikasi menghentikan mikrofon.
  Mikrofon yang tetap hidup di latar belakang adalah tahap berikutnya
  (butuh layanan latar depan penuh dan izin tambahan).
- Halaman tetap bisa dibuka lewat browser seperti sebelumnya; APK ini tidak menggantikan server.
- Jika ingin mengganti alamat server, ubah `HOST` di
  `app/src/main/java/ai/pairo/voice/MainActivity.kt`.
