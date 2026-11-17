-- Development projects are a separate RA activity, not tied to research projects.
DROP TRIGGER IF EXISTS development_projects_enforce_author ON development_projects;
DROP FUNCTION IF EXISTS enforce_development_project_author();

DROP INDEX IF EXISTS development_projects_project_id_idx;

ALTER TABLE development_projects
    DROP COLUMN IF EXISTS project_id;

CREATE FUNCTION enforce_development_project_assistant()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'INSERT' OR NEW.assistant_id IS DISTINCT FROM OLD.assistant_id THEN
        IF NOT EXISTS (
            SELECT 1
            FROM users
            WHERE user_id = NEW.assistant_id
              AND role = 'RESEARCH_ASSISTANT'
              AND is_active
        ) THEN
            RAISE EXCEPTION 'assistant_id % must be an active research assistant', NEW.assistant_id;
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER development_projects_enforce_assistant
BEFORE INSERT OR UPDATE ON development_projects
FOR EACH ROW
EXECUTE FUNCTION enforce_development_project_assistant();
