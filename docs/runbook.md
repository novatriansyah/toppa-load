# Runbook Operasional Satu Halaman — Toppa Load

Panduan darurat dan operasional rutin untuk mengelola **Toppa Load** di VPS + Coolify. Dirancang agar developer lain atau rekan IT dapat mengambil alih dalam 15 menit bila developer utama berhalangan.

---

## 1. Peta Infrastruktur Ringkas

- **Arsitektur:** 1 Kontainer Aplikasi (`toppa-load`, Java 25 + Spring Boot 4.1 + React Static SPA di port internal `8080`) + 1 Kontainer PostgreSQL 17 terkelola di Coolify.
- **Lingkungan di VPS yang Sama:**
  - `production`: Branch `main` (deploy via tombol manual di Coolify atau webhook terproteksi setelah CI hijau), database `toppaload_prod`.
  - `staging`: Branch `main` (auto-deploy saat CI hijau), database terpisah `toppaload_staging` (hanya berisi data sintetis).
- **Health Check Endpoint:** `GET /actuator/health` (harus mengembalikan `{"status":"UP"}`).
- **Batas Memori JVM:** `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -Xmx512m -Duser.timezone=UTC"`.

---

## 2. Prosedur Deploy Normal

1. Pastikan seluruh pengujian lulus di GitHub Actions pada branch `main` (`Maven Verify + Modulith + Testcontainers` dan `Frontend Lint/Build/Playwright`).
2. Periksa bahwa *auto-deploy* ke **Staging** di Coolify berhasil dan `https://staging-workload.<domain>/actuator/health` berstatus `UP`.
3. Buka Dashboard Coolify -> Project `Toppa Load` -> Environment `production` -> Application `toppa-load-app` -> klik **Deploy**.
4. Pantau log startup Coolify hingga muncul baris:
   - `Successfully applied N migrations to schema "public"` (Flyway)
   - `Started ToppaLoadApplication in X seconds`
5. Verifikasi login SSO dan buka layar utama **Heatmap Orang × Minggu**.

---

## 3. Prosedur Rollback Darurat (< 5 Menit)

Karena seluruh migrasi database wajib mematuhi pola **Expand-Contract** (migrasi baru tidak langsung menghapus/mengubah nama kolom yang masih dibaca versi aplikasi`N-1`):

1. Buka Dashboard Coolify -> `production` -> `toppa-load-app` -> tab **Deployments**.
2. Cari *commit/image* terakhir yang stabil -> klik **Redeploy / Rollback**.
3. Verifikasi `/actuator/health` kembali `UP`.
4. **Jika kegagalan disebabkan oleh data korup pada migrasi baru** (bukan sekadar bug kode): ikuti **Prosedur 4 (Restore Database)** menggunakan dump sebelum waktu deploy.

---

## 4. Prosedur Backup & Restore Database PostgreSQL

### Jadwal Backup Otomatis
- **Frekuensi:** Setiap hari pukul `01:30 WIB` (`18:30 UTC`) via fitur *Scheduled Database Backup* bawaan Coolify ke bucket S3 eksternal (Cloudflare R2 / Backblaze B2), ditambah Snapshot VPS mingguan.
- **Retensi:** 30 hari di S3.

### Langkah Uji Restore (Wajib Tiap Kuartal ke Staging) / Pemulihan Insiden
1. Unduh berkas backup terbaru (`.sql.gz` atau `.dmp`) dari bucket S3 atau tab **Backups** resource PostgreSQL di Coolify.
2. Untuk uji berkala, arahkan ke `toppaload_staging`. Untuk pemulihan produksi, **hentikan sementara** kontainer `toppa-load-app` produksi agar tidak ada koneksi tulis baru.
3. Jalankan restore melalui terminal VPS ke kontainer PostgreSQL target:
   ```bash
   # Contoh untuk custom format pg_dump (.dmp / .backup)
   docker exec -i <nama-container-postgres> pg_restore \
     -U toppaload --clean --if-exists --no-owner -d toppaload_prod < backup_terbaru.dmp

   # Contoh untuk plain SQL terkompresi (.sql.gz)
   gunzip -c backup_terbaru.sql.gz | docker exec -i <nama-container-postgres> \
     psql -U toppaload -d toppaload_prod
   ```
4. Nyalakan kembali kontainer `toppa-load-app` dan verifikasi tabel `flyway_schema_history` serta layar Heatmap.

---

## 5. Manajemen Pengguna (Menambah / Menonaktifkan Akun)

- **Menambah Pengguna Baru:**
  1. Login sebagai `ADMIN` -> buka menu **Pengaturan -> Pengguna & Akses**.
  2. Klik **Tambah Email Allowlist**, masukkan email Google/Microsoft kantor pengguna, nama lengkap, peran (`SUPERVISOR` atau `MEMBER`), jam kerja harian (default `8.0`), dan hari kerja aktif.
  3. Pengguna langsung dapat masuk lewat tombol **Masuk dengan SSO**.
- **Menonaktifkan Pengguna (Resign / Rotasi Tim):**
  1. Buka **Pengaturan -> Pengguna & Akses** -> ubah status akun menjadi **Nonaktif (`active = false`)** (*jangan hard-delete* agar histori `allocations`, `time_entries`, dan `audit_logs` tetap utuh).
  2. Sesi aktif pengguna otomatis ditolak pada request berikutnya.
- **Pemulihan Admin Terkunci (*Lockout Recovery*):**
  Variabel environment `APP_SECURITY_BOOTSTRAP_ADMIN_EMAIL` di Coolify selalu memastikan email admin utama otomatis diaktifkan kembali dengan peran `ADMIN` saat aplikasi *restart*.

---

## 6. Diagnostik Gangguan Cepat

| Gejala | Pemeriksaan Pertama | Tindakan Perbaikan |
|---|---|---|
| Aplikasi `502 Bad Gateway` di Coolify | Cek log kontainer `toppa-load-app` & `df -h` / `free -m` di VPS | Jika `OutOfMemoryError`, pastikan `-Xmx512m` terpasang; jika gagal konek DB, cek status kontainer Postgres |
| Login SSO gagal `redirect_uri_mismatch` | Cek `SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_*` & header proxy | Pastikan `server.forward-headers-strategy=framework` aktif agar Spring membaca `X-Forwarded-Proto: https` dari Coolify |
| Email notifikasi tidak terkirim | Cek log `NotificationScheduler` & kuota harian Resend/Brevo | Periksa variabel `SPRING_MAIL_*` di Coolify; baris gagal dapat dikirim ulang setelah SMTP pulih karena tabel notifikasi mencatat status kirim |
| Disk VPS penuh (`> 85%`) | Jalankan `docker system df` di VPS | Bersihkan image build lama: `docker image prune -af --filter "until=168h"` |
