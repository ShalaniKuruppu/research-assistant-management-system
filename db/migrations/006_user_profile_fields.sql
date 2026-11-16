-- Optional profile details for research assistant directory cards.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone VARCHAR(40),
    ADD COLUMN IF NOT EXISTS summary TEXT,
    ADD COLUMN IF NOT EXISTS profile_image_url VARCHAR(1000);
