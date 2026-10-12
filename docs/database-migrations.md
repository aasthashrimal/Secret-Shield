# PostgreSQL migrations

M01.2 uses Flyway with Hibernate `ddl-auto: validate` for all profiles. Flyway
migrates first; Hibernate validates mappings without creating, updating, or dropping
tables. SQL logging is disabled. Migration checksums/history are authoritative.
Automatic baseline-on-migrate is disabled and Flyway clean is disabled.

M02 adds V3: stored email is trimmed/lowercased, a normalized unique index prevents
case/space variants, and a check constraint requires canonical stored values.
It aborts transactionally if existing users collide after normalization; it does
not merge/delete accounts. Normal startup applies V3 to databases already at V2.
Back up important existing data before applying new migrations (the startup helper's
AdoptExistingDatabase switch also makes a backup when Flyway history already exists).
If a collision is reported, stop and review the affected identities privately,
resolve them deliberately with a backup, and rerun. Do not delete users or repair
history blindly. Tests prove collision refusal preserves both rows and V2 history.

M03 adds V4. It trims and uppercases supported historical role values, narrows the
column to `VARCHAR(20)`, and adds a check constraint allowing only `DEVELOPER` or
`ADMIN`. Any other existing role aborts the transactional migration before it changes
that value. Resolve unknown roles deliberately from a private backup; do not map them
to ADMIN automatically. Hibernate then maps the column to `UserRole` with `EnumType.STRING`.

M04 adds V5. It creates `project_repositories`, preserves owner records with a
restrictive foreign key, enforces owner-scoped normalized GitHub identity, verifies
the derived canonical URL, and adds list indexes. Repository removal is intentionally
absent; deactivation keeps the row available for later scan and audit history.

## Fresh database

Create an empty database with your local PostgreSQL tools, configure `.env`, and
run `scripts/start-local.ps1`. Only the `public` schema is managed.
V1 creates the original six-column `users` schema (generated BIGINT identity,
unique username/email, password hash, role, created_at). V2 adds updated_at,
backfills NULL values from created_at, and enforces NOT NULL. Both timestamps are
`TIMESTAMP(6) WITHOUT TIME ZONE`; new JPA callback values represent UTC.

## Existing local database

Stop other writers first. Run:

```powershell
powershell -NoProfile -File .\scripts\start-local.ps1 -AdoptExistingDatabase
```

Before the app starts, `pg_dump -Fc` creates a complete logical backup under
`target/database-backups/<random-id>/before-adoption.dump`. A backup failure stops
the procedure. The password is passed privately via PGPASSWORD, never as a command
argument. This helper supports explicit `jdbc:postgresql://host:port/database`
URLs without embedded credentials/options. Use matching/newer PostgreSQL backup
tools; `-PostgresBin` selects an installation.

Without existing Flyway history, the adoption strategy requires `users` to be the
only table in the managed schema. It checks the six required column names/types,
nullability, varchar lengths, generated ID, primary key and separate username/email
unique indexes. The nullable/non-null updated_at added by M01.4 is accepted too.
Unsupported schemas are refused before baseline creation. Explicit adoption then
records **baseline version 0**, so V1 still runs with `CREATE TABLE IF NOT EXISTS`
and V2 backfills/enforces updated_at. Never manually baseline at version 1 or 2.
Existing users, IDs, password hashes, roles and creation times are preserved.

After successful adoption, omit the switch on all normal starts. Existing Flyway
history is validated and migrations are applied normally; adoption does not bypass
checksum validation. The application flag defaults to false.

Historical created_at values may reflect an older local timezone. V2 copies their
stored values unchanged and does not silently infer/convert a timezone. Audit and
convert such historical values separately only with a known timezone and backup.

## Verification and recovery

The disposable PostgreSQL suite tests fresh migration, repeated migration, adoption
with a synthetic existing user/hash, nullable updated_at backfill, missing opt-in,
and incompatible-schema rejection. No development credentials are used in those tests.

Keep the printed backup path privately. `target/` is ignored and removed by Maven
clean, so copy important backups outside it first. Backups contain private user data
and hashes; never upload them as test evidence. To inspect a backup without restoring:

```powershell
& 'C:\Program Files\PostgreSQL\18\bin\pg_restore.exe' --list 'C:\private\before-adoption.dump'
```

For recovery, stop the app and restore into a **new empty recovery database** with
matching PostgreSQL tooling, then verify its rows/schema before changing DB_URL.
Use `pg_restore -h localhost -U postgres -W -d <new-recovery-database> <backup-file>`.
Do not use `--clean` against your working database or delete it to troubleshoot.
Do not edit applied V1–V5 scripts; future changes need new versioned migrations.
