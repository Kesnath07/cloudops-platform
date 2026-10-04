terraform {
  # 1.11+ is required for write-only arguments, which keep generated secrets out of state.
  required_version = ">= 1.11.0"

  required_providers {
    aws = {
      source  = "hashicorp/aws"
      version = "~> 6.67"
    }
    random = {
      source  = "hashicorp/random"
      version = "~> 3.9"
    }
  }

  # Partial configuration: bucket comes from -backend-config at init time, the state key and
  # region from environments/<env>/backend.hcl. Locking uses S3 conditional writes (no DynamoDB).
  backend "s3" {}
}
