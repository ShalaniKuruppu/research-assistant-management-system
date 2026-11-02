-- Preserve the previous research plan whenever an assistant updates it.
CREATE TABLE IF NOT EXISTS research_plan_versions (
    version_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id    BIGINT NOT NULL REFERENCES projects (project_id) ON DELETE CASCADE,
    changed_by    BIGINT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    plan_text     TEXT NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS research_plan_versions_project_created_idx
    ON research_plan_versions (project_id, created_at DESC, version_id DESC);
