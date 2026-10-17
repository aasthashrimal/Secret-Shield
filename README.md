# Secret Shield

Secret Shield is a DevSecOps project for detecting exposed credentials, blocking
unsafe deployments, and tracking findings and incidents. The current checkout
implements the backend through M11: authentication and roles, repositories,
scans and findings, incident and audit history, the deployment gate, notification
delivery, and a React dashboard. Cloud deployment and GitHub workflow integration
are later modules.

Modules 0 through 11 are currently maintained by Member 1 alone. GitHub collaboration
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

The Java 21 target and 146 tests passed on Windows with disposable PostgreSQL;
four real-scanner tests remain opt-in. The full runner creates and removes a separate PostgreSQL cluster;
it does not use the development database or `.env`.

Run the dashboard in a second terminal after starting the backend:

```powershell
cd frontend
npm ci --offline=false
npm run dev
```

Open `http://localhost:5173`. See the [dashboard guide](docs/dashboard-guide.md)
for the UI checks and disposable browser walkthrough.

- [Module checklist](MODULE_TODO.md)
- [API contract and planned DTOs](docs/api-contract.md)
- [Current and planned data model](docs/data-model.md)
- [Database migrations](docs/database-migrations.md)
- [Tests and isolation](docs/testing.md)
- [Authentication session policy](docs/auth-session.md)
- [Authentication manual checks](docs/auth-manual-checks.md)
- [Roles, authorization and admin bootstrap](docs/user-roles.md)
- [Repository management and synthetic fixtures](docs/repository-management.md)
- [Scan management and CI ingestion](docs/scan-management.md)
- [Runtime configuration](docs/runtime-configuration.md)
- [Dashboard development and review](docs/dashboard-guide.md)
- [Final acceptance evidence](docs/acceptance-checklist.md)
- [Page/documentation review checklist](docs/page-review-checklist.md)
