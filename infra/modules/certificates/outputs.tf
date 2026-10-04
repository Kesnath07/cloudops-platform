output "cloudfront_certificate_arn" {
  description = "Validated us-east-1 certificate for the CloudFront distribution."
  value       = aws_acm_certificate_validation.cloudfront.certificate_arn
}

output "origin_certificate_arn" {
  description = "Validated regional certificate for the load balancer."
  value       = aws_acm_certificate_validation.origin.certificate_arn
}
