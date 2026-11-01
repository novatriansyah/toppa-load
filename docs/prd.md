# Product Requirements Document (PRD) — Toppa Load
**Versi:** 1.3 · **Tanggal:** 1 November 2026 · **Skala Target:** 1 Tim Internal (6–7 Akun Aktif, Zona Waktu WIB)

---

## 1. Latar Belakang dan Masalah

Tim internal saat ini (4 anggota tim dan 2 supervisor) merencanakan beban kerja dan mencatat cuti memakai spreadsheet terpisah. Akibatnya:
1. Supervisor harus menyusun ulang rekap beban kerja secara manual setiap minggu untuk mengetahui siapa yang kelebihan beban (*overloaded*) dan siapa yang masih longgar (*underutilized*).
2. Cuti dan hari libur nasional/cuti bersama tidak otomatis mengurangi kapasitas kerja mingguan di spreadsheet alokasi, sehingga anggota sering terlihat memiliki kapasitas 40 jam penuh padahal hanya efektif bekerja 24–32 jam.
3. Tidak ada pemisahan jelas antara rencana pasti (*confirmed*) dan penjajakan (*tentative*), maupun perbandingan antara jam alokasi, estimasi task, dan jam aktual (*timesheet*).

**Toppa Load** menggantikan spreadsheet tersebut menjadi satu alat kerja web internal yang cepat, padat informasi, jujur terhadap privasi anggota tim, dan cukup ringan untuk dirawat oleh **satu orang developer**.

---

## 2. Tujuan, Non-Tujuan, dan Anti-Tujuan

### Tujuan (*Goals*)
- **Visibilitas Instan (< 1 detik):** Supervisor membuka layar utama dan langsung melihat matriks **Heatmap Orang × Minggu** beserta dampak libur dan cuti terhadap kapasitas bersih.
- **Input Minim Klik:** Membuat proyek beserta daftar task selesai dalam `< 2 menit` (Fase 1); mengisi timesheet mingguan selesai dalam `< 3 menit` (Fase 2).
- **Hitungan Terpercaya:** Seluruh angka kapasitas bersih dan persentase utilisasi konsisten 100% dengan 10 kasus uji aturan kapasitas (lihat Bagian 7).
- **Operasional Mandiri:** Satu image Docker di Coolify, satu PostgreSQL dengan backup harian otomatis ke S3, tanpa komponen infrastruktur berlebih.

### Non-Tujuan (*Non-Goals* — Sengaja Dipangkas untuk 6 Pengguna)
- Multi-organisasi, hierarki departemen, atau tabel `Team` bertingkat.
- Pembaruan *real-time* berbasis WebSocket/SSE (polling ringan 30 detik atau *refetch on focus* via TanStack Query sudah memadai).
- Antrean pesan eksternal (Redis/RabbitMQ/Kafka) atau microservice terpisah.
- Integrasi HRIS/payroll, API publik eksternal, webhook keluar generik, dan PWA push notification.

### Anti-Tujuan (*Anti-Goals* — Kegagalan Produk Walau Kode Berfungsi)
- **Terasa Seperti Alat Pengawasan (*Surveillance Tool*):** Aplikasi ini adalah alat pemerataan beban dan perencanaan kapasitas, **bukan** pelacak aktivitas layar, *keystroke*, atau jam online menit-ke-menit. Timer pada Fase 2 bersifat opsional untuk memudahkan pencatatan pribadi anggota tim.
- **Tampilan Template Dashboard Generik:** Dilarang menampilkan deretan kartu KPI raksasa tanpa aksi, gradien dekoratif ungu/biru, ilustrasi kosong, atau kalimat bergaya pemasaran.

---

## 3. Peran Pengguna dan Matriks Hak Akses (RBAC)

Seluruh pengguna masuk melalui **SSO (Google / Microsoft OIDC)** dan diverifikasi terhadap daftar *email allowlist* aktif. Satu zona waktu kerja standar: **WIB (`Asia/Jakarta`)**.

| Kemampuan / Aksi | Admin (1 Dev) | Supervisor (2 Orang) | Member (4 Orang) |
|---|:---:|:---:|:---:|
| Mengelola *allowlist* user, role, jam & hari kerja default, ambang utilisasi | Ya | Lihat | Lihat diri sendiri |
| Mengimpor & mengedit kalender Libur Nasional / Cuti Bersama | Ya | Ya | Lihat |
| Membuat, mengubah, mengarsipkan Proyek & Task | Ya | Ya | Buat/ubah Task pada proyeknya |
| Melihat Heatmap Orang × Minggu seluruh anggota tim | Ya | Ya | Ya *(transparan satu tim)* |
| Membuat & mengubah Alokasi *Confirmed* | Ya | Ya | Tidak |
| Mengajukan Alokasi *Tentative* (usul mandiri) | Ya | Ya | Ya *(untuk diri sendiri)* |
| Mencatat Cuti (Fase 1: langsung / Fase 2: alur *approval*) | Ya | Ya (+ Approve) | Ya *(diri sendiri)* |
| Mengisi & *submit* Timesheet mingguan (Fase 2) | Ya | Lihat & Review/Approve | Ya *(diri sendiri)* |
| Mengekspor Laporan & menjalankan Skenario *What-If* (Fase 3) | Ya | Ya | Lihat ringkasan pribadi |
| Melihat Audit Log sistem | Ya | Lihat entitas bisnis | Tidak |

> **Catatan Transparansi Tim:** Seluruh 6 anggota tim dapat melihat matriks beban kerja tim (bukan catatan privat HR) agar semangat pemerataan beban terasa terbuka dan adil. Catatan alasan cuti sensitif hanya terlihat oleh pemilik cuti, Supervisor, dan Admin.

---

## 4. Empat Pekerjaan Utama (*Core Jobs-to-be-Done*)

Sebelum satu layar pun dibuat, setiap halaman harus melayani salah satu dari 4 pekerjaan inti berikut:

1. **Pemerataan Beban Mingguan (Supervisor):** *"Siapa yang overload (>100%) atau kosong (<60%) dalam 4 minggu ke depan, dan ke siapa saya bisa menggeser alokasi task ini?"* -> Dijawab oleh **Layar Utama: Heatmap Orang × Minggu**.
2. **Perencanaan Proyek & Alokasi (Supervisor):** *"Saya punya proyek baru selama 3 minggu; bagaimana memecah task-nya dan membagi jam per hari ke anggota tim tanpa menabrak jadwal cuti mereka?"* -> Dijawab oleh **Layar Proyek & Panel Alokasi**.
3. **Pemeriksaan Beban & Pencatatan Kerja (Member):** *"Apa fokus pekerjaan saya minggu ini, berapa jam alokasi saya per hari, dan berapa jam aktual yang sudah saya kerjakan?"* -> Dijawab oleh **Layar Beban Saya / Timesheet Mingguan**.
4. **Pengajuan & Persetujuan Cuti/Timesheet (Member & Supervisor):** *"Jika saya cuti Kamis–Jumat depan, alokasi mana yang terdampak, dan apa yang perlu disetujui supervisor hari ini?"* -> Dijawab oleh **Layar Cuti & Kotak Persetujuan**.

---

## 5. Spesifikasi Fungsional per Fase

### Fase 1: Inti Workload (Minggu 1–7) — *MVP Wajib*
- **F1.1 Autentikasi SSO & Allowlist:** Login OIDC, penolakan otomatis untuk email di luar *allowlist*, sesi cookie `HttpOnly` + `SameSite=Lax`, manajemen user & konfigurasi jam kerja harian (default `8.0` jam) serta hari kerja aktif (default Senin–Jumat `[1,2,3,4,5]`).
- **F1.2 Manajemen Proyek & Task:** CRUD Proyek (`kode`, `nama`, `status`: `PLANNING`/`ACTIVE`/`ON_HOLD`/`COMPLETED`/`ARCHIVED`, rentang tanggal, `owner_id`) dan CRUD Task (`judul`, `deskripsi`, `status`: `TODO`/`IN_PROGRESS`/`DONE`/`CANCELLED`, `prioritas`: `LOW`/`MEDIUM`/`HIGH`/`URGENT`, `estimasi_jam`, `due_date`, `label` array). Pencarian teks dan filter status/proyek.
- **F1.3 Manajemen Alokasi:** Penugasan user ke Proyek (dan opsional Task spesifik) pada rentang `start_date`..`end_date` dengan nilai `jam_per_hari` (`NUMERIC(4,2)`, kelipatan `0.25` jam) dan tipe `CONFIRMED` atau `TENTATIVE`. Deteksi dan peringatan tumpang tindih (*overlap warning*) saat total alokasi harian melebihi kapasitas bersih user pada tanggal mana pun di rentang tersebut.
- **F1.4 Kalender Libur & Cuti Sederhana:** Impor massal (CSV/JSON) dan edit manual Libur Nasional & Cuti Bersama Indonesia (`NATIONAL` / `COMPANY`). Pencatatan cuti anggota (`ANNUAL`, `SICK`, `UNPAID`, `OTHER`) mendukung cuti penuh (`1.0` hari / `jam_kerja_per_hari`) maupun setengah hari (`0.5` hari / separuh `jam_kerja_per_hari`).
- **F1.5 Heatmap Orang × Minggu:** Matriks padat berisi baris Anggota Tim dan kolom Periode (zoom: **Hari**, **Minggu** default 8 minggu, **Bulan**). Setiap sel menampilkan:
  - Jam teralokasi *confirmed* vs kapasitas bersih (contoh: `36 / 32j`)
  - Persentase utilisasi rata kanan tabular (`113%`) + indikator status aksesibel (ikon/pola + badge, bukan warna saja)
  - Indikator terpisah bila ada tambahan jam `TENTATIVE` (contoh: `+8j tentatif`)
  - Klik sel membuka *drawer* rincian alokasi minggu tersebut; satu klik berikutnya mengedit/menggeser alokasi.
- **F1.6 Notifikasi In-App & Email Terjadwal:** Lonceng notifikasi *in-app* dan pengiriman email SMTP terjadwal (`@Scheduled`) dengan kunci idempotensi (`dedup_key`) agar email tidak pernah terkirim ganda untuk: alokasi baru/berubah, *due date* task H-2, dan peringatan *overload* mingguan ke supervisor.
- **F1.7 Audit Log & Lokalisasi Dasar:** Seluruh perubahan `User`, `Project`, `Task`, `Allocation`, `Leave`, dan `Holiday` mencatat `sebelum` dan `sesudah` (`JSONB`) di `audit_logs`. Seluruh string UI dimuat dari kamus `id.json` sejak hari pertama.

### Fase 2: Operasional Harian (Minggu 8–16)
- **F2.1 Timesheet Mingguan & Timer:** Grid input cepat `Task × Hari (Sen–Min)` + timer satu klik opsional per task, perbandingan `Estimasi vs Alokasi vs Aktual`, status siklus `DRAFT -> SUBMITTED -> APPROVED / RETURNED` dengan catatan review supervisor, pengingat Jumat sore.
- **F2.2 Multi-View & Saved Views:** Tampilan Kanban, List padat, Kalender (FullCalendar), dan Timeline/Gantt ringkas; filter gabungan tersimpan di query parameter URL sehingga setiap tampilan dapat dibagikan via tautan.
- **F2.3 Approval Cuti, Saldo Cuti, Skill & Rebalancing Drag-and-Drop:** Alur `PENDING -> APPROVED / REJECTED`, kuota cuti tahunan, penandaan *skill* & level per user, serta *drag-and-drop* pemindahan alokasi dengan *live preview* perubahan utilisasi dan tombol *Undo*.
- **F2.4 Kolaborasi Task & Notifikasi Chat:** Komentar, `@mention`, lampiran file terbatas (tersimpan aman di volume/S3), task berulang, template proyek, dan webhook keluar ke 1 kanal chat pilihan tim (Slack / Microsoft Teams / Telegram / WhatsApp API resmi) dengan *toggle* per pengguna.

### Fase 3: Insight, Forecasting, Integrasi & AI (Minggu 17–24 — *Opsional*)
> **Gerbang Keputusan:** Fase 3 baru dieksekusi setelah evaluasi pemakaian nyata pada Minggu 16. Fitur tanpa pengguna nyata dipotong.
- **F3.1 Laporan & Ekspor:** Tren utilisasi per orang/proyek, akurasi estimasi, deteksi *overload* `>= 3` minggu berturut-turut, ekspor CSV/XLSX/PDF, rekap mingguan otomatis ke email supervisor setiap Senin pagi.
- **F3.2 Forecasting *What-If*:** Simulasi proyek hipotetis atau simulasi anggota cuti panjang di ruang skenario terisolasi tanpa menyentuh tabel alokasi aktif hingga ditekan *"Terapkan sebagai Rencana"*.
- **F3.3 Integrasi Satu Arah (Maksimal 2 Alat):** Sinkronisasi satu arah dari Jira / GitHub / GitLab (isu -> task) atau ke Google Calendar / Outlook (alokasi & cuti -> blok kalender) beserta tabel log sinkronisasi.
- **F3.4 Bantuan AI Terkendali:** Rekomendasi *assignee* berbasis *skill* + sisa kapasitas, estimasi awal dari histori task serupa, dan draf ringkasan mingguan. Wajib memiliki *kill-switch* global di Admin, meminimalkan PII sebelum memanggil API LLM, dan selalu menyertakan alasan tertulis di bawah setiap saran.

---

## 6. Panduan UI/UX dan Daftar Cek Pra-Rilis

### Sistem Visual Alat Kerja
- **Tipografi:** Satu keluarga font modern yang terbaca tajam pada ukuran `12px–14px` dalam Bahasa Indonesia (contoh: `Inter` atau `Plus Jakarta Sans` untuk teks + `font-variant-numeric: tabular-nums` wajib untuk seluruh kolom angka, jam, tanggal, dan persentase).
- **Kepadatan & Tata Letak:** Skala spasi kelipatan `4px`; radius sudut kecil dan tegas (`4px–6px`); pemisah antar area menggunakan garis `1px` kontras halus, bukan bayangan tebal (*drop shadow*).
- **Semantik Status Kapasitas (Aksesibel Buta Warna):**
  - `UNDERUTILIZED` (`< 60%`): Nada netral/biru-abu tenang + label `Longgar` / ikon panah bawah tipis.
  - `OPTIMAL` (`60% – 100%`): Nada hijau/teal kerja tenang + label `Optimal` / ikon centang tipis.
  - `OVERLOADED` (`> 100%`): Nada merah/amber tegas + pola garis arsir halus di latar sel + teks tebal + ikon peringatan segitiga (`!`), sehingga langsung terbedakan walau dicetak hitam-putih.
  - `NO_CAPACITY` (`kapasitas bersih = 0`, mis. cuti penuh seminggu namun ada alokasi `> 0`): Ditandai khusus `Konflik Cuti (!)` agar tidak terjadi pembagian dengan nol.
- **Navigasi Samping (5–6 Item Bahasa Tim):** `Beban Kerja` (Heatmap), `Proyek & Task`, `Timesheet` *(Fase 2)*, `Cuti & Libur`, `Laporan` *(Fase 3)*, `Pengaturan`.

### Daftar Cek Kualitas Layar (Wajib Lolos Sebelum Rilis Fase)
1. **Satu Pertanyaan Utama:** Dapatkah tujuan layar dijelaskan dalam satu kalimat tugas kerja?
2. **Nol Dekorasi:** Tidak ada kartu KPI tanpa aksi, gradien hiasan, emoji pengganti ikon, atau ilustrasi pemanis.
3. **Makna Warna Ganda:** Tidak ada informasi status yang hanya bisa dibaca lewat perbedaan warna (WCAG AA `>= 4.5:1`).
4. **Angka Tabular Rata Kanan:** Seluruh jam dan persentase memakai `tabular-nums`, rata kanan, dengan satuan konsisten (`j` untuk jam, `%` untuk utilisasi).
5. **Empat Kondisi Spesifik:** State *loading*, *empty* (berisi CTA langkah berikutnya), *error* (sebut penyebab & solusi), dan *success* ditulis spesifik sesuai konteks layar.
6. **Alur Keyboard:** Filter cepat, pindah minggu di heatmap, dan buka/simpan form alokasi bisa diselesaikan tanpa mouse.
7. **Uji Data Nyata:** Tetap rapi dengan nama Indonesia 35+ karakter, 30 baris proyek, dan rentang lintas bulan/tahun.

---

## 7. Aturan Perhitungan Kapasitas dan Utilisasi *(Rujukan Kanonik)*

Bagian ini menjadi acuan tunggal untuk implementasi SQL PostgreSQL (`generate_series`), tampilan UI, dan pengujian otomatis JUnit 5 + Testcontainers.

### 7.1 Definisi Variabel Dasar
- **Zona Waktu Kalender Kerja:** Seluruh tanggal kerja (`start_date`, `end_date`, `tanggal` libur/cuti) disimpan sebagai tipe PostgreSQL `DATE` yang merepresentasikan hari kalender **WIB (`Asia/Jakarta`)**. Timestamp sistem (`created_at`, `updated_at`) disimpan dalam `TIMESTAMPTZ` (UTC).
- **Hari Kerja User ($W_u$):** Himpunan hari aktif per user dalam format ISO-8601 (`1 = Senin` .. `7 = Minggu`). Default: `{1, 2, 3, 4, 5}` (Senin–Jumat).
- **Jam Kerja Dasar Harian ($B_u(d)$):**
  - Jika $\text{isodow}(d) \in W_u$, maka $B_u(d) = \text{jam\_kerja\_per\_hari}_u$ (default `8.00` jam).
  - Jika $\text{isodow}(d) \notin W_u$ (akhir pekan bagi user tersebut), maka $B_u(d) = 0.00$ jam.

### 7.2 Pengurangan Hari Libur dan Cuti Harian
Pada setiap tanggal kalender $d$:
1. **Pengurangan Libur ($H(d)$):**
   - Jika terdapat minimal satu entri aktif pada tabel `holidays` di tanggal $d$ (baik `NATIONAL` maupun `COMPANY`), maka hari tersebut adalah hari libur penuh: hari kerja efektif menjadi `0.00` jam ($H(d) = B_u(d)$).
   - *Aturan Anti-Minus Ganda:* Jika seorang user memiliki cuti yang melintasi hari libur nasional atau akhir pekan, hari libur/akhir pekan tersebut **tidak** memotong kapasitas dua kali dan tidak memotong kuota cuti.
2. **Pengurangan Cuti ($L_u(d)$) pada Hari Kerja Non-Libur ($B_u(d) > 0$ dan bukan `Holiday`):**
   - Hanya cuti berstatus `APPROVED` yang mengurangi kapasitas bersih. *(Pada Fase 1, setiap cuti yang dicatat langsung berstatus `APPROVED`; pada Fase 2 melalui persetujuan supervisor).*
   - Cuti penuh (`portion = FULL` / `1.0`): $L_u(d) = B_u(d)$.
   - Cuti setengah hari (`portion = HALF_AM` atau `HALF_PM` / `0.5`): $L_u(d) = 0.5 \times B_u(d)$ (mis. `4.00` jam untuk user dengan jam kerja `8.00` jam).
   - Total pengurangan cuti dalam satu hari dibatasi maksimum sebesar sisa kapasitas hari tersebut.

3. **Kapasitas Bersih Harian ($C_u(d)$):**
   $$C_u(d) = \max\bigl(0,\; B_u(d) - H(d) - L_u(d)\bigr)$$

### 7.3 Ekspansi Alokasi Harian
Sebuah `Allocation` $a$ memiliki `start_date`, `end_date`, `hours_per_day`, dan `allocation_type` $\in \{\text{CONFIRMED}, \text{TENTATIVE}\}$.
- Alokasi diekspansi ke setiap tanggal $d \in [\text{start\_date}, \text{end\_date}]$ menggunakan `generate_series`.
- **Aturan Hari Aktif Alokasi:** Alokasi hanya menghasilkan beban jam pada tanggal $d$ yang merupakan **hari kerja reguler user dan bukan hari libur** ($\text{isodow}(d) \in W_u$ dan bukan `Holiday`).
  - *Mengapa?* Jika proyek dialokasikan `1 Nov – 14 Nov` sebesar `4 jam/hari`, akhir pekan Sabtu–Minggu dan Tanggal Merah otomatis dilewati (`0 jam`).
  - **Penting — Interaksi dengan Cuti Pribadi:** Jika tanggal $d$ adalah hari kerja normal (bukan libur nasional), tetapi user mengambil **Cuti** pada tanggal $d$ padahal masih memiliki `Allocation` aktif di tanggal $d$, maka:
    - Kapasitas bersih $C_u(d)$ turun (menjadi `0` untuk cuti penuh, atau `4` untuk cuti setengah hari), **sedangkan** jam alokasi pada tanggal $d$ **tetap dihitung** sebesar `hours_per_day`!
    - *Tujuan bisnis:* Ini secara sengaja memicu status **Overloaded / Konflik Cuti** pada heatmap sehingga supervisor langsung melihat bahwa alokasi pada hari cuti tersebut belum dipindahkan atau disesuaikan!

### 7.4 Agregasi Periode, Utilisasi, dan Ambang Status
Untuk suatu periode $P = [d_1, d_2]$ (Hari, Minggu ISO Senin–Minggu, atau Bulan kalender):
- **Total Kapasitas Bersih:** $C_u(P) = \sum_{d \in P} C_u(d)$
- **Total Alokasi Confirmed:** $A^{\text{conf}}_u(P) = \sum_{d \in P} A^{\text{conf}}_u(d)$
- **Total Alokasi Tentative:** $A^{\text{tent}}_u(P) = \sum_{d \in P} A^{\text{tent}}_u(d)$ *(ditampilkan terpisah, tidak dicampur ke persentase utilisasi utama kecuali mode pratinjau "Sertakan Tentatif" diaktifkan di filter UI)*
- **Persentase Utilisasi Confirmed ($U_u(P)$):**
  - Jika $C_u(P) > 0$:
    $$U_u(P) = \text{ROUND}\left(\frac{A^{\text{conf}}_u(P)}{C_u(P)} \times 100,\; 1\right)$$
  - Jika $C_u(P) = 0$ dan $A^{\text{conf}}_u(P) = 0$: $U_u(P) = 0.0\%$, Status = `NO_CAPACITY` (Libur / Cuti Penuh Tanpa Beban).
  - Jika $C_u(P) = 0$ dan $A^{\text{conf}}_u(P) > 0$: $U_u(P) = \text{null}$ (ditampilkan `Konflik!`, mis. `8 / 0j`), Status = `OVERLOADED` (dengan flag `LEAVE_CONFLICT = true`).

- ** Klasifikasi Status Default (Ambang Dapat Diubah Admin di Pengaturan, default `min=60`, `max=100`):**
  - `UNDERUTILIZED`: $C_u(P) > 0$ dan $U_u(P) < 60.0\%$
  - `OPTIMAL`: $C_u(P) > 0$ dan $60.0\% \le U_u(P) \le 100.0\%$
  - `OVERLOADED`: ($C_u(P) > 0$ dan $U_u(P) > 100.0\%$) ATAU ($C_u(P) = 0$ dan $A^{\text{conf}}_u(P) > 0$)

### 7.5 Sepuluh Kasus Uji Manual Kanonik (*Golden Test Suite* — Minggu 4)
Seluruh 10 skenario berikut wajib lulus di unit/integration test sebelum rilis Fase 1:

| # | Skenario Uji (User default: Sen–Jum, 8j/hari = 40j/minggu) | Kapasitas Bersih | Alokasi Confirmed | Utilisasi & Status yang Diharapkan |
|---|---|---:|---:|---|
| **TC-01** | Minggu normal tanpa libur/cuti, 1 proyek `6j/hari` Sen–Jum | `40.0j` | `30.0j` | `75.0%` -> `OPTIMAL` |
| **TC-02** | Minggu normal, 1 proyek `4j/hari` Sen–Jum (`20j` total) | `40.0j` | `20.0j` | `50.0%` -> `UNDERUTILIZED` (`< 60%`) |
| **TC-03** | Batas bawah tepat `60%`: alokasi `24j` dalam seminggu `40j` | `40.0j` | `24.0j` | `60.0%` -> `OPTIMAL` (inklusif) |
| **TC-04** | Batas atas tepat `100%`: dua proyek tumpang tindih (`5j` + `3j`/hari) Sen–Jum | `40.0j` | `40.0j` | `100.0%` -> `OPTIMAL` (inklusif) |
| **TC-05** | Tumpang tindih melebihi kapasitas: Proyek A `6j/hari` Sen–Jum + Proyek B `4j/hari` Rab–Jum | `40.0j` | `30 + 12 = 42.0j` | `105.0%` -> `OVERLOADED` |
| **TC-06** | Ada 1 Hari Libur Nasional di hari Kamis; alokasi rentang Sen–Jum `8j/hari` | `32.0j` | `32.0j` *(Kamis otomatis 0j)* | `100.0%` -> `OPTIMAL` |
| **TC-07** | User Cuti Penuh hari Jumat (`APPROVED`), namun masih ada alokasi proyek Sen–Jum `8j/hari` | `32.0j` | `40.0j` *(Jumat tetap terhitung beban)* | `125.0%` -> `OVERLOADED` (konflik cuti terdeteksi!) |
| **TC-08** | User Cuti Setengah Hari (`0.5` = `4j`) hari Rabu; alokasi Sen–Jum `6j/hari` (`30j`) | `36.0j` | `30.0j` | `83.3%` -> `OPTIMAL` |
| **TC-09** | User Cuti Penuh Sen–Jum (`5 hari`), dan masih punya alokasi `4j/hari` di Sen–Sel | `0.0j` | `8.0j` | `C=0, A=8.0j` -> `OVERLOADED` (`leaveConflict=true`, tanpa error *divide-by-zero*) |
| **TC-10** | Alokasi `CONFIRMED` `6j/hari` (`30j`) + Alokasi `TENTATIVE` `4j/hari` (`20j`) di minggu normal | `40.0j` | `30.0j` (+`20.0j` tentatif) | Default: `75.0%` (`OPTIMAL`) + badge `+20j tentatif`; Mode *Include Tentative*: `125.0%` (`OVERLOADED`) |

---

## 8. Kebutuhan Non-Fungsional (NFR)

1. **Performa:**
   - Waktu respons API `p95 < 300 ms` untuk dataset 10 user × 3 tahun riwayat alokasi.
   - Render penuh layar Heatmap Orang × Minggu (8 minggu) `< 1 detik` di jaringan kantor standar.
2. **Keamanan (OWASP Baseline):**
   - Wajib HTTPS (Let's Encrypt via Coolify), cookie sesi `HttpOnly`, `Secure` (di produksi), `SameSite=Lax`.
   - Proteksi CSRF aktif untuk seluruh endpoint mutasi (`POST`, `PUT`, `PATCH`, `DELETE`).
   - Otorisasi peran (`@PreAuthorize`) diverifikasi di server pada setiap endpoint bisnis, dilengkapi *integration test* akses silang (Member mencoba mutasi hak Supervisor wajib `403 Forbidden`).
   - Rate limiting sederhana di level filter aplikasi untuk endpoint login/mutasi publik.
3. **Pelindungan Data Pribadi (UU PDP Indonesia):**
   - Pemberitahuan privasi singkat pada layar masuk pertama yang menjelaskan data apa yang dicatat (alokasi proyek, tanggal cuti, jam timesheet) dan siapa yang dapat mengaksesnya.
   - Persetujuan tertulis perusahaan sebelum data karyawan nyata dimasukkan ke VPS.
   - Kemudahan portabilitas penuh: standar `pg_dump` PostgreSQL + Docker image standar tanpa ketergantungan layanan *proprietary* yang mengunci data.
4. **Keandalan & Pemulihan Bencana:**
   - Target ketersediaan (*SLA internal*): Jam kerja Senin–Jumat `08.00–18.00 WIB`.
   - RPO (*Recovery Point Objective*): `<= 24 jam` (backup dump PostgreSQL harian otomatis ke S3 eksternal + retensi 14–30 hari).
   - RTO (*Recovery Time Objective*): `<= 2 jam` (prosedur restore terdokumentasi di [`docs/runbook.md`](runbook.md) dan diuji ke *staging* sejak Minggu 1 serta setiap kuartal).
