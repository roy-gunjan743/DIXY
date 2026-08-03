# DIXY — Virtual Deadlock Detection (VDD) System

> Real-time deadlock detection, virtual-thread pinning analysis, and pinning-correlated
> root-cause reporting for Java 21 Virtual Threads & Platform Threads — backed by a
> native JVMTI agent, a JFR event pipeline, and a Spring Boot graph engine.

---

## 📑 Table of Contents

1. [What's New](#-whats-new)
2. [Prerequisites](#-prerequisites)
3. [Project Architecture Overview](#-project-architecture-overview)
4. [How Detection Works](#-how-detection-works)
5. [Local Development & Testing](#-local-development--testing)
   - [Step 1: Start Infrastructure Dependencies (PostgreSQL & Redis)](#step-1-start-infrastructure-dependencies-postgresql--redis)
   - [Step 2: Build the Native JVMTI Agent](#step-2-build-the-native-jvmti-agent)
   - [Step 3: Build the Application](#step-3-build-the-application)
   - [Step 4: Run Automated Tests](#step-4-run-automated-tests)
   - [Step 5: Run the Application Locally](#step-5-run-the-application-locally)
6. [Interactive CLI (Spring Shell)](#-interactive-cli-spring-shell)
7. [Authentication & Security](#-authentication--security)
8. [Running with Docker & Docker Compose](#-running-with-docker--docker-compose)
9. [Deploying to Kubernetes (k8s)](#️-deploying-to-kubernetes-k8s)
10. [Observability & Monitoring](#-observability--monitoring)
11. [REST API Reference](#-rest-api-reference)
12. [Continuous Integration](#-continuous-integration)
13. [Evidence & Verification Logs](#-evidence--verification-logs)

---

## 🆕 What's New

This revision of the README reflects the current state of the codebase, which has grown
significantly beyond a simple graph-based detector:

- **Native JVMTI agent** (`native/`, built with CMake) plus a full in-process
  `LockDetectionAgent` pipeline (JVMTI bridge → JFR recorder/listener → `EventCollector` →
  `WaitForGraph`) that observes real platform- and virtual-thread lock activity instead of
  relying solely on `ThreadMXBean` dumps.
- **Pinning-correlated deadlock detection.** `DeadlockDetector` finds cycles via DFS
  back-edge detection on the wait-for graph, and a separate `correlatePinning()` step
  attaches time-correlated virtual-thread pinning events (`PINS` edges) to a detected
  cycle — surfacing *why* a deadlock is especially severe on the virtual-thread runtime.
- **API-key authentication is now enforced.** `SecurityConfig` + `ApiKeyAuthenticationFilter`
  require a valid `X-API-Key` header on every endpoint except the actuator health check and
  static dashboard assets. CORS origins are now read from configuration instead of being
  wide open (`@CrossOrigin(origins = "*")` on individual controllers has been superseded by
  the global security filter chain).
- **Built-in web dashboard.** A static single-page dashboard (`src/main/resources/static/index.html`)
  is served directly by the app and consumes `/api/v1/status` for a live HEALTHY / WARNING /
  CRITICAL view of threads, locks, deadlocks, and pinning.
- **Dual run profiles.** The app can run as a headless detection engine with an interactive
  shell (`cli` profile) or as a web/dashboard/API server with the shell disabled (`web` profile).
- **CI now builds the native agent.** GitHub Actions installs `cmake`/`g++`, compiles the
  JVMTI shared library, runs the full Gradle test suite (including Testcontainers-based
  end-to-end deadlock scenarios), and builds the Docker image on every push/PR to `main`.
- **Package publishing.** `build.gradle` now publishes the agent jar (plus a `boot`-classified
  fat jar) to GitHub Packages via a dedicated workflow.

---

## 🛠 Prerequisites

Ensure the following tools are installed on your machine:

| Tool | Recommended Version | Purpose |
|---|---|---|
| **JDK** | Java 21 (Temurin / OpenJDK) | Application runtime & build |
| **Gradle** | 8.x+ (or use included `./gradlew`) | Build system |
| **CMake** | 3.20+ | Builds the native JVMTI agent |
| **C++ toolchain** | g++ / clang / MSVC | Compiles `native/src/agent.cpp` |
| **Docker & Docker Compose** | Latest Desktop / Engine | Containerization & local stack |
| **kubectl** | v1.28+ | Kubernetes deployment management |
| **Kubernetes Cluster** | Minikube / Kind / Docker Desktop K8s | Container orchestration |

---

## 🏗 Project Architecture Overview

- **Port `8080` / `8081`**: Spring Boot REST API, static dashboard & Actuator
- **Port `5432` / `5434`**: PostgreSQL 16 (Persistence)
- **Port `6379`**: Redis 7 (Fast telemetry cache)
- **Port `9090`**: Prometheus (Metrics scraper)
- **Port `3000`**: Grafana (Monitoring dashboards)

**Major code areas:**

| Package | Responsibility |
|---|---|
| `LockDetectionAgent/agent` | JVMTI `-javaagent` bootstrap, `Premain-Class`, agent lifecycle |
| `LockDetectionAgent/jvmti` | JNI bridge to the native agent, loads the compiled shared library |
| `LockDetectionAgent/jfr` | JFR recorder + event listener for monitor-enter / thread events |
| `LockDetectionAgent/collector`, `sender`, `runtime` | In-process event pipeline feeding the shared `WaitForGraph` |
| `graph/model`, `graph/registry` | Graph domain model — nodes, edges (`WAITS_FOR`, `HOLDS`, `PINS`), thread/lock registries |
| `graph/detector` | `DeadlockDetector` (cycle detection) and `PinningDetector` |
| `graph/report` | Human-readable deadlock report generation |
| `backend/controller` | REST API surface (status, threads, locks, deadlocks, graph, telemetry) |
| `backend/security` | API-key auth filter, CORS/security filter chain |
| `cli` | Spring Shell commands (`status`, `deadlocks *`, `threads *`, `locks *`) |
| `native/` | Standalone CMake project compiling the JVMTI shared library used by the agent |

---

## 🔍 How Detection Works

1. The **native JVMTI agent** and **JFR listener** observe monitor acquisitions, blocks, and
   thread-carrier pinning at the JVM level — covering the observability gaps in
   `ThreadMXBean` for virtual threads (see [Evidence & Verification Logs](#-evidence--verification-logs)).
2. Observed events flow through `EventCollector` → `GraphGateway` into a shared,
   in-memory `WaitForGraph` (`RuntimeContext.GRAPH`).
3. `DeadlockDetector.detect()` runs DFS-based cycle detection over the graph to find
   `WAITS_FOR` / `HOLDS` cycles — classic deadlocks.
4. `correlatePinning()` separately checks for any sufficiently-recent `PINS` edge in the
   graph and attaches it to a detected cycle **by time**, not by structural position,
   surfacing cases where a virtual-thread pin is compounding a deadlock elsewhere in the JVM.
5. Results are exposed via the REST API, the Spring Shell CLI, and the built-in dashboard,
   and persisted to PostgreSQL for historical reporting.

---

## 💻 Local Development & Testing

### Step 1: Start Infrastructure Dependencies (PostgreSQL & Redis)

```bash
# Start PostgreSQL (port 5434 mapped locally) and Redis (port 6379) in background
docker compose up -d postgres redis
```

### Step 2: Build the Native JVMTI Agent

The JVMTI bridge requires the native shared library to be compiled first:

```bash
cmake -S native -B native/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/build --config Release
```

### Step 3: Build the Application

Set your environment variables and compile the project using Gradle:

#### On Windows (PowerShell):
```powershell
$env:DB_PASSWORD="gupi@1234"
.\gradlew.bat clean build -x test
```

#### On Windows (CMD):
```cmd
set DB_PASSWORD=gupi@1234
gradlew.bat clean build -x test
```

#### On Linux / macOS:
```bash
export DB_PASSWORD="gupi@1234"
./gradlew clean build -x test
```

---

### Step 4: Run Automated Tests

Execute the JUnit 5 test suite, including Testcontainers-backed end-to-end deadlock scenarios:

```bash
./gradlew test
```

Notable end-to-end suites:

| Test class | Scenario |
|---|---|
| `ClassicDeadlockEndToEndTest` | Two platform threads deadlocked via `synchronized` blocks |
| `VirtualThreadPinningEndToEndTest` | A virtual thread pinning its carrier |
| `HybridPinningDeadlockEndToEndTest` | A live platform-thread deadlock *and* a concurrent virtual-thread pin, correlated together |
| `VirtualDeadlockDetectionApplicationTests` | Spring context / application smoke test |

To run a specific test class:
```bash
./gradlew test --tests com.virtual_deadlock_detection.HybridPinningDeadlockEndToEndTest
```

---

### Step 5: Run the Application Locally

The application supports two Spring profiles:

- **`cli`** — headless detection engine + interactive Spring Shell, no web server.
- **`web`** — REST API + dashboard, shell disabled.

#### Option A: Using the Gradle `bootRun` task
```bash
# Linux / macOS
DB_PASSWORD="gupi@1234" ./gradlew bootRun --args='--spring.profiles.active=web'

# Windows PowerShell
$env:DB_PASSWORD="gupi@1234"
.\gradlew.bat bootRun --args='--spring.profiles.active=web'
```

#### Option B: Using the Packaged JAR
```bash
# Linux / macOS
DB_PASSWORD="gupi@1234" java -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar --spring.profiles.active=web

# Windows PowerShell
$env:DB_PASSWORD="gupi@1234"
java -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar --spring.profiles.active=web
```

#### Option C: Using provided run scripts
- **Windows**: `.\run.bat gupi@1234`
- **Linux/macOS**: `./run.sh gupi@1234`

> Omit `--spring.profiles.active=web` to fall back to the default configuration
> (interactive shell enabled, servlet container still active per `application.yml`).

---

## 🖥 Interactive CLI (Spring Shell)

When running with the shell enabled, the Spring Shell interactive prompt appears:

```text
  ____   _  _  ____
 / ___| | || ||  _ \
| |     | || || | | |
| |___  |__  || |_| |
 \____|    |_||____/

vdd:>
```

### CLI Command Reference

| Command | Description |
|---|---|
| `status` | Show full diagnostic status (HEALTHY / WARNING / CRITICAL) |
| `deadlocks seed` | Seed the database with a demo 2-way deadlock scenario |
| `deadlocks detect` | Run live deadlock detection on the current wait-for graph |
| `deadlocks list` | List all stored deadlock reports |
| `deadlocks clear` | Clear all threads, locks, and wait-edges from the database |
| `threads list` | List all registered threads in the database |
| `threads get --thread-id <id>` | Get a thread by JVM thread-id |
| `threads delete --id <id>` | Delete a thread by its database ID |
| `locks list` | List all locks in the database |
| `locks get --lock-id <id>` | Get a lock by its lock-id string |
| `locks delete --id <id>` | Delete a lock by its database ID |
| `help` | View all available shell commands |
| `exit` / `quit` | Exit the CLI session |

---

## 🔐 Authentication & Security

All API endpoints — except `/actuator/health` and the static dashboard assets
(`/`, `/index.html`, `/dashboard.html`, `/static/**`, `/css/**`, `/js/**`, `/favicon.ico`) —
require a valid API key.

- Configure the expected key via `security.api-key` (see `application.properties`) or the
  `SECURITY_API_KEY` environment variable in your deployment.
- Send it on every request as an `X-API-Key` header:

```bash
curl -H "X-API-Key: <your-api-key>" http://localhost:8081/api/threads
```

- CORS is restricted to origins listed in `security.allowed-origins`
  (defaults to `http://localhost:3000`; comma-separate multiple origins, or set the
  `ALLOWED_ORIGINS` environment variable).
- The API is stateless (no cookies/sessions) — auth is enforced per-request by
  `ApiKeyAuthenticationFilter`, and unauthenticated requests are rejected by
  `AuthenticationEntryPoint`.

> ⚠️ Treat the value in `application.properties` as a local development placeholder only —
> set a fresh secret via environment variable for any shared or deployed environment.

---

## 🐳 Running with Docker & Docker Compose

### Build Docker Image Standalone

The Docker build compiles both the Java application and the native JVMTI agent in a
multi-stage build:

```bash
docker build -t vdd-app:latest -f src/main/java/com/virtual_deadlock_detection/devOps/dev.dockerfile .
```

### Full Stack with Docker Compose

Spin up the complete ecosystem:
- **`vdd-postgres`** (PostgreSQL 16)
- **`vdd-redis`** (Redis 7)
- **`vdd-app`** (Spring Boot app with JVMTI agent, port `8080`)
- **`vdd-prometheus`** (Prometheus server)
- **`vdd-grafana`** (Grafana dashboards)

```bash
# 1. Build and start all services in detached mode
docker compose up -d --build

# 2. Check running status of all containers
docker compose ps

# 3. Follow application logs
docker compose logs -f app

# 4. Stop all services
docker compose down

# 5. (Optional) Stop and wipe all database & Grafana volumes
docker compose down -v
```

---

## ☸️ Deploying to Kubernetes (k8s)

All deployment manifests are located in the [`k8s/`](k8s/) directory.

### Step-by-Step Manifest Application

```bash
# 1. Create the dedicated namespace
kubectl apply -f k8s/namespace.yml

# 2. Create Database & Redis secrets
kubectl apply -f k8s/secret.yml

# 3. Deploy PostgreSQL storage, pod & service
kubectl apply -f k8s/postgres-pvc.yml
kubectl apply -f k8s/postgres-deployment.yml
kubectl apply -f k8s/postgres-service.yml

# 4. Deploy Redis pod & service
kubectl apply -f k8s/redis-deployment.yml
kubectl apply -f k8s/redis-service.yml

# 5. Deploy Prometheus ConfigMap, pod & service
kubectl apply -f k8s/prometheus-configmap.yml
kubectl apply -f k8s/prometheus-deployment.yml
kubectl apply -f k8s/prometheus-service.yml

# 6. Deploy Grafana storage, pod & service
kubectl apply -f k8s/grafana-pvc.yml
kubectl apply -f k8s/grafana-deployment.yml
kubectl apply -f k8s/grafana-service.yml

# 7. Deploy the Virtual Deadlock Detection application & service
kubectl apply -f k8s/app-deployment.yml
kubectl apply -f k8s/app-service.yml
```

> 💡 **Quick deploy all manifests:**
> ```bash
> kubectl apply -f k8s/
> ```

### Verify Pod & Service Health

```bash
kubectl get all -n vdd
kubectl get pods -n vdd -w
kubectl logs -n vdd -l app=vdd-app -f
```

### Accessing Services via Port-Forward

```bash
# App REST API, dashboard & Actuator (http://localhost:8080)
kubectl port-forward -n vdd svc/app 8080:8080

# Prometheus UI (http://localhost:9090)
kubectl port-forward -n vdd svc/prometheus 9090:9090

# Grafana Dashboard (http://localhost:3000)
kubectl port-forward -n vdd svc/grafana 3000:3000
```

### Tear Down Kubernetes Resources

```bash
# Delete all resources defined in manifests
kubectl delete -f k8s/

# Or delete the entire namespace directly
kubectl delete namespace vdd
```

---

## 📊 Observability & Monitoring

| Component | URL | Notes |
|---|---|---|
| **App Health** | `http://localhost:8080/actuator/health` | Public — no API key required |
| **Prometheus Metrics** | `http://localhost:8080/actuator/prometheus` | Raw metrics stream scraped by Prometheus |
| **Prometheus Dashboard** | `http://localhost:9090` | Expression query browser & target status |
| **Grafana UI** | `http://localhost:3000` | User: `admin` \| Password: `admin` (dev default — change in production) |
| **Built-in Dashboard** | `http://localhost:8080/` | Live HEALTHY / WARNING / CRITICAL view backed by `/api/v1/status` |

---

## 🌐 REST API Reference

All routes below require `X-API-Key: <key>` unless noted otherwise (see
[Authentication & Security](#-authentication--security)).

### Live diagnostic status

```bash
# Full HEALTHY/WARNING/CRITICAL snapshot: metrics, active deadlock cycles, pinning details
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/v1/status

# Seed a demo 2-way deadlock + pinning scenario
curl -X POST -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/v1/status/seed

# Clear all threads, locks, and wait-edges
curl -X DELETE -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/v1/status/clear
```

### Deadlock reports

```bash
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/deadlocks
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/deadlocks/{id}
curl -X POST -H "X-API-Key: $VDD_API_KEY" -H "Content-Type: application/json" \
     -d '{ ... }' http://localhost:8081/api/deadlocks
```

### Graph-level detection

```bash
# Run cycle detection directly against the live in-memory WaitForGraph
curl -X POST -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/graph/detect
```

### Threads

```bash
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/threads
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/threads/{threadId}
curl -X POST -H "X-API-Key: $VDD_API_KEY" -H "Content-Type: application/json" \
     -d '{ ... }' http://localhost:8081/api/threads
curl -X DELETE -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/threads/{id}
```

### Locks

```bash
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/locks
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/locks/{lockId}
curl -X POST -H "X-API-Key: $VDD_API_KEY" -H "Content-Type: application/json" \
     -d '{ ... }' http://localhost:8081/api/locks
curl -X DELETE -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/locks/{id}
```

### Telemetry & internal agent ingestion

```bash
# Public telemetry ingestion endpoint used by external reporters
curl -X POST -H "X-API-Key: $VDD_API_KEY" -H "Content-Type: application/json" \
     -d '{ ... }' http://localhost:8081/api/telemetry

# Internal endpoint the in-process agent posts detected deadlocks to
curl -X POST -H "X-API-Key: $VDD_API_KEY" -H "Content-Type: application/json" \
     -d '{ ... }' http://localhost:8081/api/internal/deadlocks
```

### Actuator (public)

```bash
curl http://localhost:8081/actuator/health
```

---

## ⚙️ Continuous Integration

GitHub Actions (`.github/workflows/`) runs on every push and pull request to `main`:

- **`ci.yml`** — sets up JDK 21, installs `cmake`/`g++`/`make`, builds the native JVMTI
  library, runs `./gradlew test`, builds the `bootJar`/`jar` artifacts, and builds the
  Docker image.
- **`publish-package.yml`** — publishes the agent jar (plus a `boot`-classified fat jar) to
  GitHub Packages on pushes to `main`, on releases, or via manual dispatch.
- **`qodana_code_quality.yml`** — runs JetBrains Qodana static analysis on pushes and pull
  requests to `main`.

---

## 🧾 Evidence & Verification Logs

The [`evidence/`](evidence/) directory contains dated, reproducible verification runs
documenting detector behavior, superseding older evidence as the detector evolves:

- **`evidence/2026-07-27-e2e-pinning-correlation-pass/`** — proves that
  `HybridPinningDeadlockEndToEndTest` correlates a real platform-thread deadlock with a real,
  concurrent virtual-thread pinning event through the full production pipeline
  (JVMTI bridge → JFR → `EventCollector` → `WaitForGraph` → `DeadlockDetector`), with logs
  from both a local Windows run and Linux CI. It also documents two JDK observability gaps
  (carrier-thread misattribution, and `ThreadMXBean`/JFR's inability to observe an
  unresolved virtual-thread block) that motivate the native JVMTI bridge component.
- **`evidence/2026-07-19-e2e-pinning-deadlock-pass/`** — earlier evidence, superseded by the
  above once correlation (rather than mere coexistence of edge types) became the tested claim.

---