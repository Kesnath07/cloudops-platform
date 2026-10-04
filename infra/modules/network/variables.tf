variable "name" {
  description = "Prefix for resource names, e.g. cloudops-dev."
  type        = string
}

variable "cidr_block" {
  description = "IPv4 CIDR of the VPC. A /16 leaves room for the subnet plan in locals."
  type        = string

  validation {
    condition     = can(cidrhost(var.cidr_block, 0)) && endswith(var.cidr_block, "/16")
    error_message = "cidr_block must be a valid /16 network."
  }
}

variable "az_count" {
  description = "Number of availability zones to span (2 or 3)."
  type        = number

  validation {
    condition     = var.az_count >= 2 && var.az_count <= 3
    error_message = "az_count must be 2 or 3; the load balancer and database subnet group need at least two zones."
  }
}

variable "single_nat_gateway" {
  description = "Use one shared NAT gateway (cheaper, single-AZ egress) instead of one per AZ."
  type        = bool
}

variable "kms_key_arn" {
  description = "KMS key used to encrypt the flow log group."
  type        = string
}

variable "flow_log_retention_days" {
  description = "Retention of VPC flow logs in CloudWatch."
  type        = number
}

variable "flow_log_traffic_type" {
  description = "Traffic captured by flow logs: ACCEPT, REJECT or ALL."
  type        = string
  default     = "REJECT"

  validation {
    condition     = contains(["ACCEPT", "REJECT", "ALL"], var.flow_log_traffic_type)
    error_message = "flow_log_traffic_type must be ACCEPT, REJECT or ALL."
  }
}
