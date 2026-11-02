-- Add the research plan submitted by an assigned research assistant.
ALTER TABLE projects
    ADD COLUMN IF NOT EXISTS research_plan TEXT;
