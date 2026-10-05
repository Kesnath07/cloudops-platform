key          = "cloudops-platform/dev/terraform.tfstate"
encrypt      = true
# Encrypt state with the bootstrap foundation key; without it the backend requests SSE-S3.
kms_key_id   = "alias/cloudops-foundation"
use_lockfile = true
