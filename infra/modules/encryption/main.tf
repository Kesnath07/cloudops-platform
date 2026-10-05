data "aws_caller_identity" "current" {}
data "aws_region" "current" {}
data "aws_partition" "current" {}

locals {
  account_id = data.aws_caller_identity.current.account_id
  region     = data.aws_region.current.region
  partition  = data.aws_partition.current.partition
}

# One customer-managed key per environment encrypts logs, secrets, the database, SNS topics and
# the static site bucket. Services that act on their own behalf are granted narrowly below;
# everything else is controlled through IAM policies, enabled by the account root statement.
data "aws_iam_policy_document" "key" {
  #checkov:skip=CKV_AWS_109:In a key policy, Resource "*" refers only to the key the policy is attached to.
  #checkov:skip=CKV_AWS_111:In a key policy, Resource "*" refers only to the key the policy is attached to.
  #checkov:skip=CKV_AWS_356:In a key policy, Resource "*" refers only to the key the policy is attached to.
  statement {
    sid       = "AccountAdministration"
    actions   = ["kms:*"]
    resources = ["*"]

    principals {
      type        = "AWS"
      identifiers = ["arn:${local.partition}:iam::${local.account_id}:root"]
    }
  }

  statement {
    sid = "CloudWatchLogsEncryption"
    actions = [
      "kms:Encrypt*",
      "kms:Decrypt*",
      "kms:ReEncrypt*",
      "kms:GenerateDataKey*",
      "kms:Describe*",
    ]
    resources = ["*"]

    principals {
      type        = "Service"
      identifiers = ["logs.${local.region}.amazonaws.com"]
    }

    condition {
      test     = "ArnLike"
      variable = "kms:EncryptionContext:aws:logs:arn"
      values   = ["arn:${local.partition}:logs:${local.region}:${local.account_id}:log-group:*"]
    }
  }

  statement {
    sid       = "CloudWatchAlarmsPublishToEncryptedTopics"
    actions   = ["kms:Decrypt", "kms:GenerateDataKey*"]
    resources = ["*"]

    principals {
      type        = "Service"
      identifiers = ["cloudwatch.amazonaws.com"]
    }

    condition {
      test     = "StringEquals"
      variable = "aws:SourceAccount"
      values   = [local.account_id]
    }
  }

  # The site behaviour only allows GET/HEAD/OPTIONS, so CloudFront needs to decrypt, never encrypt.
  statement {
    sid       = "CloudFrontReadsEncryptedSiteObjects"
    actions   = ["kms:Decrypt"]
    resources = ["*"]

    principals {
      type        = "Service"
      identifiers = ["cloudfront.amazonaws.com"]
    }

    condition {
      test     = "ArnLike"
      variable = "aws:SourceArn"
      values   = ["arn:${local.partition}:cloudfront::${local.account_id}:distribution/*"]
    }
  }
}

resource "aws_kms_key" "this" {
  description             = "Encryption key for ${var.name}"
  enable_key_rotation     = true
  rotation_period_in_days = 365
  deletion_window_in_days = var.deletion_window_days
  policy                  = data.aws_iam_policy_document.key.json
}

resource "aws_kms_alias" "this" {
  name          = "alias/${var.name}"
  target_key_id = aws_kms_key.this.key_id
}
