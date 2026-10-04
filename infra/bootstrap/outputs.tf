output "state_bucket_name" {
  description = "Set as the TF_STATE_BUCKET repository variable in GitHub."
  value       = aws_s3_bucket.state.bucket
}

output "ecr_repository_url" {
  description = "Registry the pipeline pushes API images to."
  value       = module.container_registry.repository_url
}

output "publish_role_arn" {
  description = "Set as the AWS_PUBLISH_ROLE_ARN repository variable in GitHub."
  value       = aws_iam_role.publish.arn
}

output "deploy_role_arns" {
  description = "Set each as the AWS_DEPLOY_ROLE_ARN variable of the matching GitHub environment."
  value       = { for environment, role in aws_iam_role.deploy : environment => role.arn }
}
