-- Migration: Add PriceQuantity Schedule to Snapshot Store
-- CDM-inspired PriceQuantity field to track quantity and price schedules over time

ALTER TABLE snapshot_store 
ADD COLUMN price_quantity_schedule JSONB;

COMMENT ON COLUMN snapshot_store.price_quantity_schedule IS 
'CDM-inspired PriceQuantity schedule: tracks quantity and price pairs over time. Format: { "schedule": [{"effectiveDate": "...", "quantity": ..., "price": ...}], "unit": "SHARES", "currency": "USD" }';
