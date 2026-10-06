# Development

- [Prerequisites](#prerequisites)
- [Running locally](#running-locally)
- [Configuration reference](#configuration-reference)
- [Testing](#testing)
- [Quality gates](#quality-gates)
- [Working on the API](#working-on-the-api)
- [Working on the console](#working-on-the-console)
- [Working on the infrastructure](#working-on-the-infrastructure)

---

## Prerequisites

| Tool | Version | Needed for |
|---|---|---|
| JDK | 25 | API (the Maven wrapper downloads Maven 3.9 itself) |
| Node.js | 24 (≥ 22.22) | Console |
| Docker with Compose v2 | recent | Full local stack, image builds |
| Terraform | ≥ 1.11 | Infrastructure checks |
| TFLint, Checkov, Trivy | optional | Running the CI security checks locally |

## Running locally

### Option A — everything in containers

```bash
cp .env.example .env
# POSTGRES_PASSWORD: any local value
# CLOUDOPS_JWT_SECRET: openssl rand -base64 48
# CLOUDOPS_BOOTSTRAP_ADMIN_EMAIL: the address you will register with
docker compose up --build
```

- Console: http://localhost:3000
- API and Swagger UI: http://localhost:8080/swagger-ui.html
- PostgreSQL: `localhost:5432`, database and user `cloudops`

`docker compose down` stops the stack; add `-v` to delete the database volume.

### Option B — native processes

Start only PostgreSQL in Docker, then run the API on the JVM and the console on the Vite dev server
(which hot-reloads console changes; restart the API after Java changes):

```bash
docker compose up -d postgres

cd api
export SPRING_DATASOURCE_PASSWORD="<POSTGRES_PASSWORD from .env>"
export CLOUDOPS_JWT_SECRET="$(openssl rand -base64 48)"
export CLOUDOPS_BOOTSTRAP_ADMIN_EMAIL="you@example.com"
./mvnw spring-boot:run

# second terminal
cd web
npm ci
npm run dev          # http://localhost:5173, proxies /api to localhost:8080
```

Set `API_PROXY_TARGET` to point the Vite proxy at a different API address.

### First steps in the console

1. Register with the bootstrap administrator email; you become `ADMIN`.
2. **Teams** → create a team.
3. **Overview** → **Register workload**.
4. Open the workload, record a deployment, open an incident and walk it through
   `OPEN → MITIGATED → RESOLVED`; the overview health follows.
5. Register a second account and promote it on **Users** to see role-based behaviour.

With the default `log` notification channel, notifications appear in the API log as
`Incident notification [INCIDENT_OPENED] ...`.

## Configuration reference

The API reads `application.yml`, overridden by environment variables. The `aws` profile
(`application-aws.yml`, activated by ECS) switches to JSON logs, disables the API docs, selects the
SNS channel, and requires verified TLS to the database.

| Environment variable | Default | Description |
|---|---|---|
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/cloudops` | JDBC URL |
| `SPRING_DATASOURCE_USERNAME` | `cloudops` | Database user |
| `SPRING_DATASOURCE_PASSWORD` | *(none)* | Database password |
| `DB_POOL_MAX_SIZE` | `10` | HikariCP maximum pool size |
| `CLOUDOPS_JWT_SECRET` | *(none — required)* | HS256 signing key, ≥ 32 characters |
| `CLOUDOPS_ACCESS_TOKEN_TTL` | `1h` | Access token lifetime (ISO-8601 or `30m`, `2h` style) |
| `CLOUDOPS_BOOTSTRAP_ADMIN_EMAIL` | empty | Registration with this email grants `ADMIN` |
| `CLOUDOPS_NOTIFICATION_CHANNEL` | `log` | `log` or `sns` |
| `CLOUDOPS_INCIDENT_TOPIC_ARN` | empty | Required when the channel is `sns` |
| `CLOUDOPS_NOTIFICATION_MIN_SEVERITY` | `SEV2` | `SEV1`–`SEV4` |
| `CLOUDOPS_RELEASE` | `local` | Reported by `/api/v1/platform/info` |
| `CLOUDOPS_API_DOCS_ENABLED` | `true` | OpenAPI document and Swagger UI |
| `SPRING_PROFILES_ACTIVE` | none | `aws` in ECS |

The application validates its own configuration at startup: a missing or short JWT secret, or the
SNS channel without a topic ARN, stops it with an explicit error.

## Testing

### API

```bash
cd api
./mvnw test       # 54 unit and web-slice tests (fast, no database)
./mvnw verify     # + 12 integration tests, coverage gate and SpotBugs
```

| Kind | Examples | Technique |
|---|---|---|
| Domain | `IncidentTest`, `WorkloadHealthTest` | Plain JUnit; lifecycle transitions, timestamps, health derivation |
| Application | `AccountServiceTest`, `AuthenticationServiceTest`, `UserAdministrationServiceTest` | Mockito for repositories; real BCrypt encoder and real JWT encoder/decoder |
| Notifications | `IncidentNotificationListenerTest`, `SnsIncidentNotifierTest` | Severity threshold and event-type policy; SNS request shape and failure isolation |
| Web | `IncidentControllerTest` | `@WebMvcTest` with the real security configuration: 401/403, role hierarchy, validation errors, problem responses, security and correlation headers |
| Architecture | `ArchitectureTest` | ArchUnit module and layering rules |
| Integration (`*IT`) | `IncidentWorkflowIT`, `AccountsAndOperationsIT` | Full Spring context over MockMvc against an embedded PostgreSQL 17 server; Flyway migration and Hibernate validation included |

Integration tests use real PostgreSQL binaries (zonky embedded-postgres), so they need no Docker
daemon and behave the same locally and in CI. Each test starts from truncated tables.

Reports: `api/target/surefire-reports`, `api/target/failsafe-reports`, and the merged coverage report
in `api/target/site/jacoco/index.html`.

### Console

```bash
cd web
npm test                 # Vitest + Testing Library (jsdom)
npm run test:coverage
```

`src/test/harness.tsx` replaces `fetch` with a route table and provides a router and auth provider,
so page tests exercise real components, hooks and the API client.

### Infrastructure

```bash
cd infra
terraform init -backend=false
terraform test           # mocked AWS provider; no credentials needed
```

The tests apply individual modules and the full composition against a mocked provider and assert
properties such as "the database is not publicly accessible", "PostgreSQL requires TLS", "the ALB
only accepts CloudFront", and "the database only accepts the API security group".

## Quality gates

Everything below runs in CI ([DEPLOYMENT.md](DEPLOYMENT.md#5-what-the-pipeline-does)); these are the
local equivalents.

| Gate | Command |
|---|---|
| Java compile without warnings | `./mvnw compile` (`-Xlint:all -Werror`) |
| API tests, coverage ≥ 80 %, SpotBugs | `./mvnw verify` |
| Lint and types | `npm run lint` (zero warnings), `npm run typecheck` |
| Console tests and build | `npm test`, `npm run build` |
| Terraform | `terraform fmt -check -recursive`, `terraform validate`, `terraform test`, `tflint --recursive`, `checkov -d infra` |
| Dependencies | `trivy fs --scanners vuln --severity HIGH,CRITICAL --ignore-unfixed .` |
| Images | `docker build api` / `docker build web`, then `trivy image <tag>` |
| Workflows | `actionlint` |

SpotBugs exclusions (`api/spotbugs-exclude.xml`) and Checkov skips (inline `#checkov:skip` comments)
each state the reason they do not apply.

## Working on the API

- **Where code goes.** New behaviour belongs to a module's `application` service; controllers only
  translate HTTP. Input is a validated command record, output a view record. Reach into another
  module only through its `application` services or `events`.
- **Errors.** Throw `ResourceNotFoundException`, `ConflictException` or
  `BusinessRuleViolationException`; `ApiExceptionHandler` turns them into problem responses.
  When a uniqueness check precedes an insert, use `saveAndFlush` and translate
  `DataIntegrityViolationException` into the same `ConflictException`, so a concurrent duplicate
  gets the specific message rather than a generic one at commit.
- **Lists.** Bound `page` and `size` with `Paging.MAX_PAGE` and `Paging.MAX_SIZE`, and sort on a
  unique key (add the identifier or slug as a tie-breaker) so pages are stable. Add a matching index
  in a migration when a new ordering is introduced.
- **Authorisation.** Annotate mutating endpoints with `@PreAuthorize("hasRole('OPERATOR')")` or
  `'ADMIN'`; declare an `Actor` parameter to get the caller.
- **Schema changes.** Add a new `V<n>__description.sql` under `src/main/resources/db/migration`.
  Never edit an applied migration. Hibernate validates the result at startup, and the integration
  tests run the migration chain on PostgreSQL.
- **Dependency overrides.** `pom.xml` pins Tomcat and Jackson ahead of Spring Boot's managed versions
  for security fixes; remove the overrides once the Boot version you upgrade to includes them.

## Working on the console

- API calls go through `src/api/endpoints.ts`; add the matching type in `src/api/types.ts`.
- Use `useApi(key, loader)` for reads and `useAction(fn)` for writes; show `ErrorBanner` for errors
  and pass `onRetry` for reads. Do not render a form whose options failed to load.
- Build selects for API enumerations from the typed label maps in `src/lib/options.ts`; the compiler
  then flags any value added to `src/api/types.ts` that has no label.
- Gate UI on roles with `hasRole(user.role, 'OPERATOR')`, but remember the API is the authority.
- Styles are plain CSS with custom properties in `src/styles.css` (light and dark schemes).

## Working on the infrastructure

- Modules are self-contained with `variables.tf`, `main.tf`, `outputs.tf` and `versions.tf`; every
  variable and output has a description (enforced by TFLint).
- Network access rules belong in `modules/security-groups` so the allowed paths stay reviewable in
  one place.
- Add a `run` block to `tests/platform.tftest.hcl` for any new security-relevant property.
- Prefix names with the module's `name` input (`cloudops-<env>`); tags come from provider defaults.
- When a Checkov finding genuinely does not apply, skip it inline with a reason next to the resource.
