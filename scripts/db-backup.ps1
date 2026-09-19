<#
.SYNOPSIS
    Food Risk Analysis - Automated Database Backup Script (PowerShell)
.DESCRIPTION
    Creates a timestamped snapshot of the PostgreSQL database,
    verifies backup integrity, and auto-prunes backups older than the retention threshold.
.EXAMPLE
    .\scripts\db-backup.ps1 -BackupDir ".\backups" -RetentionDays 7
#>

param (
    [string]$HostName = $env:DB_HOST,
    [int]$Port = 5432,
    [string]$Database = $env:DB_NAME,
    [string]$Username = $env:DB_USERNAME,
    [string]$Password = $env:DB_PASSWORD,
    [string]$BackupDir = ".\backups",
    [int]$RetentionDays = 7
)

# If password not in environment, try loading from .env
if (-not $Password -and (Test-Path ".\.env")) {
    Get-Content ".\.env" | ForEach-Object {
        $line = $_.Trim()
        if ($line -and -not $line.StartsWith("#") -and $line.Contains("=")) {
            $parts = $line.Split("=", 2)
            $k = $parts[0].Trim()
            $v = $parts[1].Trim()
            if ($k -eq "DB_PASSWORD" -and -not $Password) { $Password = $v }
            if ($k -eq "DB_USERNAME" -and -not $Username) { $Username = $v }
            if ($k -eq "DB_NAME" -and -not $Database) { $Database = $v }
            if ($k -eq "DB_HOST" -and -not $HostName) { $HostName = $v }
        }
    }
}

if (-not $HostName) { $HostName = "localhost" }
if (-not $Database) { $Database = "food_risk_analysis" }
if (-not $Username) { $Username = "postgres" }
if ($env:DB_PORT) { $Port = [int]$env:DB_PORT }

# Ensure output directory exists
if (-not (Test-Path -Path $BackupDir)) {
    New-Item -ItemType Directory -Path $BackupDir -Force | Out-Null
}

$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$backupFilename = "foodrisk_${Database}_${timestamp}.sql"
$backupPath = Join-Path $BackupDir $backupFilename

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host " Food Risk Analysis - Database Backup Execution" -ForegroundColor Cyan
Write-Host " Timestamp:  $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host " Target DB:  ${HostName}:${Port}/${Database}"
Write-Host " Output:     $backupPath"
Write-Host "================================================="

# Set password environment variable for pg_dump if provided
if ($Password) {
    $env:PGPASSWORD = $Password
}

try {
    # Check if pg_dump is available on host or via docker compose
    $pgDumpCmd = Get-Command pg_dump -ErrorAction SilentlyContinue
    $pgDumpPath = if ($pgDumpCmd) { $pgDumpCmd.Source } else { "C:\Program Files\PostgreSQL\18\bin\pg_dump.exe" }
    if (-not (Test-Path $pgDumpPath)) {
        $pgDumpPath = "C:\Program Files\PostgreSQL\16\bin\pg_dump.exe"
    }

    if (Test-Path $pgDumpPath) {
        Write-Host "Executing native pg_dump ($pgDumpPath)..." -ForegroundColor Yellow
        & $pgDumpPath -h $HostName -p $Port -U $Username -F p -b -v -f $backupPath $Database
    } else {
        Write-Host "Native pg_dump not found in PATH. Attempting via Docker Compose..." -ForegroundColor Yellow
        docker compose exec -T postgres pg_dump -U $Username -d $Database > $backupPath
    }

    if (Test-Path $backupPath) {
        $fileSize = (Get-Item $backupPath).Length
        if ($fileSize -gt 0) {
            Write-Host "SUCCESS: Database backup created successfully ($([math]::Round($fileSize / 1KB, 2)) KB)" -ForegroundColor Green
        } else {
            Write-Error "FAILURE: Backup file was created with 0 bytes."
            exit 1
        }
    } else {
        Write-Error "FAILURE: Backup file was not generated."
        exit 1
    }

    # Prune historical backups older than RetentionDays
    $cutoff = (Get-Date).AddDays(-$RetentionDays)
    $oldBackups = Get-ChildItem -Path $BackupDir -Filter "foodrisk_*.sql" | Where-Object { $_.CreationTime -lt $cutoff }
    foreach ($old in $oldBackups) {
        Write-Host "Pruning expired backup: $($old.Name)" -ForegroundColor DarkGray
        Remove-Item -Path $old.FullName -Force
    }

} finally {
    if ($env:PGPASSWORD) {
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}

Write-Host "Backup operation completed." -ForegroundColor Cyan
