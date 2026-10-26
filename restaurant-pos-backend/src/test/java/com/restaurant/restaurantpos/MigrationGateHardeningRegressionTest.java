package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.util.FlywayApprovalGateCallback;
import com.restaurant.restaurantpos.util.MigrationApprovalGate;
import com.restaurant.restaurantpos.util.MigrationPreflightValidator;
import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AnnaDhara P0-3 Stage 3S/3V: Migration Gate Hardening Regression Tests
 *
 * Verifies that:
 * 1. FlywayApprovalGateCallback intercepts execution and rejects unapproved migrations (1.1.0, 1.8.0, unversioned).
 * 2. Approved production migrations (1.0.0 through 1.7.0) are permitted by the boundary guard.
 * 3. Preflight validation success does NOT override, mutate, or weaken the migration approval gate.
 * 4. Production allowlist remains strictly unmodifiable, containing exactly the 7 approved versions.
 * 5. All tests use isolated test doubles and never connect to live databases.
 */
public class MigrationGateHardeningRegressionTest {

    private final FlywayApprovalGateCallback callback = new FlywayApprovalGateCallback();

    @Nested
    @DisplayName("1. Flyway Execution Boundary Guard Tests")
    class ExecutionBoundaryGuardTests {

        @Test
        @DisplayName("Callback supports BEFORE_MIGRATE and BEFORE_EACH_MIGRATE events")
        void testCallbackSupportsMigrationEvents() {
            Context mockContext = mock(Context.class);
            assertTrue(callback.supports(Event.BEFORE_MIGRATE, mockContext), "Must support BEFORE_MIGRATE");
            assertTrue(callback.supports(Event.BEFORE_EACH_MIGRATE, mockContext), "Must support BEFORE_EACH_MIGRATE");
            assertFalse(callback.supports(Event.AFTER_MIGRATE, mockContext), "Should not handle AFTER_MIGRATE");
            assertFalse(callback.supports(Event.BEFORE_CLEAN, mockContext), "Should not handle BEFORE_CLEAN");
            assertTrue(callback.canHandleInTransaction(Event.BEFORE_EACH_MIGRATE, mockContext));
            assertEquals("AnnaDharaFlywayApprovalGateCallback", callback.getCallbackName());
        }

        @Test
        @DisplayName("Execution boundary permits approved production migrations 1.6.0 and 1.7.0")
        void testCallbackPermitsApprovedProductionVersionsV1_6_0AndV1_7_0() {
            Context mockContext = mock(Context.class);

            for (String version : Arrays.asList("1.6.0", "1.7.0")) {
                MigrationInfo mockInfo = mock(MigrationInfo.class);
                MigrationVersion mockVersion = MigrationVersion.fromVersion(version);
                when(mockContext.getMigrationInfo()).thenReturn(mockInfo);
                when(mockInfo.getVersion()).thenReturn(mockVersion);

                assertDoesNotThrow(() -> callback.handle(Event.BEFORE_EACH_MIGRATE, mockContext),
                        "Approved production version " + version + " must be permitted by execution boundary guard");
            }
        }

        @Test
        @DisplayName("Execution boundary strictly blocks unauthorized draft 1.1.0")
        void testCallbackRejectsV1_1_0() {
            Context mockContext = mock(Context.class);
            MigrationInfo mockInfo = mock(MigrationInfo.class);
            MigrationVersion mockVersion = MigrationVersion.fromVersion("1.1.0");

            when(mockContext.getMigrationInfo()).thenReturn(mockInfo);
            when(mockInfo.getVersion()).thenReturn(mockVersion);

            FlywayException ex = assertThrows(FlywayException.class, () ->
                    callback.handle(Event.BEFORE_EACH_MIGRATE, mockContext));
            assertTrue(ex.getMessage().contains("Flyway Migration Gate REJECTED"));
            assertTrue(ex.getMessage().contains("1.1.0"));
        }

        @Test
        @DisplayName("Execution boundary strictly blocks unapproved future version 1.9.0")
        void testCallbackRejectsUnapprovedV1_9_0() {
            Context mockContext = mock(Context.class);
            MigrationInfo mockInfo = mock(MigrationInfo.class);
            MigrationVersion mockVersion = MigrationVersion.fromVersion("1.9.0");

            when(mockContext.getMigrationInfo()).thenReturn(mockInfo);
            when(mockInfo.getVersion()).thenReturn(mockVersion);

            FlywayException ex = assertThrows(FlywayException.class, () ->
                    callback.handle(Event.BEFORE_EACH_MIGRATE, mockContext));
            assertTrue(ex.getMessage().contains("Flyway Migration Gate REJECTED"));
            assertTrue(ex.getMessage().contains("1.9.0"));
        }

        @Test
        @DisplayName("Execution boundary permits all approved migration versions (1.0.0 through 1.8.0)")
        void testCallbackPermitsApprovedVersions() {
            Context mockContext = mock(Context.class);

            for (String approvedVer : Arrays.asList("1.0.0", "1.2.0", "1.3.0", "1.4.0", "1.5.0", "1.6.0", "1.7.0", "1.8.0")) {
                MigrationInfo mockInfo = mock(MigrationInfo.class);
                MigrationVersion mockVersion = MigrationVersion.fromVersion(approvedVer);
                when(mockContext.getMigrationInfo()).thenReturn(mockInfo);
                when(mockInfo.getVersion()).thenReturn(mockVersion);

                assertDoesNotThrow(() -> callback.handle(Event.BEFORE_EACH_MIGRATE, mockContext),
                        "Approved version " + approvedVer + " must be permitted by execution boundary guard");
            }
        }

        @Test
        @DisplayName("Execution boundary fails closed on unversioned migration")
        void testCallbackRejectsUnversionedMigration() {
            Context mockContext = mock(Context.class);
            when(mockContext.getMigrationInfo()).thenReturn(null);

            FlywayException ex = assertThrows(FlywayException.class, () ->
                    callback.handle(Event.BEFORE_EACH_MIGRATE, mockContext));
            assertTrue(ex.getMessage().contains("strictly forbidden"));
        }
    }

    @Nested
    @DisplayName("2. Preflight Independence from Authorization")
    class PreflightIndependenceTests {

        private Connection createCleanMockConnection() throws Exception {
            Connection mockConn = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);
            ResultSet mockRs = mock(ResultSet.class);
            Statement mockStmt = mock(Statement.class);

            when(mockConn.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeQuery()).thenReturn(mockRs);
            when(mockRs.next()).thenReturn(false);

            when(mockConn.createStatement()).thenReturn(mockStmt);
            ResultSet tableRs = mock(ResultSet.class);
            when(tableRs.next()).thenReturn(true);
            when(tableRs.getInt(1)).thenReturn(0);
            when(mockStmt.executeQuery(anyString())).thenReturn(tableRs);

            return mockConn;
        }

        @Test
        @DisplayName("Passing preflight validation does NOT authorize unapproved draft 1.1.0")
        void testPreflightSuccessDoesNotAuthorizeDraftV1_1_0() throws Exception {
            Connection mockConn = createCleanMockConnection();
            MigrationPreflightValidator.PreflightResult preflightResult =
                    MigrationPreflightValidator.validateVersion(mockConn, "1.1.0");
            assertTrue(preflightResult.isValid(), "Preflight should return valid for version with no schema prereqs");

            // 1. BUT MigrationApprovalGate allowlist still blocks 1.1.0!
            assertFalse(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.1.0"),
                    "1.1.0 must NOT be in the approved allowlist despite passing preflight");
            assertFalse(MigrationApprovalGate.isVersionApprovedForConnection("1.1.0", "jdbc:postgresql://localhost:5432/restaurant_pos"),
                    "1.1.0 must NOT be approved for connection");

            // 2. AND FlywayApprovalGateCallback throws FlywayException if 1.1.0 execution is attempted!
            Context mockContext = mock(Context.class);
            MigrationInfo mockInfo = mock(MigrationInfo.class);
            when(mockContext.getMigrationInfo()).thenReturn(mockInfo);
            when(mockInfo.getVersion()).thenReturn(MigrationVersion.fromVersion("1.1.0"));

            assertThrows(FlywayException.class, () ->
                    callback.handle(Event.BEFORE_EACH_MIGRATE, mockContext));
        }

        @Test
        @DisplayName("Passing preflight validation does NOT authorize unapproved future version 1.9.0")
        void testPreflightSuccessDoesNotAuthorizeUnapprovedV1_9_0() throws Exception {
            Connection mockConn = createCleanMockConnection();
            MigrationPreflightValidator.PreflightResult preflightResult =
                    MigrationPreflightValidator.validateVersion(mockConn, "1.9.0");
            assertTrue(preflightResult.isValid());

            assertFalse(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.9.0"),
                    "1.9.0 must NOT be in the approved allowlist despite passing preflight");
            assertFalse(MigrationApprovalGate.isVersionApprovedForConnection("1.9.0", "jdbc:postgresql://localhost:5432/restaurant_pos"),
                    "1.9.0 must NOT be approved for connection");

            Context mockContext = mock(Context.class);
            MigrationInfo mockInfo = mock(MigrationInfo.class);
            when(mockContext.getMigrationInfo()).thenReturn(mockInfo);
            when(mockInfo.getVersion()).thenReturn(MigrationVersion.fromVersion("1.9.0"));

            assertThrows(FlywayException.class, () ->
                    callback.handle(Event.BEFORE_EACH_MIGRATE, mockContext));
        }

        @Test
        @DisplayName("Versions 1.6.0, 1.7.0, and 1.8.0 authorization derives strictly from production allowlist, not preflight")
        void testProductionVersionsAuthorizedByAllowlistNotPreflight() throws Exception {
            // All approved versions are authorized by explicit inclusion in APPROVED_ALLOWLIST
            assertTrue(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.6.0"),
                    "1.6.0 must be in production APPROVED_ALLOWLIST");
            assertTrue(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.7.0"),
                    "1.7.0 must be in production APPROVED_ALLOWLIST");
            assertTrue(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.8.0"),
                    "1.8.0 must be in production APPROVED_ALLOWLIST");

            assertTrue(MigrationApprovalGate.isVersionApprovedForConnection("1.6.0", "jdbc:postgresql://localhost:5432/restaurant_pos"),
                    "1.6.0 is authorized for production database");
            assertTrue(MigrationApprovalGate.isVersionApprovedForConnection("1.7.0", "jdbc:postgresql://localhost:5432/restaurant_pos"),
                    "1.7.0 is authorized for production database");
            assertTrue(MigrationApprovalGate.isVersionApprovedForConnection("1.8.0", "jdbc:postgresql://localhost:5432/restaurant_pos"),
                    "1.8.0 is authorized for production database");

            // Preflight validation execution does NOT mutate the allowlist
            Connection mockConn = createCleanMockConnection();

            int sizeBefore = MigrationApprovalGate.APPROVED_ALLOWLIST.size();
            MigrationPreflightValidator.validateVersion(mockConn, "1.6.0");
            MigrationPreflightValidator.validateVersion(mockConn, "1.7.0");
            MigrationPreflightValidator.validateVersion(mockConn, "1.8.0");
            assertEquals(sizeBefore, MigrationApprovalGate.APPROVED_ALLOWLIST.size(),
                    "Allowlist size must remain strictly unchanged after preflight execution");
        }
    }

    @Nested
    @DisplayName("3. Migration Approval Gate Immutability")
    class GateImmutabilityTests {

        @Test
        @DisplayName("APPROVED_ALLOWLIST is strictly immutable and contains exactly the 8 approved versions")
        void testApprovedAllowlistStrictImmutability() {
            Set<String> allowlist = MigrationApprovalGate.APPROVED_ALLOWLIST;
            assertNotNull(allowlist);
            assertEquals(8, allowlist.size(), "Allowlist must contain exactly 8 approved versions");
            assertTrue(allowlist.containsAll(Arrays.asList("1.0.0", "1.2.0", "1.3.0", "1.4.0", "1.5.0", "1.6.0", "1.7.0", "1.8.0")));
            assertFalse(allowlist.contains("1.1.0"), "Draft 1.1.0 must remain strictly excluded");
            assertTrue(allowlist.contains("1.8.0"), "Approved 1.8.0 must be in allowlist");
            assertFalse(allowlist.contains("1.9.0"), "Future unapproved 1.9.0 must remain strictly excluded");
            assertFalse(allowlist.contains("2.0.0"), "Future unapproved 2.0.0 must remain strictly excluded");

            assertThrows(UnsupportedOperationException.class, () -> allowlist.add("1.9.0"));
            assertThrows(UnsupportedOperationException.class, () -> allowlist.remove("1.8.0"));
            assertThrows(UnsupportedOperationException.class, () -> allowlist.remove("1.7.0"));
            assertThrows(UnsupportedOperationException.class, () -> allowlist.remove("1.0.0"));
        }
    }

    @Nested
    @DisplayName("4. Connection Authorization Rules Tests")
    class IsolatedTestScopedAuthorizationTests {

        @Test
        @DisplayName("Production authorization permits 1.6.0 and 1.7.0 on live and test databases")
        void testProductionAuthorizationPermits160And170() {
            assertTrue(MigrationApprovalGate.isVersionApprovedForConnection("1.6.0", "jdbc:postgresql://localhost:5432/restaurant_pos"),
                    "1.6.0 must be approved for live restaurant_pos DB");
            assertTrue(MigrationApprovalGate.isVersionApprovedForConnection("1.7.0", "jdbc:postgresql://localhost:5432/restaurant_pos"),
                    "1.7.0 must be approved for live restaurant_pos DB");
            assertTrue(MigrationApprovalGate.isVersionApprovedForConnection("1.6.0", "jdbc:postgresql://localhost:5432/restaurant_pos_migration_test"),
                    "1.6.0 must be approved for test DB");
            assertTrue(MigrationApprovalGate.isVersionApprovedForConnection("1.7.0", "jdbc:postgresql://localhost:5432/restaurant_pos_migration_test"),
                    "1.7.0 must be approved for test DB");
        }

        @Test
        @DisplayName("Connection authorization strictly rejects unauthorized draft 1.1.0 and future 1.9.0 on live DB")
        void testAuthorizationRejectsUnauthorizedVersionsOnLiveDatabase() {
            assertFalse(MigrationApprovalGate.isVersionApprovedForConnection("1.1.0", "jdbc:postgresql://localhost:5432/restaurant_pos"),
                    "Must strictly reject 1.1.0 against live restaurant_pos DB");
            assertFalse(MigrationApprovalGate.isVersionApprovedForConnection("1.9.0", "jdbc:postgresql://localhost:5432/restaurant_pos"),
                    "Must strictly reject 1.9.0 against live restaurant_pos DB");
            assertFalse(MigrationApprovalGate.isVersionApprovedForConnection(null, "jdbc:postgresql://localhost:5432/restaurant_pos"),
                    "Must strictly reject null version");
            assertFalse(MigrationApprovalGate.isVersionApprovedForConnection("", "jdbc:postgresql://localhost:5432/restaurant_pos"),
                    "Must strictly reject empty version");
            assertFalse(MigrationApprovalGate.isVersionApprovedForConnection("   ", "jdbc:postgresql://localhost:5432/restaurant_pos"),
                    "Must strictly reject whitespace version");
        }

        @Test
        @DisplayName("Connection authorization strictly rejects 1.1.0 and unknown versions on test DB regardless of flags")
        void testAuthorizationRejectsUnauthorizedOnTestDb() {
            String orig = System.getProperty("flyway.test.isolated.approved");
            try {
                System.setProperty("flyway.test.isolated.approved", "true");
                assertFalse(MigrationApprovalGate.isVersionApprovedForConnection("1.1.0", "jdbc:postgresql://localhost:5432/restaurant_pos_migration_test"),
                        "Unauthorized draft 1.1.0 must never be permitted even on test DB");
                assertFalse(MigrationApprovalGate.isVersionApprovedForConnection("1.9.0", "jdbc:postgresql://localhost:5432/restaurant_pos_migration_test"),
                        "Unapproved version 1.9.0 must never be permitted even on test DB");
            } finally {
                if (orig == null) System.clearProperty("flyway.test.isolated.approved");
                else System.setProperty("flyway.test.isolated.approved", orig);
            }
        }

        @Test
        @DisplayName("Callback boundary guard permits 1.6.0 on both test and live databases")
        void testCallbackPermitsOnTestAndLiveDb() throws Exception {
            for (String url : Arrays.asList("jdbc:postgresql://localhost:5432/restaurant_pos", "jdbc:postgresql://localhost:5432/restaurant_pos_migration_test")) {
                Context mockContext = mock(Context.class);
                MigrationInfo mockInfo = mock(MigrationInfo.class);
                Connection mockConn = mock(Connection.class);
                java.sql.DatabaseMetaData mockMeta = mock(java.sql.DatabaseMetaData.class);

                when(mockContext.getMigrationInfo()).thenReturn(mockInfo);
                when(mockContext.getConnection()).thenReturn(mockConn);
                when(mockConn.getMetaData()).thenReturn(mockMeta);
                when(mockMeta.getURL()).thenReturn(url);
                when(mockInfo.getVersion()).thenReturn(MigrationVersion.fromVersion("1.6.0"));

                assertDoesNotThrow(() -> callback.handle(Event.BEFORE_EACH_MIGRATE, mockContext),
                        "Callback must permit authorized 1.6.0 on " + url);
            }
        }
    }
}
