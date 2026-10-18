# Deliverable security audit (partial, 18 October 2026)

This is a local, sanitized review. It does not replace the final M16.2 audit
after cloud deployment, image publication, browser capture, and release.

- `git check-ignore` confirmed `.env`, `infra/terraform.tfvars`, and the saved
  `infra/m14.tfplan` are excluded from Git. The Terraform plan and state must
  remain private because they can contain metadata and references.
- A pattern scan over tracked and new deliverable files found no private-key
  headers, credential-bearing PostgreSQL URLs, Azure account keys, or ACS keys.
- Pinned Gitleaks 8.30.0 scanned the working directory. It reported ten
  matches: four in ignored private `.env`/`.env.compose` files, three in ignored
  `target/` tool documentation/configuration, two generic-rule matches on prose
  in project documentation, and one deliberate synthetic fixture in `target/`.
  Reports were written only to ignored `target/`; no matched value is copied
  into this document. The private file matches are expected for local runtime
  configuration and must not be published. The docs matches need one final
  review before release to ensure they remain non-sensitive prose.
- The disposable PostgreSQL suite includes admin-role, cross-owner, and CI
  credential-revocation negative tests. Its most recent run passed 147 tests
  with zero failures/errors; four opt-in real-scanner tests were skipped.
- After the separately approved upload of four Neon/JWT values, Azure reported
  all four backend Key Vault references `Resolved`. A private, in-memory check
  of Terraform state confirmed `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, and
  `JWT_SECRET` are Key Vault reference strings, not raw uploaded values. No
  secret value was printed or added to this report.

Before M16.2 can be checked, inspect built image contents/history, GitHub
artifacts, cloud logs, sanitized screenshots, raw reports, and final tracked
files. Run a fresh scanner audit after the release commit and confirm revoked
machine credentials cannot ingest. Docker Desktop was stopped for this partial
audit, so image inspection was not repeated.
