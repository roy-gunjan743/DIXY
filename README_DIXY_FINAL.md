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

## 💻 Requirements

For the easiest setup, you should have:

- ☕ **JDK 21**
- 🐳 **Docker Desktop / Docker**
- 🐘 **PostgreSQL**
- ⚡ **Redis**

If you want to build the native JVM component yourself, you will also need:

- CMake
- A C++ compiler
- JDK/JNI development files

---

# 🪟 Windows Installation

### 1. Check Java

Open PowerShell:

```powershell
java -version
```

Make sure Java 21 is being used.

---

### 2. Open the DIXY folder

```powershell
cd D:\DIXY
```

Replace the path with the location where you downloaded DIXY.

---

### 3. Build DIXY

```powershell
.\gradlew.bat clean build
```

Wait for:

```text
BUILD SUCCESSFUL
```

---

### 4. Start PostgreSQL and Redis

Make sure Docker Desktop is running, then:

```powershell
docker compose up -d postgres redis
```

Check:

```powershell
docker compose ps
```

---

### 5. Start DIXY

#### 🖥️ CLI Mode

```powershell
java "-Duser.timezone=UTC" "-Dspring.profiles.active=cli" `
  -jar build\libs\virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

#### 🌐 Web Mode

```powershell
java "-Duser.timezone=UTC" "-Dspring.profiles.active=web" `
  -jar build\libs\virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

---

# 🐧 Linux Installation

### 1. Check Java

```bash
java -version
```

Use Java 21.

### 2. Build

```bash
./gradlew clean build
```

### 3. Start PostgreSQL and Redis

```bash
docker compose up -d postgres redis
```

### 4. Start CLI Mode

```bash
java -Duser.timezone=UTC \
     -Dspring.profiles.active=cli \
     -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

### 5. Or start Web Mode

```bash
java -Duser.timezone=UTC \
     -Dspring.profiles.active=web \
     -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

---

# 🐳 Docker — Quick Start

If Docker is already installed, you can run the complete development stack with:

```bash
docker compose up -d
```

Check the running services:

```bash
docker compose ps
```

View application logs:

```bash
docker compose logs -f app
```

Stop the stack:

```bash
docker compose down
```

The Docker setup includes:

```text
🐘 PostgreSQL
⚡ Redis
☕ DIXY
📊 Prometheus
📈 Grafana
```

---

# 🖥️ CLI Mode

DIXY includes an interactive command-line interface.

After starting CLI mode, you can use commands such as:

```text
vdd:> status
vdd:> threads list
vdd:> locks list
vdd:> events list
vdd:> deadlocks detect
vdd:> deadlocks list
```

## 🔎 Check status

```text
status
```

Shows the current DIXY status.

## 🧵 View threads

```text
threads list
```

## 🔒 View locks

```text
locks list
```

## 📡 View events

```text
events list
```

## 🔍 Detect deadlocks

```text
deadlocks detect
```

## 🔴 View detected deadlocks

```text
deadlocks list
```

---

# 🌐 Web / API Mode

DIXY can also run as a web server.

Start it using:

```bash
java -Duser.timezone=UTC \
     -Dspring.profiles.active=web \
     -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

The local server runs on:

```text
http://localhost:8081
```

> DIXY's Web Mode provides the backend REST/API and monitoring endpoints. The project does not include a separate browser dashboard application.

---

# 📡 REST API

DIXY provides APIs for working with deadlocks, threads, locks, and graph detection.

## 🔴 Deadlocks

```http
GET /api/deadlocks
```

Get detected deadlock reports.

```http
GET /api/deadlocks/{id}
```

Get a specific report.

---

## 🕸️ Detection

```http
POST /api/graph/detect
```

Run deadlock detection against the current graph.

---

## 🧵 Threads

```http
GET /api/threads
GET /api/threads/{threadId}
```

---

## 🔒 Locks

```http
GET /api/locks
GET /api/locks/{lockId}
```

---

## 📡 Telemetry

```http
POST /api/telemetry
```

Send telemetry information to DIXY.

---

# ☕ Java Agent

DIXY can be attached to a Java application using a Java Agent.

Build the agent:

```bash
./gradlew agentJar
```

Then start your Java application with the DIXY agent:

```bash
java \
  -javaagent:path/to/dixy-agent.jar \
  -jar your-application.jar
```

The agent allows DIXY to observe the target Java application's runtime activity.

A typical configuration contains:

```text
serverUrl
apiKey
appName
```

Example:

```text
-javaagent:path/to/agent.jar=serverUrl=http://localhost:8081,apiKey=YOUR_KEY,appName=my-service
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

DIXY supports monitoring through:

### Prometheus

```text
http://localhost:9090
```

### Grafana

```text
http://localhost:3000
```

### Application metrics

```text
/actuator/prometheus
```

This allows DIXY to be monitored alongside other applications and infrastructure.

---

# 🧪 Testing

DIXY includes tests covering real concurrency scenarios.

Run all tests:

```bash
./gradlew test
```

Windows:

```powershell
.\gradlew.bat test
```

Build the complete project:

```bash
./gradlew clean build
```

The test suite includes scenarios for:

- 🔒 Classic deadlocks
- 🧵 Virtual threads
- 📌 Virtual-thread pinning
- 🧬 Combined deadlock/pinning scenarios

---

# 🧰 Useful Commands

| Action | Command |
|---|---|
| Build | `./gradlew build` |
| Clean build | `./gradlew clean build` |
| Run tests | `./gradlew test` |
| Build Java Agent | `./gradlew agentJar` |
| Start Docker | `docker compose up -d` |
| Stop Docker | `docker compose down` |
| View logs | `docker compose logs -f app` |
| Check containers | `docker compose ps` |

### Windows

Use:

```powershell
.\gradlew.bat build
```

instead of:

```bash
./gradlew build
```

---

# 🔧 Troubleshooting

## Java is not found

Run:

```bash
java -version
```

If the command is not recognized, install JDK 21 and configure your system `PATH`.

---

## Docker services are not starting

Make sure Docker Desktop is running.

Then:

```bash
docker compose up -d
```

Check:

```bash
docker compose ps
```

---

## Build problems

Try a clean build:

```bash
./gradlew clean build
```

Windows:

```powershell
.\gradlew.bat clean build
```

---

## PostgreSQL connection problems

Check that the PostgreSQL container is running:

```bash
docker compose ps
```

Then check the application logs:

```bash
docker compose logs -f app
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
│   │   └── java/
│   │       ├── backend/
│   │       ├── graph/
│   │       ├── cli/
│   │       └── LockDetectionAgent/
│   │
│   └── test/
│
├── native/
├── k8s/
├── devops/
├── docker-compose.yml
├── Dockerfile
├── build.gradle
├── gradlew
└── gradlew.bat
```

---

# ☸️ Kubernetes

For users deploying DIXY in Kubernetes, deployment resources are provided in:

```text
k8s/
```

The folder contains resources for:

- DIXY
- PostgreSQL
- Redis
- Prometheus
- Grafana
- Services
- Persistent storage

A Kubernetes installation can be applied with:

```bash
kubectl apply -f k8s/
```

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
