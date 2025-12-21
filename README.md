# Marathon Bib Expo Service

A Spring Boot application for managing bib expo operations for marathon events. This service handles participant registration, bib number distribution, and expo management with guide support.

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

Start MySQL, Prometheus, and Grafana using Docker Compose:

```bash
docker-compose up -d
```

This will start:
- MySQL database on port 3306
- Prometheus on port 9090
- Grafana on port 3000

**Note:** MySQL runs in Docker, the application runs locally on your machine, and Prometheus scrapes metrics from your local IP: 172.21.208.1:8080

### 3. Build and Run the Application

```bash
./mvnw spring-boot:run
```

The application will start on port 8080 (or the port specified in your .env file).

## Monitoring

### Prometheus
Access Prometheus at http://localhost:9090 to view metrics and query application data.

### Grafana
Access Grafana at http://localhost:3000
- **Username**: admin
- **Password**: admin

Prometheus is pre-configured as a datasource. You can create dashboards to visualize application metrics.

### Application Metrics
Raw Prometheus metrics are available at: http://localhost:8080/actuator/prometheus

### Health Check
Check application health at: http://localhost:8080/actuator/health

## Development

### Run Tests
```bash
./mvnw test
```

### Build JAR
```bash
./mvnw clean package
```

The JAR file will be created in the `target/` directory.

## Project Structure

```
marathon-bib-expo-service/
├── src/
│   ├── main/
│   │   ├── java/com/timekeeper/bibexpo/
│   │   └── resources/
│   │       └── application.yaml
│   └── test/
├── docker-compose.yml
├── prometheus.yml
├── grafana/
│   └── provisioning/
├── .env
└── pom.xml
```

## Tech Stack

- **Java 17**
- **Spring Boot 4.0.1**
- **Spring Data JPA** - Database persistence
- **MySQL 8.0** - Primary database
- **Spring Web MVC** - REST API
- **Spring Boot Actuator** - Metrics and monitoring
- **Prometheus** - Metrics collection
- **Grafana** - Metrics visualization
- **Lombok** - Reduce boilerplate code
- **Spring Validation** - Input validation

## License

[Add your license information here]
