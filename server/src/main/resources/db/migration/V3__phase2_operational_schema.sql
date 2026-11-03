-- V3__phase2_operational_schema.sql
-- Fase 2: Timesheet, Multi-View, Saldo Cuti, Skill Matrix, Komentar Task, dan Konfigurasi Teams Webhook

-- 1. timesheet_submissions
CREATE TABLE timesheet_submissions (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL REFERENCES users(id),
    week_start_date     DATE         NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT'
                        CHECK (status IN ('DRAFT', 'SUBMITTED', 'APPROVED', 'RETURNED')),
    submitted_at        TIMESTAMPTZ,
    approved_at         TIMESTAMPTZ,
    approved_by         UUID         REFERENCES users(id),
    reviewer_note       TEXT,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, week_start_date)
);
CREATE INDEX idx_timesheet_submissions_status ON timesheet_submissions (status, week_start_date);
CREATE INDEX idx_timesheet_submissions_user ON timesheet_submissions (user_id, week_start_date DESC);

-- 2. time_entries
CREATE TABLE time_entries (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    submission_id   UUID         REFERENCES timesheet_submissions(id) ON DELETE CASCADE,
    user_id         UUID         NOT NULL REFERENCES users(id),
    project_id      UUID         NOT NULL REFERENCES projects(id),
    task_id         UUID         REFERENCES tasks(id),
    entry_date      DATE         NOT NULL,
    hours           NUMERIC(4,2) NOT NULL CHECK (hours >= 0 AND hours <= 24),
    notes           VARCHAR(500),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at      TIMESTAMPTZ
);
CREATE INDEX idx_time_entries_user_date ON time_entries (user_id, entry_date) WHERE deleted_at IS NULL;
CREATE INDEX idx_time_entries_submission ON time_entries (submission_id) WHERE deleted_at IS NULL;
CREATE INDEX idx_time_entries_task ON time_entries (task_id) WHERE deleted_at IS NULL;

-- 3. leave_balances
CREATE TABLE leave_balances (
    id                   UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id              UUID         NOT NULL REFERENCES users(id),
    year                 SMALLINT     NOT NULL,
    total_allowance_days NUMERIC(4,1) NOT NULL DEFAULT 12.0 CHECK (total_allowance_days >= 0),
    used_days            NUMERIC(4,1) NOT NULL DEFAULT 0.0 CHECK (used_days >= 0),
    remaining_days       NUMERIC(4,1) NOT NULL DEFAULT 12.0 CHECK (remaining_days >= 0),
    created_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at           TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, year)
);

-- 4. user_skills
CREATE TABLE user_skills (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users(id),
    skill_name        VARCHAR(80)  NOT NULL,
    proficiency_level VARCHAR(20)  NOT NULL DEFAULT 'INTERMEDIATE'
                      CHECK (proficiency_level IN ('BEGINNER', 'INTERMEDIATE', 'ADVANCED', 'EXPERT')),
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, skill_name)
);
CREATE INDEX idx_user_skills_user ON user_skills (user_id);

-- 5. task_comments
CREATE TABLE task_comments (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    task_id      UUID         NOT NULL REFERENCES tasks(id) ON DELETE CASCADE,
    author_id    UUID         NOT NULL REFERENCES users(id),
    comment_text TEXT         NOT NULL,
    created_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    deleted_at   TIMESTAMPTZ
);
CREATE INDEX idx_task_comments_task ON task_comments (task_id, created_at ASC) WHERE deleted_at IS NULL;

-- 6. saved_views
CREATE TABLE saved_views (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id           UUID         NOT NULL REFERENCES users(id),
    view_type         VARCHAR(40)  NOT NULL,
    name              VARCHAR(100) NOT NULL,
    filter_state_json JSONB        NOT NULL DEFAULT '{}'::jsonb,
    is_shared         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_saved_views_user ON saved_views (user_id, view_type);

-- 7. system_settings: tambah konfigurasi Microsoft Teams Webhook URL
ALTER TABLE system_settings
    ADD COLUMN teams_webhook_url VARCHAR(500);

