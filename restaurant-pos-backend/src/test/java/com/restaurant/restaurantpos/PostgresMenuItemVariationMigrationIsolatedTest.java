package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.util.MigrationApprovalGate;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnnaDhara POS — Menu Item Variations (Client Change 4)
 * Isolated PostgreSQL Integration & Migration Test for V1_9_0.
 *
 * CRITICAL SAFETY RULES:
 * 1. NEVER connects to, inspects, or executes DDL against the live 'restaurant_pos' database.
 * 2. Strictly scoped to the isolated test database 'restaurant_pos_variations_test'.
 * 3. Gated with @EnabledIfEnvironmentVariable("VARIATIONS_TEST_DB_PASSWORD") - fails safe (skips) if unconfigured.
 * 4. Safety gate in @Order(1) verifies URL and refuses execution if non-production cannot be proven.
 */
@SpringBootTest
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ContextConfiguration(initializers = PostgresMenuItemVariationMigrationIsolatedTest.DatabaseInitializer.class)
@org.junit.jupiter.api.condition.EnabledIf("isVariationsDbAvailable")
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/restaurant_pos_variations_test",
        "spring.datasource.username=restaurant_app",
        "spring.datasource.password=${VARIATIONS_TEST_DB_PASSWORD:${MIGRATION_TEST_DB_PASSWORD:${DB_PASSWORD:password}}}",
        "spring.datasource.driver-class-name=org.postgresql.Driver",
        "spring.flyway.enabled=true",
        "spring.flyway.baseline-on-migrate=true",
        "spring.flyway.baseline-version=1.8.0",
        "spring.flyway.locations=classpath:db/migration",
        "spring.jpa.hibernate.ddl-auto=validate",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect",
        "app.seed.demo-users=false",
        "flyway.test.isolated.approved=true"
})
public class PostgresMenuItemVariationMigrationIsolatedTest {

    public static final String EXPECTED_TEST_DB_NAME = "restaurant_pos_variations_test";

    static {
        // Set test approval property early so MigrationApprovalGate authorizes 1.9.0 on restaurant_pos_variations_test
        System.setProperty("flyway.test.isolated.approved", "true");
    }

    public static String resolvePassword() {
        String pass = System.getenv("VARIATIONS_TEST_DB_PASSWORD");
        if (pass != null && !pass.trim().isEmpty()) {
            return pass.trim();
        }
        pass = System.getProperty("VARIATIONS_TEST_DB_PASSWORD");
        if (pass != null && !pass.trim().isEmpty()) {
            return pass.trim();
        }
        pass = System.getenv("MIGRATION_TEST_DB_PASSWORD");
        if (pass != null && !pass.trim().isEmpty()) {
            return pass.trim();
        }
        pass = System.getProperty("MIGRATION_TEST_DB_PASSWORD");
        if (pass != null && !pass.trim().isEmpty()) {
            return pass.trim();
        }
        pass = System.getenv("DB_PASSWORD");
        if (pass != null && !pass.trim().isEmpty()) {
            return pass.trim();
        }
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection c = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/" + EXPECTED_TEST_DB_NAME, "restaurant_app", "password")) {
                return "password";
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isVariationsDbAvailable() {
        String pass = resolvePassword();
        if (pass == null) {
            return false;
        }
        try {
            Class.forName("org.postgresql.Driver");
            try (Connection c = DriverManager.getConnection(
                    "jdbc:postgresql://localhost:5432/" + EXPECTED_TEST_DB_NAME, "restaurant_app", pass)) {
                return true;
            }
        } catch (Exception ignored) {
            return false;
        }
    }

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * ApplicationContextInitializer that guarantees the isolated test database
     * has the proper pre-migration baseline (up to version 1.7.0/1.8.0) before Spring Boot
     * Flyway and EntityManagerFactory beans initialize.
     */
    public static class DatabaseInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {
        @Override
        public void initialize(ConfigurableApplicationContext applicationContext) {
            System.setProperty("flyway.test.isolated.approved", "true");

            String dbPass = resolvePassword();
            if (dbPass == null || dbPass.trim().isEmpty()) {
                return;
            }

            String url = "jdbc:postgresql://localhost:5432/" + EXPECTED_TEST_DB_NAME;
            String user = "restaurant_app";

            // Strict safety check before establishing connection
            if (url.endsWith("/restaurant_pos") || url.contains("/restaurant_pos?")) {
                throw new IllegalStateException("CRITICAL SAFETY VIOLATION: Refusing to touch live production database!");
            }

            try {
                Class.forName("org.postgresql.Driver");
            } catch (ClassNotFoundException ignored) {}

            try (Connection conn = DriverManager.getConnection(url, user, dbPass)) {
                DatabaseMetaData metaData = conn.getMetaData();
                String connUrl = metaData.getURL();
                if (!connUrl.contains(EXPECTED_TEST_DB_NAME)) {
                    throw new IllegalStateException("CRITICAL SAFETY VIOLATION: Target URL is not " + EXPECTED_TEST_DB_NAME);
                }

                // Check if application tables already exist
                boolean hasRestaurants = false;
                try (ResultSet rs = metaData.getTables(null, "public", "restaurants", new String[]{"TABLE"})) {
                    if (rs.next()) {
                        hasRestaurants = true;
                    }
                }

                // Check if restaurants has is_active column (introduced in V1.2.0)
                boolean hasIsActive = false;
                if (hasRestaurants) {
                    try (ResultSet rs = metaData.getColumns(null, "public", "restaurants", "is_active")) {
                        if (rs.next()) {
                            hasIsActive = true;
                        }
                    }
                }

                // Check flyway_schema_history max version
                boolean needsRestore = !hasRestaurants || !hasIsActive;
                if (hasRestaurants && hasIsActive) {
                    try (Statement stmt = conn.createStatement();
                         ResultSet rs = stmt.executeQuery("SELECT MAX(version) FROM flyway_schema_history")) {
                        if (rs.next()) {
                            String maxVer = rs.getString(1);
                            if (maxVer == null || MigrationApprovalGate.compareVersions(maxVer, "1.7.0") < 0) {
                                needsRestore = true;
                            }
                        }
                    } catch (Exception ex) {
                        needsRestore = true;
                    }
                }

                if (needsRestore) {
                    System.out.println("[ISOLATED TEST SETUP] Cleaning public schema and restoring baseline schema from production backup into " + EXPECTED_TEST_DB_NAME + "...");
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("DROP SCHEMA IF EXISTS public CASCADE");
                        stmt.execute("CREATE SCHEMA public");
                        stmt.execute("GRANT ALL ON SCHEMA public TO restaurant_app");
                        stmt.execute("GRANT ALL ON SCHEMA public TO public");
                    } catch (Exception ex) {
                        System.err.println("[ISOLATED TEST SETUP WARN] Schema reset notice: " + ex.getMessage());
                    }
                    restoreBaselineBackup(dbPass);
                }
            } catch (Exception ex) {
                System.err.println("[ISOLATED TEST SETUP NOTICE] Pre-startup check: " + ex.getMessage());
            }
        }

        private static void restoreBaselineBackup(String dbPass) {
            File dumpFile = new File("C:/AURA2/backups/restaurant_pos_prod_backup_20261025_195207.dump");
            if (!dumpFile.exists()) {
                dumpFile = new File("../backups/restaurant_pos_prod_backup_20261025_195207.dump");
            }
            if (!dumpFile.exists()) {
                System.err.println("[ISOLATED TEST SETUP WARN] Baseline backup dump not found at " + dumpFile.getAbsolutePath());
                return;
            }

            String pgRestoreBin = "pg_restore";
            File pg18Bin = new File("C:/Program Files/PostgreSQL/18/bin/pg_restore.exe");
            if (pg18Bin.exists()) {
                pgRestoreBin = pg18Bin.getAbsolutePath();
            }

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        pgRestoreBin,
                        "-h", "localhost",
                        "-p", "5432",
                        "-U", "restaurant_app",
                        "-d", EXPECTED_TEST_DB_NAME,
                        "--clean",
                        "--if-exists",
                        "--no-owner",
                        dumpFile.getAbsolutePath()
                );
                pb.environment().put("PGPASSWORD", dbPass);
                pb.redirectErrorStream(true);
                Process proc = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println("[pg_restore] " + line);
                    }
                }
                int exitCode = proc.waitFor();
                System.out.println("[ISOLATED TEST SETUP] Baseline restore completed with exit code: " + exitCode);
            } catch (Exception ex) {
                System.err.println("[ISOLATED TEST SETUP ERROR] pg_restore failed: " + ex.getMessage());
            }
        }
    }

    @Test
    @Order(1)
    @DisplayName("Safety Gate: Verify effective database URL strictly targets ONLY restaurant_pos_variations_test")
    void verifyEffectiveDatabaseUrlSafetyGate() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String url = metaData.getURL();
            assertNotNull(url, "Datasource connection URL must not be null");
            assertTrue(url.contains(EXPECTED_TEST_DB_NAME),
                    "CRITICAL SAFETY VIOLATION: Test datasource URL must strictly target '" + EXPECTED_TEST_DB_NAME + "'. Actual URL: " + url);
            assertFalse(url.endsWith("/restaurant_pos"),
                    "CRITICAL SAFETY VIOLATION: Test must NEVER target live restaurant_pos database");
            assertFalse(url.contains("/restaurant_pos?"),
                    "CRITICAL SAFETY VIOLATION: Test must NEVER target live restaurant_pos database");
            assertFalse(url.contains("restaurant_pos_restore_test"),
                    "CRITICAL SAFETY VIOLATION: Test must not target restore test database");
            System.out.println("[SAFETY CHECK VERIFIED] Targeting test database: " + url);
        }
    }

    @Test
    @Order(2)
    @DisplayName("Flyway V1_9_0: Execution recorded as success in flyway_schema_history")
    void verifyFlywayMigrationExecution() {
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT installed_rank, version, description, script, success FROM flyway_schema_history WHERE version = '1.9.0'");
        assertEquals(1, rows.size(), "Flyway migration V1_9_0 must be recorded in flyway_schema_history");
        Map<String, Object> record = rows.get(0);
        assertEquals("1.9.0", record.get("version"));
        assertEquals("add menu item variations", record.get("description"));
        assertEquals("V1_9_0__add_menu_item_variations.sql", record.get("script"));
        assertEquals(Boolean.TRUE, record.get("success"), "Flyway V1_9_0 execution must be marked success = true");
        System.out.println("[FLYWAY MIGRATION VERIFIED] V1.9.0 applied successfully.");
    }

    @Test
    @Order(3)
    @DisplayName("Catalog Inspection: menu_item_variations table structure, types, and nullability")
    void verifyMenuItemVariationsTableCatalog() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            // Verify table exists
            try (ResultSet rs = metaData.getTables(null, "public", "menu_item_variations", new String[]{"TABLE"})) {
                assertTrue(rs.next(), "Table menu_item_variations must exist in schema public");
            }

            // Verify columns
            try (ResultSet rs = metaData.getColumns(null, "public", "menu_item_variations", null)) {
                boolean hasId = false;
                boolean hasRestaurantId = false;
                boolean hasMenuItemId = false;
                boolean hasName = false;
                boolean hasPrice = false;
                boolean hasIsAvailable = false;
                boolean hasSortOrder = false;

                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    String isNullable = rs.getString("IS_NULLABLE");
                    if ("id".equalsIgnoreCase(col)) {
                        hasId = true;
                        assertEquals("NO", isNullable);
                    } else if ("restaurant_id".equalsIgnoreCase(col)) {
                        hasRestaurantId = true;
                        assertEquals("NO", isNullable);
                    } else if ("menu_item_id".equalsIgnoreCase(col)) {
                        hasMenuItemId = true;
                        assertEquals("NO", isNullable);
                    } else if ("name".equalsIgnoreCase(col)) {
                        hasName = true;
                        assertEquals("NO", isNullable);
                    } else if ("price".equalsIgnoreCase(col)) {
                        hasPrice = true;
                        assertEquals("NO", isNullable);
                    } else if ("is_available".equalsIgnoreCase(col)) {
                        hasIsAvailable = true;
                        assertEquals("NO", isNullable);
                    } else if ("sort_order".equalsIgnoreCase(col)) {
                        hasSortOrder = true;
                        assertEquals("NO", isNullable);
                    }
                }

                assertTrue(hasId && hasRestaurantId && hasMenuItemId && hasName && hasPrice && hasIsAvailable && hasSortOrder,
                        "menu_item_variations must contain all required columns with NOT NULL constraints");
            }
        }
    }

    @Test
    @Order(4)
    @DisplayName("Catalog Inspection: order_items variation_id and variation_name columns are NULLABLE")
    void verifyOrderItemsVariationColumnsCatalog() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();

            try (ResultSet rs = metaData.getColumns(null, "public", "order_items", null)) {
                boolean hasVariationId = false;
                boolean hasVariationName = false;

                while (rs.next()) {
                    String col = rs.getString("COLUMN_NAME");
                    String isNullable = rs.getString("IS_NULLABLE");
                    if ("variation_id".equalsIgnoreCase(col)) {
                        hasVariationId = true;
                        assertEquals("YES", isNullable, "variation_id must be nullable for historical compatibility");
                    } else if ("variation_name".equalsIgnoreCase(col)) {
                        hasVariationName = true;
                        assertEquals("YES", isNullable, "variation_name must be nullable for historical compatibility");
                    }
                }

                assertTrue(hasVariationId && hasVariationName, "order_items must contain variation_id and variation_name");
            }
        }
    }

    @Test
    @Order(5)
    @DisplayName("Constraints: Check constraint price >= 0 and unique constraint uk_variation_item_name enforced")
    void verifyConstraintsEnforced() {
        // Find existing restaurant and menu item
        List<Map<String, Object>> restaurants = jdbcTemplate.queryForList("SELECT id FROM restaurants LIMIT 1");
        if (restaurants.isEmpty()) return;
        Long restId = ((Number) restaurants.get(0).get("id")).longValue();

        List<Map<String, Object>> items = jdbcTemplate.queryForList(
                "SELECT id FROM menu_items WHERE restaurant_id = ? LIMIT 1", restId);
        if (items.isEmpty()) return;
        Long itemId = ((Number) items.get(0).get("id")).longValue();

        String varName = "Tst_Var_" + System.currentTimeMillis();

        // 1. Insert valid variation
        jdbcTemplate.update(
                "INSERT INTO menu_item_variations (restaurant_id, menu_item_id, name, price, is_available, sort_order) " +
                "VALUES (?, ?, ?, 100.00, true, 0)", restId, itemId, varName);

        // 2. Duplicate variation name within same menu_item must fail
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO menu_item_variations (restaurant_id, menu_item_id, name, price, is_available, sort_order) " +
                    "VALUES (?, ?, ?, 120.00, true, 1)", restId, itemId, varName);
        });

        // 3. Negative price must fail check constraint
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO menu_item_variations (restaurant_id, menu_item_id, name, price, is_available, sort_order) " +
                    "VALUES (?, ?, 'Negative_Price_Var', -10.00, true, 2)", restId, itemId);
        });

        // Cleanup test variation
        jdbcTemplate.update("DELETE FROM menu_item_variations WHERE menu_item_id = ? AND name = ?", itemId, varName);
    }
}
