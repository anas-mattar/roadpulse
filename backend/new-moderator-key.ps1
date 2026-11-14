# Creates a moderator key for the RoadPulse moderation API.
#   powershell -ExecutionPolicy Bypass -File new-moderator-key.ps1
# Keep the KEY private (password manager). Put only the HASH in backend\.env, then:
#   docker compose up -d
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$key = [Convert]::ToBase64String($bytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
$sha = [System.Security.Cryptography.SHA256]::Create()
$hash = -join ($sha.ComputeHash([Text.Encoding]::UTF8.GetBytes($key)) | ForEach-Object { $_.ToString("X2") })
Write-Host ""
Write-Host "Moderator key (send as header X-Moderator-Key, keep secret):"
Write-Host "  $key"
Write-Host ""
Write-Host "Add these lines to backend\.env :"
Write-Host "  MODERATOR_NAME=$env:USERNAME"
Write-Host "  MODERATOR_KEY_HASH=$hash"
