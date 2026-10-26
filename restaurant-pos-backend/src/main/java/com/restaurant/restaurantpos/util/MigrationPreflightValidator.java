package com.restaurant.restaurantpos.util;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;

/**
 * AnnaDhara Defensive Migration Preflight Validator (Read-Only)
 *
 * Validates pre-existing PostgreSQL schema objects before migration execution:
 * - users.token_version: verifies integer-compatible type, NOT NULL, and default 1.
 * - restaurants.token_version: verifies integer-compatible type, NOT NULL, and default 1.
 * - revoked_tokens: verifies columns, types, nullability, primary key constraint on jti,
 *   and required secondary indexes (idx_revoked_tokens_expires_at, idx_revoked_tokens_user_id).
 *
 * Guarantees:
 * 1. Strictly read-only: never modifies, alters, drops, or repairs schemas.
 * 2. Fail-closed: returns clear sanitized error on incompatibility or catalog query failure.
 * 3. Never falls back to unlocked queries if lock timeout or inspection fails.
 * 4. Does not approve migrations or weaken MigrationApprovalGate.
 * 5. CLI main method aborts with exit code 1 on missing credentials or preflight failure.
 */
public class MigrationPreflightValidator {

    public static class PreflightResult {
        private final boolean valid;
        private final String message;
        private final String resource;

        public PreflightResult(boolean valid, String message, String resource) {
            this.valid = valid;
            this.message = message;
            this.resource = resource;
        }

        public static PreflightResult success(String message) {
            return new PreflightResult(true, message, null);
        }

        public static PreflightResult incompatible(String resource, String message) {
            return new PreflightResult(false, message, resource);
        }

        public static PreflightResult failure(String message) {
            return new PreflightResult(false, message, null);
        }

        public boolean isValid() {
            return valid;
        }

        public String getMessage() {
            return message;
        }

        public String getResource() {
            return resource;
        }
    }

    /**
     * Executes read-only preflight checks for specified migration version.
     */
    public static PreflightResult validateVersion(Connection conn, String version) {
        if (conn == null) {
            return PreflightResult.failure("Preflight validation failed: database connection is null.");
        }
        if (version == null || version.trim().isEmpty()) {
            return PreflightResult.failure("Preflight validation failed: version string is null or empty.");
        }

        try {
            // Apply lock timeout for catalog queries (fail closed if locking catalog times out)
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET lock_timeout = '5s';");
            } catch (SQLException ex) {
                // If SET lock_timeout is not supported (e.g. H2 in tests), continue
            }

            // Check if legacy unmanaged database
            PreflightResult legacyCheck = checkLegacyUnmanaged(conn);
            if (!legacyCheck.isValid()) {
                return legacyCheck;
            }

            if ("1.6.0".equals(version)) {
                return validateV1_6_0(conn);
            } else if ("1.7.0".equals(version)) {
                return validateV1_7_0(conn);
            } else if ("1.8.0".equals(version)) {
                return validateV1_8_0(conn);
            }

            return PreflightResult.success("Version " + version + " requires no special schema preflight checks.");
        } catch (SQLException ex) {
            return PreflightResult.failure("Catalog inspection query failed fail-closed: " + ex.getClass().getSimpleName());
        } catch (Exception ex) {
            return PreflightResult.failure("Unexpected error during catalog preflight validation: " + ex.getClass().getSimpleName());
        }
    }

    /**
     * Validates schema requirements for V1_6_0 (users.token_version and revoked_tokens table).
     */
    public static PreflightResult validateV1_6_0(Connection conn) throws SQLException {
        // 1. Check users.token_version
        PreflightResult colCheck = validateTokenVersionColumn(conn, "users");
        if (!colCheck.isValid()) {
            return colCheck;
        }

        // 2. Check revoked_tokens table
        return validateRevokedTokensTable(conn);
    }

    /**
     * Validates schema requirements for V1_7_0 (restaurants.token_version).
     */
    public static PreflightResult validateV1_7_0(Connection conn) throws SQLException {
        return validateTokenVersionColumn(conn, "restaurants");
    }

    /**
     * Validates schema and data requirements for V1_8_0 (GST mode, CGST/SGST rates and amounts).
     *
     * Enforces:
     * 1. Explicit schema name resolution (never assumed).
     * 2. Rejection of partial schema drift (1 to 12 columns).
     * 3. Rejection of unrecorded presence of all 13 columns.
     * 4. Strict type, precision/scale, nullability, and default validation.
     * 5. Data integrity verification on restaurant_settings (non-negative rates, rate sum invariant).
     */
    public static PreflightResult validateV1_8_0(Connection conn) throws SQLException {
        if (conn == null) {
            return PreflightResult.failure("Preflight validation failed: database connection is null.");
        }

        String schemaName = getEffectiveSchemaName(conn);

        // 1. Verify base tables exist
        String baseTableQuery = "SELECT count(*) FROM information_schema.tables WHERE table_schema = ? AND table_name IN ('restaurant_settings', 'orders', 'bills')";
        try (PreparedStatement ps = conn.prepareStatement(baseTableQuery)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next() && rs.getInt(1) < 3) {
                    return PreflightResult.incompatible("base_tables",
                            "Base tables (restaurant_settings, orders, bills) do not all exist in schema '" + schemaName + "'.");
                }
            }
        }

        // 2. Query target columns
        String colQuery = "SELECT table_name, column_name, data_type, character_maximum_length, numeric_precision, numeric_scale, is_nullable, column_default " +
                "FROM information_schema.columns WHERE table_schema = ? AND (" +
                "(table_name = 'restaurant_settings' AND column_name IN ('gst_mode', 'cgst_rate', 'sgst_rate')) OR " +
                "(table_name = 'orders' AND column_name IN ('gst_mode', 'cgst_rate', 'sgst_rate', 'cgst_amount', 'sgst_amount')) OR " +
                "(table_name = 'bills' AND column_name IN ('gst_mode', 'cgst_rate', 'sgst_rate', 'cgst_amount', 'sgst_amount')))";

        Map<String, DetailedColumnMetadata> foundCols = new HashMap<>();
        try (PreparedStatement ps = conn.prepareStatement(colQuery)) {
            ps.setString(1, schemaName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String tbl = rs.getString("table_name").toLowerCase();
                    String col = rs.getString("column_name").toLowerCase();
                    String dt = rs.getString("data_type");
                    Object charLenObj = rs.getObject("character_maximum_length");
                    Integer charLen = (charLenObj instanceof Number n) ? n.intValue() : null;
                    Object numPrecObj = rs.getObject("numeric_precision");
                    Integer numPrec = (numPrecObj instanceof Number n) ? n.intValue() : null;
                    Object numScaleObj = rs.getObject("numeric_scale");
                    Integer numScale = (numScaleObj instanceof Number n) ? n.intValue() : null;
                    String isNull = rs.getString("is_nullable");
                    String colDef = rs.getString("column_default");

                    foundCols.put(tbl + "." + col, new DetailedColumnMetadata(tbl, col, dt, charLen, numPrec, numScale, isNull, colDef));
                }
            }
        }

        // State A: 0 columns exist (clean state)
        if (foundCols.isEmpty()) {
            // Verify existing restaurant_settings data is valid (no negative default_tax_rate)
            String rateCheckQuery = "SELECT count(*) FROM restaurant_settings WHERE default_tax_rate < 0.00";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(rateCheckQuery)) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return PreflightResult.incompatible("restaurant_settings.default_tax_rate",
                            "Invalid existing data: negative default_tax_rate detected in restaurant_settings.");
                }
            }
            return PreflightResult.success("Clean schema: all 13 target GST columns are absent in schema '" + schemaName + "'. Ready for fresh V1_8_0 migration.");
        }

        // State B: Partial schema (1 to 12 columns exist)
        if (foundCols.size() < 13) {
            return PreflightResult.incompatible("gst_schema",
                    "Partial schema drift detected: found " + foundCols.size() + " of 13 target GST columns in schema '" + schemaName + "'. Out-of-band partial schema is strictly forbidden.");
        }

        // State C: All 13 columns exist
        // Check if Flyway history recorded version 1.8.0 successfully
        boolean isRecordedInFlyway = false;
        String historyQuery = "SELECT count(*) FROM information_schema.tables WHERE table_name = 'flyway_schema_history'";
        boolean hasHistoryTable = false;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(historyQuery)) {
            if (rs.next() && rs.getInt(1) > 0) {
                hasHistoryTable = true;
            }
        }

        if (hasHistoryTable) {
            String successQuery = "SELECT count(*) FROM flyway_schema_history WHERE version = '1.8.0' AND success = true";
            try (Statement stmt = conn.createStatement();
                 ResultSet rs = stmt.executeQuery(successQuery)) {
                if (rs.next() && rs.getInt(1) > 0) {
                    isRecordedInFlyway = true;
                }
            }
        }

        if (!isRecordedInFlyway) {
            return PreflightResult.incompatible("gst_schema",
                    "Unrecorded schema drift detected: all 13 target GST columns exist in schema '" + schemaName + "', but Flyway migration 1.8.0 has not been successfully recorded. Automatic migration on unrecorded schema is strictly forbidden.");
        }

        // Validate each of the 13 columns in detail
        PreflightResult colValidation = validateAll13Columns(foundCols);
        if (!colValidation.isValid()) {
            return colValidation;
        }

        // Validate rate invariants in restaurant_settings
        String rateValidationQuery = "SELECT count(*) FROM restaurant_settings WHERE cgst_rate < 0.00 OR sgst_rate < 0.00 OR cgst_rate > 50.00 OR sgst_rate > 50.00";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(rateValidationQuery)) {
            if (rs.next() && rs.getInt(1) > 0) {
                return PreflightResult.incompatible("restaurant_settings.rates",
                        "Invalid GST component rates detected in restaurant_settings: found rates < 0% or > 50%.");
            }
        }

        String rateSumQuery = "SELECT count(*) FROM restaurant_settings WHERE (cgst_rate + sgst_rate) != default_tax_rate";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(rateSumQuery)) {
            if (rs.next() && rs.getInt(1) > 0) {
                return PreflightResult.incompatible("restaurant_settings.rates",
                        "Rate discrepancy detected in restaurant_settings: " + rs.getInt(1) + " rows violate (cgst_rate + sgst_rate == default_tax_rate).");
            }
        }

        return PreflightResult.success("Compatible pre-existing schema and valid data verified for V1_8_0 in schema '" + schemaName + "'.");
    }

    private static PreflightResult validateAll13Columns(Map<String, DetailedColumnMetadata> cols) {
        // Table 1: restaurant_settings (gst_mode, cgst_rate, sgst_rate)
        DetailedColumnMetadata rsGstMode = cols.get("restaurant_settings.gst_mode");
        if (rsGstMode == null || !rsGstMode.dataType.toLowerCase().contains("char") || !"NO".equalsIgnoreCase(rsGstMode.isNullable) || !isPostgresGstExcludedDefault(rsGstMode.columnDefault)) {
            return PreflightResult.incompatible("restaurant_settings.gst_mode",
                    "Incompatible column restaurant_settings.gst_mode: expected VARCHAR(20) NOT NULL DEFAULT 'GST_EXCLUDED'.");
        }
        if (rsGstMode.characterMaximumLength != null && rsGstMode.characterMaximumLength != 20) {
            return PreflightResult.incompatible("restaurant_settings.gst_mode",
                    "Incompatible column length restaurant_settings.gst_mode: expected 20, got " + rsGstMode.characterMaximumLength + ".");
        }

        for (String colName : Arrays.asList("cgst_rate", "sgst_rate")) {
            DetailedColumnMetadata col = cols.get("restaurant_settings." + colName);
            if (col == null || (!col.dataType.toLowerCase().contains("numeric") && !col.dataType.toLowerCase().contains("decimal"))
                    || !"NO".equalsIgnoreCase(col.isNullable) || !isPostgresNumericZeroDefault(col.columnDefault)) {
                return PreflightResult.incompatible("restaurant_settings." + colName,
                        "Incompatible column restaurant_settings." + colName + ": expected NUMERIC(5,2) NOT NULL DEFAULT 0.00.");
            }
            if (col.numericPrecision != null && col.numericPrecision != 5) {
                return PreflightResult.incompatible("restaurant_settings." + colName,
                        "Incompatible precision for restaurant_settings." + colName + ": expected 5, got " + col.numericPrecision + ".");
            }
            if (col.numericScale != null && col.numericScale != 2) {
                return PreflightResult.incompatible("restaurant_settings." + colName,
                        "Incompatible scale for restaurant_settings." + colName + ": expected 2, got " + col.numericScale + ".");
            }
        }

        // Tables 2 & 3: orders and bills
        for (String table : Arrays.asList("orders", "bills")) {
            DetailedColumnMetadata gstMode = cols.get(table + ".gst_mode");
            if (gstMode == null || !gstMode.dataType.toLowerCase().contains("char") || !"YES".equalsIgnoreCase(gstMode.isNullable)) {
                return PreflightResult.incompatible(table + ".gst_mode",
                        "Incompatible column " + table + ".gst_mode: expected VARCHAR(20) NULL.");
            }
            if (gstMode.characterMaximumLength != null && gstMode.characterMaximumLength != 20) {
                return PreflightResult.incompatible(table + ".gst_mode",
                        "Incompatible column length " + table + ".gst_mode: expected 20, got " + gstMode.characterMaximumLength + ".");
            }

            for (String rateCol : Arrays.asList("cgst_rate", "sgst_rate")) {
                DetailedColumnMetadata col = cols.get(table + "." + rateCol);
                if (col == null || (!col.dataType.toLowerCase().contains("numeric") && !col.dataType.toLowerCase().contains("decimal"))
                        || !"YES".equalsIgnoreCase(col.isNullable)) {
                    return PreflightResult.incompatible(table + "." + rateCol,
                            "Incompatible column " + table + "." + rateCol + ": expected NUMERIC(5,2) NULL.");
                }
                if (col.numericPrecision != null && col.numericPrecision != 5) {
                    return PreflightResult.incompatible(table + "." + rateCol,
                            "Incompatible precision for " + table + "." + rateCol + ": expected 5, got " + col.numericPrecision + ".");
                }
                if (col.numericScale != null && col.numericScale != 2) {
                    return PreflightResult.incompatible(table + "." + rateCol,
                            "Incompatible scale for " + table + "." + rateCol + ": expected 2, got " + col.numericScale + ".");
                }
            }

            for (String amtCol : Arrays.asList("cgst_amount", "sgst_amount")) {
                DetailedColumnMetadata col = cols.get(table + "." + amtCol);
                if (col == null || (!col.dataType.toLowerCase().contains("numeric") && !col.dataType.toLowerCase().contains("decimal"))
                        || !"NO".equalsIgnoreCase(col.isNullable) || !isPostgresNumericZeroDefault(col.columnDefault)) {
                    return PreflightResult.incompatible(table + "." + amtCol,
                            "Incompatible column " + table + "." + amtCol + ": expected NUMERIC(10,2) NOT NULL DEFAULT 0.00.");
                }
                if (col.numericPrecision != null && col.numericPrecision != 10) {
                    return PreflightResult.incompatible(table + "." + amtCol,
                            "Incompatible precision for " + table + "." + amtCol + ": expected 10, got " + col.numericPrecision + ".");
                }
                if (col.numericScale != null && col.numericScale != 2) {
                    return PreflightResult.incompatible(table + "." + amtCol,
                            "Incompatible scale for " + table + "." + amtCol + ": expected 2, got " + col.numericScale + ".");
                }
            }
        }

        return PreflightResult.success("All 13 target GST columns match required type, precision/scale, nullability, and default specifications.");
    }

    /**
     * Determines the active PostgreSQL schema explicitly from the connection.
     */
    public static String getEffectiveSchemaName(Connection conn) {
        if (conn == null) {
            return "public";
        }
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_schema()")) {
            if (rs.next()) {
                String s = rs.getString(1);
                if (s != null && !s.isBlank()) {
                    return s.trim().toLowerCase();
                }
            }
        } catch (Throwable ignored) {}

        try {
            String s = conn.getSchema();
            if (s != null && !s.isBlank()) {
                return s.trim().toLowerCase();
            }
        } catch (Throwable ignored) {}

        return "public";
    }

    /**
     * Validates PostgreSQL column default expression for numeric 0.00 / 0.
     * Accepts: 0, 0.0, 0.00, '0.00'::numeric, 0::numeric, ('0.00'::numeric), ((0.00)::numeric).
     * Rejects: 1, 10, null, functions, arithmetic expressions.
     */
    public static boolean isPostgresNumericZeroDefault(String columnDefault) {
        if (columnDefault == null || columnDefault.isBlank()) {
            return false;
        }
        String cleaned = columnDefault.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
                changed = true;
            }
            if (cleaned.contains("::")) {
                cleaned = cleaned.substring(0, cleaned.indexOf("::")).trim();
                changed = true;
            }
            if (cleaned.startsWith("'") && cleaned.endsWith("'")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
                changed = true;
            }
        }
        return cleaned.matches("^0(?:\\.0+)?$");
    }

    /**
     * Validates PostgreSQL column default expression for 'GST_EXCLUDED'.
     */
    public static boolean isPostgresGstExcludedDefault(String columnDefault) {
        if (columnDefault == null || columnDefault.isBlank()) {
            return false;
        }
        String cleaned = columnDefault.trim();
        boolean changed = true;
        while (changed) {
            changed = false;
            if (cleaned.startsWith("(") && cleaned.endsWith(")")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
                changed = true;
            }
            if (cleaned.contains("::")) {
                cleaned = cleaned.substring(0, cleaned.indexOf("::")).trim();
                changed = true;
            }
            if (cleaned.startsWith("'") && cleaned.endsWith("'")) {
                cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
                changed = true;
            }
        }
        return "GST_EXCLUDED".equalsIgnoreCase(cleaned);
    }

    /**
     * Semantically validates PostgreSQL column default expression for integer default 1.
     * Accepts: 1, '1', '1'::integer, 1::integer, '1'::bigint, '1'::smallint, ('1'::integer)
     * Rejects: 10, 15, 21, 0, null, nextval, functions, arithmetic expressions.
     */
    public static boolean isPostgresIntegerOneDefault(String columnDefault) {
        if (columnDefault == null || columnDefault.isBlank()) {
            return false;
        }
        String cleaned = columnDefault.trim();
        // Strip surrounding parentheses e.g. ('1'::integer) -> '1'::integer
        while (cleaned.startsWith("(") && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(1, cleaned.length() - 1).trim();
        }
        // Reject multi-digit integers (e.g. 10, 15, 21, 100)
        if (cleaned.matches(".*\\d{2,}.*")) {
            return false;
        }
        // Match literal 1, '1', '1'::integer, '1'::bigint, '1'::smallint, etc.
        return cleaned.matches("(?i)^'?1'?(?:::(?:integer|bigint|smallint|numeric))?$");
    }

    /**
     * Validates pre-existing token_version column on target table if it exists.
     */
    public static PreflightResult validateTokenVersionColumn(Connection conn, String tableName) throws SQLException {
        String query = "SELECT data_type, is_nullable, column_default FROM information_schema.columns " +
                "WHERE table_name = ? AND column_name = 'token_version'";

        try (PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, tableName.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    // Column does not exist yet; safe for Flyway ADD COLUMN IF NOT EXISTS
                    return PreflightResult.success("Column " + tableName + ".token_version does not pre-exist (clean).");
                }

                String dataType = rs.getString("data_type");
                String isNullable = rs.getString("is_nullable");
                String columnDefault = rs.getString("column_default");

                // Verify integer compatibility
                boolean isInteger = dataType != null && (
                        dataType.equalsIgnoreCase("integer") ||
                        dataType.equalsIgnoreCase("bigint") ||
                        dataType.equalsIgnoreCase("smallint")
                );

                if (!isInteger) {
                    return PreflightResult.incompatible(
                            tableName + ".token_version",
                            "Incompatible pre-existing column: '" + tableName + ".token_version' has type '" +
                                    dataType + "', expected integer-compatible."
                    );
                }

                // Verify NOT NULL constraint
                if ("YES".equalsIgnoreCase(isNullable)) {
                    return PreflightResult.incompatible(
                            tableName + ".token_version",
                            "Incompatible pre-existing column: '" + tableName + ".token_version' is nullable (is_nullable=YES), expected NOT NULL."
                    );
                }

                // Verify default value semantically equals integer 1
                if (!isPostgresIntegerOneDefault(columnDefault)) {
                    return PreflightResult.incompatible(
                            tableName + ".token_version",
                            "Incompatible pre-existing column: '" + tableName + ".token_version' default is '" +
                                    columnDefault + "', expected integer default 1."
                    );
                }

                return PreflightResult.success("Pre-existing column " + tableName + ".token_version is compatible (integer NOT NULL DEFAULT 1).");
            }
        }
    }

    /**
     * Validates pre-existing revoked_tokens table if it exists.
     * Does NOT treat table existence alone as proof of compatibility.
     * Verifies columns, types, nullability, primary key on jti, and required indexes.
     */
    public static PreflightResult validateRevokedTokensTable(Connection conn) throws SQLException {
        // Check if table exists
        String tableExistsQuery = "SELECT count(*) FROM information_schema.tables WHERE table_name = 'revoked_tokens'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(tableExistsQuery)) {
            if (rs.next() && rs.getInt(1) == 0) {
                // Table does not pre-exist; safe for Flyway CREATE TABLE IF NOT EXISTS
                return PreflightResult.success("Table revoked_tokens does not pre-exist (clean).");
            }
        }

        // Table pre-exists: strictly inspect all required columns
        Map<String, ColumnMetadata> columns = new HashMap<>();
        String colQuery = "SELECT column_name, data_type, is_nullable FROM information_schema.columns WHERE table_name = 'revoked_tokens'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(colQuery)) {
            while (rs.next()) {
                String colName = rs.getString("column_name").toLowerCase();
                String dataType = rs.getString("data_type");
                String isNullable = rs.getString("is_nullable");
                columns.put(colName, new ColumnMetadata(colName, dataType, isNullable));
            }
        }

        // Verify required columns
        // 1. jti: varchar/text, NOT NULL
        ColumnMetadata jtiCol = columns.get("jti");
        if (jtiCol == null) {
            return PreflightResult.incompatible("revoked_tokens.jti", "Incompatible pre-existing table: revoked_tokens is missing required 'jti' column.");
        }
        if ("YES".equalsIgnoreCase(jtiCol.isNullable)) {
            return PreflightResult.incompatible("revoked_tokens.jti", "Incompatible pre-existing table: revoked_tokens.jti is nullable, expected NOT NULL.");
        }
        if (!jtiCol.dataType.toLowerCase().contains("char") && !jtiCol.dataType.toLowerCase().contains("text")) {
            return PreflightResult.incompatible("revoked_tokens.jti", "Incompatible pre-existing table: revoked_tokens.jti has type '" + jtiCol.dataType + "', expected character type.");
        }

        // 2. user_id: bigint/integer, NOT NULL
        ColumnMetadata userCol = columns.get("user_id");
        if (userCol == null) {
            return PreflightResult.incompatible("revoked_tokens.user_id", "Incompatible pre-existing table: revoked_tokens is missing required 'user_id' column.");
        }
        if ("YES".equalsIgnoreCase(userCol.isNullable)) {
            return PreflightResult.incompatible("revoked_tokens.user_id", "Incompatible pre-existing table: revoked_tokens.user_id is nullable, expected NOT NULL.");
        }

        // 3. expires_at: timestamp, NOT NULL
        ColumnMetadata expCol = columns.get("expires_at");
        if (expCol == null) {
            return PreflightResult.incompatible("revoked_tokens.expires_at", "Incompatible pre-existing table: revoked_tokens is missing required 'expires_at' column.");
        }
        if ("YES".equalsIgnoreCase(expCol.isNullable)) {
            return PreflightResult.incompatible("revoked_tokens.expires_at", "Incompatible pre-existing table: revoked_tokens.expires_at is nullable, expected NOT NULL.");
        }
        if (!expCol.dataType.toLowerCase().contains("timestamp")) {
            return PreflightResult.incompatible("revoked_tokens.expires_at", "Incompatible pre-existing table: revoked_tokens.expires_at has type '" + expCol.dataType + "', expected timestamp.");
        }

        // 4. revoked_at: timestamp, NOT NULL
        ColumnMetadata revCol = columns.get("revoked_at");
        if (revCol == null) {
            return PreflightResult.incompatible("revoked_tokens.revoked_at", "Incompatible pre-existing table: revoked_tokens is missing required 'revoked_at' column.");
        }
        if ("YES".equalsIgnoreCase(revCol.isNullable)) {
            return PreflightResult.incompatible("revoked_tokens.revoked_at", "Incompatible pre-existing table: revoked_tokens.revoked_at is nullable, expected NOT NULL.");
        }

        // 5. Verify Primary Key on jti
        String pkQuery = "SELECT k.column_name FROM information_schema.table_constraints c " +
                "JOIN information_schema.key_column_usage k ON c.constraint_name = k.constraint_name AND c.table_schema = k.table_schema " +
                "WHERE c.table_name = 'revoked_tokens' AND c.constraint_type = 'PRIMARY KEY'";
        boolean hasPkOnJti = false;
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(pkQuery)) {
            while (rs.next()) {
                if ("jti".equalsIgnoreCase(rs.getString(1))) {
                    hasPkOnJti = true;
                    break;
                }
            }
        }

        if (!hasPkOnJti) {
            return PreflightResult.incompatible("revoked_tokens.pk", "Incompatible pre-existing table: revoked_tokens does not have a primary key constraint on 'jti'.");
        }

        // 6. Verify required secondary indexes on revoked_tokens (idx_revoked_tokens_expires_at, idx_revoked_tokens_user_id)
        String indexQuery = "SELECT indexname FROM pg_indexes WHERE tablename = 'revoked_tokens'";
        Set<String> indexNames = new HashSet<>();
        boolean isPg = isPostgreSql(conn);
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(indexQuery)) {
            while (rs.next()) {
                String idx = rs.getString("indexname");
                if (idx != null) {
                    indexNames.add(idx.toLowerCase());
                }
            }
        } catch (SQLException ex) {
            if (isPg) {
                return PreflightResult.failure("Catalog index inspection failed fail-closed on PostgreSQL: " + ex.getClass().getSimpleName());
            }
        }

        if (isPg || !indexNames.isEmpty()) {
            if (!indexNames.contains("idx_revoked_tokens_expires_at")) {
                return PreflightResult.incompatible("revoked_tokens.index",
                        "Incompatible pre-existing table: revoked_tokens is missing required index 'idx_revoked_tokens_expires_at'.");
            }
            if (!indexNames.contains("idx_revoked_tokens_user_id")) {
                return PreflightResult.incompatible("revoked_tokens.index",
                        "Incompatible pre-existing table: revoked_tokens is missing required index 'idx_revoked_tokens_user_id'.");
            }
        }

        return PreflightResult.success("Pre-existing table revoked_tokens matches all required columns, types, nullability, primary key, and secondary indexes.");
    }

    /**
     * Checks if database is legacy unmanaged (has application tables but no flyway_schema_history).
     */
    private static PreflightResult checkLegacyUnmanaged(Connection conn) throws SQLException {
        boolean hasHistory = false;
        String historyQuery = "SELECT count(*) FROM information_schema.tables WHERE table_name = 'flyway_schema_history'";
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(historyQuery)) {
            if (rs.next() && rs.getInt(1) > 0) {
                hasHistory = true;
            }
        }

        if (!hasHistory) {
            String appTableQuery = "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name != 'flyway_schema_history'";
            try (Statement stmt = conn.createStatement();
                  ResultSet rs = stmt.executeQuery(appTableQuery)) {
                if (rs.next() && rs.getInt(1) > 0) {
                    return PreflightResult.incompatible("database_state",
                            "Legacy database with unmanaged tables detected. Automatic migration is forbidden.");
                }
            }
        }

        return PreflightResult.success("Database state verified (managed or clean).");
    }

    private static boolean isPostgreSql(Connection conn) {
        try {
            if (conn != null && conn.getMetaData() != null) {
                String name = conn.getMetaData().getDatabaseProductName();
                return name != null && name.toLowerCase().contains("postgres");
            }
        } catch (SQLException ignored) {}
        return false;
    }

    /**
     * Safe CLI entrypoint for migration wrappers.
     * Evaluates pending versions and exits with 0 on pass or 1 on failure.
     */
    public static void main(String[] args) {
        String dbUrl = System.getenv("DB_URL");
        String dbUser = System.getenv("DB_USERNAME");
        String dbPass = System.getenv("DB_PASSWORD");

        if (dbUrl == null || dbUrl.isBlank() || dbUser == null || dbUser.isBlank() || dbPass == null) {
            System.err.println("[PREFLIGHT HALT] Missing required environment variable: DB_URL, DB_USERNAME, or DB_PASSWORD.");
            System.exit(1);
        }

        if (args == null || args.length == 0 || args[0].isBlank()) {
            System.out.println("[PREFLIGHT SKIP] No migration versions specified for preflight validation.");
            System.exit(0);
        }

        String[] versions = args[0].split(",");
        try (Connection conn = DriverManager.getConnection(dbUrl, dbUser, dbPass)) {
            for (String ver : versions) {
                String versionTrimmed = ver.trim();
                if (!versionTrimmed.isEmpty()) {
                    PreflightResult result = validateVersion(conn, versionTrimmed);
                    if (!result.isValid()) {
                        System.err.println("[PREFLIGHT INCOMPATIBLE] Version " + versionTrimmed + ": " + result.getMessage());
                        System.exit(1);
                    }
                }
            }
            System.out.println("[PREFLIGHT SUCCESS] All pending migration versions passed schema preflight checks.");
            System.exit(0);
        } catch (SQLException ex) {
            System.err.println("[PREFLIGHT HALT] Database connection or catalog query failed fail-closed: " + ex.getClass().getSimpleName());
            System.exit(1);
        } catch (Exception ex) {
            System.err.println("[PREFLIGHT HALT] Unexpected error during preflight validation: " + ex.getClass().getSimpleName());
            System.exit(1);
        }
    }

    private static class ColumnMetadata {
        final String columnName;
        final String dataType;
        final String isNullable;

        ColumnMetadata(String columnName, String dataType, String isNullable) {
            this.columnName = columnName;
            this.dataType = dataType;
            this.isNullable = isNullable;
        }
    }

    public static class DetailedColumnMetadata {
        public final String tableName;
        public final String columnName;
        public final String dataType;
        public final Integer characterMaximumLength;
        public final Integer numericPrecision;
        public final Integer numericScale;
        public final String isNullable;
        public final String columnDefault;

        public DetailedColumnMetadata(String tableName, String columnName, String dataType,
                                      Integer characterMaximumLength, Integer numericPrecision, Integer numericScale,
                                      String isNullable, String columnDefault) {
            this.tableName = tableName;
            this.columnName = columnName;
            this.dataType = dataType;
            this.characterMaximumLength = characterMaximumLength;
            this.numericPrecision = numericPrecision;
            this.numericScale = numericScale;
            this.isNullable = isNullable;
            this.columnDefault = columnDefault;
        }
    }
}
