#!/usr/bin/env bash
# ==============================================================================
# AnnaDhara Controlled Flyway Migration Gate (POSIX Bash)
# Enforces pre-flight authorization checks, reliable tri-state database detection,
# and allowlist verification before permitting Flyway migrations to run.
# ==============================================================================

set -euo pipefail

TARGET="${1:-}"

echo "=== AnnaDhara P0-2 Controlled Migration Gate ==="

# 1. Environment Variable Validation (Fail-Closed if missing)
if [[ -z "${DB_URL:-}" || -z "${DB_USERNAME:-}" || -z "${DB_PASSWORD:-}" ]]; then
    echo "[GATE HALT] Missing required environment variable: DB_URL, DB_USERNAME, or DB_PASSWORD." >&2
    echo "[GATE HALT] Cannot proceed without explicit database credentials. Aborting." >&2
    exit 1
fi

# 2. Immutable Approved Migration Allowlist
APPROVED_ALLOWLIST=("1.0.0" "1.2.0" "1.3.0" "1.4.0" "1.5.0")
# NOTE: 1.1.0 is strictly EXCLUDED as an unauthorized draft

# 3. Read-Only Migration Metadata Inspection (mvn flyway:info with authorized locations)
echo "[GATE CHECK] Querying current database migration status..."
INFO_OUTPUT=$(mvn flyway:info -Dflyway.skip=false -Dflyway.authorized=true -Dflyway.locations=filesystem:src/main/resources/db/migration 2>&1) || {
    echo "[GATE HALT] Failed to retrieve migration info from database. Failing closed." >&2
    exit 1
}

# 4. Reliable Tri-State Database Detection
HAS_HISTORY=false
if echo "$INFO_OUTPUT" | grep -q "Category" && ! echo "$INFO_OUTPUT" | grep -q "has no schema history table"; then
    HAS_HISTORY=true
fi

if [[ "$HAS_HISTORY" != "true" ]]; then
    echo "[GATE CHECK] No Flyway history table detected. Inspecting catalog for application tables..."
    
    if ! command -v psql &> /dev/null; then
        echo "[GATE HALT] Missing Flyway history table, and 'psql' is not available to inspect catalog tables." >&2
        echo "[GATE HALT] Cannot reliably distinguish empty database from unmanaged legacy database. Failing closed." >&2
        exit 1
    fi

    TABLE_QUERY="SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name != 'flyway_schema_history';"
    APP_TABLE_COUNT=$(psql -d "$DB_URL" -t -A -c "$TABLE_QUERY" 2>&1) || {
        echo "[GATE HALT] Database catalog table inspection query failed. Failing closed." >&2
        exit 1
    }

    APP_TABLE_COUNT=$(echo "$APP_TABLE_COUNT" | tr -d '[:space:]')
    if ! [[ "$APP_TABLE_COUNT" =~ ^[0-9]+$ ]]; then
        echo "[GATE HALT] Unexpected response from catalog query ('$APP_TABLE_COUNT'). Failing closed." >&2
        exit 1
    fi

    if [[ "$APP_TABLE_COUNT" -gt 0 ]]; then
        # State 3: Populated database with no Flyway history (Legacy) -> REFUSE AUTO-BASELINE
        echo "[GATE HALT] Legacy database detected! Database contains $APP_TABLE_COUNT application tables with no Flyway history." >&2
        echo "[GATE HALT] Automatic baselining or migration is strictly forbidden." >&2
        echo "[REQUIRED ACTION] Requires manual DBA schema audit against V1_0_0 and explicit manual baseline." >&2
        exit 1
    fi

    # State 1: Genuinely empty database -> Must strictly target 1.0.0
    echo "[GATE INFO] Confirmed genuinely empty database (0 application tables)."
    if [[ "$TARGET" != "1.0.0" ]]; then
        echo "[GATE HALT] Genuinely empty database must be initialized strictly with Target 1.0.0 first. Aborting." >&2
        exit 1
    fi
fi

# 5. Extract Pending Migrations and Validate Against Allowlist
PENDING_LINES=$(echo "$INFO_OUTPUT" | grep -E '\|\s*[0-9]+\.[0-9]+\.[0-9]+\s*\|.*\|\s*Pending\s*\|' || true)

if [[ -z "$PENDING_LINES" ]]; then
    echo "[GATE SUCCESS] Database is up to date. No pending migrations to execute."
    exit 0
fi

while IFS= read -r line; do
    VER=$(echo "$line" | awk -F'|' '{gsub(/ /, "", $2); print $2}')
    DESC=$(echo "$line" | awk -F'|' '{gsub(/^ +| +$/, "", $3); print $3}')
    
    echo " -> Pending: $VER - $DESC"
    
    # Check if version is in approved allowlist
    APPROVED=false
    for app in "${APPROVED_ALLOWLIST[@]}"; do
        if [[ "$app" == "$VER" ]]; then
            APPROVED=true
            break
        fi
    done
    
    if [[ "$APPROVED" != "true" ]]; then
        echo "[GATE REJECTED] Pending migration version '$VER' is NOT in the approved allowlist!" >&2
        echo "[GATE REJECTED] Script is not authorized for execution. Aborting before migrate." >&2
        exit 1
    fi
done <<< "$PENDING_LINES"

# 6. Execute Authorized Migration
echo "[GATE APPROVED] All pending migrations up to target are authorized."
MIGRATE_ARGS=(
    "flyway:migrate"
    "-Dflyway.skip=false"
    "-Dflyway.authorized=true"
    "-Dflyway.locations=filesystem:src/main/resources/db/migration"
)
if [[ -n "$TARGET" ]]; then
    MIGRATE_ARGS+=("-Dflyway.target=$TARGET")
fi

echo "[GATE EXECUTE] Invoking: mvn ${MIGRATE_ARGS[*]}"
mvn "${MIGRATE_ARGS[@]}"

# 7. Post-Migration Checksum Validation
echo "[GATE VALIDATE] Executing post-migration checksum validation..."
mvn flyway:validate -Dflyway.skip=false -Dflyway.locations=filesystem:src/main/resources/db/migration
echo "[GATE COMPLETE] Migration and validation completed successfully."
