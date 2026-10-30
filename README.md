# 🧠⚡ DIXY — Virtual Deadlock Detection

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 21">
  <img src="https://img.shields.io/badge/Spring%20Boot-green?style=for-the-badge&logo=springboot&logoColor=white" alt="Spring Boot">
  <img src="https://img.shields.io/badge/JVMTI-Native%20Agent-purple?style=for-the-badge" alt="JVMTI">
  <img src="https://img.shields.io/badge/JFR-Java%20Flight%20Recorder-blue?style=for-the-badge" alt="JFR">
  <img src="https://img.shields.io/badge/PostgreSQL-Database-336791?style=for-the-badge&logo=postgresql&logoColor=white" alt="PostgreSQL">
  <img src="https://img.shields.io/badge/Docker-Supported-2496ED?style=for-the-badge&logo=docker&logoColor=white" alt="Docker">
</p>

<p align="center">
  <strong>🔍 Detect Java deadlocks and virtual-thread pinning with DIXY.</strong>
</p>

<p align="center">
  <em>Understand what your threads are waiting for — before a hidden concurrency problem becomes difficult to diagnose.</em>
</p>

---

## 🌈 What is DIXY?

**DIXY** is a Java runtime monitoring and deadlock-detection tool.

It watches thread and lock activity inside a Java application and builds a relationship graph that can be used to identify circular dependencies between threads.

In simple terms:

```text
Your Java Application
        │
        ▼
      DIXY
        │
        ├── 🧵 Watches threads
        ├── 🔒 Watches locks
        ├── 🔥 Reads JVM/JFR events
        ├── 🕸️ Builds relationships
        └── 🔍 Finds dangerous cycles
                │
                ▼
          🔴 Deadlock Found
```

DIXY also supports monitoring scenarios involving **Java Virtual Threads** and **virtual-thread pinning**.

---

# ✨ Features

### 🔒 Deadlock Detection

Detects circular dependencies where threads are waiting for resources held by other threads.

### 🧵 Virtual Thread Monitoring

Works with modern Java applications that use virtual threads.

### 📌 Virtual Thread Pinning

Uses JVM Flight Recorder information to help identify virtual-thread pinning.

### 🕸️ Relationship Graph

DIXY turns thread/lock relationships into a graph so circular dependencies can be identified.

### 🖥️ CLI Mode

Use DIXY directly from your terminal.

### 🌐 Web / API Mode

Run DIXY as a server and access its REST APIs.

### 📊 Monitoring

Supports Spring Boot Actuator, Prometheus, and Grafana.

### 🐳 Docker

Run the application and supporting services with Docker Compose.

### ☸️ Kubernetes

Deployment resources are included for Kubernetes environments.

---

# 🎯 Why use DIXY?

A deadlock can look like an application that has simply stopped responding.

For example:

```text
Thread A
   │
   ├── owns Lock A
   │
   └── waits for Lock B
                 │
                 ▼
              Thread B
                 │
                 ├── owns Lock B
                 │
                 └── waits for Lock A
```

Both threads are waiting forever:

```text
Thread A → Thread B
    ▲          │
    └──────────┘

       🔴 DEADLOCK
```

DIXY helps turn this hidden relationship into something that can be detected and investigated.

---

# 🚀 Getting Started

## Install

The public release repository is `roy-gunjan743/DIXY-CLI`. Install the latest
release with one command:

**macOS / Linux**

```bash
curl -fsSL https://raw.githubusercontent.com/roy-gunjan743/DIXY-CLI/main/install.sh | sh
```

**Windows PowerShell**

```powershell
irm https://raw.githubusercontent.com/roy-gunjan743/DIXY-CLI/main/install.ps1 | iex
```

The installer requires Java 21+, downloads the CLI and native agent from a
GitHub Release, verifies SHA-256 checksums, and installs to `~/.dixy` (Windows:
`%USERPROFILE%\.dixy`). Re-running it upgrades the jars in place and preserves
the install directory. Pin a release with `install.sh --version 1.0.0` or
`install.ps1 -Version 1.0.0`; uninstall with `install.sh uninstall` or
`install.ps1 -Uninstall`.

Open a new terminal after installation, then run `dixy` from any directory.
`dixy detect --pid <pid>`, `dixy detect --jar app.jar`, and `dixy detect --json`
stream live telemetry and return exit code 1 when a deadlock cycle is found.

To monitor a local application directory, pass its root to `dixy`. DIXY scans
that directory for runnable jars, asks you to choose when there is more than
one, starts the selected jar with the agent, and opens the shell:

```powershell
dixy C:\path\to\target\root
dixy> detect
```

The `detect` command then performs a live scan of the selected target's real
thread, lock, and wait-for telemetry every second. Press `Ctrl+C` to stop the
scan or type `exit` to leave the shell. The same flow works on macOS and Linux:

```bash
dixy /path/to/target/root
dixy> detect
```

## 💻 Requirements

| Tool | Needed for | Notes |
|---|---|---|
| ☕ **JDK 21** | Building and running | `java -version` must report 21 — the Gradle toolchain is pinned to Java 21. |
| 🐳 **Docker** (with Compose v2) | PostgreSQL, Redis, the full stack, and `./gradlew test` | Tests start throwaway PostgreSQL/Redis containers (Testcontainers), so Docker must be **running** even for tests. |
| 🌐 **Internet access** (first build) | Gradle wrapper and dependencies | The wrapper downloads Gradle 9.5.1 on first use. You do **not** need to install Gradle yourself. |
| 🔧 CMake + C++ compiler | *Optional* | Only to rebuild the native JVMTI library. Prebuilt libraries are already committed. |

> **Platform support:** the bundled native JVMTI library exists for **Linux x86-64** and **Windows x86-64**. The `-javaagent` needs it, so on macOS or ARM machines the agent fails with an `UnsatisfiedLinkError` unless you supply your own native build.

---

## 1️⃣ Get the code

```bash
git clone https://github.com/roy-gunjan743/DIXY.git
cd DIXY
```

---

## 2️⃣ Build

**Linux / macOS**

```bash
chmod +x gradlew        # only needed if the wrapper is not executable
./gradlew clean bootJar agentJar -x test
```

**Windows (PowerShell)**

```powershell
.\gradlew.bat clean bootJar agentJar -x test
```

This produces two jars in `build/libs/`:

| File | What it is |
|---|---|
| `virtual_deadlock_detection-0.0.1-SNAPSHOT.jar` | The DIXY application (REST API + CLI). Runnable with `java -jar`. |
| `virtual_deadlock_detection-0.0.1-SNAPSHOT-agent.jar` | The standalone `-javaagent` jar you attach to your own Java application. |

> `-x test` skips the test suite, which needs Docker. See [Testing](#-testing) to run it. A plain `./gradlew build` does **not** produce the agent jar — ask for `agentJar` explicitly.

---

## 3️⃣ Start PostgreSQL and Redis

```bash
docker compose up -d postgres redis
docker compose ps        # wait until vdd-postgres shows "healthy"
```

This starts PostgreSQL on `localhost:5434` and Redis on `localhost:6379`, which match DIXY's built-in defaults, so no configuration is needed. The database schema is created automatically (Flyway) the first time DIXY starts.

---

## 4️⃣ Run DIXY

Choose a mode.

### 🌐 Web / API mode (port 8081)

**Linux / macOS**

```bash
java -Duser.timezone=UTC -Dspring.profiles.active=web \
     -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

**Windows (PowerShell)**

```powershell
java "-Duser.timezone=UTC" "-Dspring.profiles.active=web" `
  -jar build\libs\virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

Check that it is up:

```bash
curl http://localhost:8081/actuator/health
curl -H "X-API-Key: dixy-local-dev-key-123" http://localhost:8081/api/v1/status
```

The first call reports `UP`; the second returns the diagnostic JSON with `"status":"HEALTHY"`.

> On Windows PowerShell, `curl` is an alias for `Invoke-WebRequest` — use `curl.exe` instead.

### 🖥️ CLI mode (interactive shell)

**Linux / macOS**

```bash
java -Duser.timezone=UTC -Dspring.profiles.active=cli \
     -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

**Windows (PowerShell)**

```powershell
java "-Duser.timezone=UTC" "-Dspring.profiles.active=cli" `
  -jar build\libs\virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

You get a `dixy>` prompt — see [CLI Mode](#️-cli-mode) for the commands.

> **Windows shortcut:** `run-cli.bat` builds the jar, starts PostgreSQL + Redis, waits until PostgreSQL is healthy, and launches the CLI in one step.

### 🐳 Everything in Docker

See [Docker — Full Stack](#-docker--full-stack).

---

## 🔧 Optional: rebuild the native library

The JVMTI library is committed under `src/main/resources/native/` (`Linux-amd64`, `Windows-amd64`). You only need to rebuild it if you change `native/src/agent.cpp`. This needs CMake 3.20+, a C++17 compiler and a JDK (with JNI headers):

```bash
cmake -S native -B native/build -DCMAKE_BUILD_TYPE=Release
cmake --build native/build --config Release
```

The library is written to `src/main/resources/native/<OS>-<arch>/` and is packaged by the next Gradle build.

---

# 🐳 Docker — Full Stack

Run PostgreSQL, Redis, the DIXY API (web mode), Prometheus and Grafana together — no local Java build required:

```bash
docker compose up -d --build
```

Manage the stack:

```bash
docker compose ps                # status of all services
docker compose logs -f app       # follow DIXY logs
docker compose down              # stop (keeps database/Grafana data)
docker compose down -v           # stop and delete database/Grafana data
```

| Service | Address | Notes |
|---|---|---|
| ☕ DIXY API | `http://localhost:8081` | Web profile. Needs the `X-API-Key` header. |
| 🐘 PostgreSQL | `localhost:5434` | DB `virtual_deadlock_detection`, user `postgres`, password `gupi@1234` |
| ⚡ Redis | `localhost:6379` | |
| 📊 Prometheus | `http://localhost:9090` | See [Monitoring](#-monitoring) |
| 📈 Grafana | `http://localhost:3000` | Login `admin` / `admin` |

The `app` image is built from `src/main/java/com/virtual_deadlock_detection/devOps/dev.dockerfile`, which compiles the native library and the jars inside Docker — so the first build is slow and needs internet access.

> The interactive CLI is not part of the Compose stack. To use it against the Compose database, run `docker compose up -d postgres redis` and start the CLI as shown in [Getting Started](#-getting-started).

---

# ⚙️ Configuration

DIXY reads its settings from environment variables. The defaults below match `docker-compose.yml`:

| Variable | Default | Purpose |
|---|---|---|
| `DB_HOST` | `localhost` | PostgreSQL host |
| `DB_PORT` | `5434` | PostgreSQL port |
| `DB_NAME` | `virtual_deadlock_detection` | Database name |
| `DB_USERNAME` | `postgres` | Database user |
| `DB_PASSWORD` | `gupi@1234` | Database password |
| `REDIS_HOST` | `localhost` | Redis host |
| `REDIS_PORT` | `6379` | Redis port |
| `PORT` | `8081` | HTTP port (web mode) |
| `ALLOWED_ORIGINS` | `http://localhost:3000` | Comma-separated CORS origins |

### 🔑 API key

Every endpoint except `/actuator/health` requires an `X-API-Key` header. The default key is `dixy-local-dev-key-123`. Change it with a JVM property — agents must then send the same key:

```bash
-Dsecurity.api-key=YOUR_KEY
```

### Example with overrides

**Linux / macOS**

```bash
DB_HOST=db.internal DB_PASSWORD='s3cret' PORT=9000 \
java -Dsecurity.api-key=MY-KEY -Dspring.profiles.active=web \
     -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

**Windows (PowerShell)**

```powershell
$env:DB_PASSWORD = "s3cret"
$env:PORT = "9000"
java "-Dsecurity.api-key=MY-KEY" "-Dspring.profiles.active=web" `
  -jar build\libs\virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

### 📝 Logs

The console stays quiet; technical logs are written to `logs/vdd.log` in the directory you start DIXY from.

> ⚠️ The default database password and API key are for local development only. Change both before exposing DIXY beyond your machine.

---

# 🖥️ CLI Mode

DIXY includes an interactive command-line interface. PostgreSQL and Redis must be running (see [Getting Started](#-getting-started)).

Start it:

```bash
java -Duser.timezone=UTC -Dspring.profiles.active=cli \
     -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

You will get a `dixy>` prompt:

```text
dixy> status
dixy> threads list
dixy> locks list
dixy> events list
dixy> deadlocks detect
dixy> deadlocks list
```

| Command | What it does |
|---|---|
| `status` | Full diagnostic status (HEALTHY / WARNING / CRITICAL) |
| `threads list` · `threads get` · `threads delete` | List threads, get one by JVM thread id, delete one by database ID |
| `locks list` · `locks get` · `locks delete` | List locks, get one by lock-id, delete one by database ID |
| `events list` | Recent raw JVMTI events (LockOwned / LockReleased) |
| `deadlocks detect` | Run live detection on the current wait-for graph |
| `deadlocks list` | List the latest stored deadlock reports |
| `deadlocks clear` | Clear all stored DIXY telemetry data |

Type `help` inside the shell to see every command and its options.

---

# 🌐 Web / API Mode

Web mode runs DIXY as an HTTP server (no interactive shell) on **http://localhost:8081**.

**Linux / macOS**

```bash
java -Duser.timezone=UTC -Dspring.profiles.active=web \
     -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

**Windows (PowerShell)**

```powershell
java "-Duser.timezone=UTC" "-Dspring.profiles.active=web" `
  -jar build\libs\virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

Change the port with the `PORT` environment variable (see [Configuration](#️-configuration)).

Every endpoint except `/actuator/health` needs the `X-API-Key` header — without it you get `401 Unauthorized`.

> DIXY's Web Mode provides the backend REST/API and monitoring endpoints. The project does not include a separate browser dashboard application.

---

# 📡 REST API

Base URL: `http://localhost:8081`. Send the API key on every request (except health):

```bash
curl -H "X-API-Key: dixy-local-dev-key-123" http://localhost:8081/api/deadlocks
```

A missing or wrong key returns `401` with `{"message":"Missing or invalid X-API-Key header", ...}`.

| Method | Path | Description |
|---|---|---|
| `GET` | `/api/deadlocks` | List detected deadlock reports |
| `GET` | `/api/deadlocks/{id}` | Get one report |
| `POST` | `/api/graph/detect` | Run deadlock detection on the current graph |
| `GET` | `/api/threads` · `/api/threads/{threadId}` | List threads / get one by JVM thread id |
| `DELETE` | `/api/threads/{id}` | Delete a thread by database ID |
| `GET` | `/api/locks` · `/api/locks/{lockId}` | List locks / get one by lock id |
| `DELETE` | `/api/locks/{id}` | Delete a lock by database ID |
| `GET` | `/api/v1/status` | Diagnostic status (HEALTHY / WARNING / CRITICAL) with metrics |
| `DELETE` | `/api/v1/status/clear` | ⚠️ Remove all stored threads, locks and wait edges |
| `POST` | `/api/telemetry` | Send telemetry to DIXY |
| `POST` | `/api/internal/deadlocks` | Used by the Java agent to push detected deadlocks |
| `GET` | `/actuator/health` | Health check (no key needed) |
| `GET` | `/actuator/prometheus` | Prometheus metrics |

---

# ☕ Java Agent

Attach DIXY to **any** Java application with `-javaagent`. The agent watches that application's threads and locks (JVMTI + JFR) and pushes detected deadlocks to a running DIXY backend.

### 1. Build the agent jar

```bash
./gradlew agentJar          # Windows: .\gradlew.bat agentJar
```

Output: `build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT-agent.jar`

### 2. Start the DIXY backend in web mode

See [Web / API Mode](#-web--api-mode) — the agent reports to it.

### 3. Start your application with the agent

**Linux**

```bash
java -javaagent:/path/to/DIXY/build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT-agent.jar=serverUrl=http://localhost:8081,apiKey=dixy-local-dev-key-123,appName=my-service \
     -jar your-application.jar
```

**Windows (PowerShell)** — the quotes are required, otherwise PowerShell splits the argument at the commas:

```powershell
java "-javaagent:C:\path\to\DIXY\build\libs\virtual_deadlock_detection-0.0.1-SNAPSHOT-agent.jar=serverUrl=http://localhost:8081,apiKey=dixy-local-dev-key-123,appName=my-service" `
  -jar your-application.jar
```

| Option | Meaning |
|---|---|
| `serverUrl` | Base URL of the DIXY backend. **Always set it.** If omitted, the agent defaults to `http://localhost:8080`, but DIXY listens on `8081`. |
| `apiKey` | Must equal the backend's API key, otherwise reports are rejected with `401`. |
| `appName` | Label for this application in DIXY (default `unknown-app`). |

Options are comma-separated with no spaces.

On startup the application prints `Agent Started Successfully` and `Reporting detected deadlocks to: http://localhost:8081/api/internal/deadlocks`. Then check the results:

```bash
curl -H "X-API-Key: dixy-local-dev-key-123" http://localhost:8081/api/deadlocks
```

…or run `deadlocks list` in the [CLI](#️-cli-mode).

> The agent needs the bundled native library, which exists for Linux x86-64 and Windows x86-64 only.

### Launcher scripts (`dixy.sh` / `dixy.bat`)

These scripts start a target jar with the agent attached and then open the DIXY CLI. They use `dixy-agent.jar` and `dixy-cli.jar` placed next to the script:

```bash
./dixy.sh path/to/your-app.jar
```

```powershell
.\dixy.bat path\to\your-app.jar
```

> The scripts attach the agent **without** `serverUrl` / `apiKey`, so the agent falls back to `http://localhost:8080` with no key. To report to a web-mode backend, use the explicit `-javaagent` command above. The CLI the scripts open needs PostgreSQL running.

The `dixy-*.jar` files committed in the repository root can lag behind the source. Refresh them from source with:

```bash
./gradlew clean agentJar bootJar -x test
cp build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT-agent.jar dixy-agent.jar
cp build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar dixy-cli.jar
```

```powershell
.\gradlew.bat clean agentJar bootJar -x test
copy /Y build\libs\virtual_deadlock_detection-0.0.1-SNAPSHOT-agent.jar dixy-agent.jar
copy /Y build\libs\virtual_deadlock_detection-0.0.1-SNAPSHOT.jar dixy-cli.jar
```

---

# 📌 Virtual Thread Pinning

DIXY is designed for modern Java applications using **Virtual Threads**.

A simplified example of a potentially pinned virtual thread is:

```text
Virtual Thread
      │
      ▼
 synchronized section
      │
      ▼
 long-running operation
      │
      ▼
📌 Pinning
```

DIXY uses JVM Flight Recorder information together with its monitoring pipeline to help identify this type of runtime behavior.

---

# 📊 Monitoring

DIXY exposes Prometheus metrics at `/actuator/prometheus` (and health at `/actuator/health`). The metrics endpoint requires the API key:

```bash
curl -H "X-API-Key: dixy-local-dev-key-123" http://localhost:8081/actuator/prometheus
```

With the Docker Compose stack:

```text
Prometheus  →  http://localhost:9090
Grafana     →  http://localhost:3000   (admin / admin)
```

### ⚠️ Set the API key Prometheus uses

`devops/prometheus/prometheus.yml` sends an `X-API-Key` header when scraping DIXY. That value must equal DIXY's API key. With the default configuration it does **not**, so Prometheus receives `401` and collects no metrics.

1. Edit `devops/prometheus/prometheus.yml` and set the `X-API-Key` value to `dixy-local-dev-key-123` (or your own key).
2. Restart Prometheus:

```bash
docker compose restart prometheus
```

3. Open `http://localhost:9090/targets` and confirm the target is **UP**.

The scrape target is `host.docker.internal:8081`, which is meant for DIXY running on your host machine. If DIXY runs as the Compose `app` service, change the target to `app:8081`.

### 📈 Grafana

The Compose file does not pre-provision a data source. After logging in, add a Prometheus data source with URL `http://prometheus:9090`.

---

# 🧪 Testing

DIXY's tests exercise real concurrency scenarios — classic deadlocks, virtual threads, virtual-thread pinning and combined deadlock/pinning cases — and boot the Spring context against PostgreSQL and Redis started by Testcontainers. **Docker must be running.**

**Linux / macOS**

```bash
./gradlew test
```

**Windows (PowerShell)**

```powershell
.\gradlew.bat test
```

Run a single test class:

```bash
./gradlew test --tests '*ClassicDeadlockEndToEndTest'
```

Build everything, including tests:

```bash
./gradlew clean build
```

Each test class runs in its own JVM, so the suite is slow — about 2 minutes on CI. CI (`.github/workflows/ci.yml`) runs it on every push and pull request to `main`.

| Test class | Covers |
|---|---|
| `ClassicDeadlockEndToEndTest` | 🔒 Classic platform-thread deadlock through the real pipeline |
| `VirtualThreadPinningEndToEndTest` | 📌 Virtual-thread pinning |
| `HybridPinningDeadlockEndToEndTest` | 🧬 Deadlock cycle correlated with pinning |
| `GraphEngineTest` | 🕸️ Graph and cycle detection |
| `LockIdentityTest` · `ThreadIdentityTest` · `MultiInstanceConsistencyTest` | 🆔 Lock/thread identity and multi-instance consistency |
| `VirtualDeadlockDetectionApplicationTests` | 🚀 Application context starts against PostgreSQL + Redis |

---

# 🧰 Useful Commands

| Action | Linux / macOS | Windows (PowerShell) |
|---|---|---|
| Build app + agent jars (no tests) | `./gradlew clean bootJar agentJar -x test` | `.\gradlew.bat clean bootJar agentJar -x test` |
| Build agent jar only | `./gradlew agentJar` | `.\gradlew.bat agentJar` |
| Full build with tests | `./gradlew clean build` | `.\gradlew.bat clean build` |
| Run tests | `./gradlew test` | `.\gradlew.bat test` |
| Start PostgreSQL + Redis | `docker compose up -d postgres redis` | `docker compose up -d postgres redis` |
| Start the full stack | `docker compose up -d --build` | `docker compose up -d --build` |
| Check containers | `docker compose ps` | `docker compose ps` |
| Follow app logs | `docker compose logs -f app` | `docker compose logs -f app` |
| Stop the stack | `docker compose down` | `docker compose down` |
| Rebuild native library | `cmake -S native -B native/build -DCMAKE_BUILD_TYPE=Release && cmake --build native/build --config Release` | same two `cmake` commands, run one after the other |

Run commands (web, CLI, agent) are in [Getting Started](#-getting-started) and [Java Agent](#-java-agent).

---

# 🔧 Troubleshooting

## Java is not found / wrong version

```bash
java -version
```

It must report **21**. If the command is not recognized, install JDK 21 and add it to your `PATH`.

---

## Build fails at "Fetching distribution" / "Unable to tunnel through proxy"

The Gradle wrapper downloads Gradle 9.5.1 from `services.gradle.org` on first use. If you are behind a proxy or firewall that blocks it, configure the proxy in `~/.gradle/gradle.properties` (Windows: `%USERPROFILE%\.gradle\gradle.properties`) or make that host reachable, then run the build again.

---

## `401 Unauthorized` from the API

Add the API key header:

```bash
curl -H "X-API-Key: dixy-local-dev-key-123" http://localhost:8081/api/deadlocks
```

If you changed the key with `-Dsecurity.api-key=...`, use that value instead.

---

## Cannot connect to PostgreSQL

Check that the container is healthy and that DIXY points at the right port:

```bash
docker compose ps
docker compose logs postgres
```

Docker Compose publishes PostgreSQL on **port 5434**, which is DIXY's default. The optional `local` Spring profile (`application-local.yml`) uses port **5433** — don't activate it against the Compose database unless you change the port.

---

## Port 8081 is already in use

Run DIXY on another port:

```bash
PORT=8090 java -Dspring.profiles.active=web -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

On PowerShell use `$env:PORT = "8090"` first.

---

## Tests fail with "Could not find a valid Docker environment"

The tests use Testcontainers. Start Docker (Docker Desktop on Windows/macOS) and re-run `./gradlew test`.

---

## Nothing appears in the console / where are the logs?

DIXY writes its technical logs to `logs/vdd.log` in the directory you started it from.

---

## Agent: `UnsatisfiedLinkError` / "Native lib not found"

The bundled native library exists only for Linux x86-64 and Windows x86-64. On other platforms you must build and package your own native library (see [Optional: rebuild the native library](#-optional-rebuild-the-native-library)).

---

## Agent starts but no deadlocks show up in DIXY

- Is the DIXY backend running in **web mode**?
- Did you pass `serverUrl=http://localhost:8081` (the agent's default is port 8080)?
- Does `apiKey=` match the backend's key? A wrong or missing key is rejected with `401`.

---

## Prometheus shows no metrics (target DOWN, 401)

Set the `X-API-Key` value in `devops/prometheus/prometheus.yml` to DIXY's API key — see [Monitoring](#-monitoring).

---

## JVM fails to start: "insufficient memory" / "Native memory allocation (mmap) failed"

The machine is short on free RAM. Close other applications or limit the heap, e.g. `java -Xmx512m -jar ...`.

---

## Build problems

Try a clean build:

```bash
./gradlew clean bootJar agentJar -x test
```

Windows:

```powershell
.\gradlew.bat clean bootJar agentJar -x test
```

---

# 🗺️ Simple Architecture

You don't need to understand the internal implementation to use DIXY.

At a high level:

```text
          ☕ Java Application
                  │
                  ▼
             🧠 DIXY Agent
                  │
        ┌─────────┴─────────┐
        ▼                   ▼
     ⚙️ JVMTI              🔥 JFR
        │                   │
        └─────────┬─────────┘
                  ▼
            📥 Event Data
                  │
                  ▼
             🕸️ Graph
                  │
                  ▼
            🔍 Detection
             │         │
             ▼         ▼
          🔴 Deadlock  📌 Pinning
             │         │
             └────┬────┘
                  ▼
             📋 Results
             │         │
             ▼         ▼
          🖥️ CLI     🌐 API
```

---

# 📁 Project Structure

```text
DIXY
│
├── src/
│   ├── main/
│   │   ├── java/com/virtual_deadlock_detection/
│   │   │   ├── LockDetectionAgent/   # -javaagent: JVMTI/JFR collectors, event sender
│   │   │   ├── backend/              # REST controllers, services, JPA entities, security
│   │   │   ├── cli/                  # Spring Shell commands
│   │   │   ├── graph/                # wait-for graph, deadlock & pinning detectors
│   │   │   ├── prediction/           # feature extraction
│   │   │   └── devOps/dev.dockerfile # Docker build used by docker-compose.yml
│   │   └── resources/
│   │       ├── application.yml       # configuration and profiles (cli / web)
│   │       ├── db/migration/         # Flyway SQL migrations
│   │       └── native/               # prebuilt JVMTI libraries (Linux / Windows)
│   └── test/                         # end-to-end and unit tests
│
├── native/                           # C++ JVMTI source + CMake build
├── k8s/                              # Kubernetes manifests
├── devops/prometheus/                # Prometheus scrape config
├── .github/workflows/                # CI, release and publish workflows
├── docker-compose.yml
├── build.gradle · settings.gradle
├── gradlew · gradlew.bat
├── dixy.sh · dixy.bat                # launcher scripts (agent + CLI)
└── dixy-agent.jar · dixy-cli.jar     # prebuilt jars used by the launcher scripts
```

---

# ☸️ Kubernetes

Kubernetes manifests live in `k8s/` and deploy into the `vdd` namespace:

- DIXY (`app`)
- PostgreSQL
- Redis
- Prometheus
- Grafana
- Services, persistent volumes and a secret

Create the namespace first, then apply the rest. Applying the whole folder in one step processes files alphabetically and can reach namespaced resources before the namespace exists:

```bash
kubectl apply -f k8s/namespace.yml
kubectl apply -f k8s/
kubectl -n vdd get pods
```

Reach the API from your machine:

```bash
kubectl -n vdd port-forward svc/app 8081:8080
curl -H "X-API-Key: dixy-local-dev-key-123" http://localhost:8081/api/v1/status
```

Before a real deployment:

- `k8s/app-deployment.yml` pulls the image `z0ker69/patent_26-app:latest` using the pull secret `dockerhub-secret`. To use your own image, build it from the Compose Dockerfile, push it, and edit `image:`:

```bash
docker build -f src/main/java/com/virtual_deadlock_detection/devOps/dev.dockerfile -t YOUR_REGISTRY/dixy:latest .
docker push YOUR_REGISTRY/dixy:latest
```

- The database password is stored (base64-encoded) in `k8s/secret.yml` — replace it.

---

# 🔐 Security

DIXY supports API-key authentication for protected API access.

When deploying DIXY outside a local development environment:

- Use your own API key.
- Keep credentials outside the source code.
- Use environment variables or a secret manager.
- Protect the API with HTTPS when exposed publicly.
- Change default monitoring credentials.

---

# 🤝 Contributing

Want to improve DIXY?

1. Fork the repository.
2. Create a branch:

```bash
git checkout -b feature/my-feature
```

3. Make your changes.
4. Run the tests:

```bash
./gradlew clean test
```

5. Build the project:

```bash
./gradlew clean build
```

6. Open a Pull Request.

---

# 📜 License

See the [`LICENSE`](LICENSE) file in the repository.

---

# 🚀 DIXY in One Sentence

> **DIXY monitors Java thread and lock activity, builds a relationship graph, and helps detect deadlocks and virtual-thread pinning.**

---

<p align="center">
  <strong>🧠 Understand your threads.</strong><br>
  <strong>🕸️ See their relationships.</strong><br>
  <strong>🔍 Find the cycle.</strong><br>
  <strong>🚀 Build more reliable Java applications.</strong>
</p>