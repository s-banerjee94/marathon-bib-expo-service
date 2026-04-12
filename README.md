# Marathon Bib Expo Service

A Spring Boot application for managing bib expo operations for marathon events. This service handles participant bib and goodies distribution, CSV batch imports, real-time notifications, and expo management across multiple organizations and events.

## Prerequisites

- Java 17 or higher
- Docker and Docker Compose
- Maven (or use the included Maven wrapper)

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
- **MySQL** on port `3306`
- **LocalStack (DynamoDB)** on port `4566`
- **Prometheus** on port `9090`
- **Grafana** on port `3000`

### 3. Run the Application

```bash
./mvnw spring-boot:run
```

The application starts on port `8080` by default.

- **Swagger UI**: http://localhost:8080/swagger-ui.html
- **Health Check**: http://localhost:8080/actuator/health
- **OpenAPI Spec**: http://localhost:8080/v3/api-docs

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.0.1 |
| REST | Spring Web MVC |
| Security | Spring Security + JWT (JJWT 0.12.5) |
| Primary DB | MySQL 8.0 via Spring Data JPA / Hibernate |
| Secondary DB | AWS DynamoDB (LocalStack for local dev) |
| Batch Processing | Spring Batch 5.2.2 |
| CSV Handling | Apache Commons CSV 1.11.0 |
| API Docs | SpringDoc OpenAPI 2.7.0 (Swagger UI) |
| Metrics | Spring Boot Actuator + Micrometer (Prometheus) |
| Boilerplate | Lombok |
| Monitoring | Prometheus + Grafana |

---

## Architecture

The application uses a **dual-database architecture**:

- **MySQL** — relational storage for organizations, users, events, races, and categories
- **DynamoDB** — NoSQL storage for participant records, distribution logs, import errors, and notifications

```
src/main/java/com/timekeeper/bibexpo/
├── batch/          # Spring Batch jobs for async CSV imports
├── config/         # Spring configuration (Security, CORS, DynamoDB, JPA, OpenAPI)
├── controller/     # REST controllers + API interface definitions (*ControllerApi.java)
├── exception/      # Custom exceptions and GlobalExceptionHandler
├── model/
│   ├── dto/        # Request/response DTOs
│   ├── entity/     # JPA entities (MySQL)
│   ├── dynamodb/   # DynamoDB entity mappings
│   └── enums/      # Domain enums (UserRole, Gender, EventStatus, etc.)
├── repository/     # JPA repositories (MySQL) + DynamoDB repositories
├── security/       # JWT filter, entry point, access denied handler
├── service/        # Business logic (interfaces + impl/)
├── util/           # CSV parsing utilities, DynamoDB pagination codec
└── validator/      # Custom bean validators (@ValidEnum, @ValidCreateParticipant)
```

---

## Key Features

- **Organization Management** — multi-tenant support with isolated organizations, user capacity limits, and enable/disable controls
- **Event Management** — create and manage marathon events with status lifecycle (DRAFT → PUBLISHED → COMPLETED/CANCELLED), races, and age/gender-based categories
- **Participant Management** — store participant data in DynamoDB with support for single and bulk create, update, delete, search, lookup, and CSV export
- **CSV Batch Import** — asynchronous participant import via Spring Batch with real-time progress updates over SSE and per-row error tracking
- **Bib Distribution** — track bib collection per participant with staff attribution, bulk collect, and undo support
- **Goodies Distribution** — distribute and track goodies items per participant with bulk operations and duplicate prevention
- **Distribution Logs** — full audit trail of all bib and goodies distribution activity with search and pagination
- **Real-time Notifications** — Server-Sent Events (SSE) for live import progress and in-app notifications with read/unread tracking
- **SMS Templates** — manage SMS templates with DLT ID tracking for future SMS gateway integration
- **Application Statistics** — cached snapshots of user, organization, and event counts with scope-aware views (global vs per-organization)
- **JWT Authentication** — stateless Bearer token auth with role-based access control across all endpoints

---

## Authentication

All API endpoints (except `/api/auth/login`) require a JWT Bearer token.

**1. Login to get a token:**
```http
POST /api/auth/login
Content-Type: application/json

{
  "username": "your-username",
  "password": "your-password"
}
```

**2. Use the token in subsequent requests:**
```http
Authorization: Bearer <token>
```

Tokens are valid for **7 days**. On Swagger UI, click **Authorize** and enter `Bearer <token>` to authenticate.

---

## Root User

On first startup, the application automatically creates a root user using the credentials set in `.env` (`ROOT_USERNAME` / `ROOT_PASSWORD`). This user has the `ROOT` role with full system access and is the entry point for all administrative setup.

> **Important:** Change the default root credentials before deploying to any non-local environment.

Use the root account to log in via `/api/auth/login` and obtain a JWT token to start creating organizations and users.

---

## User Roles

| Role | Description |
|---|---|
| `ROOT` | Full system access across all organizations |
| `ADMIN` | System-level admin access |
| `ORGANIZER_ADMIN` | Admin for their own organization |
| `ORGANIZER_USER` | Standard user within an organization |
| `DISTRIBUTOR` | Can perform bib/goodies distribution only |

---

## Environment Variables

| Variable | Default | Description |
|---|---|---|
| `DB_HOST` | `localhost` | MySQL host |
| `DB_PORT` | `3306` | MySQL port |
| `DB_NAME` | `marathon_bib_expo` | Database name |
| `DB_USERNAME` | `root` | MySQL username |
| `DB_PASSWORD` | `root` | MySQL password |
| `DDL_AUTO` | `update` | Hibernate DDL strategy |
| `SERVER_PORT` | `8080` | Application port |
| `ROOT_USERNAME` | `root` | Root user username |
| `ROOT_PASSWORD` | `root` | Root user password |
| `JWT_SECRET` | dev key | JWT signing secret (use 256+ bits in production) |
| `AWS_REGION` | `us-east-1` | AWS region |
| `AWS_ACCESS_KEY_ID` | `test` | AWS access key |
| `AWS_SECRET_ACCESS_KEY` | `test` | AWS secret key |
| `AWS_DYNAMODB_ENDPOINT` | `http://localhost:4566` | DynamoDB / LocalStack endpoint |
| `AWS_DYNAMODB_TABLE_NAME` | `marathon-participants` | DynamoDB table name |
| `STATS_STALE_THRESHOLD_MINUTES` | `15` | Statistics cache stale threshold |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://localhost:4200` | Allowed CORS origins |

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

---

## Monitoring

| Service | URL | Credentials |
|---|---|---|
| Swagger UI | http://localhost:8080/swagger-ui.html | Bearer JWT |
| Health Check | http://localhost:8080/actuator/health | — |
| Prometheus Metrics | http://localhost:8080/actuator/prometheus | — |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | admin / admin |

---

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for guidelines on how to contribute to this project.

---

## License

This project is licensed under the **GNU Affero General Public License v3.0 (AGPL-3.0)**. See the [LICENSE](LICENSE) file for details.

Anyone who uses, modifies, or runs this software as a service must release their source code under the same license.

