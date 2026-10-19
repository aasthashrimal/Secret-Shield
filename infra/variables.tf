variable "subscription_id" {
  type        = string
  description = "Target Azure subscription UUID."
  validation {
    condition     = can(regex("^[0-9a-fA-F-]{36}$", var.subscription_id))
    error_message = "Set subscription_id to the Azure subscription UUID."
  }
}

variable "location" {
  type        = string
  description = "Azure region supporting App Service, PostgreSQL Flexible Server, and ACR."
}

variable "name_prefix" {
  type        = string
  description = "Short lowercase project/environment prefix, for example secretshield-dev."
  validation {
    condition     = can(regex("^[a-z][a-z0-9-]{1,16}[a-z0-9]$", var.name_prefix))
    error_message = "Use 3-18 lowercase letters, digits, or internal hyphens, beginning with a letter."
  }
}

variable "unique_suffix" {
  type        = string
  description = "Globally unique 4-8 character lowercase alphanumeric suffix."
  validation {
    condition     = can(regex("^[a-z0-9]{4,8}$", var.unique_suffix))
    error_message = "Use 4-8 lowercase letters or digits."
  }
  validation {
    condition     = length(replace(var.name_prefix, "-", "")) + length(var.unique_suffix) <= 22
    error_message = "The combined prefix and suffix must leave room for the 24-character Key Vault name."
  }
}

variable "acs_data_location" {
  type        = string
  description = "ACS/Email data geography; choose an available matching geography for both resources."
  default     = "India"
}

variable "app_service_sku" {
  type        = string
  description = "Linux App Service SKU for the two app containers. Review cost before apply."
  default     = "B1"
}

variable "database_source" {
  type        = string
  description = "Use neon for the low-cost public site or azure for the M14 managed-database demonstration."
  default     = "neon"
  validation {
    condition     = contains(["neon", "azure"], var.database_source)
    error_message = "Use neon or azure."
  }
}

variable "image_source" {
  type        = string
  description = "Use a public placeholder until M15 publishes exact-commit images to GHCR or ACR."
  default     = "placeholder"
  validation {
    condition     = contains(["placeholder", "ghcr", "acr"], var.image_source)
    error_message = "Use placeholder, ghcr, or acr."
  }
}

variable "enable_acr" {
  type        = bool
  description = "Provision Azure Container Registry only for an explicitly reviewed ACR demonstration."
  default     = false
  validation {
    condition     = var.image_source != "acr" || var.enable_acr
    error_message = "Set enable_acr=true when image_source=acr."
  }
}

variable "postgres_sku" {
  type        = string
  description = "Flexible Server compute SKU. Review region availability and cost before apply."
  default     = "B_Standard_B1ms"
}

variable "postgres_storage_mb" {
  type        = number
  description = "Flexible Server provisioned storage in MiB."
  default     = 32768
}

variable "postgres_admin_login" {
  type        = string
  description = "Initial PostgreSQL administrator login. A dedicated app role should replace it in M15."
  default     = "ssadmin"
}

variable "app_db_login" {
  type        = string
  description = "Restricted PostgreSQL application role created after infrastructure apply, before M15 deployment."
  default     = "secretshield_app"
}

variable "github_repository" {
  type        = string
  description = "Exact owner/repository allowed to request Azure deployment OIDC tokens."
  default     = "aasthashrimal/Secret-Shield"
}

variable "github_oidc_repository_subject" {
  type        = string
  description = "Immutable GitHub OIDC repository subject prefix, including owner and repository IDs."
  default     = "repo:aasthashrimal@213026276/Secret-Shield@1413171705"
  validation {
    condition     = can(regex("^repo:[A-Za-z0-9_.-]+@[0-9]+/[A-Za-z0-9_.-]+@[0-9]+$", var.github_oidc_repository_subject))
    error_message = "Use GitHub's immutable repo:owner@ID/repository@ID OIDC subject prefix."
  }
}

variable "github_environment" {
  type        = string
  description = "Protected GitHub environment name used by the M15 deployment job."
  default     = "azure-dev"
}

variable "postgres_admin_password" {
  type        = string
  sensitive   = true
  ephemeral   = true
  default     = null
  description = "Private initial Azure PostgreSQL password required only in azure database mode; write-only and absent from state."
  validation {
    condition     = var.database_source != "azure" || try(length(var.postgres_admin_password) >= 16, false)
    error_message = "Azure database mode requires a random administrator password of at least 16 characters."
  }
}

variable "postgres_password_version" {
  type        = number
  description = "Increment when intentionally rotating the write-only PostgreSQL administrator password."
  default     = 1
}

variable "image_tag" {
  type        = string
  description = "Exact scanned commit SHA used for both images when image_source is ghcr or acr."
  default     = ""
  validation {
    condition     = var.image_source == "placeholder" || can(regex("^[0-9a-f]{40}$", var.image_tag))
    error_message = "GHCR and ACR image_tag must be a full lowercase 40-character commit SHA."
  }
}

variable "notification_provider" {
  type        = string
  description = "Keep disabled until the ACS sender and recipients are configured in M15."
  default     = "disabled"
  validation {
    condition     = contains(["disabled", "azure"], var.notification_provider)
    error_message = "Use disabled or azure."
  }
}

variable "tags" {
  type    = map(string)
  default = { project = "secret-shield", managed-by = "terraform" }
}
