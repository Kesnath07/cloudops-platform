output "vpc_id" {
  description = "ID of the VPC."
  value       = aws_vpc.this.id
}

output "public_subnet_ids" {
  description = "Subnets for internet-facing load balancers."
  value       = aws_subnet.public[*].id
}

output "app_subnet_ids" {
  description = "Private subnets for application tasks (egress through NAT)."
  value       = aws_subnet.app[*].id
}

output "data_subnet_ids" {
  description = "Isolated subnets for the database (no internet route)."
  value       = aws_subnet.data[*].id
}

output "availability_zones" {
  description = "Availability zones in use."
  value       = local.azs
}
