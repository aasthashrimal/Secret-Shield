# Free portfolio hosting migration

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
- `JWT_SECRET`: the existing signing secret, retained so existing tokens behave
  consistently during the migration

Azure Communication Services is intentionally not migrated. Notifications are
disabled on Render, while existing notification/audit records remain in Neon.

## Expected free-tier behavior

The static site stays on Render's CDN. The free backend sleeps after fifteen idle
minutes and can take about a minute to wake on the next request. Its filesystem is
ephemeral, which is acceptable because application records are in Neon and local
scanner workspaces are not part of the portfolio deployment. A first login attempt
during wake-up may need one retry.

## Migration gate

Do not disable or delete Azure until all of these pass on Render:

1. backend `/api/health/live` returns `UP`;
2. static frontend loads over HTTPS;
3. registration, login, dashboard and logout work;
4. existing repositories, scans, findings and incidents are present from Neon;
5. browser requests accept only the configured Render frontend origin;
6. GitHub CI reports a clean commit to the Render backend;
7. the Azure publish/deploy switch is disabled before the teardown commit.

Before Azure teardown, create a fresh private Neon dump and retain the existing
Terraform state securely. Terraform destruction is a separate reviewed action.
The Azure architecture, verified run links and sanitized screenshots remain as
historical project evidence after the live resources are removed.
