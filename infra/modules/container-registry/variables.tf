variable "repository_name" {
  description = "Name of the ECR repository."
  type        = string
}

variable "kms_key_arn" {
  description = "KMS key used to encrypt images at rest."
  type        = string
}

variable "retained_image_count" {
  description = "Number of most recent images kept; older ones are expired."
  type        = number
  default     = 50
}
