-- Research assistant reimbursement claims and receipt uploads.
CREATE TYPE reimbursement_status AS ENUM (
    'DRAFT',
    'SUBMITTED',
    'APPROVED',
    'REJECTED'
);

CREATE TABLE reimbursements (
    reimbursement_id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    project_id       BIGINT NOT NULL REFERENCES projects (project_id) ON DELETE RESTRICT,
    assistant_id     BIGINT NOT NULL REFERENCES users (user_id) ON DELETE RESTRICT,
    title            VARCHAR(200) NOT NULL,
    description      TEXT,
    amount           NUMERIC(12, 2) NOT NULL,
    currency         CHAR(3) NOT NULL DEFAULT 'LKR',
    expense_date     DATE NOT NULL,
    status           reimbursement_status NOT NULL DEFAULT 'DRAFT',
    submitted_at     TIMESTAMPTZ,
    reviewed_at      TIMESTAMPTZ,
    reviewer_note    TEXT,
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT reimbursements_title_not_blank CHECK (length(btrim(title)) > 0),
    CONSTRAINT reimbursements_amount_positive CHECK (amount > 0),
    CONSTRAINT reimbursements_currency_format CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT reimbursements_submission_state CHECK (
        (
            status = 'DRAFT'
            AND submitted_at IS NULL
            AND reviewed_at IS NULL
        )
        OR (
            status = 'SUBMITTED'
            AND submitted_at IS NOT NULL
            AND reviewed_at IS NULL
        )
        OR (
            status IN ('APPROVED', 'REJECTED')
            AND submitted_at IS NOT NULL
            AND reviewed_at IS NOT NULL
        )
    )
);

CREATE FUNCTION enforce_reimbursement_author()
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
            RAISE EXCEPTION 'reimbursements can be filed only by an active assistant on the project';
        END IF;
    END IF;
    RETURN NEW;
END;
$$;

CREATE TRIGGER reimbursements_set_updated_at
BEFORE UPDATE ON reimbursements
FOR EACH ROW
EXECUTE FUNCTION set_updated_at();

CREATE TRIGGER reimbursements_enforce_author
BEFORE INSERT OR UPDATE ON reimbursements
FOR EACH ROW
EXECUTE FUNCTION enforce_reimbursement_author();

CREATE TABLE reimbursement_receipts (
    receipt_id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    reimbursement_id   BIGINT NOT NULL REFERENCES reimbursements (reimbursement_id) ON DELETE CASCADE,
    original_filename  VARCHAR(255) NOT NULL,
    content_type       VARCHAR(120) NOT NULL,
    file_size          BIGINT NOT NULL,
    storage_url        VARCHAR(1000) NOT NULL,
    uploaded_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT reimbursement_receipts_filename_not_blank CHECK (length(btrim(original_filename)) > 0),
    CONSTRAINT reimbursement_receipts_size_positive CHECK (file_size > 0)
);

CREATE INDEX reimbursements_project_id_idx
    ON reimbursements (project_id);

CREATE INDEX reimbursements_assistant_id_idx
    ON reimbursements (assistant_id);

CREATE INDEX reimbursements_status_idx
    ON reimbursements (status);

CREATE INDEX reimbursement_receipts_reimbursement_id_idx
    ON reimbursement_receipts (reimbursement_id);
