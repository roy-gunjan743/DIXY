#!/usr/bin/env bash
set -euo pipefail
SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
CLI_JAR="$SCRIPT_DIR/dixy-cli.jar"
AGENT_JAR="$SCRIPT_DIR/dixy-agent.jar"
if [[ "${1:-}" == "--version" || "${1:-}" == "version" || "${1:-}" == "-v" ]]; then
	if [[ -f "$SCRIPT_DIR/VERSION" ]]; then cat "$SCRIPT_DIR/VERSION"; else echo "DIXY development build"; fi
	exit 0
fi
[[ -f "$CLI_JAR" && -f "$AGENT_JAR" ]] || { echo "DIXY installation is incomplete: expected jars in $SCRIPT_DIR" >&2; exit 1; }
JAVA_BIN="${JAVA_HOME:-}/bin/java"; [[ -x "$JAVA_BIN" ]] || JAVA_BIN=java
command -v "$JAVA_BIN" >/dev/null 2>&1 || { echo "DIXY requires Java 21 or newer. Install it from https://adoptium.net/ and retry." >&2; exit 1; }
JAVA_MAJOR="$($JAVA_BIN -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n1)"
[[ -n "$JAVA_MAJOR" && "$JAVA_MAJOR" -ge 21 ]] || { echo "DIXY requires Java 21 or newer. Install it from https://adoptium.net/ and retry." >&2; exit 1; }
NON_INTERACTIVE=false; SHELL_INTERACTIVE=true
if [[ "${1:-}" == "detect" ]]; then NON_INTERACTIVE=true; SHELL_INTERACTIVE=false; fi
if command -v powershell.exe >/dev/null 2>&1; then DIXY_PORT="$(powershell.exe -NoProfile -Command '$p=8081; do {$c=[Net.Sockets.TcpClient]::new(); try {$c.Connect("127.0.0.1",$p); $busy=$true} catch {$busy=$false}; $c.Dispose(); if($busy){$p++}} while($busy); $p' | tr -d '\r')"; else DIXY_PORT=8081; fi
if [[ $# -gt 0 && "${1:-}" != "detect" && "${1:-}" != "--version" && "${1:-}" != "version" && "${1:-}" != "-v" ]]; then
	TARGET_ROOT="$1"
	if [[ -d "$TARGET_ROOT" ]]; then
		mapfile -t CANDIDATES < <(find "$TARGET_ROOT" -maxdepth 1 -type f -name '*.jar' \
			! -name 'dixy-agent.jar' ! -name 'dixy-cli.jar' ! -name '*-plain.jar' | sort)
		if [[ ${#CANDIDATES[@]} -eq 0 ]]; then echo "[dixy] No runnable jar found in $TARGET_ROOT" >&2; exit 1; fi
		if [[ ${#CANDIDATES[@]} -eq 1 ]]; then TARGET_JAR="${CANDIDATES[0]}"; else
			echo "[dixy] Select the target jar:"; select TARGET_JAR in "${CANDIDATES[@]}"; do [[ -n "$TARGET_JAR" ]] && break; done
		fi
	elif [[ -f "$TARGET_ROOT" && "$TARGET_ROOT" == *.jar ]]; then
		TARGET_JAR="$TARGET_ROOT"
	else
		echo "[dixy] Target root or jar does not exist: $TARGET_ROOT" >&2; exit 1
	fi
	echo "[dixy] Starting target: $TARGET_JAR"
	nohup "$JAVA_BIN" -javaagent:"$AGENT_JAR"=serverUrl=http://localhost:"$DIXY_PORT",apiKey=dixy-local-dev-key-123,appName=dixy-target -jar "$TARGET_JAR" > "$SCRIPT_DIR/dixy-target.log" 2>&1 &
	echo "[dixy] Target started. Use 'detect' in the dixy shell for live scanning."
	shift
fi
ACTIVE_PROFILE=cli; [[ "${1:-}" == "detect" ]] && ACTIVE_PROFILE=web
exec "$JAVA_BIN" -Dserver.port="$DIXY_PORT" -Ddixy.server.url="http://localhost:$DIXY_PORT" -Dspring.profiles.active="$ACTIVE_PROFILE" -Dspring.shell.interactive.enabled="$SHELL_INTERACTIVE" -Ddixy.agent.path="$AGENT_JAR" -Ddixy.noninteractive="$NON_INTERACTIVE" -jar "$CLI_JAR" "$@"
