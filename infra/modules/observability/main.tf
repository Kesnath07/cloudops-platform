data "aws_region" "current" {}

resource "aws_sns_topic" "alarms" {
  name              = "${var.name}-alarms"
  kms_master_key_id = var.kms_key_arn
}

resource "aws_sns_topic_subscription" "alarm_email" {
  for_each = toset(var.alarm_emails)

  topic_arn = aws_sns_topic.alarms.arn
  protocol  = "email"
  endpoint  = each.value
}

locals {
  alb_dimensions = {
    LoadBalancer = var.load_balancer_arn_suffix
    TargetGroup  = var.target_group_arn_suffix
  }
  ecs_dimensions = {
    ClusterName = var.ecs_cluster_name
    ServiceName = var.ecs_service_name
  }
  rds_dimensions = {
    DBInstanceIdentifier = var.db_instance_identifier
  }

  # Each alarm notifies on both transitions so responders also learn when a problem clears.
  alarms = {
    api-5xx = {
      description  = "API returned more than ${var.target_5xx_threshold} server errors in 5 minutes"
      namespace    = "AWS/ApplicationELB"
      metric       = "HTTPCode_Target_5XX_Count"
      statistic    = "Sum"
      extended     = null
      dimensions   = local.alb_dimensions
      period       = 300
      evaluations  = 1
      threshold    = var.target_5xx_threshold
      comparison   = "GreaterThanThreshold"
      missing_data = "notBreaching"
    }
    api-latency-p95 = {
      description  = "API p95 latency above ${var.latency_p95_threshold_seconds}s for 15 minutes"
      namespace    = "AWS/ApplicationELB"
      metric       = "TargetResponseTime"
      statistic    = null
      extended     = "p95"
      dimensions   = local.alb_dimensions
      period       = 300
      evaluations  = 3
      threshold    = var.latency_p95_threshold_seconds
      comparison   = "GreaterThanThreshold"
      missing_data = "notBreaching"
    }
    api-unhealthy-targets = {
      description  = "At least one API task failing load balancer health checks"
      namespace    = "AWS/ApplicationELB"
      metric       = "UnHealthyHostCount"
      statistic    = "Maximum"
      extended     = null
      dimensions   = local.alb_dimensions
      period       = 60
      evaluations  = 3
      threshold    = 0
      comparison   = "GreaterThanThreshold"
      missing_data = "notBreaching"
    }
    # UnHealthyHostCount stays at zero when no task is registered at all, so the absence of
    # healthy targets is alarmed separately and missing data counts as an outage.
    api-no-healthy-targets = {
      description  = "No API task is passing load balancer health checks"
      namespace    = "AWS/ApplicationELB"
      metric       = "HealthyHostCount"
      statistic    = "Minimum"
      extended     = null
      dimensions   = local.alb_dimensions
      period       = 60
      evaluations  = 3
      threshold    = 1
      comparison   = "LessThanThreshold"
      missing_data = "breaching"
    }
    api-cpu = {
      description  = "API service CPU above 85% for 15 minutes despite autoscaling"
      namespace    = "AWS/ECS"
      metric       = "CPUUtilization"
      statistic    = "Average"
      extended     = null
      dimensions   = local.ecs_dimensions
      period       = 300
      evaluations  = 3
      threshold    = 85
      comparison   = "GreaterThanThreshold"
      missing_data = "notBreaching"
    }
    api-memory = {
      description  = "API service memory above 85% for 15 minutes"
      namespace    = "AWS/ECS"
      metric       = "MemoryUtilization"
      statistic    = "Average"
      extended     = null
      dimensions   = local.ecs_dimensions
      period       = 300
      evaluations  = 3
      threshold    = 85
      comparison   = "GreaterThanThreshold"
      missing_data = "notBreaching"
    }
    database-cpu = {
      description  = "Database CPU above 80% for 15 minutes"
      namespace    = "AWS/RDS"
      metric       = "CPUUtilization"
      statistic    = "Average"
      extended     = null
      dimensions   = local.rds_dimensions
      period       = 300
      evaluations  = 3
      threshold    = 80
      comparison   = "GreaterThanThreshold"
      missing_data = "notBreaching"
    }
    database-free-storage = {
      description  = "Database free storage below ${floor(var.db_free_storage_threshold_bytes / 1073741824)} GiB"
      namespace    = "AWS/RDS"
      metric       = "FreeStorageSpace"
      statistic    = "Minimum"
      extended     = null
      dimensions   = local.rds_dimensions
      period       = 300
      evaluations  = 1
      threshold    = var.db_free_storage_threshold_bytes
      comparison   = "LessThanThreshold"
      missing_data = "notBreaching"
    }
  }
}

resource "aws_cloudwatch_metric_alarm" "this" {
  for_each = local.alarms

  alarm_name          = "${var.name}-${each.key}"
  alarm_description   = each.value.description
  namespace           = each.value.namespace
  metric_name         = each.value.metric
  statistic           = each.value.statistic
  extended_statistic  = each.value.extended
  dimensions          = each.value.dimensions
  period              = each.value.period
  evaluation_periods  = each.value.evaluations
  threshold           = each.value.threshold
  comparison_operator = each.value.comparison
  treat_missing_data  = each.value.missing_data
  alarm_actions       = [aws_sns_topic.alarms.arn]
  ok_actions          = [aws_sns_topic.alarms.arn]
}

resource "aws_cloudwatch_dashboard" "this" {
  dashboard_name = var.name

  dashboard_body = jsonencode({
    widgets = [
      {
        type = "metric", x = 0, y = 0, width = 12, height = 6
        properties = {
          title  = "API requests and errors"
          region = data.aws_region.current.region
          stat   = "Sum"
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "RequestCount", "LoadBalancer", var.load_balancer_arn_suffix, "TargetGroup", var.target_group_arn_suffix],
            [".", "HTTPCode_Target_4XX_Count", ".", ".", ".", "."],
            [".", "HTTPCode_Target_5XX_Count", ".", ".", ".", "."],
          ]
        }
      },
      {
        type = "metric", x = 12, y = 0, width = 12, height = 6
        properties = {
          title  = "API latency"
          region = data.aws_region.current.region
          period = 60
          metrics = [
            ["AWS/ApplicationELB", "TargetResponseTime", "LoadBalancer", var.load_balancer_arn_suffix, "TargetGroup", var.target_group_arn_suffix, { stat = "p50" }],
            ["...", { stat = "p95" }],
            ["...", { stat = "p99" }],
          ]
        }
      },
      {
        type = "metric", x = 0, y = 6, width = 12, height = 6
        properties = {
          title  = "API service utilisation"
          region = data.aws_region.current.region
          stat   = "Average"
          period = 60
          metrics = [
            ["AWS/ECS", "CPUUtilization", "ClusterName", var.ecs_cluster_name, "ServiceName", var.ecs_service_name],
            [".", "MemoryUtilization", ".", ".", ".", "."],
          ]
        }
      },
      {
        type = "metric", x = 12, y = 6, width = 12, height = 6
        properties = {
          title  = "Database"
          region = data.aws_region.current.region
          stat   = "Average"
          period = 60
          metrics = [
            ["AWS/RDS", "CPUUtilization", "DBInstanceIdentifier", var.db_instance_identifier],
            [".", "DatabaseConnections", ".", "."],
          ]
        }
      },
      {
        type = "log", x = 0, y = 12, width = 24, height = 6
        properties = {
          title  = "Recent API errors"
          region = data.aws_region.current.region
          query  = "SOURCE '${var.api_log_group_name}' | fields @timestamp, `log.level`, message, requestId | filter `log.level` = 'ERROR' | sort @timestamp desc | limit 50"
          view   = "table"
        }
      },
    ]
  })
}
