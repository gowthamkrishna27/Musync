# Musync Cloudflare Tunnel Launcher
Write-Host "=========================================" -ForegroundColor Cyan
Write-Host "  Musync Cloudflare Tunnel Gateway" -ForegroundColor Green
Write-Host "=========================================" -ForegroundColor Cyan

if (-not (Test-Path "cloudflared.exe")) {
    Write-Host "Downloading cloudflared for Windows..." -ForegroundColor Yellow
    Invoke-WebRequest -Uri "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe" -OutFile "cloudflared.exe"
}

Write-Host "Starting Cloudflare Tunnel to port 5000..." -ForegroundColor Green
.\cloudflared.exe tunnel --url http://localhost:5000
