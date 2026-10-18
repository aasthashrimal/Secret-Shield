output "resource_group_name" { value = azurerm_resource_group.app.name }
output "container_registry_login_server" { value = var.enable_acr ? azurerm_container_registry.app[0].login_server : null }
output "backend_url" { value = local.backend_url }
output "frontend_url" { value = local.frontend_url }
output "postgres_server_name" { value = var.database_source == "azure" ? azurerm_postgresql_flexible_server.app[0].name : null }
output "key_vault_name" { value = azurerm_key_vault.app.name }
output "communication_service_name" { value = azurerm_communication_service.app.name }
output "email_service_name" { value = azurerm_email_communication_service.app.name }
output "github_deploy_client_id" { value = azurerm_user_assigned_identity.deploy.client_id }
output "azure_tenant_id" { value = data.azurerm_client_config.current.tenant_id }
