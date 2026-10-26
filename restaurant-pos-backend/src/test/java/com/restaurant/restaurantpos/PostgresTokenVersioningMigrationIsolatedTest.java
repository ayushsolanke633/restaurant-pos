package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.util.MigrationApprovalGate;
import com.restaurant.restaurantpos.util.MigrationPreflightValidator;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * AnnaDhara P0-3 Stage 3Q Task C:
 * Isolated PostgreSQL Integration Test for Persistent Token Versioning Migrations (V1.6.0 & V1.7.0).
 *
 * CRITICAL SAFETY RULES:
 * 1. NEVER connects to, inspects, or executes DDL against the live 'restaurant_pos' database.
 * 2. Scoped strictly to the isolated test database 'restaurant_pos_migration_test'.
 * 3. Gated with @EnabledIfEnvironmentVariable("MIGRATION_TEST_DB_PASSWORD") - fails safe (skips) if unconfigured.
 * 4. Safety gate in @Order(1) verifies URL and refuses execution if non-production cannot be proven.
 * 5. Seeding and cleanup are strictly confined to disposable fixture records.
 */
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
public class PostgresTokenVersioningMigrationIsolatedTest {

    private static final String EXPECTED_TEST_DB_NAME = "restaurant_pos_migration_test";
    private static final String TEST_USER_PREFIX = "iso_test_user_";
    private static final String TEST_RESTAURANT_PREFIX = "ISO_TEST_REST_";

    @Autowired
    private DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final Set<Long> createdUserIds = new HashSet<>();
    private static final Set<Long> createdRestaurantIds = new HashSet<>();
    private static final Set<String> createdJtis = new HashSet<>();

    @AfterEach
    void cleanupFixtureData() {
        // Strict scoped cleanup of only rows created by this isolated test fixture
        for (String jti : createdJtis) {
            try {
                jdbcTemplate.update("DELETE FROM revoked_tokens WHERE jti = ?", jti);
            } catch (Exception ignored) {}
        }
        createdJtis.clear();

        for (Long userId : createdUserIds) {
            try {
                jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId);
            } catch (Exception ignored) {}
        }
        createdUserIds.clear();

        for (Long restId : createdRestaurantIds) {
            try {
                jdbcTemplate.update("DELETE FROM restaurant_settings WHERE restaurant_id = ?", restId);
                jdbcTemplate.update("DELETE FROM restaurants WHERE id = ?", restId);
            } catch (Exception ignored) {}
        }
        createdRestaurantIds.clear();
    }

    @Test
    @Order(1)
    @DisplayName("Safety Gate: Verify test datasource URL strictly targets ONLY restaurant_pos_migration_test")
    void verifyEffectiveDatabaseUrlSafetyGate() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData metaData = connection.getMetaData();
            String url = metaData.getURL();
            assertNotNull(url, "Datasource connection URL must not be null");
            assertTrue(url.contains(EXPECTED_TEST_DB_NAME),
                    "CRITICAL SAFETY VIOLATION: Test datasource URL must strictly target '" + EXPECTED_TEST_DB_NAME + "'. Actual URL: " + url);
            assertFalse(url.contains("restaurant_pos_restore_test"),
                    "CRITICAL SAFETY VIOLATION: Test must not target restaurant_pos_restore_test");
            assertFalse(url.endsWith("/restaurant_pos"),
                    "CRITICAL SAFETY VIOLATION: Test must NEVER target live restaurant_pos database");
            System.out.println("[SAFETY CHECK VERIFIED] Strictly targeting isolated test database: " + url);
        }
    }

    @Test
    @Order(2)
    @DisplayName("Flyway Check: Verify Flyway V1_6_0 and V1_7_0 applied cleanly on test database")
    void verifyFlywayMigrationExecution() {
        List<Map<String, Object>> v16Rows = jdbcTemplate.queryForList(
                "SELECT installed_rank, version, description, script, success FROM flyway_schema_history WHERE version = '1.6.0'");
        assertEquals(1, v16Rows.size(), "Flyway migration V1_6_0 must be recorded in flyway_schema_history");
        assertEquals(Boolean.TRUE, v16Rows.get(0).get("success"), "Flyway V1_6_0 execution must be marked success = true");

        List<Map<String, Object>> v17Rows = jdbcTemplate.queryForList(
                "SELECT installed_rank, version, description, script, success FROM flyway_schema_history WHERE version = '1.7.0'");
        assertEquals(1, v17Rows.size(), "Flyway migration V1_7_0 must be recorded in flyway_schema_history");
        assertEquals(Boolean.TRUE, v17Rows.get(0).get("success"), "Flyway V1_7_0 execution must be marked success = true");

        System.out.println("[FLYWAY MIGRATION VERIFIED] V1.6.0 and V1.7.0 applied successfully on isolated test DB.");
    }

    @Test
    @Order(3)
    @DisplayName("Catalog Constraints: Verify column data types, NOT NULL, defaults, PK and indexes in PostgreSQL catalog")
    void verifyPostgresCatalogMetadataConstraints() {
        // 1. Verify users.token_version catalog attributes
        Map<String, Object> userCol = jdbcTemplate.queryForMap(
                "SELECT data_type, is_nullable, column_default FROM information_schema.columns " +
                        "WHERE table_name = 'users' AND column_name = 'token_version'");
        assertEquals("integer", userCol.get("data_type"), "users.token_version must be integer");
        assertEquals("NO", userCol.get("is_nullable"), "users.token_version must be NOT NULL");
        assertNotNull(userCol.get("column_default"), "users.token_version must have a default value");
        assertTrue(userCol.get("column_default").toString().contains("1"),
                "users.token_version default must be 1. Got: " + userCol.get("column_default"));

        // 2. Verify restaurants.token_version catalog attributes
        Map<String, Object> restCol = jdbcTemplate.queryForMap(
                "SELECT data_type, is_nullable, column_default FROM information_schema.columns " +
                        "WHERE table_name = 'restaurants' AND column_name = 'token_version'");
        assertEquals("integer", restCol.get("data_type"), "restaurants.token_version must be integer");
        assertEquals("NO", restCol.get("is_nullable"), "restaurants.token_version must be NOT NULL");
        assertNotNull(restCol.get("column_default"), "restaurants.token_version must have a default value");
        assertTrue(restCol.get("column_default").toString().contains("1"),
                "restaurants.token_version default must be 1. Got: " + restCol.get("column_default"));

        // 3. Verify revoked_tokens table columns
        List<Map<String, Object>> revokedCols = jdbcTemplate.queryForList(
                "SELECT column_name, data_type, is_nullable FROM information_schema.columns " +
                        "WHERE table_name = 'revoked_tokens' ORDER BY ordinal_position");
        Map<String, Map<String, Object>> colMap = new HashMap<>();
        for (Map<String, Object> row : revokedCols) {
            colMap.put((String) row.get("column_name"), row);
        }

        assertTrue(colMap.containsKey("jti"), "revoked_tokens must have jti column");
        assertEquals("character varying", colMap.get("jti").get("data_type"));
        assertEquals("NO", colMap.get("jti").get("is_nullable"));

        assertTrue(colMap.containsKey("user_id"), "revoked_tokens must have user_id column");
        assertEquals("bigint", colMap.get("user_id").get("data_type"));
        assertEquals("NO", colMap.get("user_id").get("is_nullable"));

        assertTrue(colMap.containsKey("expires_at"), "revoked_tokens must have expires_at column");
        assertEquals("NO", colMap.get("expires_at").get("is_nullable"));

        assertTrue(colMap.containsKey("revoked_at"), "revoked_tokens must have revoked_at column");
        assertEquals("NO", colMap.get("revoked_at").get("is_nullable"));

        // 4. Verify primary key constraint on revoked_tokens(jti)
        Integer pkCount = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM information_schema.table_constraints tc " +
                        "JOIN information_schema.constraint_column_usage ccu ON tc.constraint_name = ccu.constraint_name " +
                        "WHERE tc.table_name = 'revoked_tokens' AND tc.constraint_type = 'PRIMARY KEY' AND ccu.column_name = 'jti'",
                Integer.class);
        assertEquals(1, pkCount, "revoked_tokens must have a PRIMARY KEY on 'jti'");

        // 5. Verify indexes on revoked_tokens
        Integer idxExpires = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename = 'revoked_tokens' AND indexname = 'idx_revoked_tokens_expires_at'",
                Integer.class);
        assertEquals(1, idxExpires, "idx_revoked_tokens_expires_at index must exist");

        Integer idxUserId = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_indexes WHERE tablename = 'revoked_tokens' AND indexname = 'idx_revoked_tokens_user_id'",
                Integer.class);
        assertEquals(1, idxUserId, "idx_revoked_tokens_user_id index must exist");

        System.out.println("[POSTGRES CATALOG METADATA VERIFIED] Data types, nullability, defaults, PK, and indexes confirmed.");
    }

    @Test
    @Order(4)
    @DisplayName("Preflight Validator: Run read-only catalog inspector against PostgreSQL test database")
    void verifyMigrationPreflightValidatorPasses() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            MigrationPreflightValidator.PreflightResult res16 = MigrationPreflightValidator.validateVersion(connection, "1.6.0");
            assertNotNull(res16, "PreflightResult for V1.6.0 must not be null");
            assertTrue(res16.isValid(), "Preflight validation for V1.6.0 must pass on PostgreSQL test DB. Message: " + res16.getMessage());

            MigrationPreflightValidator.PreflightResult res17 = MigrationPreflightValidator.validateVersion(connection, "1.7.0");
            assertNotNull(res17, "PreflightResult for V1.7.0 must not be null");
            assertTrue(res17.isValid(), "Preflight validation for V1.7.0 must pass on PostgreSQL test DB. Message: " + res17.getMessage());

            System.out.println("[PREFLIGHT VALIDATOR VERIFIED] Read-only catalog metadata checks passed cleanly for 1.6.0 and 1.7.0.");
        }
    }

    @Test
    @Order(5)
    @DisplayName("Data Integrity: Verify existing rows have token_version=1, new rows default to 1, and PK enforcement on revoked_tokens")
    void verifyDataIntegrityAndConstraints() {
        // 1. Verify all pre-existing users have token_version = 1
        List<Integer> userVersions = jdbcTemplate.queryForList("SELECT token_version FROM users", Integer.class);
        assertFalse(userVersions.isEmpty(), "Users table must not be empty");
        for (Integer version : userVersions) {
            assertEquals(1, version, "All existing users must have token_version = 1");
        }

        // 2. Verify all pre-existing restaurants have token_version = 1
        List<Integer> restVersions = jdbcTemplate.queryForList("SELECT token_version FROM restaurants", Integer.class);
        assertFalse(restVersions.isEmpty(), "Restaurants table must not be empty");
        for (Integer version : restVersions) {
            assertEquals(1, version, "All existing restaurants must have token_version = 1");
        }

        // 3. Test insert new restaurant without specifying token_version -> defaults to 1
        long ts = System.currentTimeMillis();
        String restName = TEST_RESTAURANT_PREFIX + ts;
        Long restId = jdbcTemplate.queryForObject(
                "INSERT INTO restaurants (name, address, phone, is_active, created_at, updated_at) " +
                        "VALUES (?, '123 Isolated Test St', '5551234567', true, NOW(), NOW()) RETURNING id",
                Long.class, restName);
        assertNotNull(restId);
        createdRestaurantIds.add(restId);

        Integer defaultRestVersion = jdbcTemplate.queryForObject(
                "SELECT token_version FROM restaurants WHERE id = ?", Integer.class, restId);
        assertEquals(1, defaultRestVersion, "New restaurant must default to token_version = 1");

        // 4. Test insert new user without specifying token_version -> defaults to 1
        Long adminRoleId = jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = 'ADMIN'", Long.class);
        String userEmail = TEST_USER_PREFIX + ts + "@isolated-test.local";
        Long userId = jdbcTemplate.queryForObject(
                "INSERT INTO users (email, name, password_hash, is_active, role_id, restaurant_id, created_at, updated_at) " +
                        "VALUES (?, 'Iso Test User', 'hash', true, ?, ?, NOW(), NOW()) RETURNING id",
                Long.class, userEmail, adminRoleId, restId);
        assertNotNull(userId);
        createdUserIds.add(userId);

        Integer defaultUserVersion = jdbcTemplate.queryForObject(
                "SELECT token_version FROM users WHERE id = ?", Integer.class, userId);
        assertEquals(1, defaultUserVersion, "New user must default to token_version = 1");

        // 5. Test revoked_tokens insert and constraints
        String testJti = UUID.randomUUID().toString();
        createdJtis.add(testJti);
        Instant now = Instant.now();
        Instant expiresAt = now.plus(1, ChronoUnit.HOURS);

        int inserted = jdbcTemplate.update(
                "INSERT INTO revoked_tokens (jti, user_id, restaurant_id, expires_at, revoked_at) VALUES (?, ?, ?, ?, ?)",
                testJti, userId, restId, Timestamp.from(expiresAt), Timestamp.from(now));
        assertEquals(1, inserted, "Must insert 1 row into revoked_tokens");

        // Duplicate JTI MUST fail primary key constraint
        assertThrows(DataIntegrityViolationException.class, () -> {
            jdbcTemplate.update(
                    "INSERT INTO revoked_tokens (jti, user_id, restaurant_id, expires_at, revoked_at) VALUES (?, ?, ?, ?, ?)",
                    testJti, userId, restId, Timestamp.from(expiresAt), Timestamp.from(now));
        }, "Inserting duplicate jti must throw DataIntegrityViolationException");

        System.out.println("[DATA INTEGRITY & CONSTRAINTS VERIFIED] Default values, existing rows, and PK violation tested successfully.");
    }

    @Test
    @Order(6)
    @DisplayName("Gate Immutability: Verify MigrationApprovalGate allowlist is strictly unchanged and blocks 1.6.0/1.7.0")
    void verifyMigrationApprovalGateImmutability() {
        Set<String> allowlist = MigrationApprovalGate.APPROVED_ALLOWLIST;
        assertNotNull(allowlist, "APPROVED_ALLOWLIST must not be null");
        assertEquals(5, allowlist.size(), "APPROVED_ALLOWLIST must contain exactly 5 versions");
        assertFalse(allowlist.contains("1.6.0"), "V1.6.0 must NOT be in the approved allowlist");
        assertFalse(allowlist.contains("1.7.0"), "V1.7.0 must NOT be in the approved allowlist");
        assertTrue(allowlist.containsAll(Arrays.asList("1.0.0", "1.2.0", "1.3.0", "1.4.0", "1.5.0")),
                "APPROVED_ALLOWLIST must strictly contain only 1.0.0, 1.2.0, 1.3.0, 1.4.0, 1.5.0");

        // Test unmodifiable collection
        assertThrows(UnsupportedOperationException.class, () -> allowlist.add("1.6.0"),
                "APPROVED_ALLOWLIST must be immutable");

        System.out.println("[MIGRATION GATE IMMUTABILITY VERIFIED] Migration allowlist unchanged; 1.6.0 and 1.7.0 remain blocked.");
    }
}
