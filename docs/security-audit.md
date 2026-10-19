# Deliverable security audit (completed, 19 October 2026)

This sanitized review covers the tracked deliverables, local images, published
workflow behavior, bounded cloud logs, Terraform artifacts, browser checks and
temporary credentials. It never copies matched values into evidence.

- `git check-ignore` confirmed `.env`, `infra/terraform.tfvars`, and the saved
  `infra/m14.tfplan` are excluded from Git. The Terraform plan and state must
  remain private because they can contain metadata and references.
- A pattern scan over tracked and new deliverable files found no private-key
  headers, credential-bearing PostgreSQL URLs, Azure account keys, or ACS keys.
- Pinned Gitleaks 8.30.0 scanned all 34 commits reachable from the current
  branch. Its sole result is rule `secret-shield-synthetic` at the exact removed
  PR #15 fixture path/commit. This marker is deliberately invalid for external
  providers and remains historical gate evidence. The three pending evidence
  commits scanned clean. Raw scanner reports were deleted after sanitized
  metadata inspection.
- The disposable PostgreSQL suite includes admin-role, cross-owner, and CI
  credential-revocation negative tests. Its most recent run passed 147 tests
  with zero failures/errors; four opt-in real-scanner tests were skipped.
- After the separately approved upload of four Neon/JWT values, Azure reported
  all four backend Key Vault references `Resolved`. A private, in-memory check
  of Terraform state confirmed `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and
  `JWT_SECRET` are Key Vault reference strings, not raw uploaded values. No
  secret value was printed or added to this report.
- Git tracks 269 files. The only sensitive-looking names are the two example
  environment templates; their values are placeholders. `.env`, Terraform
  variables/state/plans, raw reports, logs and the disposable evidence helper
  are ignored and absent from `git ls-files`.
- Four local backend/frontend image tags were inspected. Their histories contain
  zero credential-style assignments and their application roots contain zero
  `.env`, key/certificate, Terraform state/variable, or raw Gitleaks-report
  filenames. The only broad sensitive-name match in image environment metadata
  was the non-secret Git setting `GIT_CONFIG_KEY_0`.
- A private audit of 39 bounded Azure log files found no private-value or
  credential-pattern match after the final deployment. Older retained Flyway
  lines contain only a database endpoint and expire under the three-day policy.
  Browser checks exposed no token in captured evidence.
- Live repository 5 has exactly one `CI_CREDENTIAL_REVOKED` audit event for the
  disposable M12 credential (ID 4, `2026-10-19T11:33:20.421653Z`). The associated
  GitHub Actions secret and variables were removed. The production reporting
  credential remains intentionally active for protected `main` CI.
