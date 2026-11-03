# Fase 2 (Minggu 8–16): Manajemen Operasional Harian, Timesheet, Multi-View, Cuti Berjenjang & Integrasi Microsoft Teams

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Membangun lapisan operasional harian yang mulus di atas mesin kapasitas Fase 1: pengisian & persetujuan timesheet mingguan (`timesheet`), visualisasi multi-view (Kanban, List, Kalender), alur persetujuan cuti berjenjang & saldo cuti tahunan (`leave_approval`), matriks skill tim, kolaborasi task (komentar, mention), serta integrasi notifikasi channel Microsoft Teams via Adaptive Cards.

**Architecture:** Spring Modulith 2.1 menambahkan modul-modul operasional baru:
- `com.toppaload.timesheet`: Pencatatan jam aktual per task/hari, perbandingan *Estimasi vs Alokasi vs Aktual*, dan alur persetujuan mingguan (`SUBMITTED -> APPROVED / RETURNED`).
- `com.toppaload.collaboration`: Komentar task (`task_comments`), lampiran metadata, template proyek, dan task rutin berulang.
- Modul `leave` diperluas: Pengajuan cuti berjenjang (`PENDING -> APPROVED / REJECTED`), saldo cuti tahunan (`leave_balances`), dan penandaan skill (`user_skills`).
- Modul `notification` diperluas: Pengiriman notifikasi aktivitas task & pengingat timesheet Jumat sore ke Microsoft Teams Channel (Adaptive Cards).
- Frontend React 19: Halaman Timesheet Mingguan (`/timesheet`), Task Multi-View switch (Kanban / List / Calendar), Modal Approval Cuti Supervisor, dan konfigurasi Webhook Microsoft Teams di Pengaturan.

**Tech Stack:** Java 25, Spring Boot 4.1.0, PostgreSQL 17 (Flyway `V3`), Spring Modulith 2.1, React 19, Lucide Icons, `@dnd-kit`, TanStack Table / Virtual.

---

## Peta File & Skema Fase 2

### Database Migration: `server/src/main/resources/db/migration/V3__phase2_operational_schema.sql`
- `timesheet_submissions`: `(id, user_id, week_start_date, status, submitted_at, approved_at, approved_by, reviewer_note)`
- `time_entries`: `(id, submission_id, user_id, project_id, task_id, entry_date, hours, notes, created_at, updated_at)`
- `leave_balances`: `(id, user_id, year, total_allowance_days, used_days, remaining_days)`
- `leave_requests_approval`: status perpanjangan dari tabel `leaves` (`status VARCHAR(20) DEFAULT 'APPROVED' CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED'))`, `approved_by UUID REFERENCES users(id)`, `review_notes TEXT`)
- `skills` & `user_skills`: `(user_id, skill_name, proficiency_level)`
- `task_comments`: `(id, task_id, author_id, comment_text, created_at, updated_at)`
- `saved_views`: `(id, user_id, view_type, name, filter_state_json, is_shared)`

---

### Task 1: Timesheet Mingguan & Komparasi Estimasi vs Alokasi vs Aktual
- Modul `server/src/main/java/com/toppaload/timesheet/`
  - Kontrak Publik: `TimesheetSubmissionDto`, `TimeEntryDto`, `TimesheetService`
  - Internal: `TimesheetSubmissionEntity`, `TimeEntryEntity`, `TimesheetRepository`, `TimesheetController`
  - Validasi: Total jam per hari `<= 24`, tanggal harus sesuai minggu bersangkutan, otorisasi Member (hanya milik sendiri) vs Supervisor (review).
  - API:
    - `GET /api/v1/timesheets/weekly?weekStartDate=YYYY-MM-DD[&userId=UUID]`
    - `PUT /api/v1/timesheets/weekly` (Simpan draf / baris harian)
    - `POST /api/v1/timesheets/weekly/submit`
    - `POST /api/v1/timesheets/{id}/approve` & `POST /api/v1/timesheets/{id}/return`
  - Frontend: `web/src/features/timesheets/TimesheetWeeklyGrid.tsx`, `TimesheetPage.tsx` (`/timesheet`)

### Task 2: Task Multi-View (Kanban, List, Kalender) & Saved Views
- Frontend & Backend View State:
  - Backend: `server/src/main/java/com/toppaload/project/internal/SavedViewController.java`
  - Frontend:
    - `web/src/features/projects/views/TaskKanbanView.tsx` (Drag-and-drop kolom TODO / IN_PROGRESS / DONE)
    - `web/src/features/projects/views/TaskListView.tsx` (Dense sortable list dengan quick-edit)
    - `web/src/features/projects/views/TaskCalendarView.tsx` (Kalender grid per tanggal jatuh tempo)
    - Filter bar URL-synchronized (`?view=kanban&status=IN_PROGRESS&assignee=UUID`)

### Task 3: Alur Persetujuan Cuti Berjenjang, Saldo Cuti & Skill Matrix
- Backend:
  - Perluasan `server/src/main/java/com/toppaload/leave/`:
    - Pengajuan cuti oleh Member berstatus `PENDING`.
    - Persetujuan oleh Supervisor mengubah status menjadi `APPROVED` dan otomatis mengurangi `leave_balances.remaining_days`.
    - Integrasi Capacity: hanya cuti `APPROVED` yang memotong `net_capacity_hours` (opsional pratinjau `PENDING`).
  - Matriks Skill: `GET/PUT /api/v1/users/{id}/skills`
- Frontend:
  - Tab Pengajuan Cuti & Approval Drawer di `web/src/features/leaves/LeavesAndHolidaysPage.tsx`
  - Skill badges pada profil anggota dan drawer alokasi

### Task 4: Kolaborasi Task, Task Rutin & Microsoft Teams Adaptive Cards Webhook
- Backend:
  - Modul `collaboration`: `TaskCommentController`, `TaskCommentService`
  - Webhook Dispatcher: Perluasan `TeamsWebhookDispatcher` untuk:
    - Overload alert (>100% kapasitas pada minggu berjalan)
    - Notifikasi penugasan task baru (`TaskAssignedEvent`)
    - Notifikasi timesheet Jumat sore yang belum di-submit
- Frontend:
  - Tab Aktivitas & Komentar di `ProjectTasksDrawer.tsx`
  - Pengaturan Webhook URL Microsoft Teams di `SettingsPage.tsx`

---

## Kriteria Selesai Fase 2 (Definition of Done)
1. Seluruh pengujian backend `mvnw test` dan modularitas Spring Modulith lulus 100%.
2. Seluruh pengujian frontend `npm test` dan build `npm run build` lulus tanpa kesalahan.
3. Alur timesheet: Member mengisi jam kerja mingguan -> Submit -> Supervisor Approve/Return berfungsi mulus.
4. Notifikasi peringatan overload terformat rapi dan terkirim ke Microsoft Teams Webhook.

