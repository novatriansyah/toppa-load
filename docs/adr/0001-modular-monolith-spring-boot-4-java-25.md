# ADR-0001: Modular Monolith dengan Java 25 LTS, Spring Boot 4.1, dan Spring Modulith 2.1

- **Status:** Accepted
- **Tanggal:** 2026-11-01
- **Pengambil Keputusan:** Rencana v1.3 + Konfirmasi Versi Pra-Kick-Off

## Konteks
Toppa Load melayani satu tim internal (6–7 pengguna aktif) namun memiliki aturan bisnis yang kaya: RBAC tiga peran, ekspansi alokasi harian, pemotongan hari libur & cuti penuh/setengah hari, pencatatan audit log, job notifikasi terjadwal, serta rencana bertahap hingga timesheet dan laporan. Seluruh sistem dibangun dan dirawat sendirian oleh **satu developer** yang sudah menguasai dasar Java dan menargetkan hasil proyek sebagai portfolio Java modern di VPS 4 GB RAM dengan Coolify.

Memulai dengan Spring Boot 3.5 (dukungan OSS berakhir 30 Juni 2026) atau Spring Boot 4.0 (dukungan OSS berakhir 31 Desember 2026) akan langsung membebani proyek dengan kewajiban upgrade mayor di tengah Fase 2.

## Keputusan
1. Gunakan **Java 25 LTS** dengan **Spring Boot `4.1.0`** dan **Maven Wrapper (`mvnw`)** dalam satu modul build (`server/`).
2. Strukturkan paket aplikasi sebagai **Modular Monolith** menggunakan **Spring Modulith `2.1.0`** di bawah root `com.toppaload`:
   - `shared`: Tipe nilai bersama, waktu/kalender WIB, error RFC 7807 (`ProblemDetail`), utilitas keamanan baca-konteks.
   - `identity`: Pengguna, allowlist SSO OIDC, RBAC, pengaturan jam/hari kerja.
   - `project`: Proyek dan Task.
   - `allocation`: Alokasi jam kerja harian (`CONFIRMED` / `TENTATIVE`) dan deteksi tumpang tindih.
   - `leave`: Hari libur nasional/perusahaan (`Holiday`) dan pengajuan/pencatatan cuti (`Leave`).
   - `capacity`: Mesin perhitungan kapasitas bersih, utilisasi, dan matriks Heatmap Orang × Minggu.
   - `timesheet` *(mulai aktif Fase 2, kerangka paket disiapkan bersih)*: Pencatatan jam aktual & timer.
   - `notification`: Notifikasi *in-app* dan pengiriman email SMTP terjadwal (`@Scheduled`) dengan idempotensi.
   - `report` *(mulai aktif Fase 3)*: Analitik, ekspor, dan proyeksi.
   - `audit`: Pencatatan jejak audit `JSONB` lintas entitas penting.
3. Wajibkan pengujian arsitektur `ApplicationModules.of(ToppaLoadApplication.class).verify()` di setiap eksekusi CI agar tidak ada akses langsung ke sub-paket `internal` lintas modul.
4. Gunakan `springdoc-openapi-starter-webmvc-ui` versi **`3.1.x`** yang kompatibel penuh dengan Spring Boot 4.1 dan Jackson 3.

## Alternatif yang Dipertimbangkan
- **Go (二进制 tunggal):** Pemakaian memori sangat kecil (±30 MB), tetapi membutuhkan waktu belajar bahasa dan merakit sendiri ekosistem security/OIDC, migrasi, audit, serta laporan yang sudah matang di Spring.
- **Rust (Axum/Actix):** Performa ekstrem yang sama sekali tidak terpakai untuk 6 pengguna, dengan kurva belajar *ownership/async* yang mengancam jadwal solo 24 minggu.
- **Microservices / Multi-Modul Maven Berlapis:** Menambah biaya konfigurasi, orkestrasi kontainer, dan latensi build tanpa manfaat nyata pada skala 1 developer.

## Konsekuensi & Trade-offs
- **Positif:** Kecepatan pengembangan tinggi, ekosistem keamanan & penjadwalan sangat matang, batas modul terjaga rapi tanpa kompleksitas jaringan microservice, dan sangat kuat untuk nilai portfolio enterprise Java 2026+.
- **Negatif:** Jejak memori JVM lebih besar dibanding Go; dimitigasi dengan membatasi heap `-Xmx512m` pada VPS 4 GB RAM.
