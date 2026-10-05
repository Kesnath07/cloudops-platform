data "aws_region" "current" {}
data "aws_caller_identity" "current" {}

locals {
  container_name = "api"
}

resource "aws_ecs_cluster" "this" {
  name = var.name

  setting {
    name  = "containerInsights"
    value = "enabled"
  }
}

resource "aws_cloudwatch_log_group" "api" {
  name              = "/ecs/${var.name}/api"
  retention_in_days = var.log_retention_days
  kms_key_id        = var.kms_key_arn
}

# --- Secrets and messaging owned by the application ---------------------------------------------

ephemeral "random_password" "jwt_signing_key" {
  length  = 64
  special = false
}

resource "aws_secretsmanager_secret" "jwt_signing_key" {
  #checkov:skip=CKV2_AWS_57:Rotating the HS256 key invalidates all sessions; it is rotated deliberately via jwt_secret_version.
  name                    = "${var.name}/api/jwt-signing-key"
  description             = "HS256 signing key for API access tokens"
  kms_key_id              = var.kms_key_arn
  recovery_window_in_days = 7
}

resource "aws_secretsmanager_secret_version" "jwt_signing_key" {
  secret_id                = aws_secretsmanager_secret.jwt_signing_key.id
  secret_string_wo         = ephemeral.random_password.jwt_signing_key.result
  secret_string_wo_version = var.jwt_secret_version
}

resource "aws_sns_topic" "incidents" {
  name              = "${var.name}-incidents"
  kms_master_key_id = var.kms_key_arn
}

resource "aws_sns_topic_subscription" "incident_email" {
  for_each = toset(var.incident_notification_emails)

  topic_arn = aws_sns_topic.incidents.arn
  protocol  = "email"
  endpoint  = each.value
}

# --- IAM ------------------------------------------------------------------------------------------

data "aws_iam_policy_document" "ecs_tasks_assume" {
  statement {
    actions = ["sts:AssumeRole"]

    principals {
      type        = "Service"
      identifiers = ["ecs-tasks.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [data.aws_caller_identity.current.account_id]
    }
  }
}

# Execution role: used by the ECS agent to pull the image, ship logs and inject secrets. It is
# scoped to this service's repository, log group and secrets instead of the account-wide
# AmazonECSTaskExecutionRolePolicy.
resource "aws_iam_role" "execution" {
  name               = "${var.name}-api-execution"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "execution" {
  statement {
    sid       = "RegistryLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "PullApiImage"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:GetDownloadUrlForLayer",
    ]
    resources = [var.image_repository_arn]
  }

  statement {
    sid       = "WriteApiLogs"
    actions   = ["logs:CreateLogStream", "logs:PutLogEvents"]
    resources = ["${aws_cloudwatch_log_group.api.arn}:*"]
  }

  statement {
    sid       = "ReadApplicationSecrets"
    actions   = ["secretsmanager:GetSecretValue"]
    resources = [var.database_secret_arn, aws_secretsmanager_secret.jwt_signing_key.arn]
  }

  # The key also protects logs, the database and notifications; it may only be used to decrypt
  # through Secrets Manager.
  statement {
    sid       = "DecryptApplicationSecrets"
    actions   = ["kms:Decrypt"]
    resources = [var.kms_key_arn]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["secretsmanager.${data.aws_region.current.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "execution" {
  name   = "launch-api-tasks"
  role   = aws_iam_role.execution.id
  policy = data.aws_iam_policy_document.execution.json
}

# Task role: what the application code itself may do. It only publishes incident notifications.
resource "aws_iam_role" "task" {
  name               = "${var.name}-api-task"
  assume_role_policy = data.aws_iam_policy_document.ecs_tasks_assume.json
}

data "aws_iam_policy_document" "task" {
  statement {
    sid       = "PublishIncidentNotifications"
    actions   = ["sns:Publish"]
    resources = [aws_sns_topic.incidents.arn]
  }

  statement {
    sid       = "EncryptNotificationsForTopic"
    actions   = ["kms:GenerateDataKey*", "kms:Decrypt"]
    resources = [var.kms_key_arn]

    condition {
      test     = "StringEquals"
      variable = "kms:ViaService"
      values   = ["sns.${data.aws_region.current.region}.amazonaws.com"]
    }
  }
}

resource "aws_iam_role_policy" "task" {
  name   = "publish-incident-notifications"
  role   = aws_iam_role.task.id
  policy = data.aws_iam_policy_document.task.json
}

# --- Service ------------------------------------------------------------------------------------

resource "aws_ecs_task_definition" "api" {
  family                   = "${var.name}-api"
  requires_compatibilities = ["FARGATE"]
  network_mode             = "awsvpc"
  cpu                      = var.cpu
  memory                   = var.memory
  execution_role_arn       = aws_iam_role.execution.arn
  task_role_arn            = aws_iam_role.task.arn

  runtime_platform {
    operating_system_family = "LINUX"
    cpu_architecture        = "ARM64"
  }

  volume {
    name = "tmp"
  }

  container_definitions = jsonencode([
    {
      name                   = local.container_name
      image                  = var.image
      essential              = true
      readonlyRootFilesystem = true
      user                   = "10001:10001"
      stopTimeout            = 30

      # The JVM runs unprivileged on an unprivileged port and needs no kernel capabilities.
      linuxParameters = {
        capabilities = { drop = ["ALL"] }
      }

      portMappings = [
        { containerPort = var.app_port, protocol = "tcp" }
      ]

      mountPoints = [
        { sourceVolume = "tmp", containerPath = "/tmp", readOnly = false }
      ]

      environment = [
        { name = "SPRING_PROFILES_ACTIVE", value = "aws" },
        { name = "SPRING_DATASOURCE_URL", value = "jdbc:postgresql://${var.database_address}:${var.database_port}/${var.database_name}" },
        { name = "CLOUDOPS_RELEASE", value = var.release },
        { name = "CLOUDOPS_INCIDENT_TOPIC_ARN", value = aws_sns_topic.incidents.arn },
        { name = "CLOUDOPS_NOTIFICATION_MIN_SEVERITY", value = var.incident_notification_min_severity },
        { name = "CLOUDOPS_BOOTSTRAP_ADMIN_EMAIL", value = var.bootstrap_admin_email },
        # Not read by the application: changing it when a secret is rotated creates a new task
        # definition revision, so ECS replaces running tasks and they load the new secret values.
        { name = "CLOUDOPS_SECRETS_REVISION", value = "db-${var.database_secret_version}.jwt-${var.jwt_secret_version}" },
      ]

      secrets = [
        { name = "SPRING_DATASOURCE_USERNAME", valueFrom = "${var.database_secret_arn}:username::" },
        { name = "SPRING_DATASOURCE_PASSWORD", valueFrom = "${var.database_secret_arn}:password::" },
        { name = "CLOUDOPS_JWT_SECRET", valueFrom = aws_secretsmanager_secret.jwt_signing_key.arn },
      ]

      healthCheck = {
        command     = ["CMD-SHELL", "wget -q -O /dev/null http://127.0.0.1:${var.app_port}/actuator/health/liveness || exit 1"]
        interval    = 30
        timeout     = 5
        retries     = 3
        startPeriod = 90
      }

      logConfiguration = {
        logDriver = "awslogs"
        options = {
          awslogs-group         = aws_cloudwatch_log_group.api.name
          awslogs-region        = data.aws_region.current.region
          awslogs-stream-prefix = "api"
        }
      }
    }
  ])

  depends_on = [aws_secretsmanager_secret_version.jwt_signing_key, aws_iam_role_policy.execution]
}

resource "aws_ecs_service" "api" {
  name                              = "api"
  cluster                           = aws_ecs_cluster.this.id
  task_definition                   = aws_ecs_task_definition.api.arn
  desired_count                     = var.desired_count
  launch_type                       = "FARGATE"
  platform_version                  = "LATEST"
  health_check_grace_period_seconds = 120
  enable_ecs_managed_tags           = true
  propagate_tags                    = "SERVICE"
  wait_for_steady_state             = true

  deployment_minimum_healthy_percent = 100
  deployment_maximum_percent         = 200

  deployment_circuit_breaker {
    enable   = true
    rollback = true
  }

  network_configuration {
    subnets          = var.subnet_ids
    security_groups  = [var.security_group_id]
    assign_public_ip = false
  }

  load_balancer {
    target_group_arn = var.target_group_arn
    container_name   = local.container_name
    container_port   = var.app_port
  }

  lifecycle {
    # Autoscaling owns the running task count after creation.
    ignore_changes = [desired_count]
  }
}

resource "aws_appautoscaling_target" "api" {
  service_namespace  = "ecs"
  resource_id        = "service/${aws_ecs_cluster.this.name}/${aws_ecs_service.api.name}"
  scalable_dimension = "ecs:service:DesiredCount"
  min_capacity       = var.min_count
  max_capacity       = var.max_count
}

resource "aws_appautoscaling_policy" "cpu" {
  name               = "${var.name}-api-cpu"
  policy_type        = "TargetTrackingScaling"
  service_namespace  = aws_appautoscaling_target.api.service_namespace
  resource_id        = aws_appautoscaling_target.api.resource_id
  scalable_dimension = aws_appautoscaling_target.api.scalable_dimension

  target_tracking_scaling_policy_configuration {
    target_value       = var.cpu_target_percent
    scale_in_cooldown  = 300
    scale_out_cooldown = 60

    predefined_metric_specification {
      predefined_metric_type = "ECSServiceAverageCPUUtilization"
    }
  }
}
