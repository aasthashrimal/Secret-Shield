# Secret Shield

Secret Shield is a DevSecOps project for detecting exposed credentials, blocking
unsafe deployments, and tracking findings and incidents. The current checkout
implements the backend foundation and authentication: registration/login/me with JWT, safe JSON errors,
UTC user timestamps, liveness, PostgreSQL migrations, and isolated tests.
Scanning, dashboard, notifications and cloud deployment are later modules.

Modules 0, 1 and 2 are currently maintained by Member 1 alone. GitHub collaboration
is deferred; no second-machine reproduction is claimed.

Use Java 21, Windows PowerShell, the checked-in Maven wrapper, and PostgreSQL server
tools. See [local setup](docs/local-setup.md) for private environment values and
the existing-database backup/adoption procedure. Spring Boot itself does not load `.env`.

```powershell
.\mvnw.cmd -DskipTests compile
.\mvnw.cmd test
powershell -NoProfile -File .\scripts\test-postgres.ps1
powershell -NoProfile -File .\scripts\start-local.ps1
```

In another terminal while the backend is running:

```powershell
powershell -NoProfile -File .\scripts\check-local.ps1
```

On 11 October 2026, Java 21 compilation and all 70 tests passed on Windows with
PostgreSQL 18.6. The full runner creates and removes a separate PostgreSQL cluster;
it does not use the development database or `.env`.

- [Module checklist](MODULE_TODO.md)
- [API contract and planned DTOs](docs/api-contract.md)
- [Current and planned data model](docs/data-model.md)
- [Database migrations](docs/database-migrations.md)
- [Tests and isolation](docs/testing.md)
- [Authentication session policy](docs/auth-session.md)
- [Authentication manual checks](docs/auth-manual-checks.md)
- [Runtime configuration](docs/runtime-configuration.md)
- [Final acceptance evidence](docs/acceptance-checklist.md)
- [Page/documentation review checklist](docs/page-review-checklist.md)
