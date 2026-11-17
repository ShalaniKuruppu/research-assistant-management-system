-- Track which research assistant created each research project.
-- Only that creator may add or remove assistants on the project.

ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS created_by BIGINT REFERENCES users (user_id) ON DELETE RESTRICT;

UPDATE projects p
SET created_by = sub.assistant_id
FROM (
    SELECT DISTINCT ON (project_id)
           project_id,
           assistant_id
    FROM project_assistants
    ORDER BY project_id, assigned_at ASC, project_assistant_id ASC
) sub
WHERE p.project_id = sub.project_id
  AND p.created_by IS NULL;

DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM projects WHERE created_by IS NULL) THEN
        RAISE EXCEPTION 'projects.created_by could not be backfilled for every project';
    END IF;
END $$;

ALTER TABLE projects
    ALTER COLUMN created_by SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'projects_creator_not_supervisor'
    ) THEN
        ALTER TABLE projects
            ADD CONSTRAINT projects_creator_not_supervisor CHECK (created_by <> supervisor_id);
    END IF;
END $$;

CREATE OR REPLACE FUNCTION enforce_project_creator()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.created_by IS NOT DISTINCT FROM OLD.created_by THEN
        RETURN NEW;
    END IF;

    IF TG_OP = 'UPDATE' AND NEW.created_by IS DISTINCT FROM OLD.created_by THEN
        RAISE EXCEPTION 'project created_by cannot be changed';
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM users
        WHERE user_id = NEW.created_by
          AND role = 'RESEARCH_ASSISTANT'
          AND is_active
    ) THEN
        RAISE EXCEPTION 'created_by % must be an active research assistant', NEW.created_by;
    END IF;

    IF NEW.created_by = NEW.supervisor_id THEN
        RAISE EXCEPTION 'the project creator cannot also be the project supervisor';
    END IF;

    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS projects_enforce_creator ON projects;
CREATE TRIGGER projects_enforce_creator
BEFORE INSERT OR UPDATE ON projects
FOR EACH ROW
EXECUTE FUNCTION enforce_project_creator();

COMMENT ON TABLE project_assistants IS
    'One project has many research assistants. Each row is one assistant assigned to that project by its creating research assistant. The same assistant appears once per project.';
