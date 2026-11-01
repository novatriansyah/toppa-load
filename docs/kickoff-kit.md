# Paket Siap Kick-Off (Hari-0) — Toppa Load

Dokumen ini menyelesaikan seluruh persiapan pra-eksekusi dari **Bagian 11 (Pertanyaan & Langkah Berikutnya)** agar pengembangan **Minggu 1** dapat langsung jalan tanpa hambatan.

> **Prinsip Keputusan Default:** Seluruh 7 pertanyaan terbuka di draf v1.3 telah ditetapkan menggunakan **Keputusan Default Builder** yang paling aman dan fleksibel (mudah diubah lewat konfigurasi tanpa membongkar arsitektur). Tandai `[x]` atau ubah nilainya bila kebijakan tim berbeda.

---

## 1. Keputusan Default untuk 7 Pertanyaan Pra-Kick-Off

| # | Pertanyaan Bagian 11 | Keputusan Default Builder *(Siap Jalan)* | Dampak di Kode / Cara Mengubah Nanti |
|---|---|---|---|
| **1** | **Waktu kerja dev & izin publikasi portfolio?** | **Penuh waktu efektif (±30 jam/minggu -> 7 minggu Fase 1)**. Repositori disiapkan **bersih dari data perusahaan/karyawan asli** sejak komit pertama agar aman dipisahkan menjadi *demo generik* kapan saja. | Jika paruh waktu (±15 jam/minggu), kalikan jadwal kalender di [`roadmap-backlog.md`](roadmap-backlog.md) dengan `2x` tanpa mengubah urutan task. |
| **2** | **Peran 2 Supervisor: hanya review atau ikut eksekusi?** | **Peran penuh operasional:** Melihat seluruh beban kerja, membuat/mengubah proyek & alokasi, menyetujui cuti & timesheet (Fase 2), serta memberi komentar pada alokasi/task. | Diatur terpusat di matriks `@PreAuthorize` modul `identity`; mudah dipersempit bila 1 supervisor hanya ingin *read-only*. |
| **3** | **Siapa yang boleh membuat alokasi?** | **Supervisor & Admin** membuat/mengubah alokasi `CONFIRMED` maupun `TENTATIVE`. **Member** dapat mengajukan usulan alokasi `TENTATIVE` untuk dirinya sendiri (yang kemudian dapat di-*confirm* oleh Supervisor). | Memberikan otonomi tim tanpa merusak angka utilisasi `CONFIRMED` utama. Bisa dimatikan lewat satu *feature flag* `app.allocation.member-tentative-enabled=true`. |
| **4** | **Persetujuan hosting VPS pribadi & rencana pindah?** | **Staging memakai data sintetis 100%** sampai formulir persetujuan VPS (Bagian 3 dokumen ini) ditandatangani perusahaan sebelum Minggu 7. Arsitektur 100% portabel (`Docker + PostgreSQL 17`) sehingga migrasi ke VM/on-prem perusahaan cukup `pg_dump` + `docker compose up` (< 1 jam). | Nol *vendor lock-in*; seluruh konfigurasi lewat environment variable di [`deploy/.env.example`](../deploy/.env.example). |
| **5** | **Spesifikasi VPS, S3 Backup, dan Domain?** | **VPS:** 2 vCPU, 4 GB RAM, 60–80 GB NVMe SSD, Ubuntu 24.04 LTS, Region **Singapura / Jakarta**. **S3 Backup:** **Cloudflare R2** (tier gratis 10 GB tanpa biaya *egress*). **Domain:** `workload.<domain-anda>` (Prod) & `staging-workload.<domain-anda>` (Staging). | Cukup isi variabel di Coolify sesuai panduan [`deploy/coolify-setup-checklist.md`](../deploy/coolify-setup-checklist.md). |
| **6** | **Provider SSO & Aplikasi Chat Tim?** | **SSO:** Konfigurasi Spring Security OAuth2 disiapkan mendukung **Google Workspace OIDC** (default aktif) **dan Microsoft Entra ID** (siap aktif lewat env var) + mode *Dev Local Login* khusus profil `local`/`test`. **Chat (Fase 2 Minggu 14):** Abstraksi `ChatNotifier` (default webhook Slack/Telegram/Teams, dipilih saat Minggu 13). | Tidak memblokir Minggu 1–2; cukup isi `GOOGLE_CLIENT_ID` atau `MICROSOFT_CLIENT_ID` di Coolify. |
| **7** | **Impor data awal spreadsheet?** | **Minggu 5 menyediakan fitur Impor CSV/JSON Libur Nasional**, ditambah utilitas **Impor CSV Proyek & Alokasi Awal** sederhana untuk memindahkan data aktif dari spreadsheet tim saat *onboarding* Minggu 7 (riwayat lama tetap di arsip spreadsheet agar bersih). | Menghindari migrasi data kotor bertahun-tahun; tim mulai di Fase 1 hanya dengan proyek aktif berjalan. |

---

## 2. Audit Kesiapan Mesin Lokal (Windows Workstation)

Hasil pemeriksaan otomatis pada mesin pengembangan ini (`d:\Nova\Personal\Projects\toppa-load`):

| Perkakas | Status Saat Ini | Tindakan yang Diperlukan Sebelum Menjalankan Build |
|---|---|---|
| **Git** | **Siap** (`git 2.54.0.windows.1`, repo `main` aktif) | Tidak ada. |
| **Node.js** | **Siap** (`v24.18.0` terpasang) | Catatan PowerShell: kebijakan skrip Windows memblokir `npm.ps1`. Gunakan `npm.cmd` atau jalankan sekali: `Set-ExecutionPolicy -Scope CurrentUser RemoteSigned`. |
| **Java 25 LTS** | **Belum ada di `PATH`** | Pasang Eclipse Temurin JDK 25 via Winget:<br>`winget install --id EclipseAdoptium.Temurin.25.JDK -e`<br>*(Setelah pasang, buka ulang terminal & cek `java -version`)*. |
| **Maven** | **Dibungkus Wrapper (`mvnw.cmd`)** | Rencana Minggu 1 menyertakan Maven Wrapper (`./mvnw.cmd`), sehingga Anda tidak wajib memasang Maven global secara manual. |
| **Docker Desktop** | **Belum ada di `PATH`** | Diperlukan untuk PostgreSQL lokal & Testcontainers JUnit 5:<br>`winget install --id Docker.DockerDesktop -e`<br>*(Pastikan WSL2 aktif dan Docker Desktop menyala saat menjalankan integration test)*. |

---

## 3. Template Persetujuan Hosting VPS & Kepatuhan Data (Siap Kirim ke Manajemen/HR)

> Salin draf singkat ini sebelum Minggu 7 (sebelum data karyawan nyata dimasukkan ke sistem produksi):

```text
Subjek: Konfirmasi Hosting & Pelindungan Data Internal — Aplikasi Workload Tim (Toppa Load)

Halo [Nama Manager / Perwakilan HR / IT],

Sebagai bagian dari inisiatif merapikan perencanaan kapasitas dan beban kerja tim internal (6 pengguna: 4 anggota tim dan 2 supervisor), kami sedang menyiapkan aplikasi web internal "Toppa Load" untuk menggantikan rekap spreadsheet mingguan.

Sebelum data operasional nyata dimasukkan pada akhir Fase 1, mohon konfirmasi dan persetujuan tertulis untuk ketentuan tata kelola data berikut:

1. Data yang Dicatat: Nama, alamat email kerja (untuk login SSO), proyek/task, jam alokasi kerja harian, tanggal cuti, dan catatan jam kerja (timesheet). Sistem TIDAK mencatat data gaji/payroll, NIK, alamat rumah, maupun aktivitas layar/keystroke.
2. Lokasi & Keamanan Hosting Awal: Server VPS terdedikasi di region [Singapura/Indonesia] yang dikelola melalui Coolify dengan enkripsi HTTPS penuh, login wajib SSO + daftar email terbatas (allowlist), firewall ketat, dan backup terenkripsi harian ke penyimpanan objek terpisah.
3. Pengendali Data & Portabilitas: Seluruh data operasional tim adalah milik perusahaan. Aplikasi dibangun di atas kontainer Docker dan PostgreSQL standar, sehingga dapat dipindahkan penuh ke server/infrastruktur milik perusahaan kapan pun diminta dalam waktu kurang dari 1 hari kerja, atau dihapus permanen bila tidak lagi digunakan.
4. Batasan Portfolio Developer: Kode sumber aplikasi tidak memuat data karyawan maupun rahasia bisnis perusahaan. [Opsional: Mohon konfirmasi pula apakah versi generik tanpa merek/data perusahaan diizinkan untuk ditampilkan sebagai portfolio teknis developer.]

Mohon balas email ini dengan konfirmasi persetujuan atau catatan penyesuaian bila diperlukan. Terima kasih!
```

---

## 4. Scorecard Pengukuran Baseline (Langkah Berikutnya #3 — Minggu 1)

Isi tabel ini sekali saja di **Minggu 1** bersama kedua supervisor sebagai pembanding metrik keberhasilan setelah Fase 1 & Fase 2 rilis:

| Metrik Kondisi Sebelum Toppa Load | Supervisor 1 | Supervisor 2 | Rata-rata Awal | Target Pasca Fase 1/2 |
|---|---|---|---|---|
| Waktu menyusun rekap beban kerja mingguan (menit/minggu) | `... mnt` | `... mnt` | `... mnt` | Turun `>= 50%` (Fase 1), `0 mnt` manual (Fase 3) |
| Jumlah spreadsheet/file berbeda yang harus dibuka untuk cek kapasitas + cuti | `... file` | `... file` | `... file` | `1 layar` (Heatmap) |
| Kejadian jadwal proyek menabrak cuti/libur yang baru disadari belakangan (kali/bulan) | `... kali` | `... kali` | `... kali` | `0 kali` |
| Kepuasan terhadap kejelasan beban kerja tim saat ini (skala 1–5) | `... / 5` | `... / 5` | `... / 5` | `>= 4.0 / 5` |

---

## 5. Panduan Wawancara 15 Menit & Agenda Workshop 1 Jam (Minggu 2)

### A. Skrip Wawancara 15 Menit (1 Supervisor + 2 Anggota Tim)
1. **Minta Buka Alat Sekarang (5 menit):** *"Boleh tunjukkan file spreadsheet atau catatan yang terakhir kali dibuka untuk mengecek kerjaan minggu ini?"* -> **Catat nama kolom persis yang mereka pakai** untuk diserap ke file kamus UI (`id.json`).
2. **Momen Paling Menyebalkan (5 menit):** *"Dalam sebulan terakhir, kapan momen paling bikin pusing soal pembagian tugas atau jadwal cuti?"*
3. **Uji Sensitivitas Privasi (5 menit):** *"Kalau semua anggota 1 tim bisa melihat persentase beban mingguan rekan setimnya di satu tabel supaya pembagian tugas adil, apakah nyaman, atau ada bagian yang harus hanya dilihat supervisor?"*

### B. Agenda Sesi 1 Jam Kesepakatan Aturan Kapasitas (Tim + 2 Supervisor)
- **Menit 00–10:** Tunjukkan tujuan aplikasi (meratakan beban & mendeteksi bentrok cuti lebih awal, **bukan** alat absensi/pengawasan).
- **Menit 10–30:** Verifikasi bersama **Aturan Kapasitas ([`docs/prd.md` Bagian 7](prd.md#7-aturan-perhitungan-kapasitas-dan-utilisasi))**:
  - Jam kerja standar (`8 jam/hari`, Senin–Jumat)?
  - Ambang warna/status: `< 60%` Longgar, `60–100%` Optimal, `> 100%` Overload — apakah angka `60%` dan `100%` sudah pas?
  - Konfirmasi aturan **TC-07**: Saat orang cuti tapi masih ada alokasi proyek di tanggal tersebut, sistem sengaja menandai *Overload / Konflik Cuti* agar supervisor memindahkan tugasnya.
  - Konfirmasi pemisahan alokasi `Confirmed` vs `Tentative`.
- **Menit 30–50:** Review *wireframe low-fidelity* 4 layar utama (Heatmap Orang × Minggu, Daftar/Detail Proyek & Task, Drawer Form Alokasi, Kalender Cuti & Libur).
- **Menit 50–60:** Kunci keputusan daftar email 6 pengguna + jadwal sesi demo rutin **30 menit tiap akhir minggu**.

