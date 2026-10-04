# CloudFront only accepts certificates from us-east-1; the load balancer needs one in its own
# region. Both are validated through DNS records in the same hosted zone.

resource "aws_acm_certificate" "cloudfront" {
  provider          = aws.us_east_1
  domain_name       = var.app_domain_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

resource "aws_acm_certificate" "origin" {
  domain_name       = var.origin_domain_name
  validation_method = "DNS"

  lifecycle {
    create_before_destroy = true
  }
}

locals {
  validation_records = merge(
    { for option in aws_acm_certificate.cloudfront.domain_validation_options : option.domain_name => option },
    { for option in aws_acm_certificate.origin.domain_validation_options : option.domain_name => option },
  )
}

resource "aws_route53_record" "validation" {
  for_each = local.validation_records

  zone_id         = var.hosted_zone_id
  name            = each.value.resource_record_name
  type            = each.value.resource_record_type
  records         = [each.value.resource_record_value]
  ttl             = 300
  allow_overwrite = true
}

resource "aws_acm_certificate_validation" "cloudfront" {
  provider                = aws.us_east_1
  certificate_arn         = aws_acm_certificate.cloudfront.arn
  validation_record_fqdns = [aws_route53_record.validation[var.app_domain_name].fqdn]
}

resource "aws_acm_certificate_validation" "origin" {
  certificate_arn         = aws_acm_certificate.origin.arn
  validation_record_fqdns = [aws_route53_record.validation[var.origin_domain_name].fqdn]
}
