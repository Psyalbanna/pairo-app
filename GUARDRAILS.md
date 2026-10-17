# Pagar pengaman akses sistem (P.A.I.R.O Voice)

## Status hari ini — jujur, tanpa dibaguskan

| Bagian | Keadaan |
|---|---|
| Shell di HP (Termux/Shizuku) | **Tidak dipasang.** Keputusan sadar: HP tetap mikrofon, speaker, notifikasi |
| Tangan P.A.I.R.O di server (`tools.py`) | **Terpasang & teruji** — 4 aksi, daftar putih mati |
| Uji pagar (`test_pagar.py`) | **20 dari 20 serangan ditolak**, aksi tulis butuh "ya" |
| Catatan audit (`audit.jsonl`) | **Terpasang**, mode 0600, terisi tiap aksi |
| Batas laju | **Terpasang & teruji** (10/jam, 1 per 5 detik) |
| Daftar putih di sisi aplikasi | **Belum** — belum ada aksi perangkat baru yang perlu dijaga |
| Kedaluwarsa 7 hari | **Belum** — baru relevan kalau izin HP diberikan |
| Pertahanan prompt injection | **Sebagian** — satu aksi per giliran sudah; penandaan "data" belum |

Bukti bisa dijalankan sendiri di STB:

```
cd /root/.hermes/apps/pairo-voice
.venv/bin/python test_pagar.py      # harus berakhir "ditolak: 20 dari 20"
tail -5 audit.jsonl                 # jejak setiap aksi
grep -rn "shell=True" *.py          # harus kosong
```

Prinsip utama: **kecerdasan tidak pernah menjadi penjaga.** Model bahasa tidak
boleh memegang wewenang; semua pembatasan ditegakkan di kode, di dua tempat
(server dan aplikasi), bukan lewat kalimat di prompt.

## Lapisan 1 — Daftar kemampuan tetap (capability whitelist)

Model hanya boleh mengembalikan **nama** aksi, tidak pernah teks perintah.

```json
{"do": "shell", "nama": "baterai"}
```

Di aplikasi ada tabel mati:

| nama | perintah (argv, tanpa shell) | tingkat |
|---|---|---|
| baterai | `termux-battery-status` | A (baca) |
| memori | `cat /proc/meminfo` | A |
| ruang | `df -h /data` | A |
| unduhan | `ls -1 ~/storage/downloads` | A |
| layanan | `pgrep -l termux` | A |
| jam | `date +%H:%M` | A |
| notifikasi | `termux-notification -t P.A.I.R.O -c ...` | B (tulis) |
| pasang | `pkg install -y <paket>` | C (berbahaya) |

Apa pun yang tidak ada di tabel → ditolak, tanpa pengecualian. Tidak ada kolom
`cmd` bebas di skema aksi.

## Lapisan 2 — Validasi ganda

- Server memeriksa `nama` terhadap salinan tabelnya sendiri sebelum meneruskan.
- Aplikasi memeriksa ulang terhadap tabelnya. Server yang disusupi pun tidak
  bisa menyuruh perintah di luar daftar.

## Lapisan 3 — Argumen terbatas

Kalau sebuah aksi butuh argumen, nilainya berasal dari himpunan tertutup:

- folder: hanya `downloads`, `documents`, `dcim`
- paket: hanya dari daftar putih paket Termux (mis. `python`, `ffmpeg`)
- pola: `^[a-z0-9_.-]{1,24}$`, menolak `/`, spasi, `$`, backtick, `;`, `|`, `&`

Tidak ada jalur berkas bebas, tidak ada penggabungan perintah.

## Lapisan 4 — Tangga konfirmasi

- **Tingkat A (baca):** langsung jalan, hasil diucapkan.
- **Tingkat B (tulis):** aplikasi meminta konfirmasi lisan "ya" dalam 60 detik;
  tanpa itu, batal dan dilaporkan.
- **Tingkat C (berbahaya):** mati secara bawaan. Harus dinyalakan manual di
  setelan, tetap butuh konfirmasi lisan, dan otomatis mati lagi setelah 24 jam.

## Lapisan 5 — Sakelar mati

- Sakelar "Cabut akses shell" di setelan: menghentikan semua pengiriman Intent
  ke Termux dan menghapus token sesi.
- Sisi server: `SHELL_ENABLED=0` menolak semua aksi shell, apa pun isi pesan.
- Tekan lama orb: mematikan mikrofon + membatalkan aksi berjalan.

## Lapisan 6 — Pertahanan prompt injection

Serangan yang dicegah: teks dari luar (notifikasi, pesan, halaman web) yang
menyuruh model melakukan sesuatu.

- Aksi hanya boleh lahir dari **giliran yang dimulai pengguna**, bukan dari
  keluaran alat sebelumnya.
- Keluaran perintah dipotong 300 karakter, dibersihkan, dan ditandai sebagai
  *data*, bukan instruksi.
- **Satu aksi per giliran**, tanpa rantai otomatis: hasil satu perintah tidak
  boleh memicu perintah berikutnya dalam giliran yang sama.
- Konten dari sumber tak tepercaya tidak pernah boleh memetakan ke tingkat B/C.

## Lapisan 7 — Catatan audit

Setiap aksi ditulis ke `audit.jsonl` (0600, append-only):

```json
{"t":"2026-10-18T07:12:04+07:00","aksi":"shell","nama":"baterai",
 "argv":["termux-battery-status"],"kode":0,"ms":412,"picu":"suara","id":"a7f3"}
```

Rotasi 1 MB, simpan 3 berkas. Ringkasan mingguan ikut masuk sapaan pagi.

## Lapisan 8 — Pengaman luapan

- Maksimum 10 aksi shell per jam, 1 aksi per 5 detik.
- Aksi sama tiga kali dalam 60 detik → berhenti, ucapkan peringatan.
- Batas waktu 15 detik per perintah; tanpa hasil dalam 20 detik → dilaporkan gagal.
- Termux tidak terpasang atau izin belum diberikan → pesan jelas, tanpa mencoba ulang.

## Lapisan 9 — Hak minimum di tingkat sistem

- Termux berjalan dengan UID-nya sendiri; P.A.I.R.O tidak mendapat shell-nya.
- Tidak meminta `MANAGE_EXTERNAL_STORAGE`, tidak membaca SMS, tidak memakai
  aksesibilitas.
- Shizuku **tidak** dipasang. Kalau nanti diperlukan, itu keputusan terpisah
  dengan pagar tambahan.

## Lapisan 10 — Identitas dan transport

- Aksi hanya diterima lewat WSS bertoken, terikat satu sesi.
- Token bisa dicabut dari setelan; pencabutan langsung memutus kemampuan aksi.
- Semua aksi tercatat dengan waktu dan sumber, sehingga penyalahgunaan bisa
  ditelusuri.

## Lapisan 11 — Gagal dengan aman

- Aksi tak dikenal, argumen aneh, atau keadaan tak terduga → **tolak**, bukan
  coba-coba.
- Tidak ada percobaan ulang otomatis untuk aksi tulis.

## Lapisan 12 — Kedaluwarsa

Akses shell otomatis mati setelah 7 hari kecuali diperpanjang. Pemberian izin
yang lupa dicabut tidak hidup selamanya.

---

## Yang tidak dilindungi skema ini

- Serangan pada perangkat yang sudah di-root.
- Malware yang sudah punya akses setara di HP.
- Pencurian HP dalam keadaan terbuka.
- Penyadapan jaringan di luar Tailscale.

Skema ini mengecilkan kemungkinan dan dampak, bukan menghilangkannya.

---

## Mengapa tangan ada di server, bukan di ponsel

Aksi yang dijalankan di server hanya bisa menyentuh server: berkas rencana,
layanan, skrip pemeliharaan. Kalau model salah memilih, yang rusak adalah kotak
headless di sudut kamar yang bisa di-reset — bukan ponsel yang berisi foto,
pesan, dan aplikasi bank.

Empat aksi yang ada sekarang semuanya lahir dari kebutuhan nyata: menanyakan
kondisi, melihat isi folder kerja, dan menjalankan dua skrip pemeliharaan yang
sudah dipakai sehari-hari. Tidak ada satu pun yang bisa menghapus, memasang,
atau mengirim apa pun keluar.

Kalau nanti sebuah aksi benar-benar harus menyentuh ponsel, ia harus melewati
lapisan 1 sampai 12 di atas **plus** daftar putih di sisi aplikasi — dan itu
pekerjaan terpisah, bukan tambahan diam-diam.
