-- Research Assistant Management System
-- Version 1 database schema (PostgreSQL 14+)
--
-- Apply to an empty database:
--   createdb research_assistant_management
--   psql -d research_assistant_management -f db/schema.sql
--
-- The tables follow the version 1 ER diagram. These rules are enforced here:
--
-- 1. A user writes many task updates.
-- 2. An assistant is assigned to a project at most once.
-- 3. An assistant files at most one weekly report per project per week.
-- 4. A foreign key to users only proves the person exists. Triggers also
--    require the right role and project membership.
-- 5. tasks.status and tasks.completion_percentage are the current snapshot.
--    They change only when a task_updates row is written, and they follow
--    the latest update. Direct edits to those two task columns are rejected.
-- 6. weekly_reports.created_at exists so a draft can be saved before
--    submitted_at is set.
-- 7. Role, status, priority, and outcome only accept the values below.
-- 8. Dates must run forward, and completion percentage must be 0-100.
--
-- Version 1 choices, taken where the diagram is silent:
-- * A person has one role. The same account cannot supervise one project
--   and assist on another. Role cannot be changed later.
-- * A project has one supervisor.
-- * Task progress notes can be written by the assignee or that project's
--   supervisor.
-- * Feedback can be written only by that project's supervisor, and only
--   after the report leaves DRAFT. The first feedback row moves a
--   SUBMITTED report to REVIEWED.
-- * Removing a person is done with is_active. A project is retired with
--   status ARCHIVED. Rows are not cascaded away, so history stays put.
-- * Task status includes CANCELLED, beyond the values drawn on Task_Update.
--   A cancelled task was dropped. That is different from DONE. The same
--   value is accepted on a task update so the snapshot can record it.

BEGIN;

-- ---------------------------------------------------------------------------
-- Enumerations
-- ---------------------------------------------------------------------------

CREATE TYPE user_role AS ENUM (
    'SUPERVISOR',
    'RESEARCH_ASSISTANT'
);

CREATE TYPE project_status AS ENUM (
    'PLANNED',
    'ACTIVE',
    'ON_HOLD',
    'COMPLETED',
    'ARCHIVED'
);

CREATE TYPE task_priority AS ENUM (
    'LOW',
    'MEDIUM',
    'HIGH'
);

CREATE TYPE task_status AS ENUM (
    'TODO',
    'IN_PROGRESS',
    'BLOCKED',
    'DONE',
    'CANCELLED'
);

CREATE TYPE report_status AS ENUM (
    'DRAFT',
    'SUBMITTED',
    'REVIEWED'
);

CREATE TYPE feedback_outcome AS ENUM (
    'APPROVED',
    'NEEDS_REVISION'
);

-- ---------------------------------------------------------------------------
-- Shared functions
-- ---------------------------------------------------------------------------

CREATE FUNCTION set_updated_at()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$;

CREATE FUNCTION reject_role_change()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF NEW.role IS DISTINCT FROM OLD.role THEN
        RAISE EXCEPTION 'user role cannot be changed after the account is created';
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION enforce_project_supervisor()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'UPDATE' AND NEW.supervisor_id IS NOT DISTINCT FROM OLD.supervisor_id THEN
        RETURN NEW;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM users
        WHERE user_id = NEW.supervisor_id
          AND role = 'SUPERVISOR'
          AND is_active
    ) THEN
        RAISE EXCEPTION 'supervisor_id % must be an active supervisor', NEW.supervisor_id;
    END IF;
    RETURN NEW;
END;
$$;

CREATE FUNCTION enforce_project_assistant_member()
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
    ELSIF NOT EXISTS (
        SELECT 1
        FROM users
        WHERE user_id = NEW.assistant_id
          AND role = 'RESEARCH_ASSISTANT'
    ) THEN
        RAISE EXCEPTION 'assistant_id % must be a research assistant', NEW.assistant_id;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM projects
        WHERE project_id = NEW.project_id
          AND supervisor_id = NEW.assistant_id
    ) THEN
        RAISE EXCEPTION 'the project supervisor cannot also be an assistant on that project';
    END IF;

    RETURN NEW;
END;
$$;

CREATE FUNCTION enforce_task_participants()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'INSERT'
       OR NEW.project_id IS DISTINCT FROM OLD.project_id
       OR NEW.created_by IS DISTINCT FROM OLD.created_by
    THEN
        IF NOT EXISTS (
            SELECT 1
            FROM projects AS p
            JOIN users AS u ON u.user_id = p.supervisor_id
            WHERE p.project_id = NEW.project_id
              AND p.supervisor_id = NEW.created_by
              AND u.role = 'SUPERVISOR'
              AND u.is_active
        ) THEN
            RAISE EXCEPTION 'created_by must be the active supervisor who owns the project';
        END IF;
    END IF;

    IF TG_OP = 'INSERT'
       OR NEW.project_id IS DISTINCT FROM OLD.project_id
       OR NEW.assigned_to IS DISTINCT FROM OLD.assigned_to
    THEN
        IF NOT EXISTS (
            SELECT 1
            FROM project_assistants
            WHERE project_id = NEW.project_id
              AND assistant_id = NEW.assigned_to
              AND is_active
        ) THEN
            RAISE EXCEPTION 'assigned_to must be an active research assistant on this project';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE FUNCTION enforce_task_update_author()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF TG_OP = 'UPDATE'
       AND NEW.task_id IS NOT DISTINCT FROM OLD.task_id
       AND NEW.updated_by IS NOT DISTINCT FROM OLD.updated_by
    THEN
        RETURN NEW;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM tasks AS t
        JOIN projects AS p ON p.project_id = t.project_id
        JOIN users AS u ON u.user_id = NEW.updated_by
        WHERE t.task_id = NEW.task_id
          AND u.is_active
          AND (
              NEW.updated_by = t.assigned_to
              OR NEW.updated_by = p.supervisor_id
          )
    ) THEN
        RAISE EXCEPTION 'task updates can be written by the assignee or the project supervisor';
    END IF;

    RETURN NEW;
END;
$$;

CREATE FUNCTION protect_task_progress()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    IF NEW.status IS NOT DISTINCT FROM OLD.status
       AND NEW.completion_percentage IS NOT DISTINCT FROM OLD.completion_percentage
    THEN
        RETURN NEW;
    END IF;

    IF current_setting('rams.syncing_task', true) = '1' THEN
        RETURN NEW;
    END IF;

    RAISE EXCEPTION 'change task status and completion percentage by inserting a task update';
END;
$$;

-- Keep the task row aligned with the newest progress log entry.
CREATE FUNCTION sync_task_progress_from_update()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
BEGIN
    PERFORM set_config('rams.syncing_task', '1', true);

    UPDATE tasks
    SET status = NEW.status,
        completion_percentage = NEW.completion_percentage,
        updated_at = NOW()
    WHERE task_id = NEW.task_id
      AND NOT EXISTS (
          SELECT 1
          FROM task_updates AS newer
          WHERE newer.task_id = NEW.task_id
            AND (
                newer.created_at > NEW.created_at
                OR (
                    newer.created_at = NEW.created_at
                    AND newer.update_id > NEW.update_id
                )
            )
      );

    PERFORM set_config('rams.syncing_task', '0', true);
    RETURN NEW;
END;
$$;

CREATE FUNCTION enforce_weekly_report_author()
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
            RAISE EXCEPTION 'weekly reports can be filed only by an active assistant on the project';
        END IF;
    END IF;

    RETURN NEW;
END;
$$;

CREATE FUNCTION enforce_report_feedback_author()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = public
AS $$
DECLARE
    report_state report_status;
BEGIN
    IF TG_OP = 'UPDATE'
       AND NEW.report_id IS NOT DISTINCT FROM OLD.report_id
       AND NEW.supervisor_id IS NOT DISTINCT FROM OLD.supervisor_id
    THEN
        RETURN NEW;
    END IF;

    SELECT wr.status
    INTO report_state
    FROM weekly_reports AS wr
    JOIN projects AS p ON p.project_id = wr.project_id
    WHERE wr.report_id = NEW.report_id
      AND p.supervisor_id = NEW.supervisor_id;

    IF report_state IS NULL THEN
        RAISE EXCEPTION 'feedback must be written by the supervisor who owns the report''s project';
    END IF;

    IF report_state = 'DRAFT' THEN
        RAISE EXCEPTION 'feedback cannot be written on a draft weekly report';
    END IF;

    IF TG_OP = 'INSERT' AND report_state = 'SUBMITTED' THEN
        UPDATE weekly_reports
        SET status = 'REVIEWED'
        WHERE report_id = NEW.report_id;
    END IF;

    RETURN NEW;
END;
$$;

-- ---------------------------------------------------------------------------
-- Tables
-- ---------------------------------------------------------------------------

CREATE TABLE users (
    user_id       BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name          VARCHAR(150) NOT NULL,
    email         VARCHAR(254) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          user_role NOT NULL,
    is_active     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT users_name_not_blank CHECK (length(btrim(name)) > 0),
    CONSTRAINT users_email_lowercase CHECK (email = lower(email)),
    CONSTRAINT users_email_format CHECK (
        email ~ '^[a-z0-9._%+-]+@[a-z0-9.-]+\.[a-z]{2,}$'
    ),
    CONSTRAINT users_email_unique UNIQUE (email),
    -- Reject a raw password. bcrypt hashes are 60 characters; argon2 is longer.
    CONSTRAINT users_password_is_hash CHECK (char_length(password_hash) >= 50)
);

CREATE TRIGGER users_set_updated_at
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER users_reject_role_change
BEFORE UPDATE ON users
FOR EACH ROW
EXECUTE FUNCTION reject_role_change();

CREATE TABLE projects (
    project_id    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    supervisor_id BIGINT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    title         VARCHAR(200) NOT NULL,
    description   TEXT,
    objectives    TEXT,
    start_date    DATE NOT NULL,
    end_date      DATE NOT NULL,
    status        project_status NOT NULL DEFAULT 'PLANNED',
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT projects_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT projects_dates_ordered CHECK (end_date >= start_date)
);

CREATE TRIGGER projects_set_updated_at
BEFORE UPDATE ON projects
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER projects_enforce_supervisor
BEFORE INSERT OR UPDATE ON projects
FOR EACH ROW
EXECUTE FUNCTION enforce_project_supervisor();

CREATE TABLE project_assistants (
    project_assistant_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id           BIGINT NOT NULL REFERENCES projects (project_id) ON DELETE RESTRICT,
    assistant_id         BIGINT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    assigned_at          TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    is_active            BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT project_assistants_member_once UNIQUE (project_id, assistant_id)
);

COMMENT ON TABLE project_assistants IS
    'One project has many research assistants. Each row is one assistant assigned to that project by its supervisor. The same assistant appears once per project.';

CREATE TRIGGER project_assistants_enforce_member
BEFORE INSERT OR UPDATE ON project_assistants
FOR EACH ROW
EXECUTE FUNCTION enforce_project_assistant_member();

CREATE TABLE tasks (
    task_id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id             BIGINT NOT NULL REFERENCES projects (project_id) ON DELETE RESTRICT,
    assigned_to            BIGINT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    created_by             BIGINT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    title                  VARCHAR(200) NOT NULL,
    description            TEXT,
    priority               task_priority NOT NULL DEFAULT 'MEDIUM',
    start_date             DATE NOT NULL,
    due_date               DATE NOT NULL,
    expected_deliverables  TEXT,
    status                 task_status NOT NULL DEFAULT 'TODO',
    completion_percentage  SMALLINT NOT NULL DEFAULT 0,
    is_active              BOOLEAN NOT NULL DEFAULT TRUE,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT tasks_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT tasks_dates_ordered CHECK (due_date >= start_date),
    CONSTRAINT tasks_percentage_range CHECK (
        completion_percentage BETWEEN 0 AND 100
    )
);

COMMENT ON COLUMN tasks.status IS
    'Current status. A new task_updates row overwrites this when it is the latest update.';
COMMENT ON COLUMN tasks.completion_percentage IS
    'Current percentage. Kept in step with the latest task_updates row.';
COMMENT ON COLUMN tasks.is_active IS
    'Hides a task from current lists. Workflow state lives in status.';

CREATE TRIGGER tasks_protect_progress
BEFORE UPDATE ON tasks
FOR EACH ROW
EXECUTE FUNCTION protect_task_progress();

CREATE TRIGGER tasks_set_updated_at
BEFORE UPDATE ON tasks
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER tasks_enforce_participants
BEFORE INSERT OR UPDATE ON tasks
FOR EACH ROW
EXECUTE FUNCTION enforce_task_participants();

CREATE TABLE task_updates (
    update_id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    task_id                BIGINT NOT NULL REFERENCES tasks (task_id) ON DELETE RESTRICT,
    updated_by             BIGINT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    status                 task_status NOT NULL,
    completion_percentage  SMALLINT NOT NULL,
    progress_note          TEXT NOT NULL,
    blocker_note           TEXT,
    created_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT task_updates_note_not_blank CHECK (length(btrim(progress_note)) > 0),
    CONSTRAINT task_updates_percentage_range CHECK (
        completion_percentage BETWEEN 0 AND 100
    ),
    CONSTRAINT task_updates_blocker_required CHECK (
        status <> 'BLOCKED'
        OR (
            blocker_note IS NOT NULL
            AND length(btrim(blocker_note)) > 0
        )
    )
);

COMMENT ON TABLE task_updates IS
    'Append-only progress log. One user writes many rows. Inserting a row refreshes the parent task.';

CREATE TRIGGER task_updates_set_updated_at
BEFORE UPDATE ON task_updates
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER task_updates_enforce_author
BEFORE INSERT OR UPDATE ON task_updates
FOR EACH ROW
EXECUTE FUNCTION enforce_task_update_author();

CREATE TRIGGER task_updates_sync_task
AFTER INSERT OR UPDATE ON task_updates
FOR EACH ROW
EXECUTE FUNCTION sync_task_progress_from_update();

CREATE TABLE weekly_reports (
    report_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id        BIGINT NOT NULL REFERENCES projects (project_id) ON DELETE RESTRICT,
    assistant_id      BIGINT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    week_start_date   DATE NOT NULL,
    week_end_date     DATE NOT NULL,
    work_completed    TEXT,
    current_tasks     TEXT,
    findings          TEXT,
    problems_support  TEXT,
    next_week_plan    TEXT,
    supporting_links  TEXT,
    status            report_status NOT NULL DEFAULT 'DRAFT',
    submitted_at      TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT weekly_reports_one_week CHECK (
        week_end_date - week_start_date BETWEEN 0 AND 6
    ),
    CONSTRAINT weekly_reports_submission_state CHECK (
        (
            status = 'DRAFT'
            AND submitted_at IS NULL
        )
        OR (
            status IN ('SUBMITTED', 'REVIEWED')
            AND submitted_at IS NOT NULL
            AND work_completed IS NOT NULL
            AND length(btrim(work_completed)) > 0
        )
    ),
    CONSTRAINT weekly_reports_one_per_week UNIQUE (
        project_id,
        assistant_id,
        week_start_date
    )
);

COMMENT ON COLUMN weekly_reports.current_tasks IS
    'Narrative list of tasks for the week. Not a foreign key to tasks in version 1.';
COMMENT ON COLUMN weekly_reports.supporting_links IS
    'One URL per line. A separate link table can wait until version 2.';
COMMENT ON COLUMN weekly_reports.created_at IS
    'When the draft was first saved. submitted_at stays null until the report is submitted.';

CREATE TRIGGER weekly_reports_set_updated_at
BEFORE UPDATE ON weekly_reports
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER weekly_reports_enforce_author
BEFORE INSERT OR UPDATE ON weekly_reports
FOR EACH ROW
EXECUTE FUNCTION enforce_weekly_report_author();

CREATE TABLE report_feedback (
    feedback_id   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    report_id     BIGINT NOT NULL REFERENCES weekly_reports (report_id) ON DELETE RESTRICT,
    supervisor_id BIGINT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    feedback_text TEXT NOT NULL,
    outcome       feedback_outcome NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT report_feedback_text_not_blank CHECK (length(btrim(feedback_text)) > 0)
);

COMMENT ON TABLE report_feedback IS
    'One report can receive many feedback rows, one per review round.';

CREATE TRIGGER report_feedback_set_updated_at
BEFORE UPDATE ON report_feedback
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER report_feedback_enforce_author
AFTER INSERT OR UPDATE ON report_feedback
FOR EACH ROW
EXECUTE FUNCTION enforce_report_feedback_author();

-- ---------------------------------------------------------------------------
-- Indexes for foreign keys that are not already the leftmost unique column
-- ---------------------------------------------------------------------------

CREATE INDEX projects_supervisor_id_idx
    ON projects (supervisor_id);

CREATE INDEX project_assistants_assistant_id_idx
    ON project_assistants (assistant_id);

CREATE INDEX tasks_project_id_idx
    ON tasks (project_id);

CREATE INDEX tasks_assigned_to_idx
    ON tasks (assigned_to);

CREATE INDEX tasks_created_by_idx
    ON tasks (created_by);

CREATE INDEX task_updates_task_id_idx
    ON task_updates (task_id, created_at);

CREATE INDEX task_updates_updated_by_idx
    ON task_updates (updated_by);

CREATE INDEX weekly_reports_assistant_id_idx
    ON weekly_reports (assistant_id);

CREATE INDEX report_feedback_report_id_idx
    ON report_feedback (report_id, created_at);

CREATE INDEX report_feedback_supervisor_id_idx
    ON report_feedback (supervisor_id);

COMMIT;
