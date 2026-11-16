# Gives the servers on this laptop a public HTTPS address, so the app works away from home Wi-Fi
# and other people can try it. Free: a Cloudflare "quick tunnel" (no account, no domain).
# The address is new every time you start it, and it works only while this laptop is on.
#   powershell -ExecutionPolicy Bypass -File backend\start-public-demo.ps1
#   powershell -ExecutionPolicy Bypass -File backend\start-public-demo.ps1 -Stop
param(
    [switch]$Stop,
    # A clone of your PUBLIC "address book" repository (see docs/IOS.md). The apps read
    # servers.json from it to find the server, so nobody types an address.
    [string]$ConfigRepo = (Join-Path $PSScriptRoot "..\..\jalanna-config")
)

# Writes the current server list to the address book and pushes it.
function Publish-AddressBook([string[]]$servers) {
    if (-not (Test-Path (Join-Path $ConfigRepo ".git"))) {
        Write-Host "Address book not updated: no repository at $ConfigRepo (see 'Finding the server' in docs/IOS.md)." -ForegroundColor Yellow
        return
    }
    $book = [ordered]@{ servers = @($servers); updated = (Get-Date).ToUniversalTime().ToString("yyyy-MM-ddTHH:mm:ssZ") }
    $file = Join-Path $ConfigRepo "servers.json"
    [System.IO.File]::WriteAllText($file, ($book | ConvertTo-Json), (New-Object System.Text.UTF8Encoding($false)))
    git -C $ConfigRepo add servers.json | Out-Null
    git -C $ConfigRepo commit -q -m "Server address" | Out-Null
    git -C $ConfigRepo push -q
    if ($LASTEXITCODE -eq 0) { Write-Host "Address book updated: apps find the new address within about 5 minutes." -ForegroundColor Green }
    else { Write-Host "Could not push the address book ($ConfigRepo). Push it by hand: git -C `"$ConfigRepo`" push" -ForegroundColor Yellow }
}

$ErrorActionPreference = "Stop"
Set-Location $PSScriptRoot

if ($Stop) {
    docker compose --profile public rm -sf tunnel gateway | Out-Null
    $env:SHARE_PUBLIC_BASE_URL = $null
    docker compose up -d api | Out-Null
    Publish-AddressBook @()
    Write-Host "Stopped: the laptop is no longer reachable from the internet."
    exit 0
}

if (-not (Test-Path ".env") -or -not (Select-String -Path ".env" -Pattern "^MODERATOR_KEY_HASH=.+" -Quiet)) {
    Write-Host "Note: no moderator key is set (.env). The admin page will be public too; run new-moderator-key.ps1 first." -ForegroundColor Yellow
}

Write-Host "Starting the public address..."
docker compose up -d | Out-Null
docker compose --profile public up -d --force-recreate tunnel | Out-Null
if ($LASTEXITCODE -ne 0) { throw "Could not start the tunnel." }

$url = $null
foreach ($i in 1..40) {
    Start-Sleep -Seconds 2
    $log = (docker compose --profile public logs tunnel | Out-String)
    if ($log -match "https://[a-z0-9-]+\.trycloudflare\.com") { $url = $Matches[0]; break }
}
if (-not $url) { throw "Cloudflare did not give an address. Check your internet connection and try again." }

# Share links ("follow my drive") must point at the public address.
$env:SHARE_PUBLIC_BASE_URL = $url
docker compose up -d api | Out-Null

$ok = $false
foreach ($i in 1..30) {
    Start-Sleep -Seconds 2
    try { if ((Invoke-WebRequest "$url/health" -UseBasicParsing -TimeoutSec 10).Content -match "ok") { $ok = $true; break } } catch { }
}

Write-Host ""
if ($ok) { Write-Host "Public address is working:" -ForegroundColor Green }
else { Write-Host "Address created, but the server did not answer yet (it may need another minute):" -ForegroundColor Yellow }
Write-Host "  $url" -ForegroundColor Cyan
Write-Host ""
if ($ok) { Publish-AddressBook @($url) }
Write-Host "Apps find this address by themselves (address book). To type it by hand instead: Settings > Server."
Write-Host "Privacy page: $url/privacy/    Moderation page: $url/admin/"
Write-Host "Keep this laptop on. To stop:  powershell -ExecutionPolicy Bypass -File backend\start-public-demo.ps1 -Stop"
