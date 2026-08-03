# Virtual Deadlock Detection (VDD) System

> Real-time deadlock detection, thread pinning analysis, and wait-for graph engine for Java 21 Virtual Threads & Platform Threads.

---

## 📑 Table of Contents

1. [Prerequisites](#-prerequisites)
2. [Project Architecture Overview](#-project-architecture-overview)
3. [Local Development & Testing](#-local-development--testing)
   - [Step 1: Start Infrastructure Dependencies (PostgreSQL & Redis)](#step-1-start-infrastructure-dependencies-postgresql--redis)
   - [Step 2: Build the Application](#step-2-build-the-application)
   - [Step 3: Run Automated Tests](#step-3-run-automated-tests)
   - [Step 4: Run the Application Locally](#step-4-run-the-application-locally)
4. [Interactive CLI (Spring Shell)](#-interactive-cli-spring-shell)
5. [Running with Docker & Docker Compose](#-running-with-docker--docker-compose)
   - [Build Docker Image Standalone](#build-docker-image-standalone)
   - [Full Stack with Docker Compose](#full-stack-with-docker-compose)
   - [Useful Docker Commands](#useful-docker-commands)
6. [Deploying to Kubernetes (k8s)](#-deploying-to-kubernetes-k8s)
   - [Step-by-Step Manifest Application](#step-by-step-manifest-application)
   - [Verify Pod & Service Health](#verify-pod--service-health)
   - [Accessing Services via Port-Forward](#accessing-services-via-port-forward)
   - [Tear Down Kubernetes Resources](#tear-down-kubernetes-resources)
7. [Observability & Monitoring](#-observability--monitoring)
8. [REST API Testing (cURL Examples)](#-rest-api-testing-curl-examples)

---

## 🛠 Prerequisites

Ensure the following tools are installed on your machine:

| Tool | Recommended Version | Purpose |
|---|---|---|
| **JDK** | Java 21 (Temurin / OpenJDK) | Application runtime & build |
| **Gradle** | 8.x+ (or use included `./gradlew`) | Build system |
| **Docker & Docker Compose** | Latest Desktop / Engine | Containerization & local stack |
| **kubectl** | v1.28+ | Kubernetes deployment management |
| **Kubernetes Cluster** | Minikube / Kind / Docker Desktop K8s | Container orchestration |

---

## 🏗 Project Architecture Overview

- **Port `8080` / `8081`**: Spring Boot REST API & Actuator
- **Port `5432` / `5433`**: PostgreSQL 16 (Persistence)
- **Port `6379`**: Redis 7 (Fast telemetry cache)
- **Port `9090`**: Prometheus (Metrics scraper)
- **Port `3000`**: Grafana (Monitoring dashboards)

---

## 💻 Local Development & Testing

### Step 1: Start Infrastructure Dependencies (PostgreSQL & Redis)

You can launch just the required databases using Docker Compose:

```bash
# Start PostgreSQL (port 5433 mapped locally) and Redis (port 6379) in background
docker compose up -d postgres redis
```

### Step 2: Build the Application

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

### Step 3: Run Automated Tests

Execute the JUnit 5 test suite (including Testcontainers integration tests):

#### On Windows (PowerShell):
```powershell
.\gradlew.bat test
```

#### On Linux / macOS:
```bash
./gradlew test
```

To run a specific test class (e.g., `ClassicDeadlockEndToEndTest`):
```bash
./gradlew test --tests com.virtual_deadlock_detection.ClassicDeadlockEndToEndTest
```

---

### Step 4: Run the Application Locally

#### Option A: Using the Gradle `bootRun` task
```bash
# Windows PowerShell
$env:DB_PASSWORD="gupi@1234"
.\gradlew.bat bootRun

# Linux / macOS
DB_PASSWORD="gupi@1234" ./gradlew bootRun
```

#### Option B: Using the Packaged JAR
```bash
# Windows PowerShell
$env:DB_PASSWORD="gupi@1234"
java -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar

# Linux / macOS
DB_PASSWORD="gupi@1234" java -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
```

#### Option C: Using provided run scripts
- **Windows**: `.\run.bat gupi@1234`
- **Linux/macOS**: `./run.sh gupi@1234`

---

## 🖥 Interactive CLI (Spring Shell)

When running the application locally via terminal or JAR, the Spring Shell interactive prompt will appear:

```text
  ____   _  _  ____
 / ___| | || ||  _ \
| |     | || || | | |
| |___  |__  || |_| |
 \____|    |_||____/

vdd:>
```

### CLI Command Reference:

| Command | Description |
|---|---|
| `status` | Display real-time deadlock detector dashboard |
| `deadlocks seed` | Seed a 2-way deadlock scenario into the engine |
| `deadlocks detect` | Trigger live cycle detection on the Wait-For Graph |
| `deadlocks list` | Show all historical detected deadlock reports |
| `deadlocks clear` | Reset and clear all threads, locks, and graph edges |
| `threads list` | Display active threads, carrier threads, and states |
| `threads get --thread-id <id>` | Query metadata for a specific JVM thread ID |
| `locks list` | List all tracked locks and current owners |
| `locks get --lock-id <id>` | Inspect a specific lock |
| `help` | View all available shell commands |
| `exit` / `quit` | Exit the CLI session |

---

## 🐳 Running with Docker & Docker Compose

### Build Docker Image Standalone

Build the multi-stage Docker image (compiles Java app + JVMTI native agent):

```bash
docker build -t patent_26-app:latest -f src/main/java/com/virtual_deadlock_detection/devOps/dev.dockerfile .
```

### Full Stack with Docker Compose

Spin up the complete microservice ecosystem:
- **`vdd-postgres`** (PostgreSQL 16)
- **`vdd-redis`** (Redis 7)
- **`vdd-app`** (Spring Boot App with JVMTI agent)
- **`vdd-prometheus`** (Prometheus Server)
- **`vdd-grafana`** (Grafana Dashboards)

```bash
# 1. Build and start all services in detached mode
docker compose up -d --build

# 2. Check running status of all containers
docker compose ps

# 3. Follow application logs
docker compose logs -f app

# 4. Stop all services
docker compose down

# 5. (Optional) Stop and wipe all database & grafana volumes
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

# 3. Deploy PostgreSQL Storage, Pod & Service
kubectl apply -f k8s/postgres-pvc.yml
kubectl apply -f k8s/postgres-deployment.yml
kubectl apply -f k8s/postgres-service.yml

# 4. Deploy Redis Pod & Service
kubectl apply -f k8s/redis-deployment.yml
kubectl apply -f k8s/redis-service.yml

# 5. Deploy Prometheus ConfigMap, Pod & Service
kubectl apply -f k8s/prometheus-configmap.yml
kubectl apply -f k8s/prometheus-deployment.yml
kubectl apply -f k8s/prometheus-service.yml

# 6. Deploy Grafana Storage, Pod & Service
kubectl apply -f k8s/grafana-pvc.yml
kubectl apply -f k8s/grafana-deployment.yml
kubectl apply -f k8s/grafana-service.yml

# 7. Deploy Virtual Deadlock Detection Application & Service
kubectl apply -f k8s/app-deployment.yml
kubectl apply -f k8s/app-service.yml
```

> 💡 **Quick Deploy All Manifests:**
> ```bash
> kubectl apply -f k8s/
> ```

---

### Verify Pod & Service Health

```bash
# View all resources in the vdd namespace
kubectl get all -n vdd

# Watch pod startup progress
kubectl get pods -n vdd -w

# Check logs of the main app container
kubectl logs -n vdd -l app=vdd-app -f
```

---

### Accessing Services via Port-Forward

To access services running in Kubernetes on your local browser:

```bash
# App REST API & Actuator (http://localhost:8080)
kubectl port-forward -n vdd svc/app 8080:8080

# Prometheus UI (http://localhost:9090)
kubectl port-forward -n vdd svc/prometheus 9090:9090

# Grafana Dashboard (http://localhost:3000)
kubectl port-forward -n vdd svc/grafana 3000:3000
```

---

### Tear Down Kubernetes Resources

```bash
# Delete all resources defined in manifests
kubectl delete -f k8s/

# Or delete entire namespace directly
kubectl delete namespace vdd
```

---

## 📊 Observability & Monitoring

Once running via Docker Compose or Kubernetes port-forward:

| Component | URL | Credentials / Notes |
|---|---|---|
| **App Health** | `http://localhost:8080/actuator/health` | Spring Boot Actuator health status |
| **Prometheus Metrics** | `http://localhost:8080/actuator/prometheus` | Raw metrics stream scraped by Prometheus |
| **Prometheus Dashboard** | `http://localhost:9090` | Expression query browser & target status |
| **Grafana UI** | `http://localhost:3000` | User: `admin` \| Password: `admin` |

---

## 🌐 REST API Testing (cURL Examples)

You can test the system using `curl` or Postman:

### 1. Health Check
```bash
curl -X GET http://localhost:8080/actuator/health
```

### 2. Status & Live Deadlock Overview
```bash
curl -X GET http://localhost:8080/api/status
```

### 3. Seed Deadlock Scenario (Demo)
```bash
curl -X POST http://localhost:8080/api/deadlocks/seed
```

### 4. Trigger Live Deadlock Detection
```bash
curl -X GET http://localhost:8080/api/deadlocks/detect
```

### 5. Fetch Active Threads
```bash
curl -X GET http://localhost:8080/api/threads
```

### 6. Fetch Active Locks
```bash
curl -X GET http://localhost:8080/api/locks
```

### 7. Clear All Data & Reset Graph
```bash
curl -X DELETE http://localhost:8080/api/deadlocks/clear
```
