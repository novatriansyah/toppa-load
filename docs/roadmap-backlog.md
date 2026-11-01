# Master Roadmap & Backlog Mingguan (24 Minggu) — Toppa Load

Dokumen ini menjadi sumber tunggal untuk papan backlog proyek (GitHub Projects / Trello) dengan irama kerja **Solo Developer (±30 jam efektif/minggu)**.

---

## 1. Aturan Disiplin Eksekusi Solo

1. **Batas WIP = 1:** Hanya boleh ada **satu** fitur/task aktif di kolom `In Progress` pada satu waktu.
2. **Aturan Potong Scope `1.5x`:** Setiap baris pekerjaan memiliki estimasi jam. Jika realisasi melewati `1.5x` estimasi (mis. estimasi 8 jam sudah terpakai 12 jam belum selesai), **wajib memangkas variasi/polesannya** ke solusi paling sederhana yang tetap memenuhi kriteria *Selesai Bila*, bukan menambah jam lembur.
3. **Demo 30 Menit Setiap Akhir Minggu:** Setiap Jumat sore / awal pekan, demokan progres di lingkungan *Staging* ke kedua supervisor untuk mengunci umpan balik lebih dini.
4. **Definition of Done (DoD) per Fitur:**
   - Pull Request di-review mandiri (cek diff, tanpa kode mati atau `TODO` liar).
   - Unit test + Testcontainers integration test lulus hijau di GitHub Actions CI.
   - Migrasi database Flyway mengikuti pola *expand-contract* (tidak merusak rilis sebelumnya bila aplikasi di-*rollback*).
   - Teks UI dimuat melalui file terjemahan (`id.json`), angka rata kanan `tabular-nums`, dan lolos otorisasi `@PreAuthorize` sisi server.
   - Ter-deploy dan terverifikasi di *Staging* Coolify.

---

## 2. Fase 1: Inti Workload (Minggu 1–7) — *MVP Wajib*

> **Rencana Implementasi Teknis Detail (TDD Step-by-Step):**
> - Minggu 1: [`docs/superpowers/plans/2026-11-01-f1-01-foundation-and-deploy.md`](superpowers/plans/2026-11-01-f1-01-foundation-and-deploy.md)
> - Minggu 2–3: [`docs/superpowers/plans/2026-11-01-f1-02-auth-users-projects-tasks.md`](superpowers/plans/2026-11-01-f1-02-auth-users-projects-tasks.md)
> - Minggu 4–5: [`docs/superpowers/plans/2026-11-01-f1-03-allocations-leaves-capacity-heatmap.md`](superpowers/plans/2026-11-01-f1-03-allocations-leaves-capacity-heatmap.md)
> - Minggu 6–7: [`docs/superpowers/plans/2026-11-01-f1-04-notifications-and-hardening.md`](superpowers/plans/2026-11-01-f1-04-notifications-and-hardening.md)

| Minggu | ID Backlog | Fokus Pekerjaan | Estimasi | Kriteria Selesai (*Exit Criteria*) | Aturan Pemangkasan Darurat (*Jika Meleset*) |
|:---:|---|---|---:|---|---|
| **M1** | `F1-W1` | **Fondasi & Infrastruktur:** Monorepo `server/` (Java 25 + Spring Boot 4.1.0 + Modulith 2.1) & `web/` (React + Vite + TS + i18n), Flyway `V1` skema inti, Multi-stage `Dockerfile`, GitHub Actions CI, VPS + Coolify (Prod & Staging) + HTTPS + Backup PostgreSQL ke S3 + Uji Restore pertama | 28j | Push ke `main` otomatis lulus CI & ter-deploy di Staging Coolify; 1 backup S3 berhasil di-restore ke DB Staging | Tunda integrasi Sentry ke M6; fokus penuh pada pipeline CI -> Coolify -> Backup S3 |
| **M2** | `F1-W2` | **Discovery, Desain & Auth:** Wawancara 3 user (15 mnt), kunci aturan kapasitas Bagian 7 PRD, wireframe 4 layar utama, *spike* 1 hari library DnD (`@dnd-kit`) & Gantt, implementasi Login SSO OIDC (Google/Microsoft) + Email Allowlist + RBAC (`ADMIN`/`SUPERVISOR`/`MEMBER`) | 30j | Wireframe disetujui supervisor; user di luar allowlist tertolak `403`; user terdaftar masuk sesuai role; hasil *spike* DnD/Gantt tercatat di ADR | *Minggu terpadat:* Jika wawancara/wireframe memakan waktu lebih, batasi *spike* DnD/Gantt maksimal 4 jam dan geser perapian profil user ke M3 |
| **M3** | `F1-W3` | **Konfigurasi Kerja, Proyek, Task & Audit Log:** Pengaturan `jam_kerja_per_hari` & `hari_kerja` per user; CRUD Proyek & Task lengkap (status, prioritas, estimasi jam, due date, label); tabel pencarian & filter padat; pencatatan `audit_logs` (`JSONB` sebelum/sesudah) | 28j | Supervisor membuat proyek + 5 task dalam `< 2 menit`; Member tertolak pada aksi khusus Supervisor; setiap mutasi tercatat di `audit_logs` | Sederhanakan label task menjadi array tag teks biasa (tanpa manajemen warna label kustom) |
| **M4** | `F1-W4` | **Alokasi & Mesin Kapasitas PostgreSQL:** Drawer/Form Alokasi (user, proyek/task, rentang tanggal, `jam_per_hari`, `CONFIRMED`/`TENTATIVE`), validasi peringatan tumpang tindih, query SQL `generate_series` kapasitas bersih & utilisasi | 28j | Seluruh **10 Kasus Uji Kanonik (`TC-01` s/d `TC-10`)** lulus 100% di Testcontainers PostgreSQL 17 | Form alokasi fokus satu rentang per simpan (input batch multi-baris ditunda ke Fase 2) |
| **M5** | `F1-W5` | **Libur, Cuti & Layar Utama Heatmap:** Impor CSV/JSON Libur Nasional & Cuti Bersama Indonesia + edit manual; pencatatan cuti penuh & setengah hari; layar utama **Heatmap Orang × Minggu** (zoom Hari/Minggu/Bulan) + drawer detail klik sel | 30j | Heatmap 8 minggu tampil `< 1 detik`; perubahan libur/cuti langsung mengubah kapasitas bersih & status aksesibel (angka tabular + pola/ikon) | Utamakan zoom **Minggu** (default) dan **Hari** lebih dulu; zoom **Bulan** boleh berupa agregasi tabel ringkas tanpa grafik tambahan |
| **M6** | `F1-W6` | **Notifikasi In-App, Email Terjadwal & Pengaturan Ambang:** Ambang utilisasi (`min`/`max`, default `60%`/`100%`) dapat diatur Admin; lonceng notifikasi *in-app*; scheduler email SMTP harian/mingguan dengan kunci idempotensi (`dedup_key`) | 26j | Email alokasi baru, pengingat H-2 deadline, dan rekap overload terkirim tepat sasaran dan **tidak pernah dobel** walau aplikasi restart | Bila Minggu 2 sempat bergeser, pangkas kustomisasi template HTML email menjadi email teks/HTML bersih minimalis |
| **M7** | `F1-W7` | **Hardening, Uji E2E, Persetujuan Hosting & Onboarding:** Uji Playwright 4 alur inti, audit keamanan OWASP dasar (CSRF, headers, rate limit, RBAC), uji restore S3 kedua, impor CSV proyek/alokasi aktif, *onboarding* 6 pengguna nyata + survei awal | 26j | Persetujuan tertulis perusahaan untuk VPS diperoleh; 6 dari 6 pengguna aktif memakai aplikasi menggantikan spreadsheet alokasi & cuti | Bekukan seluruh penambahan fitur baru selama Minggu 7; hanya perbaiki bug penghambat pemakaian |

### Gerbang Rilis Fase 1 (Akhir Minggu 7)
- [ ] Spreadsheet alokasi dan pencatatan cuti lama resmi dibekukan (*read-only*).
- [ ] Alokasikan **±4 jam/minggu** mulai Minggu 8 untuk dukungan operasional & perbaikan kecil tim.

---

## 3. Fase 2: Operasional Harian (Minggu 8–16)

> *Catatan Fleksibilitas:* Urutan blok 2-mingguan di bawah ini dapat ditukar pada awal Minggu 8 berdasarkan keluhan nyata paling sering dari 6 pengguna selama pemakaian Fase 1.

| Minggu | ID Backlog | Fokus Pekerjaan | Kriteria Selesai (*Exit Criteria*) |
|:---:|---|---|---|
| **M8–M9** | `F2-B1` | **Timesheet Mingguan & Timer:** Grid input `Task × Hari (Sen–Min)`, timer opsional pada task, komparasi `Estimasi vs Alokasi vs Aktual`, pengingat Jumat sore, alur `Submit -> Review Supervisor (Approve / Return)` | Anggota mengisi timesheet mingguan dalam `< 3 menit`; Supervisor menyetujui atau mengembalikan beserta komentar |
| **M10–M11** | `F2-B2` | **Multi-View & Saved URL Views:** Tampilan Kanban, List, Kalender (`FullCalendar` *lazy-loaded*), dan Timeline/Gantt sederhana; filter gabungan terikat ke URL & fitur *Saved Views* | Tautan URL view yang dibagikan ke rekan tim membuka tampilan dan kombinasi filter yang identik |
| **M12–M13** | `F2-B3` | **Approval Cuti, Saldo Cuti, Skill & Rebalancing DnD:** Alur persetujuan cuti berjenjang + pemotongan saldo cuti tahunan, penandaan *skill* & level user, pemindahan alokasi *drag-and-drop* dengan pratinjau utilisasi instan + *Undo* | Cuti yang disetujui langsung memotong kapasitas & memunculkan peringatan bila menabrak alokasi aktif |
| **M14–M15** | `F2-B4` | **Kolaborasi Task & Integrasi Chat:** Komentar task, `@mention`, lampiran file, task rutin berulang, template proyek, serta pengiriman notifikasi ke **1 aplikasi chat utama tim** (Slack / Teams / Telegram / WhatsApp API resmi) | Notifikasi chat tiba ke penerima yang tepat dan memiliki saklar aktif/nonaktif per pengguna |
| **M16** | `F2-B5` | **Buffer Kualitas & Evaluasi Fase 2:** Pembersihan bug, optimasi indeks/query lambat, panduan pengguna ringkas 2 halaman, survei kepuasan tim, dan rapat keputusan Fase 3 | `0` bug prioritas tinggi terbuka; timesheet tepat waktu `> 80%`; keputusan tertulis `LANJUT / POTONG / SELESAI` untuk Fase 3 |

---

## 4. Fase 3: Insight, Forecasting, Integrasi & AI (Minggu 17–24 — *Opsional*)

> **Syarat Masuk Fase 3:** Hanya kerjakan item di bawah yang memiliki permintaan nyata dari minimal 1 supervisor atau `>= 3` anggota tim pada evaluasi Minggu 16.

| Minggu | ID Backlog | Fokus Pekerjaan | Kriteria Selesai (*Exit Criteria*) |
|:---:|---|---|---|
| **M17–M18** | `F3-B1` | **Laporan & Ekspor Terjadwal:** Analitik utilisasi per orang/proyek, tren beban, akurasi estimasi, deteksi *overload* berturut-turut (`>= 3` minggu), ekspor CSV/Excel/PDF, email laporan Senin pagi otomatis ke supervisor | Supervisor tidak lagi menyusun laporan rekap mingguan secara manual |
| **M19–M20** | `F3-B2` | **Forecasting *What-If*:** Ruang simulasi skenario proyek baru, pergeseran jadwal, atau simulasi anggota cuti panjang untuk proyeksi kapasitas 3–6 bulan ke depan | Skenario dapat disimpan, dibandingkan berdampingan, dan dipromosikan menjadi alokasi resmi tanpa merusak data berjalan selama simulasi |
| **M21–M22** | `F3-B3` | **Integrasi Satu Arah (Maksimal 2 Alat):** Pilih maksimal 2 dari Jira, GitHub/GitLab, Google Calendar, atau Outlook; sinkronisasi terjadwal satu arah + halaman log kegagalan sinkronisasi | Data tersinkron otomatis sesuai jadwal dan error koneksi terbaca jelas oleh Admin tanpa membuka SSH |
| **M23–M24** | `F3-B4` | **Rekomendasi AI, Dwibahasa Penuh (`ID`/`EN`), Audit Search & Final Hardening:** Saran *assignee* (berdasar *skill* + kapasitas), estimasi awal, ringkasan mingguan (wajib persetujuan kebijakan LLM perusahaan + saklar *off* + minimalisasi PII), kelengkapan kamus `en.json`, pencarian `audit_logs`, dan uji *disaster recovery* penuh | Setiap saran AI memuat penjelasan alasan & dapat dimatikan Admin; restore penuh dari S3 terverifikasi end-to-end |

