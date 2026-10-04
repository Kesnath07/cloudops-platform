output "url" {
  description = "Public URL of the platform."
  value       = module.edge.url
}

output "site_bucket_name" {
  description = "S3 bucket the pipeline uploads the built console to."
  value       = module.edge.site_bucket_name
}

output "cloudfront_distribution_id" {
  description = "Distribution to invalidate after uploading the console."
  value       = module.edge.distribution_id
}

output "ecs_cluster_name" {
  description = "ECS cluster running the API."
  value       = module.api.cluster_name
}

output "ecs_service_name" {
  description = "ECS service running the API."
  value       = module.api.service_name
}

output "api_log_group_name" {
  description = "CloudWatch log group of the API."
  value       = module.api.log_group_name
}

output "deployed_image" {
  description = "Image reference currently configured for the API."
  value       = "${data.aws_ecr_repository.api.repository_url}:${var.api_image_tag}"
}

output "database_endpoint" {
  description = "Private database endpoint (reachable only from the API tasks)."
  value       = "${module.database.address}:${module.database.port}"
}

output "dashboard_name" {
  description = "CloudWatch dashboard for the environment."
  value       = module.observability.dashboard_name
}

output "alarm_topic_arn" {
  description = "SNS topic for CloudWatch alarms."
  value       = module.observability.alarm_topic_arn
}

output "incident_topic_arn" {
  description = "SNS topic for incident notifications."
  value       = module.api.incident_topic_arn
}
