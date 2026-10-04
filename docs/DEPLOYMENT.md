# Deployment

How to provision CloudOps Platform on AWS, wire up GitHub Actions, and operate the Terraform
configuration by hand when needed.

- [1. Prerequisites](#1-prerequisites)
- [2. Bootstrap the AWS account](#2-bootstrap-the-aws-account)
- [3. Configure GitHub](#3-configure-github)
- [4. First deployment](#4-first-deployment)
- [5. What the pipeline does](#5-what-the-pipeline-does)
- [6. Running Terraform manually](#6-running-terraform-manually)
- [7. Environment configuration](#7-environment-configuration)
- [8. Custom domain](#8-custom-domain)
- [9. State management](#9-state-management)
- [10. Secret rotation](#10-secret-rotation)
- [11. Tearing down](#11-tearing-down)
- [12. Cost considerations](#12-cost-considerations)

---

## 1. Prerequisites

- An AWS account and an identity with administrator access, used **only** for the one-time bootstrap.
- Terraform ≥ 1.11 (CI uses 1.16.4) and the AWS CLI v2.
- A GitHub repository containing this code, with GitHub Actions enabled.
- Optional: a Route 53 public hosted zone if you want a custom domain.

Both environments live in the same account and region in this setup (see
[future improvements](../README.md#future-improvements) for multi-account). The region must be the
same in the bootstrap stack, `infra/environments/*/terraform.tfvars` (default `eu-west-1`) and the
`AWS_REGION` GitHub variable, because the environment stack looks up the ECR repository there.

## 2. Bootstrap the AWS account

`infra/bootstrap` creates what the pipeline needs before any environment exists:

| Resource | Purpose |
|---|---|
| S3 bucket (versioned, KMS-encrypted, public access blocked, TLS-only policy, `prevent_destroy`) | Terraform state for all environments |
| ECR repository `cloudops-api` (immutable tags, scan on push, KMS, lifecycle keeps 50 images) | API images shared by dev and prod |
| KMS key `alias/cloudops-foundation` | Encrypts state and images |
| GitHub OIDC identity provider | Lets workflows exchange GitHub tokens for AWS credentials |
| IAM role `cloudops-ci-publish` | Assumable only from `main`; may push to the ECR repository only |
| IAM roles `cloudops-deploy-dev`, `cloudops-deploy-prod` | Assumable only by jobs running in the matching GitHub environment |

```bash
cd infra/bootstrap
cp terraform.tfvars.example terraform.tfvars   # git-ignored
# set aws_region, github_repository ("owner/name") and a globally unique state_bucket_name
terraform init
terraform plan -out=bootstrap.tfplan
terraform apply bootstrap.tfplan
terraform output
```

If the account already has a GitHub OIDC provider, set `create_github_oidc_provider = false`.

**Moving bootstrap state into the bucket (recommended).** The bootstrap stack starts with local state
because the bucket does not exist yet. Afterwards, add a backend block to `infra/bootstrap/versions.tf`:

```hcl
backend "s3" {
  bucket       = "<state_bucket_name>"
  key          = "cloudops-platform/bootstrap/terraform.tfstate"
  region       = "<aws_region>"
  encrypt      = true
  use_lockfile = true
}
```

then run `terraform init -migrate-state` and delete the local `terraform.tfstate*` files.

**About the deploy role's permissions.** Terraform manages many AWS services, so each deploy role has
the AWS-managed `PowerUserAccess` policy plus IAM permissions restricted to roles named
`cloudops-*`. An explicit deny stops it from modifying the pipeline roles themselves. Isolation
between environments comes from the trust policies; narrowing the permission set further is listed as
a future improvement.

## 3. Configure GitHub

**Repository variables** (Settings → Secrets and variables → Actions → Variables):

| Variable | Value |
|---|---|
| `AWS_REGION` | Region used for bootstrap and the environments, e.g. `eu-west-1` |
| `AWS_PUBLISH_ROLE_ARN` | `publish_role_arn` output |
| `TF_STATE_BUCKET` | `state_bucket_name` output |

**Environments** (Settings → Environments): create `dev` and `prod`.

| Environment variable | dev | prod | Notes |
|---|---|---|---|
| `AWS_DEPLOY_ROLE_ARN` | required | required | From `deploy_role_arns` output |
| `BOOTSTRAP_ADMIN_EMAIL` | optional | optional | Email that becomes the first administrator |
| `ALARM_EMAILS` | optional | optional | Comma-separated; recipients must confirm the SNS subscription email |
| `INCIDENT_NOTIFICATION_EMAILS` | optional | optional | Comma-separated recipients of incident notifications |
| `DOMAIN_NAME` | optional | optional | e.g. `ops.example.com`; requires `HOSTED_ZONE_ID` |
| `HOSTED_ZONE_ID` | optional | optional | Route 53 zone containing `DOMAIN_NAME` |

Protect `prod` with **required reviewers** and restrict its deployment branches to `main`. Because
the prod deploy role trusts only the `prod` environment, those protection rules gate every use of
prod credentials.

No GitHub secrets are needed.

## 4. First deployment

Push or merge to `main` (or run the **CD** workflow manually). The first run creates each
environment from scratch, which takes roughly 20–30 minutes, mostly for RDS and the CloudFront
distribution. When it finishes, the job summary and the environment URL in GitHub show where the
platform is served. Register there with `BOOTSTRAP_ADMIN_EMAIL` to obtain the administrator role,
then create a team and invite colleagues to register; promote them on the **Users** page.

If you set alarm or incident recipients, AWS sends each a confirmation email; notifications start
after they confirm.

## 5. What the pipeline does

### Pull requests — `ci.yml`

| Job | Steps | Fails on |
|---|---|---|
| API | `./mvnw verify`: compile with `-Werror`, 54 unit/slice tests, 12 integration tests on embedded PostgreSQL, JaCoCo coverage gate (80 %), SpotBugs | compile warnings, test failures, coverage, SpotBugs findings |
| Web | `npm ci`, ESLint (zero warnings), `tsc`, Vitest with coverage, production build | any failure |
| Dependencies | Trivy filesystem scan of `pom.xml` and `package-lock.json`; GitHub dependency review on PRs | fixable HIGH/CRITICAL vulnerabilities; newly added high-severity dependencies |
| Images | Build the API image for `linux/arm64` (QEMU for the runtime stage only) and the web image; Trivy scan of both | fixable HIGH/CRITICAL vulnerabilities |
| Infrastructure | `terraform fmt -check`, `validate` (both roots), `terraform test`, TFLint with the AWS ruleset, Checkov | any finding |
| Workflow lint | actionlint (includes ShellCheck of `run` scripts) | any finding |

`codeql.yml` adds CodeQL analysis (`security-and-quality` queries) for Java and TypeScript.

### Releases — `cd.yml` on `main`

1. **CI** — calls `ci.yml` with `publish-artifacts: true`, which uploads the scanned image archive
   and the console bundle.
2. **Publish image** — assumes `cloudops-ci-publish` through OIDC, loads the archive and pushes it
   to ECR as `cloudops-api:<commit-sha>`. Re-running for the same commit skips the push because tags
   are immutable.
3. **Deploy dev** — `deploy.yml` with `environment: dev`.
4. **Deploy prod** — `deploy.yml` with `environment: prod`, after dev succeeds and the prod
   reviewers approve.

Runs are serialised (`concurrency: cd-main`) so two releases never apply Terraform at the same time.

### Per environment — `deploy.yml`

1. Assume `cloudops-deploy-<env>` through OIDC.
2. `terraform init` against `infra/environments/<env>/backend.hcl` and the state bucket (composite
   action `.github/actions/terraform-init`).
3. Write `pipeline.auto.tfvars.json` from the environment's GitHub variables and the image tag.
4. `terraform plan -out=tfplan` (summary in the job summary), then `terraform apply tfplan`. The ECS
   service waits for new tasks to be healthy; the circuit breaker rolls back if they are not.
5. Upload the console to S3: fingerprinted assets with a one-year immutable cache, everything else
   with `no-cache`.
6. Invalidate CloudFront and wait for the invalidation to complete.
7. Run `scripts/verify-deployment.sh <url> <sha>`, which waits until `/api/v1/platform/info` reports
   the new commit, then checks that the console and a client-side route return 200, that a protected
   API endpoint returns 401 to anonymous callers, and that HSTS and CSP headers are present.

## 6. Running Terraform manually

Useful for previewing changes or recovering from a failed pipeline. Use credentials for the target
account (for example `aws sso login`).

```bash
cd infra
terraform init \
  -backend-config=environments/dev/backend.hcl \
  -backend-config="bucket=<state bucket>" \
  -backend-config="region=<region>"

terraform validate

# api_image_tag must name an image that exists in ECR; to keep the running version, read it from state:
TAG=$(terraform output -raw deployed_image | sed 's/.*://')

terraform plan -var-file=environments/dev/terraform.tfvars -var="api_image_tag=$TAG" -out=dev.tfplan
terraform apply dev.tfplan
```

Re-run `init` with `-reconfigure` and the other environment's backend file before switching
environments. Offline checks need no credentials:

```bash
terraform init -backend=false
terraform fmt -check -recursive && terraform validate && terraform test
tflint --init && tflint --recursive
checkov -d . --framework terraform
```

## 7. Environment configuration

Committed values live in `infra/environments/<env>/terraform.tfvars`; account-specific values come
from the pipeline. The most relevant variables (see `infra/variables.tf` for all of them):

| Variable | Meaning |
|---|---|
| `environment`, `aws_region` | Name prefix (`cloudops-<env>`) and region |
| `api_image_tag` | Commit SHA of the image to run (validated as 7–40 hex characters) |
| `vpc_cidr`, `az_count`, `single_nat_gateway` | Network size, zone count, NAT redundancy |
| `api_cpu`, `api_memory`, `api_min_count`, `api_max_count` | Task size and autoscaling bounds |
| `db_instance_class`, `db_multi_az`, `db_backup_retention_days`, `db_allocated_storage`, `db_max_allocated_storage` | Database sizing and resilience |
| `deletion_protection` | Protects RDS, the ALB and bucket contents; also forces a final DB snapshot |
| `enable_waf`, `cloudfront_price_class` | Edge protection and distribution reach |
| `domain_name`, `hosted_zone_id` | Optional custom domain (set together) |
| `log_retention_days`, `alarm_emails`, `incident_notification_emails` | Operations |
| `bootstrap_admin_email` | First administrator |
| `db_password_version`, `jwt_secret_version` | Increment to rotate the generated secrets |

## 8. Custom domain

Set `DOMAIN_NAME` and `HOSTED_ZONE_ID` on the GitHub environment (the zone must already exist and be
delegated). On the next deployment Terraform:

1. requests and DNS-validates an ACM certificate for the domain in `us-east-1` (CloudFront) and one for
   `origin.<domain>` in the environment's region (ALB);
2. switches the ALB to an HTTPS listener on 443 with a TLS 1.2+/1.3 policy and points
   `origin.<domain>` at it;
3. sets CloudFront's API origin to `origin.<domain>` over HTTPS only, adds the domain as an alias
   with `TLSv1.2_2021` as the minimum viewer protocol, and creates A/AAAA alias records.

Without a domain the platform is reachable at `https://<id>.cloudfront.net`, and CloudFront reaches
the ALB over HTTP restricted by the security group and origin secret.

## 9. State management

- One state file per environment: `cloudops-platform/<env>/terraform.tfstate` in the bootstrap bucket.
- Locking uses S3 conditional writes (`use_lockfile = true`); no DynamoDB table.
- The bucket is versioned (old versions expire after 90 days, the latest 20 are kept), KMS-encrypted
  and TLS-only. To recover from a bad state write, restore the previous object version.
- Never commit state or plan files; `.gitignore` excludes them.

## 10. Secret rotation

ECS injects secrets only when a task starts. To make rotation safe, the task definition carries a
`CLOUDOPS_SECRETS_REVISION` value derived from both version variables: changing either one creates a
new task definition revision, so the same `terraform apply` rolls every task onto the new secret.

| Secret | How to rotate | Effect |
|---|---|---|
| Database master password | Increment `db_password_version` and deploy | RDS, the secret and the tasks are updated in one apply. Between the password change and the task rollover, old tasks keep their open connections but cannot open new ones, so rotate during a quiet period |
| JWT signing key | Increment `jwt_secret_version` and deploy | All existing access tokens become invalid; users sign in again |
| Origin secret | `terraform apply -replace=random_password.origin_verify` | CloudFront and the ALB rule are updated in the same apply; expect brief 403s while the CloudFront change propagates |

## 11. Tearing down

Development:

```bash
cd infra   # initialised for dev as above
terraform destroy -var-file=environments/dev/terraform.tfvars -var="api_image_tag=$TAG"
```

Production has `deletion_protection = true`: RDS and the ALB refuse deletion and the buckets are not
force-emptied. To destroy prod deliberately, set `deletion_protection = false`, apply, then destroy;
RDS will take a final snapshot (`cloudops-prod-postgres-final`) that you must delete separately if
you no longer need it. Secrets are scheduled for deletion with a 7-day recovery window and KMS keys
with a 30-day waiting period.

The bootstrap stack outlives the environments. Its state bucket has `prevent_destroy`; remove that
lifecycle block (and empty the bucket) only when decommissioning everything.

## 12. Cost considerations

The resources that bill continuously are the NAT gateway(s), the ALB, the RDS instance, Fargate
tasks, the KMS keys, Secrets Manager secrets, CloudWatch logs/metrics and, in prod, WAF. In dev the
NAT gateway and ALB dominate. Check current prices for your region with the AWS Pricing Calculator,
and destroy dev when idle.
