# Remaining prerequisites and evidence

This list records open work as of 18 October 2026. The approved application
infrastructure is live with placeholder pages; Secret Shield itself is not
deployed. The original `infra/m14.tfplan` must not be applied again. Do not publish
private environment values, Terraform state, plan files, raw scan reports, or
notification addresses.

| Step | What is ready locally | Blocking prerequisite and evidence needed |
| --- | --- | --- |
| M00.2–M00.3, M13.4 | Solo setup and Compose guide | Teammate available for GitHub collaboration and independent clean clone. |
| M10.4 | Fake-provider outbox/retry tests; ACS configuration slots | Applied ACS/Email resources, verified sender and an approved recipient; record actual send and failed/retry outcome. |
| M12.2–M12.6 | Scan/gate/reporter workflow, local tests, prior clean/finding/error GitHub runs | Fork and required-check settings, live backend reporting, new image/publish/deploy runs, and sanitized run/record evidence. No CI image deployment is claimed yet. |
| M14.3–M14.5 | Complete: protected state, approved 18-resource apply, four approved Neon/JWT secret uploads, all four references `Resolved`, and [actual architecture/inventory](azure-architecture-actual.md) | No further M14 implementation; future infra changes need a fresh reviewed plan. Actual app runtime and ACS delivery belong to M15/M10.4. |
| M15.1 | Guarded exact-SHA GHCR publish and Azure OIDC deploy jobs, plus a disabled one-time bootstrap publisher and dry-run deployment helper; app infrastructure exists | GitHub CLI sign-in, protected `main` and `azure-dev`, separately approved bootstrap publication/first image switch, public image visibility, then trusted backend reporting and normal deployment approval. Verify a clean push deploys and failed gates skip deployment. |
| M15.2 | Neon TLS/JWT validation, four resolved Key Vault references, and Nginx same-origin proxy | Published images, then migration, HTTPS, login, dashboard API, CORS/session behavior, readiness and ACS verification in Azure. |
| M15.3–M15.5 | Deployment/operations guide and acceptance matrix drafted | Live clean/blocked workflow, incident/audit/notification delivery, logs, backup restore, rollback, and sanitized cloud screenshots. |
| M16.1–M16.2 | 147 backend and 6 frontend tests; local reporting tests and two Docker builds; [partial security audit](security-audit.md) | Cloud end-to-end checks plus final image/log/browser audit, credential revocation and admin restrictions. Keep negative tests in final verification. |
| M16.3–M16.6 | Report and demo drafts | Replace pending fields with actual cloud evidence, prepare slides/screenshots, rehearse with teammate, review PRs and tag only a tested final version. |

The activation order and dry-run commands are in [deployment-guide.md](deployment-guide.md).
Each GitHub, Key Vault, image-publication, or Azure action needs its own review
under the user's instruction. Before any paid resource creation, show the exact
action and updated cost estimate; the USD 20 budget is an alert, not a cap.
