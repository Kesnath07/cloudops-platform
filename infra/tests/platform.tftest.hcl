# Offline tests: the AWS provider is mocked, so no credentials or cloud resources are needed.
# They assert security and topology properties that must hold for every environment.

mock_provider "aws" {
  mock_data "aws_availability_zones" {
    defaults = { names = ["eu-west-1a", "eu-west-1b", "eu-west-1c"] }
  }
  mock_data "aws_caller_identity" {
    defaults = { account_id = "123456789012" }
  }
  mock_data "aws_region" {
    defaults = { region = "eu-west-1" }
  }
  mock_data "aws_partition" {
    defaults = { partition = "aws" }
  }
  mock_data "aws_iam_policy_document" {
    defaults = { json = "{\"Version\":\"2012-10-17\",\"Statement\":[]}" }
  }
  mock_data "aws_ecr_repository" {
    defaults = { repository_url = "123456789012.dkr.ecr.eu-west-1.amazonaws.com/cloudops-api" }
  }
  mock_data "aws_elb_service_account" {
    defaults = { arn = "arn:aws:iam::156460612806:root" }
  }
  mock_data "aws_ec2_managed_prefix_list" {
    defaults = { id = "pl-4fa04526" }
  }
  mock_resource "aws_kms_key" {
    defaults = { arn = "arn:aws:kms:eu-west-1:123456789012:key/11111111-2222-3333-4444-555555555555" }
  }
  mock_resource "aws_iam_role" {
    defaults = { arn = "arn:aws:iam::123456789012:role/cloudops-test" }
  }
  mock_resource "aws_sns_topic" {
    defaults = { arn = "arn:aws:sns:eu-west-1:123456789012:cloudops-test" }
  }
  mock_resource "aws_secretsmanager_secret" {
    defaults = { arn = "arn:aws:secretsmanager:eu-west-1:123456789012:secret:cloudops-test" }
  }
  mock_resource "aws_lb_target_group" {
    defaults = { arn = "arn:aws:elasticloadbalancing:eu-west-1:123456789012:targetgroup/cloudops-test/0123456789abcdef" }
  }
  mock_resource "aws_lb" {
    defaults = {
      arn        = "arn:aws:elasticloadbalancing:eu-west-1:123456789012:loadbalancer/app/cloudops-test/0123456789abcdef"
      arn_suffix = "app/cloudops-test/0123456789abcdef"
      dns_name   = "cloudops-test-alb-123456.eu-west-1.elb.amazonaws.com"
      zone_id    = "Z32O12XQLNTSW2"
    }
  }
  mock_resource "aws_lb_listener" {
    defaults = { arn = "arn:aws:elasticloadbalancing:eu-west-1:123456789012:listener/app/cloudops-test/0123456789abcdef/0123456789abcdef" }
  }
  mock_resource "aws_cloudwatch_log_group" {
    defaults = { arn = "arn:aws:logs:eu-west-1:123456789012:log-group:/cloudops/test" }
  }
  mock_resource "aws_cloudfront_function" {
    defaults = { arn = "arn:aws:cloudfront::123456789012:function/cloudops-test-spa-routing" }
  }
  mock_resource "aws_s3_bucket" {
    defaults = { arn = "arn:aws:s3:::cloudops-test-bucket" }
  }
  mock_resource "aws_ecs_cluster" {
    defaults = { arn = "arn:aws:ecs:eu-west-1:123456789012:cluster/cloudops-test" }
  }
  mock_resource "aws_ecs_task_definition" {
    defaults = { arn = "arn:aws:ecs:eu-west-1:123456789012:task-definition/cloudops-test-api:1" }
  }
  mock_resource "aws_iam_openid_connect_provider" {
    defaults = { arn = "arn:aws:iam::123456789012:oidc-provider/token.actions.githubusercontent.com" }
  }
  mock_resource "aws_cloudfront_distribution" {
    defaults = {
      arn            = "arn:aws:cloudfront::123456789012:distribution/E2TESTDISTRIBUTION"
      domain_name    = "d111111abcdef8.cloudfront.net"
      hosted_zone_id = "Z2FDTNDATAQYW2"
    }
  }
}

mock_provider "aws" {
  alias = "us_east_1"
}

variables {
  environment              = "dev"
  aws_region               = "eu-west-1"
  api_image_tag            = "0123abc"
  vpc_cidr                 = "10.40.0.0/16"
  az_count                 = 2
  single_nat_gateway       = true
  api_cpu                  = 512
  api_memory               = 1024
  api_min_count            = 1
  api_max_count            = 2
  db_instance_class        = "db.t4g.micro"
  db_multi_az              = false
  db_backup_retention_days = 3
  deletion_protection      = false
  enable_waf               = false
  log_retention_days       = 14
}

run "development_environment_without_custom_domain" {
  command = apply

  assert {
    condition     = output.deployed_image == "123456789012.dkr.ecr.eu-west-1.amazonaws.com/cloudops-api:0123abc"
    error_message = "The API must run the image tagged with the requested commit."
  }

  assert {
    condition     = output.url == "https://d111111abcdef8.cloudfront.net"
    error_message = "Without a domain the platform is served from the CloudFront hostname over HTTPS."
  }

  assert {
    condition     = length(module.certificates) == 0
    error_message = "No certificates should be requested without a domain."
  }
}

run "image_tag_must_be_a_commit_sha" {
  command = plan

  variables {
    api_image_tag = "latest"
  }

  expect_failures = [var.api_image_tag]
}

run "domain_requires_hosted_zone" {
  command = plan

  variables {
    domain_name = "ops.example.com"
  }

  expect_failures = [var.hosted_zone_id]
}

run "network_isolates_the_data_tier" {
  command = apply

  module {
    source = "./modules/network"
  }

  variables {
    name                    = "cloudops-test"
    cidr_block              = "10.40.0.0/16"
    az_count                = 3
    single_nat_gateway      = false
    kms_key_arn             = "arn:aws:kms:eu-west-1:123456789012:key/test"
    flow_log_retention_days = 7
  }

  assert {
    condition     = length(aws_nat_gateway.this) == 3 && length(aws_route.app_egress) == 3
    error_message = "Each AZ needs its own NAT gateway route when single_nat_gateway is false."
  }

  assert {
    condition     = [for subnet in aws_subnet.data : subnet.cidr_block] == ["10.40.200.0/24", "10.40.201.0/24", "10.40.202.0/24"]
    error_message = "Data subnets must be the 10.40.200-202 /24 blocks."
  }

  assert {
    condition     = aws_subnet.app[0].cidr_block == "10.40.16.0/20"
    error_message = "App subnets must be /20 blocks starting at 10.40.16.0."
  }

  assert {
    condition     = length(aws_route_table_association.data) == 3 && alltrue([for association in aws_route_table_association.data : association.route_table_id == aws_route_table.data.id])
    error_message = "Every data subnet must use the route table that has no internet route."
  }
}

run "security_groups_only_allow_the_intended_paths" {
  command = apply

  module {
    source = "./modules/security-groups"
  }

  variables {
    name          = "cloudops-test"
    vpc_id        = "vpc-0123456789abcdef0"
    listener_port = 443
    app_port      = 8080
  }

  assert {
    condition     = aws_vpc_security_group_ingress_rule.alb_from_cloudfront.prefix_list_id == "pl-4fa04526" && aws_vpc_security_group_ingress_rule.alb_from_cloudfront.cidr_ipv4 == null
    error_message = "The load balancer must accept traffic only from the CloudFront origin-facing prefix list."
  }

  assert {
    condition     = aws_vpc_security_group_ingress_rule.database_from_app.referenced_security_group_id == aws_security_group.app.id && aws_vpc_security_group_ingress_rule.database_from_app.cidr_ipv4 == null
    error_message = "The database must accept connections only from the API security group."
  }

  assert {
    condition     = aws_vpc_security_group_ingress_rule.app_from_alb.referenced_security_group_id == aws_security_group.alb.id
    error_message = "API tasks must accept traffic only from the load balancer."
  }
}

run "database_is_private_encrypted_and_tls_only" {
  command = apply

  module {
    source = "./modules/database"
  }

  variables {
    name                  = "cloudops-test"
    subnet_ids            = ["subnet-a", "subnet-b"]
    security_group_id     = "sg-0123456789abcdef0"
    kms_key_arn           = "arn:aws:kms:eu-west-1:123456789012:key/test"
    instance_class        = "db.t4g.micro"
    allocated_storage     = 20
    max_allocated_storage = 100
    multi_az              = false
    backup_retention_days = 7
    deletion_protection   = true
  }

  assert {
    condition     = aws_db_instance.this.publicly_accessible == false
    error_message = "The database must never be publicly accessible."
  }

  assert {
    condition     = aws_db_instance.this.storage_encrypted && aws_db_instance.this.kms_key_id == "arn:aws:kms:eu-west-1:123456789012:key/test"
    error_message = "Database storage must be encrypted with the environment key."
  }

  assert {
    condition     = anytrue([for parameter in aws_db_parameter_group.this.parameter : parameter.name == "rds.force_ssl" && parameter.value == "1"])
    error_message = "The database must reject non-TLS connections."
  }

  assert {
    condition     = aws_db_instance.this.deletion_protection && aws_db_instance.this.skip_final_snapshot == false
    error_message = "Protected databases must take a final snapshot."
  }

  assert {
    condition     = aws_db_instance.this.backup_retention_period == 7
    error_message = "Automated backups must be retained as configured."
  }
}

run "load_balancer_without_certificate_still_requires_origin_secret" {
  command = apply

  module {
    source = "./modules/load-balancer"
  }

  variables {
    name                       = "cloudops-test"
    vpc_id                     = "vpc-0123456789abcdef0"
    subnet_ids                 = ["subnet-a", "subnet-b"]
    security_group_id          = "sg-0123456789abcdef0"
    app_port                   = 8080
    origin_verify_header_value = "test-origin-secret"
    deletion_protection        = false
    access_log_retention_days  = 7
  }

  assert {
    condition     = length(aws_lb_listener.http) == 1 && length(aws_lb_listener.https) == 0
    error_message = "Without a certificate only the HTTP origin listener is created."
  }

  assert {
    condition     = aws_lb_listener.http[0].default_action[0].fixed_response[0].status_code == "403"
    error_message = "Requests without the origin secret must be refused."
  }

  assert {
    condition     = aws_lb.this.drop_invalid_header_fields
    error_message = "The load balancer must drop malformed headers."
  }
}

run "load_balancer_with_certificate_uses_modern_tls" {
  command = apply

  module {
    source = "./modules/load-balancer"
  }

  variables {
    name                       = "cloudops-test"
    vpc_id                     = "vpc-0123456789abcdef0"
    subnet_ids                 = ["subnet-a", "subnet-b"]
    security_group_id          = "sg-0123456789abcdef0"
    app_port                   = 8080
    certificate_arn            = "arn:aws:acm:eu-west-1:123456789012:certificate/test"
    origin_domain_name         = "origin.ops.example.com"
    hosted_zone_id             = "Z0123456789ABCDEFGHIJ"
    origin_verify_header_value = "test-origin-secret"
    deletion_protection        = true
    access_log_retention_days  = 30
  }

  assert {
    condition     = length(aws_lb_listener.http) == 0 && aws_lb_listener.https[0].ssl_policy == "ELBSecurityPolicy-TLS13-1-2-2021-06"
    error_message = "With a certificate only an HTTPS listener with a TLS 1.2+ policy may exist."
  }
}
