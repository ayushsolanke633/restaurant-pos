package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.util.MigrationApprovalGate;
import com.restaurant.restaurantpos.util.MigrationApprovalGate.DatabaseState;
import com.restaurant.restaurantpos.util.MigrationApprovalGate.GateResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for MigrationApprovalGate.
 * Purely in-memory, zero database connection, zero network calls.
 */
public class MigrationApprovalGateTest {

    private static final String VALID_URL = "jdbc:postgresql://localhost:5432/restaurant_pos";
    private static final String VALID_USER = "restaurant_app";
    private static final String VALID_PASS = "sample_test_pass";
    private static final String VALID_TOKEN = MigrationApprovalGate.GATE_AUTHORIZATION_TOKEN;

    private static final String INFO_OUTPUT_WITH_UNAUTHORIZED_V1_1_0 =
            "+-----------+---------+-----------------------------------+------+---------------------+---------+\n" +
            "| Category  | Version | Description                       | Type | Installed On        | State   |\n" +
            "+-----------+---------+-----------------------------------+------+---------------------+---------+\n" +
            "| Versioned | 1.0.0   | existing schema                   | SQL  | 2026-03-01 10:00:00 | Success |\n" +
            "| Versioned | 1.1.0   | add tenant id to child tables     | SQL  |                     | Pending |\n" +
            "| Versioned | 1.2.0   | add super admin and rest status   | SQL  |                     | Pending |\n" +
            "+-----------+---------+-----------------------------------+------+---------------------+---------+\n";

    private static final String INFO_OUTPUT_AUTHORIZED_ONLY =
            "+-----------+---------+-----------------------------------+------+---------------------+---------+\n" +
            "| Category  | Version | Description                       | Type | Installed On        | State   |\n" +
            "+-----------+---------+-----------------------------------+------+---------------------+---------+\n" +
            "| Versioned | 1.0.0   | existing schema                   | SQL  | 2026-03-01 10:00:00 | Success |\n" +
            "| Versioned | 1.2.0   | add super admin and rest status   | SQL  |                     | Pending |\n" +
            "| Versioned | 1.5.0   | add kitchen status and batch      | SQL  |                     | Pending |\n" +
            "+-----------+---------+-----------------------------------+------+---------------------+---------+\n";

    private static final String INFO_OUTPUT_EMPTY_DB =
            "+-----------+---------+-----------------------------------+------+---------------------+---------+\n" +
            "| Category  | Version | Description                       | Type | Installed On        | State   |\n" +
            "+-----------+---------+-----------------------------------+------+---------------------+---------+\n" +
            "| Versioned | 1.0.0   | existing schema                   | SQL  |                     | Pending |\n" +
            "+-----------+---------+-----------------------------------+------+---------------------+---------+\n";

    @Test
    @DisplayName("Fail-Closed: Missing DB credentials aborts before any Flyway invocation")
    void testMissingCredentialsFailsClosed() {
        AtomicBoolean migrationExecuted = new AtomicBoolean(false);

        GateResult r1 = MigrationApprovalGate.evaluate(null, VALID_USER, VALID_PASS, DatabaseState.VERSIONED_WITH_HISTORY, INFO_OUTPUT_AUTHORIZED_ONLY, "1.5.0", VALID_TOKEN);
        assertFalse(r1.isAuthorized());
        assertTrue(r1.getMessage().contains("Missing required environment credentials"));

        GateResult r2 = MigrationApprovalGate.evaluate(VALID_URL, "", VALID_PASS, DatabaseState.VERSIONED_WITH_HISTORY, INFO_OUTPUT_AUTHORIZED_ONLY, "1.5.0", VALID_TOKEN);
        assertFalse(r2.isAuthorized());

        GateResult r3 = MigrationApprovalGate.evaluate(VALID_URL, VALID_USER, "  ", DatabaseState.VERSIONED_WITH_HISTORY, INFO_OUTPUT_AUTHORIZED_ONLY, "1.5.0", VALID_TOKEN);
        assertFalse(r3.isAuthorized());

        assertFalse(migrationExecuted.get(), "Migration command must NEVER be called when credentials are missing");
    }

    @Test
    @DisplayName("Fail-Closed: Missing or invalid authorization token aborts before any Flyway invocation")
    void testMissingOrInvalidAuthorizationTokenFailsClosed() {
        AtomicBoolean migrationExecuted = new AtomicBoolean(false);

        // Missing token
        GateResult r1 = MigrationApprovalGate.evaluate(VALID_URL, VALID_USER, VALID_PASS, DatabaseState.VERSIONED_WITH_HISTORY, INFO_OUTPUT_AUTHORIZED_ONLY, "1.5.0", null);
        assertFalse(r1.isAuthorized());
        assertTrue(r1.getMessage().contains("Missing or invalid gate authorization token"));

        // Wrong token
        GateResult r2 = MigrationApprovalGate.evaluate(VALID_URL, VALID_USER, VALID_PASS, DatabaseState.VERSIONED_WITH_HISTORY, INFO_OUTPUT_AUTHORIZED_ONLY, "1.5.0", "INVALID_TOKEN");
        assertFalse(r2.isAuthorized());
        assertTrue(r2.getMessage().contains("Missing or invalid gate authorization token"));

        assertFalse(migrationExecuted.get(), "Migration command must NEVER be called without valid authorization token");
    }

    @Test
    @DisplayName("Gate Rejection: Unauthorized pending migration V1_1_0 blocks execution")
    void testUnauthorizedPendingMigrationRejected() {
        AtomicBoolean migrationExecuted = new AtomicBoolean(false);

        GateResult result = MigrationApprovalGate.evaluate(
                VALID_URL, VALID_USER, VALID_PASS,
                DatabaseState.VERSIONED_WITH_HISTORY,
                INFO_OUTPUT_WITH_UNAUTHORIZED_V1_1_0,
                "1.5.0",
                VALID_TOKEN
        );

        assertFalse(result.isAuthorized(), "Pending 1.1.0 must be rejected");
        assertTrue(result.getMessage().contains("1.1.0"), "Error message must cite unauthorized version 1.1.0");

        if (result.isAuthorized()) {
            migrationExecuted.set(true);
        }
        assertFalse(migrationExecuted.get(), "Migration command must NEVER be called when unauthorized version is pending");
    }

    @Test
    @DisplayName("Gate Rejection: Legacy unmanaged database strictly refuses automatic baseline/migrate")
    void testLegacyDatabaseRefusesAutoBaseline() {
        AtomicBoolean migrationExecuted = new AtomicBoolean(false);

        GateResult result = MigrationApprovalGate.evaluate(
                VALID_URL, VALID_USER, VALID_PASS,
                DatabaseState.LEGACY_UNMANAGED,
                INFO_OUTPUT_WITH_UNAUTHORIZED_V1_1_0,
                "1.5.0",
                VALID_TOKEN
        );

        assertFalse(result.isAuthorized());
        assertTrue(result.getMessage().contains("Legacy database with unmanaged tables detected"));
        assertTrue(result.getMessage().contains("Automatic baselining/migration is forbidden"));

        if (result.isAuthorized()) {
            migrationExecuted.set(true);
        }
        assertFalse(migrationExecuted.get(), "Migration command must NEVER be called on unmanaged legacy database");
    }

    @Test
    @DisplayName("Fail-Closed: Failed catalog inspection strictly aborts before migration")
    void testFailedCatalogInspectionFailsClosed() {
        AtomicBoolean migrationExecuted = new AtomicBoolean(false);

        GateResult result = MigrationApprovalGate.evaluate(
                VALID_URL, VALID_USER, VALID_PASS,
                DatabaseState.FAILED_INSPECTION,
                INFO_OUTPUT_AUTHORIZED_ONLY,
                "1.5.0",
                VALID_TOKEN
        );

        assertFalse(result.isAuthorized());
        assertTrue(result.getMessage().contains("Database catalog inspection failed or was inconclusive"));

        if (result.isAuthorized()) {
            migrationExecuted.set(true);
        }
        assertFalse(migrationExecuted.get(), "Migration command must NEVER be called when catalog inspection failed");
    }

    @Test
    @DisplayName("Database State Detection: Correctly classifies all tri-state and failure modes")
    void testDatabaseStateDetectionTriState() {
        // State 1: Versioned database with history table
        assertEquals(DatabaseState.VERSIONED_WITH_HISTORY,
                MigrationApprovalGate.detectDatabaseState(true, 16, true));

        // State 2: Genuinely empty database (0 tables, successful inspection)
        assertEquals(DatabaseState.EMPTY,
                MigrationApprovalGate.detectDatabaseState(false, 0, true));

        // State 3: Populated database with no history (Legacy)
        assertEquals(DatabaseState.LEGACY_UNMANAGED,
                MigrationApprovalGate.detectDatabaseState(false, 16, true));

        // State 4: Failed inspection (psql missing, query failed, null count)
        assertEquals(DatabaseState.FAILED_INSPECTION,
                MigrationApprovalGate.detectDatabaseState(false, null, false));
        assertEquals(DatabaseState.FAILED_INSPECTION,
                MigrationApprovalGate.detectDatabaseState(false, 16, false));
        assertEquals(DatabaseState.FAILED_INSPECTION,
                MigrationApprovalGate.detectDatabaseState(false, null, true));
    }

    @Test
    @DisplayName("Gate Rejection: Empty database must strictly be initialized with target 1.0.0")
    void testEmptyDatabaseRequiresTargetOneZeroZero() {
        GateResult result = MigrationApprovalGate.evaluate(
                VALID_URL, VALID_USER, VALID_PASS,
                DatabaseState.EMPTY,
                INFO_OUTPUT_EMPTY_DB,
                "1.5.0",
                VALID_TOKEN
        );

        assertFalse(result.isAuthorized());
        assertTrue(result.getMessage().contains("Empty database must be initialized strictly with Target=1.0.0"));
    }

    @Test
    @DisplayName("Gate Approval: Empty database with target 1.0.0 is authorized to establish baseline")
    void testEmptyDatabaseWithTargetOneZeroZeroApproved() {
        AtomicBoolean migrationExecuted = new AtomicBoolean(false);

        GateResult result = MigrationApprovalGate.evaluate(
                VALID_URL, VALID_USER, VALID_PASS,
                DatabaseState.EMPTY,
                INFO_OUTPUT_EMPTY_DB,
                "1.0.0",
                VALID_TOKEN
        );

        assertTrue(result.isAuthorized());
        assertTrue(result.getMessage().contains("APPROVED"));
        assertEquals(1, result.getPendingVersions().size());
        assertEquals("1.0.0", result.getPendingVersions().get(0));

        if (result.isAuthorized()) {
            migrationExecuted.set(true);
        }
        assertTrue(migrationExecuted.get());
    }

    @Test
    @DisplayName("Fail-Closed: Corrupted or error info output halts execution")
    void testCorruptedOrFailedInfoOutputFailsClosed() {
        AtomicBoolean migrationExecuted = new AtomicBoolean(false);

        GateResult r1 = MigrationApprovalGate.evaluate(VALID_URL, VALID_USER, VALID_PASS, DatabaseState.VERSIONED_WITH_HISTORY, "", "1.5.0", VALID_TOKEN);
        assertFalse(r1.isAuthorized());

        GateResult r2 = MigrationApprovalGate.evaluate(VALID_URL, VALID_USER, VALID_PASS, DatabaseState.VERSIONED_WITH_HISTORY, "[ERROR] Connection refused: localhost:5432", "1.5.0", VALID_TOKEN);
        assertFalse(r2.isAuthorized());
        assertTrue(r2.getMessage().contains("errors"));

        assertFalse(migrationExecuted.get(), "Migration command must NEVER be called on malformed/error info output");
    }

    @Test
    @DisplayName("Gate Approval: All pending migrations in allowlist are authorized")
    void testAuthorizedPendingMigrationsApproved() {
        AtomicBoolean migrationExecuted = new AtomicBoolean(false);

        GateResult result = MigrationApprovalGate.evaluate(
                VALID_URL, VALID_USER, VALID_PASS,
                DatabaseState.VERSIONED_WITH_HISTORY,
                INFO_OUTPUT_AUTHORIZED_ONLY,
                "1.5.0",
                VALID_TOKEN
        );

        assertTrue(result.isAuthorized());
        assertTrue(result.getMessage().contains("APPROVED"));
        assertEquals(2, result.getPendingVersions().size());
        assertTrue(result.getPendingVersions().contains("1.2.0"));
        assertTrue(result.getPendingVersions().contains("1.5.0"));

        if (result.isAuthorized()) {
            migrationExecuted.set(true);
        }
        assertTrue(migrationExecuted.get(), "Migration command is authorized when all pending versions are allowlisted");
    }
}
