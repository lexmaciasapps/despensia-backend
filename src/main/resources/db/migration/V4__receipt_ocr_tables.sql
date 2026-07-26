-- V4: Receipt OCR tables + product_item.receipt_id column
-- Supports receipt scanning workflow: receipt → line items → products.

-- Add receipt_id column to product_item for linking products extracted from receipts
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'product_item' AND column_name = 'receipt_id'
    ) THEN
        ALTER TABLE product_item ADD COLUMN receipt_id UUID;
    END IF;
END $$;

-- Receipt entity table
CREATE TABLE IF NOT EXISTS receipt (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    scan_id UUID REFERENCES inventory_scan(id),
    "date" DATE,
    store_name VARCHAR(255),
    total_amount NUMERIC(10, 2),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

-- Receipt line items
CREATE TABLE IF NOT EXISTS receipt_line_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    receipt_id UUID NOT NULL REFERENCES receipt(id) ON DELETE CASCADE,
    product_id VARCHAR(255),
    name VARCHAR(255) NOT NULL,
    quantity INTEGER NOT NULL,
    price NUMERIC(10, 2) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);
