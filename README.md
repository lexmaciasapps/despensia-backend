# despensia-backend

Modular monolith backend for Despensia — AI-powered grocery expiration tracking.

## Tech Stack

- **Language**: Java 21
- **Framework**: Spring Boot 3.2
- **Build**: Maven 3.9+
- **Database**: PostgreSQL 16 (JPA/Hibernate)
- **NoSQL**: MongoDB 7
- **Cache**: Redis 7
- **Messaging**: Kafka 7.6.1 (KRaft mode)
- **Migration**: Flyway
- **Containerization**: Docker + Docker Compose

## Quick Start

### 1. Start Infrastructure

```bash
docker compose -f docker-compose.infra.yml up -d
```

This starts PostgreSQL, MongoDB, Redis, and Kafka (KRaft single-node).

### 2. Build the Application

```bash
mvn clean package -DskipTests
```

### 3. Run Locally

```bash
# Using Maven
mvn spring-boot:run

# Or using the JAR
java -jar target/despensia-backend-0.0.1-SNAPSHOT.jar

# Or with Docker
docker compose -f docker-compose.app.yml up
```

The application starts on `http://localhost:8080`.

### 4. Verify Health

```bash
curl http://localhost:8080/actuator/health
```

Expected response:
```json
{"status":"UP","components":{"db":{"status":"UP"},"kafka":{"status":"UP"},"mongo":{"status":"UP"},"redis":{"status":"UP"}}}
```

## Module Structure

```
src/main/java/com/despensia/
├── DespensiaApplication.java    # Entry point
├── config/                       # Cross-cutting config (Kafka, Flyway, Redis)
├── users/                        # User module
│   ├── api/
│   ├── service/
│   ├── domain/
│   └── repository/
├── product/                      # Product module (core domain)
│   ├── api/
│   ├── service/
│   ├── domain/
│   │   ├── ProductItem.java      # Core entity
│   │   ├── DefaultExpirationStrategy.java
│   │   ├── ConservativeAverageStrategy.java
│   │   └── AiVisualEstimateStrategy.java
│   └── repository/
├── inventory/                    # Inventory module
│   ├── api/
│   ├── service/
│   ├── domain/
│   └── repository/
└── scan/                         # Scan module
    ├── api/
    ├── service/
    ├── domain/
    └── repository/
```

## Configuration

- **Default profile**: Production-ready defaults (localhost connections)
- **Local profile**: Dev overrides (SQL logging, Flyway validation off, devtools)
- **Docker profile**: Set via `SPRING_PROFILES_ACTIVE=docker` in `docker-compose.app.yml`

## ProductItem Expiration

The `ProductItem` entity uses a strategy pattern for default expiration estimation:

- **Packaged products**: Conservative average (30 days default)
- **Organic products**: AI visual estimate (7 days stub — AI integration pending)
- **User override**: Always allowed — the user can set any estimated days value

## Docker Compose Files

| File | Purpose |
|------|---------|
| `docker-compose.infra.yml` | Infrastructure services (PostgreSQL, MongoDB, Redis, Kafka) |
| `docker-compose.app.yml` | Application container (depends on infra health checks) |

## Stop Infrastructure

```bash
docker compose -f docker-compose.infra.yml down -v
```

> **Warning**: `-v` removes all data volumes. Use without `-v` to preserve data.
