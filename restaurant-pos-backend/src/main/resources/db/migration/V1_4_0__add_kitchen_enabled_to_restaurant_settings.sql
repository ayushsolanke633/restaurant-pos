-- Migration V1_4_0: Add persistent kitchen_enabled setting to restaurant_settings table
-- Defaults to TRUE for existing and new restaurants to preserve KDS workflow by default.

ALTER TABLE restaurant_settings 
ADD COLUMN IF NOT EXISTS kitchen_enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Enforce payment idempotency: prevent duplicate successful payments for the same bill
CREATE UNIQUE INDEX IF NOT EXISTS uk_payments_bill_id_paid ON payments (bill_id) WHERE payment_status = 'PAID';
