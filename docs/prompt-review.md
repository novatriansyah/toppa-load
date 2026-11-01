# Prompt Review Lintas Model (Adversarial Plan & PRD Critique)

Sesuai **Bagian 1 dan Bagian 11 (Langkah Berikutnya #2)**, gunakan prompt di bawah ini untuk meminta model AI lain (mis. Claude Opus, GPT-5/o3, atau sesi `/council`) mengkritisi rencana dan PRD **Toppa Load** secara tajam sebelum atau bersamaan dengan Minggu 1, lalu catat keputusan apa yang diambil dan apa yang ditolak.

---

## 1. Prompt Siap Salin

````markdown
Anda adalah Principal Software Architect dan Product Engineering Lead yang sangat berpengalaman membimbing solo developer membangun aplikasi bisnis internal yang benar-benar selesai tepat waktu dan dipakai nyata oleh tim.

Tugas Anda adalah mengkritisi dokumen Rencana Pengembangan & PRD proyek "Toppa Load" yang saya lampirkan di bawah ini secara jujur, tajam, dan pragmatis (bukan memuji).

Konteks keras yang TIDAK boleh dilanggar dalam saran Anda:
- Pengguna nyata hanya 6–7 orang dalam 1 tim internal (4 member, 2 supervisor, 1 admin/developer), satu zona waktu (WIB).
- Dikerjakan oleh 1 developer (±30 jam efektif/minggu) yang sudah memiliki dasar Java dan menargetkan proyek ini juga relevan sebagai portfolio Java modern (Java 25 LTS + Spring Boot 4.1 + Spring Modulith + PostgreSQL + React/Vite di VPS 4 GB RAM dengan Coolify).
- Setiap saran penambahan komponen wajib dibayar dengan saran pemangkasan komponen lain yang setara agar jadwal Fase 1 (7 minggu) tidak meleset.

Tolong periksa dokumen saya dan jawab dalam 5 bagian berikut:

1. **Titik Rawan Jadwal Meleset (Schedule Killers):**
   Minggu mana di Fase 1 (Minggu 1–7) dan Fase 2 (Minggu 8–16) yang paling tidak realistis untuk 30 jam/minggu seorang diri? Apa yang harus dipotong atau disederhanakan sekarang sebelum menulis kode?

2. **Celah Logika Kapasitas & Model Data:**
   Uji aturan perhitungan kapasitas (`generate_series`, hari kerja per user, libur nasional, cuti penuh/setengah hari, alokasi confirmed vs tentative) dan skema 10 entitas. Kasus tepi nyata apa di kantor Indonesia yang akan membuat rumus atau query ini menghasilkan angka yang membingungkan supervisor?

3. **Jebakan Operasional Solo di VPS + Coolify + Spring Boot 4:**
   Apa risiko produksi paling berbahaya pada kombinasi 1 VPS 4 GB RAM + Coolify + Docker multi-stage + Flyway expand-contract + backup S3 yang belum tertutup di rencana ini?

4. **Risiko UX & Penolakan Tim (Adopsi 6 Orang):**
   Di bagian mana anggota tim atau supervisor kemungkinan besar akan malas mengisi data lalu diam-diam kembali ke spreadsheet? Perubahan UX konkret apa (maksimal 3 perubahan) yang paling menentukan adopsi?

5. **5 Keputusan Konkret (Ambil / Tolak):**
   Berikan tepat 5 rekomendasi perubahan terpenting, diurutkan dari dampak terbesar, masing-masing dengan format:
   - Masalah: ...
   - Usulan Perubahan: ...
   - Biaya jika diabaikan: ...

---
[TEMPEL ISI docs/prd.md DAN docs/superpowers/specs/2026-11-01-toppa-load-design.md DI SINI]
````

---

## 2. Log Keputusan Hasil Review (*Decision Log*)

Catat hasil kritik yang Anda terima di tabel ini agar enam bulan ke depan alasan menerima atau menolak sebuah saran tetap terdokumentasi:

| Tanggal | Sumber Model / Reviewer | Ringkasan Kritik / Usulan | Keputusan (`TERIMA` / `TOLAK` / `TUNDA`) | Alasan Singkat |
|---|---|---|---|---|
| 2026-11-01 | Audit Pra-Kick-Off Internal | `springdoc-openapi` v2.x gagal di Spring Boot 4 karena migrasi Jackson 3; Testcontainers 2.0 mengganti nama artefak `postgresql` menjadi `testcontainers-postgresql`. | **TERIMA** | Diverifikasi terhadap dokumentasi resmi Spring Boot 4.1.0 & Testcontainers 2.0.3; langsung dikunci di spesifikasi & rencana Minggu 1. |
| 2026-11-01 | Audit Pra-Kick-Off Internal | Memasang generator kode jOOQ di Minggu 1 untuk 10 tabel berisiko memperlambat setup CI/Docker solo developer dibanding `JdbcClient` Spring Boot 4 + SQL eksplisit teruji Testcontainers. | **TERIMA** | Gunakan JPA untuk CRUD + `JdbcClient` native SQL untuk query `generate_series` kapasitas di Fase 1 (ADR-0002); jOOQ tetap bisa ditambahkan bila query dinamis Fase 3 membutuhkannya. |
| *Isi nanti* | *Mis. Claude / GPT / Supervisor* | *...* | *...* | *...* |

