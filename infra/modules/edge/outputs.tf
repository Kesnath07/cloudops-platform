output "distribution_id" {
  description = "CloudFront distribution ID (used for cache invalidation)."
  value       = aws_cloudfront_distribution.this.id
}

output "distribution_domain_name" {
  description = "Default cloudfront.net hostname."
  value       = aws_cloudfront_distribution.this.domain_name
}

output "site_bucket_name" {
  description = "Bucket that receives the built console."
  value       = aws_s3_bucket.site.bucket
}

output "url" {
  description = "Public URL of the platform."
  value       = "https://${local.custom_domain ? var.domain_name : aws_cloudfront_distribution.this.domain_name}"
}
