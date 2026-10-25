# Free portfolio hosting and Azure retirement

**Completed 25 October 2026.** Both Render services returned HTTP 200, browser
login/dashboard/logout passed against the existing Neon data, GitHub CI reporting
was switched to the Render API, and the Azure deployment flag was disabled.

The post-presentation target keeps the existing Neon PostgreSQL database and
moves the public application from paid Azure App Service to Render:

- `secret-shield-api-zboc5k`: free Docker web service for Spring Boot
- `secret-shield-zboc5k`: free static React site on Render's CDN
- Neon: existing managed PostgreSQL data
- GitHub Actions: security scan and tests remain the required deployment checks

`render.yaml` declares both Render services. Automatic deployment uses
`checksPass`, so a commit is deployed only after its GitHub checks pass. The
backend accepts the static site's exact HTTPS origin. The frontend's public
`VITE_API_BASE` is compiled into the browser bundle; it contains no secret.

## Private values required during Blueprint creation

Render prompts for four `sync: false` values. Copy them privately from the ignored
local `.env`; never paste them into Git, chat, screenshots, logs, or Blueprint
source:

- `DB_URL`: the existing Neon JDBC URL
- `DB_USERNAME`: the existing Neon role
- `DB_PASSWORD`: the existing Neon password
- `JWT_SECRET`: a separately generated signing secret; rotate it immediately if
  it is ever exposed and expect existing sessions to become invalid

Azure Communication Services is intentionally not migrated. Notifications are
disabled on Render, while existing notification/audit records remain in Neon.

## Expected free-tier behavior

The static site stays on Render's CDN. The free backend sleeps after fifteen idle
minutes and can take about a minute to wake on the next request. Its filesystem is
ephemeral, which is acceptable because application records are in Neon and local
scanner workspaces are not part of the portfolio deployment. A first login attempt
during wake-up may need one retry.

## Migration gate

The application checks passed before Azure was deleted; the first later trusted
`main` run provides the final CI-to-Render reporting confirmation:

1. backend `/api/health/live` returns `UP`;
2. static frontend loads over HTTPS;
3. registration, login, dashboard and logout work;
4. existing repositories, scans, findings and incidents are present from Neon;
5. browser requests accept only the configured Render frontend origin;
6. GitHub CI reports a clean commit to the Render backend;
7. the Azure publish/deploy switch is disabled before the teardown commit.

Before teardown, a fresh private Neon dump was restored into disposable PostgreSQL
18: nine public tables and nine Flyway migrations were verified. The reviewed saved
Terraform plan contained 0 creates, 0 updates and exactly 18 destroys. Its approved
apply completed successfully; the application resource group no longer exists and
the remote Terraform state contains zero managed application resources. The
private final empty-state backup, Azure architecture, run links, and sanitized
screenshots remain as historical evidence. After separate approval, the protected
Azure state storage account and its resource group were also deleted.
