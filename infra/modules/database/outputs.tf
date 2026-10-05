output "address" {
  description = "Hostname of the database endpoint."
  value       = aws_db_instance.this.address
}

output "port" {
  description = "Port of the database endpoint."
  value       = aws_db_instance.this.port
}

output "database_name" {
  description = "Name of the application database."
  value       = aws_db_instance.this.db_name
}

output "instance_identifier" {
  description = "RDS instance identifier, used for CloudWatch metrics."
  value       = aws_db_instance.this.identifier
}

output "credentials_secret_arn" {
  description = "Secrets Manager secret holding the username and password as JSON."
  # Read from the version so consumers (the API tasks) wait until the secret has a value.
  value = aws_secretsmanager_secret_version.credentials.secret_arn
}
