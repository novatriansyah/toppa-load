CREATE EXTENSION IF NOT EXISTS pgcrypto;

-- 1. users (Allowlist SSO & Profil Kapasitas Kerja)
CREATE TABLE users (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email               VARCHAR(255) NOT NULL,
    full_name           VARCHAR(150) NOT NULL,
    role                VARCHAR(20)  NOT NULL CHECK (role IN ('ADMIN', 'SUPERVISOR', 'MEMBER')),
    work_hours_per_day  NUMERIC(4,2) NOT NULL DEFAULT 8.00 CHECK (work_hours_per_day > 0 AND work_hours_per_day <= 24),
    work_days           SMALLINT[]   NOT NULL DEFAULT '{1,2,3,4,5}', -- ISO DOW: 1=Sen .. 7=Min
    active              BOOLEAN      NOT NULL DEFAULT TRUE,
    email_notifications BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at          TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_users_email_lower ON users (LOWER(email)) WHERE deleted_at IS NULL;

-- 2. system_settings (Baris tunggal konfigurasi tim, mis. ambang utilisasi)
CREATE TABLE system_settings (
    id                         SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    underutilized_threshold_pct NUMERIC(5,1) NOT NULL DEFAULT 60.0,
    overloaded_threshold_pct    NUMERIC(5,1) NOT NULL DEFAULT 100.0,
    work_timezone              VARCHAR(64)  NOT NULL DEFAULT 'Asia/Jakarta',
    updated_by                 UUID REFERENCES users(id),
    updated_at                 TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    CHECK (underutilized_threshold_pct >= 0 AND underutilized_threshold_pct < overloaded_threshold_pct)
);

-- 3. projects
CREATE TABLE projects (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code        VARCHAR(24)  NOT NULL,
    name        VARCHAR(160) NOT NULL,
    description TEXT,
    status      VARCHAR(20)  NOT NULL DEFAULT 'ACTIVE'
                CHECK (status IN ('PLANNING', 'ACTIVE', 'ON_HOLD', 'COMPLETED', 'ARCHIVED')),
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    owner_id    UUID         NOT NULL REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    CHECK (end_date >= start_date)
);
CREATE UNIQUE INDEX uq_projects_code_upper ON projects (UPPER(code)) WHERE deleted_at IS NULL;

-- 4. tasks
CREATE TABLE tasks (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    project_id      UUID         NOT NULL REFERENCES projects(id),
    title           VARCHAR(200) NOT NULL,
    description     TEXT,
    status          VARCHAR(20)  NOT NULL DEFAULT 'TODO'
                    CHECK (status IN ('TODO', 'IN_PROGRESS', 'DONE', 'CANCELLED')),
    priority        VARCHAR(16)  NOT NULL DEFAULT 'MEDIUM'
                    CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH', 'URGENT')),
    estimated_hours NUMERIC(6,2) CHECK (estimated_hours IS NULL OR estimated_hours >= 0),
    due_date        DATE,
    labels          TEXT[]       NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_tasks_project_status ON tasks (project_id, status) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_due_date ON tasks (due_date) WHERE deleted_at IS NULL AND status NOT IN ('DONE', 'CANCELLED');

-- 5. allocations
CREATE TABLE allocations (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id),
    project_id      UUID         NOT NULL REFERENCES projects(id),
    task_id         UUID         REFERENCES tasks(id),
    start_date      DATE         NOT NULL,
    end_date        DATE         NOT NULL,
    hours_per_day   NUMERIC(4,2) NOT NULL CHECK (hours_per_day > 0 AND hours_per_day <= 24),
    allocation_type VARCHAR(16)  NOT NULL DEFAULT 'CONFIRMED'
                    CHECK (allocation_type IN ('CONFIRMED', 'TENTATIVE')),
    notes           VARCHAR(500),
    created_by      UUID         NOT NULL REFERENCES users(id),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ,
    CHECK (end_date >= start_date)
);
CREATE INDEX idx_allocations_user_dates ON allocations (user_id, start_date, end_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_allocations_project_dates ON allocations (project_id, start_date, end_date) WHERE deleted_at IS NULL;

-- 6. holidays (Libur Nasional & Cuti Bersama Perusahaan)
CREATE TABLE holidays (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    holiday_date DATE         NOT NULL,
    name         VARCHAR(160) NOT NULL,
    scope        VARCHAR(16)  NOT NULL DEFAULT 'NATIONAL'
                 CHECK (scope IN ('NATIONAL', 'COMPANY')),
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ
);
CREATE UNIQUE INDEX uq_holidays_date_scope ON holidays (holiday_date, scope) WHERE deleted_at IS NULL;

-- 7. leaves (Cuti Anggota Tim — Penuh & Setengah Hari)
CREATE TABLE leaves (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID         NOT NULL REFERENCES users(id),
    leave_type  VARCHAR(20)  NOT NULL CHECK (leave_type IN ('ANNUAL', 'SICK', 'UNPAID', 'OTHER')),
    portion     VARCHAR(16)  NOT NULL DEFAULT 'FULL' CHECK (portion IN ('FULL', 'HALF_AM', 'HALF_PM')),
    start_date  DATE         NOT NULL,
    end_date    DATE         NOT NULL,
    status      VARCHAR(16)  NOT NULL DEFAULT 'APPROVED'
                CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
    reason      VARCHAR(500),
    approver_id UUID         REFERENCES users(id),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at  TIMESTAMPTZ,
    CHECK (end_date >= start_date),
    CHECK (portion = 'FULL' OR start_date = end_date) -- Cuti setengah hari berlaku untuk 1 tanggal spesifik
);
CREATE INDEX idx_leaves_user_dates ON leaves (user_id, start_date, end_date) WHERE deleted_at IS NULL AND status = 'APPROVED';

-- 8. notifications (In-App + Status Pengiriman Email Anti-Dobel)
CREATE TABLE notifications (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id         UUID         NOT NULL REFERENCES users(id),
    type            VARCHAR(40)  NOT NULL
                    CHECK (type IN ('ALLOCATION_ASSIGNED', 'TASK_DUE_SOON', 'OVERLOAD_WARNING', 'LEAVE_STATUS')),
    title           VARCHAR(200) NOT NULL,
    payload         JSONB        NOT NULL DEFAULT '{}'::jsonb,
    dedup_key       VARCHAR(160) NOT NULL UNIQUE, -- Kunci idempotensi agar job terjadwal tidak pernah kirim dobel
    read_at         TIMESTAMPTZ,
    email_status    VARCHAR(16)  NOT NULL DEFAULT 'PENDING'
                    CHECK (email_status IN ('PENDING', 'SENT', 'SKIPPED', 'FAILED')),
    email_sent_at   TIMESTAMPTZ,
    email_error     VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_notifications_user_unread ON notifications (user_id, created_at DESC) WHERE read_at IS NULL;
CREATE INDEX idx_notifications_email_pending ON notifications (email_status, created_at) WHERE email_status = 'PENDING';

-- 9. audit_logs (Append-Only Jejak Audit JSONB)
CREATE TABLE audit_logs (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    actor_id    UUID         REFERENCES users(id),
    actor_email VARCHAR(255) NOT NULL,
    action      VARCHAR(40)  NOT NULL, -- CREATE, UPDATE, DELETE, IMPORT, LOGIN_DENIED
    entity_type VARCHAR(40)  NOT NULL, -- USER, PROJECT, TASK, ALLOCATION, LEAVE, HOLIDAY, SETTINGS
    entity_id   VARCHAR(64)  NOT NULL,
    before_data JSONB,
    after_data  JSONB,
    occurred_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_audit_logs_entity ON audit_logs (entity_type, entity_id, occurred_at DESC);

-- Seed awal konfigurasi sistem (baris tunggal id=1)
INSERT INTO system_settings (id, underutilized_threshold_pct, overloaded_threshold_pct, work_timezone)
VALUES (1, 60.0, 100.0, 'Asia/Jakarta');

