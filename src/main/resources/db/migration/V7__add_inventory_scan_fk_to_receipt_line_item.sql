-- Add inventory_scan_id foreign key to receipt_line_item for ReceiptLineItem ↔ InventoryScan relationship
ALTER TABLE receipt_line_item ADD COLUMN inventory_scan_id UUID REFERENCES inventory_scan(id);
