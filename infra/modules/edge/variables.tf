variable "name" {
  description = "Prefix for resource names."
  type        = string
}

variable "kms_key_arn" {
  description = "KMS key encrypting the static site bucket."
  type        = string
}

variable "api_origin_domain_name" {
  description = "Hostname of the API origin (load balancer)."
  type        = string
}

variable "api_origin_protocol_policy" {
  description = "https-only when the origin has a certificate, otherwise http-only."
  type        = string

  validation {
    condition     = contains(["https-only", "http-only"], var.api_origin_protocol_policy)
    error_message = "api_origin_protocol_policy must be https-only or http-only."
  }
}

variable "origin_header_name" {
  description = "Header carrying the origin verification secret."
  type        = string
}

variable "origin_header_value" {
  description = "Origin verification secret, also required by the load balancer."
  type        = string
  sensitive   = true
}

variable "domain_name" {
  description = "Custom hostname for the distribution; null uses the default cloudfront.net name."
  type        = string
  default     = null
}

variable "hosted_zone_id" {
  description = "Route 53 zone for the custom hostname record."
  type        = string
  default     = null
}

variable "certificate_arn" {
  description = "us-east-1 ACM certificate for the custom hostname."
  type        = string
  default     = null
}

variable "price_class" {
  description = "CloudFront price class."
  type        = string
  default     = "PriceClass_100"
}

variable "enable_waf" {
  description = "Attach an AWS WAF web ACL with managed rules and login rate limiting."
  type        = bool
}

variable "login_rate_limit" {
  description = "Maximum authentication requests per client IP in any 5-minute window."
  type        = number
  default     = 100
}

variable "deletion_protection" {
  description = "Keep site objects when destroying the bucket."
  type        = bool
}
