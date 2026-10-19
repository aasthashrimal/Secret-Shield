# GitHub security workflow (M12)

`.github/workflows/security-ci.yml` runs on pushes to `main` and pull requests into
`main`. The scan job checks out full history without persisted checkout credentials,
scans the exact `github.sha` (the PR merge commit for pull requests), and uses
Gitleaks 8.30.0 by immutable container digest. A push scans every commit since the
previous tip; a PR scans the base-to-merge range. If that range cannot be proved,
it scans reachable history through the checked-out commit. Pull requests read the
scanner policy from the base commit so a PR cannot weaken its own scan config.

The scanner has no network access. Its raw JSON stays in the runner's temporary
directory and is deleted after conversion. The converter retains only validated
rule ID, relative path, line, and a fixed safe description. It emits one of
`CLEAN`, `FINDINGS_DETECTED`, or `FAILED`; a malformed/oversized report or scanner
error is `FAILED`. No raw report is uploaded as an artifact. The scan job fails
unless the result is clean, so the backend/frontend build job is skipped for
findings and errors. The build runs Maven tests/package, Node normalization tests,
frontend tests/build, and both backend/frontend Docker image builds.

Reporting runs after scanning only for a trusted `main` push. It sends the same
sanitized payload with GitHub run ID, attempt, commit SHA, branch, and `CI_PUSH`
through the repository-scoped machine endpoint. Transient network/429/5xx errors
are retried at most three times with an identical body. The backend makes replay
idempotent. HTTP 4xx and mismatched acknowledgements mark reporting unsuccessful;
the clean-scan build can still run, but release eligibility is skipped until the
backend acknowledges the result. Fork PRs receive no
backend credential and are scanned without reporting. Same-repository PRs also
receive no backend credential because reporting is restricted to `main` pushes.

## Configure the trusted push

Create the repository record in the backend with the GitHub owner/name matching
this repository. As its owner or an administrator, issue one CI credential using
`POST /api/repositories/{id}/ci-credentials` (see [scan management](scan-management.md)).
Save that one-time value in the GitHub repository secret
`SECRET_SHIELD_CI_CREDENTIAL`. Set repository variables
`SECRET_SHIELD_REPOSITORY_ID` to that backend repository ID and
`SECRET_SHIELD_API_BASE` to the public **HTTPS** base URL of the backend. A
`localhost` URL cannot be reached by a GitHub-hosted runner. Do not put the
credential in a variable, workflow file, log, or PR. Missing/invalid reporting
configuration leaves reporting unsuccessful and skips release eligibility; it is
never treated as backend ingestion evidence.

The `Trusted release candidate` job is restricted to a clean, reported `main`
push with a successful build and holds no deployment credential. The verified
`publish` job requires that candidate and `GHCR_PUBLISH_ENABLED=true`; it builds
and pushes both images under the exact 40-character SHA to GHCR. The prepared
`deploy` job additionally requires the publish job and
`AZURE_DEPLOY_ENABLED=true`, uses an `azure-dev` GitHub environment and
short-lived OIDC token, and sets both App Services to those SHA-tagged images.
Both switches were enabled only after separate approval and successful bootstrap.
The deployment job requires both GHCR packages to be anonymously
readable and checks the backend, frontend, and frontend `/api` proxy afterward.
See the [deployment guide](deployment-guide.md) for activation prerequisites.

The first deployment had a bootstrap dependency: the placeholder backend could
not acknowledge a CI report. A separate, manually dispatched
`bootstrap_publish` job can publish only a clean, successfully built commit on
`main` when `BOOTSTRAP_PUBLISH_ENABLED=true`. It never deploys and never counts
as a reported release candidate. After its exact-SHA images are public, the
operator uses the reviewed [one-time deployment script](../scripts/bootstrap-cloud-deploy.ps1)
to verify the successful GitHub run and point both apps at that SHA. Disable
the bootstrap flag immediately afterward, configure backend CI reporting, and
use the normal reported `publish`/`deploy` path for later commits. Bootstrap run
`39149383125` published the first images, and the flag is disabled again.

Protect `main` with required checks `Secret scan
and gate` and `Backend and frontend checks`, review for workflow/policy changes,
and prevent bypasses by alternate deployment workflows. PR checks do not carry
production credentials. A focused Node policy test rejects
`pull_request_target`, requires trusted-main conditions around reporting,
publication and deployment, and confirms only the protected deployment job can
request an OIDC token. Missing reporting credentials fail before any request.

## Evidence to collect on GitHub

For blocked-push evidence, use a disposable GitHub test repository and matching
backend repository record. Do not introduce a synthetic finding into the protected
production `main` branch. After committing and configuring the workflow, record the run URL, event, checked
commit SHA, scan outcome, reporting acknowledgement, and build job result for:

| Run | Expected scan job | Expected build | Expected backend record |
| --- | --- | --- | --- |
| Clean trusted `main` push | Success, `CLEAN` | Runs and passes | One `CLEAN` scan for the same SHA |
| Synthetic finding on a test PR | Fails, `FINDINGS_DETECTED` | Skipped | None for a PR; no credential supplied |
| Synthetic finding on a disposable test repository's trusted `main` push | Fails, `FINDINGS_DETECTED`; reporting still runs | Skipped | One finding-bearing scan for the same SHA |
| Deliberate scanner/config failure on a test PR | Fails, `FAILED` | Skipped | None for a PR |
| Fork PR | Scans with read-only source access | Runs only if clean | None; backend secrets unavailable |

Use only synthetic markers and redact screenshots. The actual clean/finding/error
GitHub run links, backend record comparison, and screenshots form M12 acceptance
evidence. Avoid testing a deliberate scanner failure on the protected production
branch. The local fixture scanner and Node tests cover the converter behavior but
do not substitute for GitHub run evidence.

## Observed GitHub runs (17 October 2026)

- [Clean `main` push, run 39054693870](https://github.com/aasthashrimal/Secret-Shield/actions/runs/39054693870): head commit `a371a29d9776be05059d5626c2cc7533fb774481`; GitHub reports the scan and backend/frontend jobs successful. Release candidate was skipped, so this is not deployment or backend reporting evidence.
- [Synthetic-finding PR #7, run 39055552521](https://github.com/aasthashrimal/Secret-Shield/actions/runs/39055552521): branch head commit `2f4c84cd4843e98757722c79006e9e81847940b7`; the user confirmed `FINDINGS_DETECTED` in the scan log. GitHub reports the scanner step successful, the clean gate failed, and the build, reporting, and release-candidate steps skipped as intended. The PR workflow scans its merge candidate; the head SHA above identifies the PR branch rather than asserting the merge SHA.
- [Scanner-error PR #8, run 39056726283](https://github.com/aasthashrimal/Secret-Shield/actions/runs/39056726283): branch head commit `cd8e677165627317f636261de006b264fdf061d5`; the PR changes only `POLICY_REF` to an invalid all-zero SHA. The user confirmed `FAILED` in the scan log. GitHub reports the scanner step successful (it normalized the configuration error), the clean gate failed, and the build, reporting, and release-candidate jobs or steps skipped. This is a deliberate test-branch configuration failure, not a scanner binary crash.

Later merged-main run
[`39242095907`](https://github.com/aasthashrimal/Secret-Shield/actions/runs/39242095907)
reported and deployed exact SHA `f151f5434c2e664ab5dd42105f851c52170a23c1`.
Live repository record 4 contained exactly one matching scan (ID 8), attempt 1,
`CI_PUSH`, Gitleaks 8.30.0, `CLEAN`, zero findings, a machine audit event and an
`ALLOW/CLEAN` gate. PRs #13, #14, #16 and #17 passed scan/build while every
release/deployment job skipped; unsafe PR #15 ran only the failing scan gate.
This verifies trusted clean reporting and the secret-free PR path. M12.3 still
needs one finding-bearing trusted run in a disposable repository; sanitized
screenshots remain M12.6 presentation evidence.
