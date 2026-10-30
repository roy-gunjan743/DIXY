#!/usr/bin/env sh
set -eu
REPO="${DIXY_REPO:-roy-gunjan743/DIXY-CLI}"
BASE_URL="${DIXY_RELEASE_URL:-https://github.com/$REPO/releases}"
INSTALL_DIR="${DIXY_HOME:-$HOME/.dixy}"
BIN_DIR="${DIXY_BIN_DIR:-$HOME/.local/bin}"
VERSION=""; UNINSTALL=false
while [ "$#" -gt 0 ]; do case "$1" in --version) VERSION="${2:?--version requires x.y.z}"; shift 2;; uninstall|--uninstall) UNINSTALL=true; shift;; *) echo "Usage: install.sh [--version x.y.z|uninstall]" >&2; exit 2;; esac; done
if $UNINSTALL; then rm -rf "$INSTALL_DIR"; rm -f "$BIN_DIR/dixy"; echo "DIXY removed from $INSTALL_DIR"; exit 0; fi
JAVA="${JAVA_HOME:-}/bin/java"; [ -x "$JAVA" ] || JAVA=java
command -v "$JAVA" >/dev/null 2>&1 || { echo "DIXY requires Java 21 or newer. Install openjdk-21-jdk, brew install openjdk@21, or Temurin 21." >&2; exit 1; }
MAJOR="$($JAVA -version 2>&1 | sed -n 's/.*version "\([0-9][0-9]*\).*/\1/p' | head -n1)"; [ -n "$MAJOR" ] && [ "$MAJOR" -ge 21 ] || { echo "DIXY requires Java 21 or newer. Install openjdk-21-jdk, brew install openjdk@21, or Temurin 21." >&2; exit 1; }
[ -n "$VERSION" ] || VERSION="$(curl -fsSL "https://api.github.com/repos/$REPO/releases/latest" | sed -n 's/.*"tag_name": *"v\{0,1\}\([^"]*\)".*/\1/p' | head -n1)"; [ -n "$VERSION" ] || { echo "Could not determine the latest DIXY release." >&2; exit 1; }
TAG="v$VERSION"; URL="$BASE_URL/download/$TAG"; TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
for asset in dixy-cli.jar dixy-agent.jar dixy.sh dixy.bat VERSION SHA256SUMS; do curl -fsSL "$URL/$asset" -o "$TMP/$asset"; done
(cd "$TMP" && grep -E '  (dixy-cli.jar|dixy-agent.jar)$' SHA256SUMS | sha256sum -c -)
mkdir -p "$INSTALL_DIR" "$BIN_DIR"; cp "$TMP/dixy-cli.jar" "$TMP/dixy-agent.jar" "$TMP/VERSION" "$INSTALL_DIR/"; cp "$TMP/dixy.sh" "$INSTALL_DIR/dixy"; chmod +x "$INSTALL_DIR/dixy"; ln -sfn "$INSTALL_DIR/dixy" "$BIN_DIR/dixy"
echo "DIXY $VERSION installed in $INSTALL_DIR"; case ":${PATH}:" in *":$BIN_DIR:"*) ;; *) echo "Add $BIN_DIR to PATH, then open a new terminal.";; esac
