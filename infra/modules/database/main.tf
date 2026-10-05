data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

locals {
  identifier = "${var.name}-postgres"

  # RDS creates these groups on first export with no retention and no encryption; owning them
  # here applies the environment's retention period and key instead.
  log_exports = toset(["postgresql", "upgrade"])
}

# The master password is generated as an ephemeral value and passed only to write-only arguments,
# so it never appears in the plan or the Terraform state. Bumping password_version rotates it:
# both the database and the secret receive the new value in the same apply.
ephemeral "random_password" "master" {
  length           = 40
  special          = true
  override_special = "!#$%^&*()-_=+[]{}<>:?"
}

resource "aws_secretsmanager_secret" "credentials" {
  name                    = "${var.name}/database/master"
  description             = "PostgreSQL credentials for ${var.name}"
  kms_key_id              = var.kms_key_arn
  recovery_window_in_days = var.secret_recovery_window_days
}

resource "aws_secretsmanager_secret_version" "credentials" {
  secret_id = aws_secretsmanager_secret.credentials.id
  secret_string_wo = jsonencode({
    username = var.master_username
    password = ephemeral.random_password.master.result
  })
  secret_string_wo_version = var.password_version
}

resource "aws_db_subnet_group" "this" {
  name        = "${var.name}-database"
  description = "Isolated data subnets for ${var.name}"
  subnet_ids  = var.subnet_ids
}

resource "aws_db_parameter_group" "this" {
  name_prefix = "${var.name}-pg${var.engine_version}-"
  family      = "postgres${var.engine_version}"
  description = "PostgreSQL ${var.engine_version} settings for ${var.name}"

  # Reject any connection that does not use TLS.
  parameter {
    name  = "rds.force_ssl"
    value = "1"
  }

  parameter {
    name  = "log_min_duration_statement"
    value = "1000"
  }

  parameter {
    name  = "log_connections"
    value = "1"
  }

  parameter {
    name  = "log_disconnections"
    value = "1"
  }

  lifecycle {
    create_before_destroy = true
  }
}

data "aws_iam_policy_document" "monitoring_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["monitoring.rds.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

resource "aws_iam_role" "monitoring" {
  name               = "${var.name}-rds-monitoring"
  assume_role_policy = data.aws_iam_policy_document.monitoring_assume.json
}

resource "aws_iam_role_policy_attachment" "monitoring" {
  role       = aws_iam_role.monitoring.name
  policy_arn = "arn:${data.aws_partition.current.partition}:iam::aws:policy/service-role/AmazonRDSEnhancedMonitoringRole"
}

resource "aws_cloudwatch_log_group" "exports" {
  for_each = local.log_exports

  name              = "/aws/rds/instance/${local.identifier}/${each.key}"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
}

resource "aws_db_instance" "this" {
  #checkov:skip=CKV_AWS_161:The API authenticates with a Secrets Manager credential; IAM database auth is not used.
  identifier     = local.identifier
  engine         = "postgres"
  engine_version = var.engine_version
  instance_class = var.instance_class

  allocated_storage     = var.allocated_storage
  max_allocated_storage = var.max_allocated_storage
  storage_type          = "gp3"
  storage_encrypted     = true
  kms_key_id            = var.kms_key_arn

  db_name             = var.database_name
  username            = var.master_username
  password_wo         = ephemeral.random_password.master.result
  password_wo_version = var.password_version

  db_subnet_group_name   = aws_db_subnet_group.this.name
  vpc_security_group_ids = [var.security_group_id]
  parameter_group_name   = aws_db_parameter_group.this.name
  publicly_accessible    = false
  multi_az               = var.multi_az

  backup_retention_period   = var.backup_retention_days
  backup_window             = "02:00-03:00"
  maintenance_window        = "sun:03:30-sun:04:30"
  copy_tags_to_snapshot     = true
  deletion_protection       = var.deletion_protection
  skip_final_snapshot       = !var.deletion_protection
  delete_automated_backups  = !var.deletion_protection
  final_snapshot_identifier = "${local.identifier}-final"

  auto_minor_version_upgrade      = true
  enabled_cloudwatch_logs_exports = sort(local.log_exports)
  performance_insights_enabled    = true
  performance_insights_kms_key_id = var.kms_key_arn
  monitoring_interval             = 60
  monitoring_role_arn             = aws_iam_role.monitoring.arn

  depends_on = [aws_iam_role_policy_attachment.monitoring, aws_cloudwatch_log_group.exports]
}
