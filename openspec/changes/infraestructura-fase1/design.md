# Design: infraestructura-fase1 — Base Infrastructure & Modular Monolith Scaffold

## Technical Approach

Greenfield Spring Boot 3.2 modular monolith with Maven. Four domain modules (`users`, `product`, `inventory`, `scan`) coexist in a single WAR, communicating via internal interfaces to enforce boundaries. Docker Compose splits infra (PostgreSQL 16, MongoDB 7, Redis 7, Kafka KRaft) from the application. Flyway applies versioned migrations at startup. ProductItem is the core domain entity with `estimatedDaysRemaining` (Integer, nullable) and a default strategy based on product type.

## Architecture Decisions

### Decision: Modular monolith package layout

**Choice**: Single `pom.xml` with four module packages under `com.despensia.{module}`. Each module owns its package, API layer, service layer, domain model, and repository.

**Alternatives considered**: Separate Maven modules per domain | Clean architecture hexagonal packages | Microservices from day one

**Rationale**: Modular monolith gives us clear boundaries without operational overhead. Separate Maven modules add build complexity for a single deployment. Hexagonal is over-engineering for phase 1. Microservices require auth, service discovery, distributed tracing — all out of scope.

```
src/main/java/com/despensia/
├── DespensiaApplication.java          # @SpringBootApplication, @ComponentScan
├── config/                             # Shared cross-cutting config
│   ├── KafkaConfig.java
│   ├── FlywayConfig.java
│   └── RedisConfig.java
├── users/
│   ├── api/        # REST controllers
│   ├── service/    # Business logic
│   ├── domain/     # Entity, value objects
│   └── repository/ # JPA repositories
├── product/
│   ├── api/
│   ├── service/
│   ├── domain/
│   │   └── ProductItem.java           # Core domain entity
│   └── repository/
├── inventory/
│   ├── api/
│   ├── service/
│   ├── domain/
│   └── repository/
└── scan/
    ├── api/
    ├── service/
    ├── domain/
    └── repository/
```

### Decision: Kafka KRaft single-node

**Choice**: `KAFKA_PROCESS_ROLES=broker,controller` with `KAFKA_NODE_ID=1`, no Zookeeper.

**Alternatives considered**: Zookeeper + KRaft broker | Multi-node KRaft cluster

**Rationale**: Single-node KRaft matches production topology without Zookeeper dependency. Multi-node adds compose complexity for local dev. We document the topology for staging later.

### Decision: Flyway migration naming convention

**Choice**: Module-prefixed migration names: `V{version}_{module}__{description}.sql`

**Alternatives considered**: Global migration numbering without prefix | Module-specific migration folders

**Rationale**: Prefixing prevents naming collisions across modules while keeping migrations in a single Flyway classpath folder. Module-specific folders require custom Flyway configuration.

```
src/main/resources/db/migration/
├── V1__init_schema.sql              # Core tables (users, product, inventory, scan)
├── V2__product_item_extension.sql   # ProductItem fields (estimatedDaysRemaining, productType)
└── V3__inventory_scan_tables.sql    # InventoryScan, ScanItem tables
```

### Decision: ProductItem expiration strategy

**Choice**: `estimatedDaysRemaining` as Integer (nullable) with a `DefaultExpirationStrategy` interface. Packaged → `ConservativeAverageStrategy`, Organic → `AiVisualEstimateStrategy` (stub). User always overrides.

**Alternatives considered**: Store raw expiration date | Store estimatedDaysRemaining only | Always require user input

**Rationale**: Integer days is simpler than dates for a "remaining" counter. Nullable allows products without expiration data. Strategy pattern lets us swap AI integration later without changing the entity. User override is non-negotiable — the system assists, never decides.

```java
// product/domain/ProductItem.java
@Entity
public class ProductItem {
    @Enumerated(EnumType.STRING)
    private ProductType productType;  // PACKAGED | ORGANIC
    
    private Integer estimatedDaysRemaining;  // nullable
    
    // User-editable: always allowed
    public void updateEstimatedDays(Integer days) {
        this.estimatedDaysRemaining = days;
    }
}

// product/domain/DefaultExpirationStrategy.java
public interface DefaultExpirationStrategy {
    Integer defaultFor(ProductType type);
}

// product/domain/ConservativeAverageStrategy.java
@Service
public class ConservativeAverageStrategy implements DefaultExpirationStrategy {
    private static final Map<ProductType, Integer> AVERAGES = Map.of(
        PACKAGED_MILK, 30,
        PACKAGED_JUICE, 21,
        PACKAGED_YOGURT, 14
    );
    public Integer defaultFor(ProductType type) {
        return AVERAGES.getOrDefault(type, 30);
    }
}
```

### Decision: Docker Compose split

**Choice**: `docker-compose.infra.yml` (PostgreSQL, MongoDB, Redis, Kafka) + `docker-compose.app.yml` (Spring Boot app, depends_on infra).

**Alternatives considered**: Single compose file | K8s manifests only

**Rationale**: Split enables K8s pod decomposition later (infra pods → separate deployments). Single file couples infra lifecycle to app lifecycle. K8s-only adds deployment complexity before we have running code.

```yaml
# docker-compose.infra.yml
services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: despensia
      POSTGRES_USER: despensia
      POSTGRES_PASSWORD: despensia
    volumes: [postgres_data:/var/lib/postgresql/data]
    healthcheck: [cmd: pg_isready, interval: 5s, timeout: 3s, retries: 10]
    networks: [despensia-net]

  mongodb:
    image: mongo:7
    environment:
      MONGO_INITDB_DATABASE: despensia
    volumes: [mongodb_data:/data/db]
    healthcheck: [cmd: mongosh --eval ping, interval: 5s, retries: 10]
    networks: [despensia-net]

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    volumes: [redis_data:/data]
    healthcheck: [cmd: redis-cli ping, interval: 5s, retries: 10]
    networks: [despensia-net]

  kafka:
    image: confluentinc/cp-kafka:7.6.1
    environment:
      KAFKA_NODE_ID: 1
      KAFKA_PROCESS_ROLES: broker,controller
      KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_LISTENERS: PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CONTROLLER_LISTENER_NAMES: CONTROLLER
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    volumes: [kafka_data:/var/lib/kafka/data]
    healthcheck: [cmd: kafka-broker-api-versions --bootstrap-server localhost:9092, interval: 10s, retries: 20]
    networks: [despensia-net]

volumes:
  postgres_data:
  mongodb_data:
  redis_data:
  kafka_data:

networks:
  despensia-net:
    driver: bridge
```

```yaml
# docker-compose.app.yml
services:
  app:
    build:
      context: .
      dockerfile: Dockerfile
    ports: ["8080:8080"]
    environment:
      SPRING_PROFILES_ACTIVE: docker
      SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/despensia
      SPRING_DATA_MONGODB_URI: mongodb://mongodb:27017/despensia
      SPRING_DATA_REDIS_HOST: redis
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
    depends_on:
      postgres: { condition: service_healthy }
      mongodb: { condition: service_healthy }
      redis: { condition: service_healthy }
      kafka: { condition: service_healthy }
    networks: [despensia-net]

networks:
  despensia-net:
    external: true
```

## Data Flow

### Application bootstrap
```
Docker Compose → Start infra (postgres, mongo, redis, kafka)
    → Health checks pass
    → Start app container
        → Flyway runs migrations (V1 → V2 → V3)
        → Spring Boot scans packages (users, product, inventory, scan)
        → Health endpoint /actuator/health → UP
```

### ProductItem creation flow
```
POST /api/products
    → ProductController.create()
    → ProductService.create()
        → Detect productType (PACKAGED / ORGANIC)
        → Resolve DefaultExpirationStrategy
            → PACKAGED → ConservativeAverageStrategy → 30 days
            → ORGANIC  → AiVisualEstimateStrategy    → null (stub, AI pending)
        → ProductItem.estimatedDaysRemaining = resolved value
        → productRepository.save()
        → Flyway migration ensures table has estimated_days_remaining column
```

### Module communication (internal interfaces)
```
users module ──interface──→ product module
    (UserNotFoundException)    (depends on interface, not concrete class)

inventory module ──interface──→ scan module
    (ScanResult)                 (ScanEventPublisher)
```

## File Changes

| File | Action | Description |
|------|--------|-------------|
| `pom.xml` | Create | Parent POM with Spring Boot 3.2, Java 21, Flyway, Actuator, Lombok |
| `Dockerfile` | Create | Multi-stage build: Maven compile → JAR → Eclipse Temurin 21 JRE |
| `docker-compose.infra.yml` | Create | PostgreSQL 16, MongoDB 7, Redis 7, Kafka KRaft single-node |
| `docker-compose.app.yml` | Create | Spring Boot app with depends_on health checks |
| `src/main/java/com/despensia/DespensiaApplication.java` | Create | @SpringBootApplication entry point |
| `src/main/java/com/despensia/config/` | Create | KafkaConfig, FlywayConfig, RedisConfig |
| `src/main/java/com/despensia/users/` | Create | Module skeleton (api, service, domain, repository) |
| `src/main/java/com/despensia/product/` | Create | Module skeleton + ProductItem entity + expiration strategies |
| `src/main/java/com/despensia/inventory/` | Create | Module skeleton |
| `src/main/java/com/despensia/scan/` | Create | Module skeleton |
| `src/main/resources/application.yml` | Create | Profiles: default, docker; datasource, jpa, kafka, mongo, redis config |
| `src/main/resources/db/migration/V1__init_schema.sql` | Create | Core schema: users, product, inventory, scan tables |
| `src/main/resources/db/migration/V2__product_item_extension.sql` | Create | ProductItem: product_type, estimated_days_remaining |
| `src/main/resources/db/migration/V3__inventory_scan_tables.sql` | Create | InventoryScan, ScanItem tables |
| `.gitignore` | Modify | Add Java/Docker/Maven ignores |

## Interfaces / Contracts

### ProductItem entity
```java
@Entity
@Table(name = "product_item")
public class ProductItem {
    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false)
    private ProductType productType;  // PACKAGED | ORGANIC
    
    @Column(name = "estimated_days_remaining")
    private Integer estimatedDaysRemaining;  // nullable, user-editable
    
    @Column(name = "expiration_date")
    private LocalDate expirationDate;  // computed from estimatedDaysRemaining
    
    @Column(name = "expiration_source")
    private ExpirationSource source;  // CONSERVATIVE_AVERAGE | AI_ESTIMATE | USER_OVERRIDE
    
    @Column(name = "is_expired")
    private Boolean isExpired;
    
    @CreationTimestamp
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
}

public enum ProductType { PACKAGED, ORGANIC }
public enum ExpirationSource { CONSERVATIVE_AVERAGE, AI_ESTIMATE, USER_OVERRIDE }
```

### Health check contract
```
GET /actuator/health → {"status": "UP", "components": {
  "db": {"status": "UP"},
  "kafka": {"status": "UP"},
  "mongo": {"status": "UP"},
  "redis": {"status": "UP"}
}}
```

## Testing Strategy

| Layer | What to Test | Approach |
|-------|-------------|----------|
| Unit | ProductItem expiration strategy resolution | JUnit 5 + Mockito; test each strategy returns correct default |
| Unit | Flyway migration SQL validity | Test each migration is syntactically valid against PostgreSQL |
| Integration | Docker compose infra services start | Testcontainers for PostgreSQL, Kafka in CI |
| Integration | Spring Boot app starts with all health checks | `@SpringBootTest` + `TestRestTemplate` → /actuator/health |
| E2E | ProductItem CRUD with expiration default | Testcontainers + `@DataJpaTest` + integration test |

## Threat Matrix

**N/A** — No routing, shell, subprocess, VCS/PR automation, executable-file classification, or process-integration boundary. This is a greenfield infrastructure scaffold.

## Migration / Rollout

**No data migration required** — greenfield project. Flyway migrations are schema-only (V1 → V3). Rollback: `docker compose -f docker-compose.infra.yml down -v` deletes all volumes.

## Open Questions

- [ ] Maven or Gradle? Proposal mentions both — need decision before implementation
- [ ] Should `estimatedDaysRemaining` trigger a Kafka event on change? (Out of scope per proposal, but worth noting for next phase)
- [ ] MongoDB use case for phase 1? Proposal lists it in infra but no spec mentions it — confirm if needed now or deferred
