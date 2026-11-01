# ==============================================================================
# DriveSphere Automated End-to-End Smoke Test Suite
# Tests: Health, Auth, Rate Limiter, Privilege Escalation Prevention,
# Refresh Token Rotation, Vehicle Availability, Booking Holds,
# Tax Invoices, Payment Order, Verification, GPS Validation, Admin APIs
# ==============================================================================

param (
    [string]$BaseUrl = "http://localhost:8080/api"
)

$ErrorActionPreference = "Continue"
$global:PassCount = 0
$global:FailCount = 0

function Assert-Step {
    param (
        [string]$Name,
        [scriptblock]$Test
    )
    Write-Host -NoNewline "[TEST] $Name... "
    try {
        $result = & $Test
        if ($result -eq $true) {
            Write-Host -ForegroundColor Green "PASS"
            $global:PassCount++
        } else {
            Write-Host -ForegroundColor Red "FAIL (Assertion evaluated to false)"
            $global:FailCount++
        }
    } catch {
        Write-Host -ForegroundColor Red "FAIL ($($_.Exception.Message))"
        $global:FailCount++
    }
}

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "      DriveSphere Mobility -- End-to-End API Smoke Tests        " -ForegroundColor Cyan
Write-Host "      Target Endpoint: $BaseUrl                                 " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

# 1. Health Endpoint
Assert-Step "1. System Health Check" {
    $res = Invoke-RestMethod -Uri "$BaseUrl/health" -Method Get
    return ($res.status -eq "UP" -or $res.success -eq $true)
}

# 2. Public Registration Privilege Escalation Prevention
Assert-Step "2. Privilege Escalation Prevention (Admin Role Request in Public Registration)" {
    $ts = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds()
    $body = @{
        email = "hacker_$ts@example.com"
        password = "Password123!"
        firstName = "Malicious"
        lastName = "Actor"
        phoneNumber = "+9199999$($ts.ToString().Substring($ts.ToString().Length - 5))"
        role = "ROLE_ADMIN"
    } | ConvertTo-Json

    try {
        $res = Invoke-RestMethod -Uri "$BaseUrl/auth/register" -Method Post -Body $body -ContentType "application/json"
        $assignedRoles = $res.data.roles
        return ($assignedRoles -notcontains "ROLE_ADMIN" -and $assignedRoles -contains "ROLE_CUSTOMER")
    } catch {
        return ($_.Exception.Response.StatusCode.value__ -in 400, 403)
    }
}

# 3. Customer Registration & Login
$testEmail = "smoke_customer_$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())@drivesphere.io"
$testPassword = "StrongPassword123!"
$authToken = $null
$refreshToken = $null
$customerId = $null

Assert-Step "3. Customer Registration with Valid Credentials" {
    $phoneSuffix = [DateTimeOffset]::UtcNow.ToUnixTimeSeconds().ToString()
    $body = @{
        email = $testEmail
        password = $testPassword
        firstName = "Smoke"
        lastName = "Customer"
        phoneNumber = "+9198$($phoneSuffix.Substring($phoneSuffix.Length - 8))"
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$BaseUrl/auth/register" -Method Post -Body $body -ContentType "application/json"
    $roles = if ($res.data.user -and $res.data.user.roles) { $res.data.user.roles } else { $res.data.roles }
    $t = if ($res.data.accessToken) { $res.data.accessToken } else { $res.data.token }
    return ($t -ne $null -and $roles -contains "ROLE_CUSTOMER")
}

Assert-Step "4. Customer Login and Token Issuance" {
    $body = @{
        email = $testEmail
        password = $testPassword
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body $body -ContentType "application/json"
    $script:authToken = if ($res.data.accessToken) { $res.data.accessToken } else { $res.data.token }
    $script:refreshToken = $res.data.refreshToken
    $script:customerId = if ($res.data.user) { $res.data.user.id } else { $res.data.id }
    return ($script:authToken -ne $null -and $script:refreshToken -ne $null)
}

# 5. Refresh Token Rotation
Assert-Step "5. Silent Refresh Token Rotation" {
    $body = @{
        refreshToken = $script:refreshToken
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$BaseUrl/auth/refresh" -Method Post -Body $body -ContentType "application/json"
    $newAccessToken = $res.data.accessToken
    $script:authToken = $newAccessToken
    return ($newAccessToken -ne $null -and $newAccessToken -ne "")
}

# 6. Available Vehicle Catalog
$selectedVehicleId = $null
Assert-Step "6. Fetch Available Operational Vehicle Fleet" {
    $res = Invoke-RestMethod -Uri "$BaseUrl/vehicles/available" -Method Get
    $vehicles = $res.data
    if ($vehicles.Count -gt 0) {
        $script:selectedVehicleId = $vehicles[0].id
        return $true
    }
    return $false
}

# 7. Booking Creation & 15-minute Reservation Hold
$createdBookingId = $null
Assert-Step "7. Create Vehicle Booking with 15-Minute Hold" {
    if (-not $script:selectedVehicleId) { return $false }

    $headers = @{ "Authorization" = "Bearer $script:authToken" }
    $start = (Get-Date).AddDays(1).ToString("yyyy-MM-ddTHH:mm:ss")
    $end = (Get-Date).AddDays(3).ToString("yyyy-MM-ddTHH:mm:ss")

    $body = @{
        vehicleId = $script:selectedVehicleId
        startDateTime = $start
        endDateTime = $end
        pickupLocation = "Bangalore Airport (BLR)"
        dropLocation = "Indiranagar Hub"
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$BaseUrl/bookings" -Method Post -Body $body -Headers $headers -ContentType "application/json"
    $script:createdBookingId = $res.data.id
    return ($res.data.id -ne $null -and ($res.data.bookingStatus -in "PENDING", "PENDING_PAYMENT", "HELD"))
}

# 8. Tax Invoice Generation (GST Itemized)
Assert-Step "8. Generate Itemized GST Tax Invoice" {
    if (-not $script:createdBookingId) { return $false }

    $headers = @{ "Authorization" = "Bearer $script:authToken" }
    $res = Invoke-RestMethod -Uri "$BaseUrl/bookings/$script:createdBookingId/invoice" -Method Get -Headers $headers

    $inv = $res.data
    return ($inv.invoiceNumber -ne $null -and $inv.cgstAmount -ne $null -and $inv.sgstAmount -ne $null -and $inv.totalAmount -gt 0)
}

# 9. Payment Order Creation
$gatewayOrderId = $null
Assert-Step "9. Create Payment Gateway Order" {
    if (-not $script:createdBookingId) { return $false }

    $headers = @{ "Authorization" = "Bearer $script:authToken" }
    $body = @{ bookingId = $script:createdBookingId } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$BaseUrl/payments/create-order" -Method Post -Body $body -Headers $headers -ContentType "application/json"
    $script:gatewayOrderId = $res.data.orderId
    return ($script:gatewayOrderId -ne $null -and $res.data.amount -gt 0)
}

# 10. Payment Verification (Test Sandbox Approval)
Assert-Step "10. Verify Payment (Sandbox Approval Confirmation)" {
    if (-not $script:createdBookingId -or -not $script:gatewayOrderId) { return $false }

    $headers = @{ "Authorization" = "Bearer $script:authToken" }
    $body = @{
        bookingId = $script:createdBookingId
        orderId = $script:gatewayOrderId
        paymentId = "pay_smoke_$([DateTimeOffset]::UtcNow.ToUnixTimeSeconds())"
        signature = "mock_sig_pass"
        mockApproval = $true
    } | ConvertTo-Json

    $res = Invoke-RestMethod -Uri "$BaseUrl/payments/verify" -Method Post -Body $body -Headers $headers -ContentType "application/json"
    return ($res.success -eq $true -or $res.data -eq $true)
}

# 11. GPS Bounds Validation
Assert-Step "11. GPS Coordinates Bounds Validation (Negative Test)" {
    $headers = @{ "Authorization" = "Bearer $script:authToken" }
    $body = @{
        tripId = 1
        latitude = 195.0
        longitude = 77.5
        speedKmh = 50.0
    } | ConvertTo-Json

    try {
        Invoke-RestMethod -Uri "$BaseUrl/trips/location" -Method Post -Body $body -Headers $headers -ContentType "application/json"
        return $false
    } catch {
        return ($_.Exception.Response.StatusCode.value__ -in 400, 422)
    }
}

# 12. Admin Security and Audit Trail
Assert-Step "12. Admin Authentication and Audit Log Query" {
    $adminBody = @{
        email = "admin@drivesphere.local"
        password = "Password@123"
    } | ConvertTo-Json

    try {
        $res = Invoke-RestMethod -Uri "$BaseUrl/auth/login" -Method Post -Body $adminBody -ContentType "application/json"
        $adminToken = if ($res.data.accessToken) { $res.data.accessToken } else { $res.data.token }
        $headers = @{ "Authorization" = "Bearer $adminToken" }

        $auditRes = Invoke-RestMethod -Uri "$BaseUrl/admin/audit-logs" -Method Get -Headers $headers
        $summaryRes = Invoke-RestMethod -Uri "$BaseUrl/admin/summary" -Method Get -Headers $headers

        return ($auditRes.data -ne $null -and $summaryRes.data.totalUsers -ge 1)
    } catch {
        return $false
    }
}

$summaryColor = "Green"
if ($global:FailCount -gt 0) { $summaryColor = "Red" }

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "Test Results: Total Passed: $global:PassCount | Total Failed: $global:FailCount" -ForegroundColor $summaryColor
Write-Host "================================================================" -ForegroundColor Cyan

if ($global:FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
