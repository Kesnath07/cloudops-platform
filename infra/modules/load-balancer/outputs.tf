output "origin_domain_name" {
  description = "Hostname CloudFront uses for the API origin."
  value       = local.https ? aws_route53_record.origin[0].fqdn : aws_lb.this.dns_name
}

output "origin_protocol_policy" {
  description = "Protocol CloudFront must use towards the origin."
  value       = local.https ? "https-only" : "http-only"
}

output "origin_header_name" {
  description = "Header name carrying the origin verification secret."
  value       = local.origin_header_name
}

output "target_group_arn" {
  description = "Target group for the API service. Depends on the forwarding rule so ECS registers only once traffic can flow."
  value       = aws_lb_target_group.api.arn
  depends_on  = [aws_lb_listener_rule.from_cloudfront]
}

output "arn_suffix" {
  description = "Load balancer ARN suffix for CloudWatch metrics."
  value       = aws_lb.this.arn_suffix
}

output "target_group_arn_suffix" {
  description = "Target group ARN suffix for CloudWatch metrics."
  value       = aws_lb_target_group.api.arn_suffix
}
