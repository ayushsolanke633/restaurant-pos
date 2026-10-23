package com.restaurant.restaurantpos.util;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AnnaDhara Migration Approval Gate
 * Evaluates pending migration versions, database lifecycle states, credentials,
 * and authorization tokens to enforce fail-closed approval gates before any Flyway DDL execution.
 */
public class MigrationApprovalGate {

    public static final String GATE_AUTHORIZATION_TOKEN = "AUTHORIZED_BY_GATE";

    public static final Set<String> APPROVED_ALLOWLIST = Collections.unmodifiableSet(
            new LinkedHashSet<>(Arrays.asList("1.0.0", "1.2.0", "1.3.0", "1.4.0", "1.5.0"))
    );

    private static final Pattern PENDING_MIGRATION_PATTERN = Pattern.compile(
            "\\|\\s*(?:Versioned\\s*\\|\\s*)?(\\d+\\.\\d+\\.\\d+)\\s*\\|\\s*([^|]+)\\|\\s*SQL\\s*\\|[^|]*\\|\\s*Pending\\s*\\|"
    );

    public enum DatabaseState {
        EMPTY,
        LEGACY_UNMANAGED,
        VERSIONED_WITH_HISTORY,
        FAILED_INSPECTION
    }

    public static class GateResult {
        private final boolean authorized;
        private final String message;
        private final List<String> pendingVersions;

        public GateResult(boolean authorized, String message, List<String> pendingVersions) {
            this.authorized = authorized;
            this.message = message;
            this.pendingVersions = pendingVersions != null ? pendingVersions : Collections.emptyList();
        }

        public boolean isAuthorized() {
            return authorized;
        }

        public String getMessage() {
            return message;
        }

        public List<String> getPendingVersions() {
            return pendingVersions;
        }
    }

    /**
     * Reliably determines the database lifecycle state.
     * Fails closed if inspection was unsuccessful or inconclusive.
     */
    public static DatabaseState detectDatabaseState(boolean hasHistoryTable, Integer tableCount, boolean inspectionSuccessful) {
        if (!inspectionSuccessful) {
            return DatabaseState.FAILED_INSPECTION;
        }
        if (hasHistoryTable) {
            return DatabaseState.VERSIONED_WITH_HISTORY;
        }
        if (tableCount == null) {
            return DatabaseState.FAILED_INSPECTION;
        }
        if (tableCount == 0) {
            return DatabaseState.EMPTY;
        }
        return DatabaseState.LEGACY_UNMANAGED;
    }

    /**
     * Evaluates whether Flyway migration may safely proceed with explicit authorization token.
     */
    public static GateResult evaluate(String dbUrl, String dbUser, String dbPass, DatabaseState dbState,
                                      String infoOutput, String target, String authorizationToken) {
        // 1. Verify authorization token
        if (!GATE_AUTHORIZATION_TOKEN.equals(authorizationToken)) {
            return new GateResult(false, "REJECTED: Missing or invalid gate authorization token.", Collections.emptyList());
        }

        // 2. Validate credentials (fail-closed if missing)
        if (dbUrl == null || dbUrl.trim().isEmpty() ||
            dbUser == null || dbUser.trim().isEmpty() ||
            dbPass == null || dbPass.trim().isEmpty()) {
            return new GateResult(false, "REJECTED: Missing required environment credentials (DB_URL, DB_USERNAME, DB_PASSWORD).", Collections.emptyList());
        }

        // 3. Validate database state (fail closed on failed inspection or legacy unmanaged database)
        if (dbState == DatabaseState.FAILED_INSPECTION) {
            return new GateResult(false, "REJECTED: Database catalog inspection failed or was inconclusive. Failing closed.", Collections.emptyList());
        }

        if (dbState == DatabaseState.LEGACY_UNMANAGED) {
            return new GateResult(false, "REJECTED: Legacy database with unmanaged tables detected. Automatic baselining/migration is forbidden. Manual DBA schema review required.", Collections.emptyList());
        }

        // 4. Validate info output format (Fail-closed on corrupt/empty output)
        if (infoOutput == null || infoOutput.trim().isEmpty()) {
            return new GateResult(false, "REJECTED: Empty or unreadable migration info output. Failing closed.", Collections.emptyList());
        }

        if (infoOutput.contains("[ERROR]") && !infoOutput.contains("BUILD SUCCESS")) {
            return new GateResult(false, "REJECTED: Flyway info command reported build/execution errors. Failing closed.", Collections.emptyList());
        }

        // 5. Parse pending migrations
        List<String> pendingVersions = new ArrayList<>();
        Matcher matcher = PENDING_MIGRATION_PATTERN.matcher(infoOutput);
        while (matcher.find()) {
            pendingVersions.add(matcher.group(1));
        }

        // 6. Special check for EMPTY database: must strictly target 1.0.0
        if (dbState == DatabaseState.EMPTY && (target == null || !target.equals("1.0.0"))) {
            return new GateResult(false, "REJECTED: Empty database must be initialized strictly with Target=1.0.0 first.", pendingVersions);
        }

        // 7. Check every pending migration against the allowlist
        for (String ver : pendingVersions) {
            if (target != null && !target.trim().isEmpty() && compareVersions(ver, target) > 0) {
                // Beyond target ceiling
                continue;
            }
            if (!APPROVED_ALLOWLIST.contains(ver)) {
                return new GateResult(false, "REJECTED: Pending migration version " + ver + " is not in the approved allowlist.", pendingVersions);
            }
        }

        return new GateResult(true, "APPROVED: All pending migrations are authorized.", pendingVersions);
    }

    /**
     * Backward-compatible convenience overload using default gate authorization token.
     */
    public static GateResult evaluate(String dbUrl, String dbUser, String dbPass, DatabaseState dbState,
                                      String infoOutput, String target) {
        return evaluate(dbUrl, dbUser, dbPass, dbState, infoOutput, target, GATE_AUTHORIZATION_TOKEN);
    }

    public static int compareVersions(String v1, String v2) {
        String[] parts1 = v1.split("\\.");
        String[] parts2 = v2.split("\\.");
        int len = Math.max(parts1.length, parts2.length);
        for (int i = 0; i < len; i++) {
            int p1 = i < parts1.length ? Integer.parseInt(parts1[i]) : 0;
            int p2 = i < parts2.length ? Integer.parseInt(parts2[i]) : 0;
            if (p1 != p2) {
                return Integer.compare(p1, p2);
            }
        }
        return 0;
    }
}
