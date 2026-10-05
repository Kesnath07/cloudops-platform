variable "name" {
  description = "Prefix for resource names."
  type        = string
}

variable "subnet_ids" {
  description = "Isolated data subnets for the DB subnet group (at least two AZs)."
  type        = list(string)
}

variable "security_group_id" {
  description = "Security group attached to the instance."
  type        = string
}

variable "kms_key_arn" {
  description = "KMS key for storage, Performance Insights and the credentials secret."
  type        = string
}

variable "engine_version" {
  description = "PostgreSQL major version. Minor versions are upgraded automatically."
  type        = string
  default     = "17"
}

variable "instance_class" {
  description = "RDS instance class."
  type        = string
}

variable "allocated_storage" {
  description = "Initial storage in GiB."
  type        = number
}

variable "max_allocated_storage" {
  description = "Upper bound for storage autoscaling in GiB."
  type        = number
}

variable "multi_az" {
  description = "Run a synchronous standby in a second AZ."
  type        = bool
}

variable "backup_retention_days" {
  description = "Days of automated backups and point-in-time recovery."
  type        = number
}

variable "deletion_protection" {
  description = "Block deletion of the instance and require a final snapshot on destroy."
  type        = bool
}

variable "database_name" {
  description = "Name of the application database."
  type        = string
  default     = "cloudops"
}

variable "master_username" {
  description = "Master user name."
  type        = string
  default     = "cloudops_admin"
}

variable "password_version" {
  description = "Increment to generate and apply a new master password (write-only rotation)."
  type        = number
  default     = 1
}

variable "log_retention_days" {
  description = "Retention of the PostgreSQL and upgrade logs exported to CloudWatch."
  type        = number
}

variable "secret_recovery_window_days" {
  description = "Days a deleted credentials secret can still be restored (0 deletes immediately)."
  type        = number
  default     = 7
}
