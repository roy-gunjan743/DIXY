#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────
#  DIXY launcher — attaches the DIXY javaagent to a target JVM app,
#  then opens the DIXY CLI against it.
#
#  Distribution contract: this script + dixy-agent.jar + dixy-cli.jar
#  is the ENTIRE download. No source, no gradle, no repo clone needed.
#
#  Usage:
#    ./dixy.sh                     (auto-detect target jar in cwd)
#    ./dixy.sh path/to/app.jar     (explicit target)
# ─────────────────────────────────────────────────────────────────────
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
AGENT_JAR="$SCRIPT_DIR/dixy-agent.jar"
CLI_JAR="$SCRIPT_DIR/dixy-cli.jar"
LOG_FILE="$SCRIPT_DIR/.dixy-target.log"
PID_FILE="$SCRIPT_DIR/.dixy-target.pid"

# ---- sanity checks ---------------------------------------------------
[ -f "$AGENT_JAR" ] || { echo "[ERROR] dixy-agent.jar not found next to this script."; exit 1; }
[ -f "$CLI_JAR" ]   || { echo "[ERROR] dixy-cli.jar not found next to this script."; exit 1; }

# ---- Step 1: locate the target jar (the app the user wants monitored) --
TARGET_JAR="${1:-}"

if [ -z "$TARGET_JAR" ]; then
    echo "[dixy] No target jar given — scanning current directory..."
    mapfile -t CANDIDATES < <(find . -maxdepth 2 -name "*.jar" \
        ! -name "$(basename "$AGENT_JAR")" \
        ! -name "$(basename "$CLI_JAR")")

    if [ "${#CANDIDATES[@]}" -eq 0 ]; then
        echo "[ERROR] No target .jar found. Pass one explicitly: ./dixy.sh path/to/app.jar"
        exit 1
    elif [ "${#CANDIDATES[@]}" -eq 1 ]; then
        TARGET_JAR="${CANDIDATES[0]}"
    else
        echo "[dixy] Multiple jars found — pick one:"
        select choice in "${CANDIDATES[@]}"; do
            [ -n "$choice" ] && TARGET_JAR="$choice" && break
        done
    fi
fi

echo "[dixy] Target application: $TARGET_JAR"

# ---- Step 2: launch the target with the DIXY agent attached ------------
echo "[dixy] Starting target JVM with -javaagent:dixy-agent.jar ..."
nohup java -javaagent:"$AGENT_JAR" -jar "$TARGET_JAR" > "$LOG_FILE" 2>&1 &
TARGET_PID=$!
echo "$TARGET_PID" > "$PID_FILE"
echo "[dixy] Target started (PID $TARGET_PID). Logs: $LOG_FILE"

# Wait for the agent to confirm it initialized before opening the CLI,
# instead of racing ahead and connecting to nothing.
echo "[dixy] Waiting for agent handshake..."
for i in $(seq 1 30); do
    if grep -q "Agent Components Initialized Successfully" "$LOG_FILE" 2>/dev/null; then
        echo "[dixy] Agent is live."
        break
    fi
    if ! kill -0 "$TARGET_PID" 2>/dev/null; then
        echo "[ERROR] Target process exited before the agent came up. Check $LOG_FILE"
        exit 1
    fi
    sleep 1
    if [ "$i" -eq 30 ]; then
        echo "[WARN] Agent handshake not confirmed after 30s — opening CLI anyway."
    fi
done

# ---- Step 3: open the DIXY CLI, pointed at the running target ----------
cleanup() {
    if [ -f "$PID_FILE" ]; then
        read -r p < "$PID_FILE" 2>/dev/null || true
        echo ""
        read -p "[dixy] Stop the monitored target (PID ${p:-unknown})? [y/N] " ans
        if [[ "$ans" =~ ^[Yy]$ ]] && [ -n "${p:-}" ]; then
            kill "$p" 2>/dev/null || true
        fi
        rm -f "$PID_FILE"
    fi
}
trap cleanup EXIT

echo "[dixy] Launching DIXY CLI..."
java -Dspring.profiles.active=cli -Ddixy.target.pid="$TARGET_PID" -jar "$CLI_JAR"