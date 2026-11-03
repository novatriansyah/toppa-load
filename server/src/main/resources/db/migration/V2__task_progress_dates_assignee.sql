ALTER TABLE tasks
    ADD COLUMN start_date DATE,
    ADD COLUMN progress_percent SMALLINT NOT NULL DEFAULT 0 CHECK (progress_percent >= 0 AND progress_percent <= 100),
    ADD COLUMN assignee_id UUID REFERENCES users(id);

CREATE INDEX idx_tasks_assignee ON tasks (assignee_id) WHERE deleted_at IS NULL;
ALTER TABLE tasks ADD CONSTRAINT chk_tasks_dates CHECK (due_date IS NULL OR start_date IS NULL OR due_date >= start_date);
