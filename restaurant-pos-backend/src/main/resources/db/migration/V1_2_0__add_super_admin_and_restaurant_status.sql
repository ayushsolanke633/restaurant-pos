-- ==============================================================================
-- Flyway Migration: V1_2_0__add_super_admin_and_restaurant_status.sql
-- Description: Introduces platform-level SUPER_ADMIN role, restaurant status
--              (is_active) tracking for tenant lifecycle management, allows
--              nullable restaurant_id for platform administrators, and enforces
--              a PostgreSQL trigger ensuring role-tenant relational integrity.
--
-- IMPORTANT SAFETY NOTE:
-- This migration is created in advance and must NOT be applied to the live
-- restaurant_pos database until explicit user approval is granted.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- STEP 1: ADD RESTAURANT STATUS COLUMN (IS_ACTIVE)
-- ------------------------------------------------------------------------------
ALTER TABLE restaurants
ADD COLUMN IF NOT EXISTS is_active BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_restaurants_is_active ON restaurants(is_active);

-- ------------------------------------------------------------------------------
-- STEP 2: SEED SUPER_ADMIN ROLE (IDEMPOTENT)
-- ------------------------------------------------------------------------------
INSERT INTO roles (name)
SELECT 'SUPER_ADMIN'
WHERE NOT EXISTS (
    SELECT 1 FROM roles WHERE name = 'SUPER_ADMIN'
);

-- ------------------------------------------------------------------------------
-- STEP 3: RELAX RESTAURANT_ID NULLABILITY ON USERS TABLE
-- Platform-level administrators have no tenant association (restaurant_id IS NULL).
-- ------------------------------------------------------------------------------
ALTER TABLE users ALTER COLUMN restaurant_id DROP NOT NULL;

-- ------------------------------------------------------------------------------
-- STEP 4: POSTGRESQL ROLE-TENANT INTEGRITY TRIGGER
--
-- Relational Integrity Rules:
-- 1. If role is 'SUPER_ADMIN', restaurant_id MUST be NULL.
-- 2. If role is any tenant role (ADMIN, WAITER, KITCHEN, etc.), restaurant_id MUST NOT be NULL.
-- 3. If role_id does not exist in roles table, abort with clear error.
--
-- Handled Scenarios:
-- - New user insertion (INSERT)
-- - Role change or restaurant assignment change (UPDATE)
-- - Concurrency safe (executes inside row-level transaction)
-- - Compatible with all existing database records
-- ------------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION trg_check_user_tenant_role()
RETURNS TRIGGER AS $$
DECLARE
    v_role_name VARCHAR(255);
BEGIN
    -- 1. Verify that referenced role exists
    SELECT name INTO v_role_name FROM roles WHERE id = NEW.role_id;
    IF v_role_name IS NULL THEN
        RAISE EXCEPTION 'ROLE_TENANT_INTEGRITY_VIOLATION: Role ID % does not exist.', NEW.role_id;
    END IF;

    -- 2. Platform SUPER_ADMIN must not be bound to any tenant restaurant
    IF v_role_name = 'SUPER_ADMIN' THEN
        IF NEW.restaurant_id IS NOT NULL THEN
            RAISE EXCEPTION 'ROLE_TENANT_INTEGRITY_VIOLATION: Platform SUPER_ADMIN user cannot have a restaurant_id (must be NULL). Got restaurant_id: %', NEW.restaurant_id;
        END IF;
    -- 3. All other roles are tenant-scoped and must have a valid restaurant_id
    ELSE
        IF NEW.restaurant_id IS NULL THEN
            RAISE EXCEPTION 'ROLE_TENANT_INTEGRITY_VIOLATION: Tenant user with role % must have a valid non-null restaurant_id.', v_role_name;
        END IF;
    END IF;

    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS trg_users_tenant_role ON users;

CREATE TRIGGER trg_users_tenant_role
BEFORE INSERT OR UPDATE OF role_id, restaurant_id ON users
FOR EACH ROW
EXECUTE FUNCTION trg_check_user_tenant_role();

-- ------------------------------------------------------------------------------
-- STEP 5: PERFORMANCE INDEXES
-- ------------------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS idx_users_role_id ON users(role_id);
CREATE INDEX IF NOT EXISTS idx_users_restaurant_role ON users(restaurant_id, role_id);
