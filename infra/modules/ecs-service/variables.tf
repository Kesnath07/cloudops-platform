variable "name" {
  description = "Prefix for resource names."
  type        = string
}

variable "subnet_ids" {
  description = "Private app subnets for the tasks."
  type        = list(string)
}

variable "security_group_id" {
  description = "Security group of the tasks."
  type        = string
}

variable "target_group_arn" {
  description = "Load balancer target group the service registers with."
  type        = string
}

variable "kms_key_arn" {
  description = "KMS key for logs, secrets and the incident topic."
  type        = string
}

variable "image" {
  description = "Fully qualified container image reference (repository:tag)."
  type        = string
}

variable "image_repository_arn" {
  description = "ARN of the ECR repository holding the image; the only repository the tasks may pull from."
  type        = string
}

variable "release" {
  description = "Release identifier exposed by the API, normally the git commit SHA."
  type        = string
}

variable "app_port" {
  description = "Port the container listens on."
  type        = number
}

variable "cpu" {
  description = "Task CPU units (1024 = 1 vCPU)."
  type        = number
}

variable "memory" {
  description = "Task memory in MiB."
  type        = number
}

variable "desired_count" {
  description = "Initial number of tasks; autoscaling adjusts it afterwards."
  type        = number
}

variable "min_count" {
  description = "Autoscaling lower bound."
  type        = number
}

variable "max_count" {
  description = "Autoscaling upper bound."
  type        = number
}

variable "cpu_target_percent" {
  description = "Average CPU utilization that autoscaling maintains."
  type        = number
  default     = 60
}

variable "database_address" {
  description = "Database hostname."
  type        = string
}

variable "database_port" {
  description = "Database port."
  type        = number
}

variable "database_name" {
  description = "Database name."
  type        = string
}

variable "database_secret_arn" {
  description = "Secrets Manager secret with database username and password."
  type        = string
}

variable "database_secret_version" {
  description = "Version of the database credentials; a change rolls the tasks so they pick up the new password."
  type        = number
}

variable "jwt_secret_version" {
  description = "Increment to rotate the token signing key (invalidates all issued tokens)."
  type        = number
  default     = 1
}

variable "bootstrap_admin_email" {
  description = "Email that receives the ADMIN role on registration. Empty disables the mechanism."
  type        = string
  default     = ""
}

variable "incident_notification_emails" {
  description = "Addresses subscribed to incident notifications (each must confirm by email)."
  type        = list(string)
  default     = []
}

variable "incident_notification_min_severity" {
  description = "Least severe incident level that triggers a notification."
  type        = string
  default     = "SEV2"
}

variable "log_retention_days" {
  description = "Retention of application logs."
  type        = number
}
