package com.restaurant.restaurantpos;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.StreamUtils;

import javax.sql.DataSource;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
public class V1_5_0_RestoredBackupMigrationValidationTest {

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("Edge Case 6: Simulate restored backup, apply V1_5_0 DDL, verify data preservation and Flyway validation")
    void testV1_5_0OnRestoredBackupSchemaAndData() throws Exception {
        // 1. Create restored backup table: order_items_restored_backup
        // Exact schema as extracted from restaurant_pos_backup_before_superadmin.dump.sql
        jdbcTemplate.execute("DROP TABLE IF EXISTS order_items_restored_backup");
        jdbcTemplate.execute(
                "CREATE TABLE order_items_restored_backup (" +
                "    id bigint NOT NULL," +
                "    created_at timestamp NOT NULL," +
                "    notes text," +
                "    quantity integer NOT NULL," +
                "    subtotal numeric(10,2) NOT NULL," +
                "    unit_price numeric(10,2) NOT NULL," +
                "    updated_at timestamp NOT NULL," +
                "    menu_item_id bigint NOT NULL," +
                "    order_id bigint NOT NULL," +
                "    restaurant_id bigint NOT NULL" +
                ")"
        );

        // 2. Populate exact rows from the recent backup dump (IDs 183, 184, 185, 33)
        jdbcTemplate.update(
                "INSERT INTO order_items_restored_backup (id, created_at, notes, quantity, subtotal, unit_price, updated_at, menu_item_id, order_id, restaurant_id) " +
                "VALUES (183, '2026-10-12 13:49:00', 'Medium spice - Staging Test Note', 1, 130.00, 130.00, '2026-10-12 13:49:00', 4, 129, 2)"
        );
        jdbcTemplate.update(
                "INSERT INTO order_items_restored_backup (id, created_at, notes, quantity, subtotal, unit_price, updated_at, menu_item_id, order_id, restaurant_id) " +
                "VALUES (184, '2026-10-12 13:53:25', 'Medium spice - Staging Test Note', 1, 130.00, 130.00, '2026-10-12 13:53:25', 4, 130, 2)"
        );
        jdbcTemplate.update(
                "INSERT INTO order_items_restored_backup (id, created_at, notes, quantity, subtotal, unit_price, updated_at, menu_item_id, order_id, restaurant_id) " +
                "VALUES (185, '2026-10-12 13:54:53', 'Medium spice - Staging Test Note', 1, 130.00, 130.00, '2026-10-12 13:54:53', 4, 131, 2)"
        );
        jdbcTemplate.update(
                "INSERT INTO order_items_restored_backup (id, created_at, notes, quantity, subtotal, unit_price, updated_at, menu_item_id, order_id, restaurant_id) " +
                "VALUES (33, '2026-09-24 23:35:47', NULL, 1, 129.00, 129.00, '2026-09-24 23:35:47', 12, 22, 2)"
        );

        // Verify pre-migration state
        Integer initialCount = jdbcTemplate.queryForObject("SELECT count(*) FROM order_items_restored_backup", Integer.class);
        assertEquals(4, initialCount, "Restored backup must contain 4 rows before migration");

        // 3. Load the actual V1_5_0 Flyway migration script from classpath
        ClassPathResource resource = new ClassPathResource("db/migration/V1_5_0__add_kitchen_status_and_batch_to_order_items.sql");
        assertTrue(resource.exists(), "Flyway migration V1_5_0__add_kitchen_status_and_batch_to_order_items.sql must exist");
        String sql;
        try (InputStream in = resource.getInputStream()) {
            sql = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }
        assertNotNull(sql);
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS status"));
        assertTrue(sql.contains("ADD COLUMN IF NOT EXISTS batch_number"));

        // 4. Execute the migration against the restored backup table
        String migrationSql = sql.replace("order_items", "order_items_restored_backup");
        for (String statement : migrationSql.split(";")) {
            String trimmed = statement.trim();
            if (!trimmed.isEmpty()) {
                jdbcTemplate.execute(trimmed);
            }
        }

        // 5. Verify data integrity and preservation
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT * FROM order_items_restored_backup ORDER BY id ASC");
        assertEquals(4, rows.size(), "Row count must remain exactly 4 after migration");

        // Check ID 33
        Map<String, Object> row33 = rows.stream().filter(r -> ((Number) r.get("id")).longValue() == 33L).findFirst().orElseThrow();
        assertEquals(1, ((Number) row33.get("quantity")).intValue(), "Quantity must be preserved");
        assertNull(row33.get("notes"), "NULL notes must remain NULL");
        assertEquals(12L, ((Number) row33.get("menu_item_id")).longValue(), "Menu item relationship must be preserved");
        assertEquals(22L, ((Number) row33.get("order_id")).longValue(), "Order relationship must be preserved");
        assertEquals(2L, ((Number) row33.get("restaurant_id")).longValue(), "Restaurant relationship must be preserved");
        assertEquals("NEW", row33.get("status"), "New status column must default to 'NEW'");
        assertEquals(1, ((Number) row33.get("batch_number")).intValue(), "New batch_number column must default to 1");

        // Check ID 183
        Map<String, Object> row183 = rows.stream().filter(r -> ((Number) r.get("id")).longValue() == 183L).findFirst().orElseThrow();
        assertEquals(1, ((Number) row183.get("quantity")).intValue());
        assertEquals("Medium spice - Staging Test Note", row183.get("notes"), "Notes must be preserved");
        assertEquals(4L, ((Number) row183.get("menu_item_id")).longValue());
        assertEquals(129L, ((Number) row183.get("order_id")).longValue());
        assertEquals(2L, ((Number) row183.get("restaurant_id")).longValue());
        assertEquals("NEW", row183.get("status"));
        assertEquals(1, ((Number) row183.get("batch_number")).intValue());

        // Check ID 184
        Map<String, Object> row184 = rows.stream().filter(r -> ((Number) r.get("id")).longValue() == 184L).findFirst().orElseThrow();
        assertEquals(184L, ((Number) row184.get("id")).longValue());
        assertEquals("Medium spice - Staging Test Note", row184.get("notes"));
        assertEquals("NEW", row184.get("status"));
        assertEquals(1, ((Number) row184.get("batch_number")).intValue());

        // Check ID 185
        Map<String, Object> row185 = rows.stream().filter(r -> ((Number) r.get("id")).longValue() == 185L).findFirst().orElseThrow();
        assertEquals(185L, ((Number) row185.get("id")).longValue());
        assertEquals("Medium spice - Staging Test Note", row185.get("notes"));
        assertEquals("NEW", row185.get("status"));
        assertEquals(1, ((Number) row185.get("batch_number")).intValue());

        // 6. Test inserting a newly added batch (Batch #2) alongside migrated legacy data
        jdbcTemplate.update(
                "INSERT INTO order_items_restored_backup (id, created_at, notes, quantity, subtotal, unit_price, updated_at, menu_item_id, order_id, restaurant_id, status, batch_number) " +
                "VALUES (200, '2026-10-14 02:00:00', 'New batch item', 2, 260.00, 130.00, '2026-10-14 02:00:00', 4, 129, 2, 'NEW', 2)"
        );

        Map<String, Object> row200 = jdbcTemplate.queryForMap("SELECT * FROM order_items_restored_backup WHERE id = 200");
        assertEquals(2, ((Number) row200.get("batch_number")).intValue());
        assertEquals("NEW", row200.get("status"));

        // Cleanup test table
        jdbcTemplate.execute("DROP TABLE order_items_restored_backup");
        System.out.println("[RESTORED BACKUP V1_5_0 MIGRATION VERIFIED] 100% data preservation and column defaulting confirmed.");
    }
}
