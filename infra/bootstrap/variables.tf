variable "aws_region" {
  description = "Region of the state bucket and container registry (match the environments)."
  type        = string
}

variable "github_repository" {
  description = "GitHub repository allowed to assume the CI roles, as owner/name."
  type        = string

  validation {
    condition     = can(regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$", var.github_repository))
    error_message = "github_repository must look like owner/name."
  }
}

variable "state_bucket_name" {
  description = "Globally unique name for the Terraform state bucket."
  type        = string
}

variable "environments" {
  description = "Deployment environments; each gets a role bound to the GitHub environment of the same name."
  type        = list(string)
  default     = ["dev", "prod"]
}

variable "ecr_repository_name" {
  description = "Repository holding API images, shared by all environments."
  type        = string
  default     = "cloudops-api"
}

variable "create_github_oidc_provider" {
  description = "Create the account's GitHub OIDC provider. Set false if it already exists."
  type        = bool
  default     = true
}
