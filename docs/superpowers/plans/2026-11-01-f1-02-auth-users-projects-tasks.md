# Fase 1B (Minggu 2–3): SSO Allowlist, RBAC, Audit Log, Proyek & Task Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Mengimplementasikan autentikasi SSO OIDC berbasis *email allowlist* ketat, otorisasi 3 peran (`ADMIN`, `SUPERVISOR`, `MEMBER`), pencatatan `audit_logs` `JSONB`, pengaturan jam/hari kerja user, serta CRUD Proyek & Task cepat (`< 2 menit`).

**Architecture:** Modul `audit` menyediakan `AuditRecorder` lintas modul untuk menyimpan snapshot `before_data` dan `after_data` (`JSONB`). Modul `identity` membungkus Spring Security OAuth2/OIDC dengan `AllowlistOidcUserService` yang menolak email tak terdaftar atau nonaktif, memastikan akun bootstrap admin selalu aktif, dan menegakkan CSRF cookie-to-header + `@PreAuthorize`. Modul `project` mengelola Proyek & Task dengan pencarian/filter padat dan input task berbasis keyboard.

**Tech Stack:** Spring Boot `4.1.0` (`spring-boot-starter-security`, `spring-security-oauth2-client`), Spring Modulith `2.1.0`, Spring Data JPA, PostgreSQL 17 `JSONB` & Array, Testcontainers `2.0.3`, React 19, TanStack Query v5, TanStack Table v8, Radix UI Dialog/Select.

**Spec:** `docs/superpowers/specs/2026-11-01-toppa-load-design.md`

## Global Constraints

- Hanya email yang terdaftar di tabel `users` dengan `active = TRUE` dan `deleted_at IS NULL` yang boleh masuk; *self-signup* dilarang keras.
- Setiap endpoint mutasi (`POST`, `PUT`, `PATCH`, `DELETE`) wajib dilindungi CSRF (`CookieCsrfTokenRepository` + `SpaCsrfTokenRequestHandler`) dan dicek perannya di server melalui `@PreAuthorize`.
- Setiap perubahan pada `User`, `Project`, dan `Task` wajib mencatat baris baru di `audit_logs` berisi `actor_id`, `actor_email`, `action`, `entity_type`, `entity_id`, `before_data`, dan `after_data`.
- Penghapusan `Project` dan `Task` wajib menggunakan *soft delete* (`deleted_at = NOW()`), tidak pernah `DELETE FROM`.
- Layar Proyek & Task wajib memungkinkan Supervisor membuat 1 proyek baru beserta minimal 5 task dalam waktu `< 2 menit` melalui baris *quick-add* keyboard (`Enter` simpan & lanjut baris berikutnya).

## Review Focus

- Login OIDC dengan email Google/Microsoft valid yang **tidak ada** di tabel `users` wajib ditolak (`OAuth2AuthenticationException` -> redirect `/login?error=not_allowlisted`) dan mencatat event `LOGIN_DENIED` ke `audit_logs` tanpa membuat record `users` baru.
- User yang sedang memiliki sesi aktif lalu diubah statusnya menjadi `active = false` oleh Admin wajib langsung tertolak (`401`/`403`) pada request API berikutnya, bukan tetap bisa bertransaksi sampai cookie kedaluwarsa.
- Request mutasi `POST /api/v1/projects` dari user ber-role `MEMBER` wajib ditolak `403 Forbidden`, dan request mutasi tanpa header `X-XSRF-TOKEN` yang cocok wajib ditolak `403 Forbidden`.
- Pembaruan `work_days` user dengan array kosong `{}` atau angka di luar ISO hari `1..7` (mis. `0` atau `8`) wajib ditolak `400 Bad Request` (`ProblemDetail`) sebelum menyentuh database.
- Pembuatan proyek dengan `end_date < start_date` atau duplikasi `code` proyek aktif (beda kapitalisasi, mis. `erp-01` vs `ERP-01`) wajib mengembalikan pesan error Bahasa Indonesia yang spesifik menyebut field yang salah.

---

## Peta File Rencana Ini

- `server/src/main/java/com/toppaload/audit/AuditRecorder.java`, `AuditEntry.java`, `internal/AuditLogRepository.java`, `internal/AuditLogController.java` — Modul pencatatan & pembacaan audit JSONB.
- `server/src/main/java/com/toppaload/identity/Role.java`, `AuthenticatedActor.java`, `UserSummary.java`, `UserDirectory.java` — Kontrak publik modul `identity`.
- `server/src/main/java/com/toppaload/identity/internal/UserEntity.java`, `UserRepository.java`, `BootstrapAdminInitializer.java`, `AllowlistOidcUserService.java`, `ActiveUserVerificationFilter.java`, `SecurityConfig.java`, `AuthMeController.java`, `UserAdminController.java` — Keamanan OIDC, allowlist, CSRF SPA, dan manajemen user/jam kerja.
- `server/src/main/java/com/toppaload/project/ProjectSummary.java`, `ProjectLookup.java` — Kontrak publik modul `project` untuk modul `allocation` nanti.
- `server/src/main/java/com/toppaload/project/internal/ProjectEntity.java`, `TaskEntity.java`, `ProjectRepository.java`, `TaskRepository.java`, `ProjectService.java`, `ProjectController.java`, `TaskController.java` — CRUD Proyek & Task teraudit + soft-delete + pencarian.
- `web/src/shared/api/httpClient.ts` — Wrapper `fetch` yang otomatis membaca cookie `XSRF-TOKEN`, mengirim `X-XSRF-TOKEN`, dan mem-parsing `ProblemDetail`.
- `web/src/features/auth/LoginPage.tsx`, `useCurrentUser.ts`, `AuthGuard.tsx` — Halaman masuk SSO (dengan pemberitahuan privasi UU PDP ringkas) & pelindung rute.
- `web/src/features/settings/UsersSettingsSection.tsx` — Manajemen allowlist user, role, `jam_kerja_per_hari`, dan checkbox `hari_kerja` (Sen–Min).
- `web/src/features/projects/ProjectsPage.tsx`, `ProjectTasksDrawer.tsx` — Tabel padat proyek + *quick-add* task berbasis keyboard.

---

### Task 1: Modul `audit` (`AuditRecorder` JSONB & Endpoint Pembacaan Supervisor)

**Files:**
- Create: `server/src/main/java/com/toppaload/audit/AuditEntry.java`
- Create: `server/src/main/java/com/toppaload/audit/AuditRecorder.java`
- Create: `server/src/main/java/com/toppaload/audit/internal/JdbcAuditRecorder.java`
- Create: `server/src/main/java/com/toppaload/audit/internal/AuditLogController.java`
- Test: `server/src/test/java/com/toppaload/audit/AuditRecorderIT.java`

**Interfaces:**
- Consumes: `com.toppaload.shared.WibClock`, tabel `audit_logs`
- Produces:
  - `com.toppaload.audit.AuditRecorder`:
    `void record(UUID actorId, String actorEmail, String action, String entityType, String entityId, Object beforeData, Object afterData)`
  - `GET /api/v1/audit-logs?entityType=&limit=50`

- [ ] **Step 1: Tulis failing integration test `AuditRecorderIT`**

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AuditRecorderIT {
    @Autowired AuditRecorder auditRecorder;
    @Autowired JdbcClient jdbc;

    @Test
    void persistsBeforeAndAfterSnapshotsAsQueryableJsonb() {
        auditRecorder.record(null, "supervisor@kantor.id", "UPDATE", "PROJECT", "proj-1",
                Map.of("status", "PLANNING"), Map.of("status", "ACTIVE"));

        String afterStatus = jdbc.sql("SELECT after_data->>'status' FROM audit_logs WHERE entity_id = 'proj-1'")
                .query(String.class).single();
        assertThat(afterStatus).isEqualTo("ACTIVE");
    }
}
```

- [ ] **Step 2: Jalankan test untuk memverifikasi gagal kompilasi**

Run: `cd server && ./mvnw.cmd test -Dtest=AuditRecorderIT`
Expected: FAIL

- [ ] **Step 3: Implementasikan `AuditRecorder` menggunakan `JdbcClient` + serialisasi JSON Jackson 3 (`tools.jackson.databind.ObjectMapper` di Spring Boot 4.1) ke kolom `::jsonb`**

Pastikan `ModularityTests` tetap lulus (tidak ada kebocoran tipe `internal`).

- [ ] **Step 4: Jalankan test `AuditRecorderIT` dan `ModularityTests`**

Run: `cd server && ./mvnw.cmd test -Dtest=AuditRecorderIT,ModularityTests`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/src/main/java/com/toppaload/audit/ server/src/test/java/com/toppaload/audit/
git commit -m "feat(audit): add JSONB AuditRecorder service and query endpoint"
```

---

### Task 2: Modul `identity` — SSO OIDC Allowlist, Bootstrap Admin, CSRF SPA & RBAC

**Files:**
- Create: `server/src/main/java/com/toppaload/identity/Role.java`, `AuthenticatedActor.java`, `UserSummary.java`, `UserDirectory.java`
- Create: `server/src/main/java/com/toppaload/identity/internal/UserEntity.java`, `UserRepository.java`, `BootstrapAdminInitializer.java`, `AllowlistOidcUserService.java`, `ActiveUserVerificationFilter.java`, `SecurityConfig.java`, `AuthMeController.java`, `UserAdminController.java`
- Modify: `server/pom.xml`, `server/src/main/resources/application.yml`
- Test: `server/src/test/java/com/toppaload/identity/IdentitySecurityIT.java`

**Interfaces:**
- Consumes: `com.toppaload.audit.AuditRecorder`
- Produces:
  - `enum Role { ADMIN, SUPERVISOR, MEMBER }`
  - `record AuthenticatedActor(UUID userId, String email, String fullName, Role role)`
  - `interface UserDirectory` (`Optional<UserSummary> findActiveById(UUID id)`, `List<UserSummary> listActiveUsers()`, `AuthenticatedActor requireCurrentActor()`)
  - `GET /api/v1/me`, `GET /api/v1/users`, `POST /api/v1/users`, `PATCH /api/v1/users/{id}`

- [ ] **Step 1: Tambahkan `spring-boot-starter-security`, `spring-boot-starter-oauth2-client`, dan `spring-security-test` di `server/pom.xml`, lalu tulis failing test `IdentitySecurityIT`**

Uji 5 skenario kunci di `IdentitySecurityIT`:
1. Email yang tidak ada di `users` ditolak oleh `AllowlistOidcUserService` dan tercatat `LOGIN_DENIED` di `audit_logs`.
2. `BootstrapAdminInitializer` membuat/mengaktifkan akun `app.security.bootstrap-admin-email` dengan role `ADMIN` saat startup.
3. User dengan sesi valid yang diubah `active = false` langsung mendapat `403` pada request `GET /api/v1/users` berikutnya (`ActiveUserVerificationFilter`).
4. `POST /api/v1/users` tanpa CSRF token ditolak `403`, dari `MEMBER` ditolak `403`, dan dari `ADMIN` berhasil `201 Created` + tercatat di `audit_logs`.
5. `PATCH /api/v1/users/{id}` menolak `workDays` kosong atau berisi angka `8` dengan `400 ProblemDetail`.

- [ ] **Step 2: Jalankan test untuk memverifikasi gagal**

Run: `cd server && ./mvnw.cmd test -Dtest=IdentitySecurityIT`
Expected: FAIL

- [ ] **Step 3: Implementasikan `SecurityConfig`, `AllowlistOidcUserService`, `ActiveUserVerificationFilter`, `BootstrapAdminInitializer`, serta endpoint `/api/v1/me` dan `/api/v1/users`**

Konfigurasikan `CookieCsrfTokenRepository.withHttpOnlyFalse()` dengan `SpaCsrfTokenRequestHandler` standar Spring Security untuk SPA satu origin, cookie sesi `HttpOnly` + `SameSite=Lax`, serta `@EnableMethodSecurity`.

- [ ] **Step 4: Jalankan test keamanan & modularitas**

Run: `cd server && ./mvnw.cmd test -Dtest=IdentitySecurityIT,ModularityTests,SpaForwardingIT`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(identity): implement OIDC email allowlist, RBAC, SPA CSRF, and user work schedule API"
```

---

### Task 3: Modul `project` — CRUD Proyek & Task Teraudit dengan Pencarian & Soft-Delete

**Files:**
- Create: `server/src/main/java/com/toppaload/project/ProjectSummary.java`, `TaskSummary.java`, `ProjectLookup.java`
- Create: `server/src/main/java/com/toppaload/project/internal/ProjectEntity.java`, `TaskEntity.java`, `ProjectRepository.java`, `TaskRepository.java`, `ProjectService.java`, `ProjectController.java`, `TaskController.java`
- Test: `server/src/test/java/com/toppaload/project/ProjectAndTaskIT.java`

**Interfaces:**
- Consumes: `com.toppaload.identity.UserDirectory`, `com.toppaload.audit.AuditRecorder`
- Produces:
  - `interface ProjectLookup` (`ProjectSummary requireActiveProject(UUID projectId)`, `Optional<TaskSummary> findActiveTask(UUID taskId)`, `ProjectSummary findOrCreateActiveByCode(String code, String name, LocalDate startDate, LocalDate endDate, UUID ownerId)`)
  - Endpoints `GET/POST /api/v1/projects`, `PUT/DELETE /api/v1/projects/{id}`, `GET/POST /api/v1/projects/{projectId}/tasks`, `PUT/DELETE /api/v1/tasks/{id}`

- [ ] **Step 1: Tulis failing integration test `ProjectAndTaskIT`**

Uji di `ProjectAndTaskIT`:
1. `SUPERVISOR` berhasil membuat proyek beserta 3 task, mencari berdasarkan `q` & `status`, dan tercatat di `audit_logs`.
2. `MEMBER` ditolak `403` saat `POST /api/v1/projects` atau `DELETE /api/v1/projects/{id}`, namun diizinkan membuat/mengubah status `Task` pada proyek aktif.
3. Validasi menolak `endDate < startDate` (`400`) dan menolak kode proyek duplikat *case-insensitive* (`409 Conflict` `ProblemDetail` dengan pesan Bahasa Indonesia jelas).
4. `DELETE /api/v1/projects/{id}` mengisi `deleted_at` (data masih ada di DB untuk histori, tidak muncul di `GET /api/v1/projects`).

- [ ] **Step 2: Jalankan test untuk memverifikasi gagal**

Run: `cd server && ./mvnw.cmd test -Dtest=ProjectAndTaskIT`
Expected: FAIL

- [ ] **Step 3: Implementasikan `ProjectService`, `ProjectController`, `TaskController`, dan facade `ProjectLookup`**

Gunakan validasi Jakarta Bean Validation + pengecekan domain eksplisit dengan pesan `ProblemDetail` Bahasa Indonesia yang menyebut cara memperbaikinya.

- [ ] **Step 4: Jalankan seluruh suite test backend**

Run: `cd server && ./mvnw.cmd test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(project): add audited Project and Task CRUD with RBAC, filters, and soft-delete"
```

---

### Task 4: Frontend Auth SSO, Pengaturan User/Jam Kerja, dan Layar Proyek + Quick-Add Task

**Files:**
- Create: `web/src/shared/api/httpClient.ts`
- Create: `web/src/features/auth/useCurrentUser.ts`, `LoginPage.tsx`, `AuthGuard.tsx`
- Create: `web/src/features/settings/SettingsPage.tsx`, `UsersSettingsSection.tsx`
- Create: `web/src/features/projects/ProjectsPage.tsx`, `ProjectFormModal.tsx`, `ProjectTasksDrawer.tsx`
- Modify: `web/src/app/router.tsx`, `web/src/i18n/locales/id.json`, `web/src/i18n/locales/en.json`
- Test: `web/src/features/projects/__tests__/ProjectTasksDrawer.test.tsx`

**Interfaces:**
- Consumes: `/api/v1/me`, `/api/v1/users`, `/api/v1/projects`, `/api/v1/projects/{id}/tasks`, `/api/v1/audit-logs`
- Produces:
  - `httpClient` dengan injeksi header `X-XSRF-TOKEN` otomatis dari cookie `XSRF-TOKEN`
  - `LoginPage` dengan tombol SSO & kotak transparansi data karyawan (UU PDP)
  - `ProjectsPage` padat dengan angka estimasi rata kanan `.num-tabular`, filter terikat ke URL (`?status=ACTIVE&q=...`), dan `ProjectTasksDrawer` dengan input *quick-add* (`Enter` langsung menyimpan task baru dan mengosongkan input agar 5 task selesai dalam `< 30 detik`)

- [ ] **Step 1: Tulis failing Vitest `ProjectTasksDrawer.test.tsx`**

Uji bahwa menekan `Enter` di baris *quick-add* task memanggil `POST /api/v1/projects/{id}/tasks`, langsung mengosongkan field judul sambil mempertahankan fokus keyboard di input tersebut, serta menampilkan angka `estimasi_jam` rata kanan dengan kelas `num-tabular` dan satuan `j`.

- [ ] **Step 2: Jalankan Vitest untuk memverifikasi gagal**

Run: `cd web && npm.cmd test -- --run`
Expected: FAIL

- [ ] **Step 3: Implementasikan `httpClient.ts`, alur Auth (`LoginPage`, `AuthGuard`), `SettingsPage`, serta `ProjectsPage` + `ProjectTasksDrawer`**

Pastikan semua teks berasal dari `id.json` (dan kunci baru ditambahkan juga di `en.json`), serta *empty state* proyek kosong langsung menyediakan tombol aksi *"Buat Proyek Pertama"*.

- [ ] **Step 4: Jalankan seluruh test frontend & build**

Run: `cd web && npm.cmd test -- --run && npm.cmd run build`
Expected: PASS (termasuk parity test `id.json` vs `en.json`)

- [ ] **Step 5: Commit**

```bash
git add web/
git commit -m "feat(web): add SSO login, user work-schedule settings, and keyboard-first Projects & Tasks UI"
```
