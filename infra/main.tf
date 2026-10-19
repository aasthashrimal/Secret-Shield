data "azurerm_client_config" "current" {}

locals {
  base           = "${var.name_prefix}-${var.unique_suffix}"
  compact        = replace("${var.name_prefix}${var.unique_suffix}", "-", "")
  registry_url   = var.image_source == "acr" ? "https://${azurerm_container_registry.app[0].login_server}" : var.image_source == "ghcr" ? "https://ghcr.io" : "https://mcr.microsoft.com"
  backend_image  = var.image_source == "acr" ? "secret-shield-backend:${var.image_tag}" : var.image_source == "ghcr" ? "aasthashrimal/secret-shield-backend:${var.image_tag}" : "azuredocs/aci-helloworld:latest"
  frontend_image = var.image_source == "acr" ? "secret-shield-frontend:${var.image_tag}" : var.image_source == "ghcr" ? "aasthashrimal/secret-shield-frontend:${var.image_tag}" : "azuredocs/aci-helloworld:latest"
  backend_url    = "https://${local.base}-api.azurewebsites.net"
  frontend_url   = "https://${local.base}-web.azurewebsites.net"
  secret_ref     = { for name in ["db-url", "db-username", "db-password", "db-app-password", "jwt-secret", "acs-connection-string", "acs-sender-address", "notification-recipients"] : name => "@Microsoft.KeyVault(SecretUri=${azurerm_key_vault.app.vault_uri}secrets/${name}/)" }
}

resource "azurerm_resource_group" "app" {
  name     = "rg-${local.base}"
  location = var.location
  tags     = var.tags
}

resource "azurerm_virtual_network" "app" {
  name                = "vnet-${local.base}"
  location            = azurerm_resource_group.app.location
  resource_group_name = azurerm_resource_group.app.name
  address_space       = ["10.72.0.0/24"]
  tags                = var.tags
}

resource "azurerm_subnet" "postgres" {
  count                = var.database_source == "azure" ? 1 : 0
  name                 = "snet-postgres"
  resource_group_name  = azurerm_resource_group.app.name
  virtual_network_name = azurerm_virtual_network.app.name
  address_prefixes     = ["10.72.0.0/27"]
  delegation {
    name = "postgres-flexible"
    service_delegation {
      name    = "Microsoft.DBforPostgreSQL/flexibleServers"
      actions = ["Microsoft.Network/virtualNetworks/subnets/join/action"]
    }
  }
}

resource "azurerm_subnet" "web" {
  name                 = "snet-web-integration"
  resource_group_name  = azurerm_resource_group.app.name
  virtual_network_name = azurerm_virtual_network.app.name
  address_prefixes     = ["10.72.0.64/26"]
  delegation {
    name = "app-service"
    service_delegation {
      name    = "Microsoft.Web/serverFarms"
      actions = ["Microsoft.Network/virtualNetworks/subnets/action"]
    }
  }
}

resource "azurerm_private_dns_zone" "postgres" {
  count               = var.database_source == "azure" ? 1 : 0
  name                = "${local.base}.private.postgres.database.azure.com"
  resource_group_name = azurerm_resource_group.app.name
  tags                = var.tags
}

resource "azurerm_private_dns_zone_virtual_network_link" "postgres" {
  count                 = var.database_source == "azure" ? 1 : 0
  name                  = "postgres-vnet"
  resource_group_name   = azurerm_resource_group.app.name
  private_dns_zone_name = azurerm_private_dns_zone.postgres[0].name
  virtual_network_id    = azurerm_virtual_network.app.id
  registration_enabled  = false
  tags                  = var.tags
}

resource "azurerm_postgresql_flexible_server" "app" {
  count                             = var.database_source == "azure" ? 1 : 0
  name                              = "${local.base}-pg"
  resource_group_name               = azurerm_resource_group.app.name
  location                          = azurerm_resource_group.app.location
  version                           = "16"
  administrator_login               = var.postgres_admin_login
  administrator_password_wo         = var.postgres_admin_password
  administrator_password_wo_version = var.postgres_password_version
  delegated_subnet_id               = azurerm_subnet.postgres[0].id
  private_dns_zone_id               = azurerm_private_dns_zone.postgres[0].id
  public_network_access_enabled     = false
  sku_name                          = var.postgres_sku
  storage_mb                        = var.postgres_storage_mb
  backup_retention_days             = 7
  geo_redundant_backup_enabled      = false
  tags                              = var.tags
  depends_on                        = [azurerm_private_dns_zone_virtual_network_link.postgres]
}

resource "azurerm_postgresql_flexible_server_database" "app" {
  count     = var.database_source == "azure" ? 1 : 0
  name      = "secret_shield_db"
  server_id = azurerm_postgresql_flexible_server.app[0].id
  charset   = "UTF8"
  collation = "en_US.utf8"
}

resource "azurerm_postgresql_flexible_server_configuration" "tls" {
  count     = var.database_source == "azure" ? 1 : 0
  name      = "require_secure_transport"
  server_id = azurerm_postgresql_flexible_server.app[0].id
  value     = "ON"
}

resource "azurerm_container_registry" "app" {
  count               = var.enable_acr ? 1 : 0
  name                = "${local.compact}acr"
  resource_group_name = azurerm_resource_group.app.name
  location            = azurerm_resource_group.app.location
  sku                 = "Basic"
  admin_enabled       = false
  tags                = var.tags
}

resource "azurerm_user_assigned_identity" "image_pull" {
  name                = "id-${local.base}-pull"
  resource_group_name = azurerm_resource_group.app.name
  location            = azurerm_resource_group.app.location
  tags                = var.tags
}

resource "azurerm_role_assignment" "image_pull" {
  count                = var.enable_acr ? 1 : 0
  scope                = azurerm_container_registry.app[0].id
  role_definition_name = "AcrPull"
  principal_id         = azurerm_user_assigned_identity.image_pull.principal_id
}

resource "azurerm_user_assigned_identity" "deploy" {
  name                = "id-${local.base}-deploy"
  resource_group_name = azurerm_resource_group.app.name
  location            = azurerm_resource_group.app.location
  tags                = var.tags
}

resource "azurerm_federated_identity_credential" "github_deploy" {
  name                = "github-${var.github_environment}"
  parent_id           = azurerm_user_assigned_identity.deploy.id
  resource_group_name = azurerm_resource_group.app.name
  issuer              = "https://token.actions.githubusercontent.com"
  subject             = "${var.github_oidc_repository_subject}:environment:${var.github_environment}"
  audience            = ["api://AzureADTokenExchange"]
}

resource "azurerm_role_assignment" "deploy_push" {
  count                = var.enable_acr ? 1 : 0
  scope                = azurerm_container_registry.app[0].id
  role_definition_name = "AcrPush"
  principal_id         = azurerm_user_assigned_identity.deploy.principal_id
}

resource "azurerm_key_vault" "app" {
  name                          = "${local.compact}kv"
  location                      = azurerm_resource_group.app.location
  resource_group_name           = azurerm_resource_group.app.name
  tenant_id                     = data.azurerm_client_config.current.tenant_id
  sku_name                      = "standard"
  enable_rbac_authorization     = true
  public_network_access_enabled = true
  soft_delete_retention_days    = 7
  purge_protection_enabled      = true
  tags                          = var.tags
}

resource "azurerm_role_assignment" "operator_secrets" {
  scope                = azurerm_key_vault.app.id
  role_definition_name = "Key Vault Secrets Officer"
  principal_id         = data.azurerm_client_config.current.object_id
}

resource "azurerm_service_plan" "app" {
  name                = "plan-${local.base}"
  location            = azurerm_resource_group.app.location
  resource_group_name = azurerm_resource_group.app.name
  os_type             = "Linux"
  sku_name            = var.app_service_sku
  tags                = var.tags
}

resource "azurerm_linux_web_app" "backend" {
  name                                           = "${local.base}-api"
  resource_group_name                            = azurerm_resource_group.app.name
  location                                       = azurerm_resource_group.app.location
  service_plan_id                                = azurerm_service_plan.app.id
  https_only                                     = true
  ftp_publish_basic_authentication_enabled       = false
  webdeploy_publish_basic_authentication_enabled = false
  virtual_network_subnet_id                      = azurerm_subnet.web.id
  public_network_access_enabled                  = true
  identity {
    type         = "SystemAssigned, UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.image_pull.id]
  }
  site_config {
    always_on                                     = true
    minimum_tls_version                           = "1.2"
    ftps_state                                    = "Disabled"
    vnet_route_all_enabled                        = true
    health_check_path                             = var.image_source != "placeholder" ? "/api/health/live" : null
    health_check_eviction_time_in_min             = var.image_source != "placeholder" ? 5 : null
    container_registry_use_managed_identity       = var.image_source == "acr"
    container_registry_managed_identity_client_id = var.image_source == "acr" ? azurerm_user_assigned_identity.image_pull.client_id : null
    application_stack {
      docker_image_name   = local.backend_image
      docker_registry_url = local.registry_url
    }
  }
  app_settings = merge({
    WEBSITES_PORT                = "8080"
    SPRING_PROFILES_ACTIVE       = "cloud"
    DB_URL                       = var.database_source == "azure" ? "jdbc:postgresql://${azurerm_postgresql_flexible_server.app[0].fqdn}:5432/${azurerm_postgresql_flexible_server_database.app[0].name}?sslmode=require" : local.secret_ref["db-url"]
    DB_USERNAME                  = var.database_source == "azure" ? var.app_db_login : local.secret_ref["db-username"]
    DB_PASSWORD                  = var.database_source == "azure" ? local.secret_ref["db-app-password"] : local.secret_ref["db-password"]
    JWT_SECRET                   = local.secret_ref["jwt-secret"]
    FRONTEND_ORIGIN              = local.frontend_url
    NOTIFICATION_PROVIDER        = var.notification_provider
    NOTIFICATION_SCHEDULED       = var.notification_provider == "disabled" ? "false" : "true"
    NOTIFICATION_PUBLIC_BASE_URL = local.frontend_url
    }, var.notification_provider == "azure" ? {
    NOTIFICATION_RECIPIENTS = local.secret_ref["notification-recipients"]
    ACS_CONNECTION_STRING   = local.secret_ref["acs-connection-string"]
    ACS_SENDER_ADDRESS      = local.secret_ref["acs-sender-address"]
  } : {})
  tags       = var.tags
  depends_on = [azurerm_role_assignment.image_pull]
}

resource "azurerm_role_assignment" "backend_secrets" {
  scope                = azurerm_key_vault.app.id
  role_definition_name = "Key Vault Secrets User"
  principal_id         = azurerm_linux_web_app.backend.identity[0].principal_id
}

resource "azurerm_role_assignment" "deploy_backend" {
  scope                = azurerm_linux_web_app.backend.id
  role_definition_name = "Website Contributor"
  principal_id         = azurerm_user_assigned_identity.deploy.principal_id
}

resource "azurerm_linux_web_app" "frontend" {
  name                                           = "${local.base}-web"
  resource_group_name                            = azurerm_resource_group.app.name
  location                                       = azurerm_resource_group.app.location
  service_plan_id                                = azurerm_service_plan.app.id
  https_only                                     = true
  ftp_publish_basic_authentication_enabled       = false
  webdeploy_publish_basic_authentication_enabled = false
  public_network_access_enabled                  = true
  identity {
    type         = "UserAssigned"
    identity_ids = [azurerm_user_assigned_identity.image_pull.id]
  }
  site_config {
    always_on                                     = true
    minimum_tls_version                           = "1.2"
    ftps_state                                    = "Disabled"
    health_check_path                             = var.image_source != "placeholder" ? "/health" : null
    health_check_eviction_time_in_min             = var.image_source != "placeholder" ? 5 : null
    container_registry_use_managed_identity       = var.image_source == "acr"
    container_registry_managed_identity_client_id = var.image_source == "acr" ? azurerm_user_assigned_identity.image_pull.client_id : null
    application_stack {
      docker_image_name   = local.frontend_image
      docker_registry_url = local.registry_url
    }
  }
  app_settings = {
    WEBSITES_PORT  = "80"
    BACKEND_ORIGIN = local.backend_url
  }
  tags       = var.tags
  depends_on = [azurerm_role_assignment.image_pull]
}

resource "azurerm_role_assignment" "deploy_frontend" {
  scope                = azurerm_linux_web_app.frontend.id
  role_definition_name = "Website Contributor"
  principal_id         = azurerm_user_assigned_identity.deploy.principal_id
}

resource "azurerm_communication_service" "app" {
  name                = "acs-${local.base}"
  resource_group_name = azurerm_resource_group.app.name
  data_location       = var.acs_data_location
  tags                = var.tags
}

resource "azurerm_email_communication_service" "app" {
  name                = "email-${local.base}"
  resource_group_name = azurerm_resource_group.app.name
  data_location       = var.acs_data_location
  tags                = var.tags
}

resource "azurerm_email_communication_service_domain" "managed" {
  name              = "AzureManagedDomain"
  email_service_id  = azurerm_email_communication_service.app.id
  domain_management = "AzureManaged"
}

resource "azurerm_communication_service_email_domain_association" "managed" {
  communication_service_id = azurerm_communication_service.app.id
  email_service_domain_id  = azurerm_email_communication_service_domain.managed.id
}
