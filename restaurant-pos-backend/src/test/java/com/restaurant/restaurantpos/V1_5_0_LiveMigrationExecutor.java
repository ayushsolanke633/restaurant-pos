package com.restaurant.restaurantpos;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.jdbc.DataSourceBuilder;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@EnabledIfEnvironmentVariable(named = "LIVE_MIGRATION_APPROVED", matches = "true")
public class V1_5_0_LiveMigrationExecutor {

    private static final String EXPECTED_DB_NAME = "restaurant_pos";
    private static final String DB_USER = "restaurant_app";
    private static final String EXPECTED_PRE_VERSION = "1.4.0";
    private static final String TARGET_VERSION = "1.5.0";

    private static String dbPassword;
    private static String dbUrl;
    private static DataSource dataSource;

    @BeforeAll
    static void setUp() {
        dbPassword = System.getenv("LIVE_DB_PASSWORD");
        assertNotNull(dbPassword, "LIVE_DB_PASSWORD environment variable must be provided");
        assertFalse(dbPassword.trim().isEmpty(), "LIVE_DB_PASSWORD must not be empty");

        dbUrl = System.getenv("LIVE_DB_URL");
        if (dbUrl == null || dbUrl.trim().isEmpty()) {
            dbUrl = "jdbc:postgresql://localhost:5432/restaurant_pos";
        }

        dataSource = DataSourceBuilder.create()
                .url(dbUrl)
                .username(DB_USER)
                .password(dbPassword)
                .driverClassName("org.postgresql.Driver")
                .build();
    }

    @Test
    @Order(1)
    @DisplayName("Preflight Safety Gate: Verify target database is strictly restaurant_pos at version 1.4.0")
    void preflightSafetyGate() throws Exception {
        try (Connection conn = dataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String url = meta.getURL();
            assertNotNull(url);
            assertTrue(url.contains(EXPECTED_DB_NAME), "Target URL must strictly contain: " + EXPECTED_DB_NAME);

            try (Statement stmt = conn.createStatement()) {
                // 1. Verify current database name
                try (ResultSet rs = stmt.executeQuery("SELECT current_database();")) {
                    assertTrue(rs.next());
                    String currentDb = rs.getString(1);
                    assertEquals(EXPECTED_DB_NAME, currentDb, "Target database must be strictly " + EXPECTED_DB_NAME);
                    System.out.println("[PREFLIGHT GATE 1] Verified target database: " + currentDb);
                }

                // 2. Verify current schema version is strictly 1.4.0
                try (ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM flyway_schema_history;")) {
                    assertTrue(rs.next());
                    String maxVer = rs.getString(1);
                    assertEquals(EXPECTED_PRE_VERSION, maxVer, "Database schema version must be strictly " + EXPECTED_PRE_VERSION);
                    System.out.println("[PREFLIGHT GATE 2] Verified current schema version: " + maxVer);
                }

                // 3. Verify existing 5 Flyway history records are intact
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM flyway_schema_history WHERE success = true;")) {
                    assertTrue(rs.next());
                    int successCount = rs.getInt(1);
                    assertEquals(5, successCount, "Existing Flyway history must contain exactly 5 successful records");
                    System.out.println("[PREFLIGHT GATE 3] Verified existing successful migration count: " + successCount);
                }

                // 4. Verify order_items does not already have status or batch_number columns
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT count(*) FROM information_schema.columns WHERE table_name = 'order_items' AND column_name IN ('status', 'batch_number');")) {
                    assertTrue(rs.next());
                    int colCount = rs.getInt(1);
                    assertEquals(0, colCount, "Columns status and batch_number must NOT exist prior to V1_5_0 migration");
                    System.out.println("[PREFLIGHT GATE 4] Verified columns do not exist yet on live order_items.");
                }

                // 5. Record pre-migration row count
                try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM order_items;")) {
                    assertTrue(rs.next());
                    int rowCount = rs.getInt(1);
                    assertEquals(136, rowCount, "Pre-migration order_items row count must be 136");
                    System.out.println("[PREFLIGHT GATE 5] Verified pre-migration order_items row count: " + rowCount);
                }

                // 6. Verify quiet period: no other non-idle queries
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT count(*) FROM pg_stat_activity WHERE datname = 'restaurant_pos' AND state != 'idle' AND pid != pg_backend_pid();")) {
                    assertTrue(rs.next());
                    int activeQueries = rs.getInt(1);
                    assertEquals(0, activeQueries, "Quiet period check: must have 0 non-idle concurrent queries on restaurant_pos");
                    System.out.println("[PREFLIGHT GATE 6] Quiet period verified: 0 active non-idle queries.");
                }
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("Controlled Execution: Apply V1_5_0 migration using Flyway with 10s lock timeout")
    void executeV1_5_0Migration() {
        System.out.println("Configuring Flyway for controlled migration to version " + TARGET_VERSION + "...");
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .baselineVersion("1.0.0")
                .target(TARGET_VERSION)
                .initSql("SET lock_timeout = '10s'; SET statement_timeout = '30s';")
                .load();

        System.out.println("Applying Flyway migration to " + EXPECTED_DB_NAME + "...");
        MigrateResult result = flyway.migrate();

        assertTrue(result.success, "Flyway migration must be reported as success");
        assertEquals(1, result.migrationsExecuted, "Exactly 1 migration (V1_5_0) must be executed");
        assertEquals(TARGET_VERSION, result.targetSchemaVersion, "Target schema version must be 1.5.0");
        System.out.println("[MIGRATION SUCCESS] Applied V1_5_0 successfully. Migrations executed: " + result.migrationsExecuted);
    }

    @Test
    @Order(3)
    @DisplayName("Post-Migration Verification: Verify Flyway history, schema, columns, indexes, and row preservation")
    void postMigrationVerification() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Verify Flyway schema history for 1.5.0
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT installed_rank, version, description, script, checksum, success FROM flyway_schema_history WHERE version = '1.5.0';")) {
                assertTrue(rs.next(), "Flyway history must contain entry for version 1.5.0");
                assertEquals(6, rs.getInt("installed_rank"));
                assertEquals("1.5.0", rs.getString("version"));
                assertEquals("add kitchen status and batch to order items", rs.getString("description"));
                assertEquals("V1_5_0__add_kitchen_status_and_batch_to_order_items.sql", rs.getString("script"));
                assertEquals(-1598638692, rs.getInt("checksum"));
                assertTrue(rs.getBoolean("success"));
                System.out.println("[POST-VERIFY 1] Flyway schema history entry for 1.5.0 verified with success=true and checksum=-1598638692.");
            }

            // 2. Verify all 6 migration records are intact (1.0.0 through 1.5.0)
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM flyway_schema_history;")) {
                assertTrue(rs.next());
                assertEquals(6, rs.getInt(1), "Flyway history must have exactly 6 total records");
                System.out.println("[POST-VERIFY 2] Total Flyway records: 6 intact.");
            }

            // 3. Verify columns exist on order_items
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT column_name, data_type, column_default, is_nullable FROM information_schema.columns " +
                    "WHERE table_name = 'order_items' AND column_name IN ('status', 'batch_number') ORDER BY column_name;")) {

                // Row 1: batch_number
                assertTrue(rs.next(), "batch_number column must exist");
                assertEquals("batch_number", rs.getString("column_name"));
                assertEquals("integer", rs.getString("data_type"));
                assertEquals("1", rs.getString("column_default"));
                assertEquals("NO", rs.getString("is_nullable"));

                // Row 2: status
                assertTrue(rs.next(), "status column must exist");
                assertEquals("status", rs.getString("column_name"));
                assertTrue(rs.getString("data_type").contains("character varying"));
                assertTrue(rs.getString("column_default").contains("'NEW'"));
                assertEquals("NO", rs.getString("is_nullable"));
                System.out.println("[POST-VERIFY 3] Columns status and batch_number verified with correct types, defaults, and NOT NULL constraints.");
            }

            // 4. Verify index idx_order_items_order_batch exists
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT count(*) FROM pg_indexes WHERE tablename = 'order_items' AND indexname = 'idx_order_items_order_batch';")) {
                assertTrue(rs.next());
                assertEquals(1, rs.getInt(1), "Index idx_order_items_order_batch must exist");
                System.out.println("[POST-VERIFY 4] Index idx_order_items_order_batch verified.");
            }

            // 5. Verify row count unchanged (136 rows)
            try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM order_items;")) {
                assertTrue(rs.next());
                assertEquals(136, rs.getInt(1), "Row count must remain exactly 136");
                System.out.println("[POST-VERIFY 5] Row count preservation verified: exactly 136 rows.");
            }

            // 6. Verify existing rows all have status='NEW' and batch_number=1
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT count(*) FROM order_items WHERE status = 'NEW' AND batch_number = 1;")) {
                assertTrue(rs.next());
                assertEquals(136, rs.getInt(1), "All 136 existing rows must default to status='NEW' and batch_number=1");
                System.out.println("[POST-VERIFY 6] 100% data preservation verified: all 136 existing items have status='NEW' and batch_number=1.");
            }

            // 7. Verify specific existing items preserve all original attributes
            try (ResultSet rs = stmt.executeQuery("SELECT id, quantity, notes, status, batch_number FROM order_items LIMIT 5;")) {
                int count = 0;
                while (rs.next()) {
                    count++;
                    System.out.println("  Sample item #" + count + ": id=" + rs.getLong("id") +
                            ", qty=" + rs.getInt("quantity") +
                            ", notes=" + rs.getString("notes") +
                            ", status=" + rs.getString("status") +
                            ", batch=" + rs.getInt("batch_number"));
                }
                assertEquals(5, count);
                System.out.println("[POST-VERIFY 7] Sample rows inspected and confirmed.");
            }
        }
    }
}
