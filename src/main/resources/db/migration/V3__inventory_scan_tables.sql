-- V3: InventoryScan table — replaces legacy scan_result reference
-- Flyway migration for scan module domain.
-- Aligns with InventoryScan entity (scan_type enum: PRODUCT/RECEIPT).

CREATE TABLE IF NOT EXISTS inventory_scan (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_type VARCHAR(50) NOT NULL DEFAULT 'PRODUCT',
    image_path TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
