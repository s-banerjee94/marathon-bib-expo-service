# Contributing to Marathon Bib Expo Service

First off, thank you for taking the time to contribute! Every contribution, big or small, is appreciated.

Please read this guide carefully before submitting issues or pull requests.

---

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How Can I Contribute?](#how-can-i-contribute)
  - [Reporting Bugs](#reporting-bugs)
  - [Suggesting Features](#suggesting-features)
  - [Submitting a Pull Request](#submitting-a-pull-request)
- [Development Setup](#development-setup)
- [Code Standards](#code-standards)

---

## Code of Conduct

This project adheres to a standard of respectful and inclusive collaboration. By participating, you are expected to:

- Be welcoming and respectful to all contributors
- Accept constructive feedback gracefully
- Focus on what is best for the project and community

Unacceptable behavior should be reported to the maintainers via GitHub.

---

## How Can I Contribute?

### Reporting Bugs

Before submitting a bug report, please check the [existing issues](https://github.com/s-banerjee94/marathon-bib-expo-service/issues) to avoid duplicates.

When reporting a bug, include:

- A clear and descriptive title
- Steps to reproduce the issue
- Expected behavior vs actual behavior
- Relevant logs, screenshots, or error messages
- Environment details (OS, Java version, etc.)

> Use the **bug** label when opening the issue.

### Suggesting Features

Feature requests are welcome. Open an issue with:

- A clear description of the feature and the problem it solves
- Any alternatives you have considered
- Additional context or mockups if applicable

> Use the **enhancement** label when opening the issue.

### Submitting a Pull Request

> **Important:** All contributions must branch off the `develop` branch. The `master` branch is reserved for stable releases. Never open a PR directly against `master`.

1. **Fork** the repository to your own GitHub account.

2. **Clone your fork** locally:
   ```bash
   git clone https://github.com/<your-username>/marathon-bib-expo-service.git
   cd marathon-bib-expo-service
   ```

3. **Add the upstream remote** to stay in sync with the main repository:
   ```bash
   git remote add upstream https://github.com/s-banerjee94/marathon-bib-expo-service.git
   ```

4. **Sync with upstream** before starting work:
   ```bash
   git fetch upstream
   git checkout -b fix/your-branch-name upstream/develop
   ```

5. Make your changes following the [Code Standards](#code-standards) below.

6. **Push to your fork**:
   ```bash
   git push origin fix/your-branch-name
   ```

7. Open a **Pull Request** from your fork targeting the **`develop`** branch of the main repository.

8. In your PR description:
   - Reference the related issue (e.g. `Fixes #4`)
   - Explain what changed and why
   - Include screenshots or logs if relevant

9. Address any feedback from the automated Claude Code Review or maintainers.

---

## Development Setup

### Prerequisites

- Java 17
- Maven (or use the included `./mvnw` wrapper)
- Docker & Docker Compose

### Steps

```bash
# Start dependent services (MySQL, LocalStack, Prometheus, Grafana)
docker-compose up -d

# Run the application
./mvnw spring-boot:run
```

- Application: `http://localhost:8080`
- Swagger UI: `http://localhost:8080/swagger-ui.html`
- Health check: `http://localhost:8080/actuator/health`

---

## Code Standards

- **Java 17** — use modern language features where appropriate.
- **Lombok** — use `@Data`, `@Builder`, `@Slf4j`, etc. to reduce boilerplate.
- **DTOs** — always use DTOs for API requests/responses; never expose JPA entities directly.
- **Layered architecture** — keep business logic in the service layer; controllers must be thin.
- **Exception handling** — use custom exceptions and `GlobalExceptionHandler` for consistent error responses.
- **OpenAPI** — add Swagger annotations to the `*ControllerApi` interface, not the implementation class.
- **Comments** — only add comments where the logic is not self-evident.
- **Interface Javadoc** — all interfaces must have Javadoc comments.
- **Security** — evaluate every new endpoint for authentication/authorization requirements in `SecurityConfig`.
