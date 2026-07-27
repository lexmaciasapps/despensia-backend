# Spec: Fase 2/3 — Domain Models, Ports & Use Cases

## Requirements

### R1: InventoryScan Entity

**The system MUST** have an InventoryScan entity with ScanType enum (PRODUCT, RECEIPT) and ScanStatus enum (PENDING, PROCESSING, COMPLETED, FAILED).

**Acceptance Criteria:**
- [x] JPA @Entity with UUID id via GenerationType.UUID
- [x] Enum fields using EnumType.STRING for proper DB storage
- [x] image_path column linking to uploaded file
- [x] created_at / updated_at timestamps
- [x] error_message nullable column

### R2: Receipt Entity + Line Items

**The system MUST** have Receipt and ReceiptLineItem entities linked by ManyToOne relationship.

**Acceptance Criteria:**
- [x] Receipt with date, storeName, totalAmount fields
- [x] ReceiptLineItem with name, quantity, price per item
- [x] @ManyToOne(fetch = LAZY) link from Receipt to InventoryScan (scan_id FK)
- [x] @OneToMany(mappedBy = "inventoryScan", cascade = CascadeType.ALL, orphanRemoval = true) on InventoryScan.lineItems

### R3: Product Extension for OCR Results

**The system MUST** support linking scanned products back to their source receipt.

**Acceptance Criteria:**
- [x] ProductItem has nullable receiptId column (String type)
- [x] findByReceiptId query method available or derivable on repository

### R4: Port Interfaces (Hexagonal Architecture)

**The system MUST** define port interfaces for scanning operations.

**Acceptance Criteria:**
- [x] ProductScanningPort interface with scan(InventoryScan) → ProductItem
- [x] ReceiptParserPort interface with parse(InventoryScan) → Receipt

### R5: ProcessImageUseCase Orchestration

**The system MUST** orchestrate scanning via a use case that delegates to the correct port based on ScanType.

**Acceptance Criteria:**
- [x] Switch expression on scan type (RECEIPT vs PRODUCT)
- [x] Proper status transitions PENDING → PROCESSING → COMPLETED/FAILED

### R6: Infrastructure Adapters — Stub Implementations

**The system MUST** provide stub implementations that log input and return dummy data.

**Acceptance Criteria:**
- [x] LmStudioProductScanner implements ProductScanningPort (stub with TODO parsing)
- [x] LmStudioReceiptParser implements ReceiptParserPort (stub with TODO parsing)
- [x] Both use WebClient for HTTP calls to LM Studio endpoint

## Implementation Notes

- Spring @Entity annotations used on all domain classes (not pure POJOs per user decision)
- Lombok annotation processing was broken → replaced with manual getters/setters for new files
- All entities follow the same pattern: UUID id, CreationTimestamp/UpdateTimestamp, JPA lifecycle methods
