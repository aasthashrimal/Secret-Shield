# Professor demonstration guide

This guide presents the deployed Secret Shield project without exposing passwords,
tokens, recipient addresses, raw scanner reports, Key Vault values, or Terraform
state. Use only synthetic accounts and the existing sanitized evidence.

## What is already running

The Azure deployment is already online. Docker Desktop, Maven, PostgreSQL, the
backend, and the frontend do **not** need to be started for the normal cloud demo.

- Website: <https://secretshield-dev-zboc5k-web.azurewebsites.net>
- Public liveness check:
  <https://secretshield-dev-zboc5k-api.azurewebsites.net/api/health/live>
- Final release tag:
  <https://github.com/aasthashrimal/Secret-Shield/tree/v1.0.0>
- Final post-release deployment run:
  <https://github.com/aasthashrimal/Secret-Shield/actions/runs/39261737192>

Anyone who knows the website URL can open the login page and register a Developer
account. Login is required for the dashboard and application APIs. Developers see
only their own repositories and related security records; administrator operations
are not available through public registration. Public availability therefore does
not mean that one user can view another user's dashboard data.

## Prepare before the presentation

Do this 15–30 minutes before the demonstration.

1. Use a private synthetic demonstration account. Do not put its password in this
   guide, slides, chat, or Git.
2. Open the website once so any Azure cold start completes before the presentation.
3. From PowerShell, verify both public paths:

   ```powershell
   $web = 'https://secretshield-dev-zboc5k-web.azurewebsites.net'
   $api = 'https://secretshield-dev-zboc5k-api.azurewebsites.net'

   (Invoke-WebRequest -Uri $web -UseBasicParsing -TimeoutSec 60).StatusCode
   Invoke-RestMethod -Uri "$api/api/health/live" -TimeoutSec 60
   Invoke-RestMethod -Uri "$web/api/health/live" -TimeoutSec 60
   ```

   Expected: `200`, followed by `status = UP` twice. A first request can be slower
   while the App Service container wakes up.
4. Log in and confirm that Overview, Repositories, Findings, Incidents, History,
   and Notifications load. Log out once and log back in to verify the session flow.
5. Open the GitHub run links used below in separate tabs. Keep the reviewed images
   under `docs/evidence/` ready in case GitHub or Azure is temporarily unavailable.
6. Keep `.env`, browser developer tools, Azure secret pages, raw logs, and email
   recipient details closed throughout the presentation.

## Recommended 10–12 minute demonstration

### 1. Problem and architecture — 1 minute

Explain that Secret Shield detects exposed credentials, blocks unsafe delivery,
stores sanitized findings, creates incidents and audit history, and sends high-risk
alerts. Show the [actual Azure architecture](azure-architecture-actual.md): React
and Nginx, Spring Boot, Neon PostgreSQL, GitHub Actions, GHCR, Azure App Service,
Key Vault, and Azure Communication Services Email.

### 2. Authentication and isolation — 1 minute

Open the website and sign in with the synthetic account. Point out that JWTs remain
in browser memory, logout clears the session, and public registration always creates
a Developer rather than an Administrator. Show the dashboard without exposing a
real email address. If useful, log out and attempt a protected page to show that it
returns to authentication.

Expected result: authentication succeeds, protected content loads only after login,
and logout ends the browser session.

### 3. Dashboard and repository ownership — 1 minute

Show the Overview totals and open an existing synthetic repository. Explain that a
repository owner can view its scans, findings, incidents, notification status, and
history, while authorization tests reject cross-owner access.

Expected result: the repository data and status badges load from the deployed Neon
database through the same-origin frontend proxy.

### 4. Clean CI and deployment — 1–2 minutes

Open [final run 39261737192](https://github.com/aasthashrimal/Secret-Shield/actions/runs/39261737192).
Show these successful jobs:

1. Secret scan and gate
2. Backend and frontend checks
3. Trusted release candidate
4. Publish exact-commit images
5. Deploy verified images to Azure

Explain that the one-time bootstrap publisher is correctly skipped. The workflow
deploys both exact-SHA images only after the checked commit is reported as clean.

Expected result: the workflow is green and the public application remains healthy.

### 5. Finding blocks delivery — 1–2 minutes

Open [finding run 39245401264](https://github.com/aasthashrimal/Secret-Shield/actions/runs/39245401264),
then show repository 5 / scan 9 in the dashboard if that evidence account is being
used. Point out the sanitized LOW finding metadata and `FINDINGS_DETECTED` result.

Expected result: the secret gate fails and build, release, publication, and Azure
deployment are skipped. Use the [blocked-run screenshot](evidence/m12-finding-blocked-run.png)
and [finding dashboard](evidence/m12-finding-dashboard.png) if the live record is
unavailable.

### 6. Scanner error fails closed — 1 minute

Open [scanner-error run 39056726283](https://github.com/aasthashrimal/Secret-Shield/actions/runs/39056726283).
Explain that scanner failure is stored separately from a detected finding, but both
prevent an unverified commit from reaching deployment.

Expected result: the scan job fails safely and downstream jobs remain skipped. The
fallback is the [scanner-error screenshot](evidence/m12-scanner-error-run.png).

### 7. Incident, audit trail, and notification — 2 minutes

Open the existing resolved HIGH incident 2 and show its creation and resolution
history. Show notification status `SENT` and attempt count `1` without opening the
recipient email or displaying its address or message body.

Explain that the finding gate remains blocked even after the incident is reviewed;
resolution records the response rather than rewriting the scan result. Use
the [incident and notification screenshot](evidence/m15-notification-incident.png)
as the fallback.

Do not create another HIGH/CRITICAL incident merely to demonstrate email. The
existing verified delivery is sufficient and avoids unnecessary or duplicate mail.

### 8. Azure operations and conclusion — 1–2 minutes

Show the sanitized Azure resource-group screenshot or the resource list, then explain
HTTPS, Key Vault references, OIDC deployment, bounded logs, Neon backup/restore,
exact-SHA rollback, and approval-only teardown. Finish by showing the `v1.0.0` tag
and the acceptance checklist.

## Notification behavior

Notification recipients are private **server-wide configuration**, not an address
stored on each user. The current deployment has one approved recipient alias, so
every new HIGH or CRITICAL incident from any user's repository queues mail to that
same configured address. LOW, MEDIUM, and INFO findings do not send email. The
dashboard stores and displays only the recipient alias and delivery status.

This setup is suitable for a controlled single-owner demonstration. It is not a
complete multi-tenant notification model. Before advertising the link broadly,
add invite-only or otherwise controlled registration, abuse/rate controls, and a
verified per-user or per-repository notification preference. Until then, share the
URL only with the professor/evaluators and use synthetic accounts.

## Local fallback when Azure is unavailable

The reviewed screenshots are the quickest fallback. For an interactive local
fallback, start Docker Desktop in Linux-container mode, then run from the repository
root:

```powershell
Set-Location 'C:\Aastha (D)\Projects\secret-shield'
docker info --format '{{.ServerVersion}}'
powershell -NoProfile -File .\scripts\init-compose-env.ps1
powershell -NoProfile -File .\scripts\prepare-scanner-fixtures.ps1
docker compose --env-file .env.compose up -d --build
docker compose --env-file .env.compose ps
Invoke-RestMethod http://localhost:5173/api/health/live
```

Wait for `db`, `backend`, and `frontend` to become healthy, then open
<http://localhost:5173>. The local stack uses a separate PostgreSQL database, so
cloud accounts and cloud evidence will not appear there. Local notifications are
not proof of Azure email delivery.

After the fallback demo, stop it without deleting its database:

```powershell
docker compose --env-file .env.compose down
```

Never add `--volumes` unless deliberately erasing the local Compose database.

## Presentation safety checklist

- Use only synthetic usernames, repositories, findings, and incident reasons.
- Never display `.env`, passwords, JWTs, CI credentials, connection strings,
  recipient addresses, Key Vault secret values, raw reports, or Terraform state.
- Do not merge code, trigger a new finding, rotate secrets, apply Terraform, or
  perform teardown during the presentation.
- If a live service fails, state that it is temporarily unavailable and use the
  reviewed evidence; do not describe a screenshot as a new live result.
- After the presentation, continue monitoring Azure credit and request a reviewed
  retention/teardown decision when the public deployment is no longer needed.
