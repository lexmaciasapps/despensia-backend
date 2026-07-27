# Proposal: Fase 2/3 — Domain Models, Ports & Use Cases

## Intent

Define domain models (InventoryScan, Receipt, ProductItem), application ports (ProductScanningPort, ReceiptParserPort), and the ProcessImageUseCase orchestrator. Establish stub infrastructure adapters for LM Studio integration.

## Scope

- InventoryScan entity with ScanType/ScanStatus enums
- Receipt + ReceiptLineItem entities for OCR parsing results
- ProductItem extension with receiptId column
- Port interfaces (ProductScanningPort, ReceiptParserPort)
- ProcessImageUseCase orchestrator with switch on scan type
- LmStudio stubs that log input and return dummy data

## Success Criteria

- [x] InventoryScan entity persists to PostgreSQL via JPA @Entity
- [x] ScanType enum (PRODUCT/RECEIPT) stored as STRING in DB
- [x] ScanStatus enum (PENDING/PROCESSING/COMPLETED/FAILED) with domain transitions
- [x] Receipt + ReceiptLineItem entities linked by @ManyToOne(LAZY) to InventoryScan
- [x] ProductItem has nullable receiptId column for OCR source tracking
- [x] ProductScanningPort interface defined as hexagonal port
- [x] ReceiptParserPort interface defined as hexagonal port
- [x] ProcessImageUseCase orchestrates correct port based on ScanType
- [x] LmStudioProductScanner implements ProductScanningPort (stub with TODO parsing)
- [x] LmStudioReceiptParser implements ReceiptParserPort (stub with TODO parsing)

## Out of Scope

- Real LM Studio integration (deferred to Phase 5+)
- Database migrations for new tables (handled in separate migration V3)
- Full OCR pipeline or image storage infrastructure
- Kafka event domain model (deferred to Fase5)
