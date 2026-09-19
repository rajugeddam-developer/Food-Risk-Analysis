<#
.SYNOPSIS
    Food Risk Analysis - Automated Database Restore Script (PowerShell)
.DESCRIPTION
    Restores a verified PostgreSQL SQL dump file into the target database.
.EXAMPLE
    .\scripts\db-restore.ps1 -BackupFile ".\backups\foodrisk_food_risk_analysis_20260918.sql"
#>

param (
    [Parameter(Mandatory=$true)]
    [string]$BackupFile,
    [string]$HostName = $env:DB_HOST,
    [int]$Port = 5432,
    [string]$Database = $env:DB_NAME,
    [string]$Username = $env:DB_USERNAME,
    [string]$Password = $env:DB_PASSWORD,
    [switch]$Force
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

if (-not (Test-Path $BackupFile)) {
    Write-Error "CRITICAL: Backup file not found at '$BackupFile'"
    exit 1
}

Write-Host "=================================================" -ForegroundColor Cyan
Write-Host " Food Risk Analysis - Database Restore Execution" -ForegroundColor Cyan
Write-Host " Source File: $BackupFile"
Write-Host " Target DB:   ${HostName}:${Port}/${Database}"
Write-Host "================================================="

if ($Password) {
    $env:PGPASSWORD = $Password
}

try {
    $psqlCmd = Get-Command psql -ErrorAction SilentlyContinue
    $psqlPath = if ($psqlCmd) { $psqlCmd.Source } else { "C:\Program Files\PostgreSQL\18\bin\psql.exe" }
    if (-not (Test-Path $psqlPath)) {
        $psqlPath = "C:\Program Files\PostgreSQL\16\bin\psql.exe"
    }

    if (Test-Path $psqlPath) {
        Write-Host "Restoring via native psql ($psqlPath)..." -ForegroundColor Yellow
        & $psqlPath -h $HostName -p $Port -U $Username -d $Database -f $BackupFile
    } else {
        Write-Host "Native psql not found. Restoring via Docker Compose..." -ForegroundColor Yellow
        Get-Content $BackupFile | docker compose exec -T postgres psql -U $Username -d $Database
    }

    Write-Host "SUCCESS: Database restore executed successfully." -ForegroundColor Green

} catch {
    Write-Error "FAILURE: Error restoring database: $_"
    exit 1
} finally {
    if ($env:PGPASSWORD) {
        Remove-Item env:PGPASSWORD -ErrorAction SilentlyContinue
    }
}
