# Design: Fase 2/3 — Domain Models, Ports & Use Cases

## Architecture Decisions

### AD-1: JPA @Entity on Domain Classes (not Pure POJOs)

**Decision:** Keep Spring Data JPA annotations (@Entity, @Table, etc.) directly on domain classes.

**Rationale:** User explicitly chose to skip "Pure Domain" layer and keep Spring annotations for pragmatic development speed. This trades strict hexagonal purity for simplicity — the entities double as both ORM mappings and domain models. The port interfaces still provide a boundary that can be swapped later if pure domain separation becomes necessary.

### AD-2: Dual Scan Type (PRODUCT + RECEIPT)

**Decision:** InventoryScan supports two scan types via enum, with separate OCR pipelines for each.

**Rationale:** Receipt OCR was added alongside Product Photos to handle both use cases from a single entry point. The ProcessImageUseCase orchestrator uses a switch expression on ScanType to delegate to the correct parser/processor port. This avoids duplicating the scanning lifecycle (status transitions, error handling) across two separate entities.

### AD-3: Lombok → Manual Getters/Setters Migration

**Decision:** All new domain classes in Fase 2/3 use manual getters/setters instead of Lombok annotations (@Getter, @Setter).

**Rationale:** Lombok annotation processing was broken during implementation — likely a Maven compiler plugin / Java 21 compatibility issue. Rather than debugging the build setup mid-phase, manual accessors were written for reliability and clarity. Existing files that already had Lombok (from Fase 1) are left unchanged to avoid unnecessary churn.

### AD-4: Hexagonal Ports as Spring Interfaces

**Decision:** Port interfaces (ProductScanningPort, ReceiptParserPort) are plain Java interfaces without @Component or @Service annotations. Implementations carry the stereotypes.

**Rationale:** This maintains strict hexagonal architecture — ports define contracts but don't know about DI containers. The implementations (@Component classes) wire themselves to Spring's context, while the use case depends only on the port interface (injected via constructor).

### AD-5: ReceiptLineItem @OneToMany Orphan Removal

**Decision:** InventoryScan.lineItems uses CascadeType.ALL with orphanRemoval = true.

**Rationale:** Line items are always created in the same transaction as their parent scan and should be deleted when the scan is deleted. This ensures referential integrity without manual cleanup code. The inverse side (ReceiptLineItem.inventoryScan) maps via @ManyToOne(fetch = LAZY).

### AD-6: Stub Infrastructure Adapters

**Decision:** LmStudioProductScanner and LmStudioReceiptParser are implemented but with TODO parsing logic — they log input and return dummy data rather than performing real AI inference.

**Rationale:** Real LM Studio integration is deferred to Phase 5+ when the local ML stack is confirmed working. The stubs allow the domain layer, ports, and use case orchestration to be tested independently of external services. They also serve as a template for the production implementation.

## Domain Model Relationships

```
InventoryScan (1) ──── (*) ReceiptLineItem
       │
  @ManyToOne(fetch=LAZY)
       ▼
    Receipt (optional — created from OCR, not always persisted separately)

ProductItem
   ├── receiptId: String (nullable FK to source receipt)
   └── expirationSource: enum (CONSERVATIVE_AVERAGE | AI_ESTIMATE | USER_OVERRIDE)
```

## Package Structure

```
com.despensia.scan/
├── domain/
│   ├── InventoryScan.java       — JPA entity + ScanType/ScanStatus enums
│   ├── Receipt.java             — OCR result entity (ManyToOne to InventoryScan)
│   └── ReceiptLineItem.java     — Line items within a receipt (@OneToMany from InventoryScan)
├── service/
│   ├── ProductScanningPort.java  — Hexagonal port interface
│   ├── ReceiptParserPort.java    — Hexagonal port interface
│   └── ProcessImageUseCase.java  — Orchestration use case
└── infrastructure/
    ├── LmStudioProductScanner.java   — Stub implementation of ProductScanningPort
    └── LmStudioReceiptParser.java    — Stub implementation of ReceiptParserPort
```

## Why This Design Works for a Modular Monolith

1. **Clear boundaries:** Ports define what each module needs; adapters implement how it's done externally.
2. **Testable core:** Domain models and use cases have no Spring dependencies (except JPA annotations on entities).
3. **Extensible scanning:** New scan types can be added by creating a new enum value + port implementation without touching existing code.
4. **Deferred external deps:** Stub adapters let the domain layer develop independently of LM Studio availability.
