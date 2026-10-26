-- Migration V1_8_0: Add GST mode, CGST/SGST rates and component amounts
-- AnnaDhara POS — GST Change #1
-- Target Schema: PostgreSQL version 1.7.0 -> 1.8.0

SET lock_timeout = '10s';
SET statement_timeout = '30s';

-- 1. Preflight In-Script Safeguard
DO $$
DECLARE
    v_schema TEXT;
    v_target_col_count INTEGER;
    v_conflict_count INTEGER;
    v_negative_count INTEGER;
BEGIN
    -- Determine active schema explicitly
    SELECT current_schema() INTO v_schema;
    IF v_schema IS NULL THEN
        v_schema := 'public';
    END IF;

    -- Count existing target columns in active schema
    SELECT COUNT(*) INTO v_target_col_count
    FROM information_schema.columns
    WHERE table_schema = v_schema
      AND (
          (table_name = 'restaurant_settings' AND column_name IN ('gst_mode', 'cgst_rate', 'sgst_rate'))
          OR (table_name = 'orders' AND column_name IN ('gst_mode', 'cgst_rate', 'sgst_rate', 'cgst_amount', 'sgst_amount'))
          OR (table_name = 'bills' AND column_name IN ('gst_mode', 'cgst_rate', 'sgst_rate', 'cgst_amount', 'sgst_amount'))
      );

    -- Reject partial schema drift (1 to 12 columns exist)
    IF v_target_col_count > 0 AND v_target_col_count < 13 THEN
        RAISE EXCEPTION 'Migration V1_8_0 halted: Partial schema drift detected (% of 13 target columns exist in schema %). Aborting before applying DDL.', v_target_col_count, v_schema;
    END IF;

    -- If target columns already exist, inspect rates for consistency before altering
    IF v_target_col_count = 13 THEN
        -- Check for negative or out-of-range rates (>50%)
        SELECT COUNT(*) INTO v_negative_count
        FROM restaurant_settings
        WHERE cgst_rate < 0.00 OR sgst_rate < 0.00 OR cgst_rate > 50.00 OR sgst_rate > 50.00;

        IF v_negative_count > 0 THEN
            RAISE EXCEPTION 'Migration V1_8_0 halted: Invalid pre-existing GST rates in restaurant_settings (% rows have negative or >50%% rates).', v_negative_count;
        END IF;

        -- Check for rate sum discrepancy when either rate is populated
        SELECT COUNT(*) INTO v_conflict_count
        FROM restaurant_settings
        WHERE (cgst_rate > 0.00 OR sgst_rate > 0.00)
          AND (cgst_rate + sgst_rate) != default_tax_rate;

        IF v_conflict_count > 0 THEN
            RAISE EXCEPTION 'Migration V1_8_0 halted: Pre-existing rate discrepancy in restaurant_settings (% rows have cgst_rate + sgst_rate != default_tax_rate).', v_conflict_count;
        END IF;
    END IF;
END $$;

-- 2. DDL Alterations (O(1) catalog operations in PostgreSQL 11+)
ALTER TABLE restaurant_settings
    ADD COLUMN IF NOT EXISTS gst_mode VARCHAR(20) NOT NULL DEFAULT 'GST_EXCLUDED',
    ADD COLUMN IF NOT EXISTS cgst_rate NUMERIC(5,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS sgst_rate NUMERIC(5,2) NOT NULL DEFAULT 0.00;

ALTER TABLE orders
    ADD COLUMN IF NOT EXISTS gst_mode VARCHAR(20),
    ADD COLUMN IF NOT EXISTS cgst_rate NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS sgst_rate NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS cgst_amount NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS sgst_amount NUMERIC(10,2) NOT NULL DEFAULT 0.00;

ALTER TABLE bills
    ADD COLUMN IF NOT EXISTS gst_mode VARCHAR(20),
    ADD COLUMN IF NOT EXISTS cgst_rate NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS sgst_rate NUMERIC(5,2),
    ADD COLUMN IF NOT EXISTS cgst_amount NUMERIC(10,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN IF NOT EXISTS sgst_amount NUMERIC(10,2) NOT NULL DEFAULT 0.00;

-- 3. Pre-Backfill Integrity Safeguard
DO $$
DECLARE
    v_invalid_rates INTEGER;
    v_conflict_rates INTEGER;
BEGIN
    -- Ensure default_tax_rate is not negative
    SELECT COUNT(*) INTO v_invalid_rates
    FROM restaurant_settings
    WHERE default_tax_rate < 0.00;

    IF v_invalid_rates > 0 THEN
        RAISE EXCEPTION 'Migration V1_8_0 halted: % restaurant_settings rows have negative default_tax_rate.', v_invalid_rates;
    END IF;

    -- Fail-closed on conflicting pre-existing component rates
    SELECT COUNT(*) INTO v_conflict_rates
    FROM restaurant_settings
    WHERE (cgst_rate != 0.00 OR sgst_rate != 0.00)
      AND (cgst_rate + sgst_rate) != default_tax_rate;

    IF v_conflict_rates > 0 THEN
        RAISE EXCEPTION 'Migration V1_8_0 halted: Conflicting pre-existing rates detected (% rows). Aborting backfill.', v_conflict_rates;
    END IF;
END $$;

-- 4. Deterministic Backfill for Uninitialized Settings Rows
-- Applies half-rounded split ensuring CGST + SGST == default_tax_rate exactly down to 0.01%
-- Preserves tax_enabled and default_tax_rate untouched
UPDATE restaurant_settings
SET 
    cgst_rate = ROUND(default_tax_rate / 2.0, 2),
    sgst_rate = default_tax_rate - ROUND(default_tax_rate / 2.0, 2),
    gst_mode = 'GST_EXCLUDED'
WHERE cgst_rate = 0.00 
  AND sgst_rate = 0.00 
  AND default_tax_rate > 0.00;

-- 5. Post-Backfill Invariant Validation
DO $$
DECLARE
    v_sum_mismatch_count INTEGER;
    v_negative_count INTEGER;
BEGIN
    -- Verify every row satisfies cgst_rate + sgst_rate == default_tax_rate
    SELECT COUNT(*) INTO v_sum_mismatch_count
    FROM restaurant_settings
    WHERE (cgst_rate + sgst_rate) != default_tax_rate;

    IF v_sum_mismatch_count > 0 THEN
        RAISE EXCEPTION 'Migration V1_8_0 post-backfill check failed: % rows have cgst_rate + sgst_rate != default_tax_rate.', v_sum_mismatch_count;
    END IF;

    -- Verify no negative rates exist
    SELECT COUNT(*) INTO v_negative_count
    FROM restaurant_settings
    WHERE cgst_rate < 0.00 OR sgst_rate < 0.00;

    IF v_negative_count > 0 THEN
        RAISE EXCEPTION 'Migration V1_8_0 post-backfill check failed: % rows have negative CGST or SGST rates.', v_negative_count;
    END IF;
END $$;
