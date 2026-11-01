# Fase 1A (Minggu 1): Fondasi Monorepo, Skema Awal & Jalur Deploy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Membangun fondasi monorepo `toppa-load` (Spring Boot 4.1 + Spring Modulith 2.1 + Flyway PostgreSQL 17 + React 19 Vite SPA dalam satu Docker image multi-stage) yang teruji otomatis di GitHub Actions CI dan siap di-deploy ke Coolify.

**Architecture:** Aplikasi tunggal (*Single-Image Modular Monolith*) di mana `web/` dikompilasi oleh Vite menjadi aset statis yang dikemas ke dalam JAR `server/` Spring Boot 4.1.0. Spring Modulith memverifikasi batas 8 modul domain, Flyway menjalankan migrasi baseline `V1`, dan filter SPA mengembalikan `index.html` untuk rute browser non-API tanpa mengganggu `/api/**`, `/oauth2/**`, maupun `/actuator/**`.

**Tech Stack:** Java 25 LTS, Spring Boot `4.1.0`, Spring Modulith `2.1.0`, Flyway, PostgreSQL 17, Testcontainers `2.0.3` (`testcontainers-postgresql`), React 19, TypeScript 5, Vite 6, Tailwind CSS v4, `i18next`, Vitest, Docker Multi-Stage, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-11-01-toppa-load-design.md`

## Global Constraints

- Backend wajib menggunakan Java `25`, Spring Boot `4.1.0`, Spring Modulith `2.1.0`, dan `springdoc-openapi-starter-webmvc-ui` `3.1.1` dalam satu modul Maven `server/`.
- Pengujian database wajib memakai PostgreSQL 17 nyata via `org.testcontainers:testcontainers-postgresql` (`2.0.3`), bukan database in-memory H2/HSQLDB.
- Semua timestamp sistem disimpan dalam `TIMESTAMPTZ` (UTC); semua tanggal kerja disimpan dalam `DATE` mengikuti zona waktu `Asia/Jakarta` (WIB).
- Seluruh teks UI frontend wajib dimuat melalui kamus `web/src/i18n/locales/id.json` (dengan kunci seragam di `en.json`), dan seluruh angka memakai `font-variant-numeric: tabular-nums` rata kanan.
- Image produksi wajib dibangun melalui satu `deploy/Dockerfile` multi-stage non-root dengan batas memori `JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -Xmx512m -Duser.timezone=UTC"`.

## Review Focus

- Deep-link rute SPA seperti `/beban-kerja` atau `/proyek/123` harus me-render `index.html` (`200 OK`), tetapi path API yang tidak dikenal seperti `/api/v1/unknown` wajib mengembalikan JSON `404 ProblemDetail` (bukan HTML `index.html`).
- Aset statis dengan ekstensi file yang hilang seperti `/assets/missing.js` wajib mengembalikan `404` (bukan fallback `index.html` yang memicu syntax error MIME type di browser).
- Baris tunggal `system_settings` (`id = 1`) wajib langsung terisi nilai ambang default (`60.0` dan `100.0`, zona waktu `Asia/Jakarta`) sejak migrasi `V1` selesai dijalankan pada database kosong.
- Constraint `uq_users_email_lower` wajib menolak duplikasi email berbeda huruf besar/kecil (`Budi@Kantor.id` vs `budi@kantor.id`) pada user aktif, namun mengizinkan pendaftaran ulang bila baris lama sudah di-*soft-delete* (`deleted_at IS NOT NULL`).
- `WibClock.today()` wajib mengembalikan tanggal WIB yang benar saat jam UTC masih berada di hari sebelumnya (mis. `2026-11-01T18:30:00Z` adalah `2026-11-02` di WIB).

---

## Peta File Rencana Ini

- `server/pom.xml`, `server/mvnw`, `server/mvnw.cmd`, `server/.mvn/wrapper/maven-wrapper.properties` — Konfigurasi build Maven Java 25 + Spring Boot 4.1.0 + Modulith 2.1.0.
- `server/src/main/java/com/toppaload/ToppaLoadApplication.java` — Entrypoint Spring Boot.
- `server/src/main/java/com/toppaload/shared/WibClock.java` — Penyedia waktu UTC & tanggal kerja `Asia/Jakarta` yang dapat disuntik `Clock` saat test.
- `server/src/main/java/com/toppaload/shared/SystemInfoController.java` — Endpoint `GET /api/v1/system/info`.
- `server/src/main/java/com/toppaload/shared/SpaForwardingController.java` & `ApiExceptionHandler.java` — Fallback rute React SPA & error RFC 7807 `ProblemDetail`.
- `server/src/main/resources/application.yml` & `server/src/main/resources/db/migration/V1__init_phase1_schema.sql` — Konfigurasi aplikasi & DDL lengkap 9 tabel Fase 1.
- `server/src/test/java/com/toppaload/TestcontainersConfiguration.java`, `ModularityTests.java`, `FlywayBaselineMigrationIT.java`, `SpaForwardingIT.java` — Suite verifikasi arsitektur, skema Postgres, dan routing SPA.
- `web/package.json`, `web/vite.config.ts`, `web/tsconfig.json`, `web/index.html` — Build frontend React 19 + TypeScript + Vite + Vitest.
- `web/src/styles/tokens.css`, `web/src/i18n/index.ts`, `web/src/i18n/locales/id.json`, `web/src/i18n/locales/en.json` — Sistem token alat kerja padat & kamus lokalisasi.
- `web/src/app/AppShell.tsx`, `web/src/app/router.tsx`, `web/src/main.tsx` — Kerangka navigasi 5 menu kerja & halaman status koneksi awal.
- `deploy/Dockerfile` & `.github/workflows/ci.yml` — Build image gabungan Opsi A & pipeline CI + webhook deploy Coolify.

---

### Task 1: Bootstrap Backend Spring Boot 4.1, Modulith Verification & `WibClock`

**Files:**
- Create: `server/pom.xml`, `server/mvnw`, `server/mvnw.cmd`, `server/.mvn/wrapper/maven-wrapper.properties`
- Create: `server/src/main/java/com/toppaload/ToppaLoadApplication.java`
- Create: `server/src/main/java/com/toppaload/shared/WibClock.java`
- Create: `server/src/main/resources/application.yml`
- Test: `server/src/test/java/com/toppaload/ModularityTests.java`
- Test: `server/src/test/java/com/toppaload/shared/WibClockTest.java`

**Interfaces:**
- Produces:
  - `com.toppaload.ToppaLoadApplication`
  - `com.toppaload.shared.WibClock` (`public static final ZoneId WIB = ZoneId.of("Asia/Jakarta")`; `Instant nowUtc()`; `LocalDate todayWib()`)

- [ ] **Step 1: Tulis konfigurasi `server/pom.xml`, Maven Wrapper, dan failing unit test `ModularityTests` serta `WibClockTest`**

Konfigurasikan `server/pom.xml` dengan parent `org.springframework.boot:spring-boot-starter-parent:4.1.0`, `<java.version>25</java.version>`, BOM `org.springframework.modulith:spring-modulith-bom:2.1.0`, serta dependency `spring-boot-starter-webmvc`, `spring-boot-starter-data-jpa`, `spring-boot-starter-validation`, `spring-boot-starter-actuator`, `spring-boot-starter-flyway` (atau `flyway-core` + `flyway-database-postgresql`), `postgresql`, `springdoc-openapi-starter-webmvc-ui:3.1.1`, dan scope test `spring-boot-starter-test`, `spring-boot-testcontainers`, `spring-modulith-starter-test`, `org.testcontainers:testcontainers-junit-jupiter`, `org.testcontainers:testcontainers-postgresql`.

Tulis test di `WibClockTest.java` & `ModularityTests.java`:
```java
class WibClockTest {
    @Test
    void convertsLateUtcEveningToNextCalendarDateInWib() {
        Clock fixed = Clock.fixed(Instant.parse("2026-11-01T18:30:00Z"), ZoneOffset.UTC);
        WibClock wibClock = new WibClock(fixed);
        assertThat(wibClock.todayWib()).isEqualTo(LocalDate.of(2026, 11, 2));
        assertThat(wibClock.nowUtc()).isEqualTo(Instant.parse("2026-11-01T18:30:00Z"));
    }
}

class ModularityTests {
    @Test
    void verifiesModularMonolithBoundaries() {
        ApplicationModules.of(ToppaLoadApplication.class).verify();
    }
}
```

- [ ] **Step 2: Jalankan test untuk memastikan gagal kompilasi karena kelas belum ada**

Run: `cd server && ./mvnw.cmd test -Dtest=WibClockTest,ModularityTests`
Expected: FAIL (`cannot find symbol: class WibClock` / `ToppaLoadApplication`)

- [ ] **Step 3: Implementasikan `ToppaLoadApplication`, `WibClock`, package-info `@ApplicationModule` untuk `shared`, dan `application.yml`**

Buat `@SpringBootApplication public class ToppaLoadApplication`, bean `@Component public class WibClock` dengan konstruktor default `Clock.systemUTC()` dan konstruktor `WibClock(Clock clock)`, serta `application.yml` yang mengekspos `/actuator/health` dan `server.forward-headers-strategy: framework`.

- [ ] **Step 4: Jalankan test untuk memverifikasi lulus**

Run: `cd server && ./mvnw.cmd test -Dtest=WibClockTest,ModularityTests`
Expected: PASS (`Tests run: 2, Failures: 0`)

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "build(server): bootstrap Spring Boot 4.1.0 Java 25 Modulith and WibClock"
```

---

### Task 2: Migrasi Flyway `V1__init_phase1_schema.sql` & Testcontainers PostgreSQL 17 Verification

**Files:**
- Create: `server/src/main/resources/db/migration/V1__init_phase1_schema.sql`
- Create: `server/src/test/java/com/toppaload/TestcontainersConfiguration.java`
- Test: `server/src/test/java/com/toppaload/FlywayBaselineMigrationIT.java`

**Interfaces:**
- Consumes: `com.toppaload.ToppaLoadApplication`
- Produces:
  - `com.toppaload.TestcontainersConfiguration` (`@TestConfiguration` dengan `@Bean @ServiceConnection PostgreSQLContainer<?> postgresContainer()` memakai image `postgres:17-alpine`)
  - Skema PostgreSQL 9 tabel Fase 1 (`users`, `system_settings`, `projects`, `tasks`, `allocations`, `holidays`, `leaves`, `notifications`, `audit_logs`) beserta baris seed `INSERT INTO system_settings (id, underutilized_threshold_pct, overloaded_threshold_pct, work_timezone) VALUES (1, 60.0, 100.0, 'Asia/Jakarta')`

- [ ] **Step 1: Tulis failing integration test `FlywayBaselineMigrationIT`**

```java
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class FlywayBaselineMigrationIT {
    @Autowired JdbcClient jdbc;

    @Test
    void seedsDefaultSystemSettingsRowOnFreshDatabase() {
        var row = jdbc.sql("SELECT underutilized_threshold_pct, overloaded_threshold_pct, work_timezone FROM system_settings WHERE id = 1")
                .query((rs, n) -> List.of(rs.getBigDecimal(1), rs.getBigDecimal(2), rs.getString(3)))
                .single();
        assertThat(row).containsExactly(new BigDecimal("60.0"), new BigDecimal("100.0"), "Asia/Jakarta");
    }

    @Test
    void enforcesCaseInsensitiveUniqueActiveEmailButAllowsReInsertAfterSoftDelete() {
        jdbc.sql("INSERT INTO users (email, full_name, role) VALUES ('Budi@Kantor.id', 'Budi', 'MEMBER')").update();
        assertThatThrownBy(() ->
            jdbc.sql("INSERT INTO users (email, full_name, role) VALUES ('budi@kantor.id', 'Budi Dup', 'MEMBER')").update()
        ).isInstanceOf(DataIntegrityViolationException.class);

        jdbc.sql("UPDATE users SET deleted_at = NOW() WHERE LOWER(email) = 'budi@kantor.id'").update();
        int inserted = jdbc.sql("INSERT INTO users (email, full_name, role) VALUES ('budi@kantor.id', 'Budi Baru', 'MEMBER')").update();
        assertThat(inserted).isEqualTo(1);
    }
}
```

- [ ] **Step 2: Jalankan integration test untuk memastikan gagal karena tabel belum ada**

Run: `cd server && ./mvnw.cmd test -Dtest=FlywayBaselineMigrationIT`
Expected: FAIL (`relation "system_settings" does not exist`)

- [ ] **Step 3: Tulis `V1__init_phase1_schema.sql` persis sesuai DDL Bagian 3 Spesifikasi Desain beserta baris awal `system_settings`**

Pastikan `pgcrypto` / `gen_random_uuid()` tersedia di PostgreSQL 17 dan seluruh constraint `CHECK` serta partial index dibuat.

- [ ] **Step 4: Jalankan test untuk memverifikasi migrasi lulus terhadap PostgreSQL 17 nyata**

Run: `cd server && ./mvnw.cmd test -Dtest=FlywayBaselineMigrationIT`
Expected: PASS (`Tests run: 2, Failures: 0`)

- [ ] **Step 5: Commit**

```bash
git add server/src/main/resources/db/migration/V1__init_phase1_schema.sql server/src/test/
git commit -m "feat(db): add Flyway V1 baseline schema and Testcontainers verification"
```

---

### Task 3: Endpoint Info Sistem, Penanganan `ProblemDetail` 404 API, dan Fallback Routing SPA

**Files:**
- Create: `server/src/main/java/com/toppaload/shared/SystemInfoController.java`
- Create: `server/src/main/java/com/toppaload/shared/SpaForwardingController.java`
- Create: `server/src/main/java/com/toppaload/shared/ApiExceptionHandler.java`
- Create: `server/src/main/resources/static/index.html` *(placeholder minimal untuk test backend sebelum Vite dibundel di Docker)*
- Test: `server/src/test/java/com/toppaload/shared/SpaForwardingIT.java`

**Interfaces:**
- Consumes: `com.toppaload.shared.WibClock`, `com.toppaload.TestcontainersConfiguration`
- Produces:
  - `GET /api/v1/system/info` -> `record SystemInfoResponse(String appName, String version, String workTimezone, LocalDate currentWorkDate)`
  - Fallback SPA GET tanpa titik ekstensi di luar `/api/**`, `/actuator/**`, `/oauth2/**`, `/login/**`, `/v3/api-docs/**`, `/swagger-ui/**` -> forward ke `/index.html`

- [ ] **Step 1: Tulis failing integration test `SpaForwardingIT`**

```java
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SpaForwardingIT {
    @Autowired MockMvc mockMvc;

    @Test
    void returnsSystemInfoJson() throws Exception {
        mockMvc.perform(get("/api/v1/system/info"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.appName").value("Toppa Load"))
                .andExpect(jsonPath("$.workTimezone").value("Asia/Jakarta"));
    }

    @Test
    void forwardsSpaDeepLinksToIndexHtml() throws Exception {
        mockMvc.perform(get("/beban-kerja"))
                .andExpect(status().isOk())
                .andExpect(forwardedUrl("/index.html"));
    }

    @Test
    void returns404ProblemDetailForUnknownApiPathAndMissingStaticAsset() throws Exception {
        mockMvc.perform(get("/api/v1/tidak-ada"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));

        mockMvc.perform(get("/assets/missing-chunk.js"))
                .andExpect(status().isNotFound());
    }
}
```

- [ ] **Step 2: Jalankan test untuk memverifikasi gagal (`404` pada `/api/v1/system/info` & `/beban-kerja`)**

Run: `cd server && ./mvnw.cmd test -Dtest=SpaForwardingIT`
Expected: FAIL

- [ ] **Step 3: Implementasikan `SystemInfoController`, `SpaForwardingController`, dan `ApiExceptionHandler`**

Gunakan `@RestControllerAdvice` yang mewarisi `ResponseEntityExceptionHandler` agar `NoResourceFoundException` pada prefix `/api/` dikembalikan sebagai `ProblemDetail` Bahasa Indonesia, sementara `SpaForwardingController` hanya mem-forward route aplikasi tanpa ekstensi file ke `/index.html`.

- [ ] **Step 4: Jalankan seluruh test backend**

Run: `cd server && ./mvnw.cmd test`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add server/
git commit -m "feat(server): add system info endpoint, ProblemDetail 404, and SPA route forwarding"
```

---

### Task 4: Frontend React 19 + Vite + Token Alat Kerja Padat + Kamus `i18next` (`ID`/`EN`)

**Files:**
- Create: `web/package.json`, `web/tsconfig.json`, `web/vite.config.ts`, `web/index.html`
- Create: `web/src/styles/tokens.css`
- Create: `web/src/i18n/index.ts`, `web/src/i18n/locales/id.json`, `web/src/i18n/locales/en.json`
- Create: `web/src/app/AppShell.tsx`, `web/src/app/router.tsx`, `web/src/main.tsx`
- Test: `web/src/app/__tests__/AppShell.test.tsx`

**Interfaces:**
- Consumes: `GET /api/v1/system/info` (melalui Vite dev proxy `/api` -> `http://localhost:8080`)
- Produces:
  - Komponen `AppShell` dengan 5 item navigasi samping berbahasa kerja tim (`Beban Kerja`, `Proyek & Task`, `Cuti & Libur`, `Pengaturan`) tanpa kartu KPI dekoratif
  - Kelas utilitas `.num-tabular` (`font-variant-numeric: tabular-nums; text-align: right;`) dan pola `.cell-overloaded-pattern` di `tokens.css`
  - Struktur kunci kamus `id.json` & `en.json` yang tervalidasi identik

- [ ] **Step 1: Siapkan `web/package.json` dan tulis failing Vitest `AppShell.test.tsx`**

Pasang `react`, `react-dom`, `react-router`, `@tanstack/react-query`, `i18next`, `react-i18next`, `tailwindcss`, `@tailwindcss/vite`, serta devDependencies `typescript`, `vite`, `@vitejs/plugin-react`, `vitest`, `@testing-library/react`, `@testing-library/jest-dom`, `jsdom`.

Tulis test di `web/src/app/__tests__/AppShell.test.tsx`:
```tsx
describe('AppShell & Localization Parity', () => {
  it('renders primary work navigation in Indonesian from translation files', () => {
    render(<MemoryRouter initialEntries={['/beban-kerja']}><AppShell /></MemoryRouter>);
    expect(screen.getByRole('navigation')).toHaveTextContent('Beban Kerja');
    expect(screen.getByRole('navigation')).toHaveTextContent('Proyek & Task');
    expect(screen.getByRole('navigation')).toHaveTextContent('Cuti & Libur');
    expect(screen.getByRole('navigation')).toHaveTextContent('Pengaturan');
  });

  it('keeps identical top-level and nested translation keys between id.json and en.json', () => {
    expect(flattenKeys(idLocale)).toEqual(flattenKeys(enLocale));
  });
});
```

- [ ] **Step 2: Jalankan Vitest untuk memastikan gagal**

Run: `cd web && npm.cmd test -- --run`
Expected: FAIL (`Cannot find module '../AppShell'`)

- [ ] **Step 3: Implementasikan `tokens.css`, `i18n`, `AppShell.tsx`, `router.tsx`, dan konfigurasi proxy Vite**

Pastikan `tokens.css` menerapkan skala spasi 4px, radius 4px/6px, border tipis 1px, kontras WCAG AA, `.num-tabular`, serta `vite.config.ts` mem-proxy `/api`, `/oauth2`, `/login`, dan `/actuator` ke `http://localhost:8080`.

- [ ] **Step 4: Jalankan test & production build frontend**

Run: `cd web && npm.cmd test -- --run && npm.cmd run build`
Expected: PASS dan folder `web/dist` terbentuk tanpa warning TypeScript

- [ ] **Step 5: Commit**

```bash
git add web/
git commit -m "feat(web): scaffold React 19 Vite app shell, work-tool tokens, and id/en i18n"
```

---

### Task 5: Multi-Stage `deploy/Dockerfile` (Opsi A) & GitHub Actions CI + Coolify Webhook

**Files:**
- Create: `deploy/Dockerfile`
- Create: `.dockerignore`
- Create: `.github/workflows/ci.yml`

**Interfaces:**
- Consumes: `web/` (`npm run build` -> `web/dist`), `server/` (`./mvnw package -DskipTests` -> JAR dengan static SPA terbundel)
- Produces:
  - Image Docker tunggal di port `8080` dengan `HEALTHCHECK` ke `http://localhost:8080/actuator/health`
  - Workflow GitHub Actions `ci.yml` yang menjalankan `web` (typecheck + Vitest + build), `server` (`./mvnw verify` dengan Testcontainers PostgreSQL 17), `docker build` smoke check, serta memicu `COOLIFY_STAGING_WEBHOOK_URL` hanya saat push ke `main` lulus hijau

- [ ] **Step 1: Tulis `.dockerignore` dan `deploy/Dockerfile` 3-stage (`node:24-alpine` -> `eclipse-temurin:25-jdk` -> `eclipse-temurin:25-jre` user non-root)**

Salin hasil `web/dist` dari Stage 1 ke `server/src/main/resources/static/` pada Stage 2 sebelum menjalankan `./mvnw -B package -DskipTests`, dan set `ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=50.0 -Xmx512m -Duser.timezone=UTC"` pada Stage 3.

- [ ] **Step 2: Tulis `.github/workflows/ci.yml` untuk verifikasi penuh PR & auto-trigger webhook Staging Coolify**

Pastikan job `backend-test` berjalan di `ubuntu-latest` (yang sudah menyertakan Docker untuk Testcontainers), job `frontend-test` menjalankan `npm ci && npm run test -- --run && npm run build`, job `docker-package` memvalidasi `docker build -f deploy/Dockerfile .`, dan job `deploy-staging` memanggil `curl -fsSL -X POST "${{ secrets.COOLIFY_STAGING_WEBHOOK_URL }}"` hanya bila `github.ref == 'refs/heads/main'` dan secret tersedia.

- [ ] **Step 3: Verifikasi build image multi-stage secara lokal**

Run: `docker build -f deploy/Dockerfile -t toppa-load:local .`
Expected: Image berhasil dibangun tanpa error dan berisi aset `BOOT-INF/classes/static/index.html`

- [ ] **Step 4: Jalankan smoke test kontainer terhadap PostgreSQL dev**

Run: `docker compose -f deploy/docker-compose.dev.yml up -d && docker run --rm --network host -e SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/toppaload -e SPRING_DATASOURCE_USERNAME=toppaload -e SPRING_DATASOURCE_PASSWORD=toppaload_dev_local toppa-load:local --spring.main.web-application-type=none` *(atau cek `/actuator/health` -> `200 UP`)*
Expected: Kontainer start bersih, Flyway `V1` sukses, tanpa error

- [ ] **Step 5: Commit**

```bash
git add .dockerignore deploy/Dockerfile .github/workflows/ci.yml
git commit -m "ci(deploy): add single-image multi-stage Dockerfile and GitHub Actions CI pipeline"
```

