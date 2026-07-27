# Tasks: Fase 2/3 — Domain Models, Ports & Use Cases

## Phase 1: Domain Entities

- [x] 1.1 Create `InventoryScan` entity with ScanType (PRODUCT/RECEIPT) and ScanStatus (PENDING/PROCESSING/COMPLETED/FAILED) enums
- [x] 1.2 Add JPA lifecycle annotations (@Entity, @Table, UUID id via GenerationType.UUID, CreationTimestamp, UpdateTimestamp)
- [x] 1.3 Create `Receipt` entity with date, storeName, totalAmount fields and @ManyToOne(LAZY) link to InventoryScan via scan_id FK
- [x] 1.4 Create `ReceiptLineItem` entity with name, quantity, price fields and @ManyToOne(LAZY) link to Receipt via receipt_id FK
- [x] 1.5 Add @OneToMany(mappedBy = "inventoryScan", cascade = CascadeType.ALL, orphanRemoval = true) on InventoryScan.lineItems

## Phase 2: Product Item Extension

- [x] 2.1 Add nullable `receiptId` column to `ProductItem` entity
- [x] 2.2 Document receiptId purpose as source tracking for OCR-extracted products

## Phase 3: Port Interfaces (Hexagonal Architecture)

- [x] 3.1 Create `ProductScanningPort` interface with scan(InventoryScan) → ProductItem method
- [x] 3.2 Create `ReceiptParserPort` interface with parse(InventoryScan) → Receipt method

## Phase 4: Use Case Orchestration

- [x] 4.1 Implement `ProcessImageUseCase` with switch expression on ScanType (RECEIPT vs PRODUCT)
- [x] 4.2 Wire status transitions PENDING → PROCESSING → COMPLETED/FAILED in use case flow

## Phase 5: Stub Infrastructure Adapters

- [x] 5.1 Implement `LmStudioProductScanner` as @Component implementing ProductScanningPort (stub with TODO parsing)
- [x] 5.2 Use WebClient to call LM Studio /v1/chat/completions endpoint with base64 image payload
- [x] 5.3 Add extractJson helper for markdown code block extraction in scanner response
- [x] 5.4 Implement `LmStudioReceiptParser` as @Component implementing ReceiptParserPort (stub with TODO parsing)

## Phase 6: Database Migration

- [x] 6.1 Create V3__inventory_scan_tables.sql Flyway migration for inventory_scan table
- [x] 6.2 Ensure schema alignment with InventoryScan entity fields and enum types

## Phase 7: Build Fixes & Convention Alignment

- [x] 7.1 Replace Lombok annotations with manual getters/setters in all new Fase 2/3 files (annotation processing was broken)
- [x] 7.2 Verify compilation passes without errors or warnings
- [x] 7.3 Ensure enum fields use EnumType.STRING for proper PostgreSQL storage
