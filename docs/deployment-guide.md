# Azure deployment and operations (infrastructure live; app not deployed)

The approved [M14 infrastructure](azure-architecture-actual.md) now has two
Linux App Services on one B1 plan in Central India, Key Vault, and ACS Email;
the database remains on Neon. Both apps still serve public placeholder pages.
No instruction below is evidence of a live Secret Shield deployment. The student credit
display expires **15 November 2026**; check renewal before promising a
permanent GitHub website link. The USD 20 monthly budget sends alerts but does
not shut down billable resources.

## Activation order after separate approval of each consequential action

1. **Completed:** the approved 18-resource M14 plan was applied and its
   [actual inventory](azure-architecture-actual.md) verified. Do not reapply
   `infra/m14.tfplan`; inspect a fresh plan before any future infrastructure
   change. Both apps currently run public Microsoft placeholder containers.
2. **Completed for the four core secrets:** the private Neon JDBC URL with
   TLS, username, password, and JWT secret passed local validation and were
   uploaded after separate approval. Azure reports all four Key Vault app
   setting references `Resolved`. The helper
   `powershell -NoProfile -File scripts/prepare-azure-secrets.ps1` can validate
   future input without uploading. Do not rerun with `-Apply` unless rotating
   those values: it creates new secret versions. `-IncludeNotifications` is
   separate and still requires an approved recipient, real ACS connection
   string and sender, plus separate review.
3. Install and authenticate GitHub CLI, then run
   `powershell -NoProfile -File scripts/prepare-github-environment.ps1` to
   preview the environment setup. After GitHub changes are approved and M14
   outputs exist, `-Apply` creates `azure-dev` with a `main`-only deployment
   branch rule and sets the non-secret OIDC identity IDs. It leaves publishing
   and deployment switches off. Protect `main` with required checks **Secret
   scan and gate** and **Backend and frontend checks**; review the actual
   GitHub settings before enabling deployment.
4. **Bootstrap the first real backend before CI reporting.** The placeholder
   cannot acknowledge a report, so a normal release candidate cannot yet
   publish. After separately approving one-time image publication, enable
   `BOOTSTRAP_PUBLISH_ENABLED` with
   `scripts/prepare-github-environment.ps1 -Apply -EnableBootstrapPublishing`.
   Manually dispatch **Security CI** on `main`. The bootstrap job needs a
   `CLEAN` scan and passing backend/frontend checks, then publishes only that
   exact SHA; it never deploys. Make both GHCR packages public after reviewing
   their visibility. Preview
   `scripts/bootstrap-cloud-deploy.ps1 -CommitSha <40-char-sha> -RunId <run-id>`;
   after separate deployment approval, add `-Apply`. It verifies the GitHub
   run and anonymous image manifests, switches both apps, and checks real
   backend/proxy health. Disable the one-time flag using
   `-DisableBootstrapPublishing` on the GitHub environment helper. Keep the
   normal publish/deploy flags off until reporting succeeds.
5. Once the real backend is healthy, run
   `powershell -NoProfile -File scripts/prepare-ci-reporting.ps1` for a local
   preview. After approval, `-Apply` asks for your Secret Shield login,
   finds/creates the repository record, issues one machine credential, and
   stores it in GitHub Actions secrets. The one-time value is never printed.
   A failure attempts to revoke the issued credential. A trusted `main` push
   must get a matching backend acknowledgement before it can publish images.
6. After approving normal GHCR publication, run
   `powershell -NoProfile -File scripts/prepare-github-environment.ps1 -Apply
   -EnablePublishing`. The next clean, reported `main` push builds and pushes
   backend/frontend images tagged with that exact commit SHA. GHCR packages
   under a personal account start **private**; GitHub currently requires the
   package owner to make both packages **Public** in each package's settings.
   This visibility change is consequential and cannot be assumed from the
   public source repository. The deploy job checks anonymous image pulls and
   fails before Azure sign-in if either image remains private.
7. Once Key Vault references resolve, the environment and required checks
   are verified, images are public, and Azure deployment is approved, run
   `powershell -NoProfile -File scripts/prepare-github-environment.ps1 -Apply
   -EnablePublishing -EnableDeployment`. A new clean, reported `main` push
   uses an environment-scoped GitHub OIDC token, changes both App Services to
   the same SHA-tagged images, and checks backend, frontend, and `/api` proxy
   health. The workflow does **not** grant Azure credentials to PR jobs.

The current Terraform variables still select placeholder images. After the
first CI deployment, update the ignored `infra/terraform.tfvars` to
`image_source="ghcr"` and `image_tag="<deployed 40-character SHA>"` before any
later Terraform plan. Otherwise Terraform may propose reverting the apps to
placeholders. Review every change/destroy action before an infrastructure
apply. The CI job updates only the app image settings, using `Website
Contributor` on those two apps; it does not access Terraform state.

## Live checks and incident response

After deployment, verify `GET /api/health/live` on the backend and through the
frontend proxy. Then use a synthetic account to log in and read the dashboard;
the liveness endpoint alone does not test Neon, migrations, or JWT. Check one
trusted clean GitHub run and compare its run ID, attempt, and SHA with the
backend scan record. A synthetic-finding or scanner-error run must skip build,
publish, and deploy. Validate an incident transition, audit event, blocked
gate, and approved high-risk recipient with ACS delivery evidence before
checking M10.4/M15.3 complete.

Use the backend scan/gate/notification APIs for safe operational status. The
`FAILED` scan outcome, reporting acknowledgement, notification `FAILED`
status, attempt count, and `safeErrorCode` are the first diagnostic signals.
For container startup failures, enable/stream App Service container logs only
after checking log access and retention; [Microsoft's App Service guide](https://learn.microsoft.com/en-us/azure/app-service/configure-custom-container)
documents `az webapp log config` and `az webapp log tail`. Never publish raw
scanner reports, JWTs, DB URLs/passwords, ACS keys, recipient addresses, or
whole Terraform state/plan files in logs or GitHub artifacts.

Keep a tested Neon backup outside Git. For a small database, `pg_dump` in
custom format followed by a restore into a **separate disposable database**
provides a portable recovery check; Neon also documents
[Postgres migration tools](https://neon.com/tools). Do not restore over the live
database without a separate review. Record backup time, database identifier,
and restore test result without storing credentials in the report. Verify any
Neon-native restore window against the current plan before relying on it.

For an application rollback, find the last known-good clean, reported commit
SHA from GitHub and confirm both images are public. With deployment authorized,
set *both* App Services back to those exact SHA-tagged images using
`az webapp config container set`, then repeat liveness and login checks. Keep
the database schema backward-compatible with that image; if a migration is
incompatible, use the reviewed database backup/recovery procedure first.
Disable `AZURE_DEPLOY_ENABLED` before debugging repeated deploy failures.
Never use `terraform destroy` as a routine shutdown: it may delete app data
and leave protected state/Key Vault recovery objects. Teardown and resource
retention require a separate reviewed cost/data decision.

## Evidence still required

ACS notification secret provisioning; GHCR image visibility and exact SHA; GitHub
environment/branch checks; cloud Key Vault reference resolution; live
login/dashboard/scan/incident/audit/gate checks; real ACS success and
failure/retry; backup restore; rollback; and sanitized screenshots/logs are
all **pending**. The scripts and workflow above are locally prepared only.
