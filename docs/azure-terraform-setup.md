# Historical M14 Terraform operator steps (PowerShell)

> The application stack was destroyed from an explicitly reviewed saved plan on
> 25 October 2026. Do not apply this configuration unless a new paid Azure
> deployment is intentionally approved and cost-reviewed.

Run from the repository root. These instructions create billable Azure resources
only after the plan is reviewed and the subscription/budget are approved. The
M13 Compose stack is independent; it does not need to be stopped.

**Current M14 status (updated 19 October 2026):** A monthly subscription budget
with 50%, 80%, and 100% actual-cost alerts is configured. The Education Hub
display showed ₹9,599 credit, 393 days remaining, expiry on 15 November 2027,
and ₹24.07 October cost. The state resource group
`rg-secretshield-tfstate`, account `sstfzboc5k`, and private `tfstate`
container have been created and secured; do not recreate them. Remote-backend
`terraform init` succeeded. The approved 18-resource application plan has now
been applied in two passes after correcting a provider-specific VNet setting;
both web apps currently show public placeholder pages. See the
[apply review](m14-plan-review.md) and [actual inventory](azure-architecture-actual.md).

1. In the [Azure Pricing Calculator](https://azure.microsoft.com/en-us/pricing/calculator/),
   price the region/SKUs. The ongoing Neon/GHCR variant starts with a B1 App
   Service plan estimated at USD 13.14/month in Central India. Compare that USD
   retail estimate with the portal's INR billing view before applying. Sign in
   with `az login`
   and confirm the target with `az account show --query '{id:id,name:name}'`.
   Select the subscription with `az account set --subscription '<subscription-id>'`.
   Ensure your account can create resources and assign roles. Confirm that
   Linux App Service and ACS Email are available in the chosen region and ACS
   geography. Azure PostgreSQL/ACR are optional presentation resources, not
   part of the lower-cost ongoing setup. M15 must create a protected GitHub
   environment named `azure-dev` (or the configured `github_environment`) that
   permits deployment only from the trusted branch after required checks.
   Check provider registration with
   `az provider show --namespace Microsoft.Communication --query registrationState -o tsv`.
   If it says `NotRegistered`, run
   `az provider register --namespace Microsoft.Communication --wait` before
   applying. This provider was missing during the M14 preview and was then
   registered successfully; the Web,
   Network, Key Vault, Managed Identity, and Storage providers were registered.
2. Set a budget alert in the Azure portal: open **Subscriptions**, select
   **Azure for Students**, then **Cost Management > Budgets > Add**. Name it
   `secret-shield-monthly`, choose **Monthly**, and set the approved amount in
   the subscription's displayed billing currency. Add **Actual
   cost** alerts at 50%, 80%, and 100% to an email address you check. Review
   the scope and save. The [Microsoft budget guide](https://learn.microsoft.com/en-us/azure/cost-management-billing/costs/tutorial-acm-create-budgets)
   has the current portal screens. Budget alerts do **not** stop resources;
   review Cost Analysis regularly. Confirm the currency of the displayed
   credit and its renewal/expiry date in the portal. On 19 October 2026 the
   verified display was ₹9,599, 393 days remaining, expiring 15 November 2027;
   renewal is not automatic.
3. Already completed for this project. The separate resource group
   `rg-secretshield-tfstate` contains StorageV2 account `sstfzboc5k` and a
   private `tfstate` container. The account requires HTTPS/TLS 1.2, disables
   shared-key and public blob access, and has blob versioning plus seven-day
   blob/container soft delete. The signed-in operator has `Storage Blob Data
   Contributor` scoped to this dedicated account. Retain it even if the
   application resource group is deleted; Azure encrypts its blobs at rest and
   Terraform uses a Blob lease for state locking. It can incur a small charge.
4. Already completed for the ongoing variant on this machine. Copy
   `infra/terraform.tfvars.example` to `infra/terraform.tfvars` and replace
   the subscription ID, region, and unique suffix. The real file is ignored by
   Git. Keep `database_source="neon"`, `image_source="placeholder"`, and
   `enable_acr=false` for the ongoing-site infrastructure plan. In this mode,
   no database password is passed through Terraform. If you intentionally add
   Azure PostgreSQL for the presentation, set `database_source="azure"`,
   generate a random admin password in your password manager, and provide it
   in the current PowerShell process without putting it in shell history:

   ```powershell
   $secret = Read-Host 'PostgreSQL admin password' -AsSecureString
   $env:TF_VAR_postgres_admin_password = [Net.NetworkCredential]::new('', $secret).Password
   Remove-Variable secret
   ```

5. Already completed on this machine. From `infra/`, initialize the protected
   state backend using the **state** resource names, not the app resource group.
   Azure CLI/Entra authentication avoids storage account keys:

   ```powershell
   cd infra
   terraform init -input=false `
     -backend-config='resource_group_name=rg-secretshield-tfstate' `
     -backend-config='storage_account_name=sstfzboc5k' `
     -backend-config='container_name=tfstate' `
     -backend-config='key=secret-shield/dev.tfstate' `
     -backend-config='use_azuread_auth=true' `
     -backend-config='use_cli=true' `
     -backend-config='subscription_id=90f1336d-e3cc-4aae-87f7-fcd90e2b877c' `
     -backend-config='tenant_id=29bebd42-f1ff-4c3d-9688-067e3460dc1f'
   terraform fmt -check -recursive
   terraform validate
   ```

   Backend settings are saved locally in `.terraform/`; no storage key is used.
   Do not copy a populated backend file or Terraform state into Git. The
   repository's `.gitignore` excludes these artifacts.
6. Completed for the original application creation. **Do not reapply the old
   `m14.tfplan`**: the 18 resources already exist. Create and inspect a fresh
   saved plan for any future infrastructure change:

   ```powershell
   terraform plan -input=false -out=m14.tfplan
   terraform show -no-color m14.tfplan
   ```

   Review the subscription, names, region, SKUs, the full add/change/destroy
   counts, App Service image placeholders, public endpoints, Key Vault policy,
   and monthly cost. A plan with unrelated changes or destroys must be fixed
   before any apply. Save only sanitized output; plans can contain sensitive
   metadata. Do not upload `m14.tfplan`.
7. The approved initial plan was applied; its first pass created 15 resources,
   and an inspected recovery plan created the remaining three with no changes
   or deletions. For a **future** separately approved change, apply only its
   newly reviewed plan, record non-secret `terraform output` values, and check
   the [actual resource inventory](azure-architecture-actual.md).
   If you set the optional admin-password environment variable, clear it after
   Terraform finishes:

   ```powershell
   Remove-Item Env:TF_VAR_postgres_admin_password
   ```

8. Before M15 replaces the placeholder images, add private `db-url`,
   `db-username`, `db-password`, and `jwt-secret` values to the created Key
   Vault. The operator gets a vault-scoped `Key Vault Secrets Officer` role in
   this plan; wait for role propagation before setting secrets. The first
   three come from your existing Neon project; use a JDBC
   PostgreSQL URL with TLS. Generate a random JWT value of at least 32 UTF-8
   bytes. Do not paste any of these values into Git, Terraform variables, or
   chat. The backend identity's Key Vault role may take a few minutes to
   propagate. If using the optional Azure database, instead arrange a secure
   PostgreSQL connection **from within the VNet** (for example a temporary,
   controlled admin runner) to create `secretshield_app` and grant the database
   and `public` schema permissions required by Flyway and the application.
   Never expose the database publicly just to bootstrap this role. Store the
   restricted role's password as `db-app-password`; keep the admin password out
   of App Service settings. M15 verifies migrations and tightens role grants.
9. In ACS Email, inspect the Azure-managed domain and sender address. If using
   a custom domain, complete the DNS verification and connect it to the
   Communication Service. Add `acs-connection-string`, `acs-sender-address`, and
   `notification-recipients` as Key Vault secrets only when an approved recipient
   is ready. Keep `notification_provider=disabled` until M15 validates delivery.

The non-secret `github_deploy_client_id`, `azure_tenant_id`, and subscription
ID identify the OIDC identity for M15. They are not passwords; M15 must still
restrict its workflow to the exact protected environment and same-commit scan
gate before granting a job `id-token: write`.

For local code-only validation, without Azure login or state storage:

```powershell
cd infra
terraform init -backend=false -input=false
terraform fmt -check -recursive
terraform validate
```

This is **not** a cloud plan. The separate protected-backend saved plan is
`infra/m14.tfplan`; it proposes 18 additions and must be reviewed before
apply. The write-only Azure PostgreSQL password is
needed only in `database_source="azure"` mode. Switching that mode back to
`neon` can plan deletion of the Azure database; back up any wanted data and
review the full plan. Do not use `terraform destroy` or remove the state
account as a routine shutdown.
