# Production: zone-redundant egress, database standby, WAF and longer retention.
# domain_name, hosted_zone_id and notification recipients are supplied by the pipeline from
# GitHub environment variables so this file stays free of account-specific values.
environment = "prod"
aws_region  = "eu-west-1"

vpc_cidr           = "10.50.0.0/16"
az_count           = 3
single_nat_gateway = false

api_cpu       = 1024
api_memory    = 2048
api_min_count = 2
api_max_count = 6

db_instance_class        = "db.t4g.medium"
db_multi_az              = true
db_backup_retention_days = 14
deletion_protection      = true

enable_waf         = true
log_retention_days = 90
