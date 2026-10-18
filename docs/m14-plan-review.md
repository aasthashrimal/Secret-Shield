# M14 Azure plan review — 18 October 2026

Target: enabled **Azure for Students** subscription, **Central India**, with a
verified **USD 20 monthly subscription budget** and actual-cost email alerts at
50%, 80%, and 100%. The portal showed **$9,599 available credit** with an
expiry of **15 November 2026**. Budget alerts notify; they do not stop usage.
The website's availability after that date depends on a successful student
renewal or another active subscription/payment arrangement.

Protected Terraform state is already provisioned in the separate
`rg-secretshield-tfstate` resource group. Storage account `sstfzboc5k` is
Standard_LRS in Central India, with private blobs, HTTPS/TLS 1.2, shared-key
access disabled, blob versioning, and seven-day blob/container soft delete.
The signed-in operator has `Storage Blob Data Contributor` only on this
dedicated account. Terraform initialized its Azure Blob backend using Entra
login and created the local ignored saved plan `infra/m14.tfplan`.

The saved plan proposes **18 additions, 0 changes, 0 deletions**:

| Area | Planned resources |
| --- | --- |
| App hosting | One B1 Linux App Service plan; public HTTPS backend and frontend web apps |
| Network | Application resource group, VNet, delegated web-integration subnet |
| Secrets | RBAC Key Vault; vault-scoped `Secrets User` for backend and `Secrets Officer` for the signed-in operator |
| CI identity | Two user-assigned identities, GitHub environment-scoped federated credential, and app-scoped deployment roles |
| Notifications | Communication Service, Email service, Azure-managed email domain, and domain association |

The planned application resource group is `rg-secretshield-dev-zboc5k`.
Planned URLs are `https://secretshield-dev-zboc5k-web.azurewebsites.net` and
`https://secretshield-dev-zboc5k-api.azurewebsites.net`. Both apps require
HTTPS and disable FTP/WebDeploy basic-auth publishing. The backend routes
outbound traffic through the VNet; it uses Key Vault references for Neon DB
credentials and JWT signing. Notification delivery starts disabled. **No Azure
PostgreSQL server or Azure Container Registry is in this plan.**

The Central India retail estimate for B1 is **USD 13.14 per 730 hours**.
State storage, Key Vault transactions, communication/email usage, bandwidth,
and other Azure usage are additional. The remaining margin under USD 20 is
small; this is an estimate, not a spending limit.

The two App Services initially use Microsoft's public sample image. Applying
this M14 plan creates the cloud foundation but **does not publish Secret
Shield**. M15 must publish the scanned backend and frontend images to public
GHCR under one exact commit SHA, set `image_source="ghcr"`, provide private
Neon/JWT values through Key Vault, and verify cloud login, dashboard APIs, and
email. The public link can become a working site only after those steps.

**Apply result (18 October 2026):** The user approved this exact 18-resource
plan. Its first apply created 15 resources, then AzureRM rejected
`WEBSITE_VNET_ROUTE_ALL` in `app_settings` for the backend. The same route-all
setting was moved to the provider-supported `site_config.vnet_route_all_enabled`
field. The inspected recovery plan contained only the missing backend app and
its two role assignments: three creates, 15 no-ops, zero changes/deletes. That
recovery apply succeeded. Terraform now tracks all 18 approved resources; a
post-apply read-only plan reported **no changes**.
Azure reads show both apps running and HTTPS-only, the vault using RBAC and
purge protection, and both placeholder URLs returning HTTP 200. The four
separately approved Neon/JWT secrets were uploaded from the ignored `.env`;
Azure reports all four backend references `Resolved`. See the
[actual inventory and diagram](azure-architecture-actual.md). No Secret Shield
image or live ACS notification has been deployed yet.

Keep both saved plan files and Terraform state private. A future Terraform
plan must be reviewed from current configuration and state; the original
`m14.tfplan` must not be applied again.
