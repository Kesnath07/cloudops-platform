variable "hosted_zone_id" {
  description = "Route 53 hosted zone that serves the domain (used for DNS validation)."
  type        = string
}

variable "app_domain_name" {
  description = "Public hostname of the console, served by CloudFront."
  type        = string
}

variable "origin_domain_name" {
  description = "Hostname CloudFront uses to reach the load balancer over HTTPS."
  type        = string
}
