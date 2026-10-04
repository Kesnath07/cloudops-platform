# Operations

How to observe CloudOps Platform in AWS and what to do when something goes wrong. Resource names
below use `<env>` for `dev` or `prod`.

- [Health checks](#health-checks)
- [Logs](#logs)
- [Metrics, alarms and dashboard](#metrics-alarms-and-dashboard)
- [Notifications](#notifications)
- [Runbooks](#runbooks)
- [Routine tasks](#routine-tasks)

---

## Health checks

| Endpoint | Checks | Used by |
|---|---|---|
| `/actuator/health/liveness` | The application is running | Container `HEALTHCHECK`, ECS task health check |
| `/actuator/health/readiness` | Ready for traffic, including the database connection | ALB target group (every 15 s; 2 healthy / 3 unhealthy) |
| `/actuator/health` | Aggregate status only | Manual checks |
| `/api/v1/platform/info` | Version and release (commit SHA), through CloudFront | Deployment verification, manual checks |

Only `/api/*` is routed through CloudFront, so the actuator endpoints are reachable from inside the
VPC (the load balancer) but not from the internet.

```bash
curl -s https://<platform-url>/api/v1/platform/info
# {"service":"cloudops-api","version":"1.0.0","release":"<commit sha>"}
```

## Logs

| Log group | Contents | Retention |
|---|---|---|
| `/ecs/cloudops-<env>/api` | API logs in Elastic Common Schema JSON | 14 days dev / 90 days prod |
| `/aws/rds/instance/cloudops-<env>-postgres/postgresql` | Slow statements (> 1 s), connections, errors | AWS default |
| `/vpc/cloudops-<env>/flow-logs` | Rejected network flows | 14 / 90 days |
| S3 `cloudops-<env>-alb-logs-<account>` | ALB access logs | 14 / 90 days |

Every API log line includes `requestId`, which is also returned to clients in the `X-Request-Id`
header and quoted in 500 responses. Useful CloudWatch Logs Insights queries on the API log group:

```sql
-- Everything for one request
fields @timestamp, `log.level`, `log.logger`, message
| filter requestId = 'paste-request-id'
| sort @timestamp asc

-- Errors in the last hour, grouped
fields message
| filter `log.level` = 'ERROR'
| stats count() as occurrences by message
| sort occurrences desc

-- Incident lifecycle events
fields @timestamp, message
| filter message like /Incident .* (opened|moved)/
| sort @timestamp desc
```

## Metrics, alarms and dashboard

The CloudWatch dashboard **`cloudops-<env>`** shows API request and error counts, latency (p50, p95,
p99), ECS CPU and memory, database CPU and connections, and the latest API errors from the logs.

Alarms (module `observability`) notify the `cloudops-<env>-alarms` SNS topic on both alarm and
recovery:

| Alarm | Condition | First things to check |
|---|---|---|
| `cloudops-<env>-api-5xx` | > 10 target 5xx responses in 5 minutes | API error logs; recent deployment; database health |
| `cloudops-<env>-api-latency-p95` | p95 > 1.5 s for 15 minutes | Database CPU and slow-query log; ECS CPU; autoscaling at maximum |
| `cloudops-<env>-api-unhealthy-targets` | Any unhealthy target for 3 minutes | ECS stopped-task reasons; readiness failures (database connectivity) |
| `cloudops-<env>-api-cpu` | Service CPU > 85 % for 15 minutes | Whether autoscaling reached `api_max_count` |
| `cloudops-<env>-api-memory` | Service memory > 85 % for 15 minutes | Heap usage trend; task memory size |
| `cloudops-<env>-database-cpu` | RDS CPU > 80 % for 15 minutes | Performance Insights top SQL |
| `cloudops-<env>-database-free-storage` | Free storage < 2 GiB | Storage autoscaling ceiling (`db_max_allocated_storage`) |

Missing data is treated as not breaching, so an idle environment does not alarm. Subscribe people
with the `ALARM_EMAILS` environment variable; each address must confirm the subscription email.

Additional sources: Container Insights (per-task metrics), RDS Performance Insights (query load) and
Enhanced Monitoring (OS metrics), and WAF metrics per rule in prod.

## Notifications

Incident notifications go to `cloudops-<env>-incidents`. Messages carry the attributes `severity`
(`SEV1`–`SEV4`) and `eventType` (`INCIDENT_OPENED`, `INCIDENT_MITIGATED`, `INCIDENT_OPEN` for a
reopened incident, `INCIDENT_RESOLVED`, `INCIDENT_ESCALATED`), so an SNS subscription filter policy
can, for example, send only SEV1 openings to a pager integration:

```json
{ "severity": ["SEV1"], "eventType": ["INCIDENT_OPENED"] }
```

If notifications stop, search the API log for `Failed to publish` (SNS errors are logged, never
returned to users) and check that subscribers confirmed their subscription.

## Runbooks

### A deployment fails

**Terraform apply fails or times out on the ECS service.** The new tasks never became healthy and
the ECS circuit breaker rolled the service back to the previous task definition, so the previous
release keeps serving. Find the cause:

```bash
aws ecs describe-services --cluster cloudops-<env> --services api \
  --query 'services[0].events[:10].message'
aws ecs list-tasks --cluster cloudops-<env> --service-name api --desired-status STOPPED
aws ecs describe-tasks --cluster cloudops-<env> --tasks <task-arn> \
  --query 'tasks[0].{reason:stoppedReason,containers:containers[].reason}'
```

Typical causes: a Flyway migration failure or schema validation error (visible in the API log at
startup), a secret that cannot be read (`ResourceInitializationError`), or readiness failing because
the database is unreachable.

**Verification fails after a successful apply.** The script output names the failed check: an old
release still reported (tasks still rolling or CloudFront caching), a non-200 console response (S3
upload or invalidation), or a protected endpoint not returning 401 (routing). Re-run the job once
before investigating, because CloudFront propagation can lag.

### Roll back to a previous release

Images in ECR are immutable and kept (50 most recent). Re-run the **CD** workflow for an earlier
commit (Actions → CD → select the earlier run → *Re-run all jobs*), or apply manually:

```bash
terraform plan -var-file=environments/<env>/terraform.tfvars -var="api_image_tag=<previous sha>" -out=rollback.tfplan
terraform apply rollback.tfplan
```

Flyway migrations are forward-only. If the release being rolled back added a migration, confirm that
the previous release still works with the newer schema (additive migrations do) before rolling back.

### The API cannot reach the database

1. Readiness is `DOWN` and the unhealthy-targets alarm fires.
2. Check the RDS instance status and events in the console (maintenance, storage full, failover).
3. Check that the database security group still allows the app security group (Terraform would
   reveal drift with `terraform plan`).
4. Recently rotated the password? Confirm the tasks restarted after the rotation (task definition
   revision changed) — see [DEPLOYMENT.md](DEPLOYMENT.md#10-secret-rotation).

### Restore the database

Use point-in-time recovery to a new instance, then switch the application over:

```bash
aws rds restore-db-instance-to-point-in-time \
  --source-db-instance-identifier cloudops-<env>-postgres \
  --target-db-instance-identifier cloudops-<env>-postgres-restored \
  --restore-time 2026-01-01T12:00:00Z \
  --db-subnet-group-name cloudops-<env>-database \
  --vpc-security-group-ids <database security group id>
```

Validate the restored data, then either import the restored instance into Terraform in place of the
original or copy the needed data back. Plan for this to take a while; it is a deliberate, manual
procedure.

### Someone is locked out

- **Forgot password:** there is no self-service reset. An administrator cannot set passwords either;
  the account owner can register a new account, or an operator with database access can update the
  hash. Self-service reset (email via SES) is a possible extension.
- **Lost the only administrator:** set `BOOTSTRAP_ADMIN_EMAIL` to an address that has not registered
  yet, deploy, and register with it.

## Routine tasks

| Task | How |
|---|---|
| Scale the API | Adjust `api_min_count` / `api_max_count` (and `api_cpu` / `api_memory`) in the environment's tfvars and deploy |
| Resize the database | Change `db_instance_class`; RDS applies it in the next maintenance window (`sun:03:30–04:30` UTC) unless applied immediately from the console |
| Rotate secrets | Increment `db_password_version` or `jwt_secret_version` and deploy |
| Review dependency updates | Dependabot opens weekly pull requests; CI validates each one |
| Check for infrastructure drift | `terraform plan` with the deployed image tag (see [DEPLOYMENT.md](DEPLOYMENT.md#6-running-terraform-manually)); "No changes" means no drift |
| Reduce dev cost | `terraform destroy` the dev environment when not in use |
