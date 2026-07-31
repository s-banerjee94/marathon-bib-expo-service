# Marathon Bib Expo Service

A Spring Boot application for managing bib expo operations for marathon events. This service handles participant bib and goodies distribution, CSV batch imports, real-time notifications, multi-channel messaging (SMS/WhatsApp), usage-based billing, platform and event dashboards, and an AI assistant — all across multiple organizations and events.

## Prerequisites

- Java 17 or higher
- Docker and Docker Compose
- Maven (or use the included Maven wrapper)
- *(Optional, for the AI agent sidecar)* Python 3.13+ and [uv](https://docs.astral.sh/uv/)

## Quick Start

### 1. Setup Environment Variables

Copy the example environment file and configure as needed:

```bash
cp .env.example .env
```

Edit `.env` to set your database credentials and other configuration.

### 2. Start Infrastructure Services

```bash
docker-compose up -d
```

This starts:
- **MySQL 8.0** on port `3306`
- **LocalStack** (DynamoDB, S3, Lambda, EventBridge Scheduler) on port `4566`
- **Prometheus** on port `9090`
- **Grafana** on port `3000`

`scripts/localstack-init.sh` runs on LocalStack startup and creates every DynamoDB table plus the S3
bucket. The MySQL schema is created by Hibernate (`DDL_AUTO=update`).

> The committed defaults in `application.yaml` expect this stack on a shared LAN Docker host. If you
> run it on the same machine as the application, point `DB_HOST` and the `AWS_*_ENDPOINT` variables
> at `localhost` in your `.env`.

### 3. Run the Application

```bash
./mvnw spring-boot:run
```

The application starts on port `8080` by default.

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/actuator/health
- **OpenAPI Spec**: http://localhost:8080/v3/api-docs

### 4. (Optional) Run the AI Agent Sidecar

The AI assistant is a standalone Python service under [`ai-agent/`](ai-agent/) that talks to this application's MCP server. See [AI Agent Sidecar](#ai-agent-sidecar) below.

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.0.7 |
| REST | Spring Web MVC |
| Security | Spring Security + JWT (RS256 access/refresh tokens, JJWT 0.12.5) |
| Primary DB | MySQL 8.0 via Spring Data JPA / Hibernate |
| Secondary DB | AWS DynamoDB (LocalStack for local dev) |
| Object Storage | AWS S3 (profile pictures, logos, media, invoices) |
| Async Jobs | AWS Lambda + EventBridge Scheduler (billing), Spring `@Scheduled` |
| AI | Spring AI 2.0.0 MCP server (SSE) + Python LangGraph agent sidecar |
| Batch Processing | Spring Batch 5.2.2 |
| CSV Handling | Apache Commons CSV 1.11.0 |
| QR / Barcode | ZXing 3.5.3 |
| Caching | Caffeine |
| API Docs | SpringDoc OpenAPI 2.7.0 (Swagger UI) |
| Metrics | Spring Boot Actuator + Micrometer (Prometheus) |
| Boilerplate | Lombok |
| Monitoring | Prometheus + Grafana |

---

## Architecture

The application uses a **dual-database architecture**:

- **MySQL** — relational storage for organizations, users, events, races, categories, billing invoices, messaging providers/templates, and daily statistics
- **DynamoDB** — NoSQL storage for participants, distribution logs, import errors, event stats, notifications, the audit log, and participant short links (table names are individually overridable and can be namespaced at once with `AWS_DYNAMODB_TABLE_PREFIX`)
- **S3** — object storage for profile pictures, organization/event logos, AI media attachments, and generated invoice PDFs

```
src/main/java/com/timekeeper/bibexpo/
├── ai/                 # Spring AI MCP server + tools (ai.mcp) wrapping existing services
├── annotation/         # Custom annotations
├── aspect/             # AOP aspects (cross-cutting concerns)
├── batch/              # Spring Batch jobs for async CSV imports
├── billing/            # Usage-based billing: invoices, GST PDFs, Lambda/Scheduler wiring
├── config/             # Spring configuration (Security, CORS, DynamoDB, JPA, Cache, OpenAPI)
├── controller/         # REST controllers + API interface definitions (*ControllerApi.java)
├── demo/               # Public landing-page live QR demo (SSE, in-memory session store)
├── exception/          # ApiException hierarchy and GlobalExceptionHandler
├── invitation/         # User invitation flow (token store + delivery)
├── messaging/          # SMS/WhatsApp: campaign, delivery, direct, provider, shared, system
├── model/
│   ├── dto/            # Request/response DTOs
│   ├── entity/         # JPA entities (MySQL)
│   ├── dynamodb/       # DynamoDB entity mappings
│   ├── enums/          # Domain enums (SubscriptionTier, DashboardRange, AuditAction, ...)
│   └── event/          # Spring application events
├── notification/       # In-app notifications (DynamoDB-backed, polled by clients)
├── participantaccess/  # Participant self-service access via signed QR links
├── passwordreset/      # Self-service & admin-issued password reset flow
├── repository/         # JPA repositories (MySQL) + DynamoDB repositories
├── scheduler/          # Scheduled jobs (e.g. session cleanup)
├── security/           # JWT filters, MCP token filter, entry point, access handlers
├── service/            # Business logic (interfaces + impl/, cache/, dashboard/, audit/, validator/)
├── util/               # Generic helpers (text, event date/time, name normalization)
└── validation/         # Custom bean validators (@ValidEnum, @ValidCreateParticipant)
```

Core domains use a layered structure; newer domains (`billing/`, `messaging/`, `notification/`,
`invitation/`, `passwordreset/`, `participantaccess/`, `demo/`) are organized package-by-feature,
each with its own controller/service/model/repository. New features follow the latter.

---

## Key Features

- **Organization Management** — multi-tenant support with isolated organizations, subscription tiers, user/event capacity limits, and enable/disable controls
- **Event Management** — create and manage marathon events with status lifecycle, races, age/gender-based categories, and per-race race-day reporting times
- **Participant Management** — store participant data in DynamoDB with single and bulk create, update, delete, search, lookup, and CSV export
- **Participant Self-Access** — signed QR links let participants view their own bib/collection status without an account
- **CSV Batch Import** — asynchronous participant import via Spring Batch with pollable job status, stop support, and per-row errors reported at their physical CSV line numbers
- **Bib Distribution** — track bib collection per participant with staff attribution, bulk collect, and undo support
- **Goodies Distribution** — distribute and track goodies items per participant with bulk operations and duplicate prevention
- **Distribution Logs** — full audit trail of all bib and goodies distribution activity with search and pagination
- **Messaging & Campaigns** — SMS and WhatsApp delivery through a dynamic provider engine, with per-organization overrides, reusable system and campaign templates, and DLT/sender configuration
- **Targeted Participant Messaging** — re-send a message to named bib numbers (up to 25 per request) instead of the whole event, for the participant who never received the automatic one; sent while the request is open, with a per-bib outcome and no effect on campaign send history
- **Invitations** — invite new users via tokenized links delivered over configured channels
- **Password Reset** — admin-issued reset links plus a public forgot-password flow that never discloses account existence
- **Billing** — usage-based invoicing (auto + manual), GST invoice PDFs to S3, and per-organization / global / summary views
- **Dashboards** — platform-wide rollups with revenue and trend charts, plus per-event activity dashboards
- **Audit Log** — `@Auditable` AOP capture of actor/action/entity events into DynamoDB, with search and pagination
- **Notifications** — in-app notifications stored in DynamoDB with read/unread tracking and unread counts; clients poll
- **Public Live Demo** — a landing-page QR demo backed by short-lived in-memory sessions, streamed over SSE with per-IP rate limits
- **AI Assistant** — an in-repo Spring AI MCP server exposes existing services as tools; a Python LangGraph agent sidecar drives conversations with role-based tool visibility and human-in-the-loop approval for writes
- **Caching** — Caffeine-backed caches with post-commit programmatic eviction
- **JWT Authentication** — stateless RS256 Bearer auth with refresh-token rotation and role-based access control

---

## AI Agent Sidecar

The `ai-agent/` directory contains a standalone **Python LangGraph** service (FastAPI + SSE) that acts as the chat assistant. It authenticates users, verifies their JWT locally, and calls this application's **Spring AI MCP server** (SSE at `/sse`, messages at `/mcp/message`) to read and act on data using the same role-based permissions as the REST API. Those two routes sit
on their own Spring Security chain (`McpTokenAuthenticationFilter`), which resolves the caller and
puts them in the SecurityContext so every MCP tool inherits the caller's RBAC.

Highlights:
- Conversation memory persisted in DynamoDB with automatic summarization
- Role-based MCP tool visibility (tools filtered per user role)
- Human-in-the-loop approval gating for write/mutating tools
- Per-chat MCP tool enable/disable with saved preferences
- Image/PDF attachments injected to the model via transient S3 storage

Run it locally:

```bash
cd ai-agent
cp .env.example .env   # set OpenAI/API keys, AWS, and MCP endpoint
uv sync
uv run uvicorn app.main:app --reload
```

> Requires Python 3.13+. The MCP server it talks to is served by the Java application, which must be running.

All agent endpoints are served under an `/ai` prefix (e.g. `/ai/chat`, `/ai/chat/attachments`) — a
reverse proxy in front of it must pass `/ai/` through unchanged.

---

## Authentication

The API uses **stateless RS256 JWT** authentication with a short-lived access token and a long-lived refresh token.

**1. Log in to obtain tokens:**
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "your-username",
  "password": "your-password"
}
```
The response returns an **access token** (send as `Authorization: Bearer <token>`), and a **refresh token** is set as an httpOnly cookie alongside a CSRF cookie.

**2. Use the access token on subsequent requests:**
```http
Authorization: Bearer <access-token>
```

**3. Rotate an expired access token:**
```http
POST /api/auth/refresh
```
Uses the refresh cookie to mint a new access token.

**4. Log out:**
```http
POST /api/auth/logout
```

Access tokens are valid for **15 minutes**; refresh tokens for **7 days** (both configurable). On Swagger UI, click **Authorize** and enter `Bearer <access-token>` to authenticate. Keys are RSA PEM files configured via `JWT_PRIVATE_KEY_LOCATION` / `JWT_PUBLIC_KEY_LOCATION`.

---

## Root User

On first startup, the application automatically creates a root user using the credentials set in `.env` (`ROOT_USERNAME` / `ROOT_PASSWORD`). This user has the `ROOT` role with full system access and is the entry point for all administrative setup.

Use the root account to log in via `/api/auth/login` and obtain a JWT token to start creating organizations and users.

---

## User Roles

| Role | Description |
|---|---|
| `ROOT` | Full system access across all organizations |
| `ADMIN` | System-level admin access |
| `ORGANIZER_ADMIN` | Admin for their own organization |
| `ORGANIZER_USER` | Standard user within an organization |
| `DISTRIBUTOR` | Distributor bound to a single event; bib/goodies distribution only |

---

## API Overview

Full request/response detail lives in Swagger UI. Endpoints at a glance:

| Area | Base path |
|---|---|
| Auth | `/api/auth/login`, `/refresh`, `/logout`, `/password-reset/**`, `/invitations/**` |
| Users | `/api/users`, `/api/users/invitations` |
| Organizations | `/api/organizations` (+ `/{id}/billing`, `/{id}/campaign-providers`) |
| Events | `/api/events` (+ `/{id}/limits`, `/{id}/dashboard`, `/{id}/billing`) |
| Races & Categories | `/api/events/{eventId}/races`, `/races/{raceId}/categories` |
| Participants | `/api/events/{eventId}/participants` (+ `/count`, `/lookup`, `/export`, `/bulk`, `/{bibNumber}`) |
| Batch Import | `/api/events/{eventId}/participants/batch-import` (+ `/{jobExecutionId}/status`, `/stop`, `/latest/errors`) |
| Distribution | `/api/events/{eventId}/distribution` |
| Participant Access | `/api/events/{eventId}/participant-access` |
| Messaging | `/api/events/{eventId}/{sms,whatsapp}-campaigns`, `/{sms,whatsapp}-templates`, `/campaign-provider-style` |
| Targeted Messaging | `/api/events/{eventId}/participant-messages` |
| System (root only) | `/api/system/message-templates`, `/messaging-providers`, `/campaign-providers` |
| Dashboards | `/api/dashboard/organization`, `/api/dashboard/platform` (+ `/revenue`) |
| Billing | `/api/billing` |
| Audit Logs | `/api/audit-logs` |
| Notifications | `/api/notifications` |
| Public | `/api/public/short-links`, `/api/public/demo/sessions` |
| MCP | `/sse`, `/mcp/message` |
| Actuator | `/actuator/health`, `/actuator/metrics`, `/actuator/prometheus` |

Public (unauthenticated) routes are `/api/auth/**`, `/api/public/**`, `/api/dev/**`, `/actuator/**`,
and the Swagger paths. Everything else requires a Bearer access token.

---

## Environment Variables

The active profile defaults to `local`. `application-local.yaml` is gitignored and holds local-only
secrets (such as the OpenAI key used by Spring AI); `application-prod.yaml` carries the production
overrides and deliberately gives its secrets no defaults so the app fails fast when they are missing.

Common variables (see `.env.example` and `application.yaml` for the full list and defaults). The
committed defaults point at the shared LAN Docker host — override them in `.env` if your
infrastructure runs elsewhere:

| Variable | Default | Description |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | Active Spring profile (`local` / `prod`) |
| `DB_HOST` | `192.168.0.113` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `marathon_bib_expo` | Database name |
| `DB_USERNAME` | `root` | MySQL username |
| `DB_PASSWORD` | `root` | MySQL password |
| `DDL_AUTO` | `update` | Hibernate DDL strategy |
| `SERVER_PORT` | `8080` | Application port |
| `ROOT_USERNAME` | `root` | Root user username |
| `ROOT_PASSWORD` | `root` | Root user password |
| `JWT_PRIVATE_KEY_LOCATION` | `file:keys/jwt-private-dev.pem` | RSA private key for signing tokens |
| `JWT_PUBLIC_KEY_LOCATION` | `file:keys/jwt-public-dev.pem` | RSA public key for verifying tokens |
| `JWT_ACCESS_TOKEN_EXPIRATION` | `900000` | Access token lifetime (ms) — 15 min |
| `JWT_REFRESH_TOKEN_EXPIRATION` | `604800000` | Refresh token lifetime (ms) — 7 days |
| `JWT_COOKIE_SECURE` | `false` | **Set `true` in production (HTTPS)** |
| `AWS_REGION` | `us-east-1` | AWS region |
| `AWS_ACCESS_KEY_ID` | `test` | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | `test` | AWS secret key |
| `AWS_DYNAMODB_ENDPOINT` | `http://192.168.0.113:4566` | DynamoDB / LocalStack endpoint (empty for real AWS) |
| `AWS_DYNAMODB_TABLE_PREFIX` | *(empty)* | Prefix applied to every DynamoDB table name (e.g. `staging-`) |
| `AWS_S3_BUCKET` | `marathon-bib-expo-media` | S3 bucket for media / invoices |
| `AWS_S3_ENDPOINT` | LocalStack | S3 endpoint (empty for real AWS) |
| `AWS_LAMBDA_ENDPOINT` | LocalStack | Lambda endpoint (billing) |
| `AWS_SCHEDULER_ENDPOINT` | LocalStack | EventBridge Scheduler endpoint |
| `BILLING_SCHEDULING_ENABLED` | `false` | Auto-bill scheduling; keep off locally (LocalStack never fires schedules) |
| `BILLING_DELAY_HOURS` | `5` | Delay between an event turning terminal and its auto-bill |
| `MESSAGING_STUB_ENABLED` | `true` | Stubs all outbound SMS/WhatsApp provider calls (logs, never sends) |
| `SYSTEM_MESSAGING_SECRET_KEY` | dev key | AES key encrypting provider secrets at rest — **replace in production** |
| `PARTICIPANT_ACCESS_QR_SECRET` | dev secret | Signing secret for participant QR links — **replace in production** |
| `CACHE_TYPE` | `caffeine` | Spring cache provider |
| `CORS_ALLOWED_ORIGINS` | `*` | Allowed CORS origins (must be exact origins in production) |

---

## Development

### Run Tests
```bash
./mvnw test
```

### Run a Single Test Class
```bash
./mvnw test -Dtest=ClassName
```

### Build JAR
```bash
./mvnw clean package
```

### Skip Tests
```bash
./mvnw clean install -DskipTests
```

> Spring Boot DevTools is commented out in `pom.xml` — there is no hot reload; restart the
> application after code changes.

---

## Monitoring

| Service | URL | Credentials |
|---|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html | Bearer JWT |
| Health Check | http://localhost:8080/actuator/health | — |
| Prometheus Metrics | http://localhost:8080/actuator/prometheus | — |
| Prometheus | http://\<docker-host\>:9090 | — |
| Grafana | http://\<docker-host\>:3000 | admin / admin |

Prometheus and Grafana run in Docker alongside the databases, so substitute the host running
`docker-compose` (`localhost` if that is your own machine). Prometheus scrapes the application at
`host.docker.internal:8080` by default — adjust the target in `prometheus.yml` when the app runs on
a different host than Docker.

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to contribute to this project.

---

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**. See the [LICENSE](LICENSE) file for details.

Anyone who uses, modifies, or runs this software as a service must release their source code under the same license.
