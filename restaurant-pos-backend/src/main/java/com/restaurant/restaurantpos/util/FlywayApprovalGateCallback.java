package com.restaurant.restaurantpos.util;

import org.flywaydb.core.api.FlywayException;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * AnnaDhara Flyway Execution Boundary Approval Gate Callback
 *
 * Enforces fail-closed authorization checks at the exact Flyway execution boundary.
 * Intercepts Flyway migration events (BEFORE_EACH_MIGRATE and BEFORE_MIGRATE)
 * and verifies that every version about to execute is explicitly present in
 * MigrationApprovalGate.APPROVED_ALLOWLIST.
 *
 * Guarantees:
 * 1. Aborts immediately by throwing FlywayException before any unapproved SQL script runs.
 * 2. Blocks versions 1.6.0 and 1.7.0 as unapproved drafts.
 * 3. Never approves migrations on its own; strictly consults MigrationApprovalGate.APPROVED_ALLOWLIST.
 * 4. Never leaks passwords, secrets, or sensitive connection parameters in error messages.
 */
public class FlywayApprovalGateCallback implements Callback {

    private static final Logger log = LoggerFactory.getLogger(FlywayApprovalGateCallback.class);

    @Override
    public boolean supports(Event event, Context context) {
        return Event.BEFORE_MIGRATE.equals(event) || Event.BEFORE_EACH_MIGRATE.equals(event);
    }

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public void handle(Event event, Context context) {
        if (Event.BEFORE_EACH_MIGRATE.equals(event)) {
            MigrationInfo migrationInfo = context.getMigrationInfo();
            if (migrationInfo == null || migrationInfo.getVersion() == null) {
                log.warn("[GATE HALT] Attempted execution of unversioned migration. Failing closed.");
                throw new FlywayException("Flyway Migration Gate REJECTED: Execution of unversioned migrations is strictly forbidden.");
            }

            String version = migrationInfo.getVersion().getVersion();
            String dbUrl = null;
            try {
                if (context.getConnection() != null && context.getConnection().getMetaData() != null) {
                    dbUrl = context.getConnection().getMetaData().getURL();
                }
            } catch (Exception ignored) {}

            if (!MigrationApprovalGate.isVersionApprovedForConnection(version, dbUrl)) {
                log.error("[GATE HALT] Unapproved migration version detected at execution boundary: version={}, dbUrl={}", version, dbUrl);
                throw new FlywayException("Flyway Migration Gate REJECTED: Migration version '" + version +
                        "' is NOT in the approved allowlist! Execution aborted before applying DDL.");
            }

            log.info("[GATE APPROVED] Migration version '{}' authorized by execution boundary gate.", version);
        }
    }

    @Override
    public String getCallbackName() {
        return "AnnaDharaFlywayApprovalGateCallback";
    }
}
