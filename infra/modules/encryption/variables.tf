variable "name" {
  description = "Prefix for resource names; the key alias becomes alias/<name>."
  type        = string
}

variable "deletion_window_days" {
  description = "Waiting period before a scheduled key deletion takes effect."
  type        = number
  default     = 30
}
