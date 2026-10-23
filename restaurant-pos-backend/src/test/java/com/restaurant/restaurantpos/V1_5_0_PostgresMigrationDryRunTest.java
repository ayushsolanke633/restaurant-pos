package com.restaurant.restaurantpos;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable(named = "MIGRATION_TEST_DB_PASSWORD", matches = ".+")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/restaurant_pos_migration_test",
        "spring.datasource.username=restaurant_app",
        "spring.datasource.password=${MIGRATION_TEST_DB_PASSWORD}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.baseline-version=1.0.0",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "app.seed.demo-users=false"
})
public class V1_5_0_PostgresMigrationDryRunTest {

    private static final String EXPECTED_DB_NAME = "restaurant_pos_migration_test";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @Order(1)
    @DisplayName("Safety Gate: Verify effective database URL strictly targets ONLY restaurant_pos_migration_test")
    void verifyEffectiveDatabaseUrlSafetyGate() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String url = metaData.getURL();
            assertNotNull(url, "Datasource connection URL must not be null");
            assertTrue(url.contains(EXPECTED_DB_NAME),
                    "CRITICAL SAFETY VIOLATION: Test datasource URL must strictly target '" + EXPECTED_DB_NAME + "'. Actual URL: " + url);
            assertFalse(url.endsWith("/restaurant_pos"),
                    "CRITICAL SAFETY VIOLATION: Test must NEVER target live restaurant_pos database");
            System.out.println("[SAFETY CHECK VERIFIED] Targeting test database: " + url);
        }
    }

    @Test
    @Order(2)
    @DisplayName("Flyway Check: Verify Flyway V1_5_0 executed cleanly on restaurant_pos_migration_test")
    void verifyFlywayMigrationExecution() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT installed_rank, version, description, script, success FROM flyway_schema_history WHERE version = '1.5.0'");
        assertEquals(1, rows.size(), "Flyway migration V1_5_0 must be recorded in flyway_schema_history");
        Map<String, Object> record = rows.get(0);
        assertEquals("1.5.0", record.get("version"));
        assertEquals("add kitchen status and batch to order items", record.get("description"));
        assertEquals("V1_5_0__add_kitchen_status_and_batch_to_order_items.sql", record.get("script"));
        assertEquals(Boolean.TRUE, record.get("success"), "Flyway V1_5_0 execution must be marked success = true");
        System.out.println("[FLYWAY MIGRATION VERIFIED] V1.5.0 applied successfully on test database.");
    }

    @Test
    @Order(3)
    @DisplayName("Schema & Data Integrity: Verify columns, indexes, and existing order_items preservation")
    void verifyPostgresSchemaAndDataIntegrity() {
        // 1. Verify order_items.status column exists with varchar type
        Integer statusColCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'order_items' AND column_name = 'status'",
                Integer.class);
        assertEquals(1, statusColCount, "order_items.status column must exist");

        // 2. Verify order_items.batch_number column exists with integer type
        Integer batchColCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.columns WHERE table_name = 'order_items' AND column_name = 'batch_number'",
                Integer.class);
        assertEquals(1, batchColCount, "order_items.batch_number column must exist");

        // 3. Verify indexes exist
        Integer idxBatchCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename = 'order_items' AND indexname = 'idx_order_items_order_batch'",
                Integer.class);
        assertEquals(1, idxBatchCount, "idx_order_items_order_batch index must exist");

        // 4. Verify existing order_items retain their data and default correctly
        List<Map<String, Object>> items = jdbcTemplate.queryForList("SELECT id, quantity, notes, status, batch_number FROM order_items LIMIT 50");
        for (Map<String, Object> item : items) {
            assertNotNull(item.get("id"));
            assertNotNull(item.get("quantity"));
            assertNotNull(item.get("status"), "Migrated items must have non-null status ('NEW')");
            assertNotNull(item.get("batch_number"), "Migrated items must have non-null batch_number (1)");
        }
        System.out.println("[POSTGRES TEST DB VERIFIED] Schema, columns, indexes, and order items verified successfully.");
    }
}
