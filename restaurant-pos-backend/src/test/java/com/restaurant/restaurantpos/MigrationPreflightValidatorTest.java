package com.restaurant.restaurantpos;

import com.restaurant.restaurantpos.util.MigrationApprovalGate;
import com.restaurant.restaurantpos.util.MigrationPreflightValidator;
import com.restaurant.restaurantpos.util.MigrationPreflightValidator.PreflightResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AnnaDhara P0-3 Stage 3S — Migration Preflight Validator Unit Tests
 *
 * Verifies that MigrationPreflightValidator strictly validates PostgreSQL catalog schemas
 * read-only, catches incompatible pre-existing columns/constraints/indexes, validates semantic
 * default expressions (rejecting 10, 15, 21, 0, null), and fails closed without altering tables.
 */
public class MigrationPreflightValidatorTest {

    @Nested
    @DisplayName("1. Semantic Integer Default Expression Tests")
    class SemanticDefaultExpressionTests {

        @Test
        @DisplayName("Valid PostgreSQL integer-one default representations are accepted")
        void testValidIntegerOneDefaultsAccepted() {
            assertTrue(MigrationPreflightValidator.isPostgresIntegerOneDefault("1"));
            assertTrue(MigrationPreflightValidator.isPostgresIntegerOneDefault("'1'"));
            assertTrue(MigrationPreflightValidator.isPostgresIntegerOneDefault("'1'::integer"));
            assertTrue(MigrationPreflightValidator.isPostgresIntegerOneDefault("1::integer"));
            assertTrue(MigrationPreflightValidator.isPostgresIntegerOneDefault("'1'::bigint"));
            assertTrue(MigrationPreflightValidator.isPostgresIntegerOneDefault("'1'::smallint"));
            assertTrue(MigrationPreflightValidator.isPostgresIntegerOneDefault("('1'::integer)"));
            assertTrue(MigrationPreflightValidator.isPostgresIntegerOneDefault("((1))"));
            assertTrue(MigrationPreflightValidator.isPostgresIntegerOneDefault(" '1'::integer "));
        }

        @Test
        @DisplayName("Valid PostgreSQL numeric-zero default representations are accepted")
        void testValidNumericZeroDefaultsAccepted() {
            assertTrue(MigrationPreflightValidator.isPostgresNumericZeroDefault("0"));
            assertTrue(MigrationPreflightValidator.isPostgresNumericZeroDefault("0.0"));
            assertTrue(MigrationPreflightValidator.isPostgresNumericZeroDefault("0.00"));
            assertTrue(MigrationPreflightValidator.isPostgresNumericZeroDefault("'0.00'::numeric"));
            assertTrue(MigrationPreflightValidator.isPostgresNumericZeroDefault("'0'::numeric"));
            assertTrue(MigrationPreflightValidator.isPostgresNumericZeroDefault("0::numeric"));
            assertTrue(MigrationPreflightValidator.isPostgresNumericZeroDefault("((0.00)::numeric)"));
            assertTrue(MigrationPreflightValidator.isPostgresNumericZeroDefault("('0.00'::numeric)"));
            assertTrue(MigrationPreflightValidator.isPostgresNumericZeroDefault(" '0.00'::numeric "));
        }

        @Test
        @DisplayName("Invalid PostgreSQL numeric-zero default representations are rejected")
        void testInvalidNumericZeroDefaultsRejected() {
            assertFalse(MigrationPreflightValidator.isPostgresNumericZeroDefault("1"));
            assertFalse(MigrationPreflightValidator.isPostgresNumericZeroDefault("10"));
            assertFalse(MigrationPreflightValidator.isPostgresNumericZeroDefault("0.01"));
            assertFalse(MigrationPreflightValidator.isPostgresNumericZeroDefault("-0.01"));
            assertFalse(MigrationPreflightValidator.isPostgresNumericZeroDefault("null"));
            assertFalse(MigrationPreflightValidator.isPostgresNumericZeroDefault(null));
            assertFalse(MigrationPreflightValidator.isPostgresNumericZeroDefault(""));
            assertFalse(MigrationPreflightValidator.isPostgresNumericZeroDefault("nextval('seq')"));
        }

        @Test
        @DisplayName("Valid and invalid GST_EXCLUDED default expressions")
        void testGstExcludedDefaultExpressions() {
            assertTrue(MigrationPreflightValidator.isPostgresGstExcludedDefault("GST_EXCLUDED"));
            assertTrue(MigrationPreflightValidator.isPostgresGstExcludedDefault("'GST_EXCLUDED'"));
            assertTrue(MigrationPreflightValidator.isPostgresGstExcludedDefault("'GST_EXCLUDED'::character varying"));
            assertTrue(MigrationPreflightValidator.isPostgresGstExcludedDefault("'GST_EXCLUDED'::varchar"));
            assertTrue(MigrationPreflightValidator.isPostgresGstExcludedDefault("('GST_EXCLUDED'::character varying)"));

            assertFalse(MigrationPreflightValidator.isPostgresGstExcludedDefault("GST_INCLUDED"));
            assertFalse(MigrationPreflightValidator.isPostgresGstExcludedDefault("'GST_INCLUDED'::character varying"));
            assertFalse(MigrationPreflightValidator.isPostgresGstExcludedDefault("1"));
            assertFalse(MigrationPreflightValidator.isPostgresGstExcludedDefault(null));
            assertFalse(MigrationPreflightValidator.isPostgresGstExcludedDefault(""));
        }
    }

    @Nested
    @DisplayName("2. Token Version Column Preflight Validation")
    class TokenVersionColumnTests {

        @Test
        @DisplayName("Clean state: column does not pre-exist -> validation passes")
        void testColumnDoesNotExistPasses() throws Exception {
            Connection mockConn = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);
            ResultSet mockRs = mock(ResultSet.class);

            when(mockConn.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeQuery()).thenReturn(mockRs);
            when(mockRs.next()).thenReturn(false); // Column does not exist

            PreflightResult result = MigrationPreflightValidator.validateTokenVersionColumn(mockConn, "users");
            assertTrue(result.isValid(), "Clean state without pre-existing column must be valid");
            assertTrue(result.getMessage().contains("does not pre-exist"));
        }

        @Test
        @DisplayName("Compatible pre-existing column (integer NOT NULL DEFAULT '1'::integer) -> validation passes")
        void testCompatiblePreExistingColumnPasses() throws Exception {
            Connection mockConn = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);
            ResultSet mockRs = mock(ResultSet.class);

            when(mockConn.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeQuery()).thenReturn(mockRs);
            when(mockRs.next()).thenReturn(true);
            when(mockRs.getString("data_type")).thenReturn("integer");
            when(mockRs.getString("is_nullable")).thenReturn("NO");
            when(mockRs.getString("column_default")).thenReturn("'1'::integer");

            PreflightResult result = MigrationPreflightValidator.validateTokenVersionColumn(mockConn, "users");
            assertTrue(result.isValid(), "Compatible column must pass preflight");
            assertTrue(result.getMessage().contains("is compatible"));
        }

        @Test
        @DisplayName("Incompatible data type (varchar) -> rejected with sanitized error")
        void testIncompatibleDataTypeRejected() throws Exception {
            Connection mockConn = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);
            ResultSet mockRs = mock(ResultSet.class);

            when(mockConn.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeQuery()).thenReturn(mockRs);
            when(mockRs.next()).thenReturn(true);
            when(mockRs.getString("data_type")).thenReturn("character varying");
            when(mockRs.getString("is_nullable")).thenReturn("NO");
            when(mockRs.getString("column_default")).thenReturn("1");

            PreflightResult result = MigrationPreflightValidator.validateTokenVersionColumn(mockConn, "users");
            assertFalse(result.isValid(), "Incompatible varchar type must be rejected");
            assertTrue(result.getMessage().contains("Incompatible pre-existing column"));
            assertTrue(result.getMessage().contains("character varying"));
            assertEquals("users.token_version", result.getResource());
        }

        @Test
        @DisplayName("Incompatible nullability (is_nullable=YES) -> rejected with sanitized error")
        void testNullableColumnRejected() throws Exception {
            Connection mockConn = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);
            ResultSet mockRs = mock(ResultSet.class);

            when(mockConn.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeQuery()).thenReturn(mockRs);
            when(mockRs.next()).thenReturn(true);
            when(mockRs.getString("data_type")).thenReturn("integer");
            when(mockRs.getString("is_nullable")).thenReturn("YES");
            when(mockRs.getString("column_default")).thenReturn("1");

            PreflightResult result = MigrationPreflightValidator.validateTokenVersionColumn(mockConn, "restaurants");
            assertFalse(result.isValid(), "Nullable column must be rejected");
            assertTrue(result.getMessage().contains("is nullable"));
            assertEquals("restaurants.token_version", result.getResource());
        }

        @Test
        @DisplayName("Incompatible default value (default '10' or '0') -> rejected with sanitized error")
        void testIncompatibleDefaultRejected() throws Exception {
            Connection mockConn = mock(Connection.class);
            PreparedStatement mockPs = mock(PreparedStatement.class);
            ResultSet mockRs = mock(ResultSet.class);

            when(mockConn.prepareStatement(anyString())).thenReturn(mockPs);
            when(mockPs.executeQuery()).thenReturn(mockRs);
            when(mockRs.next()).thenReturn(true);
            when(mockRs.getString("data_type")).thenReturn("integer");
            when(mockRs.getString("is_nullable")).thenReturn("NO");
            when(mockRs.getString("column_default")).thenReturn("'10'::integer");

            PreflightResult result = MigrationPreflightValidator.validateTokenVersionColumn(mockConn, "users");
            assertFalse(result.isValid(), "Incompatible default 10 must be rejected");
            assertTrue(result.getMessage().contains("default is ''10'::integer'"));
        }
    }

    @Nested
    @DisplayName("3. Revoked Tokens Table Preflight Validation")
    class RevokedTokensTableTests {

        @Test
        @DisplayName("Clean state: table does not pre-exist -> validation passes")
        void testTableDoesNotExistPasses() throws Exception {
            Connection mockConn = mock(Connection.class);
            Statement mockStmt = mock(Statement.class);
            ResultSet mockRs = mock(ResultSet.class);

            when(mockConn.createStatement()).thenReturn(mockStmt);
            when(mockStmt.executeQuery(contains("count(*) FROM information_schema.tables WHERE table_name = 'revoked_tokens'")))
                    .thenReturn(mockRs);
            when(mockRs.next()).thenReturn(true);
            when(mockRs.getInt(1)).thenReturn(0); // 0 tables

            PreflightResult result = MigrationPreflightValidator.validateRevokedTokensTable(mockConn);
            assertTrue(result.isValid(), "Clean state without table must pass");
            assertTrue(result.getMessage().contains("does not pre-exist"));
        }

        @Test
        @DisplayName("Table pre-exists with missing user_id column -> rejected")
        void testTableMissingColumnRejected() throws Exception {
            Connection mockConn = mock(Connection.class);
            Statement mockStmt = mock(Statement.class);

            ResultSet countRs = mock(ResultSet.class);
            when(countRs.next()).thenReturn(true);
            when(countRs.getInt(1)).thenReturn(1); // table exists

            ResultSet colRs = mock(ResultSet.class);
            when(colRs.next()).thenReturn(true, false);
            when(colRs.getString("column_name")).thenReturn("jti");
            when(colRs.getString("data_type")).thenReturn("character varying");
            when(colRs.getString("is_nullable")).thenReturn("NO");

            when(mockConn.createStatement()).thenReturn(mockStmt);
            when(mockStmt.executeQuery(contains("count(*) FROM information_schema.tables WHERE table_name = 'revoked_tokens'")))
                    .thenReturn(countRs);
            when(mockStmt.executeQuery(contains("SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'revoked_tokens'")))
                    .thenReturn(colRs);

            PreflightResult result = MigrationPreflightValidator.validateRevokedTokensTable(mockConn);
            assertFalse(result.isValid(), "Table missing user_id column must be rejected");
            assertTrue(result.getMessage().contains("missing required 'user_id' column"));
        }

        @Test
        @DisplayName("Table pre-exists without primary key on jti -> rejected")
        void testTableMissingPrimaryKeyRejected() throws Exception {
            Connection mockConn = mock(Connection.class);
            Statement mockStmt = mock(Statement.class);

            ResultSet countRs = mock(ResultSet.class);
            when(countRs.next()).thenReturn(true);
            when(countRs.getInt(1)).thenReturn(1);

            ResultSet colRs = mock(ResultSet.class);
            when(colRs.next()).thenReturn(true, true, true, true, true, false);
            when(colRs.getString("column_name")).thenReturn("jti", "user_id", "restaurant_id", "expires_at", "revoked_at");
            when(colRs.getString("data_type")).thenReturn("character varying", "bigint", "bigint", "timestamp with time zone", "timestamp with time zone");
            when(colRs.getString("is_nullable")).thenReturn("NO", "NO", "YES", "NO", "NO");

            ResultSet pkRs = mock(ResultSet.class);
            when(pkRs.next()).thenReturn(false);

            when(mockConn.createStatement()).thenReturn(mockStmt);
            when(mockStmt.executeQuery(contains("count(*) FROM information_schema.tables WHERE table_name = 'revoked_tokens'")))
                    .thenReturn(countRs);
            when(mockStmt.executeQuery(contains("SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'revoked_tokens'")))
                    .thenReturn(colRs);
            when(mockStmt.executeQuery(contains("table_constraints")))
                    .thenReturn(pkRs);

            PreflightResult result = MigrationPreflightValidator.validateRevokedTokensTable(mockConn);
            assertFalse(result.isValid(), "Table missing primary key on jti must be rejected");
            assertTrue(result.getMessage().contains("does not have a primary key constraint on 'jti'"));
        }

        @Test
        @DisplayName("Table pre-exists on PostgreSQL with missing required index -> rejected")
        void testTableMissingRequiredIndexRejected() throws Exception {
            Connection mockConn = mock(Connection.class);
            Statement mockStmt = mock(Statement.class);
            DatabaseMetaData mockMeta = mock(DatabaseMetaData.class);

            when(mockConn.getMetaData()).thenReturn(mockMeta);
            when(mockMeta.getDatabaseProductName()).thenReturn("PostgreSQL");

            ResultSet countRs = mock(ResultSet.class);
            when(countRs.next()).thenReturn(true);
            when(countRs.getInt(1)).thenReturn(1);

            ResultSet colRs = mock(ResultSet.class);
            when(colRs.next()).thenReturn(true, true, true, true, true, false);
            when(colRs.getString("column_name")).thenReturn("jti", "user_id", "restaurant_id", "expires_at", "revoked_at");
            when(colRs.getString("data_type")).thenReturn("character varying", "bigint", "bigint", "timestamp with time zone", "timestamp with time zone");
            when(colRs.getString("is_nullable")).thenReturn("NO", "NO", "YES", "NO", "NO");

            ResultSet pkRs = mock(ResultSet.class);
            when(pkRs.next()).thenReturn(true, false);
            when(pkRs.getString(1)).thenReturn("jti");

            // Index query returns only one index, missing idx_revoked_tokens_user_id
            ResultSet idxRs = mock(ResultSet.class);
            when(idxRs.next()).thenReturn(true, false);
            when(idxRs.getString("indexname")).thenReturn("idx_revoked_tokens_expires_at");

            when(mockConn.createStatement()).thenReturn(mockStmt);
            when(mockStmt.executeQuery(contains("count(*) FROM information_schema.tables WHERE table_name = 'revoked_tokens'")))
                    .thenReturn(countRs);
            when(mockStmt.executeQuery(contains("SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'revoked_tokens'")))
                    .thenReturn(colRs);
            when(mockStmt.executeQuery(contains("table_constraints")))
                    .thenReturn(pkRs);
            when(mockStmt.executeQuery(contains("pg_indexes WHERE tablename = 'revoked_tokens'")))
                    .thenReturn(idxRs);

            PreflightResult result = MigrationPreflightValidator.validateRevokedTokensTable(mockConn);
            assertFalse(result.isValid(), "Table missing user_id index must be rejected");
            assertTrue(result.getMessage().contains("missing required index 'idx_revoked_tokens_user_id'"));
        }

        @Test
        @DisplayName("Table pre-exists on PostgreSQL with all columns, PK, and indexes -> validation passes")
        void testTableWithColumnsPkAndIndexesPasses() throws Exception {
            Connection mockConn = mock(Connection.class);
            Statement mockStmt = mock(Statement.class);
            DatabaseMetaData mockMeta = mock(DatabaseMetaData.class);

            when(mockConn.getMetaData()).thenReturn(mockMeta);
            when(mockMeta.getDatabaseProductName()).thenReturn("PostgreSQL");

            ResultSet countRs = mock(ResultSet.class);
            when(countRs.next()).thenReturn(true);
            when(countRs.getInt(1)).thenReturn(1);

            ResultSet colRs = mock(ResultSet.class);
            when(colRs.next()).thenReturn(true, true, true, true, true, false);
            when(colRs.getString("column_name")).thenReturn("jti", "user_id", "restaurant_id", "expires_at", "revoked_at");
            when(colRs.getString("data_type")).thenReturn("character varying", "bigint", "bigint", "timestamp with time zone", "timestamp with time zone");
            when(colRs.getString("is_nullable")).thenReturn("NO", "NO", "YES", "NO", "NO");

            ResultSet pkRs = mock(ResultSet.class);
            when(pkRs.next()).thenReturn(true, false);
            when(pkRs.getString(1)).thenReturn("jti");

            // Both required indexes present
            ResultSet idxRs = mock(ResultSet.class);
            when(idxRs.next()).thenReturn(true, true, false);
            when(idxRs.getString("indexname")).thenReturn("idx_revoked_tokens_expires_at", "idx_revoked_tokens_user_id");

            when(mockConn.createStatement()).thenReturn(mockStmt);
            when(mockStmt.executeQuery(contains("count(*) FROM information_schema.tables WHERE table_name = 'revoked_tokens'")))
                    .thenReturn(countRs);
            when(mockStmt.executeQuery(contains("SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'revoked_tokens'")))
                    .thenReturn(colRs);
            when(mockStmt.executeQuery(contains("table_constraints")))
                    .thenReturn(pkRs);
            when(mockStmt.executeQuery(contains("pg_indexes WHERE tablename = 'revoked_tokens'")))
                    .thenReturn(idxRs);

            PreflightResult result = MigrationPreflightValidator.validateRevokedTokensTable(mockConn);
            assertTrue(result.isValid(), "Table with all requirements met must pass preflight");
            assertTrue(result.getMessage().contains("matches all required columns"));
        }
    }

    @Nested
    @DisplayName("4. V1_8_0 (GST Schema & Data) Preflight Validation Tests")
    class V1_8_0_PreflightValidationTests {

        private void setupMockSchema(Connection mockConn) throws Exception {
            Statement stmt = mock(Statement.class);
            when(mockConn.createStatement()).thenReturn(stmt);
            ResultSet schemaRs = mock(ResultSet.class);
            when(schemaRs.next()).thenReturn(true);
            when(schemaRs.getString(1)).thenReturn("public");
            when(stmt.executeQuery(contains("current_schema()"))).thenReturn(schemaRs);
        }

        @Test
        @DisplayName("Missing base tables (restaurant_settings, orders, bills) -> rejected fail-closed")
        void testMissingBaseTablesRejected() throws Exception {
            Connection mockConn = mock(Connection.class);
            setupMockSchema(mockConn);

            PreparedStatement psTables = mock(PreparedStatement.class);
            ResultSet rsTables = mock(ResultSet.class);
            when(mockConn.prepareStatement(contains("information_schema.tables"))).thenReturn(psTables);
            when(psTables.executeQuery()).thenReturn(rsTables);
            when(rsTables.next()).thenReturn(true);
            when(rsTables.getInt(1)).thenReturn(2); // Only 2 of 3 base tables exist

            PreflightResult result = MigrationPreflightValidator.validateV1_8_0(mockConn);
            assertFalse(result.isValid());
            assertEquals("base_tables", result.getResource());
            assertTrue(result.getMessage().contains("do not all exist"));
        }

        @Test
        @DisplayName("Clean state: 0 of 13 columns exist and valid data -> validation passes")
        void testCleanSchemaPasses() throws Exception {
            Connection mockConn = mock(Connection.class);
            setupMockSchema(mockConn);

            PreparedStatement psTables = mock(PreparedStatement.class);
            ResultSet rsTables = mock(ResultSet.class);
            when(mockConn.prepareStatement(contains("information_schema.tables"))).thenReturn(psTables);
            when(psTables.executeQuery()).thenReturn(rsTables);
            when(rsTables.next()).thenReturn(true);
            when(rsTables.getInt(1)).thenReturn(3); // All 3 base tables exist

            PreparedStatement psCols = mock(PreparedStatement.class);
            ResultSet rsCols = mock(ResultSet.class);
            when(mockConn.prepareStatement(contains("information_schema.columns"))).thenReturn(psCols);
            when(psCols.executeQuery()).thenReturn(rsCols);
            when(rsCols.next()).thenReturn(false); // 0 columns exist

            Statement stmt = mock(Statement.class);
            when(mockConn.createStatement()).thenReturn(stmt);
            ResultSet rateRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("default_tax_rate < 0.00"))).thenReturn(rateRs);
            when(rateRs.next()).thenReturn(true);
            when(rateRs.getInt(1)).thenReturn(0); // 0 negative rates

            PreflightResult result = MigrationPreflightValidator.validateV1_8_0(mockConn);
            assertTrue(result.isValid());
            assertTrue(result.getMessage().contains("Clean schema: all 13 target GST columns are absent"));
        }

        @Test
        @DisplayName("Negative default_tax_rate in clean schema -> rejected fail-closed")
        void testNegativeDefaultTaxRateInCleanSchemaRejected() throws Exception {
            Connection mockConn = mock(Connection.class);
            setupMockSchema(mockConn);

            PreparedStatement psTables = mock(PreparedStatement.class);
            ResultSet rsTables = mock(ResultSet.class);
            when(mockConn.prepareStatement(contains("information_schema.tables"))).thenReturn(psTables);
            when(psTables.executeQuery()).thenReturn(rsTables);
            when(rsTables.next()).thenReturn(true);
            when(rsTables.getInt(1)).thenReturn(3);

            PreparedStatement psCols = mock(PreparedStatement.class);
            ResultSet rsCols = mock(ResultSet.class);
            when(mockConn.prepareStatement(contains("information_schema.columns"))).thenReturn(psCols);
            when(psCols.executeQuery()).thenReturn(rsCols);
            when(rsCols.next()).thenReturn(false);

            Statement stmt = mock(Statement.class);
            when(mockConn.createStatement()).thenReturn(stmt);
            ResultSet rateRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("default_tax_rate < 0.00"))).thenReturn(rateRs);
            when(rateRs.next()).thenReturn(true);
            when(rateRs.getInt(1)).thenReturn(1);

            PreflightResult result = MigrationPreflightValidator.validateV1_8_0(mockConn);
            assertFalse(result.isValid());
            assertEquals("restaurant_settings.default_tax_rate", result.getResource());
            assertTrue(result.getMessage().contains("negative default_tax_rate detected"));
        }

        @Test
        @DisplayName("Partial schema drift (3 of 13 columns present) -> rejected fail-closed")
        void testPartialSchemaDriftRejected() throws Exception {
            Connection mockConn = mock(Connection.class);
            setupMockSchema(mockConn);

            PreparedStatement psTables = mock(PreparedStatement.class);
            ResultSet rsTables = mock(ResultSet.class);
            when(mockConn.prepareStatement(contains("information_schema.tables"))).thenReturn(psTables);
            when(psTables.executeQuery()).thenReturn(rsTables);
            when(rsTables.next()).thenReturn(true);
            when(rsTables.getInt(1)).thenReturn(3);

            PreparedStatement psCols = mock(PreparedStatement.class);
            ResultSet rsCols = mock(ResultSet.class);
            when(mockConn.prepareStatement(contains("information_schema.columns"))).thenReturn(psCols);
            when(psCols.executeQuery()).thenReturn(rsCols);

            // Mock only 3 columns returning
            when(rsCols.next()).thenReturn(true, true, true, false);
            when(rsCols.getString("table_name")).thenReturn("restaurant_settings", "restaurant_settings", "orders");
            when(rsCols.getString("column_name")).thenReturn("gst_mode", "cgst_rate", "gst_mode");
            when(rsCols.getString("data_type")).thenReturn("character varying", "numeric", "character varying");
            when(rsCols.getObject("character_maximum_length")).thenReturn(20, null, 20);
            when(rsCols.getObject("numeric_precision")).thenReturn(null, 5, null);
            when(rsCols.getObject("numeric_scale")).thenReturn(null, 2, null);
            when(rsCols.getString("is_nullable")).thenReturn("NO", "NO", "YES");
            when(rsCols.getString("column_default")).thenReturn("'GST_EXCLUDED'", "0.00", null);

            PreflightResult result = MigrationPreflightValidator.validateV1_8_0(mockConn);
            assertFalse(result.isValid());
            assertEquals("gst_schema", result.getResource());
            assertTrue(result.getMessage().contains("Partial schema drift detected: found 3 of 13"));
        }

        @Test
        @DisplayName("Unrecorded schema (all 13 columns present but V1.8.0 not in Flyway history) -> rejected")
        void testUnrecordedSchemaRejected() throws Exception {
            Connection mockConn = mock(Connection.class);
            setupMockSchema(mockConn);

            PreparedStatement psTables = mock(PreparedStatement.class);
            ResultSet rsTables = mock(ResultSet.class);
            when(mockConn.prepareStatement(contains("information_schema.tables"))).thenReturn(psTables);
            when(psTables.executeQuery()).thenReturn(rsTables);
            when(rsTables.next()).thenReturn(true);
            when(rsTables.getInt(1)).thenReturn(3);

            // Mock 13 columns present
            mock13ColumnsResultSet(mockConn, false);

            Statement stmt = mock(Statement.class);
            when(mockConn.createStatement()).thenReturn(stmt);

            // History table exists
            ResultSet histTableRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("table_name = 'flyway_schema_history'"))).thenReturn(histTableRs);
            when(histTableRs.next()).thenReturn(true);
            when(histTableRs.getInt(1)).thenReturn(1);

            // But 1.8.0 is NOT recorded as successful
            ResultSet verRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("version = '1.8.0' AND success = true"))).thenReturn(verRs);
            when(verRs.next()).thenReturn(true);
            when(verRs.getInt(1)).thenReturn(0);

            PreflightResult result = MigrationPreflightValidator.validateV1_8_0(mockConn);
            assertFalse(result.isValid());
            assertEquals("gst_schema", result.getResource());
            assertTrue(result.getMessage().contains("Unrecorded schema drift detected"));
        }

        @Test
        @DisplayName("Incompatible column length (orders.gst_mode VARCHAR(50) instead of VARCHAR(20)) -> rejected")
        void testIncompatibleColumnDefinitionRejected() throws Exception {
            Connection mockConn = mock(Connection.class);
            setupMockSchema(mockConn);

            PreparedStatement psTables = mock(PreparedStatement.class);
            ResultSet rsTables = mock(ResultSet.class);
            when(mockConn.prepareStatement(contains("information_schema.tables"))).thenReturn(psTables);
            when(psTables.executeQuery()).thenReturn(rsTables);
            when(rsTables.next()).thenReturn(true);
            when(rsTables.getInt(1)).thenReturn(3);

            // Mock 13 columns with orders.gst_mode having length 50
            mock13ColumnsResultSet(mockConn, true);

            Statement stmt = mock(Statement.class);
            when(mockConn.createStatement()).thenReturn(stmt);

            ResultSet histTableRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("table_name = 'flyway_schema_history'"))).thenReturn(histTableRs);
            when(histTableRs.next()).thenReturn(true);
            when(histTableRs.getInt(1)).thenReturn(1);

            ResultSet verRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("version = '1.8.0' AND success = true"))).thenReturn(verRs);
            when(verRs.next()).thenReturn(true);
            when(verRs.getInt(1)).thenReturn(1);

            PreflightResult result = MigrationPreflightValidator.validateV1_8_0(mockConn);
            assertFalse(result.isValid());
            assertEquals("orders.gst_mode", result.getResource());
            assertTrue(result.getMessage().contains("expected 20, got 50"));
        }

        @Test
        @DisplayName("Rate discrepancy in restaurant_settings (cgst + sgst != default) -> rejected")
        void testRateDiscrepancyRejected() throws Exception {
            Connection mockConn = mock(Connection.class);
            setupMockSchema(mockConn);

            PreparedStatement psTables = mock(PreparedStatement.class);
            ResultSet rsTables = mock(ResultSet.class);
            when(mockConn.prepareStatement(contains("information_schema.tables"))).thenReturn(psTables);
            when(psTables.executeQuery()).thenReturn(rsTables);
            when(rsTables.next()).thenReturn(true);
            when(rsTables.getInt(1)).thenReturn(3);

            mock13ColumnsResultSet(mockConn, false);

            Statement stmt = mock(Statement.class);
            when(mockConn.createStatement()).thenReturn(stmt);

            ResultSet histTableRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("table_name = 'flyway_schema_history'"))).thenReturn(histTableRs);
            when(histTableRs.next()).thenReturn(true);
            when(histTableRs.getInt(1)).thenReturn(1);

            ResultSet verRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("version = '1.8.0' AND success = true"))).thenReturn(verRs);
            when(verRs.next()).thenReturn(true);
            when(verRs.getInt(1)).thenReturn(1);

            ResultSet rateRangeRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("cgst_rate < 0.00 OR sgst_rate < 0.00"))).thenReturn(rateRangeRs);
            when(rateRangeRs.next()).thenReturn(true);
            when(rateRangeRs.getInt(1)).thenReturn(0);

            // Discrepancy found
            ResultSet rateSumRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("(cgst_rate + sgst_rate) != default_tax_rate"))).thenReturn(rateSumRs);
            when(rateSumRs.next()).thenReturn(true);
            when(rateSumRs.getInt(1)).thenReturn(1); // 1 row has discrepancy

            PreflightResult result = MigrationPreflightValidator.validateV1_8_0(mockConn);
            assertFalse(result.isValid());
            assertEquals("restaurant_settings.rates", result.getResource());
            assertTrue(result.getMessage().contains("Rate discrepancy detected"));
        }

        @Test
        @DisplayName("Negative component rates in restaurant_settings -> rejected fail-closed")
        void testNegativeComponentRatesRejected() throws Exception {
            Connection mockConn = mock(Connection.class);
            setupMockSchema(mockConn);

            PreparedStatement psTables = mock(PreparedStatement.class);
            ResultSet rsTables = mock(ResultSet.class);
            when(mockConn.prepareStatement(contains("information_schema.tables"))).thenReturn(psTables);
            when(psTables.executeQuery()).thenReturn(rsTables);
            when(rsTables.next()).thenReturn(true);
            when(rsTables.getInt(1)).thenReturn(3);

            mock13ColumnsResultSet(mockConn, false);

            Statement stmt = mock(Statement.class);
            when(mockConn.createStatement()).thenReturn(stmt);

            ResultSet histTableRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("table_name = 'flyway_schema_history'"))).thenReturn(histTableRs);
            when(histTableRs.next()).thenReturn(true);
            when(histTableRs.getInt(1)).thenReturn(1);

            ResultSet verRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("version = '1.8.0' AND success = true"))).thenReturn(verRs);
            when(verRs.next()).thenReturn(true);
            when(verRs.getInt(1)).thenReturn(1);

            ResultSet rateRangeRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("cgst_rate < 0.00 OR sgst_rate < 0.00"))).thenReturn(rateRangeRs);
            when(rateRangeRs.next()).thenReturn(true);
            when(rateRangeRs.getInt(1)).thenReturn(1); // Negative rate detected

            PreflightResult result = MigrationPreflightValidator.validateV1_8_0(mockConn);
            assertFalse(result.isValid());
            assertEquals("restaurant_settings.rates", result.getResource());
            assertTrue(result.getMessage().contains("Invalid GST component rates detected"));
        }

        @Test
        @DisplayName("Compatible pre-existing schema and valid data -> validation passes")
        void testCompatiblePreExistingSchemaPasses() throws Exception {
            Connection mockConn = mock(Connection.class);
            setupMockSchema(mockConn);

            PreparedStatement psTables = mock(PreparedStatement.class);
            ResultSet rsTables = mock(ResultSet.class);
            when(mockConn.prepareStatement(contains("information_schema.tables"))).thenReturn(psTables);
            when(psTables.executeQuery()).thenReturn(rsTables);
            when(rsTables.next()).thenReturn(true);
            when(rsTables.getInt(1)).thenReturn(3);

            mock13ColumnsResultSet(mockConn, false);

            Statement stmt = mock(Statement.class);
            when(mockConn.createStatement()).thenReturn(stmt);

            ResultSet histTableRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("table_name = 'flyway_schema_history'"))).thenReturn(histTableRs);
            when(histTableRs.next()).thenReturn(true);
            when(histTableRs.getInt(1)).thenReturn(1);

            ResultSet verRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("version = '1.8.0' AND success = true"))).thenReturn(verRs);
            when(verRs.next()).thenReturn(true);
            when(verRs.getInt(1)).thenReturn(1);

            ResultSet rateRangeRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("cgst_rate < 0.00 OR sgst_rate < 0.00"))).thenReturn(rateRangeRs);
            when(rateRangeRs.next()).thenReturn(true);
            when(rateRangeRs.getInt(1)).thenReturn(0);

            ResultSet rateSumRs = mock(ResultSet.class);
            when(stmt.executeQuery(contains("(cgst_rate + sgst_rate) != default_tax_rate"))).thenReturn(rateSumRs);
            when(rateSumRs.next()).thenReturn(true);
            when(rateSumRs.getInt(1)).thenReturn(0);

            PreflightResult result = MigrationPreflightValidator.validateV1_8_0(mockConn);
            assertTrue(result.isValid());
            assertTrue(result.getMessage().contains("Compatible pre-existing schema and valid data verified"));
        }

        private void mock13ColumnsResultSet(Connection mockConn, boolean withWrongOrderGstLength) throws Exception {
            PreparedStatement psCols = mock(PreparedStatement.class);
            ResultSet rsCols = mock(ResultSet.class);
            when(mockConn.prepareStatement(contains("information_schema.columns"))).thenReturn(psCols);
            when(psCols.executeQuery()).thenReturn(rsCols);

            List<MigrationPreflightValidator.DetailedColumnMetadata> list = Arrays.asList(
                    new MigrationPreflightValidator.DetailedColumnMetadata("restaurant_settings", "gst_mode", "character varying", 20, null, null, "NO", "'GST_EXCLUDED'"),
                    new MigrationPreflightValidator.DetailedColumnMetadata("restaurant_settings", "cgst_rate", "numeric", null, 5, 2, "NO", "0.00"),
                    new MigrationPreflightValidator.DetailedColumnMetadata("restaurant_settings", "sgst_rate", "numeric", null, 5, 2, "NO", "0.00"),
                    new MigrationPreflightValidator.DetailedColumnMetadata("orders", "gst_mode", "character varying", withWrongOrderGstLength ? 50 : 20, null, null, "YES", null),
                    new MigrationPreflightValidator.DetailedColumnMetadata("orders", "cgst_rate", "numeric", null, 5, 2, "YES", null),
                    new MigrationPreflightValidator.DetailedColumnMetadata("orders", "sgst_rate", "numeric", null, 5, 2, "YES", null),
                    new MigrationPreflightValidator.DetailedColumnMetadata("orders", "cgst_amount", "numeric", null, 10, 2, "NO", "0.00"),
                    new MigrationPreflightValidator.DetailedColumnMetadata("orders", "sgst_amount", "numeric", null, 10, 2, "NO", "0.00"),
                    new MigrationPreflightValidator.DetailedColumnMetadata("bills", "gst_mode", "character varying", 20, null, null, "YES", null),
                    new MigrationPreflightValidator.DetailedColumnMetadata("bills", "cgst_rate", "numeric", null, 5, 2, "YES", null),
                    new MigrationPreflightValidator.DetailedColumnMetadata("bills", "sgst_rate", "numeric", null, 5, 2, "YES", null),
                    new MigrationPreflightValidator.DetailedColumnMetadata("bills", "cgst_amount", "numeric", null, 10, 2, "NO", "0.00"),
                    new MigrationPreflightValidator.DetailedColumnMetadata("bills", "sgst_amount", "numeric", null, 10, 2, "NO", "0.00")
            );

            Boolean[] nextReturns = new Boolean[list.size() + 1];
            Arrays.fill(nextReturns, 0, list.size(), Boolean.TRUE);
            nextReturns[list.size()] = Boolean.FALSE;
            when(rsCols.next()).thenReturn(nextReturns[0], Arrays.copyOfRange(nextReturns, 1, nextReturns.length));

            String[] tableNames = list.stream().map(c -> c.tableName).toArray(String[]::new);
            String[] colNames = list.stream().map(c -> c.columnName).toArray(String[]::new);
            String[] dataTypes = list.stream().map(c -> c.dataType).toArray(String[]::new);
            Integer[] charLengths = list.stream().map(c -> c.characterMaximumLength).toArray(Integer[]::new);
            Integer[] numPrecs = list.stream().map(c -> c.numericPrecision).toArray(Integer[]::new);
            Integer[] numScales = list.stream().map(c -> c.numericScale).toArray(Integer[]::new);
            String[] isNullables = list.stream().map(c -> c.isNullable).toArray(String[]::new);
            String[] defaults = list.stream().map(c -> c.columnDefault).toArray(String[]::new);

            when(rsCols.getString("table_name")).thenReturn(tableNames[0], Arrays.copyOfRange(tableNames, 1, tableNames.length));
            when(rsCols.getString("column_name")).thenReturn(colNames[0], Arrays.copyOfRange(colNames, 1, colNames.length));
            when(rsCols.getString("data_type")).thenReturn(dataTypes[0], Arrays.copyOfRange(dataTypes, 1, dataTypes.length));
            when(rsCols.getObject("character_maximum_length")).thenReturn(charLengths[0], Arrays.copyOfRange(charLengths, 1, charLengths.length));
            when(rsCols.getObject("numeric_precision")).thenReturn(numPrecs[0], Arrays.copyOfRange(numPrecs, 1, numPrecs.length));
            when(rsCols.getObject("numeric_scale")).thenReturn(numScales[0], Arrays.copyOfRange(numScales, 1, numScales.length));
            when(rsCols.getString("is_nullable")).thenReturn(isNullables[0], Arrays.copyOfRange(isNullables, 1, isNullables.length));
            when(rsCols.getString("column_default")).thenReturn(defaults[0], Arrays.copyOfRange(defaults, 1, defaults.length));
        }
    }

    @Nested
    @DisplayName("5. Allowlist Immutability Verification")
    class AllowlistImmutabilityTests {

        @Test
        @DisplayName("MigrationPreflightValidator does not approve unapproved migrations or mutate gate allowlist")
        void testPreflightDoesNotWeakenGateOrAuthorizeUnapprovedVersions() throws Exception {
            // 1. Unapproved versions remain strictly blocked in MigrationApprovalGate
            assertFalse(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.1.0"),
                    "Draft 1.1.0 must NOT be in the approved allowlist");
            assertTrue(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.8.0"),
                    "Approved version 1.8.0 must be in the approved allowlist");
            assertFalse(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.9.0"),
                    "Unapproved version 1.9.0 must NOT be in the approved allowlist");

            // 2. Allowlist contains exactly the 8 approved production versions
            assertEquals(8, MigrationApprovalGate.APPROVED_ALLOWLIST.size(),
                    "Allowlist must strictly contain exactly 8 approved versions: 1.0.0, 1.2.0, 1.3.0, 1.4.0, 1.5.0, 1.6.0, 1.7.0, 1.8.0");
            assertTrue(MigrationApprovalGate.APPROVED_ALLOWLIST.containsAll(
                    Arrays.asList("1.0.0", "1.2.0", "1.3.0", "1.4.0", "1.5.0", "1.6.0", "1.7.0", "1.8.0")));

            // 3. Preflight execution for an unapproved version does NOT add it to the gate allowlist
            Connection mockConn = mock(Connection.class);
            Statement mockStmt = mock(Statement.class);
            when(mockConn.createStatement()).thenReturn(mockStmt);
            ResultSet tableRs = mock(ResultSet.class);
            when(tableRs.next()).thenReturn(true);
            when(tableRs.getInt(1)).thenReturn(0);
            when(mockStmt.executeQuery(anyString())).thenReturn(tableRs);

            PreflightResult res110 = MigrationPreflightValidator.validateVersion(mockConn, "1.1.0");
            assertTrue(res110.isValid(), "Preflight check itself succeeds");
            assertFalse(MigrationApprovalGate.APPROVED_ALLOWLIST.contains("1.1.0"),
                    "Preflight success must NOT add 1.1.0 to the gate allowlist");
            assertFalse(MigrationApprovalGate.isVersionApprovedForConnection("1.1.0", "jdbc:postgresql://localhost:5432/restaurant_pos"),
                    "Preflight success must NOT authorize 1.1.0 for database execution");

            // 4. Gate allowlist is strictly immutable
            assertThrows(UnsupportedOperationException.class, () ->
                    MigrationApprovalGate.APPROVED_ALLOWLIST.add("1.9.0"));
            assertThrows(UnsupportedOperationException.class, () ->
                    MigrationApprovalGate.APPROVED_ALLOWLIST.remove("1.0.0"));
        }
    }
}
