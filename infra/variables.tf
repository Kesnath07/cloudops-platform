# --- Environment ------------------------------------------------------------------------------

variable "environment" {
  description = "Environment name; used in every resource name."
  type        = string

  validation {
    condition     = contains(["dev", "prod"], var.environment)
    error_message = "environment must be dev or prod."
  }
}

variable "aws_region" {
  description = "Region for all regional resources."
  type        = string
}

# --- Release ----------------------------------------------------------------------------------

variable "api_image_tag" {
  description = "Tag of the API image in ECR to run; CI passes the git commit SHA."
  type        = string

  validation {
    condition     = can(regex("^[0-9a-f]{7,40}$", var.api_image_tag))
    error_message = "api_image_tag must be a git commit SHA (7-40 lowercase hex characters)."
  }
}

variable "ecr_repository_name" {
  description = "ECR repository created by the bootstrap configuration."
  type        = string
  default     = "cloudops-api"
}

# --- Network ----------------------------------------------------------------------------------

variable "vpc_cidr" {
  description = "VPC CIDR (/16)."
  type        = string
}

variable "az_count" {
  description = "Availability zones to span."
  type        = number
  default     = 2
}

variable "single_nat_gateway" {
  description = "Share one NAT gateway across AZs (lower cost, AZ-dependent egress)."
  type        = bool
}

# --- API service ------------------------------------------------------------------------------

variable "api_cpu" {
  description = "Fargate task CPU units."
  type        = number
}

variable "api_memory" {
  description = "Fargate task memory (MiB)."
  type        = number
}

variable "api_min_count" {
  description = "Minimum number of API tasks."
  type        = number
}

variable "api_max_count" {
  description = "Maximum number of API tasks."
  type        = number
}

variable "bootstrap_admin_email" {
  description = "Account that becomes administrator when it registers. Leave empty after the first admin exists."
  type        = string
  default     = ""
}

variable "jwt_secret_version" {
  description = "Increment to rotate the access-token signing key."
  type        = number
  default     = 1
}

# --- Database ---------------------------------------------------------------------------------

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string
}

variable "db_allocated_storage" {
  description = "Initial storage (GiB)."
  type        = number
  default     = 20
}

variable "db_max_allocated_storage" {
  description = "Storage autoscaling ceiling (GiB)."
  type        = number
  default     = 100
}

variable "db_multi_az" {
  description = "Run a standby replica in another AZ."
  type        = bool
}

variable "db_backup_retention_days" {
  description = "Automated backup retention."
  type        = number
}

variable "db_password_version" {
  description = "Increment to rotate the database master password."
  type        = number
  default     = 1
}

variable "deletion_protection" {
  description = "Protect stateful resources (database, load balancer, buckets) from deletion."
  type        = bool
}

# --- Edge -------------------------------------------------------------------------------------

variable "domain_name" {
  description = "Custom hostname for the platform, e.g. ops.example.com. Null uses the CloudFront hostname."
  type        = string
  default     = null
}

variable "hosted_zone_id" {
  description = "Route 53 hosted zone containing domain_name. Required with domain_name."
  type        = string
  default     = null

  validation {
    condition     = (var.hosted_zone_id == null) == (var.domain_name == null)
    error_message = "domain_name and hosted_zone_id must be set together."
  }
}

variable "enable_waf" {
  description = "Attach AWS WAF to the CloudFront distribution."
  type        = bool
}

variable "cloudfront_price_class" {
  description = "CloudFront price class."
  type        = string
  default     = "PriceClass_100"
}

# --- Operations -------------------------------------------------------------------------------

variable "log_retention_days" {
  description = "CloudWatch log retention for application and flow logs."
  type        = number
}

variable "alarm_emails" {
  description = "Recipients of CloudWatch alarm notifications."
  type        = list(string)
  default     = []
}

variable "incident_notification_emails" {
  description = "Recipients of incident notifications published by the API."
  type        = list(string)
  default     = []
}
