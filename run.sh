#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
#  Virtual Deadlock Detection — Linux / macOS startup script
#  Usage:
#    ./run.sh                          (uses DB_PASSWORD already in env)
#    ./run.sh mySecretPassword         (pass password as first argument)
# ─────────────────────────────────────────────────────────────────────
set -euo pipefail

# Accept password as positional arg, falling back to the environment
if [ -n "${1:-}" ]; then
    export DB_PASSWORD="$1"
fi

if [ -z "${DB_PASSWORD:-}" ]; then
    echo "[ERROR] DB_PASSWORD is not set."
    echo "  Usage: ./run.sh <password>"
    echo "         or export DB_PASSWORD=<password> before running."
    exit 1
fi

echo "[INFO] Starting Virtual Deadlock Detection..."
echo "[INFO] DB_PASSWORD is set."

java -jar build/libs/virtual_deadlock_detection-0.0.1-SNAPSHOT.jar
