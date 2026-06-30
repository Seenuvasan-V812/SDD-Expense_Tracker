# Daily Expense App — Startup Guide

All commands run from inside `daily-expense-app/` unless stated otherwise.

---

## Quick Start (One Command)

The fastest way to start everything:

```powershell
cd daily-expense-app
.\start-all.ps1
```

Then open **http://localhost:5173** in your browser.

To stop everything:

```powershell
.\stop-all.ps1
```

**Flags:**

| Flag            | Effect                                      |
|-----------------|---------------------------------------------|
| `-SkipDocker`   | Skip `docker compose up` (already running)  |
| `-SkipFrontend` | Don't open the Vite dev server window       |
| `-KeepDocker`   | (`stop-all.ps1`) Leave Docker containers up |

> **First time only:** run the [build step](#step-2--build-all-services) before `start-all.ps1`.

---

## Prerequisites

- Java 21 — `java -version` should show `21.x`
- Docker Desktop running
- Node.js 20+ — `node -v` should show `20.x` or higher
- Ports `8081–8085`, `5173`, `5432–5437`, `9092`, `9000–9001`, `8025`, `1025` free

---

## Step 1 — Start Infrastructure (Docker Compose)

```powershell
docker compose up -d
```

Wait ~15 seconds, then verify:

```powershell
docker compose ps
```

| Container                | Port(s)    | Purpose                       |
|--------------------------|------------|-------------------------------|
| de-postgres-identity     | 5437       | user-service database         |
| de-postgres-category     | 5433       | category-service database     |
| de-postgres-expense      | 5434       | expense-service database      |
| de-postgres-savings-goal | 5435       | savings-goal-service database |
| de-postgres-budget       | 5436       | budget-service database       |
| de-zookeeper             | 2181       | Kafka coordinator             |
| de-kafka                 | 9092       | Message broker                |
| de-minio                 | 9000, 9001 | Object storage (CSV exports)  |
| de-mailhog               | 1025, 8025 | Dev email viewer              |

> **Port conflict:** if local PostgreSQL is on 5432, identity DB is remapped to **5437** — no action needed.

---

## Step 2 — Build All Services

Run once (first time) or after any Java code changes. Run from `daily-expense-app/`:

```powershell
$mvn = "$env:USERPROFILE\.m2\wrapper\dists\apache-maven-3.9.9-bin\33b4b2b4\apache-maven-3.9.9\bin\mvn.cmd"
& $mvn package -DskipTests --no-transfer-progress
```

This builds all 5 services + shared-kernel (~2 minutes). JARs land in `services/*/target/`.

> **Note:** The `mvnw.cmd` wrapper in this repo has a broken JAR — use the command above which points to the Maven downloaded by the wrapper on first run.

---

## Step 3 — Start All 5 Backend Services (Manual)

If you prefer to start services individually (e.g. to see logs):

```powershell
$JWT = "daily-expense-app-dev-jwt-secret-32chars!!"
```

### 3a — user-service (port 8081)

```powershell
java -Xms64m -Xmx192m -XX:+UseSerialGC `
  "-DJWT_SECRET=$JWT" `
  "-DSPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5437/identity_db?sslmode=disable" `
  "-DSPRING_DATASOURCE_USERNAME=identity_user" `
  "-DSPRING_DATASOURCE_PASSWORD=identity_pass" `
  -jar services/user-service/target/user-service-1.0.0-SNAPSHOT.jar
```

### 3b — category-service (port 8082)

```powershell
java -Xms64m -Xmx192m -XX:+UseSerialGC `
  "-DJWT_SECRET=$JWT" `
  "-DSPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/category_db?sslmode=disable" `
  "-DSPRING_DATASOURCE_USERNAME=category_user" `
  "-DSPRING_DATASOURCE_PASSWORD=category_pass" `
  -jar services/category-service/target/category-service-1.0.0-SNAPSHOT.jar
```

### 3c — expense-service (port 8083)

> Uses `-exec.jar` (classifier in pom.xml). The plain `.jar` has no `Main-Class`.

```powershell
java -Xms64m -Xmx192m -XX:+UseSerialGC `
  "-DJWT_SECRET=$JWT" `
  "-DSPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5434/expense_db?sslmode=disable" `
  "-DSPRING_DATASOURCE_USERNAME=expense_user" `
  "-DSPRING_DATASOURCE_PASSWORD=expense_pass" `
  "-DSPRING_KAFKA_BOOTSTRAP_SERVERS=localhost:9092" `
  "-DMINIO_ENDPOINT=http://localhost:9000" `
  "-DMINIO_ACCESS_KEY=minio_admin" `
  "-DMINIO_SECRET_KEY=minio_secret" `
  -jar services/expense-service/target/expense-service-1.0.0-SNAPSHOT-exec.jar
```

### 3d — savings-goal-service (port 8084)

```powershell
java -Xms64m -Xmx192m -XX:+UseSerialGC `
  "-DJWT_SECRET=$JWT" `
  "-DSPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5435/savings_goal_db?sslmode=disable" `
  "-DSPRING_DATASOURCE_USERNAME=savings_goal_user" `
  "-DSPRING_DATASOURCE_PASSWORD=savings_goal_pass" `
  -jar services/savings-goal-service/target/savings-goal-service-1.0.0-SNAPSHOT.jar
```

### 3e — budget-service (port 8085)

```powershell
java -Xms64m -Xmx192m -XX:+UseSerialGC `
  "-DJWT_SECRET=$JWT" `
  "-DSPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5436/budget_db?sslmode=disable" `
  "-DSPRING_DATASOURCE_USERNAME=budget_user" `
  "-DSPRING_DATASOURCE_PASSWORD=budget_pass" `
  -jar services/budget-service/target/budget-service-1.0.0-SNAPSHOT.jar
```

---

## Step 4 — Start the Frontend

```powershell
cd frontend
npm run dev
```

Vite starts on **http://localhost:5173**.

Vite proxies API calls automatically:

| Path prefix               | Backend service         |
|---------------------------|-------------------------|
| `/api/v1/auth/*`          | user-service :8081      |
| `/api/v1/users/*`         | user-service :8081      |
| `/api/v1/categories/*`    | category-service :8082  |
| `/api/v1/expenses/*`      | expense-service :8083   |
| `/api/v1/savings-goals/*` | savings-goal-service :8084 |
| `/api/v1/budgets/*`       | budget-service :8085    |

---

## Step 5 — Health Check

```powershell
foreach ($port in 8081, 8082, 8083, 8084, 8085) {
  try {
    $r = Invoke-RestMethod "http://localhost:$port/actuator/health" -ErrorAction Stop
    Write-Host "Port $port : $($r.status)" -ForegroundColor Green
  } catch {
    Write-Host "Port $port : NOT UP" -ForegroundColor Red
  }
}
```

Expected: all 5 ports show `UP`.

---

## Step 6 — Register and Verify Account

1. Open **http://localhost:5173/register**, fill in name, email, password → click **Create account**
2. Click **Verify email** on the success screen (or go to **http://localhost:5173/verify-email**)
3. Enter your email address → click **Verify Email** → account is activated
4. Go to **http://localhost:5173/login** and sign in

> Email delivery is Phase 2. Verification works by entering your registered email on the verify page.

---

## Using the App

| Page           | URL                      | What you can do                                    |
|----------------|--------------------------|----------------------------------------------------|
| Expenses       | `/expenses`              | Add, edit, delete expenses; filter by date; CSV export/import |
| Categories     | `/categories`            | Create custom categories with colour and icon      |
| Budgets        | `/budgets`               | Set monthly/weekly limits per category; toggle active/rollover |
| Savings Goals  | `/savings-goals`         | Create goals, record contributions, pause/resume   |
| Profile        | `/profile`               | Edit name, timezone, change password               |

---

## API Examples (curl / PowerShell)

Set these variables once:

```powershell
$BASE_USER     = "http://localhost:8081"
$BASE_CATEGORY = "http://localhost:8082"
$BASE_EXPENSE  = "http://localhost:8083"
$BASE_GOAL     = "http://localhost:8084"
$BASE_BUDGET   = "http://localhost:8085"
```

### Register

```powershell
Invoke-RestMethod -Method POST "$BASE_USER/api/v1/auth/register" `
  -ContentType "application/json" `
  -Body '{"fullName":"Alice Smith","email":"alice@example.com","password":"P@ssword123"}'
```

### Verify (activate account)

```powershell
Invoke-RestMethod -Method POST "$BASE_USER/api/v1/auth/verify-email/by-email" `
  -ContentType "application/json" `
  -Body '{"email":"alice@example.com"}'
```

### Login — get JWT token

```powershell
$resp  = Invoke-RestMethod -Method POST "$BASE_USER/api/v1/auth/login" `
           -ContentType "application/json" `
           -Body '{"email":"alice@example.com","password":"P@ssword123"}'
$TOKEN = $resp.accessToken
$HDR   = @{ Authorization = "Bearer $TOKEN" }
```

---

### Categories

```powershell
# Create
Invoke-RestMethod -Method POST "$BASE_CATEGORY/api/v1/categories" -Headers $HDR `
  -ContentType "application/json" `
  -Body '{"name":"Food","type":"EXPENSE","color":"#FF5733","icon":"pizza"}'

# List
Invoke-RestMethod "$BASE_CATEGORY/api/v1/categories" -Headers $HDR

# Delete
Invoke-RestMethod -Method DELETE "$BASE_CATEGORY/api/v1/categories/{categoryId}" -Headers $HDR
```

---

### Expenses

```powershell
# Create  (replace categoryId with a real UUID from the list above)
Invoke-RestMethod -Method POST "$BASE_EXPENSE/api/v1/expenses" -Headers $HDR `
  -ContentType "application/json" `
  -Body '{
    "amount": {"amount":"450.00","currency":"INR"},
    "categoryId": "PASTE-CATEGORY-UUID-HERE",
    "description": "Lunch at canteen",
    "date": "2026-06-30",
    "paymentMethod": "UPI"
  }'

# List
Invoke-RestMethod "$BASE_EXPENSE/api/v1/expenses" -Headers $HDR

# Filter by date range
Invoke-RestMethod "$BASE_EXPENSE/api/v1/expenses?from=2026-06-01&to=2026-06-30" -Headers $HDR

# Export CSV (saves to current directory)
Invoke-WebRequest "$BASE_EXPENSE/api/v1/expenses/export?from=2026-06-01&to=2026-06-30" `
  -Headers $HDR -OutFile "expenses.csv"
```

---

### Savings Goals

```powershell
# Create
Invoke-RestMethod -Method POST "$BASE_GOAL/api/v1/savings-goals" -Headers $HDR `
  -ContentType "application/json" `
  -Body '{
    "name": "New Laptop",
    "targetAmount": {"amount":"80000.00","currency":"INR"},
    "targetDate": "2026-12-31",
    "description": "Save for MacBook Pro"
  }'

# List
Invoke-RestMethod "$BASE_GOAL/api/v1/savings-goals" -Headers $HDR

# Record a contribution
Invoke-RestMethod -Method POST "$BASE_GOAL/api/v1/savings-goals/{goalId}/contributions" `
  -Headers $HDR -ContentType "application/json" `
  -Body '{"amount":{"amount":"5000.00","currency":"INR"},"date":"2026-06-30"}'
```

---

### Budgets

```powershell
# Create overall budget
Invoke-RestMethod -Method POST "$BASE_BUDGET/api/v1/budgets" -Headers $HDR `
  -ContentType "application/json" `
  -Body '{
    "scope": "OVERALL",
    "budgetLimit": 30000.00,
    "periodType": "MONTHLY",
    "rolloverEnabled": false
  }'

# Create category budget
Invoke-RestMethod -Method POST "$BASE_BUDGET/api/v1/budgets" -Headers $HDR `
  -ContentType "application/json" `
  -Body '{
    "scope": "CATEGORY",
    "categoryId": "PASTE-CATEGORY-UUID-HERE",
    "budgetLimit": 5000.00,
    "periodType": "MONTHLY",
    "rolloverEnabled": false
  }'

# List
Invoke-RestMethod "$BASE_BUDGET/api/v1/budgets" -Headers $HDR

# Toggle active
Invoke-RestMethod -Method PATCH "$BASE_BUDGET/api/v1/budgets/{budgetId}/activation" `
  -Headers $HDR -ContentType "application/json" -Body '{"active":true}'

# Toggle rollover
Invoke-RestMethod -Method PATCH "$BASE_BUDGET/api/v1/budgets/{budgetId}/rollover" `
  -Headers $HDR -ContentType "application/json" -Body '{"rolloverEnabled":true}'
```

---

### User Profile

```powershell
# Get profile
Invoke-RestMethod "$BASE_USER/api/v1/users/me" -Headers $HDR

# Update profile
Invoke-RestMethod -Method PUT "$BASE_USER/api/v1/users/me" -Headers $HDR `
  -ContentType "application/json" `
  -Body '{"fullName":"Alice Johnson","timezone":"Asia/Kolkata","locale":"en-IN","weeklyDigestEnabled":false}'

# Change password
Invoke-RestMethod -Method PATCH "$BASE_USER/api/v1/users/me/password" -Headers $HDR `
  -ContentType "application/json" `
  -Body '{"currentPassword":"P@ssword123","newPassword":"NewP@ss456"}'
```

---

## Dev Tools

| Tool    | URL                    | Credentials              |
|---------|------------------------|--------------------------|
| MailHog | http://localhost:8025  | No login needed          |
| MinIO   | http://localhost:9001  | minio_admin / minio_secret |

---

## Stop Everything

```powershell
.\stop-all.ps1
```

Or manually:

```powershell
# Stop all Java services
Get-Process java -ErrorAction SilentlyContinue | Stop-Process -Force

# Stop Docker
docker compose down

# Frontend: press Ctrl+C in the Vite terminal
```

---

## Troubleshooting

| Problem | Cause | Fix |
|---------|-------|-----|
| Port 5432 conflict | Local PostgreSQL on 5432 | identity DB is on **5437** — already correct in all commands |
| `no main manifest` on expense-service | Using plain `.jar` | Use `expense-service-1.0.0-SNAPSHOT-exec.jar` (step 3c) |
| `mvnw.cmd` fails with `no main manifest` | Broken wrapper JAR | Use the `$mvn` variable pointing to `~/.m2/wrapper/dists/...` (step 2) |
| Login returns 401 | Account not verified | Go to `/verify-email`, enter your email, click Verify |
| Services not responding after `start-all.ps1` | Still booting | Wait 30 s then re-run the health check |
| Kafka `LEADER_NOT_AVAILABLE` on first start | Normal transient | Ignore — resolves in a few seconds |
| Service OOM crash | Default JVM heap too large | All services use `-Xms64m -Xmx192m -XX:+UseSerialGC` |
