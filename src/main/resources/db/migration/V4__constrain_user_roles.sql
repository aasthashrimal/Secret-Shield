-- Refuse unknown historical values instead of silently granting or removing access.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM users
        WHERE upper(btrim(role)) NOT IN ('DEVELOPER', 'ADMIN')
    ) THEN
        RAISE EXCEPTION 'Unsupported historical user role; resolve it before migration';
    END IF;
END $$;

UPDATE users SET role = upper(btrim(role)) WHERE role <> upper(btrim(role));
ALTER TABLE users ALTER COLUMN role TYPE VARCHAR(20);
ALTER TABLE users ADD CONSTRAINT users_role_allowed CHECK (role IN ('DEVELOPER', 'ADMIN'));
