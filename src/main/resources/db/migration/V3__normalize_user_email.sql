-- Refuse ambiguous identities before changing any data. PostgreSQL migrations are transactional.
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM users GROUP BY lower(btrim(email)) HAVING count(*) > 1) THEN
        RAISE EXCEPTION 'Email normalization collision; resolve duplicate identities before migration';
    END IF;
END $$;

UPDATE users SET email = lower(btrim(email)) WHERE email <> lower(btrim(email));
CREATE UNIQUE INDEX users_normalized_email_unique ON users (lower(btrim(email)));
ALTER TABLE users ADD CONSTRAINT users_email_normalized CHECK (email = lower(btrim(email)));
