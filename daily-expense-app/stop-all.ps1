# stop-all.ps1 — Stop all Daily Expense App services
# Run from the daily-expense-app/ directory:
#   .\stop-all.ps1

param(
    [switch]$KeepDocker  # pass -KeepDocker to leave Docker containers running
)

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-OK($msg)   { Write-Host "    [OK] $msg" -ForegroundColor Green }

Write-Step "Stopping Java services (ports 8081-8085)..."
$ports = 8081, 8082, 8083, 8084, 8085
foreach ($port in $ports) {
    $conn = Get-NetTCPConnection -LocalPort $port -State Listen -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($conn) {
        $p = Get-Process -Id $conn.OwningProcess -ErrorAction SilentlyContinue
        if ($p -and $p.Name -eq 'java') {
            Stop-Process -Id $conn.OwningProcess -Force -ErrorAction SilentlyContinue
            Write-OK "Stopped java on port $port (PID=$($conn.OwningProcess))"
        }
    }
}

if (-not $KeepDocker) {
    Write-Step "Stopping Docker infrastructure..."
    Push-Location $PSScriptRoot
    docker compose down
    Pop-Location
    Write-OK "Docker containers stopped"
} else {
    Write-Host "    Keeping Docker running (-KeepDocker)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "  All services stopped." -ForegroundColor Green
Write-Host "  Frontend: press Ctrl+C in the Vite terminal window." -ForegroundColor Yellow
