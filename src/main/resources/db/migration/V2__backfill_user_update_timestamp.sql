ALTER TABLE users ADD COLUMN IF NOT EXISTS updated_at TIMESTAMP(6) WITHOUT TIME ZONE;
-- Preserve existing creation timestamps and hashes. Do not guess historical timezones.
UPDATE users SET updated_at = created_at WHERE updated_at IS NULL;
ALTER TABLE users ALTER COLUMN updated_at SET NOT NULL;
