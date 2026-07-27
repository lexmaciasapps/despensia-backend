-- V5: Add password_hash and role columns to user table for JWT auth support

ALTER TABLE "user" ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255) DEFAULT NULL;
ALTER TABLE "user" ADD COLUMN IF NOT EXISTS role VARCHAR(50) NOT NULL DEFAULT 'USER';
