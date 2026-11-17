-- Development projects owned by research assistants, viewable by related supervisors.
CREATE TYPE development_project_status AS ENUM (
    'PLANNED',
    'IN_PROGRESS',
    'ON_HOLD',
    'COMPLETED'
);

CREATE TABLE development_projects (
    development_project_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    assistant_id           BIGINT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    project_id             BIGINT NOT NULL REFERENCES projects (project_id) ON DELETE RESTRICT,
    title                  VARCHAR(200) NOT NULL,
    description            TEXT,
    objectives             TEXT,
    repository_url         VARCHAR(1000),
    status                 development_project_status NOT NULL DEFAULT 'PLANNED',
    progress_note          TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT development_projects_title_not_blank CHECK (length(btrim(title)) > 0)
);

CREATE FUNCTION enforce_development_project_author()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'INSERT'
       OR NEW.project_id IS DISTINCT FROM OLD.project_id
       OR NEW.assistant_id IS DISTINCT FROM OLD.assistant_id
    THEN
        IF NOT EXISTS (
            SELECT 1
            FROM project_assistants
            WHERE project_id = NEW.project_id
              AND assistant_id = NEW.assistant_id
              AND is_active
        ) THEN
            RAISE EXCEPTION 'development projects can be filed only by an active assistant on the research project';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER development_projects_set_updated_at
BEFORE UPDATE ON development_projects
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER development_projects_enforce_author
BEFORE INSERT OR UPDATE ON development_projects
FOR EACH ROW
EXECUTE FUNCTION enforce_development_project_author();

CREATE INDEX development_projects_assistant_id_idx
    ON development_projects (assistant_id);

CREATE INDEX development_projects_project_id_idx
    ON development_projects (project_id);

CREATE INDEX development_projects_status_idx
    ON development_projects (status);
