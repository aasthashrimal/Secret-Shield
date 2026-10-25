# Secret Shield technical report

**Verified 25 October 2026:** Secret Shield is implemented, tested and publicly
available on Render with its existing Neon PostgreSQL data. The original Azure
deployment was verified for the presentation and then intentionally retired.
Final run `39247034941` published and deployed exact commit
`8e36c800eecfb338bca68c4654393b37db0eff03`. Backend, frontend and the
same-origin API proxy returned HTTP 200.

## Problem and scope

Secret Shield detects possible credentials in Git history, blocks unsafe
delivery, and gives repository owners a place to investigate findings and
incidents. It distinguishes `CLEAN`, `FINDINGS_DETECTED`, and scanner `FAILED`
outcomes; findings and tool failures fail closed. It manages scan evidence and
response workflow but does not rotate provider credentials automatically.

## Architecture and modules

The system combines a Spring Boot API, PostgreSQL/Flyway data layer, React/Nginx
dashboard, pinned Gitleaks scanner, GitHub Actions gate, Docker Compose, and
Terraform-managed cloud infrastructure. JWT and DEVELOPER/ADMIN authorization protect owner-scoped
resources. Sanitization removes raw match values before persistence, HTTP, logs
or artifacts. Findings link to auditable incidents; HIGH/CRITICAL events use a
bounded retryable notification outbox.

The verified presentation deployment used two Linux App Services on one B1 plan,
Key Vault references, managed identities, VNet integration, ACS Email, protected
Terraform state, and the existing TLS Neon database. The current portfolio site
uses a free Render Docker backend and static frontend with the same Neon database.
See the [data model](data-model.md),
[actual architecture](azure-architecture-actual.md), and
[deployment guide](deployment-guide.md).

M00-M11 implement foundations, APIs, scanner, notifications and dashboard. M12
enforces scan/report/build/release boundaries. M13 supplies containers. M14
provisions infrastructure. M15 deploys exact checked commits and defines
operations. M16 records final tests, audit and demonstration evidence.

## Security and DevOps decisions

- Gitleaks 8.30.0 and third-party Actions are pinned.
- PR workflows receive read-only source access and no backend/cloud secret.
- Trusted `main` reporting sends only rule/path/line metadata through a scoped
  machine credential with bounded retries and run identity.
- Publication requires clean scan, acknowledged report and successful build for
  the same commit. Deployment also requires exact public images and protected
  `azure-dev` OIDC.
- Runtime secrets are Key Vault references. HTTPS is required, basic publishing
  authentication is disabled, and logs have bounded retention.
- Terraform created 18 approved application resources. Azure PostgreSQL and ACR
  were excluded; Neon and public exact-SHA GHCR images are used.

## Verified results

- 147 PostgreSQL tests, six frontend tests and five workflow/reporting tests
  passed; both containers built.
- Clean scan 8 matched trusted run `39242095907` once. Disposable run
  `39245401264` created scan 9 with one LOW sanitized finding and skipped every
  downstream job. PR #8 demonstrated controlled scanner failure.
- [Final run `39247034941`](https://github.com/aasthashrimal/Secret-Shield/actions/runs/39247034941)
  passed scan, build, release, exact-SHA publication and Azure deployment.
- Browser registration/login/dashboard/logout/relogin passed.
- Live HIGH evidence covered three-attempt provider failure and later
  one-attempt `SENT`; incident 2 resolution/history passed while its gate stayed
  blocked.
- A private 39-file cloud-log audit found no private-value or credential pattern
  after final suppression. A Neon dump restored tables and Flyway history into
  disposable PostgreSQL 18.
- The [security audit](security-audit.md) found no real secret in deliverables or
  four local images. Temporary credential 4 is revoked and its GitHub settings
  were removed.
- All seven live presentation-path checks passed; reviewed fallback screenshots
  are under `docs/evidence/`.

## Limitations and retention

The teammate was unavailable, so collaborator workflow and second-machine
reproduction remain deferred. Render's free backend sleeps after inactivity and
may take about a minute to wake. ACS email is disabled on the current portfolio
deployment; the verified Azure delivery evidence remains in the report and
screenshots.

On 25 October 2026, a private Neon dump passed a disposable restore test before an
explicitly approved Terraform plan deleted exactly 18 Azure application resources.
The Azure application resource group is gone, while Neon data, the private backup,
private final empty-state backup, source, GHCR images, and sanitized historical
evidence remain. The Azure state storage account was then separately deleted.
Current hosting therefore does not depend on any active Azure resource group.

## Future work

Possible extensions include provider-side credential rotation, scheduled restore
verification, richer observability, provider installation flows and independent
setup reproduction. These are future work, not current claims.
