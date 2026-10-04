variable "name" {
  description = "Prefix for resource names."
  type        = string
}

variable "kms_key_arn" {
  description = "KMS key encrypting the alarm topic."
  type        = string
}

variable "alarm_emails" {
  description = "Addresses subscribed to alarm notifications (each must confirm by email)."
  type        = list(string)
  default     = []
}

variable "load_balancer_arn_suffix" {
  description = "ALB ARN suffix for metric dimensions."
  type        = string
}

variable "target_group_arn_suffix" {
  description = "Target group ARN suffix for metric dimensions."
  type        = string
}

variable "ecs_cluster_name" {
  description = "ECS cluster name for metric dimensions."
  type        = string
}

variable "ecs_service_name" {
  description = "ECS service name for metric dimensions."
  type        = string
}

variable "db_instance_identifier" {
  description = "RDS instance identifier for metric dimensions."
  type        = string
}

variable "api_log_group_name" {
  description = "Log group of the API, linked from the dashboard."
  type        = string
}

variable "target_5xx_threshold" {
  description = "5xx responses from the API per 5 minutes that raise an alarm."
  type        = number
  default     = 10
}

variable "latency_p95_threshold_seconds" {
  description = "p95 target response time that raises an alarm."
  type        = number
  default     = 1.5
}

variable "db_free_storage_threshold_bytes" {
  description = "Free storage below which the database alarm fires."
  type        = number
  default     = 2147483648
}
