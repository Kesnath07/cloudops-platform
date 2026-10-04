output "alb_security_group_id" {
  description = "Security group of the load balancer."
  value       = aws_security_group.alb.id
}

output "app_security_group_id" {
  description = "Security group of the API tasks."
  value       = aws_security_group.app.id
}

output "database_security_group_id" {
  description = "Security group of the database."
  value       = aws_security_group.database.id
}
