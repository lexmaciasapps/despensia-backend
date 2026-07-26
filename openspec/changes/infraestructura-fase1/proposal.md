# Proposal: infraestructura-fase1 — Base Infrastructure & Modular Monolith Scaffold

## Intent

Establish the foundational infrastructure and modular monolith architecture for `despensia-backend`, a greenfield Spring Boot application. This phase delivers containerized infrastructure (Docker + Kafka + PostgreSQL + Flyway) and the initial module skeleton, enabling all subsequent feature work (users, product, inventory, scan) with production-grade tooling from day one.

## Scope

### In Scope
- Docker Compose split: `docker-compose.infra.yml` (infra services) + `docker-compose.app.yml` (application)
- Kafka single-node KRaft (no Zookeeper dependency)
- PostgreSQL with Flyway for versioned migrations
- Modular monolith skeleton: `users`, `product`, `inventory`, `scan` modules
- ProductItem domain model with expiration logic:
  - `estimatedDaysRemaining`: Integer (nullable)
  - Packaged products: default `conservative_average` (e.g., 30 days for milk)
  - Organic products: default `ai_visual_estimate` (e.g., 3 days based on color/state)
  - Always user-editable ("Aprox. X días. ¿Confirmar?")
- Application bootstrap (Spring Boot, Maven/Gradle)
- Health checks and readiness probes for K8s deployment

### Out of Scope
- Authentication/authorization (next phase)
- AI vision integration for organic product scanning (next phase)
- Kafka consumers for event processing (next phase)
- CI/CD pipeline (next phase)
- Cloud deployment (next phase)

## Capabilities

### New Capabilities
- `infrastructure-docker`: Containerized infrastructure with separated compose files
- `infrastructure-kafka`: Single-node KRaft message broker
- `infrastructure-database`: PostgreSQL with Flyway migration management
- `domain-product-item`: ProductItem entity with expiration date estimation logic
- `module-users`: User module skeleton (CRUD foundation)
- `module-inventory`: Inventory module skeleton (stock tracking foundation)
- `module-scan`: Scan module skeleton (barcode/QR foundation)

### Modified Capabilities
None — greenfield project, no existing specs.

## Approach

1. **Infrastructure layer**: Separate Docker Compose files for infra services and application, enabling K8s pod decomposition later.
2. **Kafka KRaft mode**: Single node with `KAFKA_NODE_ID=1` and `KAFKA_PROCESS_ROLES=broker,controller` — eliminates Zookeeper, simplifies local dev while matching production topology.
3. **Flyway migrations**: Versioned SQL migrations applied at startup, giving us schema evolution from day one.
4. **Modular monolith**: Spring Boot with clear module boundaries (`users`, `product`, `inventory`, `scan`) — each module has its own package, API layer, and domain model. Modules communicate via internal interfaces (not direct dependency injection) to enforce boundaries.
5. **ProductItem model**: Core domain entity with `estimatedDaysRemaining` (Integer, nullable). Default strategy depends on product type: `conservative_average` for packaged items, `ai_visual_estimate` for organic items. Always editable by the user.

## Affected Areas

| Area | Impact | Description |
|------|--------|-------------|
| `openspec/config.yaml` | Modified | Update context with detected stack |
| `docker-compose.infra.yml` | New | PostgreSQL, Kafka, Flyway migration runner |
| `docker-compose.app.yml` | New | Spring Boot application container |
| `pom.xml` / `build.gradle` | New | Build configuration |
| `src/main/java/` | New | Spring Boot application entry point |
| `src/main/java/com/despensia/` | New | Module packages: users, product, inventory, scan |
| `src/main/resources/db/migration/` | New | Flyway SQL migrations |
| `src/main/resources/application.yml` | New | Application configuration |
| `.gitignore` | New | Language-specific ignores |

## Risks

| Risk | Likelihood | Mitigation |
|------|------------|------------|
| Module boundaries leak (direct DI between modules) | Medium | Enforce interface-only communication; code review gate |
| Kafka KRaft single-node not representative of prod | Low | Document topology; add multi-node compose profile for staging |
| Flyway migration ordering conflicts in modular setup | Low | Convention: each module prefixes migrations with module name (`V1_users__...`) |
| Over-engineering modular monolith too early | Medium | Keep modules as packages first; extract to separate artifacts only when needed |

## Rollback Plan

1. Delete the `infraestructura-fase1` change folder from `openspec/changes/`.
2. Run `git checkout -- .` to restore the working tree to pre-change state.
3. Delete any Docker volumes created during testing: `docker compose -f docker-compose.infra.yml down -v`.
4. All changes are local scaffolding — no data migration risk.

## Dependencies

- Java 21+ (LTS)
- Docker + Docker Compose (infra services)
- Maven 3.9+ or Gradle 8+ (build tool)
- Node.js (optional, for future frontend integration)

## Success Criteria

- [ ] `docker compose -f docker-compose.infra.yml up -d` starts PostgreSQL and Kafka successfully
- [ ] `docker compose -f docker-compose.app.yml up` starts the Spring Boot application
- [ ] Application health endpoint (`/actuator/health`) reports UP with DB and Kafka connectivity
- [ ] Flyway migrations apply cleanly on first startup
- [ ] Modular monolith structure compiles with clear module boundaries
- [ ] ProductItem entity exists with `estimatedDaysRemaining` (Integer, nullable) and default strategy logic
- [ ] All four modules (`users`, `product`, `inventory`, `scan`) have skeleton code and API endpoints
