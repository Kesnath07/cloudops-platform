output "key_arn" {
  description = "ARN of the environment's customer-managed KMS key."
  value       = aws_kms_key.this.arn
}
