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

  validation {
    condition     = can(regex("^[a-z]{2}(-[a-z]+)+-[0-9]$", var.aws_region))
    error_message = "aws_region must be an AWS region code such as eu-west-1."
  }
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

  validation {
    condition     = contains([256, 512, 1024, 2048, 4096, 8192, 16384], var.api_cpu)
    error_message = "api_cpu must be a Fargate CPU size: 256, 512, 1024, 2048, 4096, 8192 or 16384."
  }
}

variable "api_memory" {
  description = "Fargate task memory (MiB)."
  type        = number

  # Fargate accepts between 2x and 8x the CPU units in MiB (narrower at the largest sizes).
  validation {
    condition     = var.api_memory >= 2 * var.api_cpu && var.api_memory <= 8 * var.api_cpu
    error_message = "api_memory must be between 2x and 8x api_cpu to form a valid Fargate task size."
  }
}

variable "api_min_count" {
  description = "Minimum number of API tasks."
  type        = number

  validation {
    condition     = var.api_min_count >= 1
    error_message = "api_min_count must be at least 1."
  }
}

variable "api_max_count" {
  description = "Maximum number of API tasks."
  type        = number

  validation {
    condition     = var.api_max_count >= var.api_min_count
    error_message = "api_max_count must be greater than or equal to api_min_count."
  }
}

variable "bootstrap_admin_email" {
  description = "Account that becomes administrator when it registers. Leave empty after the first admin exists."
  type        = string
  default     = ""

  validation {
    condition     = var.bootstrap_admin_email == "" || can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", var.bootstrap_admin_email))
    error_message = "bootstrap_admin_email must be empty or an email address."
  }
}

variable "jwt_secret_version" {
  description = "Increment to rotate the access-token signing key."
  type        = number
  default     = 1

  validation {
    condition     = var.jwt_secret_version >= 1 && floor(var.jwt_secret_version) == var.jwt_secret_version
    error_message = "jwt_secret_version must be a positive whole number."
  }
}

# --- Database ---------------------------------------------------------------------------------

variable "db_instance_class" {
  description = "RDS instance class."
  type        = string

  validation {
    condition     = can(regex("^db\\.[a-z0-9]+\\.[a-z0-9]+$", var.db_instance_class))
    error_message = "db_instance_class must be an RDS instance class such as db.t4g.micro."
  }
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

  validation {
    condition     = var.db_max_allocated_storage >= var.db_allocated_storage
    error_message = "db_max_allocated_storage must be greater than or equal to db_allocated_storage."
  }
}

variable "db_multi_az" {
  description = "Run a standby replica in another AZ."
  type        = bool
}

variable "db_backup_retention_days" {
  description = "Automated backup retention."
  type        = number

  # 0 would disable automated backups and point-in-time recovery entirely.
  validation {
    condition     = var.db_backup_retention_days >= 1 && var.db_backup_retention_days <= 35
    error_message = "db_backup_retention_days must be between 1 and 35."
  }
}

variable "db_password_version" {
  description = "Increment to rotate the database master password."
  type        = number
  default     = 1

  validation {
    condition     = var.db_password_version >= 1 && floor(var.db_password_version) == var.db_password_version
    error_message = "db_password_version must be a positive whole number."
  }
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

  validation {
    condition     = contains(["PriceClass_100", "PriceClass_200", "PriceClass_All"], var.cloudfront_price_class)
    error_message = "cloudfront_price_class must be PriceClass_100, PriceClass_200 or PriceClass_All."
  }
}

# --- Operations -------------------------------------------------------------------------------

variable "log_retention_days" {
  description = "CloudWatch log retention for application, database and flow logs."
  type        = number

  validation {
    condition     = contains([1, 3, 5, 7, 14, 30, 60, 90, 120, 150, 180, 365, 400, 545, 731, 1096, 1827, 2192, 2557, 2922, 3288, 3653], var.log_retention_days)
    error_message = "log_retention_days must be a retention period CloudWatch Logs supports (e.g. 14, 30, 90, 365)."
  }
}

variable "alarm_emails" {
  description = "Recipients of CloudWatch alarm notifications."
  type        = list(string)
  default     = []

  validation {
    condition     = alltrue([for email in var.alarm_emails : can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", email))])
    error_message = "alarm_emails must contain only email addresses."
  }
}

variable "incident_notification_emails" {
  description = "Recipients of incident notifications published by the API."
  type        = list(string)
  default     = []

  validation {
    condition     = alltrue([for email in var.incident_notification_emails : can(regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", email))])
    error_message = "incident_notification_emails must contain only email addresses."
  }
}
