# ADR-0002: Akses Data Hibrida (Spring Data JPA + JdbcClient Native SQL PostgreSQL) dan Flyway Expand-Contract

- **Status:** Accepted *(Pilihan `JdbcClient` vs jOOQ ditandai sebagai Keputusan Default Builder)*
- **Tanggal:** 2026-11-01
- **Pengambil Keputusan:** Rencana v1.3 + Keputusan Default Builder

## Konteks
Beban komputasi utama Toppa Load bukan terletak pada jumlah request konkurensi, melainkan pada ketepatan ekspansi rentang tanggal alokasi per hari kerja (`generate_series`), pemotongan libur nasional dan cuti penuh/setengah hari, serta agregasi matriks Heatmap Orang × Minggu.

Spring Data JPA sangat produktif untuk CRUD entitas transaksional (`User`, `Project`, `Task`, `Allocation`, `Leave`, `Holiday`), tetapi kaku dan rawan menghasilkan masalah *N+1* untuk query deret tanggal PostgreSQL. Rencana v1.3 mengizinkan **jOOQ atau native SQL** untuk perhitungan kapasitas.

## Keputusan
1. **CRUD Transaksional:** Gunakan **Spring Data JPA** dengan entitas terisolasi di dalam paket `internal` masing-masing modul.
2. **Perhitungan Kapasitas & Heatmap *(Keputusan Default Builder)*:** Gunakan **`JdbcClient` bawaan Spring Framework 7 / Spring Boot 4.1 dengan Native SQL PostgreSQL (`generate_series`)** yang dipetakan langsung ke Java `record` immutable, tanpa menambahkan plugin code-generation Maven jOOQ di Fase 1.
   - *Pemicu evaluasi ulang ke jOOQ:* Jika pada Fase 3 (Laporan & Forecasting Dinamis) muncul lebih dari 5 kombinasi filter agregasi SQL dinamis yang sulit dipelihara dengan parameter `JdbcClient`, tambahkan jOOQ saat itu.
3. **Tanpa Tabel Cache Turunan:** Untuk 4–10 pengguna × 365 hari, ekspansi `generate_series` hanya memproses beberapa ribu baris di memori PostgreSQL (`< 15 ms` dengan indeks `(user_id, start_date, end_date)`). Hitung langsung saat dibaca agar tidak pernah terjadi inkonsistensi cache (*stale capacity*).
4. **Migrasi Skema & Pengujian:**
   - Kelola seluruh DDL memakai **Flyway** (`server/src/main/resources/db/migration/V*__*.sql`) dengan pola **Expand-Contract** (penambahan kolom bersifat opsional/memiliki default; penghapusan kolom lama ditunda ke rilis berikutnya) agar rollback kontainer di Coolify selalu aman.
   - Uji seluruh query kapasitas menggunakan **Testcontainers `2.0.3` (`org.testcontainers:testcontainers-postgresql`)** terhadap instance PostgreSQL 17 asli, bukan H2/HSQLDB, karena fungsi `generate_series` dan `JSONB` spesifik PostgreSQL.

## Alternatif yang Dipertimbangkan
- **JPA Murni + Hitung Deret Tanggal di Memori Java:** Mudah ditulis awal, tetapi rentan lambat saat rentang lintas bulan/tahun membesar dan menyulitkan agregasi langsung untuk laporan SQL Fase 3.
- **jOOQ Sejak Minggu 1:** Memberikan *type-safety* SQL yang sangat baik, namun mewajibkan tahapan *database-first codegen* di pipeline Maven/Docker multi-stage sejak hari pertama untuk skema yang baru terdiri dari 10 tabel.

## Konsekuensi & Trade-offs
- **Positif:** Build Docker multi-stage tetap cepat dan sederhana (tanpa perlu menyalakan Postgres sementara di tengah `docker build` hanya untuk jOOQ codegen), sementara query kapasitas tetap 100% eksplisit dan teruji terhadap Postgres nyata lewat Testcontainers di CI.
- **Negatif:** Perubahan nama kolom SQL tidak tertangkap saat kompilasi Java, sehingga **wajib** ditangkap oleh suite integration test Testcontainers pada setiap push.
