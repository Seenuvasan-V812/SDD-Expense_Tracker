# start-all.ps1 — One-command launcher for Daily Expense App
# Run from the daily-expense-app/ directory:
#   cd daily-expense-app
#   .\start-all.ps1
#
# To stop everything:  .\stop-all.ps1

param(
    [switch]$SkipDocker,   # pass -SkipDocker if Docker is already running
    [switch]$SkipFrontend  # pass -SkipFrontend to skip Vite dev server
)

$Root  = $PSScriptRoot
$JAVA  = "C:\Program Files\Java\jdk-21.0.11\bin\java.exe"
$JWT   = "daily-expense-app-dev-jwt-secret-32chars!!"
$JVM   = "-Xms64m", "-Xmx192m", "-XX:+UseSerialGC"

function Write-Step($msg) { Write-Host "`n==> $msg" -ForegroundColor Cyan }
function Write-OK($msg)   { Write-Host "    [OK] $msg" -ForegroundColor Green }
function Write-Warn($msg) { Write-Host "    [!!] $msg" -ForegroundColor Yellow }

# ── Step 1: Docker infrastructure ────────────────────────────────────────────
if (-not $SkipDocker) {
    Write-Step "Starting Docker infrastructure..."
    Push-Location $Root
    docker compose up -d
    Pop-Location
    Write-Host "    Waiting 15 s for containers to become healthy..." -ForegroundColor DarkGray
    Start-Sleep -Seconds 15
    Write-OK "Docker containers started"
} else {
    Write-Warn "Skipping Docker (--SkipDocker passed)"
}

# ── Step 2: Start Java services as hidden background processes ────────────────
Write-Step "Starting 5 backend services..."

$services = @(
    @{
        Name = "user-service"
        Port = 8081
        Jar  = "$Root\services\user-service\target\user-service-1.0.0-SNAPSHOT.jar"
        Env  = @(
            "-DJWT_SECRET=$JWT",
            "-DSPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5437/identity_db?sslmode=disable",
            "-DSPRING_DATASOURCE_USERNAME=identity_user",
            "-DSPRING_DATASOURCE_PASSWORD=identity_pass"
        )
    },
    @{
        Name = "category-service"
        Port = 8082
        Jar  = "$Root\services\category-service\target\category-service-1.0.0-SNAPSHOT.jar"
        Env  = @(
            "-DJWT_SECRET=$JWT",
            "-DSPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/category_db?sslmode=disable",
            "-DSPRING_DATASOURCE_USERNAME=category_user",
            "-DSPRING_DATASOURCE_PASSWORD=category_pass"
        )
    },
    @{
        Name = "expense-service"
        Port = 8083
        Jar  = "$Root\services\expense-service\target\expense-service-1.0.0-SNAPSHOT-exec.jar"
        Env  = @(
            "-DJWT_SECRET=$JWT",
            "-DSPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5434/expense_db?sslmode=disable",
            "-DSPRING_DATASOURCE_USERNAME=expense_user",
            "-DSPRING_DATASOURCE_PASSWORD=expense_pass",
            "-DSPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092",
            "-DMINIO_ENDPOINT=http://localhost:9000",
            "-DMINIO_ACCESS_KEY=minio_admin",
            "-DMINIO_SECRET_KEY=minio_secret"
        )
    },
    @{
        Name = "savings-goal-service"
        Port = 8084
        Jar  = "$Root\services\savings-goal-service\target\savings-goal-service-1.0.0-SNAPSHOT.jar"
        Env  = @(
            "-DJWT_SECRET=$JWT",
            "-DSPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5435/savings_goal_db?sslmode=disable",
            "-DSPRING_DATASOURCE_USERNAME=savings_goal_user",
            "-DSPRING_DATASOURCE_PASSWORD=savings_goal_pass"
        )
    },
    @{
        Name = "budget-service"
        Port = 8085
        Jar  = "$Root\services\budget-service\target\budget-service-1.0.0-SNAPSHOT.jar"
        Env  = @(
            "-DJWT_SECRET=$JWT",
            "-DSPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5436/budget_db?sslmode=disable",
            "-DSPRING_DATASOURCE_USERNAME=budget_user",
            "-DSPRING_DATASOURCE_PASSWORD=budget_pass"
        )
    }
)

foreach ($svc in $services) {
    if (-not (Test-Path $svc.Jar)) {
        Write-Warn "$($svc.Name): JAR not found at $($svc.Jar)"
        Write-Warn "  Run the build step first (see STARTUP.md Step 2)"
        continue
    }
    $allArgs = $JVM + $svc.Env + @("-jar", $svc.Jar)
    $proc = Start-Process -FilePath $JAVA -ArgumentList $allArgs -PassThru -WindowStyle Hidden
    Write-OK "$($svc.Name) started  (PID=$($proc.Id)  port=$($svc.Port))"
}

# ── Step 3: Wait and health-check ────────────────────────────────────────────
Write-Step "Waiting 20 s for services to boot..."
Start-Sleep -Seconds 20

Write-Step "Health check:"
foreach ($svc in $services) {
    try {
        $r = Invoke-RestMethod "http://localhost:$($svc.Port)/actuator/health" -TimeoutSec 3 -ErrorAction Stop
        Write-OK "$($svc.Name)  :$($svc.Port)  → $($r.status)"
    } catch {
        Write-Warn "$($svc.Name)  :$($svc.Port)  → not responding yet (still booting)"
    }
}

# ── Step 4: Frontend ──────────────────────────────────────────────────────────
if (-not $SkipFrontend) {
    Write-Step "Starting frontend (Vite dev server)..."
    $frontendDir = "$Root\frontend"
    Start-Process -FilePath "cmd.exe" `
        -ArgumentList "/k", "cd /d `"$frontendDir`" && npm run dev" `
        -WindowStyle Normal
    Write-OK "Frontend starting in a new window → http://localhost:5173"
} else {
    Write-Warn "Skipping frontend (--SkipFrontend passed)"
}

Write-Host ""
Write-Host "════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  App is starting up!" -ForegroundColor White
Write-Host "  UI  →  http://localhost:5173" -ForegroundColor Green
Write-Host "  API →  http://localhost:8081..8085" -ForegroundColor Green
Write-Host ""
Write-Host "  To stop everything: .\stop-all.ps1" -ForegroundColor Yellow
Write-Host "════════════════════════════════════════════" -ForegroundColor Cyan
