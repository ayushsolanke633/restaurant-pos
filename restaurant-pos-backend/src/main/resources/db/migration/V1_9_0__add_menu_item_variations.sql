-- Migration V1_9_0: Add menu item variations and order item variation support
-- AnnaDhara POS — Menu Item Variations (Client Change 4)
-- Target Schema: PostgreSQL version 1.8.0 -> 1.9.0

-- 1. Create menu_item_variations table
CREATE TABLE IF NOT EXISTS menu_item_variations (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL,
    menu_item_id BIGINT NOT NULL,
    name VARCHAR(100) NOT NULL,
    price NUMERIC(10, 2) NOT NULL CHECK (price >= 0),
    is_available BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_variations_restaurant FOREIGN KEY (restaurant_id) REFERENCES restaurants(id) ON DELETE CASCADE,
    CONSTRAINT fk_variations_menu_item FOREIGN KEY (menu_item_id) REFERENCES menu_items(id) ON DELETE CASCADE,
    CONSTRAINT uk_variation_item_name UNIQUE (menu_item_id, name)
);

CREATE INDEX IF NOT EXISTS idx_variations_item_rest ON menu_item_variations (restaurant_id, menu_item_id);

-- 2. Add variation columns to order_items (nullable for backward compatibility with existing order items)
ALTER TABLE order_items
    ADD COLUMN IF NOT EXISTS variation_id BIGINT NULL,
    ADD COLUMN IF NOT EXISTS variation_name VARCHAR(100) NULL;

-- 3. Foreign key and indexes for order_items variation reference
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'fk_order_items_variation'
    ) THEN
        ALTER TABLE order_items
            ADD CONSTRAINT fk_order_items_variation FOREIGN KEY (variation_id) REFERENCES menu_item_variations(id) ON DELETE SET NULL;
    END IF;
END $$;

CREATE INDEX IF NOT EXISTS idx_order_items_variation ON order_items (variation_id);
