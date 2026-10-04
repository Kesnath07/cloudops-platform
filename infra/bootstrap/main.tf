data "aws_caller_identity" "current" {}
data "aws_partition" "current" {}

locals {
  account_id   = data.aws_caller_identity.current.account_id
  partition    = data.aws_partition.current.partition
  github_host  = "token.actions.githubusercontent.com"
  oidc_arn     = var.create_github_oidc_provider ? aws_iam_openid_connect_provider.github[0].arn : data.aws_iam_openid_connect_provider.github[0].arn
  managed_role = "arn:${local.partition}:iam::${local.account_id}:role/cloudops-*"
}

# --- Foundation key -----------------------------------------------------------------------------

resource "aws_kms_key" "foundation" {
  description             = "Encrypts Terraform state and container images for cloudops-platform"
  enable_key_rotation     = true
  deletion_window_in_days = 30
}

resource "aws_kms_alias" "foundation" {
  name          = "alias/cloudops-foundation"
  target_key_id = aws_kms_key.foundation.key_id
}

# --- Terraform state ----------------------------------------------------------------------------

resource "aws_s3_bucket" "state" {
  #checkov:skip=CKV_AWS_18:State access is audited through CloudTrail data events if required; no separate log bucket.
  #checkov:skip=CKV_AWS_144:Versioning protects state; cross-region replication is out of scope for this project.
  #checkov:skip=CKV2_AWS_62:No consumers subscribe to state object events.
  bucket = var.state_bucket_name

  lifecycle {
    prevent_destroy = true
  }
}

resource "aws_s3_bucket_ownership_controls" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    object_ownership = "BucketOwnerEnforced"
  }
}

resource "aws_s3_bucket_public_access_block" "state" {
  bucket                  = aws_s3_bucket.state.id
  block_public_acls       = true
  block_public_policy     = true
  ignore_public_acls      = true
  restrict_public_buckets = true
}

resource "aws_s3_bucket_versioning" "state" {
  bucket = aws_s3_bucket.state.id

  versioning_configuration {
    status = "Enabled"
  }
}

resource "aws_s3_bucket_server_side_encryption_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    bucket_key_enabled = true

    apply_server_side_encryption_by_default {
      sse_algorithm     = "aws:kms"
      kms_master_key_id = aws_kms_key.foundation.arn
    }
  }
}

resource "aws_s3_bucket_lifecycle_configuration" "state" {
  bucket = aws_s3_bucket.state.id

  rule {
    id     = "expire-old-state-versions"
    status = "Enabled"

    filter {}

    noncurrent_version_expiration {
      noncurrent_days           = 90
      newer_noncurrent_versions = 20
    }

    abort_incomplete_multipart_upload {
      days_after_initiation = 1
    }
  }
}

data "aws_iam_policy_document" "state" {
  statement {
    sid       = "DenyInsecureTransport"
    effect    = "Deny"
    actions   = ["s3:*"]
    resources = [aws_s3_bucket.state.arn, "${aws_s3_bucket.state.arn}/*"]

    principals {
      type        = "*"
      identifiers = ["*"]
    }

    condition {
      test     = "Bool"
      variable = "aws:SecureTransport"
      values   = ["false"]
    }
  }
}

resource "aws_s3_bucket_policy" "state" {
  bucket = aws_s3_bucket.state.id
  policy = data.aws_iam_policy_document.state.json

  depends_on = [aws_s3_bucket_public_access_block.state]
}

# --- Container registry ------------------------------------------------------------------------

module "container_registry" {
  source = "../modules/container-registry"

  repository_name = var.ecr_repository_name
  kms_key_arn     = aws_kms_key.foundation.arn
}

# --- GitHub Actions OIDC -----------------------------------------------------------------------

resource "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 1 : 0

  url            = "https://${local.github_host}"
  client_id_list = ["sts.amazonaws.com"]
}

data "aws_iam_openid_connect_provider" "github" {
  count = var.create_github_oidc_provider ? 0 : 1

  url = "https://${local.github_host}"
}

# Image publishing: only workflow runs on the main branch may push, and only to this repository.
data "aws_iam_policy_document" "publish_assume" {
  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_host}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_host}:sub"
      values   = ["repo:${var.github_repository}:ref:refs/heads/main"]
    }
  }
}

resource "aws_iam_role" "publish" {
  name                 = "cloudops-ci-publish"
  description          = "GitHub Actions: push API images from main"
  assume_role_policy   = data.aws_iam_policy_document.publish_assume.json
  max_session_duration = 3600
}

data "aws_iam_policy_document" "publish" {
  statement {
    sid       = "RegistryLogin"
    actions   = ["ecr:GetAuthorizationToken"]
    resources = ["*"]
  }

  statement {
    sid = "PushApiImages"
    actions = [
      "ecr:BatchCheckLayerAvailability",
      "ecr:BatchGetImage",
      "ecr:CompleteLayerUpload",
      "ecr:DescribeImages",
      "ecr:DescribeImageScanFindings",
      "ecr:InitiateLayerUpload",
      "ecr:PutImage",
      "ecr:UploadLayerPart",
    ]
    resources = [module.container_registry.repository_arn]
  }

  statement {
    sid       = "EncryptImageLayers"
    actions   = ["kms:GenerateDataKey", "kms:Decrypt"]
    resources = [aws_kms_key.foundation.arn]
  }
}

resource "aws_iam_role_policy" "publish" {
  name   = "push-api-images"
  role   = aws_iam_role.publish.id
  policy = data.aws_iam_policy_document.publish.json
}

# Deployment: one role per environment, assumable only by jobs bound to the matching GitHub
# environment, so production protection rules (required reviewers) gate access to prod.
data "aws_iam_policy_document" "deploy_assume" {
  for_each = toset(var.environments)

  statement {
    actions = ["sts:AssumeRoleWithWebIdentity"]

    principals {
      type        = "Federated"
      identifiers = [local.oidc_arn]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_host}:aud"
      values   = ["sts.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "${local.github_host}:sub"
      values   = ["repo:${var.github_repository}:environment:${each.value}"]
    }
  }
}

resource "aws_iam_role" "deploy" {
  for_each = toset(var.environments)

  name                 = "cloudops-deploy-${each.value}"
  description          = "GitHub Actions: Terraform apply and release for ${each.value}"
  assume_role_policy   = data.aws_iam_policy_document.deploy_assume[each.value].json
  max_session_duration = 7200
}

# Terraform manages many service types, so the deploy role uses PowerUserAccess (everything
# except IAM and Organizations) and receives IAM rights only for roles and policies whose names
# start with cloudops-. It cannot modify its own role or create unrelated principals.
resource "aws_iam_role_policy_attachment" "deploy_power_user" {
  for_each = aws_iam_role.deploy

  role       = each.value.name
  policy_arn = "arn:${local.partition}:iam::aws:policy/PowerUserAccess"
}

data "aws_iam_policy_document" "deploy_iam" {
  statement {
    sid = "ManageProjectRoles"
    actions = [
      "iam:AttachRolePolicy",
      "iam:CreateRole",
      "iam:DeleteRole",
      "iam:DeleteRolePolicy",
      "iam:DetachRolePolicy",
      "iam:GetRole",
      "iam:GetRolePolicy",
      "iam:ListAttachedRolePolicies",
      "iam:ListInstanceProfilesForRole",
      "iam:ListRolePolicies",
      "iam:PassRole",
      "iam:PutRolePolicy",
      "iam:TagRole",
      "iam:UntagRole",
      "iam:UpdateAssumeRolePolicy",
      "iam:UpdateRole",
    ]
    resources = [local.managed_role]
  }

  statement {
    sid       = "ProtectPipelineRoles"
    effect    = "Deny"
    actions   = ["iam:*"]
    resources = ["arn:${local.partition}:iam::${local.account_id}:role/cloudops-deploy-*", aws_iam_role.publish.arn]
  }

  statement {
    sid       = "CreateServiceLinkedRoles"
    actions   = ["iam:CreateServiceLinkedRole"]
    resources = ["arn:${local.partition}:iam::${local.account_id}:role/aws-service-role/*"]
  }

  statement {
    sid       = "ReadOidcProvider"
    actions   = ["iam:GetOpenIDConnectProvider"]
    resources = [local.oidc_arn]
  }
}

resource "aws_iam_role_policy" "deploy_iam" {
  for_each = aws_iam_role.deploy

  name   = "manage-project-iam"
  role   = each.value.id
  policy = data.aws_iam_policy_document.deploy_iam.json
}
