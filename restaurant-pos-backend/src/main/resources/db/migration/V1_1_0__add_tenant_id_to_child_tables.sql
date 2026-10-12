-- ==============================================================================
-- Flyway Migration: V1_1_0__add_tenant_id_to_child_tables.sql
-- Description: Adds direct tenant ownership (restaurant_id) to child tables,
--              backfills existing records, validates relational integrity,
--              and applies composite unique/foreign keys and indexes.
--
-- STATUS: DRAFT ONLY — NOT YET AUTHORIZED FOR EXECUTION
--
-- ARCHITECTURAL RULES:
-- 1. Fail-fast without data mutation: Aborts with a detailed exception if any
--    orphaned record, unresolvable tenant, or cross-tenant relationship is detected.
-- 2. Foreign-key delete behavior: Preserves existing schema behavior (ON DELETE NO ACTION).
-- 3. Composite integrity: Requires parent composite unique keys before adding
--    composite foreign keys on child tables.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- STEP 1: PRE-MIGRATION INTEGRITY ASSERTIONS (FAIL-FAST WITHOUT DATA MUTATION)
-- ------------------------------------------------------------------------------
DO $$
DECLARE
    v_orphaned_order_items INT;
    v_orphaned_payments INT;
    v_orphaned_inv_tx INT;
    v_orphaned_history INT;
    v_duplicate_tables INT;
    v_mismatched_menu_categories INT;
    v_mismatched_bill_orders INT;
    v_mismatched_order_menu_items INT;
    v_mismatched_order_tables INT;
    v_mismatched_order_customers INT;
    v_mismatched_order_waiters INT;
BEGIN
    -- 1a. Check for orphaned order_items
    SELECT count(*) INTO v_orphaned_order_items
    FROM order_items oi
    LEFT JOIN orders o ON oi.order_id = o.id
    WHERE o.id IS NULL;

    IF v_orphaned_order_items > 0 THEN
        RAISE EXCEPTION 'PRE-MIGRATION FAILED: Found % orphaned order_items without a valid parent order.', v_orphaned_order_items;
    END IF;

    -- 1b. Check for orphaned payments
    SELECT count(*) INTO v_orphaned_payments
    FROM payments p
    LEFT JOIN bills b ON p.bill_id = b.id
    WHERE b.id IS NULL;

    IF v_orphaned_payments > 0 THEN
        RAISE EXCEPTION 'PRE-MIGRATION FAILED: Found % orphaned payments without a valid parent bill.', v_orphaned_payments;
    END IF;

    -- 1c. Check for orphaned inventory_transactions
    SELECT count(*) INTO v_orphaned_inv_tx
    FROM inventory_transactions it
    LEFT JOIN inventory_items ii ON it.inventory_item_id = ii.id
    WHERE ii.id IS NULL;

    IF v_orphaned_inv_tx > 0 THEN
        RAISE EXCEPTION 'PRE-MIGRATION FAILED: Found % orphaned inventory_transactions without a valid inventory item.', v_orphaned_inv_tx;
    END IF;

    -- 1d. Check for orphaned order_status_history
    SELECT count(*) INTO v_orphaned_history
    FROM order_status_history osh
    LEFT JOIN orders o ON osh.order_id = o.id
    WHERE o.id IS NULL;

    IF v_orphaned_history > 0 THEN
        RAISE EXCEPTION 'PRE-MIGRATION FAILED: Found % orphaned order_status_history without a valid parent order.', v_orphaned_history;
    END IF;

    -- 1e. Check for duplicate table_number within any restaurant before adding unique constraint
    SELECT count(*) INTO v_duplicate_tables
    FROM (
        SELECT restaurant_id, table_number
        FROM restaurant_tables
        GROUP BY restaurant_id, table_number
        HAVING count(*) > 1
    ) dupes;

    IF v_duplicate_tables > 0 THEN
        RAISE EXCEPTION 'PRE-MIGRATION FAILED: Found % duplicate (restaurant_id, table_number) combinations in restaurant_tables.', v_duplicate_tables;
    END IF;

    -- 1f. Check for cross-tenant menu_item -> category relationships
    SELECT count(*) INTO v_mismatched_menu_categories
    FROM menu_items m
    JOIN categories c ON m.category_id = c.id
    WHERE m.restaurant_id != c.restaurant_id;

    IF v_mismatched_menu_categories > 0 THEN
        RAISE EXCEPTION 'PRE-MIGRATION FAILED: Found % menu_items referencing categories belonging to a different restaurant.', v_mismatched_menu_categories;
    END IF;

    -- 1g. Check for cross-tenant bill -> order relationships
    SELECT count(*) INTO v_mismatched_bill_orders
    FROM bills b
    JOIN orders o ON b.order_id = o.id
    WHERE b.restaurant_id != o.restaurant_id;

    IF v_mismatched_bill_orders > 0 THEN
        RAISE EXCEPTION 'PRE-MIGRATION FAILED: Found % bills referencing orders belonging to a different restaurant.', v_mismatched_bill_orders;
    END IF;

    -- 1h. Check for cross-tenant order_item -> menu_item relationships via order
    SELECT count(*) INTO v_mismatched_order_menu_items
    FROM order_items oi
    JOIN orders o ON oi.order_id = o.id
    JOIN menu_items m ON oi.menu_item_id = m.id
    WHERE o.restaurant_id != m.restaurant_id;

    IF v_mismatched_order_menu_items > 0 THEN
        RAISE EXCEPTION 'PRE-MIGRATION FAILED: Found % order_items where order restaurant_id does not match menu_item restaurant_id.', v_mismatched_order_menu_items;
    END IF;

    -- 1i. Check for cross-tenant order -> table relationships (when table_id is set)
    SELECT count(*) INTO v_mismatched_order_tables
    FROM orders o
    JOIN restaurant_tables rt ON o.table_id = rt.id
    WHERE o.table_id IS NOT NULL AND o.restaurant_id != rt.restaurant_id;

    IF v_mismatched_order_tables > 0 THEN
        RAISE EXCEPTION 'PRE-MIGRATION FAILED: Found % orders referencing tables belonging to a different restaurant.', v_mismatched_order_tables;
    END IF;

    -- 1j. Check for cross-tenant order -> customer relationships (when customer_id is set)
    SELECT count(*) INTO v_mismatched_order_customers
    FROM orders o
    JOIN customers c ON o.customer_id = c.id
    WHERE o.customer_id IS NOT NULL AND o.restaurant_id != c.restaurant_id;

    IF v_mismatched_order_customers > 0 THEN
        RAISE EXCEPTION 'PRE-MIGRATION FAILED: Found % orders referencing customers belonging to a different restaurant.', v_mismatched_order_customers;
    END IF;

    -- 1k. Check for cross-tenant order -> waiter user relationships (when waiter_id is set)
    SELECT count(*) INTO v_mismatched_order_waiters
    FROM orders o
    JOIN users u ON o.waiter_id = u.id
    WHERE o.waiter_id IS NOT NULL AND o.restaurant_id != u.restaurant_id;

    IF v_mismatched_order_waiters > 0 THEN
        RAISE EXCEPTION 'PRE-MIGRATION FAILED: Found % orders referencing waiters belonging to a different restaurant.', v_mismatched_order_waiters;
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- STEP 2: ADD NULLABLE restaurant_id COLUMNS TO CHILD TABLES
-- ------------------------------------------------------------------------------
ALTER TABLE order_items ADD COLUMN restaurant_id BIGINT;
ALTER TABLE payments ADD COLUMN restaurant_id BIGINT;
ALTER TABLE inventory_transactions ADD COLUMN restaurant_id BIGINT;
ALTER TABLE order_status_history ADD COLUMN restaurant_id BIGINT;

-- ------------------------------------------------------------------------------
-- STEP 3: BACKFILL DATA FROM VERIFIED PARENT ENTITIES
-- ------------------------------------------------------------------------------
-- 3a. order_items <- orders
UPDATE order_items oi
SET restaurant_id = o.restaurant_id
FROM orders o
WHERE oi.order_id = o.id AND oi.restaurant_id IS NULL;

-- 3b. payments <- bills
UPDATE payments p
SET restaurant_id = b.restaurant_id
FROM bills b
WHERE p.bill_id = b.id AND p.restaurant_id IS NULL;

-- 3c. inventory_transactions <- inventory_items
UPDATE inventory_transactions it
SET restaurant_id = ii.restaurant_id
FROM inventory_items ii
WHERE it.inventory_item_id = ii.id AND it.restaurant_id IS NULL;

-- 3d. order_status_history <- orders
UPDATE order_status_history osh
SET restaurant_id = o.restaurant_id
FROM orders o
WHERE osh.order_id = o.id AND osh.restaurant_id IS NULL;

-- ------------------------------------------------------------------------------
-- STEP 4: POST-BACKFILL INTEGRITY ASSERTIONS (FAIL-FAST)
-- ------------------------------------------------------------------------------
DO $$
DECLARE
    v_unassigned_order_items INT;
    v_unassigned_payments INT;
    v_unassigned_inv_tx INT;
    v_unassigned_history INT;
    v_mismatched_menu_items INT;
    v_mismatched_bill_orders INT;
    v_mismatched_inv_item_tx INT;
    v_mismatched_history_orders INT;
    v_mismatched_payment_bills INT;
BEGIN
    -- 4a. Verify no nulls remain in child tables
    SELECT count(*) INTO v_unassigned_order_items FROM order_items WHERE restaurant_id IS NULL;
    IF v_unassigned_order_items > 0 THEN
        RAISE EXCEPTION 'POST-BACKFILL FAILED: Found % order_items with NULL restaurant_id.', v_unassigned_order_items;
    END IF;

    SELECT count(*) INTO v_unassigned_payments FROM payments WHERE restaurant_id IS NULL;
    IF v_unassigned_payments > 0 THEN
        RAISE EXCEPTION 'POST-BACKFILL FAILED: Found % payments with NULL restaurant_id.', v_unassigned_payments;
    END IF;

    SELECT count(*) INTO v_unassigned_inv_tx FROM inventory_transactions WHERE restaurant_id IS NULL;
    IF v_unassigned_inv_tx > 0 THEN
        RAISE EXCEPTION 'POST-BACKFILL FAILED: Found % inventory_transactions with NULL restaurant_id.', v_unassigned_inv_tx;
    END IF;

    SELECT count(*) INTO v_unassigned_history FROM order_status_history WHERE restaurant_id IS NULL;
    IF v_unassigned_history > 0 THEN
        RAISE EXCEPTION 'POST-BACKFILL FAILED: Found % order_status_history with NULL restaurant_id.', v_unassigned_history;
    END IF;

    -- 4b. Verify order_item matches parent order restaurant
    SELECT count(*) INTO v_mismatched_history_orders
    FROM order_items oi
    JOIN orders o ON oi.order_id = o.id
    WHERE oi.restaurant_id != o.restaurant_id;

    IF v_mismatched_history_orders > 0 THEN
        RAISE EXCEPTION 'POST-BACKFILL FAILED: Found % order_items with restaurant_id mismatching parent order.', v_mismatched_history_orders;
    END IF;

    -- 4c. Verify menu_item belongs to the exact same restaurant as the order_item
    SELECT count(*) INTO v_mismatched_menu_items
    FROM order_items oi
    JOIN menu_items m ON oi.menu_item_id = m.id
    WHERE oi.restaurant_id != m.restaurant_id;

    IF v_mismatched_menu_items > 0 THEN
        RAISE EXCEPTION 'POST-BACKFILL FAILED: Found % order_items referencing menu_items belonging to a different restaurant.', v_mismatched_menu_items;
    END IF;

    -- 4d. Verify payment belongs to the exact same restaurant as its bill
    SELECT count(*) INTO v_mismatched_payment_bills
    FROM payments p
    JOIN bills b ON p.bill_id = b.id
    WHERE p.restaurant_id != b.restaurant_id;

    IF v_mismatched_payment_bills > 0 THEN
        RAISE EXCEPTION 'POST-BACKFILL FAILED: Found % payments with restaurant_id mismatching parent bill.', v_mismatched_payment_bills;
    END IF;

    -- 4e. Verify inventory_transaction belongs to the exact same restaurant as its item
    SELECT count(*) INTO v_mismatched_inv_item_tx
    FROM inventory_transactions it
    JOIN inventory_items ii ON it.inventory_item_id = ii.id
    WHERE it.restaurant_id != ii.restaurant_id;

    IF v_mismatched_inv_item_tx > 0 THEN
        RAISE EXCEPTION 'POST-BACKFILL FAILED: Found % inventory_transactions with restaurant_id mismatching parent inventory_item.', v_mismatched_inv_item_tx;
    END IF;

    -- 4f. Verify bill belongs to the exact same restaurant as its order
    SELECT count(*) INTO v_mismatched_bill_orders
    FROM bills b
    JOIN orders o ON b.order_id = o.id
    WHERE b.restaurant_id != o.restaurant_id;

    IF v_mismatched_bill_orders > 0 THEN
        RAISE EXCEPTION 'POST-BACKFILL FAILED: Found % bills with restaurant_id mismatching parent order.', v_mismatched_bill_orders;
    END IF;
END $$;

-- ------------------------------------------------------------------------------
-- STEP 5: ENFORCE NOT NULL ON restaurant_id COLUMNS
-- ------------------------------------------------------------------------------
ALTER TABLE order_items ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE payments ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE inventory_transactions ALTER COLUMN restaurant_id SET NOT NULL;
ALTER TABLE order_status_history ALTER COLUMN restaurant_id SET NOT NULL;

-- ------------------------------------------------------------------------------
-- STEP 6: ADD PRIMARY FOREIGN KEYS TO restaurants(id)
-- ------------------------------------------------------------------------------
ALTER TABLE order_items ADD CONSTRAINT fk_order_items_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE NO ACTION;

ALTER TABLE payments ADD CONSTRAINT fk_payments_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE NO ACTION;

ALTER TABLE inventory_transactions ADD CONSTRAINT fk_inventory_transactions_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE NO ACTION;

ALTER TABLE order_status_history ADD CONSTRAINT fk_order_status_history_restaurant
    FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE NO ACTION;

-- ------------------------------------------------------------------------------
-- STEP 7: ADD PARENT COMPOSITE UNIQUE KEYS
-- ------------------------------------------------------------------------------
ALTER TABLE orders ADD CONSTRAINT uk_orders_id_restaurant
    UNIQUE (id, restaurant_id);

ALTER TABLE bills ADD CONSTRAINT uk_bills_id_restaurant
    UNIQUE (id, restaurant_id);

ALTER TABLE menu_items ADD CONSTRAINT uk_menu_items_id_restaurant
    UNIQUE (id, restaurant_id);

ALTER TABLE categories ADD CONSTRAINT uk_categories_id_restaurant
    UNIQUE (id, restaurant_id);

ALTER TABLE inventory_items ADD CONSTRAINT uk_inventory_items_id_restaurant
    UNIQUE (id, restaurant_id);

-- ------------------------------------------------------------------------------
-- STEP 8: ADD CHILD COMPOSITE FOREIGN KEYS (PREVENT CROSS-TENANT ASSOCIATIONS)
-- ------------------------------------------------------------------------------
ALTER TABLE order_items ADD CONSTRAINT fk_order_items_order_tenant
    FOREIGN KEY (order_id, restaurant_id) REFERENCES orders(id, restaurant_id) ON DELETE NO ACTION;

ALTER TABLE order_items ADD CONSTRAINT fk_order_items_menu_item_tenant
    FOREIGN KEY (menu_item_id, restaurant_id) REFERENCES menu_items(id, restaurant_id) ON DELETE NO ACTION;

ALTER TABLE payments ADD CONSTRAINT fk_payments_bill_tenant
    FOREIGN KEY (bill_id, restaurant_id) REFERENCES bills(id, restaurant_id) ON DELETE NO ACTION;

ALTER TABLE order_status_history ADD CONSTRAINT fk_order_status_history_order_tenant
    FOREIGN KEY (order_id, restaurant_id) REFERENCES orders(id, restaurant_id) ON DELETE NO ACTION;

ALTER TABLE menu_items ADD CONSTRAINT fk_menu_items_category_tenant
    FOREIGN KEY (category_id, restaurant_id) REFERENCES categories(id, restaurant_id) ON DELETE NO ACTION;

ALTER TABLE inventory_transactions ADD CONSTRAINT fk_inventory_transactions_item_tenant
    FOREIGN KEY (inventory_item_id, restaurant_id) REFERENCES inventory_items(id, restaurant_id) ON DELETE NO ACTION;

-- ------------------------------------------------------------------------------
-- STEP 9: ADD UNIQUE CONSTRAINT ON (restaurant_id, table_number)
-- ------------------------------------------------------------------------------
ALTER TABLE restaurant_tables ADD CONSTRAINT uk_restaurant_tables_restaurant_table_number
    UNIQUE (restaurant_id, table_number);

-- ------------------------------------------------------------------------------
-- STEP 10: ADD TENANT-SCOPED PERFORMANCE INDEXES
-- ------------------------------------------------------------------------------
CREATE INDEX idx_orders_restaurant_status_created ON orders(restaurant_id, status, created_at DESC);
CREATE INDEX idx_order_items_restaurant_order ON order_items(restaurant_id, order_id);
CREATE INDEX idx_payments_restaurant_bill ON payments(restaurant_id, bill_id);
CREATE INDEX idx_order_status_history_restaurant_order ON order_status_history(restaurant_id, order_id);
CREATE INDEX idx_inventory_transactions_restaurant_item ON inventory_transactions(restaurant_id, inventory_item_id);
CREATE INDEX idx_bills_restaurant_created ON bills(restaurant_id, created_at DESC);
CREATE INDEX idx_restaurant_tables_restaurant_status ON restaurant_tables(restaurant_id, status);
CREATE INDEX idx_menu_items_restaurant_category ON menu_items(restaurant_id, category_id);
CREATE INDEX idx_categories_restaurant_active ON categories(restaurant_id, is_active);
CREATE INDEX idx_customers_restaurant_phone ON customers(restaurant_id, phone);
CREATE INDEX idx_notifications_restaurant_user_read ON notifications(restaurant_id, recipient_user_id, is_read);
