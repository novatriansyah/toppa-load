# Fase 1D (Minggu 6–7): Notifikasi Terjadwal Anti-Dobel, Ambang Utilisasi, Hardening & E2E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Menyelesaikan Fase 1 dengan konfigurasi ambang utilisasi dinamis, notifikasi *in-app*, email SMTP terjadwal anti-dobel & Microsoft Teams Webhook (Adaptive Cards), Dual SSO (Google + Microsoft Entra ID), impor CSV data proyek/alokasi awal untuk onboarding 6 pengguna, hardening keamanan OWASP, serta pengujian E2E Playwright.

**Architecture:** Pengaturan ambang utilisasi (`system_settings`) diekspos untuk diubah `ADMIN` dan langsung memengaruhi klasifikasi Heatmap & peringatan overload. Modul `notification` menggunakan kolom unik `dedup_key` di PostgreSQL (`ON CONFLICT (dedup_key) DO NOTHING`) untuk menjamin pemicu event maupun job `@Scheduled` harian/mingguan tidak pernah menggandakan notifikasi/email, lalu mengirim antrean `email_status = 'PENDING'` lewat `JavaMailSender` serta posting peringatan overload & deadline ke Microsoft Teams via `TeamsWebhookDispatcher` (Adaptive Cards). Autentikasi mendukung Dual SSO (Google + Microsoft Entra ID) dengan verifikasi allowlist email terpusat. Filter keamanan menambahkan header OWASP + rate-limiting per sesi/IP, dan Playwright memverifikasi alur penuh di browser nyata.

**Tech Stack:** Spring Boot `4.1.0` (`spring-boot-starter-mail`, `@EnableScheduling`), PostgreSQL 17, Testcontainers `2.0.3`, GreenMail / Mailpit test SMTP, React 19, Radix UI Popover, Playwright (`@playwright/test`).

**Spec:** `docs/superpowers/specs/2026-11-01-toppa-load-design.md`

## Global Constraints

- Pengiriman email terjadwal **wajib idempotent**: pemanggilan ulang scheduler pada hari/minggu yang sama atau setelah aplikasi restart tidak boleh mengirim email kedua untuk peristiwa yang sama (`dedup_key` unik).
- Pengguna dengan `email_notifications = FALSE` tetap menerima notifikasi *in-app*, tetapi `email_status` langsung ditandai `SKIPPED` tanpa memanggil server SMTP.
- Perubahan ambang `underutilized_threshold_pct` dan `overloaded_threshold_pct` wajib menolak kondisi `underutilized >= overloaded` atau nilai negatif (`400 ProblemDetail`) dan tercatat di `audit_logs`.
- Seluruh respons HTTP wajib menyertakan header keamanan dasar OWASP (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy: strict-origin-when-cross-origin`, Content-Security-Policy satu origin).
- Sebelum onboarding produksi Minggu 7, tidak boleh ada satu pun data karyawan nyata di file seed atau test fixture.

## Review Focus

- Dua eksekusi beruntun `NotificationScheduler.scanDueSoonAndOverloads()` pada tanggal WIB yang sama wajib hanya menghasilkan **1 baris `notifications` dan 1 pengiriman SMTP** per `(userId, event, periodKey)`.
- Kegagalan sementara koneksi SMTP pada 1 baris notifikasi wajib mencatat `email_status = 'FAILED'` dan pesan `email_error` yang terpotong aman (`<= 500` karakter) tanpa membatalkan pengiriman notifikasi pengguna lain dalam batch yang sama.
- Impor CSV onboarding proyek & alokasi awal yang memuat baris dengan email tidak dikenal pada `users` wajib menolak seluruh batch dalam satu transaksi (*all-or-nothing*) dan mengembalikan daftar nomor baris CSV yang bermasalah secara spesifik.
- Rate limiter pada endpoint mutasi/login tidak boleh memblokir polling normal `GET /api/v1/capacity/heatmap` tiap 30 detik dari 6 pengguna yang berada di belakang 1 IP publik kantor yang sama (NAT kantor!).
- Pengujian Playwright harus memverifikasi bahwa alur melihat Heatmap -> membuka rincian sel -> membuat alokasi baru dapat diselesaikan dan langsung memperbarui angka utilisasi di layar.

---

## Peta File Rencana Ini

- `server/src/main/java/com/toppaload/identity/SystemSettingsDto.java`, `SystemSettingsReader.java`, `internal/SystemSettingsController.java` — Pembacaan & pengubahan ambang utilisasi oleh Admin.
- `server/src/main/java/com/toppaload/notification/NotificationPublisher.java`, `NotificationType.java` — Kontrak publik pemicu notifikasi.
- `server/src/main/java/com/toppaload/notification/internal/NotificationRepository.java`, `SmtpEmailDispatcher.java`, `TeamsWebhookDispatcher.java`, `WorkloadAlertScheduler.java`, `NotificationController.java` — Deduplikasi `dedup_key`, pengiriman SMTP, integrasi Microsoft Teams Webhook (Adaptive Cards), job H-2 deadline & peringatan overload mingguan, serta API lonceng *in-app*.
- `web/src/features/auth/LoginPage.tsx` — Tombol Dual SSO resmi: Masuk dengan Google (`/oauth2/authorization/google`) & Masuk dengan Microsoft (`/oauth2/authorization/microsoft`).
- `server/src/main/java/com/toppaload/allocation/internal/AllocationCsvImportService.java`, `AllocationCsvImportController.java` — Utilitas impor CSV proyek/alokasi aktif untuk migrasi dari spreadsheet saat onboarding Minggu 7.
- `server/src/main/java/com/toppaload/shared/OwaspHeadersAndRateLimitConfig.java` — Header keamanan CSP/Frame/Nosniff & rate-limiter mutasi ramah NAT kantor.
- `web/src/features/notifications/NotificationBellPopover.tsx` — Lonceng *in-app* di `AppShell` dengan jumlah belum dibaca & aksi *Tandai Semua Dibaca*.
- `web/src/features/settings/ThresholdsAndImportSection.tsx` — Form pengaturan ambang utilisasi (`60%`/`100%`) & unggah CSV onboarding dengan laporan validasi per baris.
- `web/e2e/phase1-core-flows.spec.ts`, `web/playwright.config.ts` — Suite E2E Playwright untuk alur kritis Fase 1.

---

### Task 1: Pengaturan Ambang Utilisasi Dinamis (`system_settings`) & Integrasi ke `CapacityQueryService`

**Files:**
- Create: `server/src/main/java/com/toppaload/identity/SystemSettingsDto.java`, `SystemSettingsReader.java`
- Create: `server/src/main/java/com/toppaload/identity/internal/SystemSettingsController.java`
- Modify: `server/src/main/java/com/toppaload/capacity/internal/JdbcCapacityQueryRepository.java`
- Test: `server/src/test/java/com/toppaload/identity/SystemSettingsIT.java`

**Interfaces:**
- Consumes: `com.toppaload.audit.AuditRecorder`, tabel `system_settings`
- Produces:
  - `record SystemSettingsDto(BigDecimal underutilizedThresholdPct, BigDecimal overloadedThresholdPct, String workTimezone)`
  - `interface SystemSettingsReader { SystemSettingsDto currentSettings(); }`
  - `GET /api/v1/settings`, `PUT /api/v1/settings` (khusus `ADMIN`)

- [ ] **Step 1: Tulis failing integration test `SystemSettingsIT`**

Uji bahwa:
1. `PUT /api/v1/settings` oleh `ADMIN` mengubah ambang menjadi `50.0` dan `95.0`, tercatat di `audit_logs`, dan pemanggilan `GET /api/v1/capacity/heatmap` untuk alokasi `96%` langsung berubah dari `OPTIMAL` menjadi `OVERLOADED`.
2. `PUT /api/v1/settings` dengan `underutilizedThresholdPct = 100.0` dan `overloadedThresholdPct = 90.0` ditolak `400 ProblemDetail`.
3. `SUPERVISOR` dan `MEMBER` ditolak `403` saat `PUT /api/v1/settings`.

- [ ] **Step 2: Jalankan test untuk memverifikasi gagal**

Run: `cd server && ./mvnw.cmd test -Dtest=SystemSettingsIT`
Expected: FAIL

- [ ] **Step 3: Implementasikan `SystemSettingsReader`, `SystemSettingsController`, dan hubungkan ke `CapacityQueryService`**

Pastikan nilai default tetap `60.0` dan `100.0` pada database baru sehingga `CapacityGoldenSuiteIT` tetap lulus tanpa perubahan.

- [ ] **Step 4: Jalankan `SystemSettingsIT` dan `CapacityGoldenSuiteIT`**

Run: `cd server && ./mvnw.cmd test -Dtest=SystemSettingsIT,CapacityGoldenSuiteIT,ModularityTests`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(settings): add configurable utilization thresholds wired to capacity classifier"
```

---

### Task 2: Modul `notification` — Lonceng In-App, Email SMTP & Microsoft Teams Webhook Terjadwal Anti-Dobel (`dedup_key`)

**Files:**
- Create: `server/src/main/java/com/toppaload/notification/NotificationType.java`, `NotificationPublisher.java`
- Create: `server/src/main/java/com/toppaload/notification/internal/NotificationRepository.java`, `SmtpEmailDispatcher.java`, `TeamsWebhookDispatcher.java`, `WorkloadAlertScheduler.java`, `NotificationController.java`
- Modify: `server/pom.xml`, `server/src/main/java/com/toppaload/allocation/internal/AllocationService.java`
- Test: `server/src/test/java/com/toppaload/notification/NotificationIdempotencyIT.java`

**Interfaces:**
- Consumes: `com.toppaload.capacity.CapacityQueryService`, `com.toppaload.identity.UserDirectory`, `com.toppaload.shared.WibClock`, `JavaMailSender`
- Produces:
  - `NotificationPublisher.publishIfAbsent(UUID userId, NotificationType type, String title, Map<String, Object> payload, String dedupKey)`
  - `@Scheduled` `WorkloadAlertScheduler` (cek H-2 due date task belum selesai & peringatan overload minggu berjalan/depan ke Supervisor via Email & Microsoft Teams Webhook)
  - `GET /api/v1/notifications`, `POST /api/v1/notifications/{id}/read`, `POST /api/v1/notifications/read-all`

- [ ] **Step 1: Tambahkan `spring-boot-starter-mail` di `server/pom.xml` dan tulis failing integration test `NotificationIdempotencyIT`**

Uji 5 skenario penting:
1. Saat alokasi baru dibuat, 1 notifikasi `ALLOCATION_ASSIGNED` terbuat dan terkirim via `JavaMailSender` mock/test.
2. Menjalankan `workloadAlertScheduler.runDailyChecks()` **dua kali berturut-turut** di tanggal WIB yang sama untuk user yang overload dan task H-2 deadline hanya membuat **tepat 1 notifikasi** dan memanggil `JavaMailSender.send(...)` **tepat 1 kali** berkat `ON CONFLICT (dedup_key) DO NOTHING`.
3. User dengan `emailNotifications = false` tetap memiliki baris notifikasi *in-app* namun `email_status = 'SKIPPED'` dan `0` panggilan SMTP.
4. Jika `JavaMailSender` melempar `MailSendException` untuk User A, baris User A menjadi `FAILED` dengan `email_error` tercatat, sedangkan email User B tetap terkirim (`SENT`).
5. Jika `app.notification.teams-webhook-url` terisi, `TeamsWebhookDispatcher` memposting payload format Adaptive Cards ke webhook Microsoft Teams dengan ringkasan overload & deadline.

- [ ] **Step 2: Jalankan test untuk memverifikasi gagal**

Run: `cd server && ./mvnw.cmd test -Dtest=NotificationIdempotencyIT`
Expected: FAIL

- [ ] **Step 3: Implementasikan `NotificationRepository` (`INSERT ... ON CONFLICT (dedup_key) DO NOTHING`), `SmtpEmailDispatcher`, `TeamsWebhookDispatcher`, `WorkloadAlertScheduler`, dan `NotificationController`**

Aktifkan `@EnableScheduling` pada konfigurasi non-test (atau kondisional properti `app.scheduling.enabled=true`) agar scheduler dapat dipanggil deterministik saat integration test.

- [ ] **Step 4: Jalankan test `NotificationIdempotencyIT` & `ModularityTests`**

Run: `cd server && ./mvnw.cmd test -Dtest=NotificationIdempotencyIT,ModularityTests`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(notification): add in-app notifications, idempotent scheduled SMTP alerts, and Microsoft Teams webhook"
```

---

### Task 3: Impor CSV Onboarding Spreadsheet + Hardening OWASP Headers & Rate Limiter Ramah NAT

**Files:**
- Create: `server/src/main/java/com/toppaload/allocation/internal/AllocationCsvImportService.java`, `AllocationCsvImportController.java`
- Create: `server/src/main/java/com/toppaload/shared/OwaspHeadersAndRateLimitConfig.java`
- Modify: `server/src/main/java/com/toppaload/identity/internal/SecurityConfig.java`
- Test: `server/src/test/java/com/toppaload/allocation/AllocationCsvImportIT.java`
- Test: `server/src/test/java/com/toppaload/shared/SecurityHardeningIT.java`

**Interfaces:**
- Consumes: `com.toppaload.identity.UserDirectory`, `com.toppaload.project.ProjectLookup` (`findOrCreateActiveByCode`), `AllocationService`, `com.toppaload.audit.AuditRecorder`
- Produces:
  - `POST /api/v1/allocations/import-csv` (`SUPERVISOR`/`ADMIN`, format kolom: `project_code,project_name,user_email,start_date,end_date,hours_per_day,type`) dengan validasi transaksional *all-or-nothing* + laporan baris error
  - Header keamanan HTTP (`X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy`, `Content-Security-Policy`) & pembatas laju mutasi per-user/IP (tanpa mencekik `GET` polling 6 user satu IP kantor)

- [ ] **Step 1: Tulis failing integration tests `AllocationCsvImportIT` dan `SecurityHardeningIT`**

Uji bahwa:
1. CSV dengan 4 baris valid dan 1 baris berisi `email_tidak_ada@kantor.id` di baris ke-4 melakukan rollback seluruh batch (`0` alokasi tersimpan) dan mengembalikan `400 ProblemDetail` yang memuat `"Baris 4: email 'email_tidak_ada@kantor.id' belum terdaftar di allowlist"`.
2. CSV valid sepenuhnya mengimpor proyek/alokasi aktif dan mencatat aksi `IMPORT` di `audit_logs`.
3. Respons API menyertakan `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, dan `Content-Security-Policy`, sementara 100 request `GET /api/v1/capacity/heatmap` dari 1 IP kantor tetap `200 OK`.

- [ ] **Step 2: Jalankan test untuk memverifikasi gagal**

Run: `cd server && ./mvnw.cmd test -Dtest=AllocationCsvImportIT,SecurityHardeningIT`
Expected: FAIL

- [ ] **Step 3: Implementasikan `AllocationCsvImportService`, `AllocationCsvImportController`, serta konfigurasi header keamanan & rate limiter**

Gunakan token bucket ringan di memori (`ConcurrentHashMap`) berbasis `actorId` (jika login) atau IP (hanya untuk endpoint unauthenticated `/oauth2/**` & `/login/**`) sehingga 6 rekan satu kantor di balik satu router Wi-Fi tidak saling mengunci.

- [ ] **Step 4: Jalankan seluruh suite test backend**

Run: `cd server && ./mvnw.cmd test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(security,import): add atomic CSV onboarding importer, OWASP headers, and NAT-safe rate limiting"
```

---

### Task 4: UI Lonceng Notifikasi, UI Ambang/Impor CSV, dan Suite E2E Playwright Fase 1

**Files:**
- Create: `web/src/features/notifications/NotificationBellPopover.tsx`
- Create: `web/src/features/settings/ThresholdsAndImportSection.tsx`
- Create: `web/playwright.config.ts`, `web/e2e/phase1-core-flows.spec.ts`
- Modify: `web/src/app/AppShell.tsx`, `web/src/features/settings/SettingsPage.tsx`, `web/src/i18n/locales/id.json`, `web/src/i18n/locales/en.json`, `web/package.json`

**Interfaces:**
- Consumes: `/api/v1/notifications`, `/api/v1/settings`, `/api/v1/allocations/import-csv`, profil backend `e2e-test` (login sesi uji sintetis khusus lokal/CI untuk E2E tanpa kredensial Google pribadi)
- Produces:
  - Lonceng notifikasi dengan lencana angka belum dibaca di `AppShell`
  - Seksi pengaturan ambang utilisasi & unggah CSV onboarding di `SettingsPage`
  - Suite Playwright 4 alur E2E inti Fase 1: (1) Login & guard akses, (2) Buat Proyek + quick-add Task, (3) Buat Alokasi & verifikasi sel Heatmap berubah ke `OVERLOADED` dengan indikator aksesibel, (4) Catat Cuti & verifikasi kapasitas bersih berkurang serta `Konflik Cuti` muncul.

- [ ] **Step 1: Tambahkan `@playwright/test` dan tulis spesifikasi `web/e2e/phase1-core-flows.spec.ts`**

Tulis 4 test E2E yang menggunakan data sintetis (mis. `Rina Pratama`, `Dimas Saputra` — nol data karyawan asli) dan memeriksa baik akurasi angka `.num-tabular` maupun navigasi keyboard.

- [ ] **Step 2: Implementasikan `NotificationBellPopover.tsx`, `ThresholdsAndImportSection.tsx`, dan aktifkan filter autentikasi uji khusus profil `@Profile("e2e")` di backend untuk CI Playwright**

Pastikan profil `e2e` **hanya** aktif saat dijalankan eksplisit oleh job E2E dan tidak pernah aktif di `prod`/`staging`.

- [ ] **Step 3: Jalankan unit test frontend, build produksi, seluruh test backend, dan Playwright E2E**

Run:
```bash
cd server && ./mvnw.cmd verify
cd ../web && npm.cmd test -- --run && npm.cmd run build && npx.cmd playwright test
```
Expected: Seluruh unit test, `ModularityTests`, `CapacityGoldenSuiteIT` (10/10), dan 4 alur Playwright E2E lulus hijau.

- [ ] **Step 4: Periksa Daftar Cek Kualitas Layar Bagian 6 PRD pada seluruh 4 halaman Fase 1**

Verifikasi tidak ada elemen dekoratif/KPI hiasan, kontras WCAG AA terpenuhi, angka rata kanan tabular, seluruh string di `id.json` & `en.json` sinkron, dan Docker image final berhasil dibangun (`docker build -f deploy/Dockerfile .`).

- [ ] **Step 5: Commit**

```bash
git add server/ web/
git commit -m "feat(phase1): add notification UI, CSV import UI, and Playwright E2E suite for Phase 1 release"
```
