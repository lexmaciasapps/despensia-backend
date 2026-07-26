-- V2: ProductItem extension — ensure estimated_days_remaining and product_type columns
-- This migration is idempotent; safe to re-run.

DO $$
BEGIN
    -- Add estimated_days_remaining if not exists
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'product_item' AND column_name = 'estimated_days_remaining'
    ) THEN
        ALTER TABLE product_item ADD COLUMN estimated_days_remaining INTEGER;
    END IF;

    -- Add product_type if not exists
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'product_item' AND column_name = 'product_type'
    ) THEN
        ALTER TABLE product_item ADD COLUMN product_type VARCHAR(50) NOT NULL DEFAULT 'PACKAGED';
    END IF;

    -- Add expiration_source if not exists
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'product_item' AND column_name = 'expiration_source'
    ) THEN
        ALTER TABLE product_item ADD COLUMN expiration_source VARCHAR(50);
    END IF;

    -- Add expiration_date if not exists
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'product_item' AND column_name = 'expiration_date'
    ) THEN
        ALTER TABLE product_item ADD COLUMN expiration_date DATE;
    END IF;

    -- Add is_expired if not exists
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'product_item' AND column_name = 'is_expired'
    ) THEN
        ALTER TABLE product_item ADD COLUMN is_expired BOOLEAN;
    END IF;
END $$;
