<#
.SYNOPSIS
    AnnaDhara Controlled Flyway Migration Gate (PowerShell)
.DESCRIPTION
    Enforces pre-flight authorization checks, reliable tri-state database detection,
    and allowlist verification before permitting Flyway migrations to run.
#>
param(
    [string]$Target = "",
    [switch]$IsolatedTestDbOnly,
    [switch]$Help
)

if ($Help) {
    Write-Host "Usage: ./scripts/safe-migrate.ps1 [-Target <version>] [-IsolatedTestDbOnly]"
    Write-Host "Example: ./scripts/safe-migrate.ps1 -Target 1.5.0"
    Write-Host "Example test: ./scripts/safe-migrate.ps1 -Target 1.6.0 -IsolatedTestDbOnly"
    exit 0
}

Write-Host "=== AnnaDhara P0-2 Controlled Migration Gate ===" -ForegroundColor Cyan

# 1. Environment Variable Validation (Fail-Closed if missing)
$dbUrl = $env:DB_URL
$dbUser = $env:DB_USERNAME
$dbPass = $env:DB_PASSWORD

if ([string]::IsNullOrWhiteSpace($dbUrl) -or [string]::IsNullOrWhiteSpace($dbUser) -or [string]::IsNullOrWhiteSpace($dbPass)) {
    Write-Host "[GATE HALT] Missing required environment variable: DB_URL, DB_USERNAME, or DB_PASSWORD." -ForegroundColor Red
    Write-Host "[GATE HALT] Cannot proceed without explicit database credentials. Aborting." -ForegroundColor Red
    exit 1
}

# 2. Immutable Approved Migration Allowlist
$APPROVED_ALLOWLIST = @("1.0.0", "1.2.0", "1.3.0", "1.4.0", "1.5.0", "1.6.0", "1.7.0", "1.8.0")
# NOTE: 1.1.0 is strictly EXCLUDED as an unauthorized draft

if ($IsolatedTestDbOnly) {
    if ($dbUrl -notmatch "restaurant_pos_migration_test" -or $dbUrl -match "restaurant_pos(?![_a-zA-Z0-9])") {
        Write-Host "[GATE HALT] -IsolatedTestDbOnly is strictly restricted to 'restaurant_pos_migration_test'. Target was: $dbUrl" -ForegroundColor Red
        exit 1
    }
    Write-Host "[GATE NOTICE] Test-specific authorization active for isolated database 'restaurant_pos_migration_test'." -ForegroundColor Yellow
    $APPROVED_ALLOWLIST += @("1.6.0", "1.7.0")
    $env:FLYWAY_TEST_ISOLATED_APPROVED = "true"
}

# 3. Read-Only Migration Metadata Inspection (mvn flyway:info with authorized locations)
Write-Host "[GATE CHECK] Querying current database migration status..." -ForegroundColor Yellow
$infoOutput = & mvn flyway:info "-Dflyway.skip=false" "-Dflyway.locations=filesystem:src/main/resources/db/migration" 2>&1
$infoExitCode = $LASTEXITCODE

if ($infoExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($infoOutput)) {
    Write-Host "[GATE HALT] Failed to retrieve migration info from database. Failing closed." -ForegroundColor Red
    exit 1
}

$infoText = $infoOutput -join "`n"

# 4. Reliable Tri-State Database Detection
$hasHistoryTable = ($infoText -match "\|\s*Category\s*\|\s*Version\s*\|") -and ($infoText -notmatch "has no schema history table")

if (-not $hasHistoryTable) {
    Write-Host "[GATE CHECK] No Flyway history table detected. Inspecting catalog for application tables..." -ForegroundColor Yellow
    
    $psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
    if (-not $psqlCmd) {
        Write-Host "[GATE HALT] Missing Flyway history table, and 'psql' is not available to inspect catalog tables." -ForegroundColor Red
        Write-Host "[GATE HALT] Cannot reliably distinguish empty database from unmanaged legacy database. Failing closed." -ForegroundColor Red
        exit 1
    }

    $tableQuery = "SELECT count(*) FROM information_schema.tables WHERE table_schema = 'public' AND table_name != 'flyway_schema_history';"
    $tableCountOutput = & psql -d $dbUrl -t -A -c $tableQuery 2>&1
    $psqlExitCode = $LASTEXITCODE

    if ($psqlExitCode -ne 0 -or [string]::IsNullOrWhiteSpace($tableCountOutput)) {
        Write-Host "[GATE HALT] Database catalog table inspection query failed. Failing closed." -ForegroundColor Red
        exit 1
    }

    $trimmedCount = $tableCountOutput.Trim()
    if ($trimmedCount -notmatch "^\d+$") {
        Write-Host "[GATE HALT] Unexpected response from catalog query ('$trimmedCount'). Failing closed." -ForegroundColor Red
        exit 1
    }

    $appTableCount = [int]$trimmedCount

    if ($appTableCount -gt 0) {
        # State 3: Populated database with no Flyway history (Legacy) -> REFUSE AUTO-BASELINE
        Write-Host "[GATE HALT] Legacy database detected! Database contains $appTableCount application tables with no Flyway history." -ForegroundColor Red
        Write-Host "[GATE HALT] Automatic baselining or migration is strictly forbidden." -ForegroundColor Red
        Write-Host "[REQUIRED ACTION] Requires manual DBA schema audit against V1_0_0 and explicit manual baseline." -ForegroundColor Red
        exit 1
    }

    # State 1: Genuinely empty database -> Must strictly target 1.0.0
    Write-Host "[GATE INFO] Confirmed genuinely empty database (0 application tables)." -ForegroundColor Cyan
    if ($Target -ne "1.0.0") {
        Write-Host "[GATE HALT] Genuinely empty database must be initialized strictly with -Target 1.0.0 first. Aborting." -ForegroundColor Red
        exit 1
    }
}

# 5. Extract Pending Migrations and Validate Against Allowlist
$pendingVersions = @()
$lines = $infoOutput -split "`r?`n"

foreach ($line in $lines) {
    if ($line -match "\|\s*(?:Versioned\s*\|\s*)?(\d+\.\d+\.\d+)\s*\|\s*([^\|]+)\|\s*SQL\s*\|[^\|]*\|\s*Pending\s*\|") {
        $ver = $matches[1]
        $desc = $matches[2].Trim()
        $pendingVersions += [PSCustomObject]@{ Version = $ver; Description = $desc }
    }
}

Write-Host "[GATE INFO] Detected $($pendingVersions.Count) pending migration(s)." -ForegroundColor Cyan

if ($pendingVersions.Count -eq 0) {
    Write-Host "[GATE SUCCESS] Database is up to date. No pending migrations to execute." -ForegroundColor Green
    exit 0
}

# Validate each pending migration against allowlist
foreach ($p in $pendingVersions) {
    Write-Host " -> Pending: $($p.Version) - $($p.Description)"
    
    if (-not [string]::IsNullOrWhiteSpace($Target)) {
        if ([version]$p.Version -gt [version]$Target) {
            Write-Host "    (Above requested target $Target - will not be applied)" -ForegroundColor Gray
            continue
        }
    }

    if ($APPROVED_ALLOWLIST -notcontains $p.Version) {
        Write-Host "[GATE REJECTED] Pending migration version $($p.Version) is NOT in the approved allowlist!" -ForegroundColor Red
        Write-Host "[GATE REJECTED] Script is not authorized for execution. Aborting before migrate." -ForegroundColor Red
        exit 1
    }
}

# 5.5 Read-Only Schema Preflight Validation
Write-Host "[GATE CHECK] Running read-only schema preflight validation for pending migrations..." -ForegroundColor Yellow
$versionsToApply = @($pendingVersions | Where-Object { [string]::IsNullOrWhiteSpace($Target) -or ([version]$_.Version -le [version]$Target) } | ForEach-Object { $_.Version })
$versionsCsv = $versionsToApply -join ","

$preflightArgs = @(
    "compile",
    "exec:java",
    "-Dexec.mainClass=com.restaurant.restaurantpos.util.MigrationPreflightValidator",
    "-Dexec.args=$versionsCsv",
    "-q"
)

$preflightOutput = & mvn @preflightArgs 2>&1
$preflightExitCode = $LASTEXITCODE

if ($preflightExitCode -ne 0) {
    Write-Host "[GATE HALT] Migration preflight validation failed! Schema incompatibility detected or catalog query failed." -ForegroundColor Red
    Write-Host "[GATE HALT] Aborting before Flyway migration execution." -ForegroundColor Red
    if (-not [string]::IsNullOrWhiteSpace($preflightOutput)) {
        Write-Host "$preflightOutput" -ForegroundColor Red
    }
    exit 1
}
Write-Host "[GATE APPROVED] Read-only schema preflight validation passed." -ForegroundColor Green

# 6. Execute Authorized Migration
Write-Host "[GATE APPROVED] All pending migrations up to target are authorized." -ForegroundColor Green
$migrateArgs = @(
    "flyway:migrate",
    "-Dflyway.skip=false",
    "-Dflyway.locations=filesystem:src/main/resources/db/migration"
)
if ($IsolatedTestDbOnly) {
    $migrateArgs += "-Dmigration.test.isolated.approved=true"
}
if (-not [string]::IsNullOrWhiteSpace($Target)) {
    $migrateArgs += "-Dflyway.target=$Target"
}

Write-Host "[GATE EXECUTE] Invoking: mvn $($migrateArgs -join ' ')" -ForegroundColor Cyan
& mvn @migrateArgs
$migrateExitCode = $LASTEXITCODE

if ($migrateExitCode -ne 0) {
    Write-Host "[GATE FAILURE] Migration execution failed with exit code $migrateExitCode." -ForegroundColor Red
    exit $migrateExitCode
}

# 7. Post-Migration Checksum Validation
Write-Host "[GATE VALIDATE] Executing post-migration checksum validation..." -ForegroundColor Cyan
$validateArgs = @(
    "flyway:validate",
    "-Dflyway.skip=false",
    "-Dflyway.locations=filesystem:src/main/resources/db/migration"
)
if (-not [string]::IsNullOrWhiteSpace($Target)) {
    $validateArgs += "-Dflyway.ignoreMigrationPatterns=*:pending"
}
& mvn @validateArgs
exit $LASTEXITCODE
