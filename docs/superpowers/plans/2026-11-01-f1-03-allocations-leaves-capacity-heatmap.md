# Fase 1C (Minggu 4–5): Libur, Cuti, Alokasi, Mesin Kapasitas SQL & Layar Utama Heatmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Membangun modul Libur Nasional/Cuti, modul Alokasi (`CONFIRMED`/`TENTATIVE`), mesin perhitungan kapasitas bersih & utilisasi berbasis PostgreSQL `generate_series` yang lulus 100% pada 10 kasus uji kanonik (`TC-01`..`TC-10`), serta layar utama **Heatmap Orang × Minggu** yang tampil `< 1 detik`.

**Architecture:** Modul `leave` mengelola kalender `holidays` (termasuk impor CSV/JSON hari libur Indonesia) dan pencatatan `leaves` penuh/setengah hari. Modul `allocation` mengelola penugasan jam harian user ke proyek/task. Modul `capacity` mengeksekusi query `generate_series` langsung lewat `JdbcClient` terhadap PostgreSQL 17 tanpa tabel cache turunan, lalu `CapacityClassifier` menentukan status utilisasi aksesibel untuk dirender di grid padat `HeatmapPage` dengan polling TanStack Query 30 detik.

**Tech Stack:** Spring Boot `4.1.0`, Spring Data JPA + `JdbcClient` Native SQL (`generate_series`), PostgreSQL 17, Testcontainers `2.0.3`, React 19, TanStack Query v5 (`refetchInterval: 30_000`), Radix UI Drawer/Tooltip, Tailwind CSS v4.

**Spec:** `docs/superpowers/specs/2026-11-01-toppa-load-design.md` · `docs/prd.md` (Bagian 7)

## Global Constraints

- Perhitungan kapasitas dan utilisasi wajib mengikuti seluruh rumus dan 10 kasus uji kanonik di `docs/prd.md` Bagian 7 (`TC-01` s/d `TC-10`) secara eksak.
- Alokasi disimpan dalam satuan `hours_per_day` (`NUMERIC(4,2)`), bukan persentase; persentase hanya dihitung untuk tampilan periode.
- Alokasi `TENTATIVE` wajib dipisahkan dari utilisasi `CONFIRMED` utama secara default, dan baru digabungkan bila parameter `includeTentative=true` diaktifkan pengguna.
- Status kapasitas pada UI **tidak boleh hanya mengandalkan warna**: wajib menampilkan angka persentase tabular rata kanan + label/ikon + pola arsir CSS khusus pada sel `OVERLOADED` (WCAG AA & ramah buta warna).
- Layar utama `HeatmapPage` tidak boleh mengimpor library grafik/kalender berat di bundle utamanya agar waktu muat `< 1 detik`.

## Review Focus

- Hari Libur Nasional yang jatuh pada hari kerja biasa wajib menurunkan kapasitas bersih **sekaligus** melewati beban alokasi pada tanggal tersebut (`TC-06`: `32 / 32j = 100% OPTIMAL`), sedangkan Cuti Pribadi (`APPROVED`) menurunkan kapasitas bersih **tanpa** menghapus beban alokasi aktif agar konflik cuti langsung terlihat (`TC-07`: `40 / 32j = 125% OVERLOADED`, `hasLeaveConflict = true`).
- Minggu di mana kapasitas bersih user `0.0j` (mis. cuti penuh Senin–Jumat) namun masih terdapat alokasi `> 0j` tidak boleh menyebabkan *division by zero* SQL/Java dan wajib berstatus `OVERLOADED` dengan `utilizationPct = null` dan `hasLeaveConflict = true` (`TC-09`).
- Cuti setengah hari (`HALF_AM` atau `HALF_PM`) dengan rentang multi-hari (`startDate != endDate`) wajib ditolak `400 ProblemDetail` dengan pesan yang meminta pengguna memilih satu tanggal spesifik untuk cuti setengah hari.
- Impor CSV/JSON Libur Nasional yang dijalankan dua kali untuk tahun yang sama wajib bersifat *idempotent* (`ON CONFLICT` update nama libur pada `(holiday_date, scope)` aktif), bukan gagal setengah jalan atau menggandakan hari libur.
- `MEMBER` hanya boleh menyimpan alokasi bertipe `TENTATIVE` untuk `userId` miliknya sendiri; percobaan `MEMBER` membuat alokasi `CONFIRMED` atau alokasi untuk rekan lain wajib ditolak `403 Forbidden`.

---

## Peta File Rencana Ini

- `server/src/main/java/com/toppaload/leave/LeavePortion.java`, `LeaveType.java`, `HolidayScope.java` — Tipe publik modul `leave`.
- `server/src/main/java/com/toppaload/leave/internal/HolidayEntity.java`, `LeaveEntity.java`, `HolidayService.java`, `LeaveService.java`, `HolidayController.java`, `LeaveController.java` — CRUD & impor idempotent libur + cuti penuh/setengah hari.
- `server/src/main/java/com/toppaload/allocation/AllocationType.java`, `AllocationSummary.java` — Tipe publik modul `allocation`.
- `server/src/main/java/com/toppaload/allocation/internal/AllocationEntity.java`, `AllocationRepository.java`, `AllocationService.java`, `AllocationController.java` — CRUD alokasi teraudit dengan aturan RBAC `CONFIRMED` vs `TENTATIVE`.
- `server/src/main/java/com/toppaload/capacity/Granularity.java`, `UtilizationStatus.java`, `CapacityCell.java`, `HeatmapResponse.java`, `CapacityQueryService.java` — Kontrak publik modul `capacity`.
- `server/src/main/java/com/toppaload/capacity/internal/CapacityClassifier.java`, `JdbcCapacityQueryRepository.java`, `CapacityController.java` — Mesin SQL `generate_series` & endpoint `/api/v1/capacity/heatmap` + `/breakdown` + `/api/v1/allocations/preview`.
- `web/src/features/heatmap/HeatmapPage.tsx`, `HeatmapGrid.tsx`, `HeatmapStatusCell.tsx`, `CellBreakdownDrawer.tsx` — Layar utama matriks Orang × Periode (zoom Hari/Minggu/Bulan) & drawer rincian 1-klik.
- `web/src/features/allocations/AllocationFormDrawer.tsx` — Form alokasi 1-klik dengan *live preview* dampak utilisasi & peringatan overlap/cuti.
- `web/src/features/leaves/LeavesAndHolidaysPage.tsx`, `HolidayImportModal.tsx`, `LeaveFormModal.tsx` — Halaman manajemen cuti & impor libur nasional Indonesia.

---

### Task 1: Modul `leave` — CRUD & Impor Idempotent Libur Nasional + Pencatatan Cuti Penuh/Setengah Hari

**Files:**
- Create: `server/src/main/java/com/toppaload/leave/LeavePortion.java`, `LeaveType.java`, `HolidayScope.java`
- Create: `server/src/main/java/com/toppaload/leave/internal/HolidayEntity.java`, `LeaveEntity.java`, `HolidayRepository.java`, `LeaveRepository.java`, `HolidayService.java`, `LeaveService.java`, `HolidayController.java`, `LeaveController.java`
- Test: `server/src/test/java/com/toppaload/leave/LeaveAndHolidayIT.java`

**Interfaces:**
- Consumes: `com.toppaload.identity.UserDirectory`, `com.toppaload.audit.AuditRecorder`
- Produces:
  - `GET /api/v1/holidays?year=2026`, `POST /api/v1/holidays`, `POST /api/v1/holidays/import` (menerima daftar `{holidayDate, name, scope}` atau teks CSV `tanggal,nama,cakupan`), `DELETE /api/v1/holidays/{id}`
  - `GET /api/v1/leaves?from=&to=&userId=`, `POST /api/v1/leaves`, `DELETE /api/v1/leaves/{id}`

- [ ] **Step 1: Tulis failing integration test `LeaveAndHolidayIT`**

Uji di `LeaveAndHolidayIT`:
1. `POST /api/v1/holidays/import` mengimpor daftar libur nasional Indonesia, dan saat dipanggil ulang dengan tanggal sama memperbarui nama tanpa error duplikat.
2. `MEMBER` dapat mencatat cuti `FULL` maupun `HALF_AM` untuk dirinya sendiri (otomatis `APPROVED` di Fase 1), tetapi tertolak `403` saat mencatat cuti untuk user lain.
3. Cuti `HALF_PM` dengan `startDate = 2026-11-04` dan `endDate = 2026-11-05` ditolak `400 ProblemDetail`.

- [ ] **Step 2: Jalankan test untuk memverifikasi gagal**

Run: `cd server && ./mvnw.cmd test -Dtest=LeaveAndHolidayIT`
Expected: FAIL

- [ ] **Step 3: Implementasikan `HolidayService`, `LeaveService`, `HolidayController`, dan `LeaveController`**

Catat setiap penambahan, impor, dan soft-delete ke `AuditRecorder`.

- [ ] **Step 4: Jalankan test `LeaveAndHolidayIT` & `ModularityTests`**

Run: `cd server && ./mvnw.cmd test -Dtest=LeaveAndHolidayIT,ModularityTests`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(leave): add idempotent Indonesian holiday import and full/half-day leave management"
```

---

### Task 2: Modul `allocation` — CRUD Alokasi `CONFIRMED` / `TENTATIVE` & Validasi RBAC

**Files:**
- Create: `server/src/main/java/com/toppaload/allocation/AllocationType.java`, `AllocationSummary.java`
- Create: `server/src/main/java/com/toppaload/allocation/internal/AllocationEntity.java`, `AllocationRepository.java`, `AllocationService.java`, `AllocationController.java`
- Test: `server/src/test/java/com/toppaload/allocation/AllocationCrudIT.java`

**Interfaces:**
- Consumes: `com.toppaload.identity.UserDirectory`, `com.toppaload.project.ProjectLookup`, `com.toppaload.audit.AuditRecorder`
- Produces:
  - `enum AllocationType { CONFIRMED, TENTATIVE }`
  - `GET /api/v1/allocations`, `POST /api/v1/allocations`, `PUT /api/v1/allocations/{id}`, `DELETE /api/v1/allocations/{id}`

- [ ] **Step 1: Tulis failing integration test `AllocationCrudIT`**

Uji di `AllocationCrudIT`:
1. `SUPERVISOR` dapat membuat, mengubah, dan soft-delete alokasi `CONFIRMED` maupun `TENTATIVE` untuk anggota mana pun; perubahan tercatat di `audit_logs`.
2. `MEMBER` dapat membuat alokasi `TENTATIVE` untuk dirinya sendiri (bila `app.allocation.member-tentative-enabled=true`), tetapi ditolak `403` saat membuat `CONFIRMED` atau menunjuk `userId` orang lain.
3. Jika `taskId` diisi namun `task.projectId != allocation.projectId`, server menolak `400 ProblemDetail`.

- [ ] **Step 2: Jalankan test untuk memverifikasi gagal**

Run: `cd server && ./mvnw.cmd test -Dtest=AllocationCrudIT`
Expected: FAIL

- [ ] **Step 3: Implementasikan `AllocationService` dan `AllocationController`**

Validasi konsistensi `projectId` vs `taskId` melalui `ProjectLookup`, simpan dengan presisi `BigDecimal` 2 desimal, dan catat audit JSONB.

- [ ] **Step 4: Jalankan test `AllocationCrudIT` & `ModularityTests`**

Run: `cd server && ./mvnw.cmd test -Dtest=AllocationCrudIT,ModularityTests`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(allocation): add audited Allocation CRUD with confirmed/tentative RBAC rules"
```

---

### Task 3: Modul `capacity` — Mesin SQL `generate_series` & Suite 10 Kasus Uji Kanonik (`TC-01`..`TC-10`)

**Files:**
- Create: `server/src/main/java/com/toppaload/capacity/Granularity.java`, `UtilizationStatus.java`, `CapacityCell.java`, `HeatmapResponse.java`, `CapacityQueryService.java`
- Create: `server/src/main/java/com/toppaload/capacity/internal/CapacityClassifier.java`, `JdbcCapacityQueryRepository.java`, `CapacityController.java`
- Test: `server/src/test/java/com/toppaload/capacity/CapacityClassifierTest.java`
- Test: `server/src/test/java/com/toppaload/capacity/CapacityGoldenSuiteIT.java`

**Interfaces:**
- Consumes: Tabel `users`, `system_settings`, `holidays`, `leaves`, `allocations`, `projects`, `tasks` (read-only SQL agregasi via `JdbcClient`)
- Produces:
  - `enum Granularity { DAY, WEEK, MONTH }`
  - `enum UtilizationStatus { UNDERUTILIZED, OPTIMAL, OVERLOADED, NO_CAPACITY }`
  - `record CapacityCell(LocalDate bucketStart, BigDecimal netCapacityHours, BigDecimal leaveHours, BigDecimal confirmedHours, BigDecimal tentativeHours, BigDecimal utilizationPct, UtilizationStatus status, boolean hasLeaveConflict)`
  - `CapacityQueryService.getHeatmap(LocalDate from, LocalDate to, Granularity granularity, boolean includeTentative)`
  - `GET /api/v1/capacity/heatmap`, `GET /api/v1/capacity/users/{userId}/breakdown`, `POST /api/v1/allocations/preview`

- [ ] **Step 1: Tulis failing unit test `CapacityClassifierTest` dan parameterized Testcontainers integration test `CapacityGoldenSuiteIT` untuk kesepuluh kasus `TC-01` s/d `TC-10`**

Pastikan `CapacityGoldenSuiteIT` menyetel dataset PostgreSQL nyata untuk masing-masing skenario `TC-01`..`TC-10` persis seperti tabel Bagian 7.5 `docs/prd.md`:
- `TC-01`: `30.0 / 40.0j` -> `75.0%`, `OPTIMAL`
- `TC-02`: `20.0 / 40.0j` -> `50.0%`, `UNDERUTILIZED`
- `TC-03`: `24.0 / 40.0j` -> `60.0%`, `OPTIMAL`
- `TC-04`: `40.0 / 40.0j` -> `100.0%`, `OPTIMAL`
- `TC-05`: `42.0 / 40.0j` -> `105.0%`, `OVERLOADED`
- `TC-06`: Libur Nasional Kamis -> `32.0 / 32.0j` -> `100.0%`, `OPTIMAL`
- `TC-07`: Cuti Penuh Jumat + Alokasi Sen–Jum `8j` -> `40.0 / 32.0j` -> `125.0%`, `OVERLOADED`, `hasLeaveConflict = true`
- `TC-08`: Cuti Setengah Hari Rabu + Alokasi Sen–Jum `6j` -> `30.0 / 36.0j` -> `83.3%`, `OPTIMAL`
- `TC-09`: Cuti Penuh Sen–Jum + Alokasi Sen–Sel `4j` -> `8.0 / 0.0j` -> `utilizationPct = null`, `OVERLOADED`, `hasLeaveConflict = true`
- `TC-10`: Confirmed `30j` + Tentative `20j` -> default `75.0% OPTIMAL` (`tentativeHours = 20.00`), dengan `includeTentative=true` -> `125.0% OVERLOADED`

- [ ] **Step 2: Jalankan test untuk memverifikasi gagal**

Run: `cd server && ./mvnw.cmd test -Dtest=CapacityClassifierTest,CapacityGoldenSuiteIT`
Expected: FAIL

- [ ] **Step 3: Implementasikan `CapacityClassifier`, `JdbcCapacityQueryRepository` (menggunakan CTE `generate_series` Bagian 4 Spesifikasi Desain), `CapacityQueryService`, dan `CapacityController`**

Tambahkan juga endpoint `POST /api/v1/allocations/preview` yang menghitung dampak penambahan/perubahan satu alokasi terhadap sel harian/mingguan user sebelum disimpan sehingga UI dapat menampilkan peringatan tumpang tindih atau bentrok cuti secara instan.

- [ ] **Step 4: Jalankan seluruh suite test backend dan pastikan 10/10 kasus emas lulus**

Run: `cd server && ./mvnw.cmd test`
Expected: PASS (`CapacityGoldenSuiteIT` 10/10 lulus hijau)

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(capacity): implement PostgreSQL generate_series capacity engine passing 10 golden test cases"
```

---

### Task 4: Frontend Layar Utama Heatmap Orang × Minggu, Drawer Alokasi 1-Klik & Halaman Cuti/Libur

**Files:**
- Create: `web/src/features/heatmap/HeatmapPage.tsx`, `HeatmapGrid.tsx`, `HeatmapStatusCell.tsx`, `CellBreakdownDrawer.tsx`
- Create: `web/src/features/allocations/AllocationFormDrawer.tsx`
- Create: `web/src/features/leaves/LeavesAndHolidaysPage.tsx`, `LeaveFormModal.tsx`, `HolidayImportModal.tsx`
- Modify: `web/src/app/router.tsx`, `web/src/i18n/locales/id.json`, `web/src/i18n/locales/en.json`
- Test: `web/src/features/heatmap/__tests__/HeatmapStatusCell.test.tsx`

**Interfaces:**
- Consumes: `/api/v1/capacity/heatmap`, `/api/v1/capacity/users/{userId}/breakdown`, `/api/v1/allocations`, `/api/v1/allocations/preview`, `/api/v1/holidays`, `/api/v1/leaves`
- Produces:
  - Layar utama `/beban-kerja` (`HeatmapPage`) dengan kontrol URL `?granularity=WEEK&from=...&tentative=false`, navigasi minggu sebelumnya/berikutnya via tombol & panah keyboard (`[`, `]`), dan polling ringan 30 detik
  - `HeatmapStatusCell` yang menampilkan angka `confirmed / netCapacity j`, persentase `.num-tabular`, badge `+Xj tentatif`, pola arsir + ikon `!` saat `OVERLOADED`, serta penanda khusus `Konflik Cuti!` saat `hasLeaveConflict === true`
  - Klik sel -> `CellBreakdownDrawer` -> 1 klik `Edit / Tambah Alokasi` -> `AllocationFormDrawer`

- [ ] **Step 1: Tulis failing Vitest `HeatmapStatusCell.test.tsx`**

Uji bahwa:
1. Sel `OVERLOADED` me-render persentase tabular (`125%`), teks rasio jam (`40 / 32j`), label aksesibel/ikon peringatan (tidak hanya class warna), dan penanda konflik cuti bila `hasLeaveConflict=true`.
2. Sel dengan `netCapacityHours = 0` dan `confirmedHours = 8` tidak menampilkan `NaN%` atau `Infinity%`, melainkan teks konflik (`8 / 0j · Konflik Cuti`).
3. Sel dengan `tentativeHours > 0` menampilkan indikator terpisah `+20j tentatif`.

- [ ] **Step 2: Jalankan Vitest untuk memverifikasi gagal**

Run: `cd web && npm.cmd test -- --run`
Expected: FAIL

- [ ] **Step 3: Implementasikan `HeatmapStatusCell`, `HeatmapGrid`, `HeatmapPage`, `CellBreakdownDrawer`, `AllocationFormDrawer`, dan `LeavesAndHolidaysPage`**

Jaga agar `HeatmapPage` 100% menggunakan CSS Grid/Table HTML ringan tanpa mengimpor ECharts/FullCalendar, dan lengkapi seluruh string di `id.json` & `en.json`.

- [ ] **Step 4: Jalankan seluruh test frontend & verifikasi ukuran bundle utama Vite**

Run: `cd web && npm.cmd test -- --run && npm.cmd run build`
Expected: PASS dan chunk awal ringan (`< 120 kB` gzipped)

- [ ] **Step 5: Commit**

```bash
git add web/
git commit -m "feat(web): build primary Person x Week Heatmap, 1-click Allocation drawer, and Leave/Holiday UI"
```

