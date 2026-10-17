# Dashboard development and review

M11 adds a React/TypeScript frontend in `frontend/` and the authorized
`GET /api/dashboard/overview` endpoint. The frontend uses the existing repository,
scan, finding, incident, audit, and notification APIs. Its JWT stays in memory;
refreshing the browser tab signs you out. Log out clears it immediately. The browser
never stores credentials or tokens in local/session storage.

## Run locally

Start the backend from the project root in one PowerShell terminal. The helper
loads your private `.env`:

```powershell
.\scripts\start-local.ps1
```

In another terminal:

```powershell
cd frontend
npm ci --offline=false
npm run dev
```

Open `http://localhost:5173`. Vite proxies `/api` to `http://localhost:8080`.
Register or sign in, create a repository, then inspect its scans and incidents.
To run a manual scan, prepare a checkout under the configured scanner workspace
and use its exact branch and commit SHA as described in
[scanning guide](scanning-guide.md). The scan detail page shows its findings and
links to incident review. Only an administrator can ignore an incident; owners
can resolve or reopen it with a reason. Conflicts prompt a refresh. The
notification panel shows aliases and safe delivery status, not addresses.

The dashboard summary counts scan and finding occurrences, incident records,
failed notification deliveries, recent audit activity, and the gate result for
each recent repository. `from`/`to` API filters are UTC and exclude the end time.
Repository inventory counts are current rather than time-filtered. The recent
activity and repository security lists each show at most 10 records.

## Checks

```powershell
cd frontend
npm test
npm run build
cd ..
.\scripts\test-postgres.ps1 -Offline
.\scripts\test-postgres.ps1 -Offline -DashboardSmoke
```

The frontend tests cover login, logout, session expiry, safe 403/409 errors,
and an incident versioned transition. The backend disposable PostgreSQL suite
covers dashboard aggregation, ownership, dates, and an invalid CLEAN scan. The
`-DashboardSmoke` option also starts the disposable backend and drives a real
browser through registration, a repository and finding, incident resolution, and
the still-blocked gate. It uses only synthetic data and writes separate live
browser screenshots to `docs/evidence/`.
four real-scanner fixture tests remain opt-in per [testing guide](testing.md).

For a browser presentation check with synthetic API responses and the installed
Microsoft Edge, run from `frontend/`:

```powershell
node scripts/capture-ui.mjs
```

This produces sanitized desktop and mobile screenshots in
[`docs/evidence`](evidence/). The 17 October 2026 review covered login,
overview, repository, scan, and incident pages at desktop width and the overview
at 390px mobile width. It checked navigation, visible labels, safe fixture content,
no console errors, no page-wide mobile overflow, and no browser-storage session.
These screenshots use synthetic responses; they are not evidence of a live
backend/browser session or an Azure notification. The disposable live browser
check uses a synthetic machine-ingested finding. A manually started Gitleaks scan
still requires a prepared local scanner checkout and account.
