variable "name" {
  description = "Prefix for resource names."
  type        = string
}

variable "vpc_id" {
  description = "VPC in which to create the security groups."
  type        = string
}

variable "listener_port" {
  description = "Port the load balancer listens on (443 with a certificate, 80 without)."
  type        = number
}

variable "app_port" {
  description = "Port the application container listens on."
  type        = number
}

variable "database_port" {
  description = "PostgreSQL port."
  type        = number
  default     = 5432
}
