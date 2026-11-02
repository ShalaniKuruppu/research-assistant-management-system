-- Add the Overleaf link submitted by the assigned research assistant.
ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS paper_link VARCHAR(1000);
