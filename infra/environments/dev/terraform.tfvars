# Development: smallest footprint that still exercises the full architecture.
environment = "dev"
aws_region  = "eu-west-1"

vpc_cidr           = "10.40.0.0/16"
az_count           = 2
single_nat_gateway = true

api_cpu       = 512
api_memory    = 1024
api_min_count = 1
api_max_count = 2

db_instance_class        = "db.t4g.micro"
db_multi_az              = false
db_backup_retention_days = 3
deletion_protection      = false

enable_waf         = false
log_retention_days = 14
