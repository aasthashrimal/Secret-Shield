mock_provider "azurerm" {
  mock_data "azurerm_client_config" {
    defaults = { tenant_id = "00000000-0000-0000-0000-000000000000" }
  }
}

run "secure_default_plan" {
  command = plan

  variables {
    subscription_id = "00000000-0000-0000-0000-000000000000"
    location        = "centralindia"
    name_prefix     = "secretshield-dev"
    unique_suffix   = "abcd12"
  }

  assert {
    condition     = length(azurerm_postgresql_flexible_server.app) == 0 && length(azurerm_container_registry.app) == 0
    error_message = "The low-cost default must not provision PostgreSQL or ACR."
  }

  assert {
    condition     = azurerm_service_plan.app.sku_name == "B1"
    error_message = "The default plan should use the lower-cost B1 SKU."
  }

  assert {
    condition     = azurerm_linux_web_app.backend.https_only && azurerm_linux_web_app.frontend.https_only
    error_message = "Both App Services must require HTTPS."
  }

  assert {
    condition     = !azurerm_linux_web_app.backend.ftp_publish_basic_authentication_enabled && !azurerm_linux_web_app.backend.webdeploy_publish_basic_authentication_enabled && !azurerm_linux_web_app.frontend.ftp_publish_basic_authentication_enabled && !azurerm_linux_web_app.frontend.webdeploy_publish_basic_authentication_enabled
    error_message = "Both App Services must disable publishing with basic authentication."
  }

  assert {
    condition     = azurerm_linux_web_app.backend.app_settings["NOTIFICATION_PROVIDER"] == "disabled"
    error_message = "Email must stay disabled until sender and recipients are configured."
  }

  assert {
    condition     = azurerm_federated_identity_credential.github_deploy.subject == "repo:aasthashrimal/Secret-Shield:environment:azure-dev"
    error_message = "Deployment federation must be scoped to the protected GitHub environment."
  }

}

run "scanned_image_plan" {
  command = plan

  variables {
    subscription_id = "00000000-0000-0000-0000-000000000000"
    location        = "centralindia"
    name_prefix     = "secretshield-dev"
    unique_suffix   = "abcd12"
    image_source    = "ghcr"
    image_tag       = "0123456789abcdef0123456789abcdef01234567"
  }

  assert {
    condition     = azurerm_linux_web_app.backend.site_config[0].health_check_path == "/api/health/live" && azurerm_linux_web_app.frontend.site_config[0].health_check_path == "/health"
    error_message = "The deployed application images need health checks."
  }

  assert {
    condition     = azurerm_linux_web_app.backend.site_config[0].application_stack[0].docker_registry_url == "https://ghcr.io" && !azurerm_linux_web_app.backend.site_config[0].container_registry_use_managed_identity
    error_message = "Public GHCR images should not require Azure registry credentials."
  }
}

run "azure_demo_plan" {
  command = plan

  variables {
    subscription_id         = "00000000-0000-0000-0000-000000000000"
    location                = "centralindia"
    name_prefix             = "secretshield-dev"
    unique_suffix           = "abcd12"
    database_source         = "azure"
    enable_acr              = true
    image_source            = "acr"
    image_tag               = "0123456789abcdef0123456789abcdef01234567"
    postgres_admin_password = "synthetic-test-value-only"
  }

  assert {
    condition     = azurerm_postgresql_flexible_server.app[0].public_network_access_enabled == false
    error_message = "Azure PostgreSQL must remain private."
  }

  assert {
    condition     = !azurerm_container_registry.app[0].admin_enabled && azurerm_linux_web_app.backend.site_config[0].container_registry_use_managed_identity
    error_message = "ACR admin must be disabled and the app must use managed-identity pulls."
  }
}
