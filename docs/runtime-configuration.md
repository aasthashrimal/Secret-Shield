# Runtime configuration and liveness

The default profile is local. Shared configuration contains environment references
only. `.env` is ignored and is not loaded automatically by Spring Boot;
`scripts/start-local.ps1` privately loads its core backend values plus optional frontend,
scanner, and notification settings.
See [local setup](local-setup.md).

| Profile | Database | Schema policy | JWT secret |
| --- | --- | --- | --- |
| local (default) | DB_URL/DB_USERNAME have localhost/postgres defaults; DB_PASSWORD required | Flyway migrations, Hibernate validate | Required JWT_SECRET |
| test (test classpath) | Dedicated TEST_DB_* with no development fallback; disposable marker guard | Flyway migrations, Hibernate validate | Synthetic pinned test-only value |
| Other explicit profiles | Required DB_URL/DB_USERNAME/DB_PASSWORD | Flyway migrations, Hibernate validate | Required JWT_SECRET |

Deployment must select/configure its own profile. No deployment configuration is
introduced in Modules 0/1. Flyway baseline-on-migrate is false, clean is disabled,
and adoption is false by default. See [migration procedures](database-migrations.md).

`spring.jpa.open-in-view` is false. Persistence work and lazy loading belong inside
service transactions; controllers return safe DTOs. SQL logging is disabled.
The application generates UTC `LocalDateTime` values and stores them directly as
timestamp-without-time-zone values. A JDBC timezone override would shift these
values again on machines outside UTC. JPA @PrePersist sets createdAt/updatedAt to the same UTC value;
@PreUpdate changes only updatedAt. Neither timestamp has a public setter and
createdAt is not updatable by Hibernate. Bulk SQL/JPQL bypass these callbacks.

Storage remains TIMESTAMP(6) WITHOUT TIME ZONE, represented by LocalDateTime.
Older records written before the JDBC override was removed may have an offset
that depends on the writer's local timezone. Do not mass-shift existing rows
without a verified backup and knowledge of the original writer timezone.
V2 backfills missing updated_at from created_at and enforces NOT NULL. Historical
creation values are preserved without guessing their timezone. New timestamps are UTC;
future timestamp DTOs must emit ISO-8601 UTC with Z as specified in the API contract.

Public GET /api/health/live returns 200 and only `{"status":"UP"}`. It does not
query the database/JWT/scanner or disclose dependency/configuration details. The
JWT filter skips this exact GET, including requests with a malformed bearer value
and requests under a servlet context path. Neighboring paths/methods remain protected.
This is process liveness; complete startup still requires valid DB/JWT configuration.
GET /api/health requires authentication.

## Verification

The initial M01 verification passed 40 database-independent and 52 full tests.
After M02/M03, Java 21 passes 54 database-independent and 86 full disposable
PostgreSQL tests. See [testing](testing.md).

Real local startup through the backup/adoption helper succeeded. The supported
existing schema was baselined and migrated; a nonempty logical backup was retained
privately under ignored target/database-backups. All seven checks in check-local.ps1
passed; no users were created or changed by smoke requests. The temporary application
was stopped afterwards. Earlier M01.4 verification used Hibernate update; that policy
has now been replaced by versioned migrations and validation.

M03 follow-up: all 54 database-independent and 86 full tests pass. V4 constrains
roles; registration cannot assign ADMIN; bootstrap, admin-only access, cross-owner
and nested-resource denials are covered. V4 was applied successfully to the configured
Neon database, followed by seven live checks and an anonymous admin-route 401 check.
See [roles and authorization](user-roles.md).
