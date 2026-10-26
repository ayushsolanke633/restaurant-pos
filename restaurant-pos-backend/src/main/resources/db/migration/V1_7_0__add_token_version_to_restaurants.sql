-- Migration V1_7_0: Add token_version to restaurants for tenant-wide JWT revocation
-- AnnaDhara P0-3 Stage 3O
--
-- Safety & Concurrency Considerations:
-- 1. In PostgreSQL 11+, adding a column with DEFAULT and NOT NULL is an O(1) catalog update
--    without a full table rewrite or long table locks.
-- 2. IF NOT EXISTS ensures idempotency if partially created in non-prod dry runs.
-- 3. Any existing restaurant rows automatically receive token_version = 1.
-- 4. Safe regardless of whether V1_6_0 has been applied, as it only alters the restaurants table.
--
-- Gate Status: DRAFT / UNAPPROVED. This migration is NOT added to MigrationApprovalGate allowlist
-- and must NOT be executed until formal sign-off.

ALTER TABLE restaurants
ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 1;
