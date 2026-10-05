locals {
  name     = "cloudops-${var.environment}"
  app_port = 8080

  custom_domain      = var.domain_name != null
  origin_domain_name = local.custom_domain ? "origin.${var.domain_name}" : null
}

data "aws_ecr_repository" "api" {
  name = var.ecr_repository_name
}

# Shared secret CloudFront attaches to every API request; the load balancer refuses requests
# without it, so the ALB cannot be used to bypass CloudFront and WAF.
resource "random_password" "origin_verify" {
  length  = 48
  special = false
}

module "encryption" {
  source = "./modules/encryption"

  name = local.name
}

module "network" {
  source = "./modules/network"

  name                    = local.name
  cidr_block              = var.vpc_cidr
  az_count                = var.az_count
  single_nat_gateway      = var.single_nat_gateway
  kms_key_arn             = module.encryption.key_arn
  flow_log_retention_days = var.log_retention_days
  flow_log_traffic_type   = var.flow_log_traffic_type
}

module "security_groups" {
  source = "./modules/security-groups"

  name          = local.name
  vpc_id        = module.network.vpc_id
  listener_port = local.custom_domain ? 443 : 80
  app_port      = local.app_port
}

module "certificates" {
  source = "./modules/certificates"
  count  = local.custom_domain ? 1 : 0

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  hosted_zone_id     = var.hosted_zone_id
  app_domain_name    = var.domain_name
  origin_domain_name = local.origin_domain_name
}

module "database" {
  source = "./modules/database"

  name                  = local.name
  subnet_ids            = module.network.data_subnet_ids
  security_group_id     = module.security_groups.database_security_group_id
  kms_key_arn           = module.encryption.key_arn
  instance_class        = var.db_instance_class
  allocated_storage     = var.db_allocated_storage
  max_allocated_storage = var.db_max_allocated_storage
  multi_az              = var.db_multi_az
  backup_retention_days = var.db_backup_retention_days
  deletion_protection   = var.deletion_protection
  password_version      = var.db_password_version
  log_retention_days    = var.log_retention_days
}

module "load_balancer" {
  source = "./modules/load-balancer"

  name                       = local.name
  vpc_id                     = module.network.vpc_id
  subnet_ids                 = module.network.public_subnet_ids
  security_group_id          = module.security_groups.alb_security_group_id
  app_port                   = local.app_port
  certificate_arn            = local.custom_domain ? module.certificates[0].origin_certificate_arn : null
  origin_domain_name         = local.origin_domain_name
  hosted_zone_id             = var.hosted_zone_id
  origin_verify_header_value = random_password.origin_verify.result
  deletion_protection        = var.deletion_protection
  access_log_retention_days  = var.log_retention_days
}

module "api" {
  source = "./modules/ecs-service"

  name                         = local.name
  subnet_ids                   = module.network.app_subnet_ids
  security_group_id            = module.security_groups.app_security_group_id
  target_group_arn             = module.load_balancer.target_group_arn
  kms_key_arn                  = module.encryption.key_arn
  image                        = "${data.aws_ecr_repository.api.repository_url}:${var.api_image_tag}"
  image_repository_arn         = data.aws_ecr_repository.api.arn
  release                      = var.api_image_tag
  app_port                     = local.app_port
  cpu                          = var.api_cpu
  memory                       = var.api_memory
  desired_count                = var.api_min_count
  min_count                    = var.api_min_count
  max_count                    = var.api_max_count
  database_address             = module.database.address
  database_port                = module.database.port
  database_name                = module.database.database_name
  database_secret_arn          = module.database.credentials_secret_arn
  database_secret_version      = var.db_password_version
  jwt_secret_version           = var.jwt_secret_version
  bootstrap_admin_email        = var.bootstrap_admin_email
  incident_notification_emails = var.incident_notification_emails
  log_retention_days           = var.log_retention_days
}

module "edge" {
  source = "./modules/edge"

  providers = {
    aws           = aws
    aws.us_east_1 = aws.us_east_1
  }

  name                       = local.name
  kms_key_arn                = module.encryption.key_arn
  api_origin_domain_name     = module.load_balancer.origin_domain_name
  api_origin_protocol_policy = module.load_balancer.origin_protocol_policy
  origin_header_name         = module.load_balancer.origin_header_name
  origin_header_value        = random_password.origin_verify.result
  domain_name                = var.domain_name
  hosted_zone_id             = var.hosted_zone_id
  certificate_arn            = local.custom_domain ? module.certificates[0].cloudfront_certificate_arn : null
  price_class                = var.cloudfront_price_class
  enable_waf                 = var.enable_waf
  deletion_protection        = var.deletion_protection
}

module "observability" {
  source = "./modules/observability"

  name                     = local.name
  kms_key_arn              = module.encryption.key_arn
  alarm_emails             = var.alarm_emails
  load_balancer_arn_suffix = module.load_balancer.arn_suffix
  target_group_arn_suffix  = module.load_balancer.target_group_arn_suffix
  ecs_cluster_name         = module.api.cluster_name
  ecs_service_name         = module.api.service_name
  db_instance_identifier   = module.database.instance_identifier
  api_log_group_name       = module.api.log_group_name
}
