# Fase 3 (Minggu 17–24): Insight, Forecasting, Integrasi Microsoft Outlook & Rekomendasi Kapasitas AI

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menyelesaikan Fase 3 Toppa Load dengan kemampuan analitik dan prediksi kapasitas tingkat lanjut: laporan utilisasi otomatis & ekspor multi-format (CSV, Excel, PDF), simulasi proyeksi kapasitas *What-If*, integrasi kalender Microsoft Outlook & Google Calendar via feed `.ics` terproteksi token, dan modul asisten rekomendasi kapasitas AI (berbasis aturan workload + LLM opsional) dengan kontrol privasi penuh.

**Architecture:** Spring Modulith 2.1 menambahkan modul-modul analisis & integrasi:
- `com.toppaload.reporting`: Agregasi analitik utilisasi historis, deteksi overload berulang (>= 3 minggu), ekspor CSV/Excel (Apache POI) / PDF, pengiriman ringkasan email Senin pagi.
- `com.toppaload.forecasting`: Ruang kerja *What-If* (`scenarios`, `scenario_allocations`) yang mensimulasikan penambahan proyek/karyawan baru tanpa memengaruhi tabel kapasitas produksi, dengan fitur *Promote Scenario to Production*.
- `com.toppaload.calendar`: Endpoint publik terproteksi token unik (`/api/v1/calendar/feed/{userToken}.ics`) yang menghasilkan feed iCalendar standar RFC 5545 untuk di-subscribe langsung di Microsoft Outlook dan Google Calendar.
- `com.toppaload.ai`: Mesin rekomendasi pembagian beban kerja optimal (*workload balancing*) dan saran penugasan (*assignee suggestion*) berdasarkan keahlian (*skills*) dan ketersediaan jam kerja bersih tim, dengan saklar on/off di pengaturan dan tanpa membocorkan PII rahasia.

**Tech Stack:** Java 25, Spring Boot 4.1.0, PostgreSQL 17 (Flyway `V4`), Apache POI / OpenCSV, `biweekly` (iCalendar RFC 5545 engine), React 19, Chart.js / Recharts.

---

## Peta File & Skema Fase 3

### Database Migration: `server/src/main/resources/db/migration/V4__phase3_forecasting_calendar_schema.sql`
- `calendar_feed_tokens`: `(id, user_id, secret_token, scope, is_active, created_at, last_accessed_at)`
- `scenarios`: `(id, name, description, created_by, status, created_at, updated_at)`
- `scenario_allocations`: `(id, scenario_id, user_id, project_name, start_date, end_date, hours_per_day)`
- `scheduled_reports`: `(id, report_type, frequency, recipient_emails, last_run_at, next_run_at)`

---

### Task 1: Laporan Utilisasi & Ekspor Multi-Format (CSV, Excel, PDF)
- Modul `reporting`:
  - `GET /api/v1/reports/utilization`: Rekap utilisasi tim dalam rentang waktu tertentu.
  - `GET /api/v1/reports/export?format=csv|xlsx|pdf`: Download file laporan berformat padat dan rapi.
  - Job terjadwal `@Scheduled` Senin pagi: Rekap mingguan otomatis terkirim via email ke Supervisor/Admin.
- Frontend: `web/src/features/reports/ReportsPage.tsx` (`/laporan`), Grafik tren utilisasi & tombol unduh cepat.

### Task 2: Simulasi Forecasting *What-If* Kapasitas
- Modul `forecasting`:
  - `POST /api/v1/scenarios` (Buat skenario simulasi baru)
  - `POST /api/v1/scenarios/{id}/simulate`: Hitung proyeksi utilisasi 3–6 bulan ke depan jika skenario diterapkan.
  - `POST /api/v1/scenarios/{id}/promote`: Promosikan seluruh alokasi simulasi menjadi alokasi riil di modul `allocation`.
- Frontend: `web/src/features/forecasting/ForecastingPage.tsx` (`/simulasi`), Slider kapasitas & grafik komparasi *Baseline vs Simulated*.

### Task 3: Integrasi Kalender Microsoft Outlook & Google Calendar (.ics Feed)
- Modul `calendar`:
  - `POST /api/v1/calendar/token`: Generate link langganan kalender privat unik per user.
  - `GET /api/v1/calendar/feed/{token}.ics`: Menghasilkan stream RFC 5545 iCalendar (`VEVENT` untuk alokasi proyek & cuti).
  - Verifikasi: Pengguna dapat meng-copy link di menu Profil dan menempelkannya di Microsoft Outlook ("Add Calendar -> Subscribe from web") atau Google Calendar ("From URL").
- Frontend: Dialog "Sinkronisasi Kalender Microsoft Outlook / Google" di menu profil dan drawer alokasi.

### Task 4: Rekomendasi Kapasitas AI & Audit Hardening
- Modul `ai`:
  - Rekomendasi anggota tim optimal untuk task baru:
    - Kriteria: Kecocokan skill + kapasitas jam luang terbesar pada rentang tanggal task.
  - Saklar konfigurasi on/off di `SystemSettings`.
  - Penjelasan transparan: Tiap saran memuat alasan terukur (misal: "Budi memiliki 16 jam luang dan keahlian Spring Boot").
- Verifikasi Final:
  - Eksekusi penuh uji regresi backend & frontend.
  - Verifikasi build Docker image rilis produksi.

