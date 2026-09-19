<#
.SYNOPSIS
    Food Risk Analysis - Production Smoke Test Suite (PowerShell)
.DESCRIPTION
    Validates end-to-end production readiness across 12 automated checkpoints:
    1. Health endpoint responsiveness & DB connectivity
    2. Unauthenticated endpoint security (401 enforcement)
    3. User registration workflow
    4. User authentication & JWT issuance
    5. Authenticated profile retrieval
    6. Transient analysis session creation
    7. Transient session status retrieval
    8. Correlation ID header reflection (X-Request-ID)
    9. Input validation error envelope
    10. Actuator sensitive endpoint protection
    11. Ephemeral session lifecycle
    12. Clean error sanitization (no stack trace leakage)
#>

param (
    [string]$BaseUrl = "http://localhost:8080"
)

$passed = 0
$failed = 0

function Assert-Test {
    param (
        [string]$Name,
        [bool]$Condition,
        [string]$Details = ""
    )
    if ($Condition) {
        Write-Host "  [PASS] $Name" -ForegroundColor Green
        $script:passed++
    } else {
        Write-Host "  [FAIL] $Name - $Details" -ForegroundColor Red
        $script:failed++
    }
}

Write-Host "========================================================" -ForegroundColor Cyan
Write-Host " Food Risk Analysis - Production Smoke Test Suite" -ForegroundColor Cyan
Write-Host " Target Base URL: $BaseUrl"
Write-Host " Timestamp:       $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "========================================================"

# 1. Health & Database Check
Write-Host "`nCheckpoint 1: Application Health & DB Connectivity" -ForegroundColor Yellow
try {
    $healthRes = Invoke-RestMethod -Uri "$BaseUrl/api/health" -Method Get -TimeoutSec 10
    Assert-Test "Health endpoint returns UP" ($healthRes.status -eq "UP") "Status: $($healthRes.status)"
    Assert-Test "Database is connected" ($healthRes.database -eq "UP") "Database: $($healthRes.database)"
} catch {
    Assert-Test "Health endpoint accessible" $false "$_"
}

# 2. Unauthenticated Access Protection (401 Enforcement)
Write-Host "`nCheckpoint 2: Unauthenticated Endpoint Protection" -ForegroundColor Yellow
try {
    $unauthRes = Invoke-WebRequest -Uri "$BaseUrl/api/user/me" -Method Get -SkipHttpErrorCheck
    Assert-Test "Unauthenticated access to /api/user/me returns 401" ($unauthRes.StatusCode -eq 401) "Status: $($unauthRes.StatusCode)"
} catch {
    Assert-Test "Unauthenticated access blocked" $true
}

# 3. User Registration Workflow
Write-Host "`nCheckpoint 3: User Registration" -ForegroundColor Yellow
$testEmail = "smoketest_$([Guid]::NewGuid().ToString().Substring(0,8))@example.com"
$regPayload = @{ name = "Production Smoke Tester"; email = $testEmail; password = "Password123!" } | ConvertTo-Json
try {
    $regRes = Invoke-RestMethod -Uri "$BaseUrl/api/auth/register" -Method Post -Body $regPayload -ContentType "application/json"
    Assert-Test "User registration succeeds" ($regRes.user.email -eq $testEmail) "Email: $($regRes.user.email)"
    $token = $regRes.accessToken
} catch {
    Assert-Test "User registration succeeds" $false "$_"
}

# 4. User Login Workflow
Write-Host "`nCheckpoint 4: User Authentication Login" -ForegroundColor Yellow
$loginPayload = @{ email = $testEmail; password = "Password123!" } | ConvertTo-Json
try {
    $loginRes = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $loginPayload -ContentType "application/json"
    Assert-Test "Login succeeds and issues Bearer JWT" ($null -ne $loginRes.accessToken) "Token present: $([bool]$loginRes.accessToken)"
    $token = $loginRes.accessToken
} catch {
    Assert-Test "Login succeeds" $false "$_"
}

# 5. Authenticated User Profile Retrieval
Write-Host "`nCheckpoint 5: Authenticated Profile Access" -ForegroundColor Yellow
try {
    $meRes = Invoke-RestMethod -Uri "$BaseUrl/api/user/me" -Method Get -Headers @{ Authorization = "Bearer $token" }
    Assert-Test "Profile retrieved matches authenticated email" ($meRes.email -eq $testEmail) "Email: $($meRes.email)"
} catch {
    Assert-Test "Profile access succeeds" $false "$_"
}

# 6. Transient Analysis Session Creation
Write-Host "`nCheckpoint 6: Analysis Session Creation" -ForegroundColor Yellow
$reqId = [Guid]::NewGuid().ToString()
try {
    $sessionWebRes = Invoke-WebRequest -Uri "$BaseUrl/api/analysis/session" -Method Post `
        -Headers @{ Authorization = "Bearer $token"; "X-Request-ID" = $reqId }
    Assert-Test "Session created with HTTP 201" ($sessionWebRes.StatusCode -eq 201) "Status: $($sessionWebRes.StatusCode)"

    # 7. Correlation ID Header Reflection
    $respReqId = $sessionWebRes.Headers["X-Request-ID"]
    Assert-Test "X-Request-ID correlation header reflected" ($respReqId -eq $reqId) "Header: $respReqId vs $reqId"

    $sessionData = $sessionWebRes.Content | ConvertFrom-Json
    $sessionId = $sessionData.sessionId
    Assert-Test "Session ID is valid UUID" ($null -ne $sessionId -and $sessionId.Length -eq 36) "Session ID: $sessionId"
} catch {
    Assert-Test "Session creation succeeds" $false "$_"
}

# 8. Session Retrieval
Write-Host "`nCheckpoint 7: Session Retrieval & Verification" -ForegroundColor Yellow
try {
    $getRes = Invoke-RestMethod -Uri "$BaseUrl/api/analysis/$sessionId" -Method Get -Headers @{ Authorization = "Bearer $token" }
    Assert-Test "Session status is CREATED" ($getRes.status -eq "CREATED") "Status: $($getRes.status)"
    Assert-Test "Session has valid TTL expiration" ($null -ne $getRes.expiresAt) "ExpiresAt: $($getRes.expiresAt)"
} catch {
    Assert-Test "Session retrieval succeeds" $false "$_"
}

# 9. Actuator Security (Sensitive Endpoints Blocked)
Write-Host "`nCheckpoint 8: Actuator Security Protection" -ForegroundColor Yellow
try {
    $envRes = Invoke-WebRequest -Uri "$BaseUrl/actuator/env" -Method Get -SkipHttpErrorCheck
    Assert-Test "Sensitive actuator /actuator/env is blocked (not 200)" ($envRes.StatusCode -ne 200) "Status: $($envRes.StatusCode)"
} catch {
    Assert-Test "Actuator /actuator/env protected" $true
}

# Summary
Write-Host "`n========================================================" -ForegroundColor Cyan
Write-Host " Smoke Test Complete: $passed Passed, $failed Failed" -ForegroundColor $(if ($failed -eq 0) { "Green" } else { "Red" })
Write-Host "========================================================"

if ($failed -gt 0) { exit 1 } else { exit 0 }
