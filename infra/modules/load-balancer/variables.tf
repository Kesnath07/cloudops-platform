variable "name" {
  description = "Prefix for resource names."
  type        = string
}

variable "vpc_id" {
  description = "VPC of the target group."
  type        = string
}

variable "subnet_ids" {
  description = "Public subnets for the load balancer."
  type        = list(string)
}

variable "security_group_id" {
  description = "Security group of the load balancer."
  type        = string
}

variable "app_port" {
  description = "Port the API container listens on."
  type        = number
}

variable "health_check_path" {
  description = "Readiness endpoint used for target health."
  type        = string
  default     = "/actuator/health/readiness"
}

variable "certificate_arn" {
  description = "Regional ACM certificate for the origin hostname. Null serves HTTP to CloudFront only."
  type        = string
  default     = null
}

variable "origin_domain_name" {
  description = "Hostname CloudFront uses to reach the load balancer when a certificate is provided."
  type        = string
  default     = null
}

variable "hosted_zone_id" {
  description = "Route 53 zone for the origin hostname record."
  type        = string
  default     = null
}

variable "origin_verify_header_value" {
  description = "Shared secret CloudFront adds to origin requests; requests without it are refused."
  type        = string
  sensitive   = true
}

variable "deletion_protection" {
  description = "Protect the load balancer from deletion."
  type        = bool
}

variable "access_log_retention_days" {
  description = "Days to keep load balancer access logs."
  type        = number
}
