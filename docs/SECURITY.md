# Security

This document lists the security controls that exist in this repository, where each one is
implemented, and the limitations that remain. It describes only what is implemented.

- [Application](#application)
- [Data and secrets](#data-and-secrets)
- [Network](#network)
- [Edge](#edge)
- [Containers](#containers)
- [Identity and access in AWS](#identity-and-access-in-aws)
- [Supply chain and scanning](#supply-chain-and-scanning)
- [Known limitations](#known-limitations)
- [Reporting a vulnerability](#reporting-a-vulnerability)

---

## Application

| Control | Implementation |
|---|---|
| Authentication | Stateless HS256 JWTs issued by `AuthenticationService`; validated by Spring Security's OAuth2 resource server with signature, expiry, not-before, issuer (`cloudops-platform`) and audience (`cloudops-api`) checks (`SecurityConfig`) |
| Token lifetime | 1 hour by default (`CLOUDOPS_ACCESS_TOKEN_TTL`); the console discards the session at expiry or on any 401 |
| Password storage | `DelegatingPasswordEncoder` (BCrypt); passwords 12–72 characters |
| Account enumeration | Unknown email and wrong password return the same 401; a dummy hash comparison keeps timing similar |
| Authorisation | Role hierarchy `ADMIN > OPERATOR > VIEWER`; `@PreAuthorize` on every mutating endpoint; administrators cannot change their own role |
| Default deny | Every endpoint requires authentication except registration, token issuance, `/api/v1/platform/info`, health probes and (outside AWS) the API docs |
| Input validation | Bean Validation on every request body and query parameter: lengths, formats (slugs, HTTPS URLs, commit SHAs, emails), enumerations, page size ≤ 100, no future deployment timestamps |
| Error handling | RFC 9457 problem responses; stack traces and exception messages are never returned (`server.error.include-*` disabled, catch-all handler returns a generic message plus request id) |
| Logging hygiene | Request records that carry passwords override `toString()`; request ids from clients are accepted only if they match `[A-Za-z0-9-]{8,64}` |
| Response headers | `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY`, `Cache-Control: no-store`, a restrictive CSP and a strict referrer policy on API responses |
| CSRF | Not applicable: no cookies or sessions; tokens travel in the `Authorization` header |
| CORS | Not enabled: the console and API share an origin |
| Concurrency | Optimistic locking prevents lost updates |
| Database integrity | Foreign keys, unique and `CHECK` constraints back the application's rules |
| Operational surface | Only `health` and `info` actuator endpoints are exposed; health details are hidden. Swagger UI is disabled in AWS |
| Bootstrap admin | No seeded credentials; the configured email becomes admin when its owner registers |

## Data and secrets

| Control | Implementation |
|---|---|
| Encryption at rest | Customer-managed KMS key per environment (`modules/encryption`, annual rotation) for RDS storage and Performance Insights, Secrets Manager, CloudWatch log groups, SNS topics and the site bucket; bootstrap KMS key for state and ECR |
| Encryption in transit | HTTPS for viewers; PostgreSQL `rds.force_ssl = 1`; the API connects with `sslmode=verify-full` against the bundled Amazon RDS CA |
| Secret generation | Database password and JWT key generated as Terraform **ephemeral** values and written through **write-only** arguments, so they appear in neither plans nor state |
| Secret delivery | ECS injects secrets from Secrets Manager into the container at start; the task definition contains only ARNs |
| Rotation | Version variables rotate secrets and roll tasks in one apply ([DEPLOYMENT.md](DEPLOYMENT.md#10-secret-rotation)) |
| Repository hygiene | `.env`, keys, state, plans and generated tfvars are git-ignored; `.env.example` contains placeholders only |
| Backups | Automated RDS backups with point-in-time recovery (3 days dev, 14 days prod); final snapshot when deleting a protected database |
| State | Versioned, encrypted, TLS-only S3 bucket with public access blocked |

## Network

| Control | Implementation |
|---|---|
| Tiered subnets | Public (ALB, NAT), private app (tasks, no public IPs), isolated data (no internet route) |
| Security group chain | CloudFront prefix list → ALB → API on 8080 → database on 5432; no other ingress anywhere (`modules/security-groups`) |
| Egress | API tasks: HTTPS (AWS APIs via NAT, S3 via gateway endpoint) and PostgreSQL to the database group only; the database group has no egress rules |
| Default security group | All rules removed |
| Flow logs | VPC flow logs to an encrypted CloudWatch log group |

## Edge

| Control | Implementation |
|---|---|
| HTTPS | Viewer requests redirected to HTTPS (static) or HTTPS-only (API); HSTS for two years including subdomains |
| Origin protection | ALB accepts only CloudFront's origin-facing ranges, and only requests carrying the origin secret header; everything else gets 403 |
| TLS policy | With a custom domain: viewer minimum `TLSv1.2_2021`, ALB `ELBSecurityPolicy-TLS13-1-2-2021-06`, CloudFront to origin over TLS 1.2 |
| Private static origin | S3 reachable only through Origin Access Control from this distribution; public access blocked; TLS-only bucket policy |
| WAF (prod) | AWS Common and Known Bad Inputs managed rule groups; per-IP rate limit on `/api/v1/auth/*` (100 requests / 5 minutes) |
| Browser headers | CSP, frame denial, nosniff and referrer policy via a CloudFront response headers policy |
| Load balancer | Invalid header fields dropped; access logs retained in S3 |

## Containers

| Control | Implementation |
|---|---|
| Minimal runtime | Multi-stage builds; JRE-only Alpine image for the API, no compilers or sources |
| Non-root | API runs as uid 10001, console as uid 101; ECS sets the user explicitly |
| Read-only filesystem | `readonlyRootFilesystem` in ECS with an ephemeral `/tmp`; `read_only` in Compose |
| Reduced privileges (Compose) | All capabilities dropped, `no-new-privileges`, ports bound to localhost |
| Patched base | `apk upgrade` during build; Trivy blocks images with fixable HIGH/CRITICAL vulnerabilities |
| Immutable releases | ECR tags are immutable commit SHAs; scan on push |

## Identity and access in AWS

| Principal | Permissions |
|---|---|
| API task role | `sns:Publish` on the incident topic; KMS data-key use for that topic |
| Task execution role | Pull images and write logs (AWS-managed policy); `GetSecretValue` on the two application secrets; KMS decrypt |
| `cloudops-ci-publish` | Push to the `cloudops-api` repository only; assumable only from `refs/heads/main` of the configured repository |
| `cloudops-deploy-<env>` | `PowerUserAccess` plus IAM limited to `cloudops-*` roles, with an explicit deny on the pipeline roles; assumable only by jobs in the matching GitHub environment |
| GitHub Actions | OIDC federation (`token.actions.githubusercontent.com`, audience `sts.amazonaws.com`); no stored AWS keys; workflow `GITHUB_TOKEN` permissions default to `contents: read` |

Trust policies for AWS service roles (ECS tasks, VPC flow logs, RDS enhanced monitoring) require `aws:SourceAccount` to match the account, preventing confused-deputy use from other accounts.

## Supply chain and scanning

| Check | Tool | Where |
|---|---|---|
| Dependency vulnerabilities | Trivy (Maven and npm lockfiles), GitHub dependency review | `ci.yml` → dependencies |
| Image vulnerabilities | Trivy on the exact archive that is later pushed | `ci.yml` → images |
| Code analysis | CodeQL `security-and-quality` for Java and TypeScript; SpotBugs for the API | `codeql.yml`, `ci.yml` → api |
| Infrastructure | Checkov, TFLint (AWS ruleset), `terraform test` security assertions | `ci.yml` → infrastructure |
| Workflow safety | actionlint + ShellCheck; inputs passed to scripts through environment variables | `ci.yml` → workflows |
| Pinning | Every third-party action pinned to a full commit SHA; provider hashes locked in `.terraform.lock.hcl`; npm lockfile | repository |
| Updates | Dependabot for Maven, npm, Docker, Terraform and GitHub Actions | `.github/dependabot.yml` |

When a security fix is not yet in a framework's managed versions, the override is pinned in
`api/pom.xml` with a comment (currently Tomcat and Jackson).

## Known limitations

These are deliberate scope decisions, not oversights:

- **Role changes and logout are not immediate.** Tokens remain valid until they expire (default 1 hour); there is no revocation list or refresh token.
- **Self-registration is open.** New accounts are read-only `VIEWER`s, but anyone who can reach the console can create one. Brute-force and registration throttling exist only where WAF is enabled (prod).
- **Notifications are best-effort.** They are sent after commit from memory; a crash in that window loses the notification (no outbox).
- **One AWS account hosts dev and prod.** Isolation relies on naming, separate IAM roles and GitHub environment protection rather than account boundaries.
- **Deploy roles are broad.** `PowerUserAccess` is scoped by trust policy, not by resource.
- **Without a custom domain**, CloudFront reaches the ALB over HTTP (restricted by security group and origin secret) and viewers see the `cloudfront.net` certificate.
- **CloudFront standard access logs are not enabled**; ALB access logs and WAF metrics provide request visibility.
- **The origin secret is stored in Terraform state** (it is also visible in the CloudFront and ALB configuration).

## Reporting a vulnerability

Please report suspected vulnerabilities privately through GitHub's **Report a vulnerability**
feature (Security → Advisories) rather than in a public issue.
