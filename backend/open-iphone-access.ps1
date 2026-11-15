# Lets an iPhone on your home Wi-Fi reach the RoadPulse servers on this laptop.
# Opens ports 8002 (routing), 8080 (RoadPulse API) and 8090 (map tiles) in Windows Firewall for
# PRIVATE networks only (your home Wi-Fi must be set to "Private" in Windows settings).
# Run in PowerShell as administrator:
#   powershell -ExecutionPolicy Bypass -File backend\open-iphone-access.ps1
#   powershell -ExecutionPolicy Bypass -File backend\open-iphone-access.ps1 -Remove   (close again)
param([switch]$Remove)

$ErrorActionPreference = "Stop"
$name = "RoadPulse iPhone access"

$admin = ([Security.Principal.WindowsPrincipal][Security.Principal.WindowsIdentity]::GetCurrent()).IsInRole(
    [Security.Principal.WindowsBuiltInRole]::Administrator)
if (-not $admin) {
    Write-Host "Please run PowerShell as administrator (right-click > Run as administrator)." -ForegroundColor Yellow
    exit 1
}

Get-NetFirewallRule -DisplayName $name -ErrorAction SilentlyContinue | Remove-NetFirewallRule
if ($Remove) {
    Write-Host "Closed: the iPhone can no longer reach the laptop's servers."
    exit 0
}

New-NetFirewallRule -DisplayName $name -Direction Inbound -Action Allow -Protocol TCP `
    -LocalPort 8002, 8080, 8090 -Profile Private | Out-Null
Write-Host "Opened ports 8002, 8080 and 8090 for private networks." -ForegroundColor Green

$public = Get-NetConnectionProfile | Where-Object { $_.NetworkCategory -eq "Public" }
foreach ($p in $public) {
    Write-Host "Note: '$($p.Name)' is a Public network, so it stays closed there. If it is your home Wi-Fi," -ForegroundColor Yellow
    Write-Host "      set it to Private in Settings > Network & internet > Wi-Fi > (your network)." -ForegroundColor Yellow
}

$ips = Get-NetIPAddress -AddressFamily IPv4 | Where-Object {
    $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" -and $_.PrefixOrigin -ne "WellKnown" -and
    $_.InterfaceAlias -notmatch "vEthernet|WSL|Docker|Loopback"
}
Write-Host ""
Write-Host "Type this laptop address in the iPhone app (Settings > Use my laptop's server):"
foreach ($ip in $ips) { Write-Host "  $($ip.IPAddress)   ($($ip.InterfaceAlias))" -ForegroundColor Cyan }
Write-Host ""
Write-Host "For 'Maps from my laptop', start the tile server open to Wi-Fi:"
Write-Host '  $env:TILES_BIND="0.0.0.0"; docker compose --profile maps up -d'
