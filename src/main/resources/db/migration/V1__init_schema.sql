-- V1: Core schema — users, product, inventory, scan foundation tables
-- Flyway baseline: V1__init_schema.sql

CREATE TABLE IF NOT EXISTS "user" (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS product_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name VARCHAR(255) NOT NULL,
    product_type VARCHAR(50) NOT NULL,
    estimated_days_remaining INTEGER,
    expiration_date DATE,
    expiration_source VARCHAR(50),
    is_expired BOOLEAN,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS inventory_item (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID NOT NULL REFERENCES product_item(id) ON DELETE CASCADE,
    quantity INTEGER NOT NULL DEFAULT 0,
    unit VARCHAR(20) NOT NULL DEFAULT 'PIECES',
    location VARCHAR(100),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS scan_result (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    product_id UUID REFERENCES product_item(id),
    barcode VARCHAR(255),
    scanned_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    raw_data TEXT
);
