-- V4__phase3_forecasting_calendar_schema.sql
-- Fase 3: Reporting, Forecasting What-If, Outlook/Google Calendar Feed, dan Rekomendasi AI

-- 1. calendar_feed_tokens (token privat untuk subscribe kalender di Outlook & Google Calendar)
CREATE TABLE calendar_feed_tokens (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id          UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token            VARCHAR(64)  NOT NULL UNIQUE,
    scope            VARCHAR(50)  NOT NULL DEFAULT 'ALL',
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_accessed_at TIMESTAMPTZ
);
CREATE INDEX idx_calendar_tokens_lookup ON calendar_feed_tokens (token) WHERE is_active = TRUE;
CREATE INDEX idx_calendar_tokens_user ON calendar_feed_tokens (user_id);

-- 2. scenarios (ruang kerja simulasi kapasitas tanpa mengotori tabel alokasi riil)
CREATE TABLE scenarios (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name        VARCHAR(150) NOT NULL,
    description TEXT,
    created_by  UUID         NOT NULL REFERENCES users(id),
    status      VARCHAR(30)  NOT NULL DEFAULT 'DRAFT'
                CHECK (status IN ('DRAFT', 'SIMULATED', 'PROMOTED', 'ARCHIVED')),
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_scenarios_status ON scenarios (status, created_at DESC);
CREATE INDEX idx_scenarios_created_by ON scenarios (created_by);

-- 3. scenario_allocations (alokasi percobaan pada skenario what-if)
CREATE TABLE scenario_allocations (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scenario_id   UUID         NOT NULL REFERENCES scenarios(id) ON DELETE CASCADE,
    user_id       UUID         NOT NULL REFERENCES users(id),
    project_id    UUID         REFERENCES projects(id) ON DELETE SET NULL,
    project_name  VARCHAR(150) NOT NULL,
    start_date    DATE         NOT NULL,
    end_date      DATE         NOT NULL,
    hours_per_day NUMERIC(4,2) NOT NULL CHECK (hours_per_day >= 0 AND hours_per_day <= 24),
    notes         TEXT,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_scenario_allocations_scenario ON scenario_allocations (scenario_id);
CREATE INDEX idx_scenario_allocations_user ON scenario_allocations (user_id, start_date, end_date);

-- 4. scheduled_reports (jadwal pengiriman rekap otomatis via email)
CREATE TABLE scheduled_reports (
    id               UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    report_type      VARCHAR(50)  NOT NULL DEFAULT 'WEEKLY_UTILIZATION',
    frequency        VARCHAR(30)  NOT NULL DEFAULT 'WEEKLY_MONDAY',
    recipient_emails TEXT         NOT NULL,
    is_active        BOOLEAN      NOT NULL DEFAULT TRUE,
    last_run_at      TIMESTAMPTZ,
    next_run_at      TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

-- 5. system_settings: tambah saklar AI Recommendation
ALTER TABLE system_settings
    ADD COLUMN ai_recommendations_enabled BOOLEAN NOT NULL DEFAULT TRUE;

