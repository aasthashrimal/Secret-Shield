# Secret Shield

Secret Shield is a DevSecOps project for detecting exposed credentials, blocking
unsafe deployments, and tracking findings and incidents. The current checkout
implements the backend through M11: authentication and roles, repositories,
scans and findings, incident and audit history, the deployment gate, notification
delivery, and a React dashboard. The M12 GitHub workflow has live clean, finding,
and scanner-error evidence; its remaining reporting and deployment integration
depends on the separately approved M14/M15 cloud work.

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

The Java 21 target and 147 tests passed on Windows with disposable PostgreSQL on 18 October 2026;
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

For the M13 Docker stack with its own PostgreSQL database, start Docker Desktop,
then run from the repository root:

```powershell
powershell -NoProfile -File .\scripts\init-compose-env.ps1
docker compose --env-file .env.compose up -d --build
```

Open `http://localhost:5173`. Use `docker compose --env-file .env.compose down`
to stop while keeping database data. See the [Docker setup guide](docs/docker-setup.md)
for scanner fixtures, persistence checks, and troubleshooting.

M14 Azure infrastructure is in `infra/`. Terraform formatting, validation,
and three mocked plan tests passed. A $20 monthly Azure budget and protected
Blob state account are active. The approved 18-resource application plan was
applied in two passes after a provider-specific VNet setting correction.
Both HTTPS app URLs currently serve public placeholder pages; Secret Shield
has **not** been deployed. Four approved Neon/JWT secrets are stored in Key
Vault, and all four backend references report `Resolved`. The ongoing design
uses the existing Neon database;
Azure PostgreSQL and ACR are optional. Review the
[actual Azure inventory](docs/azure-architecture-actual.md),
[M14 apply review](docs/m14-plan-review.md), and
[Terraform setup](docs/azure-terraform-setup.md). The
[deployment guide](docs/deployment-guide.md) describes the prepared, disabled
publish/deploy workflow and its prerequisites.

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
- [GitHub security workflow](docs/github-workflow.md)
- [Azure cloud design](docs/cloud-design.md)
- [Actual Azure architecture and inventory](docs/azure-architecture-actual.md)
- [Terraform operator steps](docs/azure-terraform-setup.md)
- [Final acceptance evidence](docs/acceptance-checklist.md)
- [Partial deliverable security audit](docs/security-audit.md)
- [Remaining cloud prerequisites](docs/remaining-prerequisites.md)
- [Technical report draft](docs/technical-report.md)
- [Demonstration script draft](docs/demo-script.md)
- [Page/documentation review checklist](docs/page-review-checklist.md)
