# ADR-0003: Single-Image Multi-Stage Deployment di VPS dengan Coolify (Opsi A)

- **Status:** Accepted
- **Tanggal:** 2026-11-01
- **Pengambil Keputusan:** Rencana v1.3

## Konteks
Sebagai solo developer, setiap komponen tambahan di produksi melipatgandakan titik salah konfigurasi (CORS lintas subdomain, cookie `SameSite` tertolak browser, ketidakcocokan versi API antara frontend dan backend saat salah satu gagal deploy, serta dua langkah rollback). Kode sumber frontend (`web/`) dan backend (`server/`) tetap perlu dipisahkan secara bersih di repositori untuk keterbacaan dan fleksibilitas masa depan.

## Keputusan
1. Gunakan **Opsi A (Satu Image Docker Multi-Stage)** di [`deploy/Dockerfile`](../../deploy/Dockerfile):
   - **Stage 1 (`web-build`, `node:24-alpine`):** Menjalankan `npm ci && npm run build` pada folder `web/` untuk menghasilkan aset statis teroptimasi di `web/dist`.
   - **Stage 2 (`server-build`, `eclipse-temurin:25-jdk`):** Menyalin `web/dist` ke `server/src/main/resources/static/` lalu membangun fat JAR Spring Boot 4.1 via `./mvnw -B package -DskipTests` (pengujian dijalankan terpisah di GitHub Actions CI sebelum deploy).
   - **Stage 3 (`runtime`, `eclipse-temurin:25-jre`):** Menjalankan aplikasi sebagai user non-root dengan `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -Xmx512m -Duser.timezone=UTC"`.
2. Tambahkan handler SPA fallback ringan di Spring MVC agar navigasi langsung (*deep-link*) ke rute React (mis. `/proyek`, `/cuti`, `/beban-kerja?week=2026-W45`) tetap mengembalikan `index.html`, sedangkan `/api/**`, `/oauth2/**`, `/login/**`, dan `/actuator/**` ditangani oleh Spring.
3. Jalankan dua environment Coolify (`staging` dan `production`) di VPS minimal **2 vCPU / 4 GB RAM** dengan backup terjadwal harian PostgreSQL ke penyimpanan S3-compatible (**Cloudflare R2** sebagai default).

## Alternatif yang Dipertimbangkan
- **Opsi B (Dua Aplikasi Terpisah di Coolify: Static Site + API Container):** Memungkinkan redeploy UI tanpa merestart JVM, namun mengharuskan pengelolaan dua subdomain/routing proxy, konfigurasi CORS + cookie lintas origin, dan risiko *version skew* saat rilis skema API berubah.

## Konsekuensi & Trade-offs
- **Positif:** Nol masalah CORS/cookie sesi, satu domain HTTPS tunggal, satu artefak rollback atomik untuk UI + API sekaligus, dan konfigurasi Coolify sangat ringkas.
- **Negatif:** Perubahan teks kecil di UI tetap memicu build image gabungan (±2–3 menit dengan Docker layer cache). Karena `web/` dan `server/` tetap terpisah di repo, migrasi ke Opsi B kapan pun dibutuhkan tidak memerlukan perombakan kode.

