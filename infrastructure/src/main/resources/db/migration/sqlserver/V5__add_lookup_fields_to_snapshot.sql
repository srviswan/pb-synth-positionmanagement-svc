-- Add lookup fields to snapshot_store for efficient querying (MS SQL Server)
-- This migration is already included in V1 for SQL Server, but kept for version consistency

-- Lookup fields (account, instrument, currency, contract_id) are already included 
-- in V1__initial_schema.sql for SQL Server
-- Indexes are also already created in V1
-- This file exists for migration version consistency with PostgreSQL
