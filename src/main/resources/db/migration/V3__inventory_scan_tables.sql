-- V3: InventoryScan and ScanItem tables
-- Flyway migration for scan module domain.

CREATE TABLE IF NOT EXISTS inventory_scan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    inventory_item_id UUID NOT NULL REFERENCES inventory_item(id) ON DELETE CASCADE,
    scan_result_id UUID REFERENCES scan_result(id),
    scanned_by UUID REFERENCES "user"(id),
    scan_type VARCHAR(50) NOT NULL DEFAULT 'MANUAL',
    scanned_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS scan_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_result_id UUID NOT NULL REFERENCES scan_result(id) ON DELETE CASCADE,
    product_id UUID REFERENCES product_item(id),
    detected_quantity INTEGER,
    confidence_score FLOAT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
