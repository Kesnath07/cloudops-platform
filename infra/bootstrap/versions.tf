terraform {
  required_version = ">= 1.11.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.67"
    }
  }

  # Bootstrap creates the remote state bucket, so its own state starts local. After the first
  # apply it can be migrated into the bucket it created (see docs/DEPLOYMENT.md).
}

provider "aws" {
  region = var.aws_region

  default_tags {
    tags = {
      Project   = "cloudops-platform"
      Component = "bootstrap"
      ManagedBy = "terraform"
    }
  }
}
