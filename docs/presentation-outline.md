# Secret Shield presentation outline

Use synthetic accounts and repositories throughout. Put the exact commit SHA
and evidence link in a small footer. Never show tokens, connection strings,
passwords, raw scanner output, recipient addresses, or Terraform state.

| Slide | Content | Evidence to insert |
| --- | --- | --- |
| 1. Problem and scope | Why leaked credentials block delivery; project boundaries | One-sentence scope and repository link |
| 2. Architecture | Spring API, React/Nginx, Neon, GitHub Actions, Azure App Service, Key Vault and ACS | `azure-architecture-actual.md` diagram |
| 3. Security model | JWT, Developer/Admin roles, ownership isolation and secret storage | Sanitized role matrix and negative-test count |
| 4. Scan and gate | `CLEAN`, `FINDINGS_DETECTED`, `FAILED`; exact-commit enforcement | Clean run plus synthetic finding/error run links |
| 5. Dashboard workflow | Repository, scan, finding, incident, history and audit views | Existing sanitized local screenshots; replace/add cloud screenshots after M15 |
| 6. Notifications | Outbox, retry/deduplication and ACS delivery | Fake-provider evidence now; real delivery/failure evidence after M10.4 |
| 7. Delivery pipeline | Required checks, exact-SHA images, OIDC and protected `azure-dev` | PR #11 and main run 39142518443; add publish/deploy run after approval |
| 8. Azure result | Applied 18-resource inventory, budget, HTTPS and health | Current placeholder status; replace with live health/login evidence after M15 |
| 9. Operations | Safe logs, backup/restore, rollback, cost and retention | Reviewed live results still pending |
| 10. Results and limitations | Verified local/CI/cloud-infrastructure outcomes and honest open items | Final acceptance checklist |

Capture screenshots only after the corresponding check passes. Keep the local
Compose walkthrough as the fallback if Azure is unavailable during the demo.
