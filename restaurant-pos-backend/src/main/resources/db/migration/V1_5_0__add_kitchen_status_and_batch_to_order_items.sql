-- Migration V1_5_0: Add kitchen preparation status and batch_number to order_items
-- Supports incremental kitchen order updates when new items are added to an active order.

ALTER TABLE order_items
ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'NEW';

ALTER TABLE order_items
ADD COLUMN IF NOT EXISTS batch_number INTEGER NOT NULL DEFAULT 1;

CREATE INDEX IF NOT EXISTS idx_order_items_order_batch ON order_items (order_id, batch_number);
