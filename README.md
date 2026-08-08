# DIXY — Virtual Deadlock Detection (VDD)

> Real-time deadlock detection, virtual-thread pinning analysis, and pinning-correlated
> root-cause reporting for Java 21 Virtual Threads & Platform Threads — backed by a
> native JVMTI agent, a JFR event pipeline, and a Spring Boot graph engine.

[![CI](https://img.shields.io/github/actions/workflow/status/roy-gunjan743/DIXY/ci.yml?branch=main&label=CI)](https://github.com/roy-gunjan743/DIXY/actions/workflows/ci.yml)
[![Qodana](https://img.shields.io/github/actions/workflow/status/roy-gunjan743/DIXY/qodana_code_quality.yml?branch=main&label=Qodana)](https://github.com/roy-gunjan743/DIXY/actions/workflows/qodana_code_quality.yml)
[![Java](https://img.shields.io/badge/Java-21-orange)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1-brightgreen)](https://spring.io/projects/spring-boot)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

---

## ⚡ Quick Start

The fastest way to see DIXY detect a deadlock is Docker Compose — it builds the native
JVMTI agent, the Java app, and starts PostgreSQL, Redis, Prometheus, and Grafana for you.

**Requirements:** Docker Desktop / Docker Engine with Compose v2.

```bash
# 1. Clone the repo
git clone https://github.com/roy-gunjan743/DIXY.git
cd DIXY

# 2. Build and start the full stack in the background
docker compose up -d --build

# 3. Watch it come up
docker compose logs -f app
```

Once the app container reports it has started, verify it's healthy:

```bash
curl http://localhost:8081/actuator/health
```

Seed a demo deadlock + pinning scenario and view the live diagnostic status:

```bash
curl -X POST -H "X-API-Key: 3e81238b-ba64-42b1-b3d7-c50ea1d65d9b" \
     http://localhost:8081/api/v1/status/seed

curl -H "X-API-Key: 3e81238b-ba64-42b1-b3d7-c50ea1d65d9b" \
     http://localhost:8081/api/v1/status
```

You should see a `CRITICAL` status with a detected two-way deadlock and correlated
pinning details in the response.

Open the built-in dashboard in your browser at **http://localhost:8081/**, or explore
metrics in Grafana at **http://localhost:3000** (`admin` / `admin`).

> ⚠️ **Port note:** `docker-compose.yml` publishes container port `8080`, but the app's
> own default (`server.port` in `application.yml`) is `8081`, and Compose does not
> override `PORT`. This means the app is actually reachable at **`:8081`**, not `:8080`,
> unless you add `PORT: "8080"` to the `app` service's environment. The commands above
> already use the correct port — but if you hit a blank `:8080`, this is why.
>
> The default `X-API-Key` above (`3e81238b-...`) comes from `application.properties` and
> is a **local development placeholder only** — see [Authentication & Security](#-authentication--security).

**To stop everything:**

```bash
docker compose down        # stop containers
docker compose down -v     # stop and wipe database/Grafana volumes too
```

That's it — you have a running deadlock detector, a Postgres-backed history of reports,
and dashboards, all from one command. For running the agent against your **own**
application, building from source, Kubernetes deployment, or the CLI, see the sections below.

---

## 📑 Table of Contents

1. [Quick Start](#-quick-start)
2. [What's New](#-whats-new)
3. [Key Features](#-key-features)
4. [Prerequisites](#-prerequisites)
5. [Project Architecture Overview](#-project-architecture-overview)
6. [How Detection Works](#-how-detection-works)
7. [Local Development & Testing](#-local-development--testing)
8. [Interactive CLI (Spring Shell)](#-interactive-cli-spring-shell)
9. [Authentication & Security](#-authentication--security)
10. [Running with Docker & Docker Compose](#-running-with-docker--docker-compose)
11. [Deploying to Kubernetes (k8s)](#️-deploying-to-kubernetes-k8s)
12. [Observability & Monitoring](#-observability--monitoring)
13. [REST API Reference](#-rest-api-reference)
14. [Continuous Integration](#-continuous-integration)
15. [Evidence & Verification Logs](#-evidence--verification-logs)
16. [License](#-license)

---

## 🆕 What's New

- **Standalone `-javaagent` jar.** `build.gradle` now produces a minimal, shaded
  `*-agent.jar` (via the Shadow plugin) containing only what the agent needs
  (SLF4J + `jakarta.annotation`) — separate from the full Spring Boot fat jar — so it can
  attach to *any* target JVM without dragging in JPA, Redis, or Spring Security.
- **Self-extracting, cross-platform native library.** The JVMTI bridge (`native/`) is now
  built per OS/architecture into `src/main/resources/native/<OS>-<arch>/`, bundled as a
  resource, and extracted to a temp file at runtime by `NativeLoader` — no more
  `-Djava.library.path` flag, and it now works out of the box on both Linux and Windows.
- **Pinning-correlated deadlock detection.** `DeadlockDetector` finds cycles via DFS
  back-edge detection on the wait-for graph, and a separate `correlatePinning()` step
  attaches time-correlated virtual-thread pinning events (`PINS` edges) to a detected
  cycle — surfacing *why* a deadlock is especially severe on the virtual-thread runtime.
- **API-key authentication is enforced.** `SecurityConfig` + `ApiKeyAuthenticationFilter`
  require a valid `X-API-Key` header on every endpoint except the actuator health check
  and static dashboard assets. CORS origins are read from configuration.
- **Built-in web dashboard.** A static single-page dashboard
  (`src/main/resources/static/index.html`) is served directly by the app and consumes
  `/api/v1/status` for a live HEALTHY / WARNING / CRITICAL view.
- **Dual run profiles** — `cli` (headless detection + interactive shell) and `web`
  (REST API + dashboard, shell disabled).
- **MIT-licensed.** A `LICENSE` file is now included at the repository root.

---

## ✨ Key Features

- 🧵 **Virtual & platform thread aware** — tracks `WAITS_FOR`, `HOLDS`, and `PINS` edges
  across both thread models on Java 21.
- 🔍 **Cycle detection via DFS back-edge analysis** on a live, in-memory wait-for graph.
- 📌 **Pinning correlation** — attaches carrier-thread pinning events to deadlock cycles
  by time, revealing root causes that pure cycle detection would miss.
- 🧩 **Native JVMTI + JFR + `ThreadMXBean` pipeline**, closing observability gaps neither
  source can cover alone (see [How Detection Works](#-how-detection-works)).
- 🖥️ **Interactive Spring Shell CLI** for headless environments and scripting.
- 🌐 **REST API + built-in dashboard** for live status, historical reports, and manual control.
- 📊 **Prometheus + Grafana** wiring out of the box.
- 🐳 **Docker Compose** and **Kubernetes** manifests for local and cluster deployment.
- 🔐 **API-key authenticated**, CORS-restricted REST surface.

---

## 🛠 Prerequisites

| Tool | Recommended Version | Purpose |
|---|---|---|
| **JDK** | Java 21 (Temurin / OpenJDK) | Application runtime & build |
| **Gradle** | 8.x+ (or use included `./gradlew`) | Build system |
| **CMake** | 3.20+ | Builds the native JVMTI agent |
| **C++ toolchain** | g++ / clang / MSVC | Compiles `native/src/agent.cpp` |
| **Docker & Docker Compose** | Latest Desktop / Engine | Containerization & local stack |
| **kubectl** | v1.28+ | Kubernetes deployment management |
| **Kubernetes Cluster** | Minikube / Kind / Docker Desktop K8s | Container orchestration |

> Only Docker is required for the [Quick Start](#-quick-start) path above — the rest of
> this table applies if you're building and running from source.

---

## 🏗 Project Architecture Overview

- **Port `8081`** (default) / **`8080`** (Docker/K8s manifests): Spring Boot REST API,
  static dashboard & Actuator
- **Port `5432` / `5434`**: PostgreSQL 16 (persistence)
- **Port `6379`**: Redis 7 (fast telemetry cache)
- **Port `9090`**: Prometheus (metrics scraper)
- **Port `3000`**: Grafana (monitoring dashboards)

**Major code areas:**

| Package | Responsibility |
|---|---|
| `LockDetectionAgent/agent` | `-javaagent` bootstrap (`Premain-Class`), agent lifecycle, in-process `DetectionPipelineLifecycle` |
| `LockDetectionAgent/jvmti` | JNI bridge to the native agent; `NativeLoader` extracts and loads the OS/arch-specific shared library |
| `LockDetectionAgent/jfr` | JFR recorder + event listener for monitor-enter / thread events |
| `LockDetectionAgent/collector`, `sender`, `runtime`, `integration` | Event pipeline feeding the shared `WaitForGraph`, including `GraphGateway` |
| `graph/model`, `graph/registry` | Graph domain model — nodes, edges (`WAITS_FOR`, `HOLDS`, `PINS`), thread/lock registries |
| `graph/detector` | `DeadlockDetector` (cycle detection + pinning correlation) and `PinningDetector` |
| `graph/report` | Human-readable deadlock report generation |
| `backend/controller` | REST API surface (status, threads, locks, deadlocks, graph, telemetry, internal agent ingestion) |
| `backend/security` | API-key auth filter, CORS/security filter chain |
| `cli` | Spring Shell commands (`status`, `deadlocks *`, `threads *`, `locks *`) |
| `native/` | Standalone CMake project compiling the JVMTI shared library used by the agent |

---

## 🔍 How Detection Works

1. The **native JVMTI agent** and **JFR listener** observe monitor acquisitions, blocks,
   and thread-carrier pinning at the JVM level — covering observability gaps in
   `ThreadMXBean` for virtual threads (see [Evidence & Verification Logs](#-evidence--verification-logs)).
2. Observed events flow through `EventCollector` → `GraphGateway` into a shared,
   in-memory `WaitForGraph`.
3. `DeadlockDetector.detect()` runs DFS-based cycle detection over the graph to find
   `WAITS_FOR` / `HOLDS` cycles — classic deadlocks.
4. `correlatePinning()` separately checks for any sufficiently recent `PINS` edge in the
   graph and attaches it to a detected cycle **by time**, not by structural position,
   surfacing cases where a virtual-thread pin is compounding a deadlock elsewhere in the JVM.
5. Results are exposed via the REST API, the Spring Shell CLI, and the built-in
   dashboard, and persisted to PostgreSQL for historical reporting.

Sample detector output from a passing end-to-end run:

```text
======================================
      DEADLOCK DETECTED
======================================
Pinning-Correlated Deadlock Cycle:
  -> Monitor-174472571 --[HOLDS]--> deadlock-thread-A
  -> deadlock-thread-A --[WAITS_FOR]--> Monitor-1325488275
  -> Monitor-1325488275 --[HOLDS]--> deadlock-thread-B
  -> deadlock-thread-B --[WAITS_FOR]--> Monitor-174472571
  [Correlated pinning — a thread in this cycle was ALSO pinned]:
    -> concurrent-pinner --[PINS]--> carrier--1
```

---

## 💻 Local Development & Testing

### Step 1 — Start infrastructure dependencies (PostgreSQL & Redis)

```bash
docker compose up -d postgres redis
```

### Step 2 — Build the native JVMTI agent

```bash
cmake -S native -B native/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/build --config Release
```

This places the compiled shared library under
`src/main/resources/native/<OS>-<arch>/` so Gradle picks it up as a resource.

### Step 3 — Build the application

```bash
# Linux / macOS
export DB_PASSWORD="gupi@1234"
./gradlew clean build -x test

# Windows PowerShell
$env:DB_PASSWORD="gupi@1234"
.\gradlew.bat clean build -x test
```

### Step 4 — Run automated tests

```bash
./gradlew test
```

| Test class | Scenario |
|---|---|
| `ClassicDeadlockEndToEndTest` | Two platform threads deadlocked via `synchronized` blocks |
| `VirtualThreadPinningEndToEndTest` | A virtual thread pinning its carrier |
| `HybridPinningDeadlockEndToEndTest` | A live platform-thread deadlock *and* a concurrent virtual-thread pin, correlated together |
| `VirtualDeadlockDetectionApplicationTests` | Spring context / application smoke test |

Run a specific class:

```bash
./gradlew test --tests com.virtual_deadlock_detection.HybridPinningDeadlockEndToEndTest
```

### Step 5 — Run the application locally

Two Spring profiles are available:

- **`cli`** — headless detection engine + interactive Spring Shell, no web server.
- **`web`** — REST API + dashboard, shell disabled.

```bash
# Linux / macOS
DB_PASSWORD="gupi@1234" ./gradlew bootRun --args='--spring.profiles.active=web'

# Windows PowerShell
$env:DB_PASSWORD="gupi@1234"
.\gradlew.bat bootRun --args='--spring.profiles.active=web'
```

Or run the packaged jar directly:

```bash
DB_PASSWORD="gupi@1234" java -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar --spring.profiles.active=web
```

Or use the provided scripts: `./run.sh gupi@1234` (Linux/macOS) / `run.bat gupi@1234` (Windows).

> Omit `--spring.profiles.active=web` to fall back to the default configuration
> (interactive shell enabled, servlet container still active per `application.yml`).
> The app listens on **port `8081`** by default (`server.port` in `application.yml`).

### Attaching the standalone agent to another JVM

`./gradlew agentJar` builds a minimal, shaded `-javaagent` jar you can attach to
**any** Java 21 process to monitor it independently of this repo's own Spring Boot app:

```bash
java -javaagent:build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT-agent.jar \
     -jar your-application.jar
```

---

## 🖥 Interactive CLI (Spring Shell)

When running with the shell enabled, the Spring Shell prompt appears:

```text
  ____   _  _  ____
 / ___| | || ||  _ \
| |     | || || | | |
| |___  |__  || |_| |
 \____|    |_||____/

vdd:>
```

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
(`/`, `/index.html`, `/dashboard.html`, `/static/**`, `/css/**`, `/js/**`, `/favicon.ico`)
— require a valid API key.

- Configure the expected key via `security.api-key` (see `application.properties`) or the
  `SECURITY_API_KEY` environment variable in your deployment.
- Send it on every request as an `X-API-Key` header:

```bash
curl -H "X-API-Key: <your-api-key>" http://localhost:8081/api/threads
```

- CORS is restricted to origins listed in `security.allowed-origins`
  (defaults to `http://localhost:3000`; comma-separate multiple origins, or set the
  `ALLOWED_ORIGINS` environment variable).
- The API is stateless (no cookies/sessions) — auth is enforced per request by
  `ApiKeyAuthenticationFilter`, and unauthenticated requests are rejected by
  `AuthenticationEntryPoint`.

> ⚠️ The key committed in `application.properties` is a **local development placeholder
> only**. Set a fresh secret via environment variable for any shared or deployed environment.

---

## 🐳 Running with Docker & Docker Compose

### Build the Docker image standalone

The build compiles the native JVMTI library and the Java application in a multi-stage build:

```bash
docker build -t vdd-app:latest -f src/main/java/com/virtual_deadlock_detection/devOps/dev.dockerfile .
```

### Full stack with Docker Compose

```bash
docker compose up -d --build   # build and start everything
docker compose ps              # check container status
docker compose logs -f app     # follow application logs
docker compose down            # stop all services
docker compose down -v         # stop and wipe database/Grafana volumes
```

Services started: `vdd-postgres` (PostgreSQL 16), `vdd-redis` (Redis 7), `vdd-app`
(Spring Boot app + JVMTI agent), `vdd-prometheus`, and `vdd-grafana`.

---

## ☸️ Deploying to Kubernetes (k8s)

All manifests live in [`k8s/`](k8s/).

```bash
# Quick deploy everything
kubectl apply -f k8s/

# Or step by step
kubectl apply -f k8s/namespace.yml
kubectl apply -f k8s/secret.yml
kubectl apply -f k8s/postgres-pvc.yml
kubectl apply -f k8s/postgres-deployment.yml
kubectl apply -f k8s/postgres-service.yml
kubectl apply -f k8s/redis-deployment.yml
kubectl apply -f k8s/redis-service.yml
kubectl apply -f k8s/prometheus-configmap.yml
kubectl apply -f k8s/prometheus-deployment.yml
kubectl apply -f k8s/prometheus-service.yml
kubectl apply -f k8s/grafana-pvc.yml
kubectl apply -f k8s/grafana-deployment.yml
kubectl apply -f k8s/grafana-service.yml
kubectl apply -f k8s/app-deployment.yml
kubectl apply -f k8s/app-service.yml
```

**Verify health:**

```bash
kubectl get all -n vdd
kubectl get pods -n vdd -w
kubectl logs -n vdd -l app=vdd-app -f
```

**Port-forward for local access:**

```bash
kubectl port-forward -n vdd svc/app 8080:8080          # App REST API, dashboard & Actuator
kubectl port-forward -n vdd svc/prometheus 9090:9090   # Prometheus UI
kubectl port-forward -n vdd svc/grafana 3000:3000      # Grafana dashboard
```

**Tear down:**

```bash
kubectl delete -f k8s/
# or
kubectl delete namespace vdd
```

---

## 📊 Observability & Monitoring

| Component | URL | Notes |
|---|---|---|
| **App Health** | `http://localhost:8081/actuator/health` | Public — no API key required |
| **Prometheus Metrics** | `http://localhost:8081/actuator/prometheus` | Raw metrics stream scraped by Prometheus |
| **Prometheus Dashboard** | `http://localhost:9090` | Expression query browser & target status |
| **Grafana UI** | `http://localhost:3000` | User: `admin` \| Password: `admin` (dev default — change in production) |
| **Built-in Dashboard** | `http://localhost:8081/` | Live HEALTHY / WARNING / CRITICAL view backed by `/api/v1/status` |

---

## 🌐 REST API Reference

All routes below require `X-API-Key: <key>` unless noted otherwise (see
[Authentication & Security](#-authentication--security)).

**Live diagnostic status**
```bash
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/v1/status
curl -X POST -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/v1/status/seed
curl -X DELETE -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/v1/status/clear
```

**Deadlock reports**
```bash
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/deadlocks
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/deadlocks/{id}
curl -X POST -H "X-API-Key: $VDD_API_KEY" -H "Content-Type: application/json" \
     -d '{ ... }' http://localhost:8081/api/deadlocks
```

**Graph-level detection**
```bash
curl -X POST -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/graph/detect
```

**Threads**
```bash
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/threads
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/threads/{threadId}
curl -X POST -H "X-API-Key: $VDD_API_KEY" -H "Content-Type: application/json" \
     -d '{ ... }' http://localhost:8081/api/threads
curl -X DELETE -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/threads/{id}
```

**Locks**
```bash
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/locks
curl -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/locks/{lockId}
curl -X POST -H "X-API-Key: $VDD_API_KEY" -H "Content-Type: application/json" \
     -d '{ ... }' http://localhost:8081/api/locks
curl -X DELETE -H "X-API-Key: $VDD_API_KEY" http://localhost:8081/api/locks/{id}
```

**Telemetry & internal agent ingestion**
```bash
# Public telemetry ingestion endpoint used by external reporters
curl -X POST -H "X-API-Key: $VDD_API_KEY" -H "Content-Type: application/json" \
     -d '{ ... }' http://localhost:8081/api/telemetry

# Internal endpoint the standalone agent posts detected deadlocks to
curl -X POST -H "X-API-Key: $VDD_API_KEY" -H "Content-Type: application/json" \
     -d '{ ... }' http://localhost:8081/api/internal/deadlocks
```

**Actuator (public)**
```bash
curl http://localhost:8081/actuator/health
```

---

## ⚙️ Continuous Integration

GitHub Actions (`.github/workflows/`) runs on every push and pull request to `main`:

- **`ci.yml`** — sets up JDK 21, installs `cmake`/`g++`/`make`, builds the native JVMTI
  library, runs `./gradlew test`, builds the `bootJar`/`jar`/`agentJar` artifacts, and
  builds the Docker image.
- **`publish-package.yml`** — publishes the agent jar (plus a `boot`-classified fat jar)
  to GitHub Packages on pushes to `main`, on releases, or via manual dispatch.
- **`qodana_code_quality.yml`** — runs JetBrains Qodana static analysis on pushes and
  pull requests to `main`.

---

## 🧾 Evidence & Verification Logs

The [`evidence/`](evidence/) directory contains dated, reproducible verification runs
documenting detector behavior, superseding older evidence as the detector evolves:

- **`evidence/2026-07-27-e2e-pinning-correlation-pass/`** — proves that
  `HybridPinningDeadlockEndToEndTest` correlates a real platform-thread deadlock with a
  real, concurrent virtual-thread pinning event through the full production pipeline
  (JVMTI bridge → JFR → `EventCollector` → `WaitForGraph` → `DeadlockDetector`), with
  logs from both a local Windows run and Linux CI. It also documents two JDK
  observability gaps (carrier-thread misattribution, and `ThreadMXBean`/JFR's inability
  to observe an unresolved virtual-thread block) that motivate the native JVMTI bridge
  component.
- **`evidence/2026-07-19-e2e-pinning-deadlock-pass/`** — earlier evidence, superseded by
  the above once correlation (rather than mere coexistence of edge types) became the
  tested claim.

---

## 📄 License

Released under the [MIT License](LICENSE) © 2026 roy-gunjan743.