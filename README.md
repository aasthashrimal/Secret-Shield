# Secret Shield

Secret Shield is a deployed DevSecOps project for detecting exposed credentials,
blocking unsafe deployments, and tracking findings and incidents. It implements
authentication and roles, owner-scoped repositories, scans and sanitized findings,
incident and audit history, a deployment gate, high-risk email notification, a
React dashboard, protected GitHub CI/CD, containers, Terraform-provisioned Azure
evidence, and a free Render portfolio deployment backed by Neon PostgreSQL.

Release `v1.0.0` and the final Azure workflow passed the secret gate,
backend/frontend checks, exact-commit publication, deployment, and live health
checks. After the presentation, the public application moved to Render and the
18 Terraform-managed Azure application resources were safely retired on
25 October 2026. Historical Azure evidence remains in this repository.

## Live portfolio deployment

- Frontend: <https://secret-shield-zboc5k.onrender.com>
- Backend liveness: <https://secret-shield-api-zboc5k.onrender.com/api/health/live>
- Database: existing managed Neon PostgreSQL

The Render backend uses the free tier and may sleep after inactivity, so its first
request can take about a minute. Azure Communication Services email is not active
on the portfolio deployment; its successful delivery evidence is retained below.

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

M14 Azure infrastructure is reproducible from `infra/`. Terraform formatting,
validation, mocked plan tests, the approved 18-resource apply, and the final
18-resource destroy all passed. The deployment verified App Service, Key Vault,
managed identity/OIDC, networking, ACS Email, Blob-backed Terraform state, GHCR,
Neon, logging, notification, backup, and recovery paths. The application resources
were intentionally deleted after a verified Neon backup and successful Render
migration; the protected Terraform state and sanitized evidence remain. Review the
[actual Azure inventory](docs/azure-architecture-actual.md),
[M14 apply review](docs/m14-plan-review.md), and
[Terraform setup](docs/azure-terraform-setup.md). The
[deployment guide](docs/deployment-guide.md) preserves the historical protected
Azure workflow and its operational controls. The
[Render migration record](docs/render-migration.md) describes current hosting.

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
- [Final deliverable security audit](docs/security-audit.md)
- [Technical report](docs/technical-report.md)
- [Current Render hosting and Azure retirement](docs/render-migration.md)
