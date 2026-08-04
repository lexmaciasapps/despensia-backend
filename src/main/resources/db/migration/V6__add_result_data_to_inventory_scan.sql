-- V6: Add JSONB result_data column to inventory_scan for storing scan results
-- Persists heterogeneous PRODUCT/RECEIPT data as raw JSON blob.

ALTER TABLE inventory_scan ADD COLUMN IF NOT EXISTS result_data TEXT DEFAULT NULL;
