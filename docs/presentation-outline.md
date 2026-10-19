# Secret Shield presentation slides

Use this presentation-ready ten-slide structure. Put final SHA
`8e36c800eecfb338bca68c4654393b37db0eff03` and the repository link in a footer.
All referenced screenshots are reviewed and sanitized.

| Slide | Main message | Visual/evidence | Speaker cue |
| --- | --- | --- | --- |
| 1. Problem and scope | Exposed credentials create delivery risk; Secret Shield detects, records and blocks | Title and one-sentence scope | It does not rotate provider secrets automatically |
| 2. Architecture | React/Nginx -> Spring Boot -> Neon, with GitHub Actions and Azure/ACS | [Actual architecture](azure-architecture-actual.md) | Explain proxy, Key Vault, OIDC and state |
| 3. Security model | JWT, roles, owner isolation, sanitized findings | [Role matrix](user-roles.md) | Mention negative tests and no browser token persistence |
| 4. Scan and gate | CLEAN allows; FINDINGS_DETECTED/FAILED block | `final-clean-deploy-run.png`, blocked/error screenshots | Emphasize exact commit and fail-closed behavior |
| 5. Dashboard | Repository -> scan -> finding -> incident -> audit | `m12-finding-dashboard.png`, `m15-live-dashboard.png` | Raw secret values never reach UI |
| 6. Notifications | High risk uses retryable outbox and ACS | `m15-notification-incident.png` | Failure/retry and SENT verified without recipient data |
| 7. Pipeline | Required checks, SHA-tagged GHCR images and protected OIDC | `final-clean-deploy-run.png` | PRs cannot deploy |
| 8. Azure result | 18 Terraform resources support live HTTPS app | Azure resources and dashboard screenshots | Neon is separate; ACR/Azure PostgreSQL excluded |
| 9. Operations | Logs, restore, rollback and retention | [Deployment guide](deployment-guide.md), [audit](security-audit.md) | No destructive operation was needed |
| 10. Results and limits | Tests/live flows passed; teammate reproduction deferred | [Acceptance matrix](acceptance-checklist.md) | Mention credit dependency and future work |

Use the [demo script](demo-script.md) and keep screenshots ready as outage
fallback. Never show `.env`, secret pages, raw reports, recipients, access tokens
or Terraform state.
